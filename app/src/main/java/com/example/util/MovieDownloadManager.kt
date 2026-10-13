package com.example.util

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R
import com.example.model.MediaItem
import com.example.model.MediaType
import com.example.service.MovieDownloadService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

enum class DownloadState {
    IDLE,
    PENDING,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class MovieDownloadProgress(
    val movieId: String,
    val title: String,
    val progress: Float = 0f, // 0.0 to 1.0
    val progressPercent: Int = 0, // 0 to 100
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val downloadedSizeFormatted: String = "0 MB",
    val totalSizeFormatted: String = "0 MB",
    val speedFormatted: String = "0 KB/s",
    val state: DownloadState = DownloadState.IDLE,
    val errorMessage: String? = null,
    val targetUrl: String = "",
    val mediaItem: MediaItem? = null
)

data class DownloadedMovie(
    val id: String,
    val title: String,
    val category: String = "Movie",
    val logoUrl: String? = null,
    val year: String? = null,
    val quality: String = "HD",
    val downloadUrl: String = "",
    val localFilePath: String = "",
    val fileSizeBytes: Long = 0L,
    val fileSizeFormatted: String = "0 MB",
    val downloadDateFormatted: String = "",
    val downloadTimestamp: Long = System.currentTimeMillis()
) {
    val fileExists: Boolean get() = localFilePath.isNotBlank() && File(localFilePath).exists()

    fun toMediaItem(): MediaItem {
        val path = localFilePath.trim()
        val playUri = if (path.startsWith("/")) "file://$path" else path
        return MediaItem(
            id = "offline_$id",
            title = "🎬 $title (Offline)",
            category = "📥 ডাউনলোডসমূহ",
            type = MediaType.MOVIE,
            streamUrl = playUri,
            logoUrl = logoUrl,
            description = "অফলাইন লোকাল ফাইল • সাইজ: $fileSizeFormatted • ডাউনলোডের তারিখ: $downloadDateFormatted",
            quality = quality,
            year = year,
            isLive = false
        )
    }
}

/**
 * Resumable, High-Speed Movie Download Manager.
 * Features:
 * 1. HTTP Range Resume (206 Partial Content): picks up exactly where left off if internet drops or download fails.
 * 2. Automatic network reconnection retry loop for brief connectivity loss.
 * 3. Never deletes partial (.part) downloads on network error or app pause.
 * 4. Persists unfinished tasks across app and phone restarts so users can resume anytime.
 * 5. Low CPU & RAM usage: 128KB buffered I/O with throttled UI state emissions.
 */
object MovieDownloadManager {

    private const val PREFS_NAME = "nafi_movie_downloads_prefs"
    private const val KEY_DOWNLOADED_MOVIES = "key_downloaded_movies_json"
    private const val KEY_PAUSED_TASKS = "key_paused_tasks_json"
    private const val NOTIF_CHANNEL_ID = "nafi_movie_download_channel"
    private const val NOTIF_CHANNEL_NAME = "মুভি ডাউনলোড নোটিফিকেশন"

    private val activeDownloadJobs = ConcurrentHashMap<String, Job>()
    private val activeProgressMap = ConcurrentHashMap<String, MovieDownloadProgress>()

    private val _downloadsState = MutableStateFlow<Map<String, MovieDownloadProgress>>(emptyMap())
    val downloadsState: StateFlow<Map<String, MovieDownloadProgress>> = _downloadsState.asStateFlow()

    private val _downloadedMoviesFlow = MutableStateFlow<List<DownloadedMovie>>(emptyList())
    val downloadedMoviesFlow: StateFlow<List<DownloadedMovie>> = _downloadedMoviesFlow.asStateFlow()

    // High-throughput, resilient HTTP client
    private val downloadHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(32, 10, TimeUnit.MINUTES))
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun init(context: Context) {
        createNotificationChannel(context)
        refreshDownloadedMoviesList(context)
        restorePausedTasks(context)
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL_ID,
                NOTIF_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "মুভি ডাউনলোড প্রগ্রেস ও নোটিফিকেশন"
                setShowBadge(false)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    fun isMovieDownloading(movieId: String): Boolean {
        val prog = activeProgressMap[movieId]
        return prog?.state == DownloadState.DOWNLOADING || prog?.state == DownloadState.PENDING
    }

    fun isMoviePaused(movieId: String): Boolean {
        val prog = activeProgressMap[movieId]
        return prog?.state == DownloadState.PAUSED
    }

    fun hasPartialDownload(context: Context, movieId: String): Boolean {
        val prog = activeProgressMap[movieId]
        if (prog != null && prog.downloadedBytes > 0L && (prog.state == DownloadState.PAUSED || prog.state == DownloadState.FAILED)) {
            return true
        }
        val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: File(context.filesDir, "movies")
        val idPrefix = "NAFITV_${movieId.take(8)}_"
        return moviesDir.listFiles()?.any { it.name.startsWith(idPrefix) && it.name.endsWith(".part") && it.length() > 0L } == true
    }

    fun getPartialDownloadBytes(context: Context, movieId: String): Long {
        val prog = activeProgressMap[movieId]
        if (prog != null && prog.downloadedBytes > 0L) return prog.downloadedBytes
        val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: File(context.filesDir, "movies")
        val idPrefix = "NAFITV_${movieId.take(8)}_"
        val file = moviesDir.listFiles()?.firstOrNull { it.name.startsWith(idPrefix) && it.name.endsWith(".part") }
        return file?.length() ?: 0L
    }

    fun isMovieDownloaded(context: Context, movieId: String): Boolean {
        val list = getDownloadedMovies(context)
        return list.any { it.id == movieId && it.fileExists }
    }

    fun pauseDownload(movieId: String) {
        activeDownloadJobs[movieId]?.cancel()
        activeDownloadJobs.remove(movieId)
        activeProgressMap[movieId]?.let { cur ->
            activeProgressMap[movieId] = cur.copy(
                state = DownloadState.PAUSED,
                speedFormatted = "স্থগিত"
            )
            _downloadsState.value = HashMap(activeProgressMap)
        }
    }

    fun resumeDownload(context: Context, movieId: String) {
        val prog = activeProgressMap[movieId]
        if (prog?.mediaItem != null) {
            startDownload(
                context = context,
                mediaItem = prog.mediaItem,
                preferredUrl = prog.targetUrl
            )
            return
        }
        val pausedList = getStoredPausedTasks(context)
        val task = pausedList.firstOrNull { it.id == movieId }
        if (task != null) {
            startDownload(
                context = context,
                mediaItem = task.toMediaItem(),
                preferredUrl = task.downloadUrl
            )
        }
    }

    fun cancelDownload(movieId: String, deletePartialFile: Boolean = false, context: Context? = null) {
        activeDownloadJobs[movieId]?.cancel()
        activeDownloadJobs.remove(movieId)

        if (deletePartialFile && context != null) {
            deletePartialDownload(context, movieId)
        } else {
            activeProgressMap[movieId]?.let { cur ->
                activeProgressMap[movieId] = cur.copy(
                    state = DownloadState.PAUSED,
                    speedFormatted = "স্থগিত"
                )
                _downloadsState.value = HashMap(activeProgressMap)
            }
        }
    }

    fun deletePartialDownload(context: Context, movieId: String): Boolean {
        activeDownloadJobs[movieId]?.cancel()
        activeDownloadJobs.remove(movieId)
        activeProgressMap.remove(movieId)
        _downloadsState.value = HashMap(activeProgressMap)
        removePausedTask(context, movieId)

        val notifId = (movieId.hashCode() and 0x7FFFFFFF)
        cancelNotification(context, notifId)

        val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: File(context.filesDir, "movies")
        val idPrefix = "NAFITV_${movieId.take(8)}_"
        moviesDir.listFiles()?.filter { it.name.startsWith(idPrefix) && it.name.endsWith(".part") }?.forEach {
            try { it.delete() } catch (_: Exception) {}
        }
        return true
    }

    /**
     * Start or resume downloading a movie file.
     * If a .part file exists from a previous attempt, Range: bytes=$existingBytes- is sent
     * to resume without restarting from 0.
     */
    fun startDownload(
        context: Context,
        mediaItem: MediaItem,
        preferredUrl: String? = null,
        onStarted: (() -> Unit)? = null,
        onComplete: ((DownloadedMovie) -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        val targetUrl = preferredUrl?.trim()?.takeIf { it.isNotBlank() } ?: mediaItem.streamUrl.trim()

        if (targetUrl.isBlank()) {
            val err = "মুভির ডাউনলোড লিংক পাওয়া যায়নি!"
            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
            onError?.invoke(err)
            return
        }

        if (isMovieDownloading(mediaItem.id)) {
            Toast.makeText(context, "এই মুভিটি ইতিমধ্যে ডাউনলোড হচ্ছে...", Toast.LENGTH_SHORT).show()
            return
        }

        if (isMovieDownloaded(context, mediaItem.id)) {
            Toast.makeText(context, "মুভিটি ইতিমধ্যে আপনার অফলাইন ডিভাইসে ডাউনলোড করা আছে!", Toast.LENGTH_SHORT).show()
            return
        }

        val notifId = (mediaItem.id.hashCode() and 0x7FFFFFFF)

        // Determine destination file paths
        val moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
            ?: File(context.filesDir, "movies").apply { mkdirs() }
        if (!moviesDir.exists()) moviesDir.mkdirs()

        val safeTitle = mediaItem.title
            .replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            .take(40)

        val ext = when {
            targetUrl.contains(".mp4", ignoreCase = true) -> ".mp4"
            targetUrl.contains(".mkv", ignoreCase = true) -> ".mkv"
            targetUrl.contains(".webm", ignoreCase = true) -> ".webm"
            targetUrl.contains(".ts", ignoreCase = true) -> ".ts"
            targetUrl.contains(".mov", ignoreCase = true) -> ".mov"
            targetUrl.contains(".avi", ignoreCase = true) -> ".avi"
            else -> ".mp4"
        }

        val finalFileName = "NAFITV_${mediaItem.id.take(8)}_$safeTitle$ext"
        val finalFile = File(moviesDir, finalFileName)
        val partFileName = "$finalFileName.part"
        val partFile = File(moviesDir, partFileName)

        if (finalFile.exists() && finalFile.length() > 0L) {
            refreshDownloadedMoviesList(context)
            Toast.makeText(context, "মুভিটি ইতিমধ্যে সম্পূর্ণ ডাউনলোড করা আছে!", Toast.LENGTH_SHORT).show()
            return
        }

        val existingBytes = if (partFile.exists()) partFile.length() else 0L

        val initialProgress = MovieDownloadProgress(
            movieId = mediaItem.id,
            title = mediaItem.title,
            downloadedBytes = existingBytes,
            downloadedSizeFormatted = formatBytes(existingBytes),
            state = DownloadState.PENDING,
            targetUrl = targetUrl,
            mediaItem = mediaItem
        )
        activeProgressMap[mediaItem.id] = initialProgress
        _downloadsState.value = HashMap(activeProgressMap)

        // Launch Foreground Service for uninterrupted background downloads
        MovieDownloadService.startService(context)

        if (existingBytes > 0L) {
            Toast.makeText(context, "📥 '${mediaItem.title}' ডাউনলোড ${formatBytes(existingBytes)} থেকে পুনরায় শুরু হচ্ছে...", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "📥 '${mediaItem.title}' দ্রুত ডাউনলোড শুরু হচ্ছে...", Toast.LENGTH_SHORT).show()
        }
        onStarted?.invoke()

        var finalTotalBytes = 0L

        val job = coroutineScope.launch {
            var retryAttempt = 0
            val maxNetworkRetries = 5
            var isDownloadFinished = false

            while (isActive && retryAttempt <= maxNetworkRetries && !isDownloadFinished) {
                var currentPartLength = if (partFile.exists()) partFile.length() else 0L
                var response: okhttp3.Response? = null
                var randomAccessFile: RandomAccessFile? = null
                var inputStream: BufferedInputStream? = null

                try {
                    val requestBuilder = Request.Builder()
                        .url(targetUrl)
                        .addHeader("User-Agent", mediaItem.userAgent ?: "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                        .addHeader("Accept", "*/*")
                        .addHeader("Connection", "keep-alive")
                        .addHeader("Accept-Encoding", "identity")

                    mediaItem.referrer?.let { requestBuilder.addHeader("Referer", it) }
                    mediaItem.cookie?.let { requestBuilder.addHeader("Cookie", it) }
                    mediaItem.origin?.let { requestBuilder.addHeader("Origin", it) }
                    mediaItem.customHeaders?.forEach { (k, v) -> requestBuilder.addHeader(k, v) }

                    if (targetUrl.contains("movielinkbd", ignoreCase = true) || (targetUrl.contains("/media/") && targetUrl.contains("token="))) {
                        val host = try { Uri.parse(targetUrl).host } catch (_: Exception) { "fast.movielinkbd.app" }
                        if (mediaItem.referrer == null) requestBuilder.header("Referer", "https://$host/")
                        if (mediaItem.origin == null) requestBuilder.header("Origin", "https://$host")
                    }

                    // Send Range header if we have existing partial bytes
                    if (currentPartLength > 0L) {
                        requestBuilder.addHeader("Range", "bytes=$currentPartLength-")
                    }

                    response = downloadHttpClient.newCall(requestBuilder.build()).execute()
                    val responseCode = response.code

                    var isResuming = false
                    var streamContentLength = 0L

                    if (responseCode == 206) {
                        // 206 Partial Content: Server resumes from currentPartLength!
                        isResuming = true
                        val contentRange = response.header("Content-Range")
                        finalTotalBytes = parseTotalFromContentRange(contentRange)
                            ?: (currentPartLength + (response.body?.contentLength() ?: 0L))
                        streamContentLength = response.body?.contentLength() ?: 0L
                    } else if (responseCode == 200) {
                        // Server does not support range, streaming from 0
                        isResuming = false
                        currentPartLength = 0L
                        finalTotalBytes = response.body?.contentLength() ?: 0L
                        streamContentLength = finalTotalBytes
                    } else if (responseCode == 416) {
                        // Requested Range Not Satisfiable: partFile may already be complete or invalid
                        response.close()
                        if (currentPartLength > 0L) {
                            partFile.delete()
                            currentPartLength = 0L
                            continue
                        } else {
                            throw Exception("সার্ভার রেসপন্স ত্রুটি: HTTP 416 (Range Not Satisfiable)")
                        }
                    } else {
                        throw Exception("সার্ভার রেসপন্স কোড: $responseCode")
                    }

                    val body = response.body ?: throw Exception("সার্ভার থেকে ডাটা পাওয়া যায়নি")

                    randomAccessFile = RandomAccessFile(partFile, "rw")
                    if (isResuming) {
                        randomAccessFile.seek(currentPartLength)
                    } else {
                        randomAccessFile.setLength(0L)
                    }

                    // Save paused task metadata so app can resume even after restart
                    savePausedTask(context, mediaItem, targetUrl, partFile.absolutePath, currentPartLength, finalTotalBytes)

                    val bufferSize = 128 * 1024 // 128 KB buffer for optimal memory & I/O balance
                    inputStream = BufferedInputStream(body.byteStream(), bufferSize)
                    val buffer = ByteArray(bufferSize)
                    var bytesRead: Int
                    var totalDownloaded = currentPartLength

                    var lastUiUpdateTime = System.currentTimeMillis()
                    var lastSpeedCalcTime = System.currentTimeMillis()
                    var lastBytesForSpeed = currentPartLength
                    var currentSpeedFormatted = "0 KB/s"

                    // Reset retry counter on successful connection
                    retryAttempt = 0

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        if (!isActive) {
                            throw CancellationException("ব্যবহারকারী ডাউনলোড স্থগিত করেছেন")
                        }

                        randomAccessFile.write(buffer, 0, bytesRead)
                        totalDownloaded += bytesRead

                        val now = System.currentTimeMillis()

                        // Calculate speed every 800ms
                        if (now - lastSpeedCalcTime >= 800) {
                            val bytesDiff = totalDownloaded - lastBytesForSpeed
                            val durationSec = (now - lastSpeedCalcTime) / 1000.0
                            val speedVal = if (durationSec > 0) (bytesDiff / durationSec).toLong() else 0L
                            currentSpeedFormatted = formatSpeed(speedVal)
                            lastBytesForSpeed = totalDownloaded
                            lastSpeedCalcTime = now
                        }

                        // Update UI and Notification every 250ms or when finished
                        if (now - lastUiUpdateTime >= 250 || (finalTotalBytes > 0 && totalDownloaded >= finalTotalBytes)) {
                            lastUiUpdateTime = now
                            val progressFloat = if (finalTotalBytes > 0) {
                                (totalDownloaded.toFloat() / finalTotalBytes.toFloat()).coerceIn(0f, 1f)
                            } else 0f
                            val progressPercent = (progressFloat * 100).toInt()
                            val dlFormatted = formatBytes(totalDownloaded)
                            val totFormatted = if (finalTotalBytes > 0) formatBytes(finalTotalBytes) else "অজানা"

                            val prog = MovieDownloadProgress(
                                movieId = mediaItem.id,
                                title = mediaItem.title,
                                progress = progressFloat,
                                progressPercent = progressPercent,
                                downloadedBytes = totalDownloaded,
                                totalBytes = finalTotalBytes,
                                downloadedSizeFormatted = dlFormatted,
                                totalSizeFormatted = totFormatted,
                                speedFormatted = currentSpeedFormatted,
                                state = DownloadState.DOWNLOADING,
                                errorMessage = null,
                                targetUrl = targetUrl,
                                mediaItem = mediaItem
                            )
                            activeProgressMap[mediaItem.id] = prog
                            _downloadsState.value = HashMap(activeProgressMap)

                            showProgressNotification(
                                context = context,
                                notifId = notifId,
                                title = mediaItem.title,
                                progress = progressPercent,
                                statusText = "$dlFormatted / $totFormatted • ⚡ $currentSpeedFormatted ($progressPercent%)",
                                movieId = mediaItem.id
                            )
                        }
                    }

                    // Flush all remaining bytes to storage safely
                    randomAccessFile.fd.sync()
                    isDownloadFinished = true
                    break

                } catch (e: CancellationException) {
                    // User explicitly paused or cancelled
                    throw e
                } catch (e: Exception) {
                    retryAttempt++
                    if (retryAttempt <= maxNetworkRetries && isActive) {
                        val waitSec = retryAttempt * 2
                        val currentBytes = if (partFile.exists()) partFile.length() else 0L
                        val statusText = "ইন্টারনেট বিচ্ছিন্ন। $waitSec সেকেন্ডে পুনঃসংযোগ চেষ্টা ($retryAttempt/$maxNetworkRetries)..."

                        activeProgressMap[mediaItem.id]?.let { cur ->
                            activeProgressMap[mediaItem.id] = cur.copy(
                                downloadedBytes = currentBytes,
                                speedFormatted = statusText,
                                state = DownloadState.DOWNLOADING
                            )
                            _downloadsState.value = HashMap(activeProgressMap)
                        }

                        delay(waitSec * 1000L)
                    } else {
                        throw e
                    }
                } finally {
                    try { inputStream?.close() } catch (_: Exception) {}
                    try { randomAccessFile?.close() } catch (_: Exception) {}
                    try { response?.close() } catch (_: Exception) {}
                }
            }

            if (isDownloadFinished && partFile.exists()) {
                // Atomic rename of .part to final file
                if (finalFile.exists()) {
                    finalFile.delete()
                }
                val renamed = partFile.renameTo(finalFile)
                val actualFile = if (renamed) finalFile else partFile
                val actualFileSize = actualFile.length()

                // Remove from paused tasks since download is complete
                removePausedTask(context, mediaItem.id)

                val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
                val downloadedMovie = DownloadedMovie(
                    id = mediaItem.id,
                    title = mediaItem.title,
                    category = mediaItem.category,
                    logoUrl = mediaItem.logoUrl,
                    year = mediaItem.year,
                    quality = mediaItem.quality,
                    downloadUrl = targetUrl,
                    localFilePath = actualFile.absolutePath,
                    fileSizeBytes = actualFileSize,
                    fileSizeFormatted = formatBytes(actualFileSize),
                    downloadDateFormatted = dateFormat.format(Date()),
                    downloadTimestamp = System.currentTimeMillis()
                )
                saveDownloadedMovie(context, downloadedMovie)

                val completedProg = MovieDownloadProgress(
                    movieId = mediaItem.id,
                    title = mediaItem.title,
                    progress = 1f,
                    progressPercent = 100,
                    downloadedBytes = actualFileSize,
                    totalBytes = actualFileSize,
                    downloadedSizeFormatted = formatBytes(actualFileSize),
                    totalSizeFormatted = formatBytes(actualFileSize),
                    speedFormatted = "সম্পন্ন",
                    state = DownloadState.COMPLETED,
                    targetUrl = targetUrl,
                    mediaItem = mediaItem
                )
                activeProgressMap[mediaItem.id] = completedProg
                _downloadsState.value = HashMap(activeProgressMap)

                showCompletedNotification(context, notifId, mediaItem.title, formatBytes(actualFileSize))

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "✅ '${mediaItem.title}' ডাউনলোড সফল হয়েছে!", Toast.LENGTH_LONG).show()
                    onComplete?.invoke(downloadedMovie)
                }
            }
        }

        job.invokeOnCompletion { cause ->
            activeDownloadJobs.remove(mediaItem.id)

            if (cause is CancellationException) {
                // Keep partial file for resuming!
                val currentBytes = if (partFile.exists()) partFile.length() else 0L
                savePausedTask(context, mediaItem, targetUrl, partFile.absolutePath, currentBytes, finalTotalBytes)

                val pausedProg = MovieDownloadProgress(
                    movieId = mediaItem.id,
                    title = mediaItem.title,
                    progress = activeProgressMap[mediaItem.id]?.progress ?: 0f,
                    progressPercent = activeProgressMap[mediaItem.id]?.progressPercent ?: 0,
                    downloadedBytes = currentBytes,
                    totalBytes = activeProgressMap[mediaItem.id]?.totalBytes ?: finalTotalBytes,
                    downloadedSizeFormatted = formatBytes(currentBytes),
                    totalSizeFormatted = activeProgressMap[mediaItem.id]?.totalSizeFormatted ?: "0 MB",
                    speedFormatted = "স্থগিত",
                    state = DownloadState.PAUSED,
                    errorMessage = "ডাউনলোড স্থগিত রাখা হয়েছে। পুনরায় চালু বাটনে চাপলে এখান থেকেই শুরু হবে।",
                    targetUrl = targetUrl,
                    mediaItem = mediaItem
                )
                activeProgressMap[mediaItem.id] = pausedProg
                _downloadsState.value = HashMap(activeProgressMap)

                showPausedNotification(
                    context = context,
                    notifId = notifId,
                    title = mediaItem.title,
                    downloadedSize = formatBytes(currentBytes),
                    percent = pausedProg.progressPercent,
                    movieId = mediaItem.id
                )
            } else if (cause != null) {
                // Failure after all retries exhausted: KEEP partFile for resuming!
                val currentBytes = if (partFile.exists()) partFile.length() else 0L
                val totalBytes = activeProgressMap[mediaItem.id]?.totalBytes ?: finalTotalBytes
                val percent = if (totalBytes > 0) ((currentBytes * 100) / totalBytes).toInt() else 0

                savePausedTask(context, mediaItem, targetUrl, partFile.absolutePath, currentBytes, totalBytes)

                val failProg = MovieDownloadProgress(
                    movieId = mediaItem.id,
                    title = mediaItem.title,
                    progress = if (totalBytes > 0) (currentBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f,
                    progressPercent = percent,
                    downloadedBytes = currentBytes,
                    totalBytes = totalBytes,
                    downloadedSizeFormatted = formatBytes(currentBytes),
                    totalSizeFormatted = if (totalBytes > 0) formatBytes(totalBytes) else "অজানা",
                    speedFormatted = "স্থগিত (ইন্টারনেট সমস্যা)",
                    state = DownloadState.PAUSED,
                    errorMessage = "ইন্টারনেট সংযোগ বিচ্ছিন্ন হয়েছে। পুনরায় শুরু করলে ${formatBytes(currentBytes)} থেকেই চলবে।",
                    targetUrl = targetUrl,
                    mediaItem = mediaItem
                )
                activeProgressMap[mediaItem.id] = failProg
                _downloadsState.value = HashMap(activeProgressMap)

                showPausedNotification(
                    context = context,
                    notifId = notifId,
                    title = mediaItem.title,
                    downloadedSize = formatBytes(currentBytes),
                    percent = percent,
                    movieId = mediaItem.id
                )

                coroutineScope.launch(Dispatchers.Main) {
                    val errMsg = "ইন্টারনেট সমস্যার কারণে ডাউনলোড স্থগিত রাখা হয়েছে (${formatBytes(currentBytes)})। পুনরায় শুরু বাটনে চাপলে এখান থেকেই চলবে।"
                    Toast.makeText(context, errMsg, Toast.LENGTH_LONG).show()
                    onError?.invoke(errMsg)
                }
            }
        }

        activeDownloadJobs[mediaItem.id] = job
    }

    private fun parseTotalFromContentRange(contentRange: String?): Long? {
        if (contentRange.isNullOrBlank()) return null
        return try {
            val slashIndex = contentRange.lastIndexOf('/')
            if (slashIndex != -1 && slashIndex + 1 < contentRange.length) {
                val totalStr = contentRange.substring(slashIndex + 1).trim()
                if (totalStr != "*") totalStr.toLong() else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    // --- Paused Task Persistence (survives app kill and phone reboot) ---

    private fun savePausedTask(
        context: Context,
        mediaItem: MediaItem,
        targetUrl: String,
        partFilePath: String,
        downloadedBytes: Long,
        totalBytes: Long
    ) {
        try {
            val prefs = getPrefs(context)
            val jsonStr = prefs.getString(KEY_PAUSED_TASKS, "[]") ?: "[]"
            val array = JSONArray(jsonStr)
            val newArray = JSONArray()

            val taskObj = JSONObject().apply {
                put("id", mediaItem.id)
                put("title", mediaItem.title)
                put("category", mediaItem.category)
                put("logoUrl", mediaItem.logoUrl ?: "")
                put("year", mediaItem.year ?: "")
                put("quality", mediaItem.quality)
                put("downloadUrl", targetUrl)
                put("partFilePath", partFilePath)
                put("downloadedBytes", downloadedBytes)
                put("totalBytes", totalBytes)
            }
            newArray.put(taskObj)

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                if (obj.optString("id") != mediaItem.id) {
                    newArray.put(obj)
                }
            }
            prefs.edit().putString(KEY_PAUSED_TASKS, newArray.toString()).apply()
        } catch (_: Exception) {}
    }

    private fun removePausedTask(context: Context, movieId: String) {
        try {
            val prefs = getPrefs(context)
            val jsonStr = prefs.getString(KEY_PAUSED_TASKS, "[]") ?: "[]"
            val array = JSONArray(jsonStr)
            val newArray = JSONArray()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                if (obj.optString("id") != movieId) {
                    newArray.put(obj)
                }
            }
            prefs.edit().putString(KEY_PAUSED_TASKS, newArray.toString()).apply()
        } catch (_: Exception) {}
    }

    private fun getStoredPausedTasks(context: Context): List<DownloadedMovie> {
        return try {
            val prefs = getPrefs(context)
            val jsonStr = prefs.getString(KEY_PAUSED_TASKS, "[]") ?: "[]"
            val array = JSONArray(jsonStr)
            val list = mutableListOf<DownloadedMovie>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    DownloadedMovie(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        category = obj.optString("category", "Movie"),
                        logoUrl = obj.optString("logoUrl").takeIf { it.isNotBlank() },
                        year = obj.optString("year").takeIf { it.isNotBlank() },
                        quality = obj.optString("quality", "HD"),
                        downloadUrl = obj.optString("downloadUrl"),
                        localFilePath = obj.optString("partFilePath"),
                        fileSizeBytes = obj.optLong("downloadedBytes"),
                        fileSizeFormatted = formatBytes(obj.optLong("downloadedBytes"))
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun restorePausedTasks(context: Context) {
        try {
            val stored = getStoredPausedTasks(context)
            for (task in stored) {
                val file = File(task.localFilePath)
                if (file.exists() && file.length() > 0L) {
                    val partBytes = file.length()
                    if (!activeProgressMap.containsKey(task.id)) {
                        val prog = MovieDownloadProgress(
                            movieId = task.id,
                            title = task.title,
                            progress = 0.5f,
                            progressPercent = 50,
                            downloadedBytes = partBytes,
                            totalBytes = 0L,
                            downloadedSizeFormatted = formatBytes(partBytes),
                            totalSizeFormatted = "সংরক্ষিত",
                            speedFormatted = "স্থগিত (Resume করুন)",
                            state = DownloadState.PAUSED,
                            targetUrl = task.downloadUrl,
                            mediaItem = task.toMediaItem()
                        )
                        activeProgressMap[task.id] = prog
                    }
                }
            }
            _downloadsState.value = HashMap(activeProgressMap)
        } catch (_: Exception) {}
    }

    // --- System Notifications ---

    private fun showProgressNotification(
        context: Context,
        notifId: Int,
        title: String,
        progress: Int,
        statusText: String,
        movieId: String
    ) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notifId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Pause action intent
            val pauseIntent = Intent(context, MovieDownloadService::class.java).apply {
                action = MovieDownloadService.ACTION_PAUSE_DOWNLOAD
                putExtra(MovieDownloadService.EXTRA_MOVIE_ID, movieId)
            }
            val pausePendingIntent = PendingIntent.getService(
                context,
                notifId + 100,
                pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, NOTIF_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("মুভি ডাউনলোড হচ্ছে: $title")
                .setContentText(statusText)
                .setProgress(100, progress, progress <= 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(pendingIntent)
                .addAction(R.mipmap.ic_launcher, "⏸️ পজ করুন", pausePendingIntent)

            NotificationManagerCompat.from(context).notify(notifId, builder.build())
        } catch (_: Exception) {}
    }

    private fun showPausedNotification(
        context: Context,
        notifId: Int,
        title: String,
        downloadedSize: String,
        percent: Int,
        movieId: String
    ) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notifId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Resume action intent
            val resumeIntent = Intent(context, MovieDownloadService::class.java).apply {
                action = MovieDownloadService.ACTION_RESUME_DOWNLOAD
                putExtra(MovieDownloadService.EXTRA_MOVIE_ID, movieId)
            }
            val resumePendingIntent = PendingIntent.getService(
                context,
                notifId + 200,
                resumeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, NOTIF_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("⏸️ মুভি ডাউনলোড স্থগিত: $title")
                .setContentText("সংরক্ষিত: $downloadedSize ($percent%) • পুনরায় শুরু করতে চাপুন")
                .setOngoing(false)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .addAction(R.mipmap.ic_launcher, "▶️ পুনরায় চালু করুন", resumePendingIntent)

            NotificationManagerCompat.from(context).notify(notifId, builder.build())
        } catch (_: Exception) {}
    }

    private fun showCompletedNotification(
        context: Context,
        notifId: Int,
        title: String,
        fileSizeFormatted: String
    ) {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                notifId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, NOTIF_CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("✅ ডাউনলোড সম্পন্ন: $title")
                .setContentText("সাইজ: $fileSizeFormatted • অফলাইনে দেখার জন্য প্রস্তুত")
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)

            NotificationManagerCompat.from(context).notify(notifId, builder.build())
        } catch (_: Exception) {}
    }

    fun cancelNotification(context: Context, notifId: Int) {
        try {
            NotificationManagerCompat.from(context).cancel(notifId)
        } catch (_: Exception) {}
    }

    // --- Saved Movies Repository ---

    fun getDownloadedMovies(context: Context): List<DownloadedMovie> {
        val prefs = getPrefs(context)
        val jsonStr = prefs.getString(KEY_DOWNLOADED_MOVIES, null) ?: return emptyList()
        return try {
            val array = JSONArray(jsonStr)
            val list = mutableListOf<DownloadedMovie>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    DownloadedMovie(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        category = obj.optString("category", "Movie"),
                        logoUrl = obj.optString("logoUrl").takeIf { it.isNotBlank() },
                        year = obj.optString("year").takeIf { it.isNotBlank() },
                        quality = obj.optString("quality", "HD"),
                        downloadUrl = obj.optString("downloadUrl"),
                        localFilePath = obj.optString("localFilePath"),
                        fileSizeBytes = obj.optLong("fileSizeBytes"),
                        fileSizeFormatted = obj.optString("fileSizeFormatted"),
                        downloadDateFormatted = obj.optString("downloadDateFormatted"),
                        downloadTimestamp = obj.optLong("downloadTimestamp", System.currentTimeMillis())
                    )
                )
            }
            list.filter { it.fileExists }.sortedByDescending { it.downloadTimestamp }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveDownloadedMovie(context: Context, movie: DownloadedMovie) {
        val existing = getDownloadedMovies(context).toMutableList()
        existing.removeAll { it.id == movie.id }
        existing.add(0, movie)
        persistDownloadedMoviesList(context, existing)
    }

    fun deleteDownloadedMovie(context: Context, movieId: String): Boolean {
        val existing = getDownloadedMovies(context).toMutableList()
        val movie = existing.firstOrNull { it.id == movieId } ?: return false

        try {
            val file = File(movie.localFilePath)
            if (file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {}

        existing.removeAll { it.id == movieId }
        persistDownloadedMoviesList(context, existing)
        return true
    }

    fun refreshDownloadedMoviesList(context: Context) {
        val movies = getDownloadedMovies(context)
        _downloadedMoviesFlow.value = movies
    }

    fun getDownloadedMovie(context: Context, movieId: String): DownloadedMovie? {
        return getDownloadedMovies(context).firstOrNull { it.id == movieId && it.fileExists }
    }

    fun getDownloadedFile(context: Context, movieId: String): File? {
        val movie = getDownloadedMovie(context, movieId) ?: return null
        val file = File(movie.localFilePath)
        return if (file.exists()) file else null
    }

    private fun persistDownloadedMoviesList(context: Context, list: List<DownloadedMovie>) {
        try {
            val array = JSONArray()
            for (m in list) {
                val obj = JSONObject().apply {
                    put("id", m.id)
                    put("title", m.title)
                    put("category", m.category)
                    put("logoUrl", m.logoUrl ?: "")
                    put("year", m.year ?: "")
                    put("quality", m.quality)
                    put("downloadUrl", m.downloadUrl)
                    put("localFilePath", m.localFilePath)
                    put("fileSizeBytes", m.fileSizeBytes)
                    put("fileSizeFormatted", m.fileSizeFormatted)
                    put("downloadDateFormatted", m.downloadDateFormatted)
                    put("downloadTimestamp", m.downloadTimestamp)
                }
                array.put(obj)
            }
            getPrefs(context).edit().putString(KEY_DOWNLOADED_MOVIES, array.toString()).apply()
            _downloadedMoviesFlow.value = list
        } catch (_: Exception) {}
    }

    // --- External Browser / DownloadManager Fallback ---

    fun openExternalOrSystemDownload(context: Context, mediaItem: MediaItem, customUrl: String? = null) {
        try {
            val url = customUrl?.trim()?.takeIf { it.isNotBlank() } ?: mediaItem.streamUrl.trim()
            if (url.isBlank()) {
                Toast.makeText(context, "ডাউনলোড লিংক নেই!", Toast.LENGTH_SHORT).show()
                return
            }

            val safeTitle = mediaItem.title.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                setTitle("NAFI TV: ${mediaItem.title}")
                setDescription("মুভি ডাউনলোড হচ্ছে...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "NAFITV_${safeTitle}.mp4")
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                mediaItem.userAgent?.let { addRequestHeader("User-Agent", it) }
                mediaItem.referrer?.let { addRequestHeader("Referer", it) }
            }

            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (dm != null) {
                dm.enqueue(request)
                Toast.makeText(context, "📥 সিস্টেম ডাউনলোড ম্যানেজারে যুক্ত হয়েছে...", Toast.LENGTH_SHORT).show()
            } else {
                openBrowserDownload(context, url)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            openBrowserDownload(context, mediaItem.streamUrl)
        }
    }

    fun openBrowserDownload(context: Context, url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Toast.makeText(context, "ব্রাউজারে ডাউনলোড লিংক খোলা হচ্ছে...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "ব্রাউজার খোলা সম্ভব হয়নি: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // --- Formatting Utilities ---

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 MB"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
            else -> String.format(Locale.US, "%.0f KB", kb)
        }
    }

    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) return "0 KB/s"
        val kb = bytesPerSec / 1024.0
        val mb = kb / 1024.0
        return if (mb >= 1.0) {
            String.format(Locale.US, "%.2f MB/s", mb)
        } else {
            String.format(Locale.US, "%.0f KB/s", kb)
        }
    }

    fun getTotalDownloadedStorageSize(context: Context): String {
        val list = getDownloadedMovies(context)
        val totalBytes = list.sumOf { it.fileSizeBytes }
        return formatBytes(totalBytes)
    }
}
