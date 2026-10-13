package com.example.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.model.MediaItem
import com.example.model.StreamServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ChannelStatusManager: Tracks working / inactive channels dynamically.
 * Remembers validated channels and auto-verifies channels smoothly in the background
 * without freezing, lagging, or crashing devices of any specification.
 */
object ChannelStatusManager {

    private const val PREFS_NAME = "nafitv_channel_status"
    private const val KEY_FAILED_CHANNELS = "failed_channel_ids"
    private const val KEY_VERIFIED_ACTIVE = "verified_channel_ids"
    private const val KEY_FAILED_SERVERS = "failed_server_urls"
    private const val KEY_ONLY_ACTIVE_ENABLED = "only_active_enabled"

    private val workingStatusMap = ConcurrentHashMap<String, Boolean>()
    private val failedServerUrls = ConcurrentHashMap.newKeySet<String>()
    private val probingIds = ConcurrentHashMap.newKeySet<String>()
    private val probedIds = ConcurrentHashMap.newKeySet<String>()
    private val probeQueue = ConcurrentLinkedQueue<MediaItem>()
    private val isWorkerRunning = AtomicBoolean(false)
    private var lastTickTime = 0L

    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _statusUpdateTick = MutableStateFlow(0L)
    val statusUpdateTick: StateFlow<Long> = _statusUpdateTick.asStateFlow()

    private var prefs: SharedPreferences? = null

    // Dedicated lightweight, zero-leak client for stream reachability checks
    // Strict 2.5s call timeout prevents thread lockups on unresponsive streaming servers
    private val checkClient by lazy {
        OkHttpClient.Builder()
            .callTimeout(2500, TimeUnit.MILLISECONDS)
            .connectTimeout(2000, TimeUnit.MILLISECONDS)
            .readTimeout(2000, TimeUnit.MILLISECONDS)
            .connectionPool(okhttp3.ConnectionPool(4, 15, TimeUnit.SECONDS))
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(false)
            .build()
    }

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            loadPersistedStatuses()
        }
    }

    /**
     * Get persisted "Only Active" toggle state.
     */
    fun isOnlyActiveEnabled(): Boolean {
        return prefs?.getBoolean(KEY_ONLY_ACTIVE_ENABLED, false) ?: false
    }

    /**
     * Persist user's choice for "Only Active" toggle.
     */
    fun setOnlyActiveEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_ONLY_ACTIVE_ENABLED, enabled)?.apply()
        _statusUpdateTick.value = System.currentTimeMillis()
    }

    private fun loadPersistedStatuses() {
        val p = prefs ?: return
        val failedSet = p.getStringSet(KEY_FAILED_CHANNELS, emptySet()) ?: emptySet()
        val verifiedSet = p.getStringSet(KEY_VERIFIED_ACTIVE, emptySet()) ?: emptySet()
        val failedServers = p.getStringSet(KEY_FAILED_SERVERS, emptySet()) ?: emptySet()

        for (id in failedSet) {
            workingStatusMap[id] = false
        }
        for (id in verifiedSet) {
            workingStatusMap[id] = true
        }
        for (url in failedServers) {
            failedServerUrls.add(url)
        }
    }

    /**
     * Checks whether a specific server URL is active and not marked broken.
     */
    fun isServerActive(serverUrl: String): Boolean {
        val trimmed = serverUrl.trim()
        if (trimmed.isBlank()) return false
        if (failedServerUrls.contains(trimmed)) return false
        return isValidStreamFormat(trimmed)
    }

    /**
     * Mark a specific server URL as failed/inactive.
     */
    fun markServerFailed(serverUrl: String) {
        val trimmed = serverUrl.trim()
        if (trimmed.isBlank()) return
        failedServerUrls.add(trimmed)
        saveFailedServerToPrefs(trimmed)
    }

    /**
     * Mark a specific server URL as active/working (re-enable).
     */
    fun markServerSuccess(serverUrl: String) {
        val trimmed = serverUrl.trim()
        if (trimmed.isBlank()) return
        if (failedServerUrls.remove(trimmed)) {
            removeFailedServerFromPrefs(trimmed)
        }
    }

    /**
     * Returns the active servers for a media item. Always returns all available servers
     * so that the user can freely choose or switch to any server.
     */
    fun getActiveServers(mediaItem: MediaItem): List<StreamServer> {
        return mediaItem.getAllServers()
    }

    /**
     * Checks whether a channel is considered currently active and working.
     */
    fun isChannelActive(channel: MediaItem): Boolean {
        // If explicitly recorded in workingStatusMap
        val recordedStatus = workingStatusMap[channel.id]
        if (recordedStatus != null) {
            return recordedStatus
        }

        // Check if stream URL is present and not a dummy
        val mainUrl = channel.streamUrl.trim()
        val allServers = channel.getAllServers()

        if (mainUrl.isBlank() && allServers.isEmpty()) {
            return false
        }

        val primaryUrl = mainUrl.ifBlank { allServers.firstOrNull()?.url.orEmpty() }.trim()
        if (!isValidStreamFormat(primaryUrl)) {
            return false
        }

        // Check if all servers in the channel are already marked broken
        if (allServers.isNotEmpty()) {
            val hasValidServer = allServers.any { isValidStreamFormat(it.url) && !failedServerUrls.contains(it.url.trim()) }
            if (!hasValidServer) return false
        }

        return true
    }

    /**
     * Enqueues channels for lightweight, non-blocking background health check.
     * Uses a single throttled worker coroutine on Dispatchers.IO with batched UI ticks.
     * Guaranteed zero UI thread blocking, zero thread explosion, zero memory leaks.
     */
    fun enqueueChannelsForProbing(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val unverified = items.filter { item ->
            item.id.isNotBlank() && !workingStatusMap.containsKey(item.id) && probedIds.add(item.id)
        }
        if (unverified.isEmpty()) return

        probeQueue.addAll(unverified)

        if (isWorkerRunning.compareAndSet(false, true)) {
            managerScope.launch {
                try {
                    val batchResults = mutableMapOf<String, Boolean>()
                    var processedInBatch = 0

                    while (true) {
                        val nextItem = probeQueue.poll() ?: break
                        probingIds.add(nextItem.id)
                        try {
                            val isWorking = checkChannelReachability(nextItem)
                            workingStatusMap[nextItem.id] = isWorking
                            batchResults[nextItem.id] = isWorking
                            processedInBatch++

                            val now = System.currentTimeMillis()
                            // Debounce UI update tick to at least 3.5 seconds to prevent recomposition stutter
                            if (processedInBatch >= 12 || (now - lastTickTime) >= 3500L) {
                                batchSaveStatusToPrefs(batchResults)
                                batchResults.clear()
                                processedInBatch = 0
                                lastTickTime = now
                                _statusUpdateTick.value = now
                            }
                        } catch (e: Exception) {
                            Log.w("ChannelStatusManager", "Probe error for ${nextItem.title}", e)
                        } finally {
                            probingIds.remove(nextItem.id)
                        }
                        kotlinx.coroutines.delay(180) // 180ms throttle keeps device cool and network free
                    }

                    if (batchResults.isNotEmpty()) {
                        batchSaveStatusToPrefs(batchResults)
                        lastTickTime = System.currentTimeMillis()
                        _statusUpdateTick.value = lastTickTime
                    }
                } finally {
                    isWorkerRunning.set(false)
                }
            }
        }
    }

    /**
     * Backwards-compatible caller for probing unverified channels.
     */
    fun probeChannelsAsync(scope: CoroutineScope, items: List<MediaItem>) {
        enqueueChannelsForProbing(items)
    }

    private fun checkChannelReachability(item: MediaItem): Boolean {
        val allServers = item.getAllServers()
        val mainUrl = item.streamUrl.trim()

        if (mainUrl.isBlank() && allServers.isEmpty()) {
            return false
        }

        // Collect all distinct stream URLs
        val urlsToTest = mutableListOf<String>()
        if (mainUrl.isNotBlank() && isValidStreamFormat(mainUrl)) {
            urlsToTest.add(mainUrl)
        }
        for (server in allServers) {
            val sUrl = server.url.trim()
            if (sUrl.isNotBlank() && isValidStreamFormat(sUrl) && !urlsToTest.contains(sUrl)) {
                urlsToTest.add(sUrl)
            }
        }

        if (urlsToTest.isEmpty()) {
            return false
        }

        // Extract channel-specific headers to avoid false 403 Forbidden on token/referer-protected channels
        val channelHeaders = mutableMapOf<String, String>()
        item.userAgent?.takeIf { it.isNotBlank() }?.let { channelHeaders["User-Agent"] = it }
        item.referrer?.takeIf { it.isNotBlank() }?.let { channelHeaders["Referer"] = it }
        item.origin?.takeIf { it.isNotBlank() }?.let { channelHeaders["Origin"] = it }
        item.cookie?.takeIf { it.isNotBlank() }?.let { channelHeaders["Cookie"] = it }
        item.customHeaders?.let { channelHeaders.putAll(it) }

        var anyServerWorking = false
        for (url in urlsToTest) {
            val ok = testSingleStreamUrl(url, channelHeaders)
            if (ok) {
                anyServerWorking = true
                markServerSuccess(url)
            } else {
                markServerFailed(url)
            }
        }

        return anyServerWorking
    }

    private fun testSingleStreamUrl(url: String, extraHeaders: Map<String, String> = emptyMap()): Boolean {
        if (!isValidStreamFormat(url)) return false

        val lowerUrl = url.lowercase()
        // Determine appropriate User-Agent & Referer for known streaming networks
        val effectiveUa = extraHeaders["User-Agent"]
            ?: when {
                lowerUrl.contains("toffee") || lowerUrl.contains("bldcmprod-cdn") -> "Toffee (Linux;Android 14)"
                else -> "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
            }
        val effectiveReferer = extraHeaders["Referer"]
            ?: when {
                lowerUrl.contains("toffee") || lowerUrl.contains("bldcmprod-cdn") -> "https://toffeelive.com/"
                lowerUrl.contains("hakunaymatata") || lowerUrl.contains("sacdn") -> "https://hakunaymatata.com/"
                else -> null
            }
        val effectiveOrigin = extraHeaders["Origin"]
            ?: when {
                lowerUrl.contains("toffee") || lowerUrl.contains("bldcmprod-cdn") -> "https://toffeelive.com"
                lowerUrl.contains("hakunaymatata") || lowerUrl.contains("sacdn") -> "https://hakunaymatata.com"
                else -> null
            }

        return try {
            // First attempt: HEAD request (Fastest)
            val headBuilder = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", effectiveUa)
                .header("Accept", "*/*")

            if (effectiveReferer != null) headBuilder.header("Referer", effectiveReferer)
            if (effectiveOrigin != null) headBuilder.header("Origin", effectiveOrigin)
            extraHeaders.forEach { (k, v) ->
                if (!k.equals("User-Agent", ignoreCase = true) &&
                    !k.equals("Referer", ignoreCase = true) &&
                    !k.equals("Origin", ignoreCase = true)
                ) {
                    headBuilder.header(k, v)
                }
            }

            val headCall = checkClient.newCall(headBuilder.build())
            val headResp = headCall.execute()
            val code = headResp.code
            headResp.close()

            if (code in 200..399) {
                return true
            }

            // Fallback: Range GET request (reads at most first 256 bytes, never streams continuously)
            if (code in listOf(400, 401, 403, 404, 405, 406, 416, 500, 501, 503)) {
                val getBuilder = Request.Builder()
                    .url(url)
                    .header("Range", "bytes=0-1024")
                    .header("User-Agent", effectiveUa)
                    .header("Accept", "*/*")

                if (effectiveReferer != null) getBuilder.header("Referer", effectiveReferer)
                if (effectiveOrigin != null) getBuilder.header("Origin", effectiveOrigin)
                extraHeaders.forEach { (k, v) ->
                    if (!k.equals("User-Agent", ignoreCase = true) &&
                        !k.equals("Referer", ignoreCase = true) &&
                        !k.equals("Origin", ignoreCase = true)
                    ) {
                        getBuilder.header(k, v)
                    }
                }

                val getCall = checkClient.newCall(getBuilder.build())
                val getResp = getCall.execute()
                getResp.use { resp ->
                    val getCode = resp.code
                    val getContentType = resp.header("Content-Type")?.lowercase() ?: ""
                    val body = resp.body
                    val bodyBytes = try { body?.source()?.peek()?.readByteArray(256) ?: ByteArray(0) } catch (_: Exception) { ByteArray(0) }

                    val isMediaContent = getContentType.contains("mpegurl") ||
                            getContentType.contains("video") ||
                            getContentType.contains("audio") ||
                            getContentType.contains("octet-stream") ||
                            getContentType.contains("vnd.apple") ||
                            String(bodyBytes).contains("#EXTM3U", ignoreCase = true)

                    return when {
                        getCode in 200..399 -> true
                        (getCode == 401 || getCode == 403) && isMediaContent -> true
                        else -> false
                    }
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    private fun isValidStreamFormat(url: String): Boolean {
        if (url.isBlank() || url.length < 8) return false
        val lower = url.lowercase()
        if (!lower.startsWith("http://") && !lower.startsWith("https://") && !lower.startsWith("rtmp://") && !lower.startsWith("rtsp://")) {
            return false
        }
        if (lower.contains("dummy") || lower.contains("placeholder") || lower.contains("127.0.0.1") || lower.contains("example.com") || lower.endsWith("#")) {
            return false
        }
        return true
    }

    /**
     * Mark a channel as working successfully (called when playback starts or frames render)
     */
    fun markChannelSuccess(channelId: String) {
        if (channelId.isBlank()) return
        workingStatusMap[channelId] = true
        _statusUpdateTick.value = System.currentTimeMillis()
        saveStatusToPrefs(channelId, true)
    }

    /**
     * Mark a channel as failed/broken (called when playback fails and all servers fail)
     */
    fun markChannelFailed(channelId: String) {
        if (channelId.isBlank()) return
        workingStatusMap[channelId] = false
        _statusUpdateTick.value = System.currentTimeMillis()
        saveStatusToPrefs(channelId, false)
    }

    /**
     * Reset a channel's failed status so it can be re-tested
     */
    fun resetChannelStatus(channelId: String) {
        workingStatusMap.remove(channelId)
        _statusUpdateTick.value = System.currentTimeMillis()
        prefs?.let { p ->
            val failedSet = p.getStringSet(KEY_FAILED_CHANNELS, emptySet())?.toMutableSet() ?: mutableSetOf()
            if (failedSet.remove(channelId)) {
                p.edit().putStringSet(KEY_FAILED_CHANNELS, failedSet).apply()
            }
        }
    }

    /**
     * Reset all status marks
     */
    fun resetAllStatuses() {
        workingStatusMap.clear()
        probedIds.clear()
        probeQueue.clear()
        _statusUpdateTick.value = System.currentTimeMillis()
        prefs?.edit()?.clear()?.apply()
    }

    private fun saveFailedServerToPrefs(serverUrl: String) {
        val p = prefs ?: return
        try {
            val curSet = p.getStringSet(KEY_FAILED_SERVERS, emptySet())?.toMutableSet() ?: mutableSetOf()
            curSet.add(serverUrl)
            p.edit().putStringSet(KEY_FAILED_SERVERS, curSet).apply()
        } catch (e: Exception) {
            Log.w("ChannelStatusManager", "Failed to save failed server status", e)
        }
    }

    private fun removeFailedServerFromPrefs(serverUrl: String) {
        val p = prefs ?: return
        try {
            val curSet = p.getStringSet(KEY_FAILED_SERVERS, emptySet())?.toMutableSet() ?: mutableSetOf()
            if (curSet.remove(serverUrl)) {
                p.edit().putStringSet(KEY_FAILED_SERVERS, curSet).apply()
            }
        } catch (e: Exception) {
            Log.w("ChannelStatusManager", "Failed to remove failed server status", e)
        }
    }

    private fun saveStatusToPrefs(channelId: String, isSuccess: Boolean) {
        val p = prefs ?: return
        try {
            val key = if (isSuccess) KEY_VERIFIED_ACTIVE else KEY_FAILED_CHANNELS
            val otherKey = if (isSuccess) KEY_FAILED_CHANNELS else KEY_VERIFIED_ACTIVE
            val curSet = p.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()
            val otherSet = p.getStringSet(otherKey, emptySet())?.toMutableSet() ?: mutableSetOf()

            otherSet.remove(channelId)
            curSet.add(channelId)

            p.edit()
                .putStringSet(key, curSet)
                .putStringSet(otherKey, otherSet)
                .apply()
        } catch (e: Exception) {
            Log.w("ChannelStatusManager", "Failed to save channel status", e)
        }
    }

    private fun batchSaveStatusToPrefs(batchResults: Map<String, Boolean>) {
        if (batchResults.isEmpty()) return
        val p = prefs ?: return
        try {
            val verifiedSet = p.getStringSet(KEY_VERIFIED_ACTIVE, emptySet())?.toMutableSet() ?: mutableSetOf()
            val failedSet = p.getStringSet(KEY_FAILED_CHANNELS, emptySet())?.toMutableSet() ?: mutableSetOf()

            batchResults.forEach { (id, isSuccess) ->
                if (isSuccess) {
                    failedSet.remove(id)
                    verifiedSet.add(id)
                } else {
                    verifiedSet.remove(id)
                    failedSet.add(id)
                }
            }

            p.edit()
                .putStringSet(KEY_VERIFIED_ACTIVE, verifiedSet)
                .putStringSet(KEY_FAILED_CHANNELS, failedSet)
                .apply()
        } catch (e: Exception) {
            Log.w("ChannelStatusManager", "Failed to batch save channel statuses", e)
        }
    }
}

