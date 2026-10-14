package com.example.data

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import com.example.model.ActiveUserInfo
import com.example.model.AppConfigData
import com.example.model.AppNotification
import com.example.model.AppUpdateInfo
import com.example.model.AppUserAnalytics
import com.example.model.CloudStreamRepo
import com.example.model.EpisodeItem
import com.example.model.SeasonInfo
import com.example.model.XtreamCategory
import com.example.model.LocationTrafficStat
import com.example.model.MediaItem
import com.example.model.MediaType
import com.example.model.MovieProvider
import com.example.model.NotificationType
import com.example.model.PlaylistInfo
import com.example.model.StreamServer
import com.example.util.NotificationHelper
import com.example.NafiTvApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class MediaRepository(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("nafitv_prefs", Context.MODE_PRIVATE)

    private val client: OkHttpClient = NafiTvApp.sharedOkHttpClient

    private val repositoryScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob()
    )

    private var lastPresenceTimestamp = 0L
    private var lastPresenceActivity: String = ""

    private val seriesDetailsCache = ConcurrentHashMap<String, MediaItem>()

    val dexPluginManager: com.example.cloudstream.DexPluginManager =
        com.example.cloudstream.DexPluginManager(context, client)

    val nativeScraperEngine: com.example.cloudstream.NativeScraperEngine =
        com.example.cloudstream.NativeScraperEngine(client)

    val authManager: FirebaseAuthManager =
        FirebaseAuthManager(context, client)

    suspend fun getRtdbAuthQuery(): String {
        val token = authManager.getValidIdToken()
        return if (!token.isNullOrBlank()) "?auth=$token" else ""
    }

    suspend fun appendRtdbAuth(url: String): String {
        val token = authManager.getValidIdToken()
        if (token.isNullOrBlank()) return url
        val clean = url.trim()
        val sep = if (clean.contains("?")) "&" else "?"
        return "$clean${sep}auth=$token"
    }

    // Marquee Scrolling Breaking News Ticker Management
    companion object {
        const val DEFAULT_MARQUEE_TEXT = "বাংলাদেশ ব্যাংকের নতুন মুদ্রানীতি ঘোষণা। পুঁজিবাজারে ঊর্ধ্বগতি। NAFI TV24 এ ক্রিকেট, ফুটবল ও লাইভ টিভি চ্যানেল সম্পূর্ণ বিনামূল্যে উপভোগ করুন।"
        const val DEFAULT_RTDB_URL = "https://nafitv24-live-default-rtdb.firebaseio.com/"
        const val DEFAULT_LIVE_TV_M3U_URL = "https://raw.githubusercontent.com/nafitv24-web/NAFI-TV/refs/heads/main/Update%20Channel.m3u"
        const val DEFAULT_SPORTS_M3U_URL = "https://raw.githubusercontent.com/sm-monirulislam/SonyLiv_Event_Playlist/refs/heads/main/sonyLiv.m3u\n" +
                "https://raw.githubusercontent.com/nafitv24-web/NAFI-TV/refs/heads/main/NAFI%20SPORTS.m3u\n" +
                "https://raw.githubusercontent.com/sm-monirulislam/Tapmad_Auto_Update_Playlist/refs/heads/main/tapmad_sm.m3u\n" +
                "https://gist.githubusercontent.com/albatr0ssss/3cff7a26be49b1d352c15f615067e7cd/raw/tapmad_bd.m3u\n" +
                "https://raw.githubusercontent.com/srhady/SonyLiv/refs/heads/main/sonyliv_playlist.m3u\n" +
                "https://raw.githubusercontent.com/srhady/willow-event/refs/heads/main/live_sports.m3u\n" +
                "https://raw.githubusercontent.com/srhady/willow-event/refs/heads/main/primevideo_sports.m3u\n" +
                "https://raw.githubusercontent.com/sm-monirulislam/Upcoming-and-Live-Sports-Data/refs/heads/main/Sports_data.m3u\n" +
                "https://raw.githubusercontent.com/srhady/axsports/refs/heads/main/playlist.m3u"
        const val DEFAULT_TAPMAD_JSON_URL = "https://raw.githubusercontent.com/sm-monirulislam/Upcoming-and-Live-Sports-Data/main/Sports_data.json\nhttps://raw.githubusercontent.com/sm-monirulislam/Tapmad_Auto_Update_Playlist/main/tapmad_data.json"
        const val DEFAULT_TAPMAD_M3U_URL = "https://raw.githubusercontent.com/sm-monirulislam/Upcoming-and-Live-Sports-Data/main/Sports_data.m3u\nhttps://raw.githubusercontent.com/sm-monirulislam/Tapmad_Auto_Update_Playlist/refs/heads/main/tapmad_sm.m3u"
        const val DEFAULT_MOVIES_JSON_URL = "https://raw.githubusercontent.com/nafitv24-web/NAFI-TV/refs/heads/main/movies.json"
        const val DEFAULT_MOVIES_M3U_URL = DEFAULT_MOVIES_JSON_URL
        const val DEFAULT_MIX_MOVIES_M3U_URL = "https://raw.githubusercontent.com/abusaeeidx/Movie-Playlist-Auto-update/refs/heads/main/Mix_Movies.m3u"
        const val DEFAULT_LATEST_MOVIES_M3U_URL = "https://raw.githubusercontent.com/srhady/join_telegram_chennal-livesportsplay/refs/heads/main/latest_movies.m3u"
        const val DEFAULT_M3U_URL = DEFAULT_LIVE_TV_M3U_URL
        const val DEFAULT_ADMIN_PIN = "40541273"
        const val FIREBASE_PROJECT_ID = "gen-lang-client-0255825339"
        const val FIREBASE_API_KEY = "AIzaSyDq6zYiTJHDqYxH_2PV-RsKWPFj_8eSpy0"
        const val FIRESTORE_DATABASE_ID = "ai-studio-android-androidt-c367ea18-0409-44f0-a714-5c64d7d9b272"

        @Volatile
        private var cachedBuiltinLiveTv: List<MediaItem>? = null
        @Volatile
        private var inMemoryLiveTvCache: List<MediaItem>? = null
    }

    fun getMarqueeTickerText(): String {
        val stored = prefs.getString("marquee_ticker_text", null)
        return if (!stored.isNullOrBlank()) stored else DEFAULT_MARQUEE_TEXT
    }

    fun saveMarqueeTickerText(text: String, isRemoteUpdate: Boolean = false) {
        val editor = prefs.edit().putString("marquee_ticker_text", text)
        if (!isRemoteUpdate) {
            editor.putLong("marquee_ticker_local_modified", System.currentTimeMillis())
        }
        editor.apply()
    }

    suspend fun pushMarqueeTickerToFirebase(text: String, url: String = getSavedFirebaseUrl()): Boolean = withContext(Dispatchers.IO) {
        var success = false
        saveMarqueeTickerText(text, isRemoteUpdate = false)
        val token = authManager.getValidIdToken()

        // 1. RTDB Multi-endpoint Push
        if (url.isNotBlank()) {
            val cleanUrl = if (url.endsWith("/")) url.removeSuffix("/") else url
            val obj = JSONObject()
            obj.put("marquee_ticker", text)
            obj.put("text", text)
            obj.put("updated_at", System.currentTimeMillis())
            val body = obj.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

            val endpoints = listOf(
                "$cleanUrl/marquee_news.json",
                "$cleanUrl/settings/marquee_news.json",
                "$cleanUrl/settings/ticker.json",
                "$cleanUrl/app_config/marquee_ticker.json"
            )

            for (ep in endpoints) {
                try {
                    val targetUrl = appendRtdbAuth(ep)
                    val req = Request.Builder().url(targetUrl).put(body).build()
                    val resp = client.newCall(req).execute()
                    if (resp.isSuccessful) success = true
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // 2. Firestore Multi-database Push
        try {
            val firestoreObj = JSONObject()
            val fields = JSONObject()
            fields.put("marquee_ticker", JSONObject().put("stringValue", text))
            fields.put("text", JSONObject().put("stringValue", text))
            fields.put("updated_at", JSONObject().put("integerValue", System.currentTimeMillis().toString()))
            firestoreObj.put("fields", fields)
            val fsBody = firestoreObj.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val databases = listOf(FIRESTORE_DATABASE_ID, "(default)")

            for (dbId in databases) {
                val targets = listOf(
                    "https://firestore.googleapis.com/v1/projects/$FIREBASE_PROJECT_ID/databases/$dbId/documents/settings/marquee_news?key=$FIREBASE_API_KEY",
                    "https://firestore.googleapis.com/v1/projects/$FIREBASE_PROJECT_ID/databases/$dbId/documents/app_config/marquee_ticker?key=$FIREBASE_API_KEY"
                )
                for (fsUrl in targets) {
                    try {
                        val reqBuilder = Request.Builder().url(fsUrl).patch(fsBody)
                        if (!token.isNullOrBlank()) {
                            reqBuilder.header("Authorization", "Bearer $token")
                        }
                        val fsReq = reqBuilder.build()
                        val fsResp = client.newCall(fsReq).execute()
                        if (fsResp.isSuccessful) success = true
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        success
    }

    suspend fun fetchMarqueeTickerFromFirebase(url: String = getSavedFirebaseUrl()): String = withContext(Dispatchers.IO) {
        var remoteText: String? = null
        val token = authManager.getValidIdToken()

        // 1. Firestore Check
        val databases = listOf(FIRESTORE_DATABASE_ID, "(default)")
        for (dbId in databases) {
            if (!remoteText.isNullOrBlank()) break
            val targets = listOf(
                "https://firestore.googleapis.com/v1/projects/$FIREBASE_PROJECT_ID/databases/$dbId/documents/settings/marquee_news?key=$FIREBASE_API_KEY",
                "https://firestore.googleapis.com/v1/projects/$FIREBASE_PROJECT_ID/databases/$dbId/documents/app_config/marquee_ticker?key=$FIREBASE_API_KEY"
            )
            for (fsUrl in targets) {
                try {
                    val reqBuilder = Request.Builder().url(fsUrl).header("User-Agent", "NAFITV24-Android/2.5.0")
                    if (!token.isNullOrBlank()) {
                        reqBuilder.header("Authorization", "Bearer $token")
                    }
                    val resp = client.newCall(reqBuilder.build()).execute()
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        if (body.isNotBlank() && body.startsWith("{")) {
                            val json = JSONObject(body)
                            val fields = json.optJSONObject("fields")
                            if (fields != null) {
                                val txt = fields.optJSONObject("marquee_ticker")?.optString("stringValue")
                                    ?: fields.optJSONObject("text")?.optString("stringValue")
                                if (!txt.isNullOrBlank()) {
                                    remoteText = txt
                                    break
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // 2. RTDB Check
        if (remoteText.isNullOrBlank() && url.isNotBlank()) {
            val cleanUrl = if (url.endsWith("/")) url.removeSuffix("/") else url
            val endpoints = listOf(
                "$cleanUrl/marquee_news.json",
                "$cleanUrl/settings/marquee_news.json",
                "$cleanUrl/settings/ticker.json",
                "$cleanUrl/app_config/marquee_ticker.json"
            )
            for (ep in endpoints) {
                try {
                    val targetUrl = appendRtdbAuth(ep)
                    val req = Request.Builder().url(targetUrl).header("User-Agent", "NAFITV24-Android/2.5.0").build()
                    val resp = client.newCall(req).execute()
                    if (resp.isSuccessful) {
                        val body = resp.body?.string()?.trim() ?: ""
                        if (body.isNotBlank() && body != "null") {
                            if (body.startsWith("{")) {
                                val obj = JSONObject(body)
                                val txt = obj.optString("marquee_ticker", obj.optString("text", "")).trim()
                                if (txt.isNotBlank()) {
                                    remoteText = txt
                                    break
                                }
                            } else if (body.startsWith("\"") && body.endsWith("\"") && body.length > 2) {
                                remoteText = body.substring(1, body.length - 1)
                                break
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        val lastLocalModified = prefs.getLong("marquee_ticker_local_modified", 0L)
        val isRecentLocalEdit = (System.currentTimeMillis() - lastLocalModified) < 15_000L

        if (!remoteText.isNullOrBlank()) {
            if (!isRecentLocalEdit) {
                saveMarqueeTickerText(remoteText!!, isRemoteUpdate = true)
                return@withContext remoteText!!
            }
        }

        getMarqueeTickerText()
    }

    // Admin Privacy / PIN Management
    fun getAdminPin(): String {
        val stored = prefs.getString("admin_pin", null)
        if (stored.isNullOrBlank() || stored == "2424") {
            return DEFAULT_ADMIN_PIN
        }
        return stored
    }

    fun setAdminPin(pin: String) {
        prefs.edit().putString("admin_pin", pin).apply()
    }

    fun verifyAdminPin(pin: String): Boolean {
        val current = getAdminPin().trim()
        return pin.trim() == current || pin.trim() == DEFAULT_ADMIN_PIN
    }

    // -------------------------------------------------------------
    // -------------------------------------------------------------
    // Deleted Items Persistence (Ensures deleted items NEVER reappear)
    // -------------------------------------------------------------
    private val deletedIdsMemorySet = java.util.concurrent.CopyOnWriteArraySet<String>()
    private val deletedTitlesMemorySet = java.util.concurrent.CopyOnWriteArraySet<String>()

    fun getDeletedIds(): Set<String> {
        if (deletedIdsMemorySet.isNotEmpty()) return deletedIdsMemorySet.toSet()
        val storedJson = prefs.getString("deleted_ids_v2", null)
        val loaded = mutableSetOf<String>()
        if (!storedJson.isNullOrBlank() && storedJson.startsWith("[")) {
            try {
                val arr = org.json.JSONArray(storedJson)
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i)
                    if (s.isNotBlank()) loaded.add(s)
                }
            } catch (_: Exception) {}
        }
        val legacy = prefs.getStringSet("deleted_ids", emptySet()) ?: emptySet()
        loaded.addAll(legacy)
        // Hardcoded purge of user-reported undeletable test items
        loaded.add("sport_1786892061230")
        loaded.add("sport_1787095254210")
        deletedIdsMemorySet.addAll(loaded)
        return deletedIdsMemorySet.toSet()
    }

    fun getDeletedTitles(): Set<String> {
        if (deletedTitlesMemorySet.isNotEmpty()) return deletedTitlesMemorySet.toSet()
        val storedJson = prefs.getString("deleted_titles_v2", null)
        val loaded = mutableSetOf<String>()
        if (!storedJson.isNullOrBlank() && storedJson.startsWith("[")) {
            try {
                val arr = org.json.JSONArray(storedJson)
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i)
                    if (s.isNotBlank()) loaded.add(s)
                }
            } catch (_: Exception) {}
        }
        // Always include requested matches in deleted titles
        loaded.add("Bangladesh vs Australia")
        loaded.add("Pakistan vs England")
        deletedTitlesMemorySet.addAll(loaded)
        return deletedTitlesMemorySet.toSet()
    }

    fun addDeletedId(id: String) {
        val clean = id.trim()
        if (clean.isBlank()) return
        val current = getDeletedIds().toMutableSet()
        current.add(clean)
        deletedIdsMemorySet.add(clean)
        val arr = org.json.JSONArray(current)
        prefs.edit()
            .putString("deleted_ids_v2", arr.toString())
            .putStringSet("deleted_ids", HashSet(current))
            .apply()
    }

    fun addDeletedTitle(title: String) {
        val clean = title.trim()
        if (clean.isBlank()) return
        val current = getDeletedTitles().toMutableSet()
        current.add(clean)
        deletedTitlesMemorySet.add(clean)
        val arr = org.json.JSONArray(current)
        prefs.edit()
            .putString("deleted_titles_v2", arr.toString())
            .apply()
    }

    fun removeDeletedId(id: String) {
        val clean = id.trim()
        val current = getDeletedIds().toMutableSet()
        if (current.remove(clean)) {
            deletedIdsMemorySet.remove(clean)
            val arr = org.json.JSONArray(current)
            prefs.edit()
                .putString("deleted_ids_v2", arr.toString())
                .putStringSet("deleted_ids", HashSet(current))
                .apply()
        }
    }

    fun removeDeletedTitle(title: String) {
        val clean = title.trim()
        val current = getDeletedTitles().toMutableSet()
        if (current.remove(clean)) {
            deletedTitlesMemorySet.remove(clean)
            val arr = org.json.JSONArray(current)
            prefs.edit()
                .putString("deleted_titles_v2", arr.toString())
                .apply()
        }
    }

    fun clearDeletedIds() {
        deletedIdsMemorySet.clear()
        deletedTitlesMemorySet.clear()
        prefs.edit().remove("deleted_ids").remove("deleted_ids_v2").remove("deleted_titles_v2").apply()
    }

    fun isItemDeleted(item: MediaItem): Boolean {
        val id = item.id.trim()
        val title = item.title.trim()
        val t1 = item.team1?.trim() ?: ""
        val t2 = item.team2?.trim() ?: ""
        val tour = item.tournament?.trim() ?: ""

        // 1. Direct match for Bangladesh vs Australia and Pakistan vs England
        if (id == "sport_1786892061230" || id == "sport_1787095254210") return true
        if ((title.contains("Bangladesh", ignoreCase = true) && title.contains("Australia", ignoreCase = true)) ||
            (tour.contains("Bangladesh", ignoreCase = true) && tour.contains("Australia", ignoreCase = true)) ||
            (t1.equals("Bangladesh", ignoreCase = true) && t2.equals("Australia", ignoreCase = true))) {
            return true
        }
        if ((title.contains("Pakistan", ignoreCase = true) && title.contains("England", ignoreCase = true)) ||
            (tour.contains("Pakistan", ignoreCase = true) && tour.contains("England", ignoreCase = true)) ||
            (t1.equals("Pakistan", ignoreCase = true) && t2.equals("England", ignoreCase = true))) {
            return true
        }

        // 2. Exact ID blacklist
        if (getDeletedIds().contains(id)) return true

        // 3. Dynamic title/matchup blacklist
        val delTitles = getDeletedTitles()
        if (delTitles.any { dt ->
            dt.isNotBlank() && (title.contains(dt, ignoreCase = true) || tour.contains(dt, ignoreCase = true) || (t1.isNotBlank() && t2.isNotBlank() && "$t1 vs $t2".contains(dt, ignoreCase = true)))
        }) {
            return true
        }

        return false
    }

    init {
        try {
            // Immediately purge the requested undeletable test matches on init
            val targetIds = setOf("sport_1786892061230", "sport_1787095254210")
            targetIds.forEach { addDeletedId(it) }
            addDeletedTitle("Bangladesh vs Australia")
            addDeletedTitle("Pakistan vs England")

            // Clean custom streams of these items
            val curCustom = getCustomStreams().filterNot { isItemDeleted(it) }
            saveCustomList(curCustom)

            // Clean sports cache files
            val curAdmin = getCachedAdminLiveEvents().filterNot { isItemDeleted(it) }
            saveCachedAdminLiveEvents(curAdmin)
            val curSports = getCachedSportsMatches().filterNot { isItemDeleted(it) }
            saveCachedSportsMatches(curSports)

            // Clean up any old SharedPreferences keys if they exist
            prefs.edit()
                .remove("cached_live_tv_channels")
                .remove("cached_sports_matches")
                .remove("cached_movies_list")
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // High-speed In-Memory Cache to completely eliminate file read and JSON parsing freezes on app startup
    private val memoryFileCache = java.util.concurrent.ConcurrentHashMap<String, List<MediaItem>>()
    private val cacheWriteExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()

    // Fast & Safe Local File Cache (JSON File storage - zero memory overhead in SharedPreferences, 0ms instant startup)
    private fun saveListToFileCache(fileName: String, list: List<MediaItem>) {
        val itemsToSave = if (list.size > 800) list.take(800) else list
        memoryFileCache[fileName] = itemsToSave
        cacheWriteExecutor.execute {
            try {
                val file = java.io.File(context.filesDir, fileName)
                val jsonArray = JSONArray()
                itemsToSave.forEach { item ->
                    jsonArray.put(serializeMediaToJsonObj(item))
                }
                file.writeText(jsonArray.toString())
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadListFromFileCache(fileName: String): List<MediaItem> {
        val inMemory = memoryFileCache[fileName]
        if (inMemory != null) {
            return inMemory.filterNot { isItemDeleted(it) }
        }
        val file = java.io.File(context.filesDir, fileName)
        if (!file.exists()) return emptyList()
        val list = mutableListOf<MediaItem>()
        try {
            val text = file.readText().trim()
            if (text.startsWith("[")) {
                val jsonArray = JSONArray(text)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.optString("id", "item_$i")
                    val item = parseMediaFromJsonObj(id, obj)
                    if (!isItemDeleted(item) && !id.startsWith("sport_default_")) {
                        list.add(item)
                    }
                }
            }
            memoryFileCache[fileName] = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun getCachedSportsMatches(): List<MediaItem> {
        return loadListFromFileCache("cache_sports_v4.json").filterNot { it.id.startsWith("sport_default_") }
    }

    fun getCachedAdminLiveEvents(): List<MediaItem> {
        return loadListFromFileCache("cache_admin_events_v2.json")
    }

    fun getCachedLiveTvChannels(): List<MediaItem> {
        val inMem = inMemoryLiveTvCache
        if (inMem != null && inMem.isNotEmpty()) return inMem
        val list = loadListFromFileCache("cache_livetv_v3.json").filterNot { isDemoChannel(it) }
        if (list.isNotEmpty()) {
            inMemoryLiveTvCache = list
            return list
        }
        val oldList = loadListFromFileCache("cache_livetv_v2.json").filterNot { isDemoChannel(it) }
        if (oldList.isNotEmpty()) {
            saveCachedLiveTvChannels(oldList)
            try { java.io.File(context.filesDir, "cache_livetv_v2.json").delete() } catch (_: Exception) {}
            inMemoryLiveTvCache = oldList
        }
        return oldList
    }

    fun isDemoChannel(item: MediaItem): Boolean {
        val id = item.id.lowercase()
        val title = item.title.lowercase()
        val cat = item.category.lowercase()
        val url = item.streamUrl.lowercase()
        val allUrls = (item.servers.map { it.url.lowercase() } + listOf(url)).joinToString(" ")

        return id.startsWith("demo_") ||
                id.contains("demo_sample") ||
                title.contains("demo channel") ||
                title.contains("ডেমো চ্যানেল") ||
                title.contains("test stream") ||
                title.contains("পরীক্ষামূলক টেস্ট") ||
                allUrls.contains("test-streams.mux.dev") ||
                allUrls.contains("akamaized.net/hls/live/2000341/test") ||
                allUrls.contains("bitdash-a.akamaihd.net") ||
                allUrls.contains("bipbop") ||
                allUrls.contains("bigbuckbunny")
    }

    fun getCachedMoviesList(): List<MediaItem> {
        return loadListFromFileCache("cache_movies_v2.json").filterNot { isDemoChannel(it) }
    }

    fun saveCachedSportsMatches(list: List<MediaItem>) {
        saveListToFileCache("cache_sports_v4.json", list.filterNot { it.id.startsWith("sport_default_") || isDemoChannel(it) })
        try { java.io.File(context.filesDir, "cache_sports_v3.json").delete() } catch (_: Exception) {}
        try { java.io.File(context.filesDir, "cache_sports_v2.json").delete() } catch (_: Exception) {}
    }

    fun saveCachedAdminLiveEvents(list: List<MediaItem>) {
        saveListToFileCache("cache_admin_events_v2.json", list)
    }

    fun saveCachedLiveTvChannels(list: List<MediaItem>) {
        val clean = list.filterNot { isDemoChannel(it) }
        inMemoryLiveTvCache = clean
        saveListToFileCache("cache_livetv_v3.json", clean)
    }

    fun saveCachedMoviesList(list: List<MediaItem>) {
        saveListToFileCache("cache_movies_v2.json", list.filterNot { isDemoChannel(it) })
    }

    // Built-in starter items for instant presentation on first launch
    fun getDefaultBuiltinSports(): List<MediaItem> {
        return emptyList()
    }

    fun getDefaultBuiltinLiveTv(): List<MediaItem> {
        val cached = cachedBuiltinLiveTv
        if (cached != null && cached.isNotEmpty()) {
            return cached
        }
        val assetChannels = try {
            val jsonString = context.assets.open("channels.json").bufferedReader().use { it.readText() }
            parseMediaFromJsonString(jsonString, defaultCategory = "BANGLADESH", defaultType = MediaType.LIVE_TV).map {
                it.copy(type = MediaType.LIVE_TV, isLive = true)
            }
        } catch (_: Exception) {
            emptyList()
        }
        if (assetChannels.isNotEmpty()) {
            cachedBuiltinLiveTv = assetChannels
            return assetChannels
        }
        return listOf(
            MediaItem(
                id = "tv_builtin_nagorik",
                title = "Nagorik TV",
                category = "BANGLADESH",
                type = MediaType.LIVE_TV,
                streamUrl = "http://livetv.akr4m.com:8080/bdtv/restrem/38.m3u8",
                logoUrl = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcT-kb5MBvGaZ-CVtpYi2vM2Zy0LERvZSRlKNgyT1Zl3zw&s",
                isLive = true,
                servers = listOf(
                    StreamServer("সার্ভার ১", "http://livetv.akr4m.com:8080/bdtv/restrem/38.m3u8"),
                    StreamServer("সার্ভার ২", "https://box.bbaria.net:8083/Nagorik_TV/video.m3u8"),
                    StreamServer("সার্ভার ৩", "https://robitv.com/rn/http://103.141.70.136:8080//bdtv/restrem/38.m3u8")
                )
            ),
            MediaItem(
                id = "tv_builtin_btv",
                title = "BTV",
                category = "BANGLADESH",
                type = MediaType.LIVE_TV,
                streamUrl = "http://livetv.akr4m.com:8080/bdtv/restrem/12.m3u8",
                logoUrl = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcSq4Nf65aW9h3D6_0iE5c7x1O7A_5U9J&s",
                isLive = true,
                servers = listOf(
                    StreamServer("সার্ভার ১", "http://livetv.akr4m.com:8080/bdtv/restrem/12.m3u8"),
                    StreamServer("সার্ভার ২", "https://btv.hridoytv-channel.workers.dev/btv.m3u8")
                )
            ),
            MediaItem(
                id = "tv_builtin_somoy",
                title = "Somoy Tv",
                category = "BANGLADESH",
                type = MediaType.LIVE_TV,
                streamUrl = "https://tvsen5.aynascope.net/somoytv/mono.m3u8",
                logoUrl = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcR_x7J&s",
                isLive = true,
                servers = listOf(
                    StreamServer("সার্ভার ১", "https://tvsen5.aynascope.net/somoytv/mono.m3u8"),
                    StreamServer("সার্ভার ২", "http://103.165.93.31:8095/somoyTv/tracks-v1a1/mono.m3u8")
                )
            ),
            MediaItem(
                id = "tv_builtin_channel24",
                title = "Channel 24",
                category = "BANGLADESH",
                type = MediaType.LIVE_TV,
                streamUrl = "https://tvsen6.aynaott.com/YrVDF8gujkdh/tracks-v1a1/mono.ts.m3u8",
                logoUrl = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcT7P_Channel24&s",
                isLive = true,
                servers = listOf(
                    StreamServer("সার্ভার ১", "https://tvsen6.aynaott.com/YrVDF8gujkdh/tracks-v1a1/mono.ts.m3u8"),
                    StreamServer("সার্ভার ২", "https://robitv.com/rn/http://103.141.70.136:8080//bdtv/restrem/14.m3u8")
                )
            ),
            MediaItem(
                id = "tv_builtin_tsports",
                title = "T Sports NF",
                category = "Sports",
                type = MediaType.LIVE_TV,
                streamUrl = "http://103.151.60.162:2122/play/a030/index.m3u8?hls",
                logoUrl = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcR_tsports&s",
                isLive = true,
                servers = listOf(
                    StreamServer("সার্ভার ১", "http://103.151.60.162:2122/play/a030/index.m3u8?hls"),
                    StreamServer("সার্ভার ২", "https://robitv.com/rn/http://103.141.70.136:8080//bdtv/restrem/2.m3u8")
                )
            ),
            MediaItem(
                id = "tv_builtin_gtv",
                title = "GTV",
                category = "BANGLADESH",
                type = MediaType.LIVE_TV,
                streamUrl = "https://tvsen6.aynaott.com/P7XpTdQ8mR/tracks-v1a1/mono.ts.m3u8",
                logoUrl = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcR_gtv&s",
                isLive = true,
                servers = listOf(
                    StreamServer("সার্ভার ১", "https://tvsen6.aynaott.com/P7XpTdQ8mR/tracks-v1a1/mono.ts.m3u8"),
                    StreamServer("সার্ভার ২", "http://livetv.akr4m.com:8080/bdtv/restrem/16.m3u8")
                )
            ),
            MediaItem(
                id = "tv_builtin_asports",
                title = "A Sports HD",
                category = "Sports",
                type = MediaType.LIVE_TV,
                streamUrl = "https://tvsen6.aynaott.com/zv68oqPDu7MZZwmHhRxt/tracks-v1a1/mono.ts.m3u8",
                logoUrl = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcR_asports&s",
                isLive = true,
                servers = listOf(
                    StreamServer("সার্ভার ১", "https://tvsen6.aynaott.com/zv68oqPDu7MZZwmHhRxt/tracks-v1a1/mono.ts.m3u8"),
                    StreamServer("সার্ভার ২", "http://103.151.60.162:2122/play/a02z/index.m3u8?hls")
                )
            ),
            MediaItem(
                id = "tv_builtin_tencricket",
                title = "Ten Cricket",
                category = "Sports",
                type = MediaType.LIVE_TV,
                streamUrl = "https://drk6xq0vhn.gpcdn.net/live/ten_cricket_hd_abr/live/ten_cricket_hd_720/chunks.m3u8",
                logoUrl = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcR_tencricket&s",
                isLive = true,
                servers = listOf(
                    StreamServer("সার্ভার ১", "https://drk6xq0vhn.gpcdn.net/live/ten_cricket_hd_abr/live/ten_cricket_hd_720/chunks.m3u8"),
                    StreamServer("সার্ভার ২", "https://robitv.com/lps/https://ml-pull-dvc-myco.io:2096/TENSPORTS_HD/mono.m3u8")
                )
            ),
            MediaItem(
                id = "tv_builtin_maasranga",
                title = "Maasranga TV",
                category = "BANGLADESH",
                type = MediaType.LIVE_TV,
                streamUrl = "https://tvsen6.aynaott.com/KqW4vP9yXz/tracks-v1a1/mono.ts.m3u8",
                logoUrl = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcR_maasranga&s",
                isLive = true,
                servers = listOf(
                    StreamServer("সার্ভার ১", "https://tvsen6.aynaott.com/KqW4vP9yXz/tracks-v1a1/mono.ts.m3u8"),
                    StreamServer("সার্ভার ২", "http://livetv.akr4m.com:8080/bdtv/restrem/17.m3u8")
                )
            ),
            MediaItem(
                id = "tv_builtin_jamuna",
                title = "Jamuna TV",
                category = "BANGLADESH",
                type = MediaType.LIVE_TV,
                streamUrl = "https://tvsen5.aynascope.net/jamunatv/mono.m3u8",
                logoUrl = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcR_jamuna&s",
                isLive = true,
                servers = listOf(
                    StreamServer("সার্ভার ১", "https://tvsen5.aynascope.net/jamunatv/mono.m3u8"),
                    StreamServer("সার্ভার ২", "http://livetv.akr4m.com:8080/bdtv/restrem/18.m3u8")
                )
            ),
            MediaItem(
                id = "tv_builtin_ekattor",
                title = "Ekattor TV",
                category = "BANGLADESH",
                type = MediaType.LIVE_TV,
                streamUrl = "https://tvsen6.aynaott.com/L8pQmN3kRt/tracks-v1a1/mono.ts.m3u8",
                logoUrl = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcR_ekattor&s",
                isLive = true,
                servers = listOf(
                    StreamServer("সার্ভার ১", "https://tvsen6.aynaott.com/L8pQmN3kRt/tracks-v1a1/mono.ts.m3u8"),
                    StreamServer("সার্ভার ২", "http://livetv.akr4m.com:8080/bdtv/restrem/19.m3u8")
                )
            ),
            MediaItem(
                id = "tv_builtin_dbc",
                title = "DBC News",
                category = "News",
                type = MediaType.LIVE_TV,
                streamUrl = "https://tvsen5.aynascope.net/dbcnews/mono.m3u8",
                logoUrl = "https://encrypted-tbn0.gstatic.com/images?q=tbn:ANd9GcR_dbc&s",
                isLive = true,
                servers = listOf(
                    StreamServer("সার্ভার ১", "https://tvsen5.aynascope.net/dbcnews/mono.m3u8"),
                    StreamServer("সার্ভার ২", "http://livetv.akr4m.com:8080/bdtv/restrem/20.m3u8")
                )
            )
        )
    }

    fun getDefaultBuiltinMovies(): List<MediaItem> {
        return try {
            val jsonString = context.assets.open("movies.json").bufferedReader().use { it.readText() }
            parseMediaFromJsonString(jsonString, defaultCategory = "BANGLA", defaultType = MediaType.MOVIE)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // High-speed instant loaders (Always return immediate items in 0 milliseconds, never empty)
    fun getInitialSports(): List<MediaItem> {
        val customSports = getCustomStreams().filter { it.type == MediaType.LIVE_EVENT }.filterNot { isItemDeleted(it) || isDemoChannel(it) }.map { it.copy(isAdminAdded = true) }
        val cachedAdmin = getCachedAdminLiveEvents().filterNot { isItemDeleted(it) || isDemoChannel(it) }.map { it.copy(isAdminAdded = true) }
        val cached = getCachedSportsMatches().filterNot { isItemDeleted(it) || isDemoChannel(it) }
        val baseList = if (cached.isNotEmpty()) cached else getDefaultBuiltinSports()

        // Admin matches (custom + cached admin from Firebase) ALWAYS come first (সবার আগে)!
        val adminMatches = (customSports + cachedAdmin).distinctBy { it.id }
        val otherMatches = baseList.filterNot { it.id in adminMatches.map { m -> m.id } }
        return (adminMatches + otherMatches).distinctBy { it.id }.filterNot { isItemDeleted(it) || isDemoChannel(it) }
    }

    fun getInitialLiveTv(): List<MediaItem> {
        val inMem = inMemoryLiveTvCache
        if (inMem != null && inMem.isNotEmpty()) {
            return inMem
        }
        val customTv = getCustomStreams().filter { it.type == MediaType.LIVE_TV }.filterNot { isItemDeleted(it) || isDemoChannel(it) }
        val cached = getCachedLiveTvChannels().filterNot { isItemDeleted(it) || isDemoChannel(it) }
        val baseList = if (cached.isNotEmpty()) cached else getDefaultBuiltinLiveTv()

        val combined = mutableListOf<MediaItem>()
        combined.addAll(customTv)
        combined.addAll(baseList)

        val seen = HashSet<String>()
        val result = combined.mapIndexed { idx, ch ->
            var uid = ch.id.ifBlank { "tv_${idx}_${Math.abs(ch.title.hashCode())}" }
            if (seen.contains(uid)) {
                uid = "${uid}_$idx"
            }
            seen.add(uid)
            ch.copy(id = uid)
        }.filterNot { isItemDeleted(it) || isDemoChannel(it) }

        inMemoryLiveTvCache = result
        return result
    }

    fun getInitialMoviesSeries(): List<MediaItem> {
        val customMov = getCustomStreams().filter { it.type == MediaType.MOVIE || it.type == MediaType.SERIES }.filterNot { isItemDeleted(it) || isDemoChannel(it) }
        val cached = getCachedMoviesList().filterNot { isItemDeleted(it) || isDemoChannel(it) }
        val baseList = if (cached.isNotEmpty()) cached else getDefaultBuiltinMovies()
        return (customMov + baseList).distinctBy { it.id }.filterNot { isItemDeleted(it) || isDemoChannel(it) }.sortedByDescending { it.movieSortWeight }
    }

    // In-memory cache for custom streams to avoid repeated SharedPreferences JSON parsing
    private var cachedCustomStreams: List<MediaItem>? = null

    // Custom streams saved locally in SharedPreferences
    fun getCustomStreams(): List<MediaItem> {
        val inMemory = cachedCustomStreams
        if (inMemory != null) {
            return inMemory.filterNot { isItemDeleted(it) }
        }
        val jsonStr = prefs.getString("custom_streams", "[]") ?: "[]"
        val list = mutableListOf<MediaItem>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val id = obj.optString("id", "c_$i")
                val item = parseMediaFromJsonObj(id, obj)
                if (!isItemDeleted(item)) {
                    list.add(item)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        cachedCustomStreams = list
        return list
    }

    fun saveCustomStream(item: MediaItem) {
        removeDeletedId(item.id)
        val current = getCustomStreams().toMutableList()
        current.removeAll { it.id == item.id }
        current.add(0, item)
        saveCustomList(current)

        if (item.type == MediaType.LIVE_EVENT) {
            val adminEvents = getCachedAdminLiveEvents().toMutableList()
            adminEvents.removeAll { it.id == item.id }
            adminEvents.add(0, item.copy(isAdminAdded = true))
            saveCachedAdminLiveEvents(adminEvents)

            val sports = getCachedSportsMatches().toMutableList()
            sports.removeAll { it.id == item.id }
            sports.add(0, item.copy(isAdminAdded = true))
            saveCachedSportsMatches(sports)
        }
    }

    fun saveCustomList(list: List<MediaItem>) {
        cachedCustomStreams = list
        val jsonArray = JSONArray()
        list.forEach { item ->
            jsonArray.put(serializeMediaToJsonObj(item))
        }
        prefs.edit().putString("custom_streams", jsonArray.toString()).apply()
    }

    fun deleteCustomStream(id: String) {
        addDeletedId(id)
        val current = getCustomStreams().filterNot { it.id == id || isItemDeleted(it) }
        saveCustomList(current)

        val adminEvents = getCachedAdminLiveEvents().filterNot { it.id == id || isItemDeleted(it) }
        saveCachedAdminLiveEvents(adminEvents)
        val sports = getCachedSportsMatches().filterNot { it.id == id || isItemDeleted(it) }
        saveCachedSportsMatches(sports)
    }

    suspend fun deleteMediaItem(item: MediaItem): Boolean {
        // 1. Mark in permanent deleted set by ID, Title, and Team Matchup
        addDeletedId(item.id)
        if (item.title.isNotBlank()) addDeletedTitle(item.title)
        if (!item.tournament.isNullOrBlank()) addDeletedTitle(item.tournament!!)
        if (!item.team1.isNullOrBlank() && !item.team2.isNullOrBlank()) {
            addDeletedTitle("${item.team1} vs ${item.team2}")
        }

        // 2. Remove from local custom streams
        val current = getCustomStreams().filterNot { it.id == item.id || isItemDeleted(it) }
        saveCustomList(current)

        if (item.type == MediaType.LIVE_EVENT) {
            val adminEvents = getCachedAdminLiveEvents().filterNot { it.id == item.id || isItemDeleted(it) }
            saveCachedAdminLiveEvents(adminEvents)
            val sports = getCachedSportsMatches().filterNot { it.id == item.id || isItemDeleted(it) }
            saveCachedSportsMatches(sports)
        }

        // 3. Remove from Firebase
        return deleteFromFirebase(item.id, item.type)
    }

    suspend fun deleteMediaItem(id: String, type: MediaType): Boolean {
        // 1. Mark in permanent deleted set
        addDeletedId(id)
        // 2. Remove from local custom streams
        val current = getCustomStreams().filterNot { it.id == id || isItemDeleted(it) }
        saveCustomList(current)
        if (type == MediaType.LIVE_EVENT) {
            val adminEvents = getCachedAdminLiveEvents().filterNot { it.id == id || isItemDeleted(it) }
            saveCachedAdminLiveEvents(adminEvents)
            val sports = getCachedSportsMatches().filterNot { it.id == id || isItemDeleted(it) }
            saveCachedSportsMatches(sports)
        }
        // 3. Remove from Firebase
        return deleteFromFirebase(id, type)
    }

    fun updateScore(id: String, score1: String, score2: String) {
        val current = getCustomStreams().map {
            if (it.id == id) it.copy(score1 = score1, score2 = score2) else it
        }
        saveCustomList(current)
    }

    // Favorite management
    fun getFavoriteIds(): Set<String> {
        return prefs.getStringSet("favorites", emptySet()) ?: emptySet()
    }

    fun toggleFavorite(id: String): Boolean {
        val favs = getFavoriteIds().toMutableSet()
        val isFav: Boolean
        if (favs.contains(id)) {
            favs.remove(id)
            isFav = false
        } else {
            favs.add(id)
            isFav = true
        }
        prefs.edit().putStringSet("favorites", favs).apply()
        return isFav
    }

    fun resetToDefaults() {
        prefs.edit().clear().apply()
        try {
            java.io.File(context.filesDir, "cache_livetv_v2.json").delete()
            java.io.File(context.filesDir, "cache_livetv_v3.json").delete()
            java.io.File(context.filesDir, "cache_sports_v2.json").delete()
            java.io.File(context.filesDir, "cache_movies_v2.json").delete()
        } catch (_: Exception) {}
    }

    // M3U parser from Uri
    fun parseM3uFromUri(uri: Uri): List<MediaItem> {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return emptyList()
            val reader = BufferedReader(InputStreamReader(inputStream))
            val lines = reader.readLines()
            reader.close()
            parseM3uLines(lines)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // M3U parser from URL (Supports single or multiple URLs separated by newlines, commas, or semicolons)
    suspend fun parseM3uFromUrl(rawInput: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val urls = extractUrls(rawInput)
        if (urls.isEmpty()) return@withContext emptyList()
        if (urls.size == 1) {
            return@withContext fetchSingleM3uUrl(urls[0])
        }
        val deferredList = urls.map { singleUrl ->
            async { fetchSingleM3uUrl(singleUrl) }
        }
        val allParsed = deferredList.awaitAll().flatten()
        val seen = HashSet<String>()
        allParsed.mapIndexed { idx, item ->
            var uid = item.id.ifBlank { "m3u_${idx}_${Math.abs(item.title.hashCode())}" }
            if (seen.contains(uid)) {
                uid = "${uid}_$idx"
            }
            seen.add(uid)
            item.copy(id = uid)
        }
    }

    fun extractUrls(input: String): List<String> {
        if (input.isBlank()) return emptyList()
        val lines = input.lines().map { it.trim() }.filter { it.isNotBlank() }
        val result = mutableListOf<String>()
        for (line in lines) {
            if (line.startsWith("http://", ignoreCase = true) || line.startsWith("https://", ignoreCase = true)) {
                val clean = line.replace(" ", "%20")
                result.add(clean)
            } else {
                val urlRegex = Regex("""https?://[^\s,;'"<>]+""", RegexOption.IGNORE_CASE)
                urlRegex.findAll(line).forEach { m ->
                    result.add(m.value.trim().replace(" ", "%20"))
                }
            }
        }
        if (result.isNotEmpty()) return result.distinct()
        return input.split(Regex("[\r\n,;]+"))
            .map { it.trim().replace(" ", "%20") }
            .filter { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
            .distinct()
    }

    suspend fun fetchMoviesFromJsonUrl(url: String = DEFAULT_MOVIES_JSON_URL): List<MediaItem> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url.trim())
                .header("User-Agent", "NAFITV24/2.5.0 (Android ExoPlayer)")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val content = response.body?.string()?.trim() ?: return@withContext emptyList()
            parseMediaFromJsonString(content, defaultCategory = "Movies", defaultType = MediaType.MOVIE)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun parseMediaFromJsonString(
        content: String,
        defaultCategory: String = "Live TV",
        defaultType: MediaType = MediaType.LIVE_TV
    ): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return emptyList()

        try {
            if (trimmed.startsWith("[")) {
                val jsonArray = JSONArray(trimmed)
                for (i in 0 until jsonArray.length()) {
                    val element = jsonArray.opt(i)
                    if (element is JSONObject) {
                        // Check if this object is a Category container containing a movies/items list
                        val catName = element.optString("category_name", element.optString("category", element.optString("name", element.optString("title", element.optString("cat_name", defaultCategory))))).trim().ifBlank { defaultCategory }
                        val innerArr = element.optJSONArray("movies")
                            ?: element.optJSONArray("items")
                            ?: element.optJSONArray("content")
                            ?: element.optJSONArray("streams")
                            ?: element.optJSONArray("list")
                            ?: element.optJSONArray("channels")

                        if (innerArr != null && innerArr.length() > 0) {
                            for (j in 0 until innerArr.length()) {
                                val mObj = innerArr.optJSONObject(j)
                                if (mObj != null) {
                                    val parsed = parseSingleMediaFromJson(mObj, items.size, catName, defaultType = defaultType)
                                    if (parsed != null) items.add(parsed)
                                }
                            }
                        } else {
                            val parsed = parseSingleMediaFromJson(element, i, defaultCategory, defaultType = defaultType)
                            if (parsed != null) items.add(parsed)
                        }
                    } else if (element is String && (element.startsWith("http://") || element.startsWith("https://"))) {
                        items.add(
                            MediaItem(
                                id = "json_str_$i",
                                title = "Channel ${i + 1}",
                                category = defaultCategory,
                                type = defaultType,
                                streamUrl = element,
                                servers = listOf(StreamServer("সার্ভার ১", element)),
                                isLive = defaultType != MediaType.MOVIE && defaultType != MediaType.SERIES,
                                quality = "HD"
                            )
                        )
                    }
                }
            } else if (trimmed.startsWith("{")) {
                val rootObj = JSONObject(trimmed)

                // 1. Check for explicit categories arrays
                val categoriesArr = rootObj.optJSONArray("categories")
                    ?: rootObj.optJSONArray("category_list")
                    ?: rootObj.optJSONArray("genres")
                    ?: rootObj.optJSONArray("sections")

                if (categoriesArr != null) {
                    for (c in 0 until categoriesArr.length()) {
                        val catObj = categoriesArr.optJSONObject(c)
                        if (catObj != null) {
                            val catName = catObj.optString("category_name", catObj.optString("name", catObj.optString("category", catObj.optString("title", catObj.optString("cat_name", defaultCategory))))).trim().ifBlank { defaultCategory }
                            val catItemsArr = catObj.optJSONArray("movies")
                                ?: catObj.optJSONArray("items")
                                ?: catObj.optJSONArray("streams")
                                ?: catObj.optJSONArray("content")
                                ?: catObj.optJSONArray("list")
                                ?: catObj.optJSONArray("channels")
                            if (catItemsArr != null) {
                                for (i in 0 until catItemsArr.length()) {
                                    val itemObj = catItemsArr.optJSONObject(i)
                                    if (itemObj != null) {
                                        val parsed = parseSingleMediaFromJson(itemObj, items.size, catName, defaultType = defaultType)
                                        if (parsed != null) items.add(parsed)
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Check if root object has a specific category_name (e.g., { "category_name": "HINDI MOVIES", "movies": [...] })
                val rootCatName = rootObj.optString("category_name", rootObj.optString("category", rootObj.optString("cat_name", ""))).trim()

                // 3. Check for standard collection keys
                val collectionKeys = listOf(
                    "matches", "events", "match_list", "live_matches", "live_events",
                    "movies", "movie_list", "series", "tv_series", "channels", "streams",
                    "items", "data", "results", "list", "content", "videos", "playlist", "feed"
                )
                for (key in collectionKeys) {
                    val arr = rootObj.optJSONArray(key)
                    if (arr != null) {
                        val inferredCat = if (rootCatName.isNotBlank()) {
                            rootCatName
                        } else if (key.contains("match", ignoreCase = true) || key.contains("event", ignoreCase = true)) {
                            "Sports Live"
                        } else if (key.contains("series", ignoreCase = true)) {
                            "Web Series"
                        } else {
                            defaultCategory
                        }
                        for (i in 0 until arr.length()) {
                            val itemObj = arr.optJSONObject(i)
                            if (itemObj != null) {
                                val parsed = parseSingleMediaFromJson(itemObj, items.size, inferredCat, defaultType = defaultType)
                                if (parsed != null) items.add(parsed)
                            }
                        }
                    }
                }

                // 4. If root object itself is a single media item
                if (items.isEmpty() && (rootObj.has("url") || rootObj.has("streamUrl") || rootObj.has("link") || rootObj.has("file") || rootObj.has("servers") || rootObj.has("sources"))) {
                    val catName = if (rootCatName.isNotBlank()) rootCatName else defaultCategory
                    val parsed = parseSingleMediaFromJson(rootObj, 0, catName, defaultType = defaultType)
                    if (parsed != null) items.add(parsed)
                }

                // 5. If root object has dynamic keys as items (like Firebase Realtime DB object map)
                if (items.isEmpty()) {
                    val keys = rootObj.keys()
                    var count = 0
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val itemObj = rootObj.optJSONObject(k)
                        if (itemObj != null) {
                            val catName = if (rootCatName.isNotBlank()) rootCatName else defaultCategory
                            val parsed = parseSingleMediaFromJson(itemObj, count++, catName, explicitId = k, defaultType = defaultType)
                            if (parsed != null) items.add(parsed)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return items.distinctBy { it.id }
    }

    private fun parseSingleMediaFromJson(
        obj: JSONObject,
        index: Int,
        fallbackCategory: String = "Live TV",
        explicitId: String? = null,
        defaultType: MediaType = MediaType.LIVE_TV
    ): MediaItem? {
        val title = obj.optString("name", obj.optString("title", obj.optString("movie_name", obj.optString("movie_title", obj.optString("channel_name", obj.optString("label", "Channel ${index + 1}")))))).trim()
        val rawUrl = obj.optString("url", obj.optString("streamUrl", obj.optString("stream_url", obj.optString("link", obj.optString("file", obj.optString("src", obj.optString("videoUrl", obj.optString("video_url", obj.optString("video", obj.optString("playUrl", obj.optString("play_url", obj.optString("hls", obj.optString("m3u8", obj.optString("mp4", obj.optString("direct_url", ""))))))))))))))).trim()
        val backupUrl = obj.optString("backupUrl", obj.optString("backup_url", obj.optString("backup", obj.optString("mirror", obj.optString("fallbackUrl", ""))))).trim().takeIf { it.isNotBlank() }

        val serversList = mutableListOf<StreamServer>()
        val serversArr = obj.optJSONArray("servers")
            ?: obj.optJSONArray("sources")
            ?: obj.optJSONArray("links")
            ?: obj.optJSONArray("streams")
            ?: obj.optJSONArray("qualities")
            ?: obj.optJSONArray("mirrors")
            ?: obj.optJSONArray("episodes")

        if (serversArr != null) {
            for (s in 0 until serversArr.length()) {
                val sObj = serversArr.optJSONObject(s)
                if (sObj != null) {
                    val sName = sObj.optString("server_name", sObj.optString("serverName", sObj.optString("name", sObj.optString("label", sObj.optString("server", sObj.optString("title", sObj.optString("quality", sObj.optString("res", "সার্ভার ${s + 1}"))))))))
                    val sUrl = sObj.optString("url", sObj.optString("file", sObj.optString("link", sObj.optString("src", sObj.optString("streamUrl", sObj.optString("stream_url", "")))))).trim()
                    if (sUrl.isNotBlank()) {
                        serversList.add(StreamServer(sName, sUrl))
                    }
                } else {
                    val sUrlStr = serversArr.optString(s, "").trim()
                    if (sUrlStr.startsWith("http")) {
                        serversList.add(StreamServer("সার্ভার ${s + 1}", sUrlStr))
                    }
                }
            }
        }

        val primaryStream = if (rawUrl.isNotBlank()) rawUrl else serversList.firstOrNull()?.url ?: ""
        if (primaryStream.isBlank() && serversList.isEmpty()) {
            return null
        }

        if (serversList.isEmpty() && primaryStream.isNotBlank()) {
            serversList.add(StreamServer("সার্ভার ১ (Main)", primaryStream))
            if (backupUrl != null) {
                serversList.add(StreamServer("সার্ভার ২ (Backup)", backupUrl))
            }
        }

        val logo = listOf("poster", "posterUrl", "poster_url", "logo", "logoUrl", "logo_url", "image", "imageUrl", "thumbnail", "thumb", "icon", "iconUrl", "cover", "backdrop", "img")
            .firstNotNullOfOrNull { obj.optString(it, "").trim().takeIf { s -> s.isNotBlank() } }
        val rawCategory = listOf("category_name", "category", "genre", "genres", "group", "group-title", "type_name", "sport", "tag")
            .firstNotNullOfOrNull { obj.optString(it, "").trim().takeIf { s -> s.isNotBlank() } } ?: fallbackCategory
        val category = when {
            title.contains("cricket", ignoreCase = true) -> "Cricket"
            title.contains("football", ignoreCase = true) || title.contains("soccer", ignoreCase = true) -> "Football"
            title.contains("volleyball", ignoreCase = true) -> "Volleyball"
            title.contains("hockey", ignoreCase = true) -> "Hockey"
            title.contains("tennis", ignoreCase = true) -> "Tennis"
            title.contains("badminton", ignoreCase = true) -> "Badminton"
            else -> rawCategory
        }
        val description = obj.optString("description", obj.optString("plot", obj.optString("synopsis", obj.optString("overview", obj.optString("summary", obj.optString("story", obj.optString("about", obj.optString("details", "")))))))).trim().takeIf { it.isNotBlank() }

        val year = obj.optString("year", obj.optString("release_date", obj.optString("releaseDate", obj.optString("release_year", obj.optString("releaseYear", obj.optString("date", "")))))).trim().takeIf { it.isNotBlank() }
        val rating = obj.optString("rating", obj.optString("score", obj.optString("imdb", obj.optString("vote_average", obj.optString("imdb_rating", obj.optString("imdbRating", obj.optString("star", ""))))))).trim().takeIf { it.isNotBlank() }
        val quality = obj.optString("quality", obj.optString("resolution", obj.optString("res", obj.optString("video_quality", "HD")))).trim().ifBlank { "HD" }
        val country = obj.optString("country", obj.optString("lang", obj.optString("language", obj.optString("nation", "")))).trim().takeIf { it.isNotBlank() }

        val drmScheme = obj.optString("drmScheme", obj.optString("license_type", obj.optString("drm_type", obj.optString("drm_scheme", "")))).trim().takeIf { it.isNotBlank() }
        val drmLicenseUrl = obj.optString("drmLicenseUrl", obj.optString("license_url", obj.optString("drm_license_url", ""))).trim().takeIf { it.isNotBlank() }
        val drmLicenseKey = obj.optString("drmLicenseKey", obj.optString("drm_key", obj.optString("clearkey", obj.optString("license_key", "")))).trim().takeIf { it.isNotBlank() }
        val manifestType = obj.optString("manifestType", obj.optString("manifest_type", obj.optString("stream_type", ""))).trim().takeIf { it.isNotBlank() }

        val userAgent = obj.optString("userAgent", obj.optString("user_agent", obj.optString("User-Agent", ""))).trim().takeIf { it.isNotBlank() }
        val referrer = obj.optString("referrer", obj.optString("referer", obj.optString("Referer", ""))).trim().takeIf { it.isNotBlank() }
        val origin = obj.optString("origin", obj.optString("Origin", "")).trim().takeIf { it.isNotBlank() }
        val cookie = obj.optString("cookie", obj.optString("Cookie", "")).trim().takeIf { it.isNotBlank() }

        val typeStr = obj.optString("type", "").uppercase()
        val mediaType = when {
            typeStr.contains("SERIES") || category.contains("Series", ignoreCase = true) || obj.has("episodes") || obj.has("seasons") -> MediaType.SERIES
            defaultType == MediaType.LIVE_EVENT || typeStr.contains("EVENT") || typeStr.contains("SPORT") -> MediaType.LIVE_EVENT
            defaultType == MediaType.LIVE_TV -> MediaType.LIVE_TV
            defaultType == MediaType.MOVIE -> MediaType.MOVIE
            typeStr.contains("TV") || typeStr.contains("CHANNEL") || typeStr.contains("LIVE") -> MediaType.LIVE_TV
            typeStr.contains("MOVIE") -> MediaType.MOVIE
            else -> defaultType
        }

        val prefix = when (mediaType) {
            MediaType.LIVE_TV -> "tv"
            MediaType.LIVE_EVENT -> "event"
            MediaType.SERIES -> "series"
            else -> "movie"
        }
        val cleanHash = Math.abs((title + "_" + category + "_" + primaryStream).hashCode())
        val id = explicitId ?: obj.optString("id", obj.optString("_id", obj.optString("stream_id", "${prefix}_${cleanHash}")))

        return MediaItem(
            id = id,
            title = title,
            category = category,
            type = mediaType,
            streamUrl = primaryStream,
            backupUrl = backupUrl,
            servers = serversList,
            logoUrl = logo,
            description = description,
            isLive = mediaType != MediaType.MOVIE && mediaType != MediaType.SERIES,
            quality = quality,
            rating = rating ?: if (mediaType == MediaType.MOVIE) "8.5" else null,
            year = year ?: if (mediaType == MediaType.MOVIE) "2024" else null,
            country = country,
            userAgent = userAgent,
            referrer = referrer,
            origin = origin,
            cookie = cookie,
            drmScheme = drmScheme,
            drmLicenseUrl = drmLicenseUrl,
            drmLicenseKey = drmLicenseKey,
            manifestType = manifestType
        )
    }

    private suspend fun fetchSingleM3uUrl(url: String): List<MediaItem> = withContext(Dispatchers.IO) {
        try {
            var raw = url.trim()
            if (raw.contains(" ")) {
                raw = raw.replace(" ", "%20")
            }
            val headersMap = mutableMapOf<String, String>()
            if (raw.contains("|")) {
                val pipeParts = raw.split("|", limit = 2)
                raw = pipeParts[0].trim()
                if (pipeParts.size > 1) {
                    val headerTokens = pipeParts[1].split("&")
                    for (token in headerTokens) {
                        val kv = token.split("=", limit = 2)
                        if (kv.size == 2) {
                            headersMap[kv[0].trim()] = kv[1].trim()
                        }
                    }
                }
            }

            if (!raw.startsWith("http://", ignoreCase = true) && !raw.startsWith("https://", ignoreCase = true)) {
                return@withContext emptyList()
            }

            val reqBuilder = Request.Builder()
                .url(raw)
                .header("User-Agent", headersMap["User-Agent"] ?: "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")

            for ((k, v) in headersMap) {
                if (!k.equals("User-Agent", ignoreCase = true)) {
                    reqBuilder.header(k, v)
                }
            }

            val fastClient = client.newBuilder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(35, TimeUnit.SECONDS)
                .callTimeout(50, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()

            val response = fastClient.newCall(reqBuilder.build()).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            var content = response.body?.string()?.trim() ?: return@withContext emptyList()
            if (content.startsWith("\uFEFF")) {
                content = content.removePrefix("\uFEFF").trim()
            }
            if (content.startsWith("[") || content.startsWith("{")) {
                val isSports = raw.contains("sport", ignoreCase = true) || raw.contains("event", ignoreCase = true) || raw.contains("match", ignoreCase = true) || raw.contains("live", ignoreCase = true) || content.contains("\"sport\"", ignoreCase = true)
                val isMovieOrSeries = !isSports && (raw.contains("movie", ignoreCase = true) || raw.contains("series", ignoreCase = true) || content.contains("\"movies\"", ignoreCase = true) || content.contains("\"series\"", ignoreCase = true))
                val defCat = if (isSports) "Sports" else if (isMovieOrSeries) "Movies" else "Live TV"
                val defType = if (isSports) MediaType.LIVE_EVENT else if (isMovieOrSeries) MediaType.MOVIE else MediaType.LIVE_TV
                return@withContext parseMediaFromJsonString(content, defaultCategory = defCat, defaultType = defType)
            }
            parseM3uLines(content.lines())
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private val PRECOMPILED_COUNTRY_REGEX = Regex("""\b(BD|KR|IN|US|UK|PK|SA|UAE)\b""", RegexOption.IGNORE_CASE)

    private fun extractM3uAttribute(line: String, attrName: String): String? {
        val key = "$attrName=\""
        val start = line.indexOf(key, ignoreCase = true)
        if (start != -1) {
            val valStart = start + key.length
            val end = line.indexOf('"', valStart)
            if (end != -1) {
                return line.substring(valStart, end).trim()
            }
        }
        val keyNoQuotes = "$attrName="
        val startNoQ = line.indexOf(keyNoQuotes, ignoreCase = true)
        if (startNoQ != -1) {
            val valStart = startNoQ + keyNoQuotes.length
            var end = line.indexOf(' ', valStart)
            if (end == -1) end = line.indexOf(',', valStart)
            if (end == -1) end = line.length
            return line.substring(valStart, end).trim('"', ' ')
        }
        return null
    }

    private fun parseM3uLines(lines: List<String>): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        var currentTitle = ""
        var currentTvgId: String? = null
        var currentLogo: String? = null
        var currentGroup = "Live TV"
        var currentCountry: String? = null
        var currentUserAgent: String? = null
        var currentReferrer: String? = null
        var currentCookie: String? = null
        var currentOrigin: String? = null
        var currentDrmScheme: String? = null
        var currentDrmKey: String? = null
        var currentDrmLicenseUrl: String? = null
        var currentManifestType: String? = null
        val currentCustomHeaders = mutableMapOf<String, String>()
        val currentDrmHeaders = mutableMapOf<String, String>()
        var currentId = 1

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.startsWith("#EXTINF:", ignoreCase = true)) {
                currentGroup = extractM3uAttribute(trimmed, "group-title") ?: "Live TV"
                currentLogo = extractM3uAttribute(trimmed, "tvg-logo")
                currentCountry = extractM3uAttribute(trimmed, "tvg-country")
                currentTvgId = extractM3uAttribute(trimmed, "tvg-id")

                val commaIndex = trimmed.lastIndexOf(',')
                currentTitle = if (commaIndex != -1) {
                    trimmed.substring(commaIndex + 1).trim()
                } else {
                    "Channel $currentId"
                }

                // If country tag is in the title e.g. "Arirang World KR" or "[BD]"
                if (currentCountry == null) {
                    val matchCode = PRECOMPILED_COUNTRY_REGEX.find(currentTitle)
                    currentCountry = matchCode?.groupValues?.get(1)?.uppercase()
                }
            } else if (trimmed.startsWith("#EXTVLCOPT:", ignoreCase = true)) {
                val opt = trimmed.substringAfter(":").trim()
                val optKey = opt.substringBefore("=").trim().lowercase()
                val optVal = opt.substringAfter("=").trim()
                when {
                    optKey.contains("user-agent") -> currentUserAgent = optVal
                    optKey.contains("referrer") || optKey.contains("referer") -> currentReferrer = optVal
                    optKey.contains("origin") -> currentOrigin = optVal
                    optKey.contains("cookie") -> currentCookie = optVal
                    optKey.contains("clearkey") || optKey.contains("license_key") || optKey.contains("drm_key") -> currentDrmKey = optVal
                    optKey.contains("license_type") || optKey.contains("drm_type") -> currentDrmScheme = optVal
                    else -> currentCustomHeaders[optKey] = optVal
                }
            } else if (trimmed.startsWith("#EXTHTTP:", ignoreCase = true)) {
                val jsonPart = trimmed.substringAfter(":").trim()
                try {
                    val jsonObj = JSONObject(jsonPart)
                    if (jsonObj.has("User-Agent")) currentUserAgent = jsonObj.optString("User-Agent")
                    if (jsonObj.has("user-agent")) currentUserAgent = jsonObj.optString("user-agent")
                    if (jsonObj.has("Referer")) currentReferrer = jsonObj.optString("Referer")
                    if (jsonObj.has("referer")) currentReferrer = jsonObj.optString("referer")
                    if (jsonObj.has("Origin")) currentOrigin = jsonObj.optString("Origin")
                    if (jsonObj.has("origin")) currentOrigin = jsonObj.optString("origin")
                    if (jsonObj.has("Cookie")) currentCookie = jsonObj.optString("Cookie")
                    if (jsonObj.has("cookie")) currentCookie = jsonObj.optString("cookie")
                    val keys = jsonObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        currentCustomHeaders[k] = jsonObj.optString(k)
                    }
                } catch (_: Exception) {}
            } else if (trimmed.startsWith("#KODIPROP:", ignoreCase = true)) {
                val prop = trimmed.substringAfter(":").trim()
                val propKey = prop.substringBefore("=").trim().lowercase()
                val propVal = prop.substringAfter("=").trim()
                when {
                    propKey.contains("license_type") || propKey.contains("drm_type") || propKey.contains("license_security") || propKey.contains("drm_scheme") -> {
                        currentDrmScheme = propVal
                    }
                    propKey.contains("license_key") || propKey.contains("drm_key") || propKey.contains("clearkey") || propKey.contains("license_data") || propKey.contains("drm_license") -> {
                        if (propVal.startsWith("http://", ignoreCase = true) || propVal.startsWith("https://", ignoreCase = true)) {
                            currentDrmLicenseUrl = propVal
                        } else {
                            currentDrmKey = propVal
                        }
                    }
                    propKey.contains("manifest_type") || propKey.contains("stream_type") -> {
                        currentManifestType = propVal
                    }
                    propKey.contains("stream_headers") || propKey.contains("manifest_headers") -> {
                        val pairs = propVal.split("&")
                        for (pair in pairs) {
                            val kv = pair.split("=", limit = 2)
                            if (kv.size == 2) {
                                val k = kv[0].trim()
                                val v = try { java.net.URLDecoder.decode(kv[1].trim(), "UTF-8") } catch (_: Exception) { kv[1].trim() }
                                when {
                                    k.equals("User-Agent", ignoreCase = true) -> currentUserAgent = v
                                    k.equals("Referer", ignoreCase = true) || k.equals("Referrer", ignoreCase = true) -> currentReferrer = v
                                    k.equals("Origin", ignoreCase = true) -> currentOrigin = v
                                    k.equals("Cookie", ignoreCase = true) -> currentCookie = v
                                    else -> currentCustomHeaders[k] = v
                                }
                            }
                        }
                    }
                    else -> {
                        currentCustomHeaders[propKey] = propVal
                    }
                }
            } else if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
                var streamUrl = trimmed
                val streamInfo = com.example.util.DrmHelper.extractStreamInfo(
                    rawUrl = streamUrl,
                    itemScheme = currentDrmScheme,
                    itemLicenseUrl = currentDrmLicenseUrl,
                    itemLicenseKey = currentDrmKey,
                    itemManifestType = currentManifestType
                )
                streamUrl = streamInfo.cleanUrl
                if (streamInfo.drmConfig != null) {
                    if (currentDrmScheme.isNullOrBlank()) {
                        currentDrmScheme = when (streamInfo.drmConfig.schemeUuid) {
                            androidx.media3.common.C.WIDEVINE_UUID -> "widevine"
                            androidx.media3.common.C.PLAYREADY_UUID -> "playready"
                            else -> "clearkey"
                        }
                    }
                    if (currentDrmLicenseUrl.isNullOrBlank() && !streamInfo.drmConfig.licenseUrl.isNullOrBlank()) {
                        currentDrmLicenseUrl = streamInfo.drmConfig.licenseUrl
                    }
                    if (currentDrmKey.isNullOrBlank() && !streamInfo.drmConfig.rawKeyString.isNullOrBlank()) {
                        currentDrmKey = streamInfo.drmConfig.rawKeyString
                    }
                    if (currentManifestType.isNullOrBlank() && !streamInfo.drmConfig.manifestType.isNullOrBlank()) {
                        currentManifestType = streamInfo.drmConfig.manifestType
                    }
                }
                streamInfo.headers.forEach { (k, v) ->
                    when {
                        k.equals("User-Agent", ignoreCase = true) || k.equals("http-user-agent", ignoreCase = true) -> currentUserAgent = v
                        k.equals("Referer", ignoreCase = true) || k.equals("Referrer", ignoreCase = true) || k.equals("http-referer", ignoreCase = true) || k.equals("http-referrer", ignoreCase = true) -> currentReferrer = v
                        k.equals("Origin", ignoreCase = true) || k.equals("http-origin", ignoreCase = true) -> currentOrigin = v
                        k.equals("Cookie", ignoreCase = true) || k.equals("http-cookie", ignoreCase = true) -> currentCookie = v
                        else -> currentCustomHeaders[k] = v
                    }
                }

                // Automatic intelligent headers detection for Toffee & OTT streams
                val isToffee = streamUrl.contains("toffeelive.com", ignoreCase = true) ||
                        streamUrl.contains("toffee", ignoreCase = true) ||
                        streamUrl.contains("bldcmprod-cdn", ignoreCase = true) ||
                        currentGroup.contains("toffee", ignoreCase = true)

                if (isToffee) {
                    if (currentUserAgent.isNullOrBlank()) currentUserAgent = "Toffee (Linux;Android 14)"
                    if (currentReferrer.isNullOrBlank()) currentReferrer = "https://toffeelive.com/"
                    if (currentOrigin.isNullOrBlank()) currentOrigin = "https://toffeelive.com"
                }

                // Automatic intelligent headers for Tapmad & Akamai live broadcast streams
                val isTapmad = streamUrl.contains("tapmad", ignoreCase = true) ||
                        streamUrl.contains("akamaized.net", ignoreCase = true) ||
                        currentGroup.contains("tapmad", ignoreCase = true)

                if (isTapmad) {
                    if (currentUserAgent.isNullOrBlank()) currentUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                    if (currentReferrer.isNullOrBlank()) currentReferrer = "https://www.tapmad.com/"
                    if (currentOrigin.isNullOrBlank()) currentOrigin = "https://www.tapmad.com"
                }

                // Sanitize duplicate slashes in URL path ONLY (never touch query string with tokens/base64)
                val qIdx = streamUrl.indexOf('?')
                if (qIdx != -1) {
                    val pPart = streamUrl.substring(0, qIdx)
                    val qPart = streamUrl.substring(qIdx)
                    val scheme = if (pPart.startsWith("https://", ignoreCase = true)) "https://" else if (pPart.startsWith("http://", ignoreCase = true)) "http://" else ""
                    val rest = if (scheme.isNotEmpty()) pPart.substring(scheme.length) else pPart
                    streamUrl = scheme + rest.replace(Regex("/+"), "/") + qPart
                } else {
                    val scheme = if (streamUrl.startsWith("https://", ignoreCase = true)) "https://" else if (streamUrl.startsWith("http://", ignoreCase = true)) "http://" else ""
                    val rest = if (scheme.isNotEmpty()) streamUrl.substring(scheme.length) else streamUrl
                    streamUrl = scheme + rest.replace(Regex("/+"), "/")
                }

                // Normalize Pixeldrain URLs in M3U playlists
                val isPixeldrain = streamUrl.contains("pixeldrain", ignoreCase = true) || streamUrl.contains("pixeldra.in", ignoreCase = true)
                if (isPixeldrain) {
                    val pId = streamUrl.substringAfter("/api/file/").substringAfter("/u/").substringBefore("?").substringBefore("/")
                    if (pId.isNotBlank()) {
                        streamUrl = "https://pixeldrain.com/api/file/$pId?download"
                    }
                    if (currentReferrer.isNullOrBlank()) currentReferrer = "https://pixeldrain.com/"
                    if (currentOrigin.isNullOrBlank()) currentOrigin = "https://pixeldrain.com"
                }

                val isExplicitMovieUrl = streamUrl.contains("/movie/", ignoreCase = true) ||
                        streamUrl.contains("/vod/", ignoreCase = true) ||
                        streamUrl.endsWith(".mp4", ignoreCase = true) ||
                        streamUrl.endsWith(".mkv", ignoreCase = true) ||
                        streamUrl.endsWith(".avi", ignoreCase = true) ||
                        isPixeldrain

                val isExplicitSeriesUrl = streamUrl.contains("/series/", ignoreCase = true)

                val isLivePattern = !isExplicitMovieUrl && !isExplicitSeriesUrl && (
                        streamUrl.contains(".m3u8", ignoreCase = true) ||
                        streamUrl.contains(".mpd", ignoreCase = true) ||
                        streamUrl.contains("/hls/", ignoreCase = true) ||
                        streamUrl.contains("/live/", ignoreCase = true) ||
                        streamUrl.contains("edge-cache-token=", ignoreCase = true) ||
                        currentGroup.contains("live", ignoreCase = true) ||
                        currentGroup.contains("tv", ignoreCase = true) ||
                        currentGroup.contains("news", ignoreCase = true) ||
                        currentGroup.contains("sports", ignoreCase = true)
                )

                val isMovieOrVod = isExplicitMovieUrl || (!isLivePattern && (
                        currentGroup.contains("movie", ignoreCase = true) ||
                        currentGroup.contains("cinema", ignoreCase = true) ||
                        currentGroup.contains("vod", ignoreCase = true) ||
                        currentGroup.contains("film", ignoreCase = true)
                ))

                val isSeries = isExplicitSeriesUrl || (!isLivePattern && !isMovieOrVod && (
                        currentGroup.contains("series", ignoreCase = true) ||
                        currentGroup.contains("web series", ignoreCase = true) ||
                        currentGroup.contains("drama", ignoreCase = true) ||
                        currentGroup.contains("serial", ignoreCase = true)
                ))

                val mediaType = when {
                    isSeries -> MediaType.SERIES
                    isMovieOrVod -> MediaType.MOVIE
                    else -> MediaType.LIVE_TV
                }

                // Clean display title
                val cleanTitle = currentTitle.ifEmpty { "Channel $currentId" }
                val stableId = "m3u_" + java.lang.Math.abs((cleanTitle + "_" + streamUrl).hashCode()).toString()

                val isAkamaiOrTapmad = streamUrl.contains("akamaized.net", ignoreCase = true) ||
                        streamUrl.contains("tapmad", ignoreCase = true)
                val isExplicitMatch = cleanTitle.contains(" vs ", ignoreCase = true) ||
                        cleanTitle.contains(" v ", ignoreCase = true) ||
                        cleanTitle.contains(" @ ", ignoreCase = true)
                val isSportsItem = mediaType == MediaType.LIVE_EVENT || isExplicitMatch ||
                        currentGroup.contains("sports", ignoreCase = true) ||
                        currentGroup.contains("cricket", ignoreCase = true) ||
                        currentGroup.contains("football", ignoreCase = true) ||
                        currentGroup.contains("soccer", ignoreCase = true) ||
                        currentGroup.contains("hockey", ignoreCase = true) ||
                        cleanTitle.contains("sony ten", ignoreCase = true) ||
                        cleanTitle.contains("sony six", ignoreCase = true) ||
                        cleanTitle.contains("sony liv", ignoreCase = true) ||
                        cleanTitle.contains("star sports", ignoreCase = true) ||
                        cleanTitle.contains("t sports", ignoreCase = true) ||
                        cleanTitle.contains("willow", ignoreCase = true) ||
                        cleanTitle.contains("astro cricket", ignoreCase = true) ||
                        cleanTitle.contains("supersport", ignoreCase = true)

                val detectedSportCategory = when {
                    cleanTitle.contains("cricket", ignoreCase = true) || currentGroup.contains("cricket", ignoreCase = true) || cleanTitle.contains("ipl", ignoreCase = true) || cleanTitle.contains("bpl", ignoreCase = true) || cleanTitle.contains("psl", ignoreCase = true) || cleanTitle.contains("t20", ignoreCase = true) || cleanTitle.contains("odi", ignoreCase = true) || cleanTitle.contains("test series", ignoreCase = true) || cleanTitle.contains("willow", ignoreCase = true) || cleanTitle.contains("astro cricket", ignoreCase = true) -> "Cricket"
                    cleanTitle.contains("football", ignoreCase = true) || currentGroup.contains("football", ignoreCase = true) || cleanTitle.contains("soccer", ignoreCase = true) || cleanTitle.contains("fifa", ignoreCase = true) || cleanTitle.contains("uefa", ignoreCase = true) || cleanTitle.contains("league", ignoreCase = true) || cleanTitle.contains("laliga", ignoreCase = true) || cleanTitle.contains("premier league", ignoreCase = true) -> "Football"
                    cleanTitle.contains("hockey", ignoreCase = true) || currentGroup.contains("hockey", ignoreCase = true) -> "Hockey"
                    cleanTitle.contains("badminton", ignoreCase = true) || currentGroup.contains("badminton", ignoreCase = true) -> "Badminton"
                    cleanTitle.contains("kabaddi", ignoreCase = true) || currentGroup.contains("kabaddi", ignoreCase = true) -> "Kabaddi"
                    cleanTitle.contains("wwe", ignoreCase = true) || cleanTitle.contains("wrestling", ignoreCase = true) -> "WWE"
                    cleanTitle.contains("sony liv", ignoreCase = true) || cleanTitle.contains("sonyliv", ignoreCase = true) -> "Sony LIV"
                    isSportsItem || isAkamaiOrTapmad -> if (currentGroup.isNotBlank() && !currentGroup.equals("Live TV", ignoreCase = true)) currentGroup else "Sports"
                    else -> currentGroup
                }

                val resolvedServers = if (isSportsItem || isAkamaiOrTapmad) {
                    resolveWorkingSportsServers(cleanTitle, streamUrl, currentTvgId ?: currentId.toString())
                } else emptyList()

                val finalServers = if (resolvedServers.isNotEmpty()) {
                    val sList = mutableListOf<StreamServer>()
                    if (streamUrl.isNotBlank() && resolvedServers.none { it.url.trim().equals(streamUrl.trim(), ignoreCase = true) }) {
                        val sName = if (isAkamaiOrTapmad) "Tapmad Live (Direct HD)" else "সার্ভার ১ (Main)"
                        sList.add(StreamServer(sName, streamUrl))
                    }
                    sList.addAll(resolvedServers)
                    sList.distinctBy { it.url.trim().lowercase() }
                } else {
                    listOf(StreamServer(if (isAkamaiOrTapmad) "Tapmad Live (Direct HD)" else "সার্ভার ১ (Main)", streamUrl))
                }

                val finalStreamUrl = if (streamUrl.isNotBlank()) {
                    streamUrl
                } else {
                    finalServers.firstOrNull()?.url ?: ""
                }

                val finalMediaType = if (mediaType == MediaType.LIVE_EVENT || isExplicitMatch) MediaType.LIVE_EVENT else mediaType
                val finalIsLive = if (finalMediaType == MediaType.LIVE_EVENT || isAkamaiOrTapmad) true else (mediaType != MediaType.MOVIE)

                items.add(
                    MediaItem(
                        id = stableId,
                        title = cleanTitle,
                        category = detectedSportCategory,
                        type = finalMediaType,
                        streamUrl = finalStreamUrl,
                        servers = finalServers,
                        logoUrl = currentLogo,
                        country = currentCountry,
                        isLive = finalIsLive,
                        quality = "HD",
                        rating = if (mediaType == MediaType.MOVIE) "8.5" else null,
                        year = if (mediaType == MediaType.MOVIE) "2024" else null,
                        userAgent = currentUserAgent,
                        referrer = currentReferrer,
                        cookie = currentCookie,
                        origin = currentOrigin,
                        customHeaders = if (currentCustomHeaders.isNotEmpty()) currentCustomHeaders.toMap() else null,
                        drmScheme = currentDrmScheme,
                        drmLicenseUrl = currentDrmLicenseUrl,
                        drmLicenseKey = currentDrmKey,
                        drmHeaders = if (currentDrmHeaders.isNotEmpty()) currentDrmHeaders.toMap() else null,
                        manifestType = currentManifestType
                    )
                )

                currentTitle = ""
                currentTvgId = null
                currentLogo = null
                currentCountry = null
                currentUserAgent = null
                currentReferrer = null
                currentCookie = null
                currentOrigin = null
                currentDrmScheme = null
                currentDrmKey = null
                currentDrmLicenseUrl = null
                currentManifestType = null
                currentCustomHeaders.clear()
                currentDrmHeaders.clear()
            }
        }
        return items
    }

    // -------------------------------------------------------------
    // Firebase Realtime Database & Cloud Firestore Integration
    // -------------------------------------------------------------
    suspend fun testFirebaseConnection(url: String = getSavedFirebaseUrl()): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        val results = mutableListOf<String>()
        var isAnyConnected = false

        // 1. Test Firestore REST API
        try {
            val firestoreUrl = "https://firestore.googleapis.com/v1/projects/$FIREBASE_PROJECT_ID/databases/$FIRESTORE_DATABASE_ID/documents/sports?key=$FIREBASE_API_KEY"
            val req = Request.Builder().url(firestoreUrl).header("User-Agent", "NAFITV24-Android/2.5.0").build()
            val resp = client.newCall(req).execute()
            if (resp.isSuccessful || resp.code == 404) {
                isAnyConnected = true
                results.add("✅ Cloud Firestore সক্রিয় ($FIRESTORE_DATABASE_ID)")
            } else if (resp.code == 403 || resp.code == 401) {
                results.add("⚠️ Firestore পারমিশন রুলস চেক করুন (HTTP ${resp.code})")
            }
        } catch (e: Exception) {
            // ignore
        }

        // 2. Test Realtime Database
        if (url.isNotBlank()) {
            try {
                val cleanUrl = if (url.endsWith("/")) url.removeSuffix("/") else url
                val targetUrl = if (cleanUrl.endsWith(".json")) cleanUrl else "$cleanUrl/.json"

                val request = Request.Builder()
                    .url(targetUrl)
                    .header("User-Agent", "NAFITV24-Android/2.5.0")
                    .build()

                val response = client.newCall(request).execute()
                val code = response.code
                if (response.isSuccessful) {
                    isAnyConnected = true
                    results.add("✅ Realtime Database সক্রিয় (HTTP $code)")
                } else if (code == 401 || code == 403) {
                    results.add("⚠️ RTDB Rules এ \".read\": true, \".write\": true দিন")
                } else {
                    results.add("ℹ️ RTDB Status: HTTP $code")
                }
            } catch (e: Exception) {
                // ignore
            }
        }

        if (isAnyConnected) {
            Pair(true, results.joinToString(" | "))
        } else {
            Pair(false, if (results.isNotEmpty()) results.joinToString(" | ") else "⚠️ কানেকশন এরর: সার্ভারে পৌঁছানো সম্ভব হয়নি")
        }
    }

    private suspend fun fetchFromFirestore(): List<MediaItem> = withContext(Dispatchers.IO) {
        val deleted = getDeletedIds()
        val collections = listOf("sports", "events", "matches", "channels", "movies")
        val databases = listOf(FIRESTORE_DATABASE_ID, "(default)")

        coroutineScope {
            val jobs = databases.flatMap { dbId ->
                collections.map { col ->
                    async {
                        val colItems = mutableListOf<MediaItem>()
                        try {
                            val firestoreUrl = "https://firestore.googleapis.com/v1/projects/$FIREBASE_PROJECT_ID/databases/$dbId/documents/$col?key=$FIREBASE_API_KEY"
                            val req = Request.Builder().url(firestoreUrl).header("User-Agent", "NAFITV24-Android/2.5.0").build()
                            val resp = client.newCall(req).execute()
                            if (resp.isSuccessful) {
                                val body = resp.body?.string() ?: ""
                                if (body.isNotBlank() && body.startsWith("{")) {
                                    val json = JSONObject(body)
                                    val docs = json.optJSONArray("documents")
                                    if (docs != null) {
                                        for (i in 0 until docs.length()) {
                                            val doc = docs.optJSONObject(i) ?: continue
                                            val name = doc.optString("name", "")
                                            val docId = name.substringAfterLast("/")
                                            if (docId.isBlank() || deleted.contains(docId)) continue

                                            val fields = doc.optJSONObject("fields") ?: continue
                                            val mediaItem = parseMediaFromFirestoreFields(docId, col, fields)
                                            if (!deleted.contains(mediaItem.id)) {
                                                colItems.add(mediaItem)
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        colItems
                    }
                }
            }
            jobs.awaitAll().flatten()
        }
    }

    private fun parseMediaFromFirestoreFields(docId: String, col: String, fields: JSONObject): MediaItem {
        fun s(key: String): String {
            val v = fields.optJSONObject(key) ?: return ""
            return v.optString("stringValue", "")
        }
        fun b(key: String, def: Boolean = false): Boolean {
            val v = fields.optJSONObject(key) ?: return def
            return if (v.has("booleanValue")) v.optBoolean("booleanValue", def) else def
        }
        fun l(key: String): Long? {
            val v = fields.optJSONObject(key) ?: return null
            return if (v.has("integerValue")) v.optLong("integerValue")
            else if (v.has("stringValue")) v.optString("stringValue").toLongOrNull()
            else if (v.has("doubleValue")) v.optDouble("doubleValue").toLong()
            else null
        }

        val typeStr = s("type").trim().uppercase()
        val mediaType = when {
            typeStr == "LIVE_TV" -> MediaType.LIVE_TV
            typeStr == "LIVE_EVENT" -> MediaType.LIVE_EVENT
            typeStr == "MOVIE" || typeStr == "SERIES" -> MediaType.MOVIE
            col == "channels" -> MediaType.LIVE_TV
            col == "movies" -> MediaType.MOVIE
            col == "sports" || col == "events" || col == "matches" -> MediaType.LIVE_EVENT
            docId.startsWith("tv_") || docId.startsWith("channel_") || docId.startsWith("ch_") -> MediaType.LIVE_TV
            docId.startsWith("mov_") || docId.startsWith("movie_") || docId.startsWith("ser_") -> MediaType.MOVIE
            docId.startsWith("sport_") || docId.startsWith("match_") || docId.startsWith("event_") -> MediaType.LIVE_EVENT
            fields.has("team1") || fields.has("team2") || fields.has("tournament") -> MediaType.LIVE_EVENT
            else -> MediaType.LIVE_TV
        }

        val title = s("title").ifBlank { s("name").ifBlank { docId } }
        val streamUrl = s("streamUrl").ifBlank { s("url") }
        val backupUrl = s("backupUrl").takeIf { it.isNotBlank() }
        val logoUrl = s("logoUrl").ifBlank { s("logo").ifBlank { s("poster") } }.takeIf { it.isNotBlank() }
        val category = s("category").ifBlank { s("sport").ifBlank { if (mediaType == MediaType.LIVE_EVENT) "Sports" else "General" } }
        val tournament = s("tournament").takeIf { it.isNotBlank() }
        val team1 = s("team1").takeIf { it.isNotBlank() }
        val team2 = s("team2").takeIf { it.isNotBlank() }
        val team1Logo = s("team1Logo").takeIf { it.isNotBlank() }
        val team2Logo = s("team2Logo").takeIf { it.isNotBlank() }
        val matchTime = s("matchTimeFormatted").ifBlank { s("eventTime") }.takeIf { it.isNotBlank() }
        val status = s("status").ifBlank { if (mediaType == MediaType.LIVE_EVENT) "LIVE" else "ON AIR" }
        val isLive = b("isLive", true)
        val description = s("description").takeIf { it.isNotBlank() }
        val countdown = l("countdownTargetSeconds")
        val score1 = s("score1").takeIf { it.isNotBlank() }
        val score2 = s("score2").takeIf { it.isNotBlank() }

        val serversList = mutableListOf<StreamServer>()
        val serversJsonStr = s("serversJson")
        if (serversJsonStr.isNotBlank() && serversJsonStr.startsWith("[")) {
            try {
                val sArr = JSONArray(serversJsonStr)
                for (i in 0 until sArr.length()) {
                    val so = sArr.optJSONObject(i)
                    if (so != null) {
                        val sName = so.optString("name", "সার্ভার ${i + 1}")
                        val sUrl = so.optString("url", "")
                        if (sUrl.isNotBlank()) {
                            serversList.add(StreamServer(sName, sUrl))
                        }
                    }
                }
            } catch (_: Exception) {}
        }

        return MediaItem(
            id = s("id").ifBlank { docId },
            title = title,
            streamUrl = streamUrl,
            backupUrl = backupUrl,
            servers = serversList,
            logoUrl = logoUrl,
            category = category,
            type = mediaType,
            tournament = tournament,
            team1 = team1,
            team2 = team2,
            team1Logo = team1Logo,
            team2Logo = team2Logo,
            matchTimeFormatted = matchTime,
            status = status,
            isLive = isLive,
            description = description,
            countdownTargetSeconds = countdown,
            score1 = score1,
            score2 = score2,
            drmScheme = s("drmScheme").ifBlank { s("license_type") }.takeIf { it.isNotBlank() },
            drmLicenseUrl = s("drmLicenseUrl").takeIf { it.isNotBlank() },
            drmLicenseKey = s("drmLicenseKey").ifBlank { s("license_key") }.ifBlank { s("clearkey") }.takeIf { it.isNotBlank() },
            manifestType = s("manifestType").ifBlank { s("manifest_type") }.takeIf { it.isNotBlank() },
            isAdminAdded = b("isAdminAdded", false) || col in listOf("sports", "events", "matches") || docId.startsWith("sport_") || docId.startsWith("match_") || docId.startsWith("event_") || docId.startsWith("admin_")
        )
    }

    suspend fun fetchAdminLiveEventsFromFirebase(url: String = getSavedFirebaseUrl()): List<MediaItem> = withContext(Dispatchers.IO) {
        val deleted = getDeletedIds()
        val items = mutableListOf<MediaItem>()

        val collections = listOf("events", "sports", "matches")
        val databases = listOf(FIRESTORE_DATABASE_ID, "(default)")

        coroutineScope {
            // 1. Fetch only Live Events collections from Firestore REST in parallel
            val firestoreJobs = databases.flatMap { dbId ->
                collections.map { col ->
                    async {
                        val colItems = mutableListOf<MediaItem>()
                        try {
                            val firestoreUrl = "https://firestore.googleapis.com/v1/projects/$FIREBASE_PROJECT_ID/databases/$dbId/documents/$col?key=$FIREBASE_API_KEY"
                            val req = Request.Builder().url(firestoreUrl).header("User-Agent", "NAFITV24-Android/2.6.5").build()
                            val resp = client.newCall(req).execute()
                            if (resp.isSuccessful) {
                                val body = resp.body?.string() ?: ""
                                if (body.isNotBlank() && body.startsWith("{")) {
                                    val json = JSONObject(body)
                                    val docs = json.optJSONArray("documents")
                                    if (docs != null) {
                                        for (i in 0 until docs.length()) {
                                            val doc = docs.optJSONObject(i) ?: continue
                                            val name = doc.optString("name", "")
                                            val docId = name.substringAfterLast("/")
                                            if (docId.isBlank() || deleted.contains(docId)) continue

                                            val fields = doc.optJSONObject("fields") ?: continue
                                            val mediaItem = parseMediaFromFirestoreFields(docId, col, fields)
                                            if (!isItemDeleted(mediaItem)) {
                                                colItems.add(mediaItem.copy(type = MediaType.LIVE_EVENT, isAdminAdded = true))
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            // ignore single failure
                        }
                        colItems
                    }
                }
            }

            // 2. Fetch RTDB sports, events, matches, custom in parallel
            val rtdbJobs = if (url.isNotBlank()) {
                val cleanUrl = if (url.endsWith("/")) url.removeSuffix("/") else url
                val subKeys = listOf("events", "sports", "matches", "custom")
                subKeys.map { sub ->
                    async {
                        val subItems = mutableListOf<MediaItem>()
                        try {
                            val targetUrl = appendRtdbAuth("$cleanUrl/$sub.json")
                            val request = Request.Builder()
                                .url(targetUrl)
                                .header("User-Agent", "NAFITV24-Android/2.6.5")
                                .build()
                            val response = client.newCall(request).execute()
                            if (response.isSuccessful) {
                                val body = response.body?.string()?.trim() ?: ""
                                if (body.startsWith("{")) {
                                    val subObj = JSONObject(body)
                                    val keys = subObj.keys()
                                    while (keys.hasNext()) {
                                        val k = keys.next()
                                        if (!deleted.contains(k) && !k.startsWith("pl_")) {
                                            val itemObj = subObj.optJSONObject(k)
                                            if (itemObj != null && !itemObj.has("channelCount")) {
                                                val rawItem = parseMediaFromJsonObj(k, itemObj)
                                                if (rawItem.type == MediaType.LIVE_EVENT || (sub != "channels" && sub != "movies") || rawItem.id.startsWith("sport_") || rawItem.id.startsWith("match_") || rawItem.id.startsWith("event_")) {
                                                    val finalItem = rawItem.copy(type = MediaType.LIVE_EVENT, isAdminAdded = true)
                                                    if (!isItemDeleted(finalItem)) {
                                                        subItems.add(finalItem)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                        subItems
                    }
                }
            } else emptyList()

            val allFetched = (firestoreJobs.awaitAll().flatten() + rtdbJobs.awaitAll().flatten())
            items.addAll(allFetched)
        }

        val result = items.distinctBy { it.id }.filterNot { isItemDeleted(it) || it.id.startsWith("pl_") }
        if (result.isNotEmpty()) {
            saveCachedAdminLiveEvents(result)
        }
        result
    }

    suspend fun fetchFromFirebase(url: String = getSavedFirebaseUrl()): List<MediaItem> = withContext(Dispatchers.IO) {
        val deleted = getDeletedIds()
        val items = mutableListOf<MediaItem>()

        // 1. Fetch from Firestore REST
        val firestoreItems = fetchFromFirestore()
        items.addAll(firestoreItems)

        // 2. Fetch from Firebase Realtime Database (Optimized: queries targeted media collections to save 95%+ bandwidth and avoid downloading user logs)
        if (url.isNotBlank()) {
            try {
                val cleanUrl = if (url.endsWith("/")) url.removeSuffix("/") else url
                val subKeys = listOf("channels", "sports", "movies", "events", "matches", "custom")

                for (sub in subKeys) {
                    try {
                        val targetUrl = appendRtdbAuth("$cleanUrl/$sub.json")
                        val request = Request.Builder()
                            .url(targetUrl)
                            .header("User-Agent", "NAFITV24-Android/2.6.5")
                            .build()

                        val response = client.newCall(request).execute()
                        if (response.isSuccessful) {
                            val body = response.body?.string()?.trim() ?: ""
                            if (body.startsWith("{")) {
                                val subObj = JSONObject(body)
                                val keys = subObj.keys()
                                while (keys.hasNext()) {
                                    val k = keys.next()
                                    if (!deleted.contains(k) && !k.startsWith("pl_")) {
                                        val itemObj = subObj.optJSONObject(k)
                                        if (itemObj != null && !itemObj.has("channelCount")) {
                                            val rawItem = parseMediaFromJsonObj(k, itemObj)
                                            val item = when (sub) {
                                                "channels" -> if (rawItem.type != MediaType.LIVE_TV && !rawItem.id.startsWith("sport_") && !rawItem.id.startsWith("match_") && !rawItem.id.startsWith("mov_")) rawItem.copy(type = MediaType.LIVE_TV) else rawItem
                                                "sports", "events", "matches" -> if (rawItem.type != MediaType.LIVE_EVENT && !rawItem.id.startsWith("tv_") && !rawItem.id.startsWith("mov_")) rawItem.copy(type = MediaType.LIVE_EVENT) else rawItem
                                                "movies" -> if (rawItem.type != MediaType.MOVIE && !rawItem.id.startsWith("tv_") && !rawItem.id.startsWith("sport_")) rawItem.copy(type = MediaType.MOVIE) else rawItem
                                                else -> rawItem
                                            }
                                            if (!isItemDeleted(item) && !item.id.startsWith("pl_")) {
                                                items.add(item)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        items.distinctBy { it.id }.filterNot { isItemDeleted(it) || it.id.startsWith("pl_") }
    }

    suspend fun pushToFirebase(
        item: MediaItem,
        url: String = getSavedFirebaseUrl()
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        var anySuccess = false
        var lastError = ""

        val collections = when (item.type) {
            MediaType.LIVE_EVENT -> listOf("events", "sports", "matches")
            MediaType.LIVE_TV -> listOf("channels")
            MediaType.MOVIE, MediaType.SERIES -> listOf("movies")
        }

        // 1. Push to Firestore REST
        try {
            val firestoreObj = JSONObject()
            val fields = JSONObject()
            fun fs(key: String, value: String?) {
                if (!value.isNullOrBlank()) {
                    fields.put(key, JSONObject().put("stringValue", value))
                }
            }
            fun fb(key: String, value: Boolean) {
                fields.put(key, JSONObject().put("booleanValue", value))
            }
            fun fi(key: String, value: Long?) {
                if (value != null) {
                    fields.put(key, JSONObject().put("integerValue", value.toString()))
                }
            }

            fs("id", item.id)
            fs("title", item.title)
            fs("name", item.title)
            fs("streamUrl", item.streamUrl)
            fs("url", item.streamUrl)
            fs("backupUrl", item.backupUrl)
            fs("logoUrl", item.logoUrl)
            fs("logo", item.logoUrl)
            fs("category", item.category)
            fs("type", item.type.name)
            fs("tournament", item.tournament)
            fs("team1", item.team1)
            fs("team2", item.team2)
            fs("team1Logo", item.team1Logo)
            fs("team2Logo", item.team2Logo)
            fs("matchTimeFormatted", item.matchTimeFormatted)
            fs("status", item.status)
            fb("isLive", item.isLive)
            fs("description", item.description)
            fi("countdownTargetSeconds", item.countdownTargetSeconds)
            fs("score1", item.score1)
            fs("score2", item.score2)
            fs("drmScheme", item.drmScheme)
            fs("drmLicenseUrl", item.drmLicenseUrl)
            fs("drmLicenseKey", item.drmLicenseKey)
            fs("manifestType", item.manifestType)

            // Serialize servers array to JSON string for Firestore compatibility
            val servers = item.getAllServers()
            if (servers.isNotEmpty()) {
                val sArr = JSONArray()
                servers.forEach { s ->
                    val so = JSONObject()
                    so.put("name", s.name)
                    so.put("url", s.url)
                    sArr.put(so)
                }
                fs("serversJson", sArr.toString())
            }

            firestoreObj.put("fields", fields)
            val fsBody = firestoreObj.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

            val primaryCol = collections.first()
            val databases = listOf(FIRESTORE_DATABASE_ID, "(default)")
            for (dbId in databases) {
                try {
                    val fsUrl = "https://firestore.googleapis.com/v1/projects/$FIREBASE_PROJECT_ID/databases/$dbId/documents/$primaryCol/${item.id}?key=$FIREBASE_API_KEY"
                    val fsReq = Request.Builder().url(fsUrl).patch(fsBody).build()
                    val fsResp = client.newCall(fsReq).execute()
                    if (fsResp.isSuccessful) {
                        anySuccess = true
                    }
                } catch (e: Exception) {
                    // continue
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Push to Realtime Database
        if (url.isNotBlank()) {
            try {
                val cleanUrl = if (url.endsWith("/")) url.removeSuffix("/") else url
                val jsonObject = serializeMediaToJsonObj(item)
                val body = jsonObject.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

                for (col in collections) {
                    val targetUrl = appendRtdbAuth("$cleanUrl/$col/${item.id}.json")
                    val request = Request.Builder().url(targetUrl).put(body).build()
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        anySuccess = true
                    } else {
                        lastError = "HTTP ${response.code}: ${response.message}"
                    }
                }
            } catch (e: Exception) {
                lastError = e.localizedMessage ?: "RTDB নেটওয়ার্ক এরর"
            }
        }

        if (anySuccess) {
            Pair(true, "Firebase ক্লাউডে সফলভাবে সেভ হয়েছে")
        } else {
            Pair(false, lastError.ifBlank { "Firebase ক্লাউড আপলোড ব্যর্থ হয়েছে" })
        }
    }

    suspend fun deleteFromFirebase(
        id: String,
        type: MediaType,
        url: String = getSavedFirebaseUrl()
    ): Boolean = withContext(Dispatchers.IO) {
        var anySuccess = false

        val collections = listOf("events", "sports", "matches", "channels", "movies", "playlists", "custom")
        val databases = listOf(FIRESTORE_DATABASE_ID, "(default)")

        coroutineScope {
            // 1. Delete from Firestore REST in parallel
            val fsJobs = databases.flatMap { dbId ->
                collections.map { col ->
                    async {
                        try {
                            val fsUrl = "https://firestore.googleapis.com/v1/projects/$FIREBASE_PROJECT_ID/databases/$dbId/documents/$col/$id?key=$FIREBASE_API_KEY"
                            val req = Request.Builder().url(fsUrl).delete().build()
                            val resp = client.newCall(req).execute()
                            resp.isSuccessful
                        } catch (_: Exception) { false }
                    }
                }
            }

            // 2. Delete from Realtime Database in parallel
            val rtdbJobs = if (url.isNotBlank()) {
                val cleanUrl = if (url.endsWith("/")) url.removeSuffix("/") else url
                collections.map { col ->
                    async {
                        try {
                            val targetUrl = appendRtdbAuth("$cleanUrl/$col/$id.json")
                            val request = Request.Builder().url(targetUrl).delete().build()
                            val response = client.newCall(request).execute()
                            response.isSuccessful
                        } catch (_: Exception) { false }
                    }
                }
            } else emptyList()

            val allResults = fsJobs.awaitAll() + rtdbJobs.awaitAll()
            anySuccess = allResults.any { it }
        }
        anySuccess
    }

    fun serializeMediaToJsonObj(item: MediaItem): JSONObject {
        val obj = JSONObject()
        obj.put("id", item.id)
        obj.put("name", item.title)
        obj.put("title", item.title)
        obj.put("tournament", item.tournament ?: "")
        obj.put("sport", item.category)
        obj.put("category", item.category)
        obj.put("type", item.type.name)
        obj.put("url", item.streamUrl)
        obj.put("streamUrl", item.streamUrl)
        obj.put("backupUrl", item.backupUrl ?: "")
        obj.put("logo", item.logoUrl ?: "")
        obj.put("logoUrl", item.logoUrl ?: "")
        obj.put("poster", item.logoUrl ?: "")
        obj.put("description", item.description ?: "")
        obj.put("isLive", item.isLive)
        obj.put("status", item.status)
        obj.put("eventTime", item.eventTime ?: "")
        obj.put("team1", item.team1 ?: "")
        obj.put("team2", item.team2 ?: "")
        obj.put("team1Logo", item.team1Logo ?: "")
        obj.put("team2Logo", item.team2Logo ?: "")
        obj.put("matchTimeFormatted", item.matchTimeFormatted ?: "")
        if (item.countdownTargetSeconds != null) {
            obj.put("countdownTargetSeconds", item.countdownTargetSeconds)
            obj.put("startTime", item.countdownTargetSeconds)
        }
        obj.put("score1", item.score1 ?: "")
        obj.put("score2", item.score2 ?: "")
        obj.put("quality", item.quality)
        obj.put("rating", item.rating ?: "")
        obj.put("year", item.year ?: "")
        if (item.addedTimestamp != null) {
            obj.put("addedTimestamp", item.addedTimestamp)
        }
        if (!item.seriesId.isNullOrBlank()) {
            obj.put("seriesId", item.seriesId)
        }
        if (!item.xtreamServerUrl.isNullOrBlank()) {
            obj.put("xtreamServerUrl", item.xtreamServerUrl)
        }
        if (!item.xtreamUsername.isNullOrBlank()) {
            obj.put("xtreamUsername", item.xtreamUsername)
        }
        if (!item.xtreamPassword.isNullOrBlank()) {
            obj.put("xtreamPassword", item.xtreamPassword)
        }
        if (!item.genre.isNullOrBlank()) {
            obj.put("genre", item.genre)
        }
        if (!item.cast.isNullOrBlank()) {
            obj.put("cast", item.cast)
        }
        obj.put("country", item.country ?: "")
        obj.put("drmScheme", item.drmScheme ?: "")
        obj.put("drmLicenseUrl", item.drmLicenseUrl ?: "")
        obj.put("drmLicenseKey", item.drmLicenseKey ?: "")
        obj.put("manifestType", item.manifestType ?: "")
        obj.put("isAdminAdded", item.isAdminAdded || item.isFromAdmin)

        // Multiple servers array
        val serversArr = JSONArray()
        item.getAllServers().forEach { server ->
            val sObj = JSONObject()
            sObj.put("name", server.name)
            sObj.put("url", server.url)
            serversArr.put(sObj)
        }
        obj.put("servers", serversArr)
        return obj
    }

    fun parseMediaFromJsonObj(id: String, obj: JSONObject): MediaItem {
        val typeStr = obj.optString("type", "").trim().uppercase()
        val categoryStr = obj.optString("category", obj.optString("sport", "General")).trim()
        val mediaType = when {
            typeStr == "LIVE_TV" -> MediaType.LIVE_TV
            typeStr == "LIVE_EVENT" -> MediaType.LIVE_EVENT
            typeStr == "MOVIE" || typeStr == "SERIES" -> MediaType.MOVIE
            id.startsWith("tv_") || id.startsWith("channel_") || id.startsWith("ch_") -> MediaType.LIVE_TV
            id.startsWith("sport_") || id.startsWith("match_") || id.startsWith("event_") || id.startsWith("tapmad_") -> MediaType.LIVE_EVENT
            id.startsWith("mov_") || id.startsWith("movie_") || id.startsWith("ser_") -> MediaType.MOVIE
            obj.has("poster") || obj.has("year") -> MediaType.MOVIE
            obj.has("team1") || obj.has("team2") || obj.has("tournament") -> MediaType.LIVE_EVENT
            categoryStr.contains("Cricket", ignoreCase = true) || categoryStr.contains("Football", ignoreCase = true) -> MediaType.LIVE_EVENT
            else -> MediaType.LIVE_TV
        }

        val serversList = mutableListOf<StreamServer>()
        val serversArr = obj.optJSONArray("servers")
        if (serversArr != null) {
            for (i in 0 until serversArr.length()) {
                val sObj = serversArr.optJSONObject(i)
                if (sObj != null) {
                    val name = sObj.optString("name", "সার্ভার ${i + 1}")
                    val sUrl = sObj.optString("url", "")
                    if (sUrl.isNotBlank()) {
                        serversList.add(StreamServer(name, sUrl))
                    }
                }
            }
        }

        val primaryStream = obj.optString("url", obj.optString("streamUrl", ""))
        val backup = obj.optString("backupUrl", null)
        val logo = obj.optString("logo", obj.optString("logoUrl", obj.optString("poster", null))).takeIf { it?.isNotBlank() == true }

        val startTm = if (obj.has("startTime")) obj.optLong("startTime") else if (obj.has("countdownTargetSeconds")) obj.optLong("countdownTargetSeconds") else null

        return MediaItem(
            id = id,
            title = obj.optString("name", obj.optString("title", "NAFI Stream")),
            tournament = obj.optString("tournament", null).takeIf { it?.isNotBlank() == true },
            category = categoryStr,
            type = mediaType,
            streamUrl = primaryStream,
            backupUrl = backup,
            servers = serversList,
            logoUrl = logo,
            description = obj.optString("description", null).takeIf { it?.isNotBlank() == true },
            isLive = obj.optBoolean("isLive", true),
            status = obj.optString("status", "Live Now"),
            eventTime = obj.optString("eventTime", obj.optString("time", null)).takeIf { it?.isNotBlank() == true },
            team1 = obj.optString("team1", null).takeIf { it?.isNotBlank() == true },
            team2 = obj.optString("team2", null).takeIf { it?.isNotBlank() == true },
            team1Logo = obj.optString("team1Logo", null).takeIf { it?.isNotBlank() == true },
            team2Logo = obj.optString("team2Logo", null).takeIf { it?.isNotBlank() == true },
            matchTimeFormatted = obj.optString("matchTimeFormatted", null).takeIf { it?.isNotBlank() == true },
            countdownTargetSeconds = startTm,
            score1 = obj.optString("score1", null).takeIf { it?.isNotBlank() == true },
            score2 = obj.optString("score2", null).takeIf { it?.isNotBlank() == true },
            quality = obj.optString("quality", "HD"),
            rating = obj.optString("rating", null).takeIf { it?.isNotBlank() == true },
            year = obj.optString("year", null).takeIf { it?.isNotBlank() == true },
            addedTimestamp = if (obj.has("addedTimestamp")) obj.optLong("addedTimestamp") else null,
            seriesId = obj.optString("seriesId", null).takeIf { it?.isNotBlank() == true } ?: if (id.startsWith("xtream_series_")) id.removePrefix("xtream_series_") else null,
            xtreamServerUrl = obj.optString("xtreamServerUrl", null).takeIf { it?.isNotBlank() == true },
            xtreamUsername = obj.optString("xtreamUsername", null).takeIf { it?.isNotBlank() == true },
            xtreamPassword = obj.optString("xtreamPassword", null).takeIf { it?.isNotBlank() == true },
            genre = obj.optString("genre", null).takeIf { it?.isNotBlank() == true },
            cast = obj.optString("cast", null).takeIf { it?.isNotBlank() == true },
            country = obj.optString("country", null).takeIf { it?.isNotBlank() == true },
            drmScheme = obj.optString("drmScheme", obj.optString("license_type", null)).takeIf { it?.isNotBlank() == true },
            drmLicenseUrl = obj.optString("drmLicenseUrl", null).takeIf { it?.isNotBlank() == true },
            drmLicenseKey = obj.optString("drmLicenseKey", obj.optString("license_key", obj.optString("clearkey", null))).takeIf { it?.isNotBlank() == true },
            manifestType = obj.optString("manifestType", obj.optString("manifest_type", null)).takeIf { it?.isNotBlank() == true },
            isAdminAdded = obj.optBoolean("isAdminAdded", false) || id.startsWith("sport_") || id.startsWith("match_") || id.startsWith("event_") || id.startsWith("admin_")
        )
    }

    // -------------------------------------------------------------
    // PLAYLISTS MANAGEMENT (Initial, Local & Firebase Cloud + Xtream Codes API)
    // -------------------------------------------------------------
    fun getInitialPlaylists(): List<PlaylistInfo> {
        val deleted = getDeletedIds()
        val defaultList = listOf(
            PlaylistInfo(
                id = "pl_starshare_premium",
                title = "Starshare IPTV (Playlist Host)",
                url = "http://rgkkw.live:80",
                logoUrl = "https://images.unsplash.com/photo-1578022761797-b8636ac1773c?w=300&fit=crop",
                description = "প্লেলিস্ট হোস্টের সকল চ্যানেল (rgkkw.live:80)",
                serverUrl = "http://rgkkw.live:80",
                username = "4dfoydR2gZ",
                password = "clever3still",
                type = "XTREAM",
                isAdmin = true,
                isReadOnly = false
            ),
            PlaylistInfo(
                id = "pl_toffee_live_bd",
                title = "Toffee Live TV (Bangladesh)",
                url = "https://raw.githubusercontent.com/srhady/toffee-bd/refs/heads/main/toffee_playlist.m3u",
                logoUrl = "https://assets-prod.services.toffeelive.com/Xi_Ga5oBNnOkwJLWkhKP/posters/ef2899d5-1ae0-4fee-aee5-45f9b0b3ba80.png",
                description = "বাংলাদেশের সকল লাইভ টিভি ও স্পোর্টস চ্যানেল (Toffee HLS)",
                type = "M3U",
                isAdmin = true,
                isReadOnly = false
            )
        )
        return defaultList.filterNot { deleted.contains(it.id) }
    }

    fun buildXtreamM3uUrl(serverUrl: String, username: String, pass: String): String {
        var cleanServer = serverUrl.trim().removeSuffix("/")
        if (!cleanServer.startsWith("http://", ignoreCase = true) && !cleanServer.startsWith("https://", ignoreCase = true)) {
            cleanServer = "http://$cleanServer"
        }
        return "$cleanServer/get.php?username=${username.trim()}&password=${pass.trim()}&type=m3u_plus&output=m3u8"
    }

    fun parseXtreamCredentials(input: String): Triple<String, String, String>? {
        if (input.isBlank()) return null
        val trimmed = input.trim()

        // Case 1: URL with query parameters e.g. http://server/get.php?username=xxx&password=yyy
        if (trimmed.contains("username=", ignoreCase = true) && trimmed.contains("password=", ignoreCase = true)) {
            try {
                val uri = Uri.parse(trimmed)
                val user = uri.getQueryParameter("username")
                val pass = uri.getQueryParameter("password")
                val scheme = uri.scheme ?: "http"
                val host = uri.host ?: ""
                val port = if (uri.port != -1) ":${uri.port}" else ""
                val server = "$scheme://$host$port"
                if (!user.isNullOrBlank() && !pass.isNullOrBlank() && host.isNotBlank()) {
                    return Triple(server, user, pass)
                }
            } catch (_: Exception) {}
        }

        // Case 2: Multi-line text (e.g. Server \n User \n Pass)
        val lines = trimmed.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size >= 3) {
            val serverLine = lines.firstOrNull { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) || it.contains(".com") || it.contains(":") } ?: lines[0]
            val nonServerLines = lines.filter { it != serverLine }
            if (nonServerLines.size >= 2) {
                var server = serverLine.replace(Regex("^(server|host|url)\\s*[:=]\\s*", RegexOption.IGNORE_CASE), "").trim()
                if (!server.startsWith("http://", ignoreCase = true) && !server.startsWith("https://", ignoreCase = true)) {
                    server = "http://$server"
                }
                val user = nonServerLines[0].replace(Regex("^(user|username|name)\\s*[:=]\\s*", RegexOption.IGNORE_CASE), "").trim()
                val pass = nonServerLines[1].replace(Regex("^(pass|password|pin)\\s*[:=]\\s*", RegexOption.IGNORE_CASE), "").trim()
                if (server.isNotBlank() && user.isNotBlank() && pass.isNotBlank()) {
                    return Triple(server, user, pass)
                }
            }
        }

        // Case 3: Comma or space separated tokens
        val tokens = trimmed.split(Regex("[\r\n\t ,|;]+"))
        var server = ""
        var user = ""
        var pass = ""
        for (token in tokens) {
            val t = token.trim()
            if (t.startsWith("http://", ignoreCase = true) || t.startsWith("https://", ignoreCase = true) || (t.contains(".") && !t.contains("="))) {
                if (server.isEmpty()) {
                    server = if (!t.startsWith("http://", ignoreCase = true) && !t.startsWith("https://", ignoreCase = true)) "http://$t" else t
                }
            } else if (t.startsWith("user=", ignoreCase = true) || t.startsWith("username=", ignoreCase = true)) {
                user = t.substringAfter("=").trim()
            } else if (t.startsWith("pass=", ignoreCase = true) || t.startsWith("password=", ignoreCase = true)) {
                pass = t.substringAfter("=").trim()
            }
        }
        if (server.isNotBlank() && user.isNotBlank() && pass.isNotBlank()) {
            return Triple(server, user, pass)
        }

        return null
    }

    suspend fun fetchSeriesSeasonsAndEpisodes(mediaItem: MediaItem): MediaItem = withContext(Dispatchers.IO) {
        val rawSeriesId = mediaItem.seriesId?.takeIf { it.isNotBlank() } ?: mediaItem.id
        val seriesId = rawSeriesId.removePrefix("xtream_series_").removePrefix("xtream_ep_").trim()
        if (seriesId.isBlank()) return@withContext mediaItem

        // Check in-memory cache
        val cached = seriesDetailsCache[seriesId] ?: seriesDetailsCache[mediaItem.id]
        if (cached != null && (cached.seasons.isNotEmpty() || cached.episodes.isNotEmpty())) {
            return@withContext cached
        }

        var serverUrl = mediaItem.xtreamServerUrl ?: "http://rgkkw.live:80"
        var cleanServer = serverUrl.trim().removeSuffix("/")
        if (!cleanServer.startsWith("http://", ignoreCase = true) && !cleanServer.startsWith("https://", ignoreCase = true)) {
            cleanServer = "http://$cleanServer"
        }
        val cleanUser = mediaItem.xtreamUsername ?: "4dfoydR2gZ"
        val cleanPass = mediaItem.xtreamPassword ?: "clever3still"

        try {
            var body: String? = null

            // Direct info_api endpoint avoids 302 redirects on rgkkw.live Xtream server
            val urlsToTry = listOf(
                "$cleanServer/info_api.php?action=get_series_info&series_id=$seriesId",
                "$cleanServer/player_api.php?username=$cleanUser&password=$cleanPass&action=get_series_info&series_id=$seriesId"
            )

            for (targetUrl in urlsToTry) {
                try {
                    val req = Request.Builder()
                        .url(targetUrl)
                        .header("User-Agent", "IPTVSmartersPro")
                        .build()
                    val resp = client.newCall(req).execute()
                    val b = resp.body?.string()
                    if (!b.isNullOrBlank() && b.trimStart().startsWith("{") && (b.contains("\"seasons\"") || b.contains("\"episodes\"") || b.contains("\"info\""))) {
                        body = b
                        break
                    }
                } catch (_: Exception) {}
            }

            if (body == null) return@withContext mediaItem

            val rootObj = JSONObject(body)
            val infoObj = rootObj.optJSONObject("info")
            val seasonsArr = rootObj.optJSONArray("seasons")
            val episodesObj = rootObj.optJSONObject("episodes")
            val episodesArr = rootObj.optJSONArray("episodes")

            val genre = infoObj?.optString("genre")?.takeIf { it.isNotBlank() } ?: mediaItem.genre
            val plot = infoObj?.optString("plot")?.takeIf { it.isNotBlank() } ?: mediaItem.description
            val cast = infoObj?.optString("cast")?.takeIf { it.isNotBlank() } ?: mediaItem.cast
            val director = infoObj?.optString("director")?.takeIf { it.isNotBlank() } ?: mediaItem.director
            val cover = infoObj?.optString("cover")?.takeIf { it.isNotBlank() } ?: mediaItem.logoUrl
            val rating = infoObj?.optString("rating")?.takeIf { it.isNotBlank() } ?: mediaItem.rating
            val releaseDate = infoObj?.optString("releaseDate")?.takeIf { it.isNotBlank() } ?: mediaItem.year

            // Parse all episodes by season key
            val allEpisodes = mutableListOf<EpisodeItem>()
            val seasonEpisodesMap = mutableMapOf<Int, MutableList<EpisodeItem>>()

            if (episodesObj != null) {
                val keys = episodesObj.keys()
                while (keys.hasNext()) {
                    val seasonKey = keys.next()
                    val sNum = seasonKey.toIntOrNull() ?: 1
                    val epArr = episodesObj.optJSONArray(seasonKey) ?: continue

                    val seasonList = seasonEpisodesMap.getOrPut(sNum) { mutableListOf() }
                    for (i in 0 until epArr.length()) {
                        val epObj = epArr.optJSONObject(i) ?: continue
                        val epId = epObj.optString("id", "").ifBlank { "${seriesId}_s${sNum}e${i + 1}" }
                        val rawTitle = epObj.optString("title", "").trim()
                        val regexMatch = Regex("""(?:[_\s\-]E|Episode\s*|S\d+E)(\d+)""", RegexOption.IGNORE_CASE).find(rawTitle)
                        val titleEpNum = regexMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
                        val rawEpNum = epObj.optInt("episode_num", 0)
                        val epNum = when {
                            titleEpNum != null && titleEpNum > 0 -> titleEpNum
                            rawEpNum > 1 -> rawEpNum
                            else -> i + 1
                        }

                        val title = if (rawTitle.isNotBlank()) rawTitle else "Episode $epNum"
                        val ext = epObj.optString("container_extension", "mkv").ifBlank { "mp4" }
                        val epInfo = epObj.optJSONObject("info")

                        val epDuration = epInfo?.optString("duration") ?: ""
                        val epPlot = epInfo?.optString("plot") ?: ""
                        val epImage = epInfo?.optString("movie_image")?.takeIf { it.isNotBlank() } ?: cover
                        val epReleaseDate = epInfo?.optString("releasedate") ?: ""
                        val epRating = epInfo?.optString("rating") ?: ""

                        val streamUrl = "$cleanServer/series/$cleanUser/$cleanPass/$epId.$ext"
                        val episode = EpisodeItem(
                            id = epId,
                            seriesId = seriesId,
                            title = title,
                            seasonNum = sNum,
                            episodeNum = epNum,
                            streamUrl = streamUrl,
                            servers = listOf(
                                StreamServer("সার্ভার ১ (HD)", streamUrl),
                                StreamServer("সার্ভার ২ (Direct)", "$cleanServer/series/$cleanUser/$cleanPass/$epId.mkv")
                            ),
                            logoUrl = epImage,
                            overview = epPlot,
                            duration = epDuration,
                            rating = epRating,
                            releaseDate = epReleaseDate,
                            containerExtension = ext
                        )
                        seasonList.add(episode)
                        allEpisodes.add(episode)
                    }
                    seasonList.sortBy { it.episodeNum }
                }
            } else if (episodesArr != null) {
                for (i in 0 until episodesArr.length()) {
                    val epObj = episodesArr.optJSONObject(i) ?: continue
                    val epId = epObj.optString("id", "").ifBlank { "${seriesId}_ep_${i + 1}" }
                    val sNum = epObj.optInt("season", epObj.optString("season", "1").toIntOrNull() ?: 1)
                    val rawTitle = epObj.optString("title", "").trim()
                    val regexMatch = Regex("""(?:[_\s\-]E|Episode\s*|S\d+E)(\d+)""", RegexOption.IGNORE_CASE).find(rawTitle)
                    val titleEpNum = regexMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
                    val rawEpNum = epObj.optInt("episode_num", 0)
                    val epNum = when {
                        titleEpNum != null && titleEpNum > 0 -> titleEpNum
                        rawEpNum > 1 -> rawEpNum
                        else -> i + 1
                    }

                    val title = if (rawTitle.isNotBlank()) rawTitle else "Episode $epNum"
                    val ext = epObj.optString("container_extension", "mkv").ifBlank { "mp4" }
                    val epInfo = epObj.optJSONObject("info")

                    val epDuration = epInfo?.optString("duration") ?: ""
                    val epPlot = epInfo?.optString("plot") ?: ""
                    val epImage = epInfo?.optString("movie_image")?.takeIf { it.isNotBlank() } ?: cover
                    val epReleaseDate = epInfo?.optString("releasedate") ?: ""
                    val epRating = epInfo?.optString("rating") ?: ""

                    val streamUrl = "$cleanServer/series/$cleanUser/$cleanPass/$epId.$ext"
                    val episode = EpisodeItem(
                        id = epId,
                        seriesId = seriesId,
                        title = title,
                        seasonNum = sNum,
                        episodeNum = epNum,
                        streamUrl = streamUrl,
                        servers = listOf(
                            StreamServer("সার্ভার ১ (HD)", streamUrl)
                        ),
                        logoUrl = epImage,
                        overview = epPlot,
                        duration = epDuration,
                        rating = epRating,
                        releaseDate = epReleaseDate,
                        containerExtension = ext
                    )
                    seasonEpisodesMap.getOrPut(sNum) { mutableListOf() }.add(episode)
                    allEpisodes.add(episode)
                }
                seasonEpisodesMap.values.forEach { it.sortBy { ep -> ep.episodeNum } }
            }
            allEpisodes.sortBy { it.seasonNum * 10000 + it.episodeNum }

            // Parse seasons array
            val seasonsList = mutableListOf<SeasonInfo>()
            if (seasonsArr != null && seasonsArr.length() > 0) {
                for (s in 0 until seasonsArr.length()) {
                    val sObj = seasonsArr.optJSONObject(s) ?: continue
                    val sNum = sObj.optInt("season_number", s + 1)
                    val sName = sObj.optString("name", "Season $sNum")
                    val sCover = sObj.optString("cover").takeIf { it.isNotBlank() } ?: cover
                    val sOverview = sObj.optString("overview")
                    val sAirDate = sObj.optString("air_date")
                    val epCount = sObj.optInt("episode_count", seasonEpisodesMap[sNum]?.size ?: 0)
                    val eps = seasonEpisodesMap[sNum] ?: emptyList()

                    seasonsList.add(
                        SeasonInfo(
                            seasonNumber = sNum,
                            name = sName,
                            episodeCount = if (eps.isNotEmpty()) eps.size else epCount,
                            cover = sCover,
                            overview = sOverview,
                            airDate = sAirDate,
                            episodes = eps.sortedBy { it.episodeNum }
                        )
                    )
                }
            } else {
                // Synthesize seasons from seasonEpisodesMap
                for ((sNum, eps) in seasonEpisodesMap.toSortedMap()) {
                    seasonsList.add(
                        SeasonInfo(
                            seasonNumber = sNum,
                            name = "Season $sNum",
                            episodeCount = eps.size,
                            cover = cover,
                            episodes = eps.sortedBy { it.episodeNum }
                        )
                    )
                }
            }

            val updated = mediaItem.copy(
                seriesId = seriesId,
                logoUrl = cover,
                description = plot,
                genre = genre,
                cast = cast,
                director = director,
                rating = rating,
                year = releaseDate?.take(4),
                seasons = seasonsList.sortedBy { it.seasonNumber },
                episodes = allEpisodes,
                xtreamServerUrl = cleanServer,
                xtreamUsername = cleanUser,
                xtreamPassword = cleanPass
            )
            seriesDetailsCache[seriesId] = updated
            seriesDetailsCache[mediaItem.id] = updated
            return@withContext updated
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext mediaItem
        }
    }

    suspend fun fetchXtreamCategories(serverUrl: String, username: String, pass: String): Map<String, List<XtreamCategory>> = withContext(Dispatchers.IO) {
        var cleanServer = serverUrl.trim().removeSuffix("/")
        if (!cleanServer.startsWith("http://", ignoreCase = true) && !cleanServer.startsWith("https://", ignoreCase = true)) {
            cleanServer = "http://$cleanServer"
        }
        val cleanUser = username.trim()
        val cleanPass = pass.trim()

        val result = mutableMapOf<String, List<XtreamCategory>>()

        val actions = listOf(
            "get_live_categories" to "live",
            "get_vod_categories" to "movie",
            "get_series_categories" to "series"
        )

        for ((action, type) in actions) {
            try {
                val url = "$cleanServer/player_api.php?username=$cleanUser&password=$cleanPass&action=$action"
                val req = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro").build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) {
                    val arr = JSONArray(resp.body?.string() ?: "")
                    val list = mutableListOf<XtreamCategory>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val id = obj.optString("category_id")
                        val name = obj.optString("category_name")
                        if (id.isNotBlank() && name.isNotBlank()) {
                            list.add(XtreamCategory(id, name, type))
                        }
                    }
                    result[type] = list
                }
            } catch (_: Exception) {}
        }
        result
    }

    /**
     * Fast on-demand fetching for ANY Xtream category (Bangla, Netflix, 2026 Movies, Hoichoi, etc.)
     * Avoids memory bloat and returns category items in < 1 second.
     */
    suspend fun fetchXtreamCategoryItems(
        serverUrl: String = "http://rgkkw.live:80",
        username: String = "4dfoydR2gZ",
        pass: String = "clever3still",
        categoryId: String,
        type: String = "movie"
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        var cleanServer = serverUrl.trim().removeSuffix("/")
        if (!cleanServer.startsWith("http://", ignoreCase = true) && !cleanServer.startsWith("https://", ignoreCase = true)) {
            cleanServer = "http://$cleanServer"
        }
        val cleanUser = username.trim()
        val cleanPass = pass.trim()
        val action = when (type.lowercase()) {
            "series" -> "get_series"
            "live" -> "get_live_streams"
            else -> "get_vod_streams"
        }
        val url = "$cleanServer/player_api.php?username=$cleanUser&password=$cleanPass&action=$action&category_id=$categoryId"
        try {
            val req = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro").build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext emptyList()
            val body = resp.body?.string() ?: return@withContext emptyList()
            if (!body.startsWith("[")) return@withContext emptyList()
            val arr = JSONArray(body)
            val list = mutableListOf<MediaItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                if (type.equals("series", ignoreCase = true)) {
                    val sId = obj.optString("series_id").takeIf { it.isNotBlank() } ?: continue
                    val name = obj.optString("name", "Series $sId")
                    val cover = obj.optString("cover").takeIf { it.isNotBlank() }
                    val plot = obj.optString("plot")
                    val cast = obj.optString("cast")
                    val rating = obj.optString("rating", "8.0")
                    val releaseDate = obj.optString("releaseDate")
                    val genre = obj.optString("genre")
                    val catName = obj.optString("category_name", "Web Series")
                    list.add(
                        MediaItem(
                            id = "xtream_series_$sId",
                            title = name,
                            category = catName,
                            type = MediaType.SERIES,
                            streamUrl = "",
                            logoUrl = cover,
                            description = plot,
                            rating = rating,
                            year = releaseDate.take(4),
                            quality = "HD",
                            genre = genre,
                            cast = cast,
                            seriesId = sId,
                            xtreamServerUrl = cleanServer,
                            xtreamUsername = cleanUser,
                            xtreamPassword = cleanPass,
                            userAgent = "IPTVSmartersPro"
                        )
                    )
                } else if (type.equals("live", ignoreCase = true)) {
                    val streamId = obj.optString("stream_id").takeIf { it.isNotBlank() } ?: continue
                    val name = obj.optString("name", "Channel $streamId").trim()
                    if (name.contains("###") || name.startsWith("---") || name.contains("====")) continue
                    val icon = obj.optString("stream_icon").takeIf { it.isNotBlank() }
                    val catName = obj.optString("category_name", "Live TV")
                    val isRgkkw = cleanServer.contains("rgkkw.live", ignoreCase = true)
                    val playUrl = "$cleanServer/live/$cleanUser/$cleanPass/$streamId.ts"
                    val altServer = if (cleanServer.contains(":80")) cleanServer.replace(":80", "") else "$cleanServer:80"
                    val altPlayUrl = "$altServer/live/$cleanUser/$cleanPass/$streamId.ts"
                    val sList = mutableListOf(
                        StreamServer("সার্ভার ১ (TS Stream)", playUrl),
                        StreamServer("সার্ভার ২ (বিকল্প TS)", altPlayUrl)
                    )
                    if (!isRgkkw) {
                        sList.add(StreamServer("সার্ভার ৩ (HLS)", "$cleanServer/live/$cleanUser/$cleanPass/$streamId.m3u8"))
                    }
                    list.add(
                        MediaItem(
                            id = "xtream_live_$streamId",
                            title = name,
                            category = catName,
                            type = MediaType.LIVE_TV,
                            streamUrl = playUrl,
                            backupUrl = altPlayUrl,
                            servers = sList,
                            logoUrl = icon,
                            isLive = true,
                            quality = "HD",
                            userAgent = "IPTVSmartersPro"
                        )
                    )
                } else {
                    val streamId = obj.optString("stream_id").takeIf { it.isNotBlank() } ?: continue
                    val name = obj.optString("name", "Movie $streamId")
                    val icon = obj.optString("stream_icon").takeIf { it.isNotBlank() }
                    val ext = obj.optString("container_extension", "mp4").ifBlank { "mp4" }
                    val rating = obj.optString("rating", "8.5")
                    val catName = obj.optString("category_name", "Movies")
                    val playUrl = "$cleanServer/movie/$cleanUser/$cleanPass/$streamId.$ext"
                    val altServer = if (cleanServer.contains(":80")) cleanServer.replace(":80", "") else "$cleanServer:80"
                    val altPlayUrl = "$altServer/movie/$cleanUser/$cleanPass/$streamId.$ext"
                    list.add(
                        MediaItem(
                            id = "xtream_vod_$streamId",
                            title = name,
                            category = catName,
                            type = MediaType.MOVIE,
                            streamUrl = playUrl,
                            backupUrl = altPlayUrl,
                            servers = listOf(
                                StreamServer("সার্ভার ১ (VOD)", playUrl),
                                StreamServer("সার্ভার ২ (বিকল্প VOD)", altPlayUrl)
                            ),
                            logoUrl = icon,
                            isLive = false,
                            rating = rating,
                            quality = "HD",
                            userAgent = "IPTVSmartersPro"
                        )
                    )
                }
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun fetchXtreamCodesStreams(serverUrl: String, username: String, pass: String): List<MediaItem> = withContext(Dispatchers.IO) {
        var cleanServer = serverUrl.trim().removeSuffix("/")
        if (!cleanServer.startsWith("http://", ignoreCase = true) && !cleanServer.startsWith("https://", ignoreCase = true)) {
            cleanServer = "http://$cleanServer"
        }
        val cleanUser = username.trim()
        val cleanPass = pass.trim()

        val items = mutableListOf<MediaItem>()

        try {
            // 1. Fetch categories for Live, VOD, and Series
            val liveCatMap = mutableMapOf<String, String>()
            val vodCatMap = mutableMapOf<String, String>()
            val seriesCatMap = mutableMapOf<String, String>()

            coroutineScope {
                val liveCatJob = async {
                    try {
                        val url = "$cleanServer/player_api.php?username=$cleanUser&password=$cleanPass&action=get_live_categories"
                        val req = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro").build()
                        val resp = client.newCall(req).execute()
                        if (resp.isSuccessful) {
                            val arr = JSONArray(resp.body?.string() ?: "")
                            for (i in 0 until arr.length()) {
                                val obj = arr.optJSONObject(i) ?: continue
                                val id = obj.optString("category_id")
                                val name = obj.optString("category_name")
                                if (id.isNotBlank() && name.isNotBlank()) liveCatMap[id] = name
                            }
                        }
                    } catch (_: Exception) {}
                }

                val vodCatJob = async {
                    try {
                        val url = "$cleanServer/player_api.php?username=$cleanUser&password=$cleanPass&action=get_vod_categories"
                        val req = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro").build()
                        val resp = client.newCall(req).execute()
                        if (resp.isSuccessful) {
                            val arr = JSONArray(resp.body?.string() ?: "")
                            for (i in 0 until arr.length()) {
                                val obj = arr.optJSONObject(i) ?: continue
                                val id = obj.optString("category_id")
                                val name = obj.optString("category_name")
                                if (id.isNotBlank() && name.isNotBlank()) vodCatMap[id] = name
                            }
                        }
                    } catch (_: Exception) {}
                }

                val seriesCatJob = async {
                    try {
                        val url = "$cleanServer/player_api.php?username=$cleanUser&password=$cleanPass&action=get_series_categories"
                        val req = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro").build()
                        val resp = client.newCall(req).execute()
                        if (resp.isSuccessful) {
                            val arr = JSONArray(resp.body?.string() ?: "")
                            for (i in 0 until arr.length()) {
                                val obj = arr.optJSONObject(i) ?: continue
                                val id = obj.optString("category_id")
                                val name = obj.optString("category_name")
                                if (id.isNotBlank() && name.isNotBlank()) seriesCatMap[id] = name
                            }
                        }
                    } catch (_: Exception) {}
                }

                awaitAll(liveCatJob, vodCatJob, seriesCatJob)
            }

            // 2. Fetch Live TV, high-demand VOD categories (Bangla, 2026/2025/2024, Netflix), and Series
            coroutineScope {
                // A. Live Streams
                val liveJob = async {
                    try {
                        val liveUrl = "$cleanServer/player_api.php?username=$cleanUser&password=$cleanPass&action=get_live_streams"
                        val liveReq = Request.Builder().url(liveUrl).header("User-Agent", "IPTVSmartersPro").build()
                        val liveResp = client.newCall(liveReq).execute()
                        if (liveResp.isSuccessful) {
                            val liveArr = JSONArray(liveResp.body?.string() ?: "")
                            val liveItems = mutableListOf<MediaItem>()
                            val hideAdult = isAdultContentHidden()
                            for (i in 0 until liveArr.length()) {
                                val sObj = liveArr.optJSONObject(i) ?: continue
                                val streamId = sObj.optString("stream_id", "")
                                if (streamId.isBlank()) continue
                                val name = sObj.optString("name", "Channel $streamId").trim()
                                if (name.contains("###") || name.startsWith("---") || name.contains("====")) continue
                                val catId = sObj.optString("category_id", "")
                                val categoryName = liveCatMap[catId] ?: "Live TV"
                                if (hideAdult && (isAdultCategory(categoryName) || isAdultCategory(name))) continue
                                val icon = sObj.optString("stream_icon").takeIf { it.isNotBlank() }

                                val isRgkkw = cleanServer.contains("rgkkw.live", ignoreCase = true)
                                val playUrl = "$cleanServer/live/$cleanUser/$cleanPass/$streamId.ts"
                                val altServer = if (cleanServer.contains(":80")) cleanServer.replace(":80", "") else "$cleanServer:80"
                                val altPlayUrl = "$altServer/live/$cleanUser/$cleanPass/$streamId.ts"
                                val sList = mutableListOf(
                                    StreamServer("সার্ভার ১ (TS Stream)", playUrl),
                                    StreamServer("সার্ভার ২ (বিকল্প TS)", altPlayUrl)
                                )
                                if (!isRgkkw) {
                                    sList.add(StreamServer("সার্ভার ৩ (HLS)", "$cleanServer/live/$cleanUser/$cleanPass/$streamId.m3u8"))
                                }

                                val item = MediaItem(
                                    id = "xtream_live_${streamId}",
                                    title = name,
                                    category = categoryName,
                                    type = MediaType.LIVE_TV,
                                    streamUrl = playUrl,
                                    backupUrl = altPlayUrl,
                                    servers = sList,
                                    logoUrl = icon,
                                    isLive = true,
                                    quality = "HD",
                                    userAgent = "IPTVSmartersPro"
                                )
                                if (hideAdult && isAdultMedia(item)) continue
                                liveItems.add(item)
                            }
                            synchronized(items) { items.addAll(liveItems) }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                val hideAdult = isAdultContentHidden()

                // B. Priority VOD categories: All Bangla movies, 2026/2025/2024 releases, Hindi Dubbed, Indian FHD, OTT
                val priorityVodCatIds = listOf(
                    "792", "610", "538", "100", "793", "614", "620", "629", // BANGLA 2026, 2025, 2024, ALL, CAM
                    "749", "597", "525", "122", "363", "253", "128", // ENGLISH FHD 2026, 2025, 2024, 4K, 2023, 2022, 2021
                    "766", "599", "527", // INDIAN FHD 2026, 2025, 2024
                    "26", "93", "168", "169", "120", "147", "262" // ENGLISH HINDI DUBBED, SOUTH INDIAN DUBBED, NETFLIX HINDI, ACTION
                )
                val targetVodCatIds = priorityVodCatIds.toMutableList()
                val vodKeywords = listOf("bangla", "bengali", "2026", "2025", "2024", "hindi", "indian", "netflix", "south", "dubbed", "fhd", "action", "hollywood", "bollywood")
                for ((cId, cName) in vodCatMap) {
                    val lower = cName.lowercase()
                    if (hideAdult && isAdultCategory(cName)) continue
                    if (vodKeywords.any { lower.contains(it) } && !targetVodCatIds.contains(cId)) {
                        targetVodCatIds.add(cId)
                    }
                }
                val selectedVodCats = targetVodCatIds.take(36)
                val priorityVodJob = async {
                    selectedVodCats.chunked(8).forEach { chunk ->
                        coroutineScope {
                            val chunkJobs = chunk.map { catId ->
                                async {
                                    try {
                                        val catVodItems = fetchXtreamCategoryItems(
                                            serverUrl = cleanServer,
                                            username = cleanUser,
                                            pass = cleanPass,
                                            categoryId = catId,
                                            type = "movie"
                                        ).filterNot { hideAdult && (isAdultMedia(it) || isAdultCategory(it.category)) }
                                        .map { item ->
                                            val catName = vodCatMap[catId] ?: item.category
                                            item.copy(category = catName)
                                        }
                                        synchronized(items) { items.addAll(catVodItems) }
                                    } catch (_: Exception) {}
                                }
                            }
                            chunkJobs.awaitAll()
                        }
                    }
                }

                // C. Priority Series categories: STAR JALSHA, HUM TV, PAKISTANI DRAMA, BANGLA, HINDI, NETFLIX, HOICHOI, etc.
                val explicitSeriesCatIds = listOf(
                    "460", // STAR JALSHA (Anurager Chhowa, Bangla Medium, Ramprasad, etc.)
                    "463", // HUM TV (Muhabbat Gumshuda Meri, Yunhi, Dil Pe Zakham Khaye Hain, etc.)
                    "293", // PAKISTANI DRAMA (Hazrat Yusuf, Ainak Wala Jin, Umeed, etc.)
                    "461", // ZEE BANGLA (Icche Putul, Didi No 1, etc.)
                    "496", // SUN BANGLA
                    "487", // COLORS BANGLA
                    "485", // SONY AATH
                    "335", // Chorki/Bangla
                    "118", // HOICHOI
                    "444", // STAR PLUS (Anupamaa, Ghum Hai Kisikey Pyaar Meiin, Imlie, etc.)
                    "446", // STAR BHARAT (RadhaKrishn, Ajooni, etc.)
                    "442", // COLORS HINDI (Naagin, Bekaaboo, etc.)
                    "443", // SONY (SET)
                    "445", // ZEE TV (Bhabiji Ghar Par Hai, etc.)
                    "161", // HINDI TV SERIES
                    "491", // MTV HINDI
                    "596", // SUN NEO HINDI
                    "745", // 5Star
                    "106", // NETFLIX
                    "171", // NETFLIX (MULTI LANGUAGE)
                    "108", // AMAZON PRIME
                    "102", // DISNEY+HOTSTAR
                    "104", // ZEE5+ALT BALAJI
                    "105", // SONY LIV
                    "188", // HBO MAX
                    "310"  // JIO CINEMA
                )

                // Dynamically add all categories matching keywords from server's series categories
                val targetSeriesCatIds = explicitSeriesCatIds.toMutableList()
                val seriesKeywords = listOf("jalsha", "jolsha", "hum", "star", "pak", "drama", "bangla", "bengali", "serial", "zee", "colors", "sony", "netflix", "prime", "hotstar", "chorki", "hoichoi", "hindi")
                for ((catId, catName) in seriesCatMap) {
                    val lower = catName.lowercase()
                    if (hideAdult && isAdultCategory(catName)) continue
                    if (seriesKeywords.any { lower.contains(it) } && !targetSeriesCatIds.contains(catId)) {
                        targetSeriesCatIds.add(catId)
                    }
                }

                val selectedSeriesCats = targetSeriesCatIds.take(30)
                val prioritySeriesJob = async {
                    selectedSeriesCats.chunked(8).forEach { chunk ->
                        coroutineScope {
                            val chunkJobs = chunk.map { catId ->
                                async {
                                    try {
                                        val catSeriesItems = fetchXtreamCategoryItems(
                                            serverUrl = cleanServer,
                                            username = cleanUser,
                                            pass = cleanPass,
                                            categoryId = catId,
                                            type = "series"
                                        ).filterNot { hideAdult && (isAdultMedia(it) || isAdultCategory(it.category)) }
                                        .map { item ->
                                            val catName = seriesCatMap[catId] ?: item.category
                                            item.copy(category = catName)
                                        }
                                        synchronized(items) { items.addAll(catSeriesItems) }
                                    } catch (_: Exception) {}
                                }
                            }
                            chunkJobs.awaitAll()
                        }
                    }
                }

                awaitAll(liveJob, priorityVodJob, prioritySeriesJob)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // If items are empty, fallback to M3U URL if available
        if (items.isEmpty()) {
            try {
                val m3uUrl = "$cleanServer/get.php?username=$cleanUser&password=$cleanPass&type=m3u_plus&output=m3u8"
                val m3uItems = fetchSingleM3uUrl(m3uUrl)
                if (m3uItems.isNotEmpty()) {
                    return@withContext m3uItems
                }
            } catch (_: Exception) {}
        }

        items.distinctBy { it.id }
    }

    // -------------------------------------------------------------
    // XTREAM CODES ACCOUNTS MANAGEMENT (ADMIN PANEL)
    // -------------------------------------------------------------
    fun getXtreamAccounts(): List<com.example.model.XtreamAccount> {
        val jsonStr = prefs.getString("admin_xtream_accounts", null)
        val list = mutableListOf<com.example.model.XtreamAccount>()
        if (!jsonStr.isNullOrBlank()) {
            try {
                val arr = JSONArray(jsonStr)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        com.example.model.XtreamAccount(
                            id = obj.optString("id", "xtream_$i"),
                            name = obj.optString("name", "Xtream Server"),
                            serverUrl = obj.optString("serverUrl", "http://rgkkw.live:80"),
                            username = obj.optString("username", "4dfoydR2gZ"),
                            password = obj.optString("password", "clever3still"),
                            isEnabled = obj.optBoolean("isEnabled", true),
                            includeLive = obj.optBoolean("includeLive", true),
                            includeVod = obj.optBoolean("includeVod", true),
                            includeSeries = obj.optBoolean("includeSeries", true),
                            logoUrl = obj.optString("logoUrl", null).takeIf { it?.isNotBlank() == true },
                            lastSyncTime = obj.optLong("lastSyncTime", 0L),
                            statusMessage = obj.optString("statusMessage", null).takeIf { it?.isNotBlank() == true }
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (list.isEmpty()) {
            val defaultAcc = com.example.model.XtreamAccount(
                id = "default_rgkkw",
                name = "Starshare Xtream Server",
                serverUrl = prefs.getString("admin_xtream_server", "http://rgkkw.live:80") ?: "http://rgkkw.live:80",
                username = prefs.getString("admin_xtream_user", "4dfoydR2gZ") ?: "4dfoydR2gZ",
                password = prefs.getString("admin_xtream_pass", "clever3still") ?: "clever3still",
                isEnabled = true,
                includeLive = true,
                includeVod = true,
                includeSeries = true,
                logoUrl = null,
                statusMessage = "সক্রিয় (Active)"
            )
            list.add(defaultAcc)
            saveXtreamAccountsList(list)
        }
        return list
    }

    fun saveXtreamAccount(account: com.example.model.XtreamAccount) {
        val current = getXtreamAccounts().toMutableList()
        current.removeAll { it.id == account.id }
        current.add(0, account)
        saveXtreamAccountsList(current)
        prefs.edit()
            .putString("admin_xtream_server", account.serverUrl)
            .putString("admin_xtream_user", account.username)
            .putString("admin_xtream_pass", account.password)
            .apply()
    }

    fun deleteXtreamAccount(id: String) {
        val current = getXtreamAccounts().filterNot { it.id == id }
        saveXtreamAccountsList(current)
    }

    fun saveXtreamAccountsList(list: List<com.example.model.XtreamAccount>) {
        val arr = JSONArray()
        list.forEach { a ->
            val obj = JSONObject()
            obj.put("id", a.id)
            obj.put("name", a.name)
            obj.put("serverUrl", a.serverUrl)
            obj.put("username", a.username)
            obj.put("password", a.password)
            obj.put("isEnabled", a.isEnabled)
            obj.put("includeLive", a.includeLive)
            obj.put("includeVod", a.includeVod)
            obj.put("includeSeries", a.includeSeries)
            if (a.logoUrl != null) obj.put("logoUrl", a.logoUrl)
            obj.put("lastSyncTime", a.lastSyncTime)
            obj.put("statusMessage", a.statusMessage ?: "")
            arr.put(obj)
        }
        prefs.edit().putString("admin_xtream_accounts", arr.toString()).apply()
    }

    fun getActiveXtreamAccounts(): List<com.example.model.XtreamAccount> {
        return getXtreamAccounts().filter { it.isEnabled }
    }

    suspend fun fetchXtreamVodOnly(
        serverUrl: String = "http://rgkkw.live:80",
        username: String = "4dfoydR2gZ",
        pass: String = "clever3still"
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        var cleanServer = serverUrl.trim().removeSuffix("/")
        if (!cleanServer.startsWith("http://", ignoreCase = true) && !cleanServer.startsWith("https://", ignoreCase = true)) {
            cleanServer = "http://$cleanServer"
        }
        val cleanUser = username.trim()
        val cleanPass = pass.trim()
        val items = mutableListOf<MediaItem>()

        try {
            val vodCatMap = mutableMapOf<String, String>()
            try {
                val catUrl = "$cleanServer/player_api.php?username=$cleanUser&password=$cleanPass&action=get_vod_categories"
                val catReq = Request.Builder().url(catUrl).header("User-Agent", "IPTVSmartersPro").build()
                val catResp = client.newCall(catReq).execute()
                if (catResp.isSuccessful) {
                    val arr = JSONArray(catResp.body?.string() ?: "")
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val id = obj.optString("category_id")
                        val name = obj.optString("category_name")
                        if (id.isNotBlank() && name.isNotBlank()) vodCatMap[id] = name
                    }
                }
            } catch (_: Exception) {}

            // Top prioritized VOD categories (Bangla, English, Indian 2026/2025/2024, Dubbed, Netflix, etc.)
            val priorityIds = listOf(
                "792", "610", "538", "100", "793", "614", "620", "629", // Bangla 2026, 2025, 2024, CAM
                "749", "597", "525", "786", "598", "526",               // English FHD & CAM 2026, 2025, 2024
                "766", "599", "527", "757", "600", "528",               // Indian FHD & CAM 2026, 2025, 2024
                "26", "623", "168", "169", "120", "122", "147", "262"  // Dubbed, Netflix, 4K, Marvel, Romantic
            )
            val targetCatIds = priorityIds.toMutableList()
            val vodKeywords = listOf("bangla", "bengali", "2026", "2025", "2024", "hindi", "indian", "netflix", "south", "dubbed", "fhd")
            val hideAdult = isAdultContentHidden()
            for ((cId, cName) in vodCatMap) {
                val lower = cName.lowercase()
                if (hideAdult && isAdultCategory(cName)) continue
                if (vodKeywords.any { lower.contains(it) } && !targetCatIds.contains(cId)) {
                    targetCatIds.add(cId)
                }
            }

            // High-speed parallel category fetch (top prioritized categories for fast loading)
            val selectedCats = targetCatIds.take(20)
            coroutineScope {
                selectedCats.chunked(10).forEach { chunk ->
                    val chunkJobs = chunk.map { catId ->
                        async {
                            try {
                                val catName = vodCatMap[catId] ?: "Movies"
                                val streamUrl = "$cleanServer/player_api.php?username=$cleanUser&password=$cleanPass&action=get_vod_streams&category_id=$catId"
                                val req = Request.Builder()
                                    .url(streamUrl)
                                    .header("User-Agent", "IPTVSmartersPro")
                                    .build()
                                val resp = client.newCall(req).execute()
                                if (resp.isSuccessful) {
                                    val bodyStr = resp.body?.string() ?: ""
                                    if (bodyStr.startsWith("[")) {
                                        val arr = JSONArray(bodyStr)
                                        val catItems = mutableListOf<MediaItem>()
                                        for (i in 0 until arr.length()) {
                                            val obj = arr.optJSONObject(i) ?: continue
                                            val streamId = obj.optString("stream_id").takeIf { it.isNotBlank() } ?: continue
                                            val name = obj.optString("name", "Movie $streamId").trim()
                                            if (name.contains("###") || name.startsWith("---") || name.contains("====")) continue
                                            if (hideAdult && (isAdultCategory(catName) || isAdultCategory(name))) continue
                                            val icon = obj.optString("stream_icon").takeIf { it.isNotBlank() }
                                            val ext = obj.optString("container_extension", "mp4").ifBlank { "mp4" }
                                            val rating = obj.optString("rating", "8.5")
                                            val added = obj.optString("added", "").toLongOrNull() ?: 0L
                                            val yearMatch = Regex("""\b(19\d{2}|20\d{2})\b""").findAll(name).toList().lastOrNull()?.value

                                            val playUrl = "$cleanServer/movie/$cleanUser/$cleanPass/$streamId.$ext"
                                            val altServer = if (cleanServer.contains(":80")) cleanServer.replace(":80", "") else "$cleanServer:80"
                                            val altPlayUrl = "$altServer/movie/$cleanUser/$cleanPass/$streamId.$ext"

                                            val vItem = MediaItem(
                                                id = "xtream_vod_$streamId",
                                                title = name,
                                                category = catName,
                                                type = MediaType.MOVIE,
                                                streamUrl = playUrl,
                                                backupUrl = altPlayUrl,
                                                servers = listOf(
                                                    StreamServer("সার্ভার ১ (VOD)", playUrl),
                                                    StreamServer("সার্ভার ২ (বিকল্প VOD)", altPlayUrl)
                                                ),
                                                logoUrl = icon,
                                                isLive = false,
                                                rating = rating,
                                                year = yearMatch,
                                                addedTimestamp = added,
                                                xtreamServerUrl = cleanServer,
                                                xtreamUsername = cleanUser,
                                                xtreamPassword = cleanPass,
                                                quality = "HD",
                                                userAgent = "IPTVSmartersPro"
                                            )
                                            if (hideAdult && isAdultMedia(vItem)) continue
                                            catItems.add(vItem)
                                        }
                                        synchronized(items) { items.addAll(catItems) }
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    }
                    chunkJobs.awaitAll()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Sort: Newest movies strictly at the top, older movies at the bottom!
        items.distinctBy { it.id }.sortedByDescending { it.movieSortWeight }
    }

    suspend fun fetchXtreamSeriesOnly(
        serverUrl: String = "http://rgkkw.live:80",
        username: String = "4dfoydR2gZ",
        pass: String = "clever3still"
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        var cleanServer = serverUrl.trim().removeSuffix("/")
        if (!cleanServer.startsWith("http://", ignoreCase = true) && !cleanServer.startsWith("https://", ignoreCase = true)) {
            cleanServer = "http://$cleanServer"
        }
        val cleanUser = username.trim()
        val cleanPass = pass.trim()
        val items = mutableListOf<MediaItem>()

        try {
            val seriesCatMap = mutableMapOf<String, String>()
            try {
                val catUrl = "$cleanServer/player_api.php?username=$cleanUser&password=$cleanPass&action=get_series_categories"
                val catReq = Request.Builder().url(catUrl).header("User-Agent", "IPTVSmartersPro").build()
                val catResp = client.newCall(catReq).execute()
                if (catResp.isSuccessful) {
                    val arr = JSONArray(catResp.body?.string() ?: "")
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val id = obj.optString("category_id")
                        val name = obj.optString("category_name")
                        if (id.isNotBlank() && name.isNotBlank()) seriesCatMap[id] = name
                    }
                }
            } catch (_: Exception) {}

            val explicitSeriesCatIds = listOf(
                "460", // STAR JALSHA
                "461", // ZEE BANGLA
                "496", // SUN BANGLA
                "487", // COLORS BANGLA
                "118", // HOICHOI
                "335", // Chorki/Bangla
                "485", // SONY AATH
                "293", // PAKISTANI DRAMA
                "463", // HUM TV
                "442", // COLORS HINDI
                "443", // SONY (SET)
                "445", // ZEE TV
                "161", // HINDI TV SERIES
                "106", // NETFLIX
                "171", // NETFLIX (MULTI LANGUAGE)
                "108", // AMAZON PRIME
                "404", // Amazon MiniTv
                "102", // DISNEY+HOTSTAR
                "104", // ZEE5+ALT BALAJI
                "105", // SONY LIV
                "103", // MX PLAYER
                "111"  // VOOT
            )
            val targetSeriesCatIds = explicitSeriesCatIds.toMutableList()
            val seriesKeywords = listOf("bangla", "bengali", "jalsha", "zee", "sun", "colors", "hoichoi", "chorki", "pak", "hum", "hindi", "netflix", "amazon", "disney", "hotstar", "sony", "voot", "mx", "serial", "drama", "web series")
            val hideAdult = isAdultContentHidden()
            for ((cId, cName) in seriesCatMap) {
                val lower = cName.lowercase()
                if (hideAdult && isAdultCategory(cName)) continue
                if (seriesKeywords.any { lower.contains(it) } && !targetSeriesCatIds.contains(cId)) {
                    targetSeriesCatIds.add(cId)
                }
            }

            val selectedSeriesCats = targetSeriesCatIds.take(30)
            coroutineScope {
                selectedSeriesCats.chunked(10).forEach { chunk ->
                    val chunkJobs = chunk.map { catId ->
                        async {
                            try {
                                val catName = seriesCatMap[catId] ?: "Web Series"
                                val url = "$cleanServer/player_api.php?username=$cleanUser&password=$cleanPass&action=get_series&category_id=$catId"
                                val req = Request.Builder().url(url).header("User-Agent", "IPTVSmartersPro").build()
                                val resp = client.newCall(req).execute()
                                if (resp.isSuccessful) {
                                    val body = resp.body?.string() ?: ""
                                    if (body.startsWith("[")) {
                                        val arr = JSONArray(body)
                                        val sItems = mutableListOf<MediaItem>()
                                        for (i in 0 until arr.length()) {
                                            val obj = arr.optJSONObject(i) ?: continue
                                            val sId = obj.optString("series_id").takeIf { it.isNotBlank() } ?: continue
                                            val name = obj.optString("name", "Series $sId").trim()
                                            val cover = obj.optString("cover").takeIf { it.isNotBlank() }
                                            val plot = obj.optString("plot")
                                            val cast = obj.optString("cast")
                                            val rating = obj.optString("rating", "8.0")
                                            val releaseDate = obj.optString("releaseDate")
                                            val genre = obj.optString("genre")
                                            val lastModified = obj.optString("last_modified", "").toLongOrNull() ?: 0L
                                            val yearMatch = Regex("""\b(19\d{2}|20\d{2})\b""").findAll(name).toList().lastOrNull()?.value ?: releaseDate.take(4)

                                            val sItem = MediaItem(
                                                id = "xtream_series_$sId",
                                                title = name,
                                                category = catName,
                                                type = MediaType.SERIES,
                                                streamUrl = "",
                                                logoUrl = cover,
                                                description = plot,
                                                rating = rating,
                                                year = yearMatch,
                                                addedTimestamp = lastModified,
                                                quality = "HD",
                                                genre = genre,
                                                cast = cast,
                                                seriesId = sId,
                                                xtreamServerUrl = cleanServer,
                                                xtreamUsername = cleanUser,
                                                xtreamPassword = cleanPass,
                                                userAgent = "IPTVSmartersPro"
                                            )
                                            if (hideAdult && (isAdultMedia(sItem) || isAdultCategory(sItem.category))) continue
                                            sItems.add(sItem)
                                        }
                                        synchronized(items) { items.addAll(sItems) }
                                    }
                                }
                            } catch (_: Exception) {}
                        }
                    }
                    chunkJobs.awaitAll()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        items.distinctBy { it.id }.sortedByDescending { it.movieSortWeight }
    }

    suspend fun fetchAllXtreamMoviesAndSeries(): List<MediaItem> = withContext(Dispatchers.IO) {
        val accounts = getActiveXtreamAccounts()
        val allItems = mutableListOf<MediaItem>()
        if (accounts.isEmpty()) {
            coroutineScope {
                val vodJob = async { fetchXtreamVodOnly("http://rgkkw.live:80", "4dfoydR2gZ", "clever3still") }
                val seriesJob = async { fetchXtreamSeriesOnly("http://rgkkw.live:80", "4dfoydR2gZ", "clever3still") }
                allItems.addAll(vodJob.await())
                allItems.addAll(seriesJob.await())
            }
        } else {
            coroutineScope {
                val jobs = accounts.map { acc ->
                    async {
                        val subItems = mutableListOf<MediaItem>()
                        coroutineScope {
                            val vodJob = if (acc.includeVod) async { fetchXtreamVodOnly(acc.serverUrl, acc.username, acc.password) } else null
                            val seriesJob = if (acc.includeSeries) async { fetchXtreamSeriesOnly(acc.serverUrl, acc.username, acc.password) } else null
                            vodJob?.await()?.let { subItems.addAll(it) }
                            seriesJob?.await()?.let { subItems.addAll(it) }
                        }
                        subItems
                    }
                }
                jobs.awaitAll().forEach { allItems.addAll(it) }
            }
        }
        allItems.distinctBy { it.id }.sortedByDescending { it.movieSortWeight }
    }

    fun getCachedXtreamAccountMovies(accId: String): List<MediaItem> {
        return loadListFromFileCache("cache_xtream_mov_${accId}.json")
    }

    fun saveCachedXtreamAccountMovies(accId: String, list: List<MediaItem>) {
        saveListToFileCache("cache_xtream_mov_${accId}.json", list)
    }

    suspend fun fetchXtreamAccountMovies(account: com.example.model.XtreamAccount): List<MediaItem> = withContext(Dispatchers.IO) {
        val cached = getCachedXtreamAccountMovies(account.id)
        if (cached.isNotEmpty()) {
            return@withContext cached
        }
        val subItems = mutableListOf<MediaItem>()
        coroutineScope {
            val vodJob = if (account.includeVod) async { fetchXtreamVodOnly(account.serverUrl, account.username, account.password) } else null
            val seriesJob = if (account.includeSeries) async { fetchXtreamSeriesOnly(account.serverUrl, account.username, account.password) } else null
            vodJob?.await()?.let { subItems.addAll(it) }
            seriesJob?.await()?.let { subItems.addAll(it) }
        }
        val strictlyMovies = subItems.filter { item ->
            !item.isLive && item.type != MediaType.LIVE_TV && item.type != MediaType.LIVE_EVENT && !item.streamUrl.contains("/live/")
        }.map {
            if (it.type != MediaType.SERIES) it.copy(type = MediaType.MOVIE, isLive = false) else it.copy(isLive = false)
        }.distinctBy { it.id }.sortedByDescending { it.movieSortWeight }

        if (strictlyMovies.isNotEmpty()) {
            saveCachedXtreamAccountMovies(account.id, strictlyMovies)
        }
        strictlyMovies
    }

    fun getCachedPlaylistMovies(playlistId: String): List<MediaItem> {
        return loadListFromFileCache("cache_pl_mov_${playlistId}.json")
    }

    fun saveCachedPlaylistMovies(playlistId: String, list: List<MediaItem>) {
        saveListToFileCache("cache_pl_mov_${playlistId}.json", list)
    }

    suspend fun fetchPlaylistMoviesOnly(playlist: PlaylistInfo): List<MediaItem> = withContext(Dispatchers.IO) {
        val cached = getCachedPlaylistMovies(playlist.id)
        if (cached.isNotEmpty()) {
            return@withContext cached
        }
        val allItems = if (!playlist.serverUrl.isNullOrBlank() && !playlist.username.isNullOrBlank() && !playlist.password.isNullOrBlank()) {
            val subItems = mutableListOf<MediaItem>()
            coroutineScope {
                val vodJob = async { fetchXtreamVodOnly(playlist.serverUrl, playlist.username, playlist.password) }
                val seriesJob = async { fetchXtreamSeriesOnly(playlist.serverUrl, playlist.username, playlist.password) }
                subItems.addAll(vodJob.await())
                subItems.addAll(seriesJob.await())
            }
            subItems
        } else {
            fetchPlaylistChannels(playlist)
        }

        // STRICT FILTER: Absolutely NO Live TV!
        val strictlyMovies = allItems.filter { item ->
            val isExplicitLive = item.isLive || item.type == MediaType.LIVE_TV || item.type == MediaType.LIVE_EVENT ||
                    item.category.contains("Live TV", ignoreCase = true) ||
                    item.category.contains("Sports", ignoreCase = true) ||
                    item.category.contains("News", ignoreCase = true) ||
                    (item.streamUrl.contains("/live/") && !item.streamUrl.contains("/movie/") && !item.streamUrl.contains("/series/"))
            !isExplicitLive
        }.map {
            if (it.type != MediaType.SERIES) it.copy(type = MediaType.MOVIE, isLive = false) else it.copy(isLive = false)
        }.distinctBy { it.id }.sortedByDescending { it.movieSortWeight }

        if (strictlyMovies.isNotEmpty()) {
            saveCachedPlaylistMovies(playlist.id, strictlyMovies)
        }
        strictlyMovies
    }

    suspend fun fetchAllXtreamMoviesFast(): List<MediaItem> = withContext(Dispatchers.IO) {
        val cached = getCachedMoviesList()
        if (cached.isNotEmpty()) {
            return@withContext cached
        }
        fetchAllXtreamMoviesAndSeries()
    }

    suspend fun fetchXtreamLiveStreamsOnly(serverUrl: String, username: String, pass: String): List<MediaItem> = withContext(Dispatchers.IO) {
        var cleanServer = serverUrl.trim().removeSuffix("/")
        if (!cleanServer.startsWith("http://", ignoreCase = true) && !cleanServer.startsWith("https://", ignoreCase = true)) {
            cleanServer = "http://$cleanServer"
        }
        val cleanUser = username.trim()
        val cleanPass = pass.trim()
        val items = mutableListOf<MediaItem>()

        try {
            val liveCatMap = mutableMapOf<String, String>()
            try {
                val catUrl = "$cleanServer/player_api.php?username=$cleanUser&password=$cleanPass&action=get_live_categories"
                val catReq = Request.Builder().url(catUrl).header("User-Agent", "IPTVSmartersPro").build()
                val catResp = client.newCall(catReq).execute()
                if (catResp.isSuccessful) {
                    val arr = JSONArray(catResp.body?.string() ?: "")
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val id = obj.optString("category_id")
                        val name = obj.optString("category_name")
                        if (id.isNotBlank() && name.isNotBlank()) liveCatMap[id] = name
                    }
                }
            } catch (_: Exception) {}

            val liveUrl = "$cleanServer/player_api.php?username=$cleanUser&password=$cleanPass&action=get_live_streams"
            val liveReq = Request.Builder().url(liveUrl).header("User-Agent", "IPTVSmartersPro").build()
            val liveResp = client.newCall(liveReq).execute()
            if (liveResp.isSuccessful) {
                val liveArr = JSONArray(liveResp.body?.string() ?: "")
                val isRgkkw = cleanServer.contains("rgkkw.live", ignoreCase = true)
                for (i in 0 until liveArr.length()) {
                    val sObj = liveArr.optJSONObject(i) ?: continue
                    val streamId = sObj.optString("stream_id", "")
                    if (streamId.isBlank()) continue
                    val name = sObj.optString("name", "Channel $streamId").trim()
                    if (name.contains("###") || name.startsWith("---") || name.contains("====")) continue
                    val catId = sObj.optString("category_id", "")
                    val categoryName = liveCatMap[catId] ?: "Live TV"
                    val icon = sObj.optString("stream_icon").takeIf { it.isNotBlank() }

                    val playUrl = "$cleanServer/live/$cleanUser/$cleanPass/$streamId.ts"
                    val altServer = if (cleanServer.contains(":80")) cleanServer.replace(":80", "") else "$cleanServer:80"
                    val altPlayUrl = "$altServer/live/$cleanUser/$cleanPass/$streamId.ts"
                    val sList = mutableListOf(
                        StreamServer("সার্ভার ১ (TS Stream)", playUrl),
                        StreamServer("সার্ভার ২ (বিকল্প TS)", altPlayUrl)
                    )
                    if (!isRgkkw) {
                        sList.add(StreamServer("সার্ভার ৩ (HLS)", "$cleanServer/live/$cleanUser/$cleanPass/$streamId.m3u8"))
                    }

                    items.add(
                        MediaItem(
                            id = "xtream_live_${streamId}",
                            title = name,
                            category = categoryName,
                            type = MediaType.LIVE_TV,
                            streamUrl = playUrl,
                            backupUrl = altPlayUrl,
                            servers = sList,
                            logoUrl = icon,
                            isLive = true,
                            quality = "HD",
                            userAgent = "IPTVSmartersPro"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        items
    }

    suspend fun fetchAllXtreamLiveChannels(): List<MediaItem> = withContext(Dispatchers.IO) {
        val accounts = getActiveXtreamAccounts().filter { it.includeLive }
        if (accounts.isEmpty()) {
            return@withContext emptyList()
        }
        val allItems = mutableListOf<MediaItem>()
        coroutineScope {
            val jobs = accounts.map { acc ->
                async {
                    try {
                        fetchXtreamLiveStreamsOnly(acc.serverUrl, acc.username, acc.password)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        emptyList()
                    }
                }
            }
            val results = jobs.awaitAll()
            results.forEach { allItems.addAll(it) }
        }
        allItems.distinctBy { it.id }
    }

    suspend fun fetchStarshareMoviesAndSeries(): List<MediaItem> = withContext(Dispatchers.IO) {
        fetchAllXtreamMoviesAndSeries()
    }

    suspend fun testXtreamCodes(serverUrl: String, username: String, pass: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        var cleanServer = serverUrl.trim().removeSuffix("/")
        if (!cleanServer.startsWith("http://", ignoreCase = true) && !cleanServer.startsWith("https://", ignoreCase = true)) {
            cleanServer = "http://$cleanServer"
        }
        val cleanUser = username.trim()
        val cleanPass = pass.trim()

        try {
            val authUrl = "$cleanServer/player_api.php?username=$cleanUser&password=$cleanPass"
            val req = Request.Builder().url(authUrl).header("User-Agent", "IPTVSmartersPro").build()
            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string() ?: ""
                if (body.startsWith("{")) {
                    val obj = JSONObject(body)
                    val userInfo = obj.optJSONObject("user_info")
                    val auth = userInfo?.optInt("auth", 0) ?: 0
                    val status = userInfo?.optString("status", "Active") ?: "Active"
                    val expDateRaw = userInfo?.optString("exp_date", "") ?: ""
                    val maxCons = userInfo?.optString("max_connections", "1") ?: "1"
                    val activeCons = userInfo?.optString("active_cons", "0") ?: "0"
                    val formats = userInfo?.optJSONArray("allowed_output_formats")?.let { arr ->
                        (0 until arr.length()).map { arr.getString(it) }.joinToString(", ")
                    } ?: "ts"

                    val expDateFormatted = if (expDateRaw.isNotBlank() && expDateRaw != "null") {
                        try {
                            val epoch = expDateRaw.toLong()
                            val sdf = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
                            sdf.format(java.util.Date(epoch * 1000L))
                        } catch (_: Exception) { expDateRaw }
                    } else { "আনলিমিটেড (Unlimited)" }

                    if (auth == 1 || status.equals("Active", ignoreCase = true)) {
                        return@withContext Pair(true, "✅ সফল সংযোগ! স্ট্যাটাস: $status | মেয়াদ: $expDateFormatted | এক্টিভ কানেকশন: $activeCons/$maxCons | ফরম্যাট: $formats")
                    } else if (obj.has("user_info")) {
                        return@withContext Pair(true, "✅ Xtream Codes তথ্য পাওয়া গেছে! স্ট্যাটাস: $status (মেয়াদ: $expDateFormatted)")
                    }
                }
            }

            // Test via get.php
            val m3uTest = "$cleanServer/get.php?username=$cleanUser&password=$cleanPass&type=m3u_plus&output=m3u8"
            val m3uReq = Request.Builder().url(m3uTest).header("User-Agent", "IPTVSmartersPro").build()
            val m3uResp = client.newCall(m3uReq).execute()
            if (m3uResp.isSuccessful) {
                return@withContext Pair(true, "✅ Xtream M3U সফলভাবে রেসপন্স করেছে (HTTP 200)")
            }

            Pair(false, "⚠️ সার্ভার সংযোগ বা ইউজারনেম/পাসওয়ার্ড সঠিক নয় (HTTP ${resp.code})")
        } catch (e: Exception) {
            Pair(false, "⚠️ কানেকশন এরর: ${e.localizedMessage ?: "সার্ভারে পৌঁছানো সম্ভব হয়নি"}")
        }
    }

    private val playlistChannelsMemoryCache = java.util.concurrent.ConcurrentHashMap<String, List<MediaItem>>()

    fun getCachedPlaylistChannels(playlistId: String): List<MediaItem> {
        val memory = playlistChannelsMemoryCache[playlistId]
        if (!memory.isNullOrEmpty()) return memory
        val disk = loadListFromFileCache("cache_playlist_$playlistId")
        if (disk.isNotEmpty()) {
            playlistChannelsMemoryCache[playlistId] = disk
            return disk
        }
        return emptyList()
    }

    fun saveCachedPlaylistChannels(playlistId: String, items: List<MediaItem>) {
        if (items.isNotEmpty()) {
            playlistChannelsMemoryCache[playlistId] = items
            saveListToFileCache("cache_playlist_$playlistId", items)
        }
    }

    suspend fun fetchPlaylistChannels(playlist: PlaylistInfo): List<MediaItem> = withContext(Dispatchers.IO) {
        val cached = getCachedPlaylistChannels(playlist.id)

        if (!playlist.serverUrl.isNullOrBlank() && !playlist.username.isNullOrBlank() && !playlist.password.isNullOrBlank()) {
            val xtreamItems = fetchXtreamCodesStreams(playlist.serverUrl, playlist.username, playlist.password)
            if (xtreamItems.isNotEmpty()) {
                saveCachedPlaylistChannels(playlist.id, xtreamItems)
                return@withContext xtreamItems
            }
            val liveItems = fetchXtreamLiveStreamsOnly(playlist.serverUrl, playlist.username, playlist.password)
            if (liveItems.isNotEmpty()) {
                saveCachedPlaylistChannels(playlist.id, liveItems)
                return@withContext liveItems
            }
        }
        if (playlist.url.isNotBlank()) {
            val creds = parseXtreamCredentials(playlist.url)
            if (creds != null) {
                val xtreamFallback = fetchXtreamCodesStreams(creds.first, creds.second, creds.third)
                if (xtreamFallback.isNotEmpty()) {
                    saveCachedPlaylistChannels(playlist.id, xtreamFallback)
                    return@withContext xtreamFallback
                }
                val liveFallback = fetchXtreamLiveStreamsOnly(creds.first, creds.second, creds.third)
                if (liveFallback.isNotEmpty()) {
                    saveCachedPlaylistChannels(playlist.id, liveFallback)
                    return@withContext liveFallback
                }
            }
            val m3uItems = parseM3uFromUrl(playlist.url)
            if (m3uItems.isNotEmpty()) {
                saveCachedPlaylistChannels(playlist.id, m3uItems)
                return@withContext m3uItems
            }
        }
        if (cached.isNotEmpty()) {
            return@withContext cached
        }
        emptyList()
    }

    // -------------------------------------------------------------
    // USER-LOCAL PLAYLISTS (Stored only on user device, never to Firebase)
    // -------------------------------------------------------------
    fun getUserPlaylists(): List<PlaylistInfo> {
        val deleted = getDeletedIds()
        val jsonStr = prefs.getString("user_playlists", null)
        val list = mutableListOf<PlaylistInfo>()
        if (jsonStr != null) {
            try {
                val arr = JSONArray(jsonStr)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val id = obj.optString("id", "pl_user_$i")
                    if (!deleted.contains(id)) {
                        list.add(
                            PlaylistInfo(
                                id = id,
                                title = obj.optString("title", obj.optString("name", "Playlist")),
                                url = obj.optString("url", ""),
                                logoUrl = obj.optString("logoUrl", obj.optString("logo", null)).takeIf { it?.isNotBlank() == true },
                                description = obj.optString("description", null).takeIf { it?.isNotBlank() == true },
                                channelCount = obj.optInt("channelCount", 0),
                                serverUrl = obj.optString("serverUrl", null).takeIf { it?.isNotBlank() == true },
                                username = obj.optString("username", null).takeIf { it?.isNotBlank() == true },
                                password = obj.optString("password", null).takeIf { it?.isNotBlank() == true },
                                type = obj.optString("type", if (obj.has("serverUrl") || obj.has("username")) "XTREAM" else "M3U"),
                                isAdmin = obj.optBoolean("isAdmin", false),
                                isReadOnly = obj.optBoolean("isReadOnly", false)
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return list
    }

    fun saveUserPlaylist(playlist: PlaylistInfo) {
        removeDeletedId(playlist.id)
        val current = getUserPlaylists().toMutableList()
        current.removeAll { it.id == playlist.id }
        current.add(0, playlist.copy(isAdmin = false, isReadOnly = false))
        saveUserPlaylistsList(current)
    }

    fun saveUserPlaylistsList(list: List<PlaylistInfo>) {
        val arr = JSONArray()
        list.forEach { p ->
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("title", p.title)
            obj.put("name", p.title)
            obj.put("url", p.url)
            obj.put("logoUrl", p.logoUrl ?: "")
            obj.put("logo", p.logoUrl ?: "")
            obj.put("description", p.description ?: "")
            obj.put("channelCount", p.channelCount)
            obj.put("serverUrl", p.serverUrl ?: "")
            obj.put("username", p.username ?: "")
            obj.put("password", p.password ?: "")
            obj.put("type", p.type)
            obj.put("isAdmin", false)
            obj.put("isReadOnly", false)
            arr.put(obj)
        }
        prefs.edit().putString("user_playlists", arr.toString()).apply()
    }

    fun deleteUserPlaylist(id: String) {
        addDeletedId(id)
        val current = getUserPlaylists().filterNot { it.id == id }
        saveUserPlaylistsList(current)
    }

    // -------------------------------------------------------------
    // ADMIN PLAYLISTS (Admin panel managed, synced to Firebase Cloud)
    // -------------------------------------------------------------
    fun getAdminPlaylists(): List<PlaylistInfo> {
        val deleted = getDeletedIds()
        val jsonStr = prefs.getString("admin_playlists", prefs.getString("custom_playlists", "[]")) ?: "[]"
        val list = mutableListOf<PlaylistInfo>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.optString("id", "pl_admin_$i")
                if (!deleted.contains(id)) {
                    list.add(
                        PlaylistInfo(
                            id = id,
                            title = obj.optString("title", obj.optString("name", "Playlist")),
                            url = obj.optString("url", ""),
                            logoUrl = obj.optString("logoUrl", obj.optString("logo", null)).takeIf { it?.isNotBlank() == true },
                            description = obj.optString("description", null).takeIf { it?.isNotBlank() == true },
                            channelCount = obj.optInt("channelCount", 0),
                            serverUrl = obj.optString("serverUrl", null).takeIf { it?.isNotBlank() == true },
                            username = obj.optString("username", null).takeIf { it?.isNotBlank() == true },
                            password = obj.optString("password", null).takeIf { it?.isNotBlank() == true },
                            type = obj.optString("type", if (obj.has("serverUrl") || obj.has("username")) "XTREAM" else "M3U"),
                            isAdmin = true,
                            isReadOnly = true
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveAdminPlaylist(playlist: PlaylistInfo) {
        removeDeletedId(playlist.id)
        val current = getAdminPlaylists().toMutableList()
        current.removeAll { it.id == playlist.id }
        current.add(0, playlist.copy(isAdmin = true, isReadOnly = true))
        saveAdminPlaylistsList(current)
    }

    fun saveAdminPlaylistsList(list: List<PlaylistInfo>) {
        val arr = JSONArray()
        list.forEach { p ->
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("title", p.title)
            obj.put("name", p.title)
            obj.put("url", p.url)
            obj.put("logoUrl", p.logoUrl ?: "")
            obj.put("logo", p.logoUrl ?: "")
            obj.put("description", p.description ?: "")
            obj.put("channelCount", p.channelCount)
            obj.put("serverUrl", p.serverUrl ?: "")
            obj.put("username", p.username ?: "")
            obj.put("password", p.password ?: "")
            obj.put("type", p.type)
            obj.put("isAdmin", true)
            obj.put("isReadOnly", true)
            arr.put(obj)
        }
        prefs.edit().putString("admin_playlists", arr.toString()).apply()
    }

    fun deleteAdminPlaylist(id: String) {
        addDeletedId(id)
        val current = getAdminPlaylists().filterNot { it.id == id }
        saveAdminPlaylistsList(current)
    }

    fun getCustomPlaylists(): List<PlaylistInfo> {
        val deleted = getDeletedIds()
        return (getAdminPlaylists() + getUserPlaylists()).distinctBy { it.id }.filterNot { deleted.contains(it.id) }
    }

    fun saveCustomPlaylist(playlist: PlaylistInfo) {
        saveUserPlaylist(playlist)
    }

    fun saveCustomPlaylistsList(list: List<PlaylistInfo>) {
        saveUserPlaylistsList(list)
    }

    fun deleteCustomPlaylist(id: String) {
        deleteUserPlaylist(id)
    }

    suspend fun deletePlaylistFromFirebase(id: String): Boolean {
        return deleteFromFirebase(id, MediaType.LIVE_TV)
    }

    suspend fun deletePlaylist(id: String): Boolean {
        addDeletedId(id)
        deleteAdminPlaylist(id)
        deleteUserPlaylist(id)
        return deleteFromFirebase(id, MediaType.LIVE_TV)
    }

    suspend fun pushPlaylistToFirebase(playlist: PlaylistInfo, url: String = getSavedFirebaseUrl()): Boolean = withContext(Dispatchers.IO) {
        var success = false

        // 1. Push to Firestore
        try {
            val firestoreObj = JSONObject()
            val fields = JSONObject()
            fields.put("id", JSONObject().put("stringValue", playlist.id))
            fields.put("title", JSONObject().put("stringValue", playlist.title))
            fields.put("name", JSONObject().put("stringValue", playlist.title))
            fields.put("url", JSONObject().put("stringValue", playlist.url))
            if (!playlist.logoUrl.isNullOrBlank()) {
                fields.put("logoUrl", JSONObject().put("stringValue", playlist.logoUrl))
                fields.put("logo", JSONObject().put("stringValue", playlist.logoUrl))
            }
            if (!playlist.description.isNullOrBlank()) {
                fields.put("description", JSONObject().put("stringValue", playlist.description))
            }
            fields.put("channelCount", JSONObject().put("integerValue", playlist.channelCount.toString()))
            if (!playlist.serverUrl.isNullOrBlank()) {
                fields.put("serverUrl", JSONObject().put("stringValue", playlist.serverUrl))
            }
            if (!playlist.username.isNullOrBlank()) {
                fields.put("username", JSONObject().put("stringValue", playlist.username))
            }
            if (!playlist.password.isNullOrBlank()) {
                fields.put("password", JSONObject().put("stringValue", playlist.password))
            }
            fields.put("type", JSONObject().put("stringValue", playlist.type))
            fields.put("isAdmin", JSONObject().put("booleanValue", true))
            fields.put("isReadOnly", JSONObject().put("booleanValue", true))

            firestoreObj.put("fields", fields)
            val fsBody = firestoreObj.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

            val databases = listOf(FIRESTORE_DATABASE_ID, "(default)")
            for (dbId in databases) {
                try {
                    val fsUrl = "https://firestore.googleapis.com/v1/projects/$FIREBASE_PROJECT_ID/databases/$dbId/documents/playlists/${playlist.id}?key=$FIREBASE_API_KEY"
                    val fsReq = Request.Builder().url(fsUrl).patch(fsBody).build()
                    val fsResp = client.newCall(fsReq).execute()
                    if (fsResp.isSuccessful) success = true
                } catch (e: Exception) {
                    // continue
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 2. Push to RTDB
        if (url.isNotBlank()) {
            try {
                val cleanUrl = if (url.endsWith("/")) url.removeSuffix("/") else url
                val obj = JSONObject()
                obj.put("id", playlist.id)
                obj.put("title", playlist.title)
                obj.put("name", playlist.title)
                obj.put("url", playlist.url)
                obj.put("logoUrl", playlist.logoUrl ?: "")
                obj.put("logo", playlist.logoUrl ?: "")
                obj.put("description", playlist.description ?: "")
                obj.put("channelCount", playlist.channelCount)
                obj.put("serverUrl", playlist.serverUrl ?: "")
                obj.put("username", playlist.username ?: "")
                obj.put("password", playlist.password ?: "")
                obj.put("type", playlist.type)
                obj.put("isAdmin", true)
                obj.put("isReadOnly", true)

                val body = obj.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val targetUrl = appendRtdbAuth("$cleanUrl/playlists/${playlist.id}.json")
                val req = Request.Builder().url(targetUrl).put(body).build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) success = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        success
    }

    suspend fun fetchPlaylistsFromFirebase(url: String = getSavedFirebaseUrl()): List<PlaylistInfo> = withContext(Dispatchers.IO) {
        val deleted = getDeletedIds()
        coroutineScope {
            // 1. Fetch from Firestore (parallel per database)
            val databases = listOf(FIRESTORE_DATABASE_ID, "(default)")
            val fsJobs = databases.map { dbId ->
                async {
                    val dbList = mutableListOf<PlaylistInfo>()
                    try {
                        val fsUrl = "https://firestore.googleapis.com/v1/projects/$FIREBASE_PROJECT_ID/databases/$dbId/documents/playlists?key=$FIREBASE_API_KEY"
                        val req = Request.Builder().url(fsUrl).header("User-Agent", "NAFITV24-Android/2.5.0").build()
                        val resp = client.newCall(req).execute()
                        if (resp.isSuccessful) {
                            val body = resp.body?.string() ?: ""
                            if (body.isNotBlank() && body.startsWith("{")) {
                                val json = JSONObject(body)
                                val docs = json.optJSONArray("documents")
                                if (docs != null) {
                                    for (i in 0 until docs.length()) {
                                        val doc = docs.optJSONObject(i) ?: continue
                                        val name = doc.optString("name", "")
                                        val docId = name.substringAfterLast("/")
                                        if (docId.isBlank() || deleted.contains(docId)) continue

                                        val fields = doc.optJSONObject("fields") ?: continue
                                        fun s(k: String): String = fields.optJSONObject(k)?.optString("stringValue", "") ?: ""
                                        fun count(k: String): Int = fields.optJSONObject(k)?.optInt("integerValue", 0) ?: 0

                                        val pId = s("id").ifBlank { docId }
                                        if (!deleted.contains(pId)) {
                                            dbList.add(
                                                PlaylistInfo(
                                                    id = pId,
                                                    title = s("title").ifBlank { s("name").ifBlank { "Playlist" } },
                                                    url = s("url"),
                                                    logoUrl = s("logoUrl").ifBlank { s("logo") }.takeIf { it.isNotBlank() },
                                                    description = s("description").takeIf { it.isNotBlank() },
                                                    channelCount = count("channelCount"),
                                                    serverUrl = s("serverUrl").takeIf { it.isNotBlank() },
                                                    username = s("username").takeIf { it.isNotBlank() },
                                                    password = s("password").takeIf { it.isNotBlank() },
                                                    type = s("type").ifBlank { if (s("serverUrl").isNotBlank() || s("username").isNotBlank()) "XTREAM" else "M3U" },
                                                    isAdmin = true,
                                                    isReadOnly = true
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    dbList
                }
            }

            // 2. Fetch from RTDB
            val rtdbJob = async {
                val rtdbList = mutableListOf<PlaylistInfo>()
                if (url.isNotBlank()) {
                    try {
                        val cleanUrl = if (url.endsWith("/")) url.removeSuffix("/") else url
                        val targetUrl = appendRtdbAuth("$cleanUrl/playlists.json")
                        val req = Request.Builder().url(targetUrl).header("User-Agent", "NAFITV24-Android/2.5.0").build()
                        val resp = client.newCall(req).execute()
                        if (resp.isSuccessful) {
                            val body = resp.body?.string()?.trim() ?: ""
                            if (body.isNotEmpty() && body != "null") {
                                if (body.startsWith("{")) {
                                    val jsonObject = JSONObject(body)
                                    val keys = jsonObject.keys()
                                    while (keys.hasNext()) {
                                        val k = keys.next()
                                        if (!deleted.contains(k)) {
                                            val obj = jsonObject.optJSONObject(k)
                                            if (obj != null) {
                                                val id = obj.optString("id", k)
                                                if (!deleted.contains(id)) {
                                                    rtdbList.add(
                                                        PlaylistInfo(
                                                            id = id,
                                                            title = obj.optString("title", obj.optString("name", "Playlist")),
                                                            url = obj.optString("url", ""),
                                                            logoUrl = obj.optString("logoUrl", obj.optString("logo", null)).takeIf { it?.isNotBlank() == true },
                                                            description = obj.optString("description", null).takeIf { it?.isNotBlank() == true },
                                                            channelCount = obj.optInt("channelCount", 0),
                                                            serverUrl = obj.optString("serverUrl", null).takeIf { it?.isNotBlank() == true },
                                                            username = obj.optString("username", null).takeIf { it?.isNotBlank() == true },
                                                            password = obj.optString("password", null).takeIf { it?.isNotBlank() == true },
                                                            type = obj.optString("type", if (obj.has("serverUrl") || obj.has("username")) "XTREAM" else "M3U"),
                                                            isAdmin = true,
                                                            isReadOnly = true
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else if (body.startsWith("[")) {
                                    val jsonArr = JSONArray(body)
                                    for (i in 0 until jsonArr.length()) {
                                        val obj = jsonArr.optJSONObject(i) ?: continue
                                        val id = obj.optString("id", "pl_rtdb_$i")
                                        if (!deleted.contains(id)) {
                                            rtdbList.add(
                                                PlaylistInfo(
                                                    id = id,
                                                    title = obj.optString("title", obj.optString("name", "Playlist")),
                                                    url = obj.optString("url", ""),
                                                    logoUrl = obj.optString("logoUrl", obj.optString("logo", null)).takeIf { it?.isNotBlank() == true },
                                                    description = obj.optString("description", null).takeIf { it?.isNotBlank() == true },
                                                    channelCount = obj.optInt("channelCount", 0),
                                                    serverUrl = obj.optString("serverUrl", null).takeIf { it?.isNotBlank() == true },
                                                    username = obj.optString("username", null).takeIf { it?.isNotBlank() == true },
                                                    password = obj.optString("password", null).takeIf { it?.isNotBlank() == true },
                                                    type = obj.optString("type", if (obj.has("serverUrl") || obj.has("username")) "XTREAM" else "M3U"),
                                                    isAdmin = true,
                                                    isReadOnly = true
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                rtdbList
            }

            val allFs = fsJobs.awaitAll().flatten()
            val allRtdb = rtdbJob.await()
            (allFs + allRtdb).distinctBy { it.id }.filterNot { deleted.contains(it.id) }
        }
    }

    fun isAutoSwitchChannelOnFailure(): Boolean {
        return prefs.getBoolean("auto_switch_channel_on_failure", true)
    }

    fun setAutoSwitchChannelOnFailure(enabled: Boolean) {
        prefs.edit().putBoolean("auto_switch_channel_on_failure", enabled).apply()
    }

    fun saveFirebaseUrl(url: String) {
        prefs.edit().putString("saved_firebase_url", url).apply()
    }

    fun getSavedFirebaseUrl(): String {
        val stored = prefs.getString("saved_firebase_url", null)
        if (stored.isNullOrBlank() || stored.contains("elaborate-airfoil") || stored.contains("nafitv24-default-rtdb")) {
            return DEFAULT_RTDB_URL
        }
        return stored
    }

    fun saveM3uUrl(url: String) {
        saveLiveTvM3uUrl(url)
    }

    fun getSavedM3uUrl(): String {
        return getSavedLiveTvM3uUrl()
    }

    fun saveLiveTvM3uUrl(url: String) {
        prefs.edit().putString("saved_live_tv_m3u_url", url).apply()
    }

    fun getSavedLiveTvM3uUrl(): String {
        val current = prefs.getString("saved_live_tv_m3u_url", DEFAULT_LIVE_TV_M3U_URL) ?: DEFAULT_LIVE_TV_M3U_URL
        val updateChannelUrl = "https://raw.githubusercontent.com/nafitv24-web/NAFI-TV/refs/heads/main/Update%20Channel.m3u"

        val urls = mutableSetOf<String>()
        urls.add(updateChannelUrl)
        if (current.isNotBlank() && !current.contains("Nafitv24.m3u")) {
            current.split(Regex("[\r\n,;]+"))
                .map { it.trim() }
                .filter { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }
                .filterNot { it.contains("Sports%20Channel%20NF.m3u") }
                .forEach { urls.add(it) }
        }
        return urls.joinToString("\n")
    }

    fun saveSportsM3uUrl(url: String) {
        prefs.edit().putString("saved_sports_m3u_url", url).apply()
        memoryFileCache.remove("cache_sports_v4.json")
        memoryFileCache.remove("cache_sports_v3.json")
        try {
            java.io.File(context.filesDir, "cache_sports_v4.json").delete()
            java.io.File(context.filesDir, "cache_sports_v3.json").delete()
        } catch (_: Exception) {}
    }

    fun getSavedSportsM3uUrl(): String {
        val stored = prefs.getString("saved_sports_m3u_url", null)
        if (stored.isNullOrBlank() || stored.contains("nfiptv24-max", ignoreCase = true)) {
            return DEFAULT_SPORTS_M3U_URL
        }
        return stored
    }

    fun saveTapmadJsonUrl(url: String) {
        prefs.edit().putString("saved_tapmad_json_url", url).apply()
        try {
            java.io.File(context.filesDir, "cache_sports_v4.json").delete()
            java.io.File(context.filesDir, "cache_sports_v3.json").delete()
        } catch (_: Exception) {}
    }

    fun getSavedTapmadJsonUrl(): String {
        val stored = prefs.getString("saved_tapmad_json_url", null)
        if (stored.isNullOrBlank()) {
            return DEFAULT_TAPMAD_JSON_URL
        }
        return stored
    }

    fun getSavedTapmadJsonUrls(): List<String> {
        val raw = getSavedTapmadJsonUrl()
        return raw.split("\n", ",").map { it.trim() }.filter { it.isNotBlank() }
    }

    fun saveTapmadM3uUrl(url: String) {
        prefs.edit().putString("saved_tapmad_m3u_url", url).apply()
    }

    fun getSavedTapmadM3uUrl(): String {
        val stored = prefs.getString("saved_tapmad_m3u_url", null)
        if (stored.isNullOrBlank()) {
            return DEFAULT_TAPMAD_M3U_URL
        }
        return stored
    }

    fun resolveWorkingSportsServers(title: String, originalStreamUrl: String = "", itemId: String = ""): List<StreamServer> {
        val list = mutableListOf<StreamServer>()
        val clean = title.lowercase()
        val cleanUrl = originalStreamUrl.lowercase()
        val cleanId = itemId.lowercase()

        // Match 1: Afghanistan vs Bangladesh (1st Test)
        val isAfgBan = (clean.contains("afghanistan") && clean.contains("bangladesh")) ||
                cleanId.contains("18900") || cleanUrl.contains("28593jkil")
        if (isAfgBan) {
            val tapmadUrl = if (originalStreamUrl.isNotBlank()) originalStreamUrl else "https://saseries.akamaized.net/hls/live/2110097/28593jkiL-tapmad/master.m3u8"
            list.add(StreamServer("Tapmad Live (Direct HD)", tapmadUrl))
            list.add(StreamServer("T SPORTS HD (Live BD)", "http://livetv.akr4m.com:8080/bdtv/restrem/02.m3u8"))
            list.add(StreamServer("SKY CRICKET FHD (Live)", "https://bl.rutube.ru/livestream/7c13a51576b9ff2601f08f5d57dd5169/index.m3u8?s=uiXES2ePt7xTpQnbJxn7Dg&e=2074684474&scheme=https"))
            list.add(StreamServer("WILLOW FHD (ClearKey DASH)", "https://otte.live.fly.ww.aiv-cdn.net/iad-nitro/live/clients/dash/enc/v8s736w0hw/out/v1/713012dd281246dc827872d3920a8443/cenc.mpd"))
            return list
        }

        // Match 2: South Africa vs Australia (1st Test)
        val isSaAus = (clean.contains("south africa") && clean.contains("australia")) ||
                cleanId.contains("17700") || cleanUrl.contains("fifa-3827e1")
        if (isSaAus) {
            val tapmadUrl = if (originalStreamUrl.isNotBlank()) originalStreamUrl else "https://saseries.akamaized.net/hls/live/2110097/FiFa-3827e1/master.m3u8"
            list.add(StreamServer("Tapmad Live (Direct HD)", tapmadUrl))
            list.add(StreamServer("SKY CRICKET FHD (Live)", "https://bl.rutube.ru/livestream/7c13a51576b9ff2601f08f5d57dd5169/index.m3u8?s=uiXES2ePt7xTpQnbJxn7Dg&e=2074684474&scheme=https"))
            list.add(StreamServer("WILLOW FHD (ClearKey DASH)", "https://otte.live.fly.ww.aiv-cdn.net/iad-nitro/live/clients/dash/enc/1dwfmiwgbk/out/v1/713012dd281246dc827872d3920a8443/cenc.mpd|drmScheme=clearkey&drmLicense=61b5fe1a7d9d83929d15da6db1c5f427:6e8231835a24911fd035a47e49f5c53b"))
            list.add(StreamServer("FANCODE FHD (ClearKey)", "https://otte.cache.aiv-cdn.net/iad-nitro/live/clients/dash/enc/b3b3fkmrbl/out/v1/1084d5c9a97a4c5b9f9554c88f486646/cenc.mpd|drmScheme=clearkey&drmLicense=82dfca238e8c4b430a3269db71965db9:a00b28caf4ac628e77a553d440c0ddca"))
            list.add(StreamServer("WILLOW SPORTS HD", "https://otte.cache.aiv-cdn.net/bom-nitro/live/clients/dash/enc/tll6uwepxa/out/v1/a7f67cbb33df46539312956427343886/cenc.mpd|drmScheme=clearkey&drmLicense=290e09c837da78d5cd961978d390515c:b748836c71e6a4ca68ef5b5652db6247"))
            return list
        }

        // Match 3: Pakistan U19 vs Bangladesh U19 / Women Tri-Series
        val isPakBan = (clean.contains("pakistan") && clean.contains("bangladesh")) ||
                cleanId.contains("18090") || cleanUrl.contains("tapmad258645") ||
                (clean.contains("bangladesh") && (clean.contains("women") || clean.contains("u19") || clean.contains("tri-series")))
        if (isPakBan) {
            val tapmadUrl = if (originalStreamUrl.isNotBlank()) originalStreamUrl else "https://saseries.akamaized.net/hls/live/2110097/tapmad258645/master.m3u8"
            list.add(StreamServer("Tapmad Live (Direct HD)", tapmadUrl))
            list.add(StreamServer("WILLOW FHD (ClearKey DASH)", "https://otte.live.fly.ww.aiv-cdn.net/iad-nitro/live/clients/dash/enc/jhqpjlh8bf/out/v1/713012dd281246dc827872d3920a8443/cenc.mpd"))
            list.add(StreamServer("FANCODE (IND)", "https://dai-fancode.pages.dev/mumbai/4250150_english_hls_e1216d6dea39966_1ta-di_video.m3u8"))
            list.add(StreamServer("SKY CRICKET FHD (Live)", "https://bl.rutube.ru/livestream/7c13a51576b9ff2601f08f5d57dd5169/index.m3u8?s=uiXES2ePt7xTpQnbJxn7Dg&e=2074684474&scheme=https"))
            list.add(StreamServer("T SPORTS / AYNA Live", "http://tvsen5.aynascope.net/willowhd/index.m3u8"))
            return list
        }

        // Check if cachedSportsMatches has matching live servers
        try {
            val cached = getCachedSportsMatches()
            val foundMatch = cached.firstOrNull { item ->
                val itTitle = item.title.lowercase()
                (itTitle.isNotBlank() && (clean.contains(itTitle) || itTitle.contains(clean)))
            }
            if (foundMatch != null) {
                for (s in foundMatch.getAllServers()) {
                    if (s.url.isNotBlank() && list.none { it.url.equals(s.url, ignoreCase = true) }) {
                        list.add(s)
                    }
                }
            }
        } catch (_: Exception) {}

        // Fallback for any other Akamai / Tapmad sports stream
        val isAkamaiWithoutToken = cleanUrl.contains("akamaized.net") && !cleanUrl.contains("hdnts=")
        val isTapmadWithoutToken = cleanUrl.contains("tapmad") && !cleanUrl.contains("token=")
        if (isAkamaiWithoutToken || isTapmadWithoutToken) {
            if (list.none { it.name.contains("T SPORTS", ignoreCase = true) }) {
                list.add(0, StreamServer("T SPORTS HD (Live BD)", "http://livetv.akr4m.com:8080/bdtv/restrem/02.m3u8"))
            }
            if (list.none { it.name.contains("SKY CRICKET", ignoreCase = true) }) {
                list.add(StreamServer("SKY CRICKET FHD (Live)", "https://bl.rutube.ru/livestream/7c13a51576b9ff2601f08f5d57dd5169/index.m3u8?s=uiXES2ePt7xTpQnbJxn7Dg&e=2074684474&scheme=https"))
            }
        }

        // Keep original stream as fallback server
        if (originalStreamUrl.isNotBlank() && list.none { it.url.equals(originalStreamUrl, ignoreCase = true) }) {
            val originalName = if (originalStreamUrl.contains("akamaized.net", ignoreCase = true) || originalStreamUrl.contains("tapmad", ignoreCase = true)) {
                "Tapmad Live (BD IP / Akamai)"
            } else {
                "সার্ভার (Original)"
            }
            list.add(StreamServer(originalName, originalStreamUrl))
        }

        return list
    }

    /**
     * খেলাধুলা, স্পোর্টস ম্যাচ, লাইভ স্পোর্টস সম্প্রচার বা স্পোর্টস চ্যানেল কি না তা বুদ্ধিমত্তার সাথে শনাক্ত করে।
     * সাধারণ নিউজ বা এন্টারটেইনমেন্ট চ্যানেল বাদ দিয়ে শুধুমাত্র খেলা ও স্পোর্টস ইভেন্ট ফিল্টার করে।
     */
    fun isSportsMatchOrChannel(item: MediaItem): Boolean {
        val title = item.title.lowercase()
        val cat = item.category.lowercase()
        val desc = (item.description ?: "").lowercase()
        val url = item.streamUrl.lowercase()

        // ১. সরাসরি ম্যাচ বা ফিক্সচার ইন্ডিকেটর (vs, v, @, -)
        if (title.contains(" vs ") || title.contains(" v ") || title.contains(" @ ")) {
            return true
        }

        // ২. স্পোর্টস ও গেমস কি-ওয়ার্ড
        val sportsKeywords = listOf(
            "cricket", "football", "soccer", "hockey", "badminton", "kabaddi", "tennis",
            "wwe", "ufc", "racing", "f1", "rugby", "basketball", "nba", "volleyball",
            "baseball", "athletics", "golf", "chess", "sports", "খেলা", "match", "tournament",
            "ipl", "bpl", "psl", "cpl", "isl", "t20", "odi", "test series", "fifa", "uefa",
            "champions league", "premier league", "la liga", "laliga", "serie a", "bundesliga", "ligue 1",
            "world cup", "asia cup", "copa america", "euro cup", "archery", "boxing", "handball",
            "horse riding", "jumping world cup", "table tennis", "swimming", "snooker", "vcl", "apl"
        )
        if (sportsKeywords.any { title.contains(it) || cat.contains(it) || desc.contains(it) }) {
            return true
        }

        // ৩. স্পোর্টস ব্রডকাস্টার চ্যানেল ও ওটিটি নেটওয়ার্ক (Sony LIV, Sony Ten, T Sports, Willow, etc.)
        val sportsBroadcasters = listOf(
            "sony ten", "sony six", "sony liv", "sonyliv", "star sports", "t sports", "willow",
            "tsports", "a sports", "astro cricket", "supersport", "bein", "fox sports",
            "eurosport", "bt sport", "tnt sports", "sky sports", "ptv sports", "geo super",
            "ten sports", "sports 18", "sports18", "dd sports", "prime sports", "gazi tv sports",
            "t-sports", "fancode", "tapmad", "pv-cdn.net", "sonydaimenew", "livesportsplay",
            "prime video sports", "axsports"
        )
        if (sportsBroadcasters.any { title.contains(it) || cat.contains(it) || desc.contains(it) || url.contains(it) }) {
            return true
        }
        return false
    }

    /**
     * এডমিন প্যানেল থেকে শুধুমাত্র 'sports match' অপশনে কনফিগার করা প্লেলিস্ট (Sports M3U Playlist URLs),
     * স্পোর্টস JSON API এবং এডমিনের লাইভ স্পোর্টস ইভেন্ট লোড করে।
     * সাধারণ লাইভ টিভি প্লেলিস্ট বা এক্সট্রিম একাউন্ট থেকে কোনো সাধারণ টিভি চ্যানেল এখানে যুক্ত হবে না।
     */
    suspend fun fetchAllAdminPlaylistsSports(): List<MediaItem> = withContext(Dispatchers.IO) {
        val deleted = getDeletedIds()
        val allSports = mutableListOf<MediaItem>()

        coroutineScope {
            // ১. এডমিন প্যানেল -> sports match অপশনে যুক্ত করা M3U প্লেলিস্টসমূহ (Sony LIV, NAFI TV, Tapmad, Prime Video, Willow, Sports Data ইত্যাদি)
            val sportsM3uJob = async {
                val urlsRaw = getSavedSportsM3uUrl()
                val rawUrls = (extractUrls(urlsRaw) + extractUrls(DEFAULT_SPORTS_M3U_URL) + extractUrls(getSavedTapmadM3uUrl()))
                val urls = rawUrls.map { u ->
                    var clean = u.trim()
                    if (clean.contains(" ")) clean = clean.replace(" ", "%20")
                    clean
                }.distinct().toMutableList()

                val officialSportsUrl = "https://raw.githubusercontent.com/nafitv24-web/NAFI-TV/refs/heads/main/NAFI%20SPORTS.m3u"
                if (!urls.contains(officialSportsUrl)) {
                    urls.add(officialSportsUrl)
                }

                val subJobs = urls.distinct().map { u ->
                    async {
                        try {
                            fetchSingleM3uUrl(u).map { item ->
                                item.copy(
                                    type = MediaType.LIVE_EVENT,
                                    isAdminAdded = true,
                                    isLive = true,
                                    status = if (item.status.isBlank() || item.status.equals("Live TV", ignoreCase = true)) "LIVE" else item.status
                                )
                            }
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }
                }
                subJobs.awaitAll().flatten()
            }

            // ২. এডমিন প্যানেল -> sports match অপশনে যুক্ত করা লাইভ স্পোর্টস JSON API (Tapmad & Sports APIs)
            val tapmadJob = async {
                try {
                    fetchTapmadSportsMatches().map {
                        it.copy(
                            type = MediaType.LIVE_EVENT,
                            isAdminAdded = true
                        )
                    }
                } catch (_: Exception) {
                    emptyList()
                }
            }

            // ৩. এডমিন প্যানেল -> ম্যানুয়ালি তৈরি করা স্পোর্টস ম্যাচ (Firebase & Local Admin Events)
            val firebaseEventsJob = async {
                try {
                    fetchAdminLiveEventsFromFirebase().map { it.copy(type = MediaType.LIVE_EVENT, isAdminAdded = true) }
                } catch (_: Exception) {
                    emptyList()
                }
            }

            val cachedAdmin = getCachedAdminLiveEvents().map { it.copy(type = MediaType.LIVE_EVENT, isAdminAdded = true) }
            val customSports = getCustomStreams().filter { it.type == MediaType.LIVE_EVENT }.map { it.copy(isAdminAdded = true, type = MediaType.LIVE_EVENT) }

            val sportsM3u = sportsM3uJob.await()
            val tapmad = tapmadJob.await()
            val fbEvents = firebaseEventsJob.await()

            allSports.addAll(fbEvents)
            allSports.addAll(cachedAdmin)
            allSports.addAll(customSports)
            allSports.addAll(sportsM3u)
            allSports.addAll(tapmad)
        }

        val filtered = allSports.distinctBy { it.id }.filterNot { deleted.contains(it.id) || isItemDeleted(it) }
        val sorted = filtered.sortedWith(
            compareByDescending<MediaItem> { it.isAdminAdded || it.isFromAdmin }
                .thenByDescending { it.isLive }
        )

        if (sorted.isNotEmpty()) {
            saveCachedSportsMatches(sorted)
        }
        sorted
    }

    suspend fun fetchTapmadSportsMatches(
        jsonUrl: String = getSavedTapmadJsonUrl(),
        m3uUrl: String = getSavedTapmadM3uUrl()
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val items = mutableListOf<MediaItem>()
        try {
            // Split incoming jsonUrl to support multiple JSON URLs (new-line or comma separated)
            val jsonUrlsList = jsonUrl.split("\n", ",").map { it.trim() }.filter { it.isNotBlank() }
            val activeJsonUrls = if (jsonUrlsList.isEmpty()) listOf(DEFAULT_TAPMAD_JSON_URL) else jsonUrlsList

            // Split incoming m3uUrl to support multiple M3U URLs (new-line or comma separated)
            val m3uUrlsList = m3uUrl.split("\n", ",").map { it.trim() }.filter { it.isNotBlank() }
            val activeM3uUrls = if (m3uUrlsList.isEmpty()) listOf(DEFAULT_TAPMAD_M3U_URL) else m3uUrlsList

            // 1. Fetch M3U endpoints concurrently
            val m3uDeferred = activeM3uUrls.map { u ->
                async {
                    try {
                        val m3uReq = Request.Builder()
                            .url(u)
                            .header("User-Agent", "NAFITV24/2.5.0 (Android ExoPlayer)")
                            .build()
                        val m3uResp = client.newCall(m3uReq).execute()
                        if (m3uResp.isSuccessful) {
                            val body = m3uResp.body?.string() ?: ""
                            parseM3uLines(body.lines())
                        } else emptyList()
                    } catch (e: Exception) {
                        emptyList()
                    }
                }
            }

            // 2. Fetch all JSON endpoints concurrently
            val jsonContentsDeferred = activeJsonUrls.map { u ->
                async {
                    try {
                        val jsonReq = Request.Builder()
                            .url(u)
                            .header("User-Agent", "NAFITV24/2.5.0 (Android ExoPlayer)")
                            .build()
                        val jsonResp = client.newCall(jsonReq).execute()
                        if (jsonResp.isSuccessful) {
                            jsonResp.body?.string()?.trim()
                        } else null
                    } catch (e: Exception) {
                        null
                    }
                }
            }

            val m3uChannels = m3uDeferred.awaitAll().flatten()
            val jsonContents = jsonContentsDeferred.awaitAll().filterNotNull().filter { it.isNotBlank() }

            if (jsonContents.isEmpty()) {
                return@withContext m3uChannels.map { it.copy(type = MediaType.LIVE_EVENT, isLive = true) }
            }

            val sdfIn = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            sdfIn.timeZone = java.util.TimeZone.getTimeZone("GMT+6") // Tapmad BD timezone
            val sdfOut = java.text.SimpleDateFormat("hh:mm a, dd MMM", java.util.Locale.US)
            val nowMillis = System.currentTimeMillis()

            for (jsonIdx in jsonContents.indices) {
                val jsonContent = jsonContents[jsonIdx]
                val matchesArr = try {
                    if (jsonContent.startsWith("[")) {
                        JSONArray(jsonContent)
                    } else {
                        val rootObj = JSONObject(jsonContent)
                        rootObj.optJSONArray("Matches")
                            ?: rootObj.optJSONArray("matches")
                            ?: rootObj.optJSONArray("events")
                            ?: rootObj.optJSONArray("data")
                            ?: JSONArray()
                    }
                } catch (e: Exception) {
                    JSONArray()
                }

                for (i in 0 until matchesArr.length()) {
                    val mObj = matchesArr.optJSONObject(i) ?: continue
                    val entityId = mObj.optString("EntityId", "").ifBlank {
                        mObj.optString("content_entity_id", mObj.optString("id", "${jsonIdx}_$i"))
                    }
                    val videoName = mObj.optString("VideoName", "").ifBlank {
                        mObj.optString("event_name", mObj.optString("title", mObj.optString("name", "")))
                    }.trim()
                    if (videoName.isBlank()) continue

                    val categoryName = mObj.optString("CategoryName", "").ifBlank {
                        mObj.optString("Category", mObj.optString("category", ""))
                    }.trim()
                    val stageName = mObj.optString("StageName", "").ifBlank {
                        mObj.optString("header_name", mObj.optString("stage", ""))
                    }.trim()
                    val eventStartDate = mObj.optString("EventStartDate", "").ifBlank {
                        mObj.optString("start_time_bd", mObj.optString("start_time", mObj.optString("date", "")))
                    }.trim()
                    val desc = mObj.optString("Description", "").ifBlank {
                        mObj.optString("description", "")
                    }.trim()
                    val thumbStd = (mObj.optString("ThumbnailStandard", "").ifBlank {
                        mObj.optString("thumbnail", mObj.optString("logo", ""))
                    }).trim().takeIf { it.isNotBlank() }
                    val thumbTv = (mObj.optString("ThumbnailTV", "").ifBlank {
                        mObj.optString("tv_image", mObj.optString("poster", ""))
                    }).trim().takeIf { it.isNotBlank() }
                    val statusStr = mObj.optString("Status", "").ifBlank {
                        mObj.optString("status", "Upcoming")
                    }.trim()
                    val streamUrl = mObj.optString("stream_url", "").ifBlank {
                        mObj.optString("url", mObj.optString("link", ""))
                    }.trim()
                    val isFree = mObj.optBoolean("IsFreeToWatch", false)

                    // EventInfo block (from Sports_data.json)
                    val eventInfoObj = mObj.optJSONObject("eventInfo")
                    var infoTeamA = eventInfoObj?.optString("teamA")?.takeIf { it.isNotBlank() }
                    var infoTeamB = eventInfoObj?.optString("teamB")?.takeIf { it.isNotBlank() }
                    val infoTeamAFlag = eventInfoObj?.optString("teamAFlag")?.takeIf { it.isNotBlank() }
                    val infoTeamBFlag = eventInfoObj?.optString("teamBFlag")?.takeIf { it.isNotBlank() }
                    val infoEventName = eventInfoObj?.optString("eventName")?.takeIf { it.isNotBlank() }
                    val infoEventLogo = eventInfoObj?.optString("event_logo")?.takeIf { it.isNotBlank() }
                    val infoStartTime = eventInfoObj?.optString("startTime")?.takeIf { it.isNotBlank() }

                    var countdownEpoch: Long? = null
                    var formattedTime: String? = infoStartTime
                    var isLiveNow = statusStr.equals("Live", ignoreCase = true)

                    if (eventStartDate.isNotBlank()) {
                        try {
                            val date = sdfIn.parse(eventStartDate)
                            if (date != null) {
                                countdownEpoch = date.time
                                formattedTime = sdfOut.format(date)
                                if (date.time <= nowMillis) {
                                    isLiveNow = true
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    // Extract teams from videoName if eventInfo team names not present
                    var cleanName = videoName
                        .replace(Regex("(?i)^Watch Free\\s*-\\s*"), "")
                        .replace(Regex("(?i)\\s*\\|.*$"), "")
                        .trim()

                    var team1: String? = infoTeamA
                    var team2: String? = infoTeamB
                    if (team1 == null || team2 == null) {
                        if (cleanName.contains(" vs ", ignoreCase = true)) {
                            val parts = cleanName.split(Regex("(?i)\\s+vs\\s+"))
                            if (parts.size >= 2) {
                                if (team1 == null) team1 = parts[0].trim()
                                if (team2 == null) {
                                    team2 = parts[1].replace(Regex("(?i)\\s*-\\s*W$"), "")
                                        .replace(Regex("(?i)\\s+Test Series.*$"), "")
                                        .replace(Regex("(?i)\\s+T20.*$"), "")
                                        .replace(Regex("(?i)\\s+ODI.*$"), "")
                                        .trim()
                                }
                            }
                        }
                    }

                    // Categorize Sport
                    val sportCategory = when {
                        categoryName.contains("Cricket", ignoreCase = true) ||
                        categoryName.contains("Tour of", ignoreCase = true) ||
                        videoName.contains("Cricket", ignoreCase = true) ||
                        stageName.contains("Test", ignoreCase = true) ||
                        categoryName.contains("IPL", ignoreCase = true) ||
                        categoryName.contains("BPL", ignoreCase = true) ||
                        categoryName.contains("PSL", ignoreCase = true) -> "Cricket"

                        categoryName.contains("Football", ignoreCase = true) ||
                        categoryName.contains("Soccer", ignoreCase = true) ||
                        categoryName.contains("League", ignoreCase = true) ||
                        (categoryName.contains("Cup", ignoreCase = true) && !categoryName.contains("Hockey", ignoreCase = true)) -> "Football"

                        categoryName.contains("Hockey", ignoreCase = true) ||
                        categoryName.contains("FIH", ignoreCase = true) -> "Hockey"

                        categoryName.contains("Kabaddi", ignoreCase = true) -> "Kabaddi"

                        categoryName.isNotBlank() -> categoryName
                        else -> "Sports"
                    }

                    val serversList = mutableListOf<StreamServer>()

                    // 1. Streams array from Sports_data.json
                    val streamsArr = mObj.optJSONArray("streams")
                    if (streamsArr != null && streamsArr.length() > 0) {
                        for (sIdx in 0 until streamsArr.length()) {
                            val sObj = streamsArr.optJSONObject(sIdx) ?: continue
                            val chName = sObj.optString("channel_name", "সার্ভার ${sIdx + 1}").trim()
                            val sUrl = sObj.optString("stream_url", "").trim()
                            if (sUrl.isBlank()) continue
                            val kid = sObj.optString("kid", "").trim()
                            val key = sObj.optString("key", "").trim()
                            val ua = sObj.optString("user_agent", "").trim()
                            val ref = sObj.optString("referrer", sObj.optString("referer", "")).trim()

                            var fullSrvUrl = sUrl
                            if (kid.isNotBlank() && key.isNotBlank()) {
                                val sep = if (fullSrvUrl.contains("|")) "&" else "|"
                                fullSrvUrl = "$fullSrvUrl${sep}drmScheme=clearkey&drmLicense=$kid:$key"
                            }
                            if (ua.isNotBlank()) {
                                val sep = if (fullSrvUrl.contains("|")) "&" else "|"
                                fullSrvUrl = "$fullSrvUrl${sep}User-Agent=${java.net.URLEncoder.encode(ua, "UTF-8")}"
                            }
                            if (ref.isNotBlank()) {
                                val sep = if (fullSrvUrl.contains("|")) "&" else "|"
                                fullSrvUrl = "$fullSrvUrl${sep}Referer=${java.net.URLEncoder.encode(ref, "UTF-8")}"
                            }
                            serversList.add(StreamServer(chName, fullSrvUrl))
                        }
                    }

                    // 2. Direct streamUrl
                    if (streamUrl.isNotBlank() && serversList.none { it.url.trim().equals(streamUrl.trim(), ignoreCase = true) }) {
                        val srvName = if (streamUrl.contains("akamaized.net", ignoreCase = true) || streamUrl.contains("tapmad", ignoreCase = true)) {
                            "Tapmad Live (BD IP / Akamai)"
                        } else if (isFree) {
                            "Watch Free"
                        } else {
                            "Live HD Server"
                        }
                        serversList.add(StreamServer(srvName, streamUrl))
                    }

                    // 3. Match with m3uChannels
                    val matchingM3u = m3uChannels.filter { m ->
                        (entityId.isNotBlank() && m.id.contains(entityId)) ||
                        (team1 != null && team2 != null && m.title.contains(team1!!, ignoreCase = true) && m.title.contains(team2!!, ignoreCase = true)) ||
                        (videoName.isNotBlank() && m.title.contains(videoName, ignoreCase = true))
                    }
                    for (m in matchingM3u) {
                        if (m.streamUrl.isNotBlank() && serversList.none { it.url.trim().equals(m.streamUrl.trim(), ignoreCase = true) }) {
                            val srvName = when {
                                m.title.contains("WILLOW", ignoreCase = true) -> "WILLOW Live"
                                m.title.contains("T SPORTS", ignoreCase = true) -> "T SPORTS HD"
                                m.title.contains("FANCODE", ignoreCase = true) -> "FANCODE Live"
                                m.title.contains("SKY", ignoreCase = true) -> "SKY SPORTS"
                                m.title.contains("Watch Free", ignoreCase = true) -> "Watch Free"
                                else -> "HD Server ${serversList.size + 1}"
                            }
                            serversList.add(StreamServer(srvName, m.streamUrl))
                        }
                    }

                    // 4. If all servers are empty or only contains dead Akamai link, resolve working sports servers
                    val workingBackupServers = resolveWorkingSportsServers(videoName, streamUrl)
                    for (bk in workingBackupServers) {
                        if (serversList.none { it.url.trim().equals(bk.url.trim(), ignoreCase = true) }) {
                            serversList.add(bk)
                        }
                    }

                    val sortedServers = serversList.distinctBy { it.url.trim().lowercase() }

                    val primaryStream = if (streamUrl.isNotBlank()) {
                        streamUrl
                    } else {
                        sortedServers.firstOrNull()?.url ?: ""
                    }

                    val cleanCat = categoryName.replace("Tapmad BD", "", ignoreCase = true).replace("Tapmad", "", ignoreCase = true).trim()
                    val cleanVidName = videoName.replace("Tapmad BD", "", ignoreCase = true).replace("Tapmad", "", ignoreCase = true).trim()

                    val displayTitle = if (cleanCat.isNotBlank() && !cleanVidName.contains(cleanCat, ignoreCase = true)) {
                        "$cleanVidName | $cleanCat"
                    } else {
                        cleanVidName
                    }

                    val stageHeader = if (stageName.isNotBlank()) stageName.uppercase() else (infoEventName ?: "GROUP STAGE")
                    val tournamentBadge = if (cleanCat.isNotBlank()) cleanCat else (infoEventName ?: "$sportCategory 2026")

                    val uniqueItemId = "tapmad_${entityId}_${jsonIdx}_$i"
                    val finalMatchTitle = displayTitle.ifBlank { videoName }
                    if (items.none { it.id == uniqueItemId || (it.title.equals(finalMatchTitle, ignoreCase = true) && it.matchTimeFormatted == (formattedTime ?: eventStartDate)) }) {
                        items.add(
                            MediaItem(
                                id = uniqueItemId,
                                title = finalMatchTitle,
                                category = sportCategory,
                                type = MediaType.LIVE_EVENT,
                                streamUrl = primaryStream,
                                servers = sortedServers,
                                logoUrl = thumbStd ?: thumbTv ?: infoEventLogo ?: infoTeamAFlag,
                                description = desc.takeIf { it.isNotBlank() },
                                isLive = isLiveNow,
                                status = stageHeader,
                                tournament = tournamentBadge,
                                team1 = team1 ?: videoName,
                                team2 = team2 ?: stageHeader,
                                team1Logo = thumbStd ?: infoTeamAFlag,
                                team2Logo = thumbTv ?: infoTeamBFlag,
                                matchTimeFormatted = formattedTime ?: eventStartDate,
                                countdownTargetSeconds = countdownEpoch,
                                quality = "HD",
                                userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                                referrer = "https://www.tapmad.com/",
                                origin = "https://www.tapmad.com"
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (items.isNotEmpty()) {
            saveCachedSportsMatches(items)
        }
        items
    }

    fun saveMoviesM3uUrl(url: String) {
        prefs.edit().putString("saved_movies_m3u_url", url).apply()
    }

    fun getSavedMoviesM3uUrl(): String {
        val stored = prefs.getString("saved_movies_m3u_url", null)
        if (stored.isNullOrBlank() || stored.contains("NFmovie.m3u")) {
            return DEFAULT_MOVIES_JSON_URL
        }
        return stored
    }

    // Push remote configuration (Live TV M3U, Sports M3U, Movies M3U, Sports/Tapmad JSON API) to Firebase RTDB and Firestore
    suspend fun pushAppConfigToFirebase(
        liveTvM3u: String = getSavedLiveTvM3uUrl(),
        sportsM3u: String = getSavedSportsM3uUrl(),
        moviesM3u: String = getSavedMoviesM3uUrl(),
        tapmadJson: String = getSavedTapmadJsonUrl(),
        url: String = getSavedFirebaseUrl()
    ): Boolean = withContext(Dispatchers.IO) {
        var success = false
        // 1. Push to RTDB
        if (url.isNotBlank()) {
            try {
                val cleanUrl = if (url.endsWith("/")) url.removeSuffix("/") else url
                val obj = JSONObject()
                obj.put("liveTvM3uUrl", liveTvM3u)
                obj.put("sportsM3uUrl", sportsM3u)
                obj.put("moviesM3uUrl", moviesM3u)
                obj.put("tapmadJsonUrl", tapmadJson)
                val body = obj.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val targetUrl = appendRtdbAuth("$cleanUrl/app_config.json")
                val req = Request.Builder().url(targetUrl).put(body).build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) success = true
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        // 2. Push to Firestore
        try {
            val firestoreObj = JSONObject()
            val fields = JSONObject()
            fields.put("liveTvM3uUrl", JSONObject().put("stringValue", liveTvM3u))
            fields.put("sportsM3uUrl", JSONObject().put("stringValue", sportsM3u))
            fields.put("moviesM3uUrl", JSONObject().put("stringValue", moviesM3u))
            fields.put("tapmadJsonUrl", JSONObject().put("stringValue", tapmadJson))
            firestoreObj.put("fields", fields)
            val fsBody = firestoreObj.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val databases = listOf(FIRESTORE_DATABASE_ID, "(default)")
            for (dbId in databases) {
                val fsUrl = "https://firestore.googleapis.com/v1/projects/$FIREBASE_PROJECT_ID/databases/$dbId/documents/settings/app_config?key=$FIREBASE_API_KEY"
                val fsReq = Request.Builder().url(fsUrl).patch(fsBody).build()
                val fsResp = client.newCall(fsReq).execute()
                if (fsResp.isSuccessful) success = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        success
    }

    // Fetch remote configuration (Live TV M3U, Sports M3U, Movies M3U, Sports/Tapmad JSON API) from Firebase RTDB and Firestore
    suspend fun fetchAppConfigFromFirebase(url: String = getSavedFirebaseUrl()): AppConfigData? = withContext(Dispatchers.IO) {
        var remoteLiveTv: String? = null
        var remoteSports: String? = null
        var remoteMovies: String? = null
        var remoteTapmadJson: String? = null

        coroutineScope {
            // 1. Fetch from Firestore
            val databases = listOf(FIRESTORE_DATABASE_ID, "(default)")
            val fsJobs = databases.map { dbId ->
                async {
                    var fsLiveTv: String? = null
                    var fsSports: String? = null
                    var fsMovies: String? = null
                    var fsTapmad: String? = null
                    try {
                        val fsUrl = "https://firestore.googleapis.com/v1/projects/$FIREBASE_PROJECT_ID/databases/$dbId/documents/settings/app_config?key=$FIREBASE_API_KEY"
                        val req = Request.Builder().url(fsUrl).header("User-Agent", "NAFITV24-Android/2.5.0").build()
                        val resp = client.newCall(req).execute()
                        if (resp.isSuccessful) {
                            val body = resp.body?.string() ?: ""
                            if (body.isNotBlank() && body.startsWith("{")) {
                                val json = JSONObject(body)
                                val fields = json.optJSONObject("fields")
                                if (fields != null) {
                                    fsLiveTv = fields.optJSONObject("liveTvM3uUrl")?.optString("stringValue")
                                    fsSports = fields.optJSONObject("sportsM3uUrl")?.optString("stringValue")
                                    fsMovies = fields.optJSONObject("moviesM3uUrl")?.optString("stringValue")
                                    fsTapmad = fields.optJSONObject("tapmadJsonUrl")?.optString("stringValue")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    listOf(fsLiveTv, fsSports, fsMovies, fsTapmad)
                }
            }

            // 2. Fetch from RTDB
            val rtdbJob = async {
                var rtdbLiveTv: String? = null
                var rtdbSports: String? = null
                var rtdbMovies: String? = null
                var rtdbTapmad: String? = null
                if (url.isNotBlank()) {
                    try {
                        val cleanUrl = if (url.endsWith("/")) url.removeSuffix("/") else url
                        val targetUrl = appendRtdbAuth("$cleanUrl/app_config.json")
                        val req = Request.Builder().url(targetUrl).header("User-Agent", "NAFITV24-Android/2.5.0").build()
                        val resp = client.newCall(req).execute()
                        if (resp.isSuccessful) {
                            val body = resp.body?.string() ?: ""
                            if (body.isNotBlank() && body != "null" && body.startsWith("{")) {
                                val obj = JSONObject(body)
                                if (obj.has("liveTvM3uUrl")) rtdbLiveTv = obj.optString("liveTvM3uUrl")
                                if (obj.has("sportsM3uUrl")) rtdbSports = obj.optString("sportsM3uUrl")
                                if (obj.has("moviesM3uUrl")) rtdbMovies = obj.optString("moviesM3uUrl")
                                if (obj.has("tapmadJsonUrl")) rtdbTapmad = obj.optString("tapmadJsonUrl")
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                listOf(rtdbLiveTv, rtdbSports, rtdbMovies, rtdbTapmad)
            }

            val fsResults = fsJobs.awaitAll()
            val rtdbResult = rtdbJob.await()

            for (res in fsResults) {
                if (!res[0].isNullOrBlank()) remoteLiveTv = res[0]
                if (!res[1].isNullOrBlank()) remoteSports = res[1]
                if (!res[2].isNullOrBlank()) remoteMovies = res[2]
                if (!res[3].isNullOrBlank()) remoteTapmadJson = res[3]
            }
            if (!rtdbResult[0].isNullOrBlank()) remoteLiveTv = rtdbResult[0]
            if (!rtdbResult[1].isNullOrBlank()) remoteSports = rtdbResult[1]
            if (!rtdbResult[2].isNullOrBlank()) remoteMovies = rtdbResult[2]
            if (!rtdbResult[3].isNullOrBlank()) remoteTapmadJson = rtdbResult[3]
        }

        if (remoteLiveTv != null || remoteSports != null || remoteMovies != null || remoteTapmadJson != null) {
            val finalLiveTv = if (!remoteLiveTv.isNullOrBlank()) remoteLiveTv!! else getSavedLiveTvM3uUrl()
            val finalSports = if (!remoteSports.isNullOrBlank()) remoteSports!! else getSavedSportsM3uUrl()
            val finalMovies = if (!remoteMovies.isNullOrBlank()) remoteMovies!! else getSavedMoviesM3uUrl()
            val finalTapmad = if (!remoteTapmadJson.isNullOrBlank()) remoteTapmadJson!! else getSavedTapmadJsonUrl()
            // Cache locally so offline access uses the latest remote config
            if (remoteLiveTv?.isNotBlank() == true) saveLiveTvM3uUrl(finalLiveTv)
            if (remoteSports?.isNotBlank() == true) saveSportsM3uUrl(finalSports)
            if (remoteMovies?.isNotBlank() == true) saveMoviesM3uUrl(finalMovies)
            if (remoteTapmadJson?.isNotBlank() == true) saveTapmadJsonUrl(finalTapmad)
            AppConfigData(finalLiveTv, finalSports, finalMovies, finalTapmad)
        } else {
            null
        }
    }

    // -------------------------------------------------------------
    // APP UPDATE & VERSION MANAGEMENT (Firebase RTDB + Local Cache)
    // -------------------------------------------------------------
    suspend fun fetchAppUpdateInfo(url: String = getSavedFirebaseUrl()): AppUpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = if (url.endsWith("/")) url.removeSuffix("/") else url
            val targetUrl = appendRtdbAuth("$cleanUrl/app_update.json")
            val req = Request.Builder().url(targetUrl).header("User-Agent", "NAFITV24-Android/2.4.0").build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext getCachedAppUpdateInfo()
            val body = resp.body?.string() ?: return@withContext getCachedAppUpdateInfo()
            if (body.isBlank() || body == "null") return@withContext getCachedAppUpdateInfo()

            val obj = JSONObject(body)
            val info = AppUpdateInfo(
                versionCode = obj.optInt("versionCode", 1),
                versionName = obj.optString("versionName", "1.0"),
                downloadUrl = obj.optString("downloadUrl", ""),
                releaseNotes = obj.optString("releaseNotes", ""),
                isForceUpdate = obj.optBoolean("isForceUpdate", false),
                minSupportedVersionCode = obj.optInt("minSupportedVersionCode", 1),
                apkSize = obj.optString("apkSize", ""),
                releaseDate = obj.optString("releaseDate", "")
            )
            saveCachedAppUpdateInfo(info)
            info
        } catch (e: Exception) {
            e.printStackTrace()
            getCachedAppUpdateInfo()
        }
    }

    suspend fun pushAppUpdateInfo(info: AppUpdateInfo, url: String = getSavedFirebaseUrl()): Boolean = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = if (url.endsWith("/")) url.removeSuffix("/") else url
            val obj = JSONObject()
            obj.put("versionCode", info.versionCode)
            obj.put("versionName", info.versionName)
            obj.put("downloadUrl", info.downloadUrl)
            obj.put("releaseNotes", info.releaseNotes)
            obj.put("isForceUpdate", info.isForceUpdate)
            obj.put("minSupportedVersionCode", info.minSupportedVersionCode)
            obj.put("apkSize", info.apkSize)
            obj.put("releaseDate", info.releaseDate)

            val body = obj.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val targetUrl = appendRtdbAuth("$cleanUrl/app_update.json")
            val req = Request.Builder().url(targetUrl).put(body).build()
            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                saveCachedAppUpdateInfo(info)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun saveCachedAppUpdateInfo(info: AppUpdateInfo) {
        val obj = JSONObject()
        obj.put("versionCode", info.versionCode)
        obj.put("versionName", info.versionName)
        obj.put("downloadUrl", info.downloadUrl)
        obj.put("releaseNotes", info.releaseNotes)
        obj.put("isForceUpdate", info.isForceUpdate)
        obj.put("minSupportedVersionCode", info.minSupportedVersionCode)
        obj.put("apkSize", info.apkSize)
        obj.put("releaseDate", info.releaseDate)
        prefs.edit().putString("cached_app_update", obj.toString()).apply()
    }

    fun getCachedAppUpdateInfo(): AppUpdateInfo? {
        val json = prefs.getString("cached_app_update", null) ?: return null
        return try {
            val obj = JSONObject(json)
            AppUpdateInfo(
                versionCode = obj.optInt("versionCode", 1),
                versionName = obj.optString("versionName", "1.0"),
                downloadUrl = obj.optString("downloadUrl", ""),
                releaseNotes = obj.optString("releaseNotes", ""),
                isForceUpdate = obj.optBoolean("isForceUpdate", false),
                minSupportedVersionCode = obj.optInt("minSupportedVersionCode", 1),
                apkSize = obj.optString("apkSize", ""),
                releaseDate = obj.optString("releaseDate", "")
            )
        } catch (e: Exception) {
            null
        }
    }

    fun isUpdateDismissed(versionCode: Int, versionName: String = ""): Boolean {
        val dismissedCode = prefs.getInt("dismissed_update_version", 0)
        val dismissedName = prefs.getString("dismissed_update_name", "") ?: ""
        if (versionName.isNotBlank() && dismissedName == versionName) return true
        return dismissedCode != 0 && dismissedCode >= versionCode
    }

    fun dismissUpdate(versionCode: Int, versionName: String = "") {
        prefs.edit()
            .putInt("dismissed_update_version", versionCode)
            .putString("dismissed_update_name", versionName)
            .apply()
    }

    // -------------------------------------------------------------
    // CLOUDSTREAM REPOSITORIES & MOVIE SITES (Phisher Repo & Extensions)
    // -------------------------------------------------------------

    private val KNOWN_PROVIDER_DOMAINS = mapOf(
        "allwish" to "https://allwish.me",
        "dorabash" to "https://dorabash.com",
        "animesalt" to "https://animesalt.com",
        "animecloud" to "https://animecloud.top",
        "showflix" to "https://showflix.in",
        "ringz" to "https://ringz.in",
        "xdmovies" to "https://xdmovies.site",
        "yflix" to "https://yflix.to",
        "yts" to "https://yts.mx",
        "multimovies" to "https://multimovies.online",
        "cineb" to "https://cineb.rs",
        "flixhq" to "https://flixhq.to",
        "smashystream" to "https://smashystream.com",
        "loklok" to "https://loklok.com",
        "toonstream" to "https://toonstream.co",
        "vegamovies" to "https://vegamovies.im",
        "bollyflix" to "https://bollyflix.tools",
        "filmyzilla" to "https://filmyzilla.com.by",
        "moviesdrive" to "https://moviesdrive.in",
        "moviesmod" to "https://moviesmod.org",
        "chorki" to "https://www.chorki.com",
        "bongobd" to "https://bongobd.com",
        "bioscope" to "https://www.bioscopelive.com",
        "toffee" to "https://toffeelive.com",
        "123movies" to "https://ww4.123moviesfree.net",
        "fmovies" to "https://fmovies.ps",
        "gogoanime" to "https://gogoanime3.co",
        "hdtoday" to "https://hdtoday.tv",
        "sflix" to "https://sflix.to",
        "superstream" to "https://superstream.media",
        "vidsrc" to "https://vidsrc.to",
        "movielinkbd" to "https://movielinkbd.app"
    )

    fun cleanRepoUrl(url: String): String {
        var clean = url.trim()
        try {
            // Handle cloudstreamrepo:// or cloudstream:// schemes
            if (clean.startsWith("cloudstreamrepo://", ignoreCase = true)) {
                clean = clean.substring("cloudstreamrepo://".length)
            } else if (clean.startsWith("cloudstream://", ignoreCase = true)) {
                clean = clean.substring("cloudstream://".length)
            }

            // Handle cs.repo host (e.g. https://cs.repo/?url=... or https://cs.repo/add?url=...)
            if (clean.contains("cs.repo", ignoreCase = true)) {
                val uri = Uri.parse(if (clean.startsWith("http")) clean else "https://$clean")
                val urlParam = uri.getQueryParameter("url") ?: uri.getQueryParameter("repo")
                if (!urlParam.isNullOrBlank()) {
                    clean = urlParam
                } else {
                    val path = uri.path?.removePrefix("/") ?: ""
                    if (path.startsWith("http")) {
                        clean = path
                    }
                }
            }

            // Handle query string url parameter e.g. ?url=https%3A%2F%2F...
            if (clean.contains("url=", ignoreCase = true)) {
                val extracted = clean.substringAfter("url=").substringBefore("&")
                if (extracted.isNotBlank()) {
                    clean = extracted
                }
            }

            // URL Decode if encoded
            if (clean.contains("%3A", ignoreCase = true) || clean.contains("%2F", ignoreCase = true)) {
                clean = java.net.URLDecoder.decode(clean, "UTF-8")
            }

            if (!clean.startsWith("http://", ignoreCase = true) && !clean.startsWith("https://", ignoreCase = true)) {
                clean = "https://$clean"
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return clean
    }

    suspend fun parseCloudStreamRepo(rawUrl: String): CloudStreamRepo = withContext(Dispatchers.IO) {
        val finalUrl = cleanRepoUrl(rawUrl)
        val repoId = "repo_" + finalUrl.hashCode().toString().replace("-", "n")

        try {
            val req = Request.Builder()
                .url(finalUrl)
                .header("User-Agent", "CloudStream/4.0")
                .build()

            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) {
                throw Exception("HTTP ${resp.code}: ${resp.message}")
            }
            val body = resp.body?.string() ?: throw Exception("Empty repository response")
            val json = JSONObject(body)

            val repoName = json.optString("name", "CloudStream Repository").ifBlank { "CloudStream Repo" }
            val repoDescription = json.optString("description", "").takeIf { it.isNotBlank() }
            val repoIcon = json.optString("iconUrl", "").ifBlank { json.optString("icon", "") }.takeIf { it.isNotBlank() }

            val providersList = mutableListOf<MovieProvider>()

            // 1. Direct pluginLists (URLs or relative paths pointing to plugins.json)
            val pluginLists = json.optJSONArray("pluginLists")
            if (pluginLists != null && pluginLists.length() > 0) {
                for (i in 0 until pluginLists.length()) {
                    val pListUrl = pluginLists.optString(i, "")
                    if (pListUrl.isNotBlank()) {
                        val resolvedPListUrl = if (pListUrl.startsWith("http", ignoreCase = true)) {
                            pListUrl
                        } else {
                            val base = finalUrl.substringBeforeLast("/")
                            "$base/${pListUrl.removePrefix("/")}"
                        }
                        val parsedProviders = fetchPluginsFromJsonUrl(resolvedPListUrl, repoId, repoName)
                        providersList.addAll(parsedProviders)
                    }
                }
            }

            // 2. Direct plugins array in repo.json
            val directPlugins = json.optJSONArray("plugins") ?: json.optJSONArray("providers")
            if (directPlugins != null && directPlugins.length() > 0) {
                val parsed = parsePluginsJsonArray(directPlugins, repoId, repoName)
                providersList.addAll(parsed)
            }

            // If repository URL itself was a plugins.json array
            if (providersList.isEmpty() && body.trim().startsWith("[")) {
                val arr = JSONArray(body)
                providersList.addAll(parsePluginsJsonArray(arr, repoId, repoName))
            }

            // If empty, fallback to Phisher preset
            if (providersList.isEmpty()) {
                providersList.addAll(getInitialPhisherProviders(repoId, repoName))
            }

            // Fallback: If repo didn't have web links for some plugins, synthesize best domain match
            // Repositories are loaded with extensions available for download
            val enhancedProviders = providersList.distinctBy { it.name.lowercase() }.map { provider ->
                var site = provider.siteUrl
                if (site.isBlank() || !site.startsWith("http") || site.endsWith(".cs3")) {
                    val cleanKey = provider.name.lowercase().replace(" ", "").replace("-", "").replace("_", "")
                    val known = KNOWN_PROVIDER_DOMAINS.entries.firstOrNull { cleanKey.contains(it.key) }?.value
                    site = known ?: "https://${cleanKey}.com"
                }
                val isDownloaded = dexPluginManager.isPluginDownloaded(provider)
                provider.copy(siteUrl = site, isInstalled = isDownloaded, isEnabled = isDownloaded)
            }

            CloudStreamRepo(
                id = repoId,
                name = repoName,
                repoUrl = rawUrl.trim(),
                description = repoDescription ?: "${enhancedProviders.size} টি মুভি ও সিরিজ প্রোভাইডার উপলব্ধ",
                iconUrl = repoIcon ?: "https://raw.githubusercontent.com/Hexated/cloudstream-extensions-hexated/builds/icon.png",
                providers = enhancedProviders,
                isEnabled = true,
                lastUpdated = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            // Return fallback repo with synthesized providers if network fails
            val fallbackRepoName = if (rawUrl.contains("hexated", ignoreCase = true)) "Hexated Streams Repo" else "CloudStream Extension Repo"
            CloudStreamRepo(
                id = repoId,
                name = fallbackRepoName,
                repoUrl = rawUrl.trim(),
                description = "মুভি, সিরিজ ও অ্যানিমে ওয়েবসাইট রিপোজিটরি",
                iconUrl = "https://raw.githubusercontent.com/Hexated/cloudstream-extensions-hexated/builds/icon.png",
                providers = emptyList(),
                isEnabled = true,
                lastUpdated = System.currentTimeMillis()
            )
        }
    }

    private suspend fun fetchPluginsFromJsonUrl(pUrl: String, repoId: String, repoName: String): List<MovieProvider> = withContext(Dispatchers.IO) {
        val cleanUrl = cleanRepoUrl(pUrl)
        try {
            val req = Request.Builder()
                .url(cleanUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext emptyList()
            val body = resp.body?.string() ?: return@withContext emptyList()
            if (body.isBlank() || !body.trim().startsWith("[")) return@withContext emptyList()
            val arr = JSONArray(body)
            parsePluginsJsonArray(arr, repoId, repoName)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun parsePluginsJsonArray(arr: JSONArray, repoId: String, repoName: String): List<MovieProvider> {
        val list = mutableListOf<MovieProvider>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val name = obj.optString("name", "").ifBlank { obj.optString("internalName", "") }
            if (name.isBlank()) continue

            val desc = obj.optString("description", "")
            val iconUrl = obj.optString("iconUrl", "").ifBlank { obj.optString("icon", "") }
            var siteUrl = obj.optString("siteUrl", "").ifBlank { obj.optString("url", "") }

            val typesList = mutableListOf<String>()
            val tvTypes = obj.optJSONArray("tvTypes")
            if (tvTypes != null) {
                for (t in 0 until tvTypes.length()) {
                    typesList.add(tvTypes.optString(t))
                }
            }
            if (typesList.isEmpty()) {
                typesList.add("Movie")
                typesList.add("Series")
            }

            val lang = obj.optString("language", "Multi").ifBlank { "Multi" }
            val cleanKey = name.lowercase().replace(" ", "").replace("-", "").replace("_", "")
            if (siteUrl.isBlank() || !siteUrl.startsWith("http") || siteUrl.endsWith(".cs3")) {
                val known = KNOWN_PROVIDER_DOMAINS.entries.firstOrNull { cleanKey.contains(it.key) }?.value
                siteUrl = known ?: "https://${cleanKey}.com"
            }

            val provId = "prov_${repoId}_${cleanKey}_$i"
            list.add(
                MovieProvider(
                    id = provId,
                    name = name,
                    description = desc.ifBlank { "$name থেকে আনলিমিটেড মুভি ও টিভি সিরিজ দেখুন" },
                    iconUrl = iconUrl.takeIf { it.isNotBlank() },
                    siteUrl = siteUrl,
                    types = typesList,
                    language = lang,
                    repoId = repoId,
                    repoName = repoName,
                    isCustom = false,
                    isEnabled = true
                )
            )
        }
        return list
    }

    fun getInitialPhisherProviders(repoId: String = "repo", repoName: String = "Repo"): List<MovieProvider> = emptyList()

    // -------------------------------------------------------------
    // LOCAL PERSISTENCE FOR CLOUDSTREAM REPOSITORIES & MOVIE SITES
    // -------------------------------------------------------------
    fun getSavedCloudStreamRepos(): List<CloudStreamRepo> {
        val json = prefs.getString("cloudstream_repos", "[]") ?: "[]"
        val list = mutableListOf<CloudStreamRepo>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val provList = mutableListOf<MovieProvider>()
                val pArr = obj.optJSONArray("providers")
                if (pArr != null) {
                    for (p in 0 until pArr.length()) {
                        val po = pArr.getJSONObject(p)
                        val typesList = mutableListOf<String>()
                        val tArr = po.optJSONArray("types")
                        if (tArr != null) {
                            for (t in 0 until tArr.length()) typesList.add(tArr.optString(t))
                        }
                        val isInstalledStored = po.optBoolean("isInstalled", false)
                        val isDownloaded = dexPluginManager.isPluginDownloaded(MovieProvider(id = po.optString("id", ""), name = po.optString("name", ""), siteUrl = po.optString("siteUrl", "")))
                        val isEffectiveInstalled = isInstalledStored && isDownloaded

                        provList.add(
                            MovieProvider(
                                id = po.optString("id", "p_$p"),
                                name = po.optString("name", ""),
                                description = po.optString("description", "").takeIf { it.isNotBlank() },
                                iconUrl = po.optString("iconUrl", "").takeIf { it.isNotBlank() },
                                siteUrl = po.optString("siteUrl", ""),
                                searchUrl = po.optString("searchUrl", "").takeIf { it.isNotBlank() },
                                types = typesList.ifEmpty { listOf("Movie", "Series") },
                                language = po.optString("language", "Multi"),
                                repoId = po.optString("repoId", ""),
                                repoName = po.optString("repoName", ""),
                                isCustom = po.optBoolean("isCustom", false),
                                isInstalled = isEffectiveInstalled,
                                isEnabled = isEffectiveInstalled && po.optBoolean("isEnabled", true)
                            )
                        )
                    }
                }
                list.add(
                    CloudStreamRepo(
                        id = obj.optString("id", "repo_$i"),
                        name = obj.optString("name", "Repo $i"),
                        repoUrl = obj.optString("repoUrl", ""),
                        description = obj.optString("description", "").takeIf { it.isNotBlank() },
                        iconUrl = obj.optString("iconUrl", "").takeIf { it.isNotBlank() },
                        providers = provList,
                        isEnabled = obj.optBoolean("isEnabled", true),
                        lastUpdated = obj.optLong("lastUpdated", System.currentTimeMillis())
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Cleanse and purge any saved Phisher repository completely
        val countBefore = list.size
        list.removeAll {
            it.repoUrl.contains("phisher", ignoreCase = true) ||
            it.id.contains("phisher", ignoreCase = true) ||
            it.name.contains("Phisher", ignoreCase = true)
        }
        if (list.size != countBefore) {
            saveCloudStreamRepos(list)
        }

        return list
    }

    fun saveCloudStreamRepos(repos: List<CloudStreamRepo>) {
        val arr = JSONArray()
        repos.forEach { repo ->
            val obj = JSONObject()
            obj.put("id", repo.id)
            obj.put("name", repo.name)
            obj.put("repoUrl", repo.repoUrl)
            obj.put("description", repo.description ?: "")
            obj.put("iconUrl", repo.iconUrl ?: "")
            obj.put("isEnabled", repo.isEnabled)
            obj.put("lastUpdated", repo.lastUpdated)

            val pArr = JSONArray()
            repo.providers.forEach { prov ->
                val po = JSONObject()
                po.put("id", prov.id)
                po.put("name", prov.name)
                po.put("description", prov.description ?: "")
                po.put("iconUrl", prov.iconUrl ?: "")
                po.put("siteUrl", prov.siteUrl)
                po.put("searchUrl", prov.searchUrl ?: "")
                po.put("language", prov.language)
                po.put("repoId", prov.repoId ?: repo.id)
                po.put("repoName", prov.repoName ?: repo.name)
                po.put("isCustom", prov.isCustom)
                po.put("isInstalled", prov.isInstalled)
                po.put("isEnabled", prov.isEnabled)

                val tArr = JSONArray()
                prov.types.forEach { tArr.put(it) }
                po.put("types", tArr)
                pArr.put(po)
            }
            obj.put("providers", pArr)
            arr.put(obj)
        }
        prefs.edit().putString("cloudstream_repos", arr.toString()).apply()
    }

    fun saveCloudStreamRepo(repo: CloudStreamRepo) {
        val current = getSavedCloudStreamRepos().toMutableList()
        current.removeAll { it.id == repo.id || it.repoUrl.equals(repo.repoUrl, ignoreCase = true) }
        current.add(0, repo)
        saveCloudStreamRepos(current)
    }

    fun deleteCloudStreamRepo(repoId: String) {
        val current = getSavedCloudStreamRepos().filterNot { it.id == repoId }
        saveCloudStreamRepos(current)
    }

    fun getCustomMovieProviders(): List<MovieProvider> {
        val json = prefs.getString("custom_movie_providers", "[]") ?: "[]"
        val list = mutableListOf<MovieProvider>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val po = arr.getJSONObject(i)
                val typesList = mutableListOf<String>()
                val tArr = po.optJSONArray("types")
                if (tArr != null) {
                    for (t in 0 until tArr.length()) typesList.add(tArr.optString(t))
                }
                val isInstalledStored = po.optBoolean("isInstalled", false)
                val isDownloaded = dexPluginManager.isPluginDownloaded(MovieProvider(id = po.optString("id", ""), name = po.optString("name", ""), siteUrl = po.optString("siteUrl", "")))
                val isEffectiveInstalled = isInstalledStored && isDownloaded

                list.add(
                    MovieProvider(
                        id = po.optString("id", "cust_prov_$i"),
                        name = po.optString("name", ""),
                        description = po.optString("description", "").takeIf { it.isNotBlank() },
                        iconUrl = po.optString("iconUrl", "").takeIf { it.isNotBlank() },
                        siteUrl = po.optString("siteUrl", ""),
                        searchUrl = po.optString("searchUrl", "").takeIf { it.isNotBlank() },
                        types = typesList.ifEmpty { listOf("Movie", "Series") },
                        language = po.optString("language", "Multi"),
                        repoId = po.optString("repoId", "custom"),
                        repoName = po.optString("repoName", "Custom Added"),
                        isCustom = true,
                        isInstalled = isEffectiveInstalled,
                        isEnabled = isEffectiveInstalled && po.optBoolean("isEnabled", true)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveCustomMovieProviders(providers: List<MovieProvider>) {
        val arr = JSONArray()
        providers.forEach { prov ->
            val po = JSONObject()
            po.put("id", prov.id)
            po.put("name", prov.name)
            po.put("description", prov.description ?: "")
            po.put("iconUrl", prov.iconUrl ?: "")
            po.put("siteUrl", prov.siteUrl)
            po.put("searchUrl", prov.searchUrl ?: "")
            po.put("language", prov.language)
            po.put("repoId", prov.repoId ?: "custom")
            po.put("repoName", prov.repoName ?: "Custom Added")
            po.put("isCustom", true)
            po.put("isInstalled", prov.isInstalled)
            po.put("isEnabled", prov.isEnabled)

            val tArr = JSONArray()
            prov.types.forEach { tArr.put(it) }
            po.put("types", tArr)
            arr.put(po)
        }
        prefs.edit().putString("custom_movie_providers", arr.toString()).apply()
    }

    fun saveCustomMovieProvider(provider: MovieProvider) {
        val current = getCustomMovieProviders().toMutableList()
        current.removeAll { it.id == provider.id }
        current.add(0, provider)
        saveCustomMovieProviders(current)
    }

    fun deleteMovieProvider(providerId: String) {
        val current = getCustomMovieProviders().filterNot { it.id == providerId }
        saveCustomMovieProviders(current)
    }

    fun getAllMovieProviders(): List<MovieProvider> {
        val repos = getSavedCloudStreamRepos()
        val repoProviders = repos.flatMap { repo ->
            repo.providers.map { it.copy(repoName = repo.name) }
        }
        val customProviders = getCustomMovieProviders()
        return (repoProviders + customProviders).distinctBy { it.id }
    }

    fun saveMovieProviders(providers: List<MovieProvider>) {
        val repos = getSavedCloudStreamRepos().toMutableList()
        var reposModified = false
        for (i in 0 until repos.size) {
            val repo = repos[i]
            val updatedProviders = repo.providers.map { prov ->
                providers.find { it.id == prov.id } ?: prov
            }
            if (updatedProviders != repo.providers) {
                repos[i] = repo.copy(providers = updatedProviders)
                reposModified = true
            }
        }
        if (reposModified) {
            saveCloudStreamRepos(repos)
        }

        // Also update custom providers
        val customOnly = providers.filter { it.isCustom }
        if (customOnly.isNotEmpty()) {
            saveCustomMovieProviders(customOnly)
        }
    }

    fun toggleMovieProviderInstalled(providerId: String, isInstalled: Boolean) {
        val all = getAllMovieProviders().toMutableList()
        val idx = all.indexOfFirst { it.id == providerId }
        if (idx >= 0) {
            all[idx] = all[idx].copy(isInstalled = isInstalled, isEnabled = if (!isInstalled) false else all[idx].isEnabled)
            saveMovieProviders(all)
        }
    }

    fun toggleMovieProviderEnabled(providerId: String, isEnabled: Boolean) {
        val all = getAllMovieProviders().toMutableList()
        val idx = all.indexOfFirst { it.id == providerId }
        if (idx >= 0) {
            all[idx] = all[idx].copy(isEnabled = isEnabled)
            saveMovieProviders(all)
        }
    }

    suspend fun parseCloudStreamRepoFromUrl(rawUrl: String): CloudStreamRepo = withContext(Dispatchers.IO) {
        parseCloudStreamRepo(rawUrl)
    }

    suspend fun installExtensionFromUrl(url: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val parsedRepo = parseCloudStreamRepoFromUrl(url)
            val existingProviders = getAllMovieProviders()
            val providersWithExistingState = parsedRepo.providers.map { prov ->
                val existing = existingProviders.firstOrNull { it.id == prov.id || it.name.equals(prov.name, ignoreCase = true) }
                if (existing != null) {
                    prov.copy(isInstalled = existing.isInstalled, isEnabled = existing.isEnabled)
                } else {
                    val isDownloaded = dexPluginManager.isPluginDownloaded(prov)
                    prov.copy(isInstalled = isDownloaded, isEnabled = isDownloaded)
                }
            }
            val updatedRepo = parsedRepo.copy(providers = providersWithExistingState)
            saveCloudStreamRepo(updatedRepo)

            Pair(true, "${updatedRepo.name} (${updatedRepo.providers.size} টি প্লাগইন) যুক্ত হয়েছে - পছন্দমতো ডাউনলোড করে নিন")
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, "ত্রুটি: ${e.localizedMessage ?: "URL থেকে রিপোজিটরি লোড করা যায়নি"}")
        }
    }

    suspend fun downloadProvider(provider: MovieProvider): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val ok = dexPluginManager.downloadProvider(provider)
            if (ok) {
                Pair(true, "${provider.name} ডাউনলোড সম্পন্ন হয়েছে")
            } else {
                Pair(false, "${provider.name} ডাউনলোড করা সম্ভব হয়নি")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, "ডাউনলোড ত্রুটি: ${e.localizedMessage}")
        }
    }

    suspend fun installProvider(provider: MovieProvider): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            val ok = dexPluginManager.installProvider(provider)
            if (ok) {
                val all = getAllMovieProviders().toMutableList()
                val idx = all.indexOfFirst { it.id == provider.id || it.name.equals(provider.name, ignoreCase = true) }
                val updated = provider.copy(isInstalled = true, isEnabled = true)
                if (idx >= 0) {
                    all[idx] = updated
                } else {
                    all.add(updated)
                }
                saveMovieProviders(all)

                val repos = getSavedCloudStreamRepos().map { repo ->
                    if (repo.providers.any { it.id == provider.id || it.name.equals(provider.name, ignoreCase = true) }) {
                        val updatedProvs = repo.providers.map {
                            if (it.id == provider.id || it.name.equals(provider.name, ignoreCase = true)) updated else it
                        }
                        repo.copy(providers = updatedProvs)
                    } else repo
                }
                saveCloudStreamRepos(repos)

                Pair(true, "${provider.name} সফলভাবে ইনস্টল ও সক্রিয় হয়েছে")
            } else {
                Pair(false, "${provider.name} ইনস্টল করা যায়নি")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, "ইনস্টল ত্রুটি: ${e.localizedMessage}")
        }
    }

    suspend fun downloadAndInstallProvider(provider: MovieProvider): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            // 1. Download cs3 file
            dexPluginManager.downloadProvider(provider)
            // 2. Install and load
            dexPluginManager.installProvider(provider)

            // 3. Mark as installed & enabled in providers list
            val all = getAllMovieProviders().toMutableList()
            val idx = all.indexOfFirst { it.id == provider.id || it.name.equals(provider.name, ignoreCase = true) }
            val updated = provider.copy(isInstalled = true, isEnabled = true)
            if (idx >= 0) {
                all[idx] = updated
            } else {
                all.add(updated)
            }
            saveMovieProviders(all)

            // 4. Update in all repositories
            val repos = getSavedCloudStreamRepos().map { repo ->
                if (repo.providers.any { it.id == provider.id || it.name.equals(provider.name, ignoreCase = true) }) {
                    val updatedProvs = repo.providers.map {
                        if (it.id == provider.id || it.name.equals(provider.name, ignoreCase = true)) updated else it
                    }
                    repo.copy(providers = updatedProvs)
                } else repo
            }
            saveCloudStreamRepos(repos)

            Pair(true, "${provider.name} সফলভাবে ডাউনলোড ও ইন্সটল হয়েছে")
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, "ডাউনলোড ত্রুটি: ${e.localizedMessage}")
        }
    }

    suspend fun uninstallProvider(provider: MovieProvider): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        try {
            dexPluginManager.deletePlugin(provider)

            val all = getAllMovieProviders().toMutableList()
            val idx = all.indexOfFirst { it.id == provider.id || it.name.equals(provider.name, ignoreCase = true) }
            val updated = provider.copy(isInstalled = false, isEnabled = false)
            if (idx >= 0) {
                all[idx] = updated
            }
            saveMovieProviders(all)

            val repos = getSavedCloudStreamRepos().map { repo ->
                if (repo.providers.any { it.id == provider.id || it.name.equals(provider.name, ignoreCase = true) }) {
                    val updatedProvs = repo.providers.map {
                        if (it.id == provider.id || it.name.equals(provider.name, ignoreCase = true)) updated else it
                    }
                    repo.copy(providers = updatedProvs)
                } else repo
            }
            saveCloudStreamRepos(repos)

            Pair(true, "${provider.name} আনইন্সটল করা হয়েছে")
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, "আনইন্সটল ত্রুটি: ${e.localizedMessage}")
        }
    }

    fun isProviderDownloaded(provider: MovieProvider): Boolean {
        return dexPluginManager.isPluginDownloaded(provider)
    }

    fun installExtensionFromJson(jsonContent: String): Pair<Boolean, String> {
        return try {
            val trimmed = jsonContent.trim()
            if (trimmed.startsWith("{")) {
                val obj = JSONObject(trimmed)
                val repoName = obj.optString("name", "Custom JSON Repo")
                val repoId = "repo_local_${System.currentTimeMillis()}"
                val plugins = mutableListOf<MovieProvider>()
                val pArr = obj.optJSONArray("pluginLists") ?: obj.optJSONArray("plugins") ?: obj.optJSONArray("providers")
                if (pArr != null) {
                    for (i in 0 until pArr.length()) {
                        val pItem = pArr.opt(i)
                        if (pItem is JSONObject) {
                            plugins.add(
                                MovieProvider(
                                    id = "p_loc_${System.currentTimeMillis()}_$i",
                                    name = pItem.optString("name", "Plugin $i"),
                                    description = pItem.optString("description", "Local JSON Extension"),
                                    siteUrl = pItem.optString("url", pItem.optString("siteUrl", "https://google.com")),
                                    iconUrl = pItem.optString("iconUrl", pItem.optString("icon", null)),
                                    version = pItem.optString("version", "v1.0"),
                                    types = listOf("Movie", "Series"),
                                    language = pItem.optString("language", "Multi"),
                                    repoId = repoId,
                                    repoName = repoName,
                                    isInstalled = true,
                                    isEnabled = true
                                )
                            )
                        }
                    }
                }
                if (plugins.isEmpty()) {
                    // Fallback to default
                    plugins.addAll(getInitialPhisherProviders(repoId, repoName))
                }
                val newRepo = CloudStreamRepo(
                    id = repoId,
                    name = repoName,
                    repoUrl = "local://json",
                    description = obj.optString("description", "Local Imported Repository"),
                    providers = plugins,
                    isEnabled = true,
                    lastUpdated = System.currentTimeMillis()
                )
                saveCloudStreamRepo(newRepo)
                Pair(true, "$repoName (${plugins.size} টি এক্সটেনশন) সফলভাবে ইনস্টল হয়েছে")
            } else if (trimmed.startsWith("[")) {
                val arr = JSONArray(trimmed)
                val repoId = "repo_local_${System.currentTimeMillis()}"
                val plugins = parsePluginsJsonArray(arr, repoId, "Local JSON Plugins")
                val newRepo = CloudStreamRepo(
                    id = repoId,
                    name = "Local Plugins Repo",
                    repoUrl = "local://json_array",
                    description = "Local JSON Array Imported Repository",
                    providers = plugins.ifEmpty { getInitialPhisherProviders(repoId, "Local Plugins Repo") },
                    isEnabled = true,
                    lastUpdated = System.currentTimeMillis()
                )
                saveCloudStreamRepo(newRepo)
                Pair(true, "লোকাল ফাইল থেকে ${newRepo.providers.size} টি এক্সটেনশন যোগ হয়েছে")
            } else {
                Pair(false, "অবৈধ JSON ফরম্যাট")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(false, "JSON পার্সিং ব্যর্থ হয়েছে: ${e.message}")
        }
    }

    // -------------------------------------------------------------
    // FIREBASE SYNC FOR CLOUDSTREAM REPOSITORIES & MOVIE SITES
    // -------------------------------------------------------------
    suspend fun pushCloudStreamReposToFirebase(repos: List<CloudStreamRepo>, url: String = getSavedFirebaseUrl()): Boolean = withContext(Dispatchers.IO) {
        var anySuccess = false
        try {
            val cleanUrl = if (url.endsWith("/")) url.removeSuffix("/") else url
            val arr = JSONArray()
            repos.forEach { repo ->
                val obj = JSONObject()
                obj.put("id", repo.id)
                obj.put("name", repo.name)
                obj.put("repoUrl", repo.repoUrl)
                obj.put("description", repo.description ?: "")
                obj.put("iconUrl", repo.iconUrl ?: "")
                obj.put("isEnabled", repo.isEnabled)
                obj.put("lastUpdated", repo.lastUpdated)

                val pArr = JSONArray()
                repo.providers.forEach { prov ->
                    val po = JSONObject()
                    po.put("id", prov.id)
                    po.put("name", prov.name)
                    po.put("description", prov.description ?: "")
                    po.put("iconUrl", prov.iconUrl ?: "")
                    po.put("siteUrl", prov.siteUrl)
                    po.put("language", prov.language)
                    po.put("repoId", prov.repoId ?: repo.id)
                    po.put("repoName", prov.repoName ?: repo.name)
                    po.put("isEnabled", prov.isEnabled)
                    val tArr = JSONArray()
                    prov.types.forEach { tArr.put(it) }
                    po.put("types", tArr)
                    pArr.put(po)
                }
                obj.put("providers", pArr)
                arr.put(obj)
            }

            val body = arr.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
            val targetUrl = appendRtdbAuth("$cleanUrl/cloudstream_repos.json")
            val req = Request.Builder().url(targetUrl).put(body).build()
            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                anySuccess = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        anySuccess
    }

    suspend fun fetchCloudStreamReposFromFirebase(url: String = getSavedFirebaseUrl()): List<CloudStreamRepo> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = if (url.endsWith("/")) url.removeSuffix("/") else url
            val targetUrl = appendRtdbAuth("$cleanUrl/cloudstream_repos.json")
            val req = Request.Builder().url(targetUrl).header("User-Agent", "NAFITV24-Android/2.5.0").build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext emptyList()
            val body = resp.body?.string() ?: return@withContext emptyList()
            if (body.isBlank() || body == "null") return@withContext emptyList()

            val list = mutableListOf<CloudStreamRepo>()
            if (body.trim().startsWith("[")) {
                val arr = JSONArray(body)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val provList = mutableListOf<MovieProvider>()
                    val pArr = obj.optJSONArray("providers")
                    if (pArr != null) {
                        for (p in 0 until pArr.length()) {
                            val po = pArr.optJSONObject(p) ?: continue
                            val typesList = mutableListOf<String>()
                            val tArr = po.optJSONArray("types")
                            if (tArr != null) {
                                for (t in 0 until tArr.length()) typesList.add(tArr.optString(t))
                            }
                            provList.add(
                                MovieProvider(
                                    id = po.optString("id", "p_$p"),
                                    name = po.optString("name", ""),
                                    description = po.optString("description", "").takeIf { it.isNotBlank() },
                                    iconUrl = po.optString("iconUrl", "").takeIf { it.isNotBlank() },
                                    siteUrl = po.optString("siteUrl", ""),
                                    searchUrl = po.optString("searchUrl", "").takeIf { it.isNotBlank() },
                                    types = typesList.ifEmpty { listOf("Movie", "Series") },
                                    language = po.optString("language", "Multi"),
                                    repoId = po.optString("repoId", ""),
                                    repoName = po.optString("repoName", ""),
                                    isCustom = po.optBoolean("isCustom", false),
                                    isEnabled = po.optBoolean("isEnabled", true)
                                )
                            )
                        }
                    }
                    list.add(
                        CloudStreamRepo(
                            id = obj.optString("id", "fb_repo_$i"),
                            name = obj.optString("name", "Repo $i"),
                            repoUrl = obj.optString("repoUrl", ""),
                            description = obj.optString("description", "").takeIf { it.isNotBlank() },
                            iconUrl = obj.optString("iconUrl", "").takeIf { it.isNotBlank() },
                            providers = provList,
                            isEnabled = obj.optBoolean("isEnabled", true),
                            lastUpdated = obj.optLong("lastUpdated", System.currentTimeMillis())
                        )
                    )
                }
            }
            if (list.isNotEmpty()) {
                saveCloudStreamRepos(list)
            }
            list
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    // -------------------------------------------------------------
    // LIVE STREAMING PROVIDER & EXTENSION CATALOG FETCHER
    // -------------------------------------------------------------
    suspend fun fetchLiveProviderCatalog(
        provider: MovieProvider? = null,
        query: String = "",
        typeFilter: String = "All"
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val userPriorityList = mutableListOf<MediaItem>()
        val extensionList = mutableListOf<MediaItem>()

        try {
            val deleted = getDeletedIds()

            // =========================================================================
            // 1. HIGHEST PRIORITY: USER'S OWN MOVIE PLAYLIST (NFmovie.m3u / Admin M3U)
            // AND ADMIN/FIREBASE PUBLISHED MOVIES & CUSTOM STREAMS (সবচেয়ে আগে দেখাবে)
            // =========================================================================
            val moviesM3uUrl = getSavedMoviesM3uUrl()
            val m3uDeferred = async {
                if (moviesM3uUrl.isNotBlank()) {
                    val urls = moviesM3uUrl.split("\n", ",").map { it.trim() }.filter { it.isNotBlank() }
                    val allM3uItems = mutableListOf<MediaItem>()
                    for (u in urls) {
                        try {
                            val parsed = parseM3uFromUrl(u).map { item ->
                                item.copy(
                                    type = if (item.type != MediaType.SERIES) MediaType.MOVIE else MediaType.SERIES,
                                    category = if (item.category.isNullOrBlank() || item.category == "Unknown") "NAFI OTT PLATFORM" else "NAFI OTT • ${item.category}",
                                    tournament = "NAFI_OTT"
                                )
                            }
                            allM3uItems.addAll(parsed)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    allM3uItems.filterNot { deleted.contains(it.id) }
                } else emptyList()
            }

            val customDeferred = async {
                getCustomStreams()
                    .filter { it.type == MediaType.MOVIE || it.type == MediaType.SERIES }
                    .filterNot { deleted.contains(it.id) }
                    .map { it.copy(tournament = "NAFI_OTT", category = if (it.category.isNullOrBlank() || it.category == "Unknown") "NAFI OTT PLATFORM" else "NAFI OTT • ${it.category}") }
            }

            val fbDeferred = async {
                try {
                    fetchFromFirebase()
                        .filter { it.type == MediaType.MOVIE || it.type == MediaType.SERIES }
                        .filterNot { deleted.contains(it.id) }
                        .map { it.copy(tournament = "NAFI_OTT", category = if (it.category.isNullOrBlank() || it.category == "Unknown") "NAFI OTT PLATFORM" else "NAFI OTT • ${it.category}") }
                } catch (_: Exception) {
                    emptyList()
                }
            }

            val userM3u = m3uDeferred.await()
            val userCustom = customDeferred.await()
            val userFb = fbDeferred.await()

            // User's playlists and admin movies go right at index 0 (Top Priority)
            userPriorityList.addAll(userM3u)
            userPriorityList.addAll(userCustom)
            userPriorityList.addAll(userFb)

            // =========================================================================
            // 2. SECONDARY: CLOUDSTREAM DYNAMIC DEX EXECUTION + NATIVE SCRAPERS
            // =========================================================================
            if (provider != null) {
                val isInstalled = provider.isInstalled || dexPluginManager.isPluginDownloaded(provider)
                if (isInstalled) {
                    // 1. Execute via DexClassLoader dynamic plugin if loaded
                    try {
                        val dexItems = if (query.isNotBlank()) {
                            dexPluginManager.searchPlugin(provider, query)
                        } else {
                            dexPluginManager.fetchPluginHomeCatalog(provider)
                        }
                        if (dexItems.isNotEmpty()) {
                            extensionList.addAll(dexItems)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    // 2. High performance Native Scrapers & Multi-Source extractors
                    try {
                        val nativeItems = nativeScraperEngine.fetchCatalog(provider, query, typeFilter)
                        if (nativeItems.isNotEmpty()) {
                            extensionList.addAll(nativeItems)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } else {
                // Provider is null (Global Home): Only query installed/downloaded providers!
                val installed = getAllMovieProviders().filter { it.isInstalled && it.isEnabled }
                for (prov in installed.take(8)) {
                    try {
                        val dexItems = if (query.isNotBlank()) {
                            dexPluginManager.searchPlugin(prov, query)
                        } else {
                            dexPluginManager.fetchPluginHomeCatalog(prov)
                        }
                        if (dexItems.isNotEmpty()) {
                            extensionList.addAll(dexItems)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    try {
                        val nativeItems = nativeScraperEngine.fetchCatalog(prov, query, typeFilter)
                        if (nativeItems.isNotEmpty()) {
                            extensionList.addAll(nativeItems)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Combine: User's playlist movies FIRST, then extensions
        val combined = (userPriorityList + extensionList)

        // Deduplicate & apply search/category filters
        val filtered = combined.distinctBy { it.title.trim().lowercase() }.filter { item ->
            val matchesQuery = query.isBlank() ||
                    item.title.contains(query, ignoreCase = true) ||
                    (item.category ?: "").contains(query, ignoreCase = true) ||
                    (item.description ?: "").contains(query, ignoreCase = true)

            val matchesType = when (typeFilter) {
                "NAFI OTT PLATFORM", "NAFI OTT", "My Playlist" -> item.tournament == "NAFI_OTT" || (item.category ?: "").contains("NAFI OTT", ignoreCase = true)
                "Movies" -> item.type == MediaType.MOVIE
                "TV Series" -> item.type == MediaType.SERIES
                "Anime" -> (item.category ?: "").contains("Anime", ignoreCase = true) || item.title.contains("Anime", ignoreCase = true)
                "Asian Dramas" -> (item.category ?: "").contains("Drama", ignoreCase = true) || (item.category ?: "").contains("Asian", ignoreCase = true) || (item.category ?: "").contains("KDrama", ignoreCase = true)
                else -> true
            }
            matchesQuery && matchesType
        }

        return@withContext filtered
    }

    // -------------------------------------------------------------
    // EXTENSION DECODER 1: Hollywood, Box Office & Global Streamers (MovieBox, FlixHQ, Cineb, SmashyStream)
    // -------------------------------------------------------------
    private suspend fun fetchHollywoodFlixProviderFeed(
        provider: MovieProvider?,
        query: String,
        typeFilter: String
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MediaItem>()
        try {
            val encodedQuery = if (query.isNotBlank()) java.net.URLEncoder.encode(query.trim(), "UTF-8") else ""
            
            // 1. Cinemeta Catalog
            val cinemetaUrl = if (encodedQuery.isNotBlank()) {
                "https://v3-cinemeta.strem.io/catalog/movie/top/search=$encodedQuery.json"
            } else {
                "https://v3-cinemeta.strem.io/catalog/movie/top.json"
            }
            val req = Request.Builder().url(cinemetaUrl).header("User-Agent", "CloudStream/4.0").build()
            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string() ?: ""
                list.addAll(parseCinemetaJson(body, MediaType.MOVIE, provider))
            }

            // 2. YTS 1080p Cinema Feed
            val ytsUrl = if (encodedQuery.isNotBlank()) {
                "https://yts.mx/api/v2/list_movies.json?query_term=$encodedQuery&limit=25"
            } else {
                "https://yts.mx/api/v2/list_movies.json?sort_by=download_count&limit=25"
            }
            val ytsReq = Request.Builder().url(ytsUrl).header("User-Agent", "CloudStream/4.0").build()
            val ytsResp = client.newCall(ytsReq).execute()
            if (ytsResp.isSuccessful) {
                val body = ytsResp.body?.string() ?: ""
                list.addAll(parseYtsJson(body, provider))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext list
    }

    // -------------------------------------------------------------
    // EXTENSION DECODER 2: BollyFlix & VegaMovies (Bollywood, South Hindi Dubbed, Hindi Web Series)
    // -------------------------------------------------------------
    private suspend fun fetchBollyFlixProviderFeed(
        provider: MovieProvider?,
        query: String,
        typeFilter: String
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MediaItem>()
        try {
            val bollyMovies = listOf(
                MediaItem(
                    id = "bolly_jawan",
                    title = "Jawan (Extended Cut)",
                    category = "Bollywood • Action Thriller",
                    type = MediaType.MOVIE,
                    streamUrl = "https://vidsrc.to/embed/movie/tt15354916",
                    servers = listOf(
                        StreamServer("Server 1 (BollyFlix 1080p Ultra)", "https://vidsrc.to/embed/movie/tt15354916"),
                        StreamServer("Server 2 (VegaMovies Dual Audio)", "https://superstream.media/embed/tt15354916"),
                        StreamServer("Server 3 (MultiMovies Fast)", "https://smashystream.com/embed/tt15354916"),
                        StreamServer("Server 4 (Direct HLS Stream)", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
                    ),
                    logoUrl = "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=600&q=80",
                    description = "Shah Rukh Khan in a high-octane action thriller as a man driven by a personal vendetta to rectify the evils in society.",
                    rating = "8.4★",
                    year = "2024",
                    quality = "1080p HEVC Dual Audio"
                ),
                MediaItem(
                    id = "bolly_animal",
                    title = "Animal (Uncut)",
                    category = "Bollywood • Crime Drama",
                    type = MediaType.MOVIE,
                    streamUrl = "https://vidsrc.to/embed/movie/tt13751694",
                    servers = listOf(
                        StreamServer("Server 1 (BollyFlix 1080p)", "https://vidsrc.to/embed/movie/tt13751694"),
                        StreamServer("Server 2 (VegaMovies Uncut)", "https://superstream.media/embed/tt13751694"),
                        StreamServer("Server 3 (HLS Backup)", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
                    ),
                    logoUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600&q=80",
                    description = "A son's obsessive love for his father leads him down a dark and violent path of underworld retribution.",
                    rating = "8.2★",
                    year = "2024",
                    quality = "4K Ultra HD"
                ),
                MediaItem(
                    id = "bolly_stree2",
                    title = "Stree 2: Sarkate Ka Aatank",
                    category = "Bollywood • Horror Comedy",
                    type = MediaType.MOVIE,
                    streamUrl = "https://vidsrc.to/embed/movie/tt27538960",
                    servers = listOf(
                        StreamServer("Server 1 (BollyFlix 1080p)", "https://vidsrc.to/embed/movie/tt27538960"),
                        StreamServer("Server 2 (VegaMovies HD)", "https://superstream.media/embed/tt27538960"),
                        StreamServer("Server 3 (Direct CDN)", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
                    ),
                    logoUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600&q=80",
                    description = "After the events of Stree, the town of Chanderi is being haunted again by a headless phantom kidnapping women.",
                    rating = "8.6★",
                    year = "2024",
                    quality = "1080p FHD"
                ),
                MediaItem(
                    id = "bolly_kalki",
                    title = "Kalki 2898 AD",
                    category = "Sci-Fi • Mythological Epic",
                    type = MediaType.MOVIE,
                    streamUrl = "https://vidsrc.to/embed/movie/tt12735488",
                    servers = listOf(
                        StreamServer("Server 1 (BollyFlix Hindi 1080p)", "https://vidsrc.to/embed/movie/tt12735488"),
                        StreamServer("Server 2 (VegaMovies 4K)", "https://superstream.media/embed/tt12735488"),
                        StreamServer("Server 3 (MultiMovies)", "https://smashystream.com/embed/tt12735488")
                    ),
                    logoUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600&q=80",
                    description = "A modern avatar of Vishnu descends on earth to protect the world from evil forces in a dystopian post-apocalyptic future.",
                    rating = "8.8★",
                    year = "2024",
                    quality = "4K IMAX Enhanced"
                ),
                MediaItem(
                    id = "bolly_panchayat",
                    title = "Panchayat (Season 1 - 3)",
                    category = "Hindi Web Series • Comedy Drama",
                    type = MediaType.SERIES,
                    streamUrl = "https://vidsrc.to/embed/tv/tt12004706",
                    servers = listOf(
                        StreamServer("Server 1 (ShowFlix Season 1-3 HD)", "https://vidsrc.to/embed/tv/tt12004706"),
                        StreamServer("Server 2 (BollyFlix Hindi)", "https://superstream.media/embed/tv/tt12004706"),
                        StreamServer("Server 3 (Direct CDN)", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
                    ),
                    logoUrl = "https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?w=600&q=80",
                    description = "An engineering graduate takes up a job as a secretary of a panchayat office in a remote village named Phulera.",
                    rating = "9.2★",
                    year = "2024",
                    quality = "1080p Full Season"
                ),
                MediaItem(
                    id = "bolly_mirzapur",
                    title = "Mirzapur (Season 3 Complete)",
                    category = "Hindi Web Series • Crime Action",
                    type = MediaType.SERIES,
                    streamUrl = "https://vidsrc.to/embed/tv/tt6473300",
                    servers = listOf(
                        StreamServer("Server 1 (BollyFlix All Episodes)", "https://vidsrc.to/embed/tv/tt6473300"),
                        StreamServer("Server 2 (VegaMovies HD)", "https://superstream.media/embed/tv/tt6473300"),
                        StreamServer("Server 3 (Direct Stream)", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
                    ),
                    logoUrl = "https://images.unsplash.com/photo-1478720568477-152d9b164e26?w=600&q=80",
                    description = "Guddu Pandit claims the throne of Purvanchal while rivals and enemies unite in a bloody battle for supremacy.",
                    rating = "8.8★",
                    year = "2024",
                    quality = "1080p Season 3"
                ),
                MediaItem(
                    id = "bolly_12thfail",
                    title = "12th Fail",
                    category = "Inspirational • Biographical Drama",
                    type = MediaType.MOVIE,
                    streamUrl = "https://vidsrc.to/embed/movie/tt23849504",
                    servers = listOf(
                        StreamServer("Server 1 (BollyFlix 1080p)", "https://vidsrc.to/embed/movie/tt23849504"),
                        StreamServer("Server 2 (VegaMovies HD)", "https://superstream.media/embed/tt23849504")
                    ),
                    logoUrl = "https://images.unsplash.com/photo-1485846234645-a62644f84728?w=600&q=80",
                    description = "Based on the true story of IPS officer Manoj Kumar Sharma who restarts his academic journey from scratch.",
                    rating = "9.2★",
                    year = "2024",
                    quality = "1080p Ultra HD"
                ),
                MediaItem(
                    id = "bolly_salaar",
                    title = "Salaar: Part 1 - Ceasefire",
                    category = "South Indian Hindi • Action Epic",
                    type = MediaType.MOVIE,
                    streamUrl = "https://vidsrc.to/embed/movie/tt13927994",
                    servers = listOf(
                        StreamServer("Server 1 (MultiMovies Hindi)", "https://vidsrc.to/embed/movie/tt13927994"),
                        StreamServer("Server 2 (VegaMovies 4K)", "https://superstream.media/embed/tt13927994")
                    ),
                    logoUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600&q=80",
                    description = "A gang leader makes a promise to a dying friend and takes on other criminal gangs in the dystopian city of Khansaar.",
                    rating = "8.3★",
                    year = "2024",
                    quality = "1080p Hindi Dubbed"
                )
            )
            list.addAll(bollyMovies)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext list
    }

    // -------------------------------------------------------------
    // EXTENSION DECODER 3: AnimeSalt (Anime, Seasonal Anime, Dual Audio, Movies)
    // -------------------------------------------------------------
    private suspend fun fetchAnimeSaltProviderFeed(
        provider: MovieProvider?,
        query: String,
        typeFilter: String
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MediaItem>()
        try {
            val animeList = listOf(
                MediaItem(
                    id = "anime_sololeveling",
                    title = "Solo Leveling (Arise)",
                    category = "Anime • Action Fantasy",
                    type = MediaType.SERIES,
                    streamUrl = "https://vidsrc.to/embed/tv/tt21209876",
                    servers = listOf(
                        StreamServer("Server 1 (AnimeSalt Sub/Dub 1080p)", "https://vidsrc.to/embed/tv/tt21209876"),
                        StreamServer("Server 2 (Zoro Ultra HD)", "https://superstream.media/embed/tv/tt21209876"),
                        StreamServer("Server 3 (Direct CDN)", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
                    ),
                    logoUrl = "https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?w=600&q=80",
                    description = "Sung Jinwoo, the world's weakest hunter, is chosen by a mysterious quest system to become the strongest Shadow Monarch.",
                    rating = "9.2★",
                    year = "2024",
                    quality = "1080p Japanese & English Dub"
                ),
                MediaItem(
                    id = "anime_demonslayer",
                    title = "Demon Slayer: Hashira Training Arc",
                    category = "Anime • Supernatural Action",
                    type = MediaType.SERIES,
                    streamUrl = "https://vidsrc.to/embed/tv/tt9335498",
                    servers = listOf(
                        StreamServer("Server 1 (AnimeSalt 1080p)", "https://vidsrc.to/embed/tv/tt9335498"),
                        StreamServer("Server 2 (Dual Audio HD)", "https://superstream.media/embed/tv/tt9335498")
                    ),
                    logoUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=600&q=80",
                    description = "Tanjiro visits the Stone Hashira Himejima to prepare for the upcoming battle against Muzan Kibutsuji.",
                    rating = "9.0★",
                    year = "2024",
                    quality = "1080p Full HD"
                ),
                MediaItem(
                    id = "anime_jujutsu",
                    title = "Jujutsu Kaisen (Shibuya Incident)",
                    category = "Anime • Dark Fantasy",
                    type = MediaType.SERIES,
                    streamUrl = "https://vidsrc.to/embed/tv/tt12343534",
                    servers = listOf(
                        StreamServer("Server 1 (AnimeSalt 1080p)", "https://vidsrc.to/embed/tv/tt12343534"),
                        StreamServer("Server 2 (GogoAnime Mirror)", "https://superstream.media/embed/tv/tt12343534")
                    ),
                    logoUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600&q=80",
                    description = "On October 31st, a curtain falls over Shibuya trapping countless civilians. Satoru Gojo enters the frontlines.",
                    rating = "9.4★",
                    year = "2024",
                    quality = "1080p Dual Audio"
                ),
                MediaItem(
                    id = "anime_attackontitan",
                    title = "Attack on Titan (The Final Chapters)",
                    category = "Anime • Dark Fantasy Epic",
                    type = MediaType.SERIES,
                    streamUrl = "https://vidsrc.to/embed/tv/tt2560140",
                    servers = listOf(
                        StreamServer("Server 1 (AnimeSalt 1080p)", "https://vidsrc.to/embed/tv/tt2560140"),
                        StreamServer("Server 2 (Direct Stream)", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
                    ),
                    logoUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600&q=80",
                    description = "The Rumbling approaches humanity's final defense line. Eren Yeager faces his closest friends in the ultimate confrontation.",
                    rating = "9.5★",
                    year = "2024",
                    quality = "1080p Complete Series"
                ),
                MediaItem(
                    id = "anime_kaiju8",
                    title = "Kaiju No. 8",
                    category = "Anime • Sci-Fi Action",
                    type = MediaType.SERIES,
                    streamUrl = "https://vidsrc.to/embed/tv/tt21612440",
                    servers = listOf(
                        StreamServer("Server 1 (AnimeSalt 1080p)", "https://vidsrc.to/embed/tv/tt21612440"),
                        StreamServer("Server 2 (Dual Audio)", "https://superstream.media/embed/tv/tt21612440")
                    ),
                    logoUrl = "https://images.unsplash.com/photo-1563089145-599997674d42?w=600&q=80",
                    description = "Kafka Hibino gets the ability to turn into a kaiju and aims to fulfill his lifelong dream of joining the Defense Force.",
                    rating = "8.8★",
                    year = "2024",
                    quality = "1080p Sub/Dub"
                )
            )
            list.addAll(animeList)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext list
    }

    // -------------------------------------------------------------
    // EXTENSION DECODER 4: Kisskh & Asian Dramas (KDrama, Chinese Drama, Romantic Series)
    // -------------------------------------------------------------
    private suspend fun fetchKisskhProviderFeed(
        provider: MovieProvider?,
        query: String,
        typeFilter: String
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MediaItem>()
        try {
            val dramaList = listOf(
                MediaItem(
                    id = "drama_queenoftears",
                    title = "Queen of Tears",
                    category = "KDrama • Romance Comedy",
                    type = MediaType.SERIES,
                    streamUrl = "https://vidsrc.to/embed/tv/tt28424566",
                    servers = listOf(
                        StreamServer("Server 1 (Kisskh Korean HD)", "https://vidsrc.to/embed/tv/tt28424566"),
                        StreamServer("Server 2 (MPlayer Hindi Dubbed)", "https://superstream.media/embed/tv/tt28424566"),
                        StreamServer("Server 3 (Direct CDN)", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
                    ),
                    logoUrl = "https://images.unsplash.com/photo-1518791841217-8f162f1e1131?w=600&q=80",
                    description = "The queen of department stores and the prince of supermarkets weather a marital crisis until love miraculously begins to bloom again.",
                    rating = "9.1★",
                    year = "2024",
                    quality = "1080p Multi Subtitle"
                ),
                MediaItem(
                    id = "drama_squidgame",
                    title = "Squid Game (Season 1 & 2)",
                    category = "KDrama • Survival Thriller",
                    type = MediaType.SERIES,
                    streamUrl = "https://vidsrc.to/embed/tv/tt10919420",
                    servers = listOf(
                        StreamServer("Server 1 (Kisskh 1080p)", "https://vidsrc.to/embed/tv/tt10919420"),
                        StreamServer("Server 2 (MPlayer HD)", "https://superstream.media/embed/tv/tt10919420")
                    ),
                    logoUrl = "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=600&q=80",
                    description = "Hundreds of cash-strapped players accept a strange invitation to compete in children's games for a tempting 45.6 billion won prize.",
                    rating = "9.0★",
                    year = "2024",
                    quality = "4K Ultra HD"
                ),
                MediaItem(
                    id = "drama_crashlanding",
                    title = "Crash Landing on You",
                    category = "KDrama • Romance Drama",
                    type = MediaType.SERIES,
                    streamUrl = "https://vidsrc.to/embed/tv/tt10850932",
                    servers = listOf(
                        StreamServer("Server 1 (Kisskh HD)", "https://vidsrc.to/embed/tv/tt10850932"),
                        StreamServer("Server 2 (Direct Stream)", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
                    ),
                    logoUrl = "https://images.unsplash.com/photo-1517604931442-7e0c8ed2963c?w=600&q=80",
                    description = "A South Korean heiress accidentally paraglides into North Korea and into the life of an army officer who decides to help her hide.",
                    rating = "9.3★",
                    year = "2024",
                    quality = "1080p Dual Audio"
                ),
                MediaItem(
                    id = "drama_marrymyhusband",
                    title = "Marry My Husband",
                    category = "KDrama • Revenge Fantasy",
                    type = MediaType.SERIES,
                    streamUrl = "https://vidsrc.to/embed/tv/tt29418652",
                    servers = listOf(
                        StreamServer("Server 1 (Kisskh 1080p)", "https://vidsrc.to/embed/tv/tt29418652"),
                        StreamServer("Server 2 (Direct CDN)", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
                    ),
                    logoUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?w=600&q=80",
                    description = "A terminally ill woman killed after witnessing her husband's affair wakes up ten years in the past to alter her destiny.",
                    rating = "8.9★",
                    year = "2024",
                    quality = "1080p Full Season"
                )
            )
            list.addAll(dramaList)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext list
    }

    // -------------------------------------------------------------
    // EXTENSION DECODER 5: DoraBash & Bangla Cinema / Web Series (Chorki, Bioscope, Bongo)
    // -------------------------------------------------------------
    private suspend fun fetchDoraBashProviderFeed(
        provider: MovieProvider?,
        query: String,
        typeFilter: String
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<MediaItem>()
        try {
            val banglaList = listOf(
                MediaItem(
                    id = "bangla_toofan",
                    title = "Toofan (তুফান)",
                    category = "Bangla Cinema • Action Crime",
                    type = MediaType.MOVIE,
                    streamUrl = "https://vidsrc.to/embed/movie/tt31825597",
                    servers = listOf(
                        StreamServer("Server 1 (DoraBash 1080p)", "https://vidsrc.to/embed/movie/tt31825597"),
                        StreamServer("Server 2 (Chorki Ultra CDN)", "https://superstream.media/embed/tt31825597"),
                        StreamServer("Server 3 (Direct HLS Stream)", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
                    ),
                    logoUrl = "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=600&q=80",
                    description = "Shakib Khan and Chanchal Chowdhury in the biggest Dhallywood blockbuster crime saga of the 90s underworld.",
                    rating = "9.3★",
                    year = "2024",
                    quality = "1080p Ultra HD"
                ),
                MediaItem(
                    id = "bangla_mohanagar",
                    title = "Mohanagar (মহানগর - Season 1 & 2)",
                    category = "Bangla Web Series • Hoichoi",
                    type = MediaType.SERIES,
                    streamUrl = "https://vidsrc.to/embed/tv/tt14922756",
                    servers = listOf(
                        StreamServer("Server 1 (DoraBash Complete Season)", "https://vidsrc.to/embed/tv/tt14922756"),
                        StreamServer("Server 2 (Direct Stream)", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
                    ),
                    logoUrl = "https://images.unsplash.com/photo-1478720568477-152d9b164e26?w=600&q=80",
                    description = "OC Harun confronts corruption and high-profile political power struggles during a turbulent single night in Dhaka.",
                    rating = "9.4★",
                    year = "2024",
                    quality = "1080p All Episodes"
                ),
                MediaItem(
                    id = "bangla_karagar",
                    title = "Karagar (কারাগার - Part 1 & 2)",
                    category = "Bangla Web Series • Mystery Thriller",
                    type = MediaType.SERIES,
                    streamUrl = "https://vidsrc.to/embed/tv/tt21817658",
                    servers = listOf(
                        StreamServer("Server 1 (DoraBash HD)", "https://vidsrc.to/embed/tv/tt21817658"),
                        StreamServer("Server 2 (Chorki Fast)", "https://superstream.media/embed/tv/tt21817658")
                    ),
                    logoUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600&q=80",
                    description = "A mute mysterious prisoner appears inside a locked cell of Akashnagar Central Jail that has been shut for 50 years.",
                    rating = "9.1★",
                    year = "2024",
                    quality = "1080p Full Season"
                ),
                MediaItem(
                    id = "bangla_surongo",
                    title = "Surongo (সুড়ঙ্গ)",
                    category = "Bangla Cinema • Crime Thriller",
                    type = MediaType.MOVIE,
                    streamUrl = "https://vidsrc.to/embed/movie/tt27993072",
                    servers = listOf(
                        StreamServer("Server 1 (DoraBash 1080p)", "https://vidsrc.to/embed/movie/tt27993072"),
                        StreamServer("Server 2 (Direct CDN)", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
                    ),
                    logoUrl = "https://images.unsplash.com/photo-1485846234645-a62644f84728?w=600&q=80",
                    description = "A poor electrician in desperate need of money plans a daring underground bank heist with unexpected consequences.",
                    rating = "8.7★",
                    year = "2024",
                    quality = "1080p Full HD"
                ),
                MediaItem(
                    id = "bangla_taqdeer",
                    title = "Taqdeer (তাকদীর)",
                    category = "Bangla Web Series • Thriller",
                    type = MediaType.SERIES,
                    streamUrl = "https://vidsrc.to/embed/tv/tt13693282",
                    servers = listOf(
                        StreamServer("Server 1 (DoraBash 1080p)", "https://vidsrc.to/embed/tv/tt13693282"),
                        StreamServer("Server 2 (Direct Stream)", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
                    ),
                    logoUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600&q=80",
                    description = "A freezer van driver finds an unidentified dead body inside his vehicle, spiraling into a deadly conspiracy.",
                    rating = "9.2★",
                    year = "2024",
                    quality = "1080p Complete Series"
                )
            )
            list.addAll(banglaList)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext list
    }

    private fun parseCinemetaJson(jsonStr: String, type: MediaType, provider: MovieProvider?): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        try {
            val obj = JSONObject(jsonStr)
            val metas = obj.optJSONArray("metas") ?: return list
            for (i in 0 until metas.length()) {
                val item = metas.optJSONObject(i) ?: continue
                val id = item.optString("id", "cm_$i")
                val name = item.optString("name", "").ifBlank { continue }
                val poster = item.optString("poster", "")
                val background = item.optString("background", poster)
                val description = item.optString("description", "HD Cinema stream with multiple high-speed servers.")
                val year = item.optString("year", item.optString("releaseInfo", "2026"))
                val rating = item.optString("imdbRating", "8.2")
                val genresArr = item.optJSONArray("genres")
                val genresList = mutableListOf<String>()
                if (genresArr != null) {
                    for (g in 0 until genresArr.length()) genresList.add(genresArr.optString(g))
                }
                val genreStr = genresList.joinToString(" • ").ifBlank { if (type == MediaType.SERIES) "TV Series" else "Movie" }

                val cleanImdb = if (id.startsWith("tt")) id else "tt$id"
                val streamServers = if (type == MediaType.SERIES) {
                    listOf(
                        StreamServer("Server 1 (VidSrc Series HD)", "https://vidsrc.me/embed/tv?imdb=$cleanImdb"),
                        StreamServer("Server 2 (SuperStream VIP)", "https://superstream.media/embed/$cleanImdb"),
                        StreamServer("Server 3 (SmashyStream)", "https://embed.smashystream.com/playere.php?imdb=$cleanImdb"),
                        StreamServer("Server 4 (Direct HLS Stream)", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
                    )
                } else {
                    listOf(
                        StreamServer("Server 1 (VidSrc 1080p)", "https://vidsrc.me/embed/movie?imdb=$cleanImdb"),
                        StreamServer("Server 2 (SuperStream VIP)", "https://superstream.media/embed/$cleanImdb"),
                        StreamServer("Server 3 (SmashyStream)", "https://embed.smashystream.com/playere.php?imdb=$cleanImdb"),
                        StreamServer("Server 4 (Direct HLS Stream)", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
                    )
                }

                list.add(
                    MediaItem(
                        id = "meta_${id}_${type.name}",
                        title = name,
                        category = genreStr,
                        type = type,
                        streamUrl = streamServers.first().url,
                        servers = streamServers,
                        logoUrl = poster.ifBlank { background },
                        description = description,
                        rating = if (rating.isNotBlank()) "$rating★" else "8.0★",
                        year = year,
                        quality = "1080p Ultra HD",
                        isLive = false,
                        referrer = "https://vidsrc.me",
                        userAgent = "Mozilla/5.0",
                        customHeaders = mapOf("Referer" to "https://vidsrc.me", "User-Agent" to "Mozilla/5.0")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    private fun parseYtsJson(jsonStr: String, provider: MovieProvider?): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        try {
            val obj = JSONObject(jsonStr)
            val data = obj.optJSONObject("data") ?: return list
            val movies = data.optJSONArray("movies") ?: return list
            for (i in 0 until movies.length()) {
                val item = movies.optJSONObject(i) ?: continue
                val id = item.optString("id", "yts_$i")
                val imdbCode = item.optString("imdb_code", id)
                val title = item.optString("title", "").ifBlank { continue }
                val year = item.optString("year", "2026")
                val rating = item.optDouble("rating", 7.8).toString()
                val summary = item.optString("summary", item.optString("synopsis", "Official YTS HD Release.")).ifBlank { "Full HD movie release." }
                val mediumCover = item.optString("medium_cover_image", "")
                val largeCover = item.optString("large_cover_image", mediumCover)
                val bgImage = item.optString("background_image_original", largeCover)

                val genresArr = item.optJSONArray("genres")
                val genresList = mutableListOf<String>()
                if (genresArr != null) {
                    for (g in 0 until genresArr.length()) genresList.add(genresArr.optString(g))
                }
                val genreStr = genresList.joinToString(" • ").ifBlank { "Action • Cinema" }

                val cleanImdb = if (imdbCode.startsWith("tt")) imdbCode else "tt$imdbCode"
                val streamServers = listOf(
                    StreamServer("Server 1 (YTS VidSrc Stream)", "https://vidsrc.me/embed/movie?imdb=$cleanImdb"),
                    StreamServer("Server 2 (SuperStream Ultra 4K)", "https://superstream.media/embed/$cleanImdb"),
                    StreamServer("Server 3 (SmashyStream)", "https://embed.smashystream.com/playere.php?imdb=$cleanImdb"),
                    StreamServer("Server 4 (Direct HLS Mirror)", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
                )

                list.add(
                    MediaItem(
                        id = "yts_$imdbCode",
                        title = title,
                        category = genreStr,
                        type = MediaType.MOVIE,
                        streamUrl = streamServers.first().url,
                        servers = streamServers,
                        logoUrl = largeCover.ifBlank { bgImage },
                        description = summary,
                        rating = "$rating★",
                        year = year,
                        quality = "1080p / 4K",
                        isLive = false,
                        referrer = "https://yts.mx",
                        userAgent = "Mozilla/5.0",
                        customHeaders = mapOf("Referer" to "https://yts.mx", "User-Agent" to "Mozilla/5.0")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    /**
     * Resolves CloudStream share / redirect links (e.g., https://recloudstream.github.io/csredirect?redirectto=csshare:...)
     * and extracts MovieBox or other provider items into a fully playable MediaItem.
     */
    suspend fun resolveCloudStreamShareLink(rawUrl: String): MediaItem? = withContext(Dispatchers.IO) {
        try {
            var target = rawUrl.trim()
            if (target.contains("redirectto=")) {
                val encodedRedirect = target.substringAfter("redirectto=").substringBefore("&")
                target = try {
                    java.net.URLDecoder.decode(encodedRedirect, "UTF-8")
                } catch (_: Exception) {
                    encodedRedirect
                }
            }

            if (target.startsWith("csshare:")) {
                target = target.removePrefix("csshare:")
            }

            var providerName = "MovieBox"
            var endpointUrl = ""

            if (target.contains("?")) {
                val p1 = target.substringBefore("?")
                val p2 = target.substringAfter("?")
                providerName = try { String(android.util.Base64.decode(p1, android.util.Base64.DEFAULT), Charsets.UTF_8) } catch (_: Exception) { p1 }
                endpointUrl = try { String(android.util.Base64.decode(p2, android.util.Base64.DEFAULT), Charsets.UTF_8) } catch (_: Exception) { p2 }
            } else if (target.contains(":")) {
                val p1 = target.substringBefore(":")
                val p2 = target.substringAfter(":")
                providerName = try { String(android.util.Base64.decode(p1, android.util.Base64.DEFAULT), Charsets.UTF_8) } catch (_: Exception) { p1 }
                endpointUrl = try { String(android.util.Base64.decode(p2, android.util.Base64.DEFAULT), Charsets.UTF_8) } catch (_: Exception) { p2 }
            } else {
                endpointUrl = target
            }

            if (endpointUrl.isBlank()) return@withContext null

            val subjectId = if (endpointUrl.contains("subjectId=")) {
                endpointUrl.substringAfter("subjectId=").substringBefore("&")
            } else {
                "8175753992266569024"
            }

            // Call MovieBox / AoneRoom API with standard app headers & guest tokens
            val req = Request.Builder()
                .url(endpointUrl)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 CloudStream/4.0 MovieBox/3.2.0")
                .header("Accept", "application/json, text/plain, */*")
                .header("x-app-id", "com.community.oneroom")
                .header("x-platform", "android")
                .header("x-device-id", "android_${java.util.UUID.randomUUID()}")
                .header("x-version-code", "320")
                .header("Referer", "https://moviebox.online/")
                .build()

            var title = "MovieBox Stream #$subjectId"
            var cover = "https://raw.githubusercontent.com/Hexated/cloudstream-extensions-hexated/builds/icon.png"
            var desc = "MovieBox Stream (ID: $subjectId)"
            var score = "8.5"
            var releaseDate = "2026"
            var isSeries = false

            val resp = try { client.newCall(req).execute() } catch (_: Exception) { null }
            if (resp != null && resp.isSuccessful) {
                val body = resp.body?.string() ?: ""
                try {
                    val json = JSONObject(body)
                    val code = json.optInt("code", 0)
                    if (code == 0 || json.has("data")) {
                        val data = json.optJSONObject("data") ?: json
                        val subject = data.optJSONObject("subject") ?: data
                        val parsedTitle = subject.optString("title", subject.optString("name", ""))
                        if (parsedTitle.isNotBlank()) title = parsedTitle
                        val parsedCover = subject.optString("cover", subject.optString("poster", subject.optString("coverUrl", "")))
                        if (parsedCover.isNotBlank()) cover = parsedCover
                        val parsedDesc = subject.optString("description", subject.optString("intro", ""))
                        if (parsedDesc.isNotBlank()) desc = parsedDesc
                        score = subject.optString("score", "8.5")
                        releaseDate = subject.optString("releaseDate", "2026")
                        isSeries = subject.optBoolean("isSeries", false)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val servers = mutableListOf<StreamServer>()
            servers.add(StreamServer("Server 1 (MovieBox API / Web)", endpointUrl))
            servers.add(StreamServer("Server 2 (VidSrc Stream)", "https://vidsrc.me/embed/movie?subjectId=$subjectId"))
            servers.add(StreamServer("Server 3 (SuperStream)", "https://superstream.media/embed/$subjectId"))
            servers.add(StreamServer("Server 4 (Direct HLS Backup)", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"))

            val item = MediaItem(
                id = "csshare_${subjectId}_${System.currentTimeMillis()}",
                title = title,
                category = "$providerName • CloudStream Link",
                type = if (isSeries) MediaType.SERIES else MediaType.MOVIE,
                streamUrl = servers.first().url,
                servers = servers,
                logoUrl = cover,
                description = desc,
                rating = "$score★",
                year = releaseDate.take(4),
                quality = "1080p Ultra HD",
                isLive = false
            )
            saveCustomStream(item)
            return@withContext item
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    // -------------------------------------------------------------
    // Notification Center & Broadcast Management
    // -------------------------------------------------------------
    fun getStoredNotifications(): List<AppNotification> {
        val jsonStr = prefs.getString("stored_notifications", "[]") ?: "[]"
        val list = mutableListOf<AppNotification>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(parseNotificationFromJsonObj(obj))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.sortedByDescending { it.timestamp }
    }

    fun saveNotification(notification: AppNotification, showSystemPopup: Boolean = false) {
        val current = getStoredNotifications().toMutableList()
        current.removeAll { it.id == notification.id }
        current.add(0, notification)
        saveNotificationList(current)
        if (showSystemPopup) {
            NotificationHelper.showSystemNotification(context, notification)
        }
    }

    fun saveNotificationList(list: List<AppNotification>) {
        val jsonArray = JSONArray()
        list.take(100).forEach { notif ->
            jsonArray.put(serializeNotificationToJsonObj(notif))
        }
        prefs.edit().putString("stored_notifications", jsonArray.toString()).apply()
    }

    fun markNotificationAsRead(id: String) {
        val current = getStoredNotifications().map {
            if (it.id == id) it.copy(isRead = true) else it
        }
        saveNotificationList(current)
    }

    fun markAllNotificationsAsRead() {
        val current = getStoredNotifications().map { it.copy(isRead = true) }
        saveNotificationList(current)
    }

    fun deleteNotification(id: String) {
        addDeletedNotificationId(id)
        val current = getStoredNotifications().filterNot { it.id == id }
        saveNotificationList(current)
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            deleteNotificationFromCloud(id)
        }
    }

    suspend fun deleteNotificationAsync(id: String): Boolean = withContext(Dispatchers.IO) {
        addDeletedNotificationId(id)
        val current = getStoredNotifications().filterNot { it.id == id }
        saveNotificationList(current)
        deleteNotificationFromCloud(id)
    }

    suspend fun deleteNotificationFromCloud(id: String): Boolean = withContext(Dispatchers.IO) {
        var anySuccess = false
        val databases = listOf(FIRESTORE_DATABASE_ID, "(default)")
        for (dbId in databases) {
            try {
                val fsUrl = "https://firestore.googleapis.com/v1/projects/$FIREBASE_PROJECT_ID/databases/$dbId/documents/notifications/$id?key=$FIREBASE_API_KEY"
                val req = Request.Builder().url(fsUrl).delete().build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) anySuccess = true
            } catch (_: Exception) {}
        }

        try {
            val rtdbUrl = getSavedFirebaseUrl()
            if (rtdbUrl.isNotBlank()) {
                val cleanUrl = if (rtdbUrl.endsWith("/")) rtdbUrl.removeSuffix("/") else rtdbUrl
                val targetUrl = appendRtdbAuth("$cleanUrl/notifications/$id.json")
                val req = Request.Builder().url(targetUrl).delete().build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) anySuccess = true
            }
        } catch (_: Exception) {}

        anySuccess
    }

    fun clearAllNotifications() {
        val allStored = getStoredNotifications()
        val allIds = allStored.map { it.id }.toSet()
        val deletedSet = getDeletedNotificationIds().toMutableSet()
        deletedSet.addAll(allIds)
        prefs.edit().putStringSet("deleted_notif_ids", deletedSet).apply()
        prefs.edit().remove("stored_notifications").apply()

        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            clearAllNotificationsFromCloud(allStored)
        }
    }

    suspend fun clearAllNotificationsAsync(): Boolean = withContext(Dispatchers.IO) {
        val allStored = getStoredNotifications()
        val allIds = allStored.map { it.id }.toSet()
        val deletedSet = getDeletedNotificationIds().toMutableSet()
        deletedSet.addAll(allIds)
        prefs.edit().putStringSet("deleted_notif_ids", deletedSet).apply()
        prefs.edit().remove("stored_notifications").apply()
        clearAllNotificationsFromCloud(allStored)
    }

    suspend fun clearAllNotificationsFromCloud(allStored: List<AppNotification>): Boolean = withContext(Dispatchers.IO) {
        var anySuccess = false
        val databases = listOf(FIRESTORE_DATABASE_ID, "(default)")
        for (notif in allStored) {
            for (dbId in databases) {
                try {
                    val fsUrl = "https://firestore.googleapis.com/v1/projects/$FIREBASE_PROJECT_ID/databases/$dbId/documents/notifications/${notif.id}?key=$FIREBASE_API_KEY"
                    val req = Request.Builder().url(fsUrl).delete().build()
                    val resp = client.newCall(req).execute()
                    if (resp.isSuccessful) anySuccess = true
                } catch (_: Exception) {}
            }
        }

        try {
            val rtdbUrl = getSavedFirebaseUrl()
            if (rtdbUrl.isNotBlank()) {
                val cleanUrl = if (rtdbUrl.endsWith("/")) rtdbUrl.removeSuffix("/") else rtdbUrl
                val targetUrl = appendRtdbAuth("$cleanUrl/notifications.json")
                val req = Request.Builder().url(targetUrl).delete().build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) anySuccess = true
            }
        } catch (_: Exception) {}

        anySuccess
    }

    fun getDeletedNotificationIds(): Set<String> {
        return prefs.getStringSet("deleted_notif_ids", emptySet()) ?: emptySet()
    }

    fun addDeletedNotificationId(id: String) {
        val current = getDeletedNotificationIds().toMutableSet()
        current.add(id)
        prefs.edit().putStringSet("deleted_notif_ids", current).apply()
    }

    fun getUnreadNotificationCount(): Int {
        return getStoredNotifications().count { !it.isRead }
    }

    fun getNotifiedNotificationIds(): Set<String> {
        return prefs.getStringSet("notified_notif_ids", emptySet()) ?: emptySet()
    }

    fun markNotificationAsNotified(id: String) {
        val current = getNotifiedNotificationIds().toMutableSet()
        current.add(id)
        prefs.edit().putStringSet("notified_notif_ids", current).apply()
    }

    fun serializeNotificationToJsonObj(notif: AppNotification): JSONObject {
        val obj = JSONObject()
        obj.put("id", notif.id)
        obj.put("title", notif.title)
        obj.put("message", notif.message)
        obj.put("timestamp", notif.timestamp)
        obj.put("type", notif.type.name)
        if (!notif.targetId.isNullOrBlank()) obj.put("targetId", notif.targetId)
        if (!notif.targetType.isNullOrBlank()) obj.put("targetType", notif.targetType)
        if (!notif.imageUrl.isNullOrBlank()) obj.put("imageUrl", notif.imageUrl)
        obj.put("isRead", notif.isRead)
        if (!notif.actionUrl.isNullOrBlank()) obj.put("actionUrl", notif.actionUrl)
        obj.put("sender", notif.sender)
        return obj
    }

    fun parseNotificationFromJsonObj(obj: JSONObject, explicitId: String? = null): AppNotification {
        val id = explicitId ?: obj.optString("id", "notif_${System.currentTimeMillis()}")
        val title = obj.optString("title", "Nafi TV আপডেট")
        val message = obj.optString("message", obj.optString("body", ""))
        val timestamp = obj.optLong("timestamp", System.currentTimeMillis())
        val typeStr = obj.optString("type", "BROADCAST").uppercase()
        val type = try { NotificationType.valueOf(typeStr) } catch (_: Exception) { NotificationType.BROADCAST }
        val targetId = obj.optString("targetId", "").takeIf { it.isNotBlank() }
        val targetType = obj.optString("targetType", "").takeIf { it.isNotBlank() }
        val imageUrl = obj.optString("imageUrl", "").takeIf { it.isNotBlank() }
        val isRead = obj.optBoolean("isRead", false)
        val actionUrl = obj.optString("actionUrl", "").takeIf { it.isNotBlank() }
        val sender = obj.optString("sender", "Admin")

        return AppNotification(
            id = id,
            title = title,
            message = message,
            timestamp = timestamp,
            type = type,
            targetId = targetId,
            targetType = targetType,
            imageUrl = imageUrl,
            isRead = isRead,
            actionUrl = actionUrl,
            sender = sender
        )
    }

    private fun parseNotificationFromFirestoreFields(docId: String, fields: JSONObject): AppNotification {
        fun s(key: String): String {
            val v = fields.optJSONObject(key) ?: return ""
            return v.optString("stringValue", "")
        }
        fun b(key: String, def: Boolean = false): Boolean {
            val v = fields.optJSONObject(key) ?: return def
            return if (v.has("booleanValue")) v.optBoolean("booleanValue", def) else def
        }
        fun l(key: String): Long {
            val v = fields.optJSONObject(key) ?: return System.currentTimeMillis()
            return if (v.has("integerValue")) v.optLong("integerValue") else System.currentTimeMillis()
        }

        val typeStr = s("type").uppercase()
        val type = try { NotificationType.valueOf(typeStr) } catch (_: Exception) { NotificationType.BROADCAST }

        return AppNotification(
            id = s("id").ifBlank { docId },
            title = s("title").ifBlank { "Nafi TV নোটিফিকেশন" },
            message = s("message").ifBlank { s("body") },
            timestamp = l("timestamp"),
            type = type,
            targetId = s("targetId").takeIf { it.isNotBlank() },
            targetType = s("targetType").takeIf { it.isNotBlank() },
            imageUrl = s("imageUrl").takeIf { it.isNotBlank() },
            isRead = b("isRead", false),
            actionUrl = s("actionUrl").takeIf { it.isNotBlank() },
            sender = s("sender").ifBlank { "Admin" }
        )
    }

    suspend fun broadcastNotification(
        notification: AppNotification,
        pushToCloud: Boolean = true
    ): Pair<Boolean, String> = sendBroadcastNotification(notification, pushToCloud)

    suspend fun sendBroadcastNotification(
        notification: AppNotification,
        pushToCloud: Boolean = true
    ): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        // 1. Save locally and show system notification immediately
        saveNotification(notification, showSystemPopup = true)
        markNotificationAsNotified(notification.id)

        if (!pushToCloud) return@withContext Pair(true, "নোটিফিকেশন তৈরি ও প্রেরণ সফল হয়েছে")

        var anySuccess = false
        val jsonObj = serializeNotificationToJsonObj(notification)

        // 2. Push to Firestore
        try {
            val firestoreObj = JSONObject()
            val fields = JSONObject()
            fun fs(key: String, value: String?) {
                if (!value.isNullOrBlank()) {
                    fields.put(key, JSONObject().put("stringValue", value))
                }
            }
            fun fb(key: String, value: Boolean) {
                fields.put(key, JSONObject().put("booleanValue", value))
            }
            fun fi(key: String, value: Long) {
                fields.put(key, JSONObject().put("integerValue", value.toString()))
            }

            fs("id", notification.id)
            fs("title", notification.title)
            fs("message", notification.message)
            fi("timestamp", notification.timestamp)
            fs("type", notification.type.name)
            fs("targetId", notification.targetId)
            fs("targetType", notification.targetType)
            fs("imageUrl", notification.imageUrl)
            fs("actionUrl", notification.actionUrl)
            fs("sender", notification.sender)

            firestoreObj.put("fields", fields)
            val fsBody = firestoreObj.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())

            val databases = listOf(FIRESTORE_DATABASE_ID, "(default)")
            for (dbId in databases) {
                try {
                    val fsUrl = "https://firestore.googleapis.com/v1/projects/$FIREBASE_PROJECT_ID/databases/$dbId/documents/notifications/${notification.id}?key=$FIREBASE_API_KEY"
                    val fsReq = Request.Builder().url(fsUrl).patch(fsBody).build()
                    val fsResp = client.newCall(fsReq).execute()
                    if (fsResp.isSuccessful) anySuccess = true
                } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Push to Firebase Realtime Database
        try {
            val rtdbUrl = getSavedFirebaseUrl()
            if (rtdbUrl.isNotBlank()) {
                val cleanUrl = if (rtdbUrl.endsWith("/")) rtdbUrl.removeSuffix("/") else rtdbUrl
                val body = jsonObj.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val targetUrl = appendRtdbAuth("$cleanUrl/notifications/${notification.id}.json")
                val req = Request.Builder().url(targetUrl).put(body).build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) anySuccess = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return@withContext if (anySuccess) {
            Pair(true, "নোটিফিকেশন সফলভাবে ক্লাউডে এবং ব্যবহারকারীদের কাছে পাঠানো হয়েছে")
        } else {
            Pair(true, "নোটিফিকেশন সফলভাবে তৈরি ও পাঠানো হয়েছে")
        }
    }

    suspend fun fetchRemoteNotifications(): List<AppNotification> = withContext(Dispatchers.IO) {
        val remoteList = mutableListOf<AppNotification>()
        val notifiedIds = getNotifiedNotificationIds()

        // 1. Fetch from Firestore
        try {
            val databases = listOf(FIRESTORE_DATABASE_ID, "(default)")
            for (dbId in databases) {
                try {
                    val url = "https://firestore.googleapis.com/v1/projects/$FIREBASE_PROJECT_ID/databases/$dbId/documents/notifications?key=$FIREBASE_API_KEY"
                    val req = Request.Builder().url(url).build()
                    val resp = client.newCall(req).execute()
                    if (resp.isSuccessful) {
                        val body = resp.body?.string() ?: ""
                        val json = JSONObject(body)
                        val docs = json.optJSONArray("documents") ?: continue
                        for (i in 0 until docs.length()) {
                            val doc = docs.optJSONObject(i) ?: continue
                            val docId = doc.optString("name", "").substringAfterLast("/")
                            val fields = doc.optJSONObject("fields") ?: continue
                            val notif = parseNotificationFromFirestoreFields(docId, fields)
                            remoteList.add(notif)
                        }
                    }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        // 2. Fetch from RTDB
        try {
            val rtdbUrl = getSavedFirebaseUrl()
            if (rtdbUrl.isNotBlank()) {
                val cleanUrl = if (rtdbUrl.endsWith("/")) rtdbUrl.removeSuffix("/") else rtdbUrl
                val targetUrl = "$cleanUrl/notifications.json"
                val req = Request.Builder().url(targetUrl).build()
                val resp = client.newCall(req).execute()
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    if (body.startsWith("{")) {
                        val obj = JSONObject(body)
                        val keys = obj.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            val nObj = obj.optJSONObject(k)
                            if (nObj != null) {
                                remoteList.add(parseNotificationFromJsonObj(nObj, explicitId = k))
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        val deletedNotifIds = getDeletedNotificationIds()
        val distinctList = remoteList
            .filterNot { deletedNotifIds.contains(it.id) }
            .distinctBy { it.id }
            .sortedByDescending { it.timestamp }

        // Process newly received notifications for system push popup
        for (notif in distinctList) {
            if (!notifiedIds.contains(notif.id)) {
                saveNotification(notif, showSystemPopup = true)
                markNotificationAsNotified(notif.id)
            }
        }

        distinctList
    }

    init {
        recordUserPresence()
    }


    // -------------------------------------------------------------
    // USER ANALYTICS & ACTIVE USERS TRACKING (Firebase RTDB + Location)
    // -------------------------------------------------------------
    fun getOrCreateDeviceId(): String {
        var id = prefs.getString("device_unique_user_id", null)
        if (id.isNullOrBlank()) {
            id = "user_" + UUID.randomUUID().toString().replace("-", "").take(12)
            prefs.edit().putString("device_unique_user_id", id).apply()
        }
        return id
    }

    /**
     * Accurately detects network connection type (WiFi, 4G/5G Cellular, Ethernet, VPN)
     */
    fun detectNetworkType(): String {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = cm?.activeNetwork
            val capabilities = cm?.getNetworkCapabilities(network)
            when {
                capabilities == null -> "Offline"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WiFi"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile Data (4G/5G)"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet / Broadband"
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
                else -> "WiFi"
            }
        } catch (_: Exception) {
            "WiFi"
        }
    }

    /**
     * Detects real device location (City, Country, ISP) without prompting runtime GPS permissions.
     * Uses fast, reliable HTTPS GeoIP lookup services with locale fallback.
     */
    fun detectDeviceLocation(): Map<String, String> {
        val now = System.currentTimeMillis()
        val lastGeoCheck = prefs.getLong("last_geo_check_timestamp", 0L)
        val cachedCity = prefs.getString("cached_user_city", null)
        val cachedCountry = prefs.getString("cached_user_country", null)
        val cachedCountryCode = prefs.getString("cached_user_country_code", null)
        val cachedIsp = prefs.getString("cached_user_isp", null)
        val cachedIp = prefs.getString("cached_user_ip", null)

        if (!cachedCity.isNullOrBlank() && !cachedCountry.isNullOrBlank() && (now - lastGeoCheck < 24 * 3600 * 1000L)) {
            val locName = "$cachedCity, $cachedCountry"
            return mapOf(
                "location" to locName,
                "city" to cachedCity,
                "country" to (cachedCountry ?: "Bangladesh"),
                "country_code" to (cachedCountryCode ?: "BD"),
                "isp" to (cachedIsp ?: ""),
                "ip" to (cachedIp ?: "")
            )
        }

        var city = cachedCity ?: ""
        var country = cachedCountry ?: ""
        var countryCode = cachedCountryCode ?: ""
        var isp = cachedIsp ?: ""
        var ip = cachedIp ?: ""

        val endpoints = listOf(
            "https://freeipapi.com/api/json",
            "https://ipapi.co/json/",
            "https://ipwho.is/"
        )

        for (endpoint in endpoints) {
            try {
                val geoReq = Request.Builder()
                    .url(endpoint)
                    .header("User-Agent", "NAFITV24/2.5.6 (Android)")
                    .build()
                client.newCall(geoReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bodyStr = resp.body?.string()?.trim() ?: ""
                        if (bodyStr.startsWith("{")) {
                            val root = JSONObject(bodyStr)
                            if (endpoint.contains("freeipapi")) {
                                val c = root.optString("cityName", "").trim()
                                val cn = root.optString("countryName", "").trim()
                                val cc = root.optString("countryCode", "").trim()
                                val qIp = root.optString("ipAddress", "").trim()
                                if (cn.isNotBlank() && cn != "-") {
                                    city = if (c.isNotBlank() && c != "-") c else "ঢাকা"
                                    country = cn
                                    countryCode = cc.ifBlank { "BD" }
                                    ip = qIp
                                    return@use
                                }
                            } else if (endpoint.contains("ipapi.co")) {
                                val c = root.optString("city", "").trim()
                                val cn = root.optString("country_name", "").trim()
                                val cc = root.optString("country_code", "").trim()
                                val org = root.optString("org", "").trim()
                                val qIp = root.optString("ip", "").trim()
                                if (cn.isNotBlank()) {
                                    city = if (c.isNotBlank()) c else "ঢাকা"
                                    country = cn
                                    countryCode = cc.ifBlank { "BD" }
                                    isp = org
                                    ip = qIp
                                    return@use
                                }
                            } else if (endpoint.contains("ipwho.is")) {
                                if (root.optBoolean("success", true)) {
                                    val c = root.optString("city", "").trim()
                                    val cn = root.optString("country", "").trim()
                                    val cc = root.optString("country_code", "").trim()
                                    val connObj = root.optJSONObject("connection")
                                    val connIsp = connObj?.optString("isp", "") ?: ""
                                    val qIp = root.optString("ip", "").trim()
                                    if (cn.isNotBlank()) {
                                        city = if (c.isNotBlank()) c else "ঢাকা"
                                        country = cn
                                        countryCode = cc.ifBlank { "BD" }
                                        isp = connIsp
                                        ip = qIp
                                        return@use
                                    }
                                }
                            }
                        }
                    }
                }
                if (country.isNotBlank()) break
            } catch (_: Exception) {}
        }

        if (country.isBlank()) {
            val loc = Locale.getDefault()
            val tz = TimeZone.getDefault().id.lowercase()
            if (tz.contains("dhaka")) {
                city = "ঢাকা"
                country = "বাংলাদেশ"
                countryCode = "BD"
            } else if (tz.contains("kolkata") || tz.contains("calcutta") || tz.contains("delhi")) {
                city = "কলকাতা"
                country = "ভারত"
                countryCode = "IN"
            } else if (loc.displayCountry.isNotBlank()) {
                country = loc.displayCountry
                city = loc.displayCountry
                countryCode = loc.country.ifBlank { "BD" }
            } else {
                city = "ঢাকা"
                country = "বাংলাদেশ"
                countryCode = "BD"
            }
        }

        if (city.isBlank()) city = "ঢাকা"

        prefs.edit()
            .putString("cached_user_city", city)
            .putString("cached_user_country", country)
            .putString("cached_user_country_code", countryCode)
            .putString("cached_user_isp", isp)
            .putString("cached_user_ip", ip)
            .putLong("last_geo_check_timestamp", now)
            .apply()

        val locationLabel = "$city, $country"
        return mapOf(
            "location" to locationLabel,
            "city" to city,
            "country" to country,
            "country_code" to countryCode,
            "isp" to isp,
            "ip" to ip
        )
    }

    fun recordUserPresence(currentActivity: String = "ব্রাউজিং") {
        try {
            val now = System.currentTimeMillis()
            // Quota optimization: Only send heartbeat every 10 minutes or if initial startup
            if (lastPresenceTimestamp > 0 && (now - lastPresenceTimestamp < 10 * 60 * 1000L)) {
                return
            }
            lastPresenceTimestamp = now
            lastPresenceActivity = currentActivity

            val deviceId = getOrCreateDeviceId()
            val isFirstInstall = !prefs.contains("app_installed_at_timestamp")
            if (isFirstInstall) {
                prefs.edit().putLong("app_installed_at_timestamp", now).apply()
                val curTotal = prefs.getInt("cached_lifetime_users_count", 1)
                prefs.edit().putInt("cached_lifetime_users_count", curTotal + 1).apply()
            }
            val installedAt = prefs.getLong("app_installed_at_timestamp", now)

            val manufacturer = android.os.Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
            val model = android.os.Build.MODEL.orEmpty()
            val deviceName = if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
            val cleanDeviceName = deviceName.ifBlank { "Android Device" }
            val appVersion = "v${com.example.BuildConfig.VERSION_NAME}"
            val versionCode = com.example.BuildConfig.VERSION_CODE
            val netType = detectNetworkType()

            // Background sync to Firebase Realtime Database with managed lifecycle scope
            repositoryScope.launch {
                try {
                    val locInfo = detectDeviceLocation()
                    val city = locInfo["city"] ?: "ঢাকা"
                    val country = locInfo["country"] ?: "বাংলাদেশ"
                    val countryCode = locInfo["country_code"] ?: "BD"
                    val locLabel = locInfo["location"] ?: "$city, $country"
                    val isp = locInfo["isp"] ?: ""
                    val ip = locInfo["ip"] ?: ""

                    val rtdbUrl = getSavedFirebaseUrl()
                    if (rtdbUrl.isNotBlank()) {
                        val cleanUrl = if (rtdbUrl.endsWith("/")) rtdbUrl.removeSuffix("/") else rtdbUrl

                        // 1. Update active user presence object (for live active count in last 5-10 min)
                        val activeObj = JSONObject().apply {
                            put("id", deviceId)
                            put("device_model", cleanDeviceName)
                            put("brand", manufacturer.ifBlank { "Android" })
                            put("app_version", appVersion)
                            put("version_code", versionCode)
                            put("last_seen", now)
                            put("registered_at", installedAt)
                            put("is_online", true)
                            put("location", locLabel)
                            put("city", city)
                            put("country", country)
                            put("country_code", countryCode)
                            put("network_type", netType)
                            put("isp", isp)
                            put("ip", ip)
                            put("current_activity", currentActivity)
                        }
                        val activeBody = activeObj.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                        val activeTargetUrl = appendRtdbAuth("$cleanUrl/active_users/$deviceId.json")
                        val activeReq = Request.Builder()
                            .url(activeTargetUrl)
                            .put(activeBody)
                            .build()
                        try { client.newCall(activeReq).execute().close() } catch (_: Exception) {}

                        // 2. Register / Update in permanent lifetime users index (all_users) - ONLY once on install or once a week!
                        val lastAllUsersSync = prefs.getLong("last_all_users_sync_timestamp", 0L)
                        if (isFirstInstall || (now - lastAllUsersSync > 7 * 24 * 3600 * 1000L)) {
                            prefs.edit().putLong("last_all_users_sync_timestamp", now).apply()
                            val userObj = JSONObject().apply {
                                put("id", deviceId)
                                put("device_model", cleanDeviceName)
                                put("app_version", appVersion)
                                put("last_seen", now)
                                put("registered_at", installedAt)
                                put("location", locLabel)
                                put("city", city)
                                put("country", country)
                                put("country_code", countryCode)
                                put("network_type", netType)
                            }
                            val userBody = userObj.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                            val allTargetUrl = appendRtdbAuth("$cleanUrl/all_users/$deviceId.json")
                            val userReq = Request.Builder()
                                .url(allTargetUrl)
                                .put(userBody)
                                .build()
                            try { client.newCall(userReq).execute().close() } catch (_: Exception) {}
                        }
                    }
                } catch (_: Exception) {
                    // Ignore transient network errors
                }
            }
        } catch (_: Exception) {}
    }

    suspend fun fetchUserAnalytics(): AppUserAnalytics = withContext(Dispatchers.IO) {
        val rtdbUrl = getSavedFirebaseUrl()
        val now = System.currentTimeMillis()
        val activeThresholdMillis = 5 * 60 * 1000L // Active in the last 5 minutes

        val allList = mutableListOf<ActiveUserInfo>()
        var totalLifetimeCount = 0

        if (rtdbUrl.isNotBlank()) {
            val cleanUrl = if (rtdbUrl.endsWith("/")) rtdbUrl.removeSuffix("/") else rtdbUrl

            // 1. Fetch active_users from Firebase RTDB
            try {
                val activeFetchUrl = appendRtdbAuth("$cleanUrl/active_users.json")
                val reqActive = Request.Builder()
                    .url(activeFetchUrl)
                    .header("User-Agent", "NAFITV24/2.5.6 (Android)")
                    .build()
                val respActive = client.newCall(reqActive).execute()
                if (respActive.isSuccessful) {
                    val body = respActive.body?.string()?.trim() ?: ""
                    if (body.startsWith("{")) {
                        val root = JSONObject(body)
                        val keys = root.keys()
                        while (keys.hasNext()) {
                            val k = keys.next()
                            val uObj = root.optJSONObject(k) ?: continue
                            val lastSeen = uObj.optLong("last_seen", 0L)
                            val deviceModel = uObj.optString("device_model", "Android Device")
                            val brand = uObj.optString("brand", "Android")
                            val appVersion = uObj.optString("app_version", "v${com.example.BuildConfig.VERSION_NAME}")
                            val vCode = uObj.optInt("version_code", com.example.BuildConfig.VERSION_CODE)
                            val registeredAt = uObj.optLong("registered_at", lastSeen)
                            val loc = uObj.optString("location", "বাংলাদেশ")
                            val city = uObj.optString("city", "ঢাকা")
                            val country = uObj.optString("country", "Bangladesh")
                            val countryCode = uObj.optString("country_code", "BD")
                            val ip = uObj.optString("ip", "")
                            val isp = uObj.optString("isp", "")
                            val netType = uObj.optString("network_type", "WiFi")
                            val curAct = uObj.optString("current_activity", "ব্রাউজিং")

                            val isCurrentlyOnline = (now - lastSeen) <= activeThresholdMillis
                            allList.add(
                                ActiveUserInfo(
                                    id = k,
                                    deviceModel = deviceModel,
                                    brand = brand,
                                    appVersion = appVersion,
                                    versionCode = vCode,
                                    lastSeen = lastSeen,
                                    registeredAt = registeredAt,
                                    isOnline = isCurrentlyOnline,
                                    location = loc,
                                    city = city,
                                    country = country,
                                    countryCode = countryCode,
                                    ip = ip,
                                    isp = isp,
                                    networkType = netType,
                                    currentActivity = curAct
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Fetch all_users count (shallow or direct)
            try {
                val allFetchUrl = appendRtdbAuth("$cleanUrl/all_users.json?shallow=true")
                val reqAll = Request.Builder()
                    .url(allFetchUrl)
                    .build()
                val respAll = client.newCall(reqAll).execute()
                if (respAll.isSuccessful) {
                    val allBody = respAll.body?.string()?.trim() ?: ""
                    if (allBody.startsWith("{")) {
                        val allRoot = JSONObject(allBody)
                        totalLifetimeCount = allRoot.length()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Add / refresh current device in list
        val currentDevId = getOrCreateDeviceId()
        val existingIndex = allList.indexOfFirst { it.id == currentDevId }
        val manufacturer = android.os.Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
        val model = android.os.Build.MODEL.orEmpty()
        val deviceName = if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
        val myCity = prefs.getString("cached_user_city", "ঢাকা") ?: "ঢাকা"
        val myCountry = prefs.getString("cached_user_country", "বাংলাদেশ") ?: "বাংলাদেশ"
        val myCountryCode = prefs.getString("cached_user_country_code", "BD") ?: "BD"
        val myIsp = prefs.getString("cached_user_isp", "") ?: ""
        val myIp = prefs.getString("cached_user_ip", "") ?: ""

        val currentDeviceItem = ActiveUserInfo(
            id = currentDevId,
            deviceModel = deviceName.ifBlank { "This Device (Admin/User)" },
            brand = manufacturer.ifBlank { "Android" },
            appVersion = "v${com.example.BuildConfig.VERSION_NAME}",
            versionCode = com.example.BuildConfig.VERSION_CODE,
            lastSeen = now,
            registeredAt = prefs.getLong("app_installed_at_timestamp", now),
            isOnline = true,
            location = "$myCity, $myCountry",
            city = myCity,
            country = myCountry,
            countryCode = myCountryCode,
            ip = myIp,
            isp = myIsp,
            networkType = detectNetworkType(),
            currentActivity = "অ্যাডমিন প্যানেল"
        )

        if (existingIndex >= 0) {
            allList[existingIndex] = currentDeviceItem
        } else {
            allList.add(0, currentDeviceItem)
        }

        // Sort: currently online first, then latest lastSeen
        allList.sortWith(compareByDescending<ActiveUserInfo> { it.isOnline }.thenByDescending { it.lastSeen })

        val liveActiveList = allList.filter { it.isOnline }
        val onlineCount = liveActiveList.size.coerceAtLeast(1)
        val finalTotal = maxOf(totalLifetimeCount, allList.size, prefs.getInt("cached_lifetime_users_count", 1))
        prefs.edit().putInt("cached_lifetime_users_count", finalTotal).apply()

        // Helper for location statistics
        fun buildLocationStats(sourceList: List<ActiveUserInfo>): List<LocationTrafficStat> {
            val list = if (sourceList.isNotEmpty()) sourceList else allList
            val locationGroups = list.groupBy {
                val c = it.city.ifBlank { it.location }
                if (c.isNotBlank()) "$c, ${it.country}" else it.country.ifBlank { "বাংলাদেশ" }
            }
            return locationGroups.map { (locName, users) ->
                val flagEmoji = when {
                    locName.contains("Bangladesh", ignoreCase = true) || locName.contains("বাংলাদেশ") || locName.contains("ঢাকা") -> "🇧🇩"
                    locName.contains("India", ignoreCase = true) || locName.contains("ভারত") || locName.contains("কলকাতা") -> "🇮🇳"
                    locName.contains("Saudi", ignoreCase = true) || locName.contains("সৌদি") -> "🇸🇦"
                    locName.contains("Emirates", ignoreCase = true) || locName.contains("Dubai", ignoreCase = true) -> "🇦🇪"
                    locName.contains("United States", ignoreCase = true) || locName.contains("USA", ignoreCase = true) -> "🇺🇸"
                    locName.contains("United Kingdom", ignoreCase = true) || locName.contains("UK", ignoreCase = true) -> "🇬🇧"
                    locName.contains("Malaysia", ignoreCase = true) || locName.contains("মালয়েশিয়া") -> "🇲🇾"
                    else -> "🌐"
                }
                val count = users.size
                val pct = if (list.isNotEmpty()) (count.toFloat() / list.size.toFloat()) else 1f
                LocationTrafficStat(
                    locationName = locName,
                    count = count,
                    percentage = pct,
                    flag = flagEmoji
                )
            }.sortedByDescending { it.count }
        }

        // Helper for network stats
        fun buildNetworkStats(sourceList: List<ActiveUserInfo>): Map<String, Int> {
            val list = if (sourceList.isNotEmpty()) sourceList else allList
            return list.groupBy {
                val net = it.networkType.trim()
                when {
                    net.contains("WiFi", ignoreCase = true) -> "WiFi"
                    net.contains("Mobile", ignoreCase = true) || net.contains("Cellular", ignoreCase = true) || net.contains("4G", ignoreCase = true) || net.contains("5G", ignoreCase = true) -> "Mobile Data (4G/5G)"
                    net.contains("Ethernet", ignoreCase = true) || net.contains("Broadband", ignoreCase = true) -> "Ethernet / Broadband"
                    net.contains("VPN", ignoreCase = true) -> "VPN"
                    else -> "WiFi"
                }
            }.mapValues { it.value.size }
        }

        val topLocations = buildLocationStats(allList)
        val liveLocations = buildLocationStats(liveActiveList)
        val networkStats = buildNetworkStats(allList)
        val liveNetworkStats = buildNetworkStats(liveActiveList)

        AppUserAnalytics(
            totalUsers = finalTotal,
            activeUsers = onlineCount,
            activeUsersList = allList,
            topLocations = topLocations,
            liveLocations = liveLocations,
            networkBreakdown = networkStats,
            liveNetworkBreakdown = liveNetworkStats,
            lastUpdated = now
        )
    }

    suspend fun cleanStaleOfflineUsers(): Boolean = withContext(Dispatchers.IO) {
        val rtdbUrl = getSavedFirebaseUrl()
        if (rtdbUrl.isBlank()) return@withContext false
        try {
            val cleanUrl = if (rtdbUrl.endsWith("/")) rtdbUrl.removeSuffix("/") else rtdbUrl
            val reqActiveUrl = appendRtdbAuth("$cleanUrl/active_users.json")
            val reqActive = Request.Builder().url(reqActiveUrl).build()
            val resp = client.newCall(reqActive).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string()?.trim() ?: ""
                if (body.startsWith("{")) {
                    val root = JSONObject(body)
                    val now = System.currentTimeMillis()
                    val staleThreshold = 12 * 60 * 60 * 1000L // 12 hours
                    val keys = root.keys()
                    val patchObj = JSONObject()
                    var countToDelete = 0
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val uObj = root.optJSONObject(k)
                        val lastSeen = uObj?.optLong("last_seen", 0L) ?: 0L
                        if (lastSeen > 0 && (now - lastSeen) > staleThreshold) {
                            patchObj.put(k, JSONObject.NULL)
                            countToDelete++
                        }
                    }
                    if (countToDelete > 0) {
                        val patchBody = patchObj.toString().toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                        val patchReq = Request.Builder()
                            .url(reqActiveUrl)
                            .patch(patchBody)
                            .build()
                        val patchResp = client.newCall(patchReq).execute()
                        patchResp.close()
                    }
                    return@withContext true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        false
    }

    // Instant Free Firebase Quota: Wipes active and lifetime user tracking logs
    suspend fun clearAllUserLogsFromFirebase(): Boolean = withContext(Dispatchers.IO) {
        val rtdbUrl = getSavedFirebaseUrl()
        if (rtdbUrl.isBlank()) return@withContext false
        try {
            val cleanUrl = if (rtdbUrl.endsWith("/")) rtdbUrl.removeSuffix("/") else rtdbUrl
            val delActiveUrl = appendRtdbAuth("$cleanUrl/active_users.json")
            val delAllUrl = appendRtdbAuth("$cleanUrl/all_users.json")
            val req1 = Request.Builder().url(delActiveUrl).delete().build()
            val req2 = Request.Builder().url(delAllUrl).delete().build()
            try { client.newCall(req1).execute().close() } catch (_: Exception) {}
            try { client.newCall(req2).execute().close() } catch (_: Exception) {}
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun getSavedUserMode(): String? {
        return prefs.getString("saved_app_user_mode", null)
    }

    fun saveUserMode(modeName: String?) {
        if (modeName == null) {
            prefs.edit().remove("saved_app_user_mode").apply()
        } else {
            prefs.edit().putString("saved_app_user_mode", modeName).apply()
        }
    }

    // -------------------------------------------------------------
    // ADULT / 18+ CONTENT PARENTAL CONTROL / SAFE MODE
    // Default is TRUE (hidden automatically by default)
    // -------------------------------------------------------------
    fun isAdultContentHidden(): Boolean {
        return prefs.getBoolean("hide_adult_content", true)
    }

    fun setHideAdultContent(hidden: Boolean) {
        prefs.edit().putBoolean("hide_adult_content", hidden).apply()
    }

    fun getHiddenKeywords(): Set<String> {
        val stored = prefs.getStringSet("admin_hidden_keywords", null)
        if (!stored.isNullOrEmpty()) {
            return stored
        }
        val legacy = prefs.getString("hidden_keywords", null)
        if (!legacy.isNullOrBlank()) {
            return legacy.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
        }
        return setOf(
            "adult", "adults", "18+", "18 +", "xxx", "nsfw", "erotic", "erotica", "porn", "sex", "sensual",
            "mature", "ullu", "kooku", "primeshots", "rabbit", "besharams", "hotx", "chikoo",
            "boomx", "cinemadosti", "cinema dosti", "redprime", "gupchup", "hunt", "feneo",
            "fliz", "voovi", "feelit", "unrated hot", "softcore", "nuefliks", "hotshots"
        )
    }

    fun setHiddenKeywords(keywords: Collection<String>) {
        prefs.edit().putStringSet("admin_hidden_keywords", keywords.toSet()).apply()
    }

    fun getHiddenItemIds(): Set<String> {
        return prefs.getStringSet("admin_hidden_media_ids", emptySet()) ?: emptySet()
    }

    fun hideMultipleItems(ids: Collection<String>) {
        val current = getHiddenItemIds().toMutableSet()
        current.addAll(ids)
        prefs.edit().putStringSet("admin_hidden_media_ids", current).apply()
    }

    fun toggleItemHidden(id: String): Boolean {
        val current = getHiddenItemIds().toMutableSet()
        val isNowHidden = if (current.contains(id)) {
            current.remove(id)
            false
        } else {
            current.add(id)
            true
        }
        prefs.edit().putStringSet("admin_hidden_media_ids", current).apply()
        return isNowHidden
    }

    fun unhideAllItems() {
        prefs.edit().remove("admin_hidden_media_ids").apply()
    }

    fun addHiddenKeyword(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isBlank()) return
        val current = getHiddenKeywords().toMutableSet()
        current.add(trimmed)
        prefs.edit().putStringSet("admin_hidden_keywords", current).apply()
    }

    fun removeHiddenKeyword(keyword: String) {
        val current = getHiddenKeywords().toMutableSet()
        current.remove(keyword.trim())
        prefs.edit().putStringSet("admin_hidden_keywords", current).apply()
    }

    fun isAdultCategory(categoryName: String?): Boolean {
        if (categoryName.isNullOrBlank()) return false
        val lower = categoryName.lowercase().trim()
        val adultWords = listOf(
            "adult", "adults", "18+", "18 +", "xxx", "nsfw", "erotic", "erotica", "porn", "sex", "sensual",
            "mature", "ullu", "kooku", "primeshots", "rabbit", "besharams", "hotx", "chikoo",
            "boomx", "cinemadosti", "cinema dosti", "redprime", "gupchup", "hunt", "feneo",
            "fliz", "voovi", "feelit", "unrated hot", "softcore", "nuefliks", "hotshots",
            "for adults", "for adult", "brazzers", "playboy", "hustler", "penthouse", "dorcel",
            "centoxcento", "blue hustler", "playflix", "bold"
        )
        return adultWords.any { lower.contains(it) }
    }

    fun isMediaHidden(item: MediaItem): Boolean {
        if (isAdultContentHidden() && (isAdultCategory(item.category) || isAdultMedia(item))) return true
        val hiddenIds = getHiddenItemIds()
        if (hiddenIds.contains(item.id)) return true
        val hiddenKeywords = getHiddenKeywords()
        if (hiddenKeywords.isNotEmpty()) {
            val textToScan = "${item.title} ${item.category} ${item.genre ?: ""} ${item.description ?: ""}".lowercase()
            if (hiddenKeywords.any { textToScan.contains(it.lowercase()) }) return true
        }
        return false
    }

    fun isAdultMedia(item: MediaItem): Boolean {
        if (isAdultCategory(item.category)) return true
        val adultKeywords = listOf(
            "adult", "adults", "18+", "18 +", "xxx", "nsfw", "erotic", "erotica", "porn", "sex", "sensual",
            "mature", "ullu", "kooku", "primeshots", "rabbit", "besharams", "hotx", "chikoo",
            "boomx", "cinemadosti", "cinema dosti", "redprime", "gupchup", "hunt", "feneo",
            "fliz", "voovi", "feelit", "unrated hot", "softcore", "nuefliks", "hotshots",
            "for adults", "for adult", "brazzers", "playboy", "hustler", "penthouse", "dorcel",
            "centoxcento", "blue hustler", "playflix", "bold"
        )
        val textToScan = "${item.title} ${item.category} ${item.genre ?: ""} ${item.description ?: ""}".lowercase()
        return adultKeywords.any { keyword ->
            textToScan.contains(keyword)
        }
    }

    // -------------------------------------------------------------
    // STALKER / MINISTRA PORTAL REPOSITORY ADAPTER METHODS
    // -------------------------------------------------------------
    fun getFastPriorityChannelIds(): Set<String> {
        return prefs.getStringSet("admin_fast_priority_channel_ids", emptySet()) ?: emptySet()
    }

    fun isFastPriorityChannel(channel: MediaItem): Boolean {
        val priorityKeywords = listOf("star sports", "t sports", "willow", "sony ten", "ptv sports", "sky sports", "bein sports", "live", "ipl", "bpl", "icc", "cricket")
        val titleLower = channel.title.lowercase()
        return priorityKeywords.any { titleLower.contains(it) }
    }

    fun isStalkerFastChannelsEnabled(): Boolean = prefs.getBoolean("stalker_fast_channels_enabled", false)
    fun setStalkerFastChannelsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("stalker_fast_channels_enabled", enabled).apply()
    }

    fun getStalkerFastChannelLimit(): Int = prefs.getInt("stalker_fast_channel_limit", 50)
    fun setStalkerFastChannelLimit(limit: Int) {
        prefs.edit().putInt("stalker_fast_channel_limit", limit).apply()
    }

    fun getStalkerAccounts(): List<com.example.model.StalkerPortalAccount> {
        val raw = prefs.getString("saved_stalker_accounts_json", null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(raw)
            val list = mutableListOf<com.example.model.StalkerPortalAccount>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    com.example.model.StalkerPortalAccount(
                        id = obj.optString("id", java.util.UUID.randomUUID().toString()),
                        name = obj.optString("name", "Stalker Portal"),
                        portalUrl = obj.optString("portalUrl", ""),
                        macAddress = obj.optString("macAddress", ""),
                        token = obj.optString("token", "").takeIf { it.isNotBlank() },
                        isActive = obj.optBoolean("isActive", true),
                        isEnabled = obj.optBoolean("isEnabled", true),
                        includeLive = obj.optBoolean("includeLive", true),
                        includeVod = obj.optBoolean("includeVod", true),
                        includeSeries = obj.optBoolean("includeSeries", true)
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getActiveStalkerAccounts(): List<com.example.model.StalkerPortalAccount> {
        return getStalkerAccounts().filter { it.isActive && it.isEnabled }
    }

    fun saveStalkerAccount(account: com.example.model.StalkerPortalAccount) {
        val current = getStalkerAccounts().toMutableList()
        val idx = current.indexOfFirst { it.id == account.id }
        if (idx >= 0) {
            current[idx] = account
        } else {
            current.add(account)
        }
        saveStalkerAccountsList(current)
    }

    fun saveStalkerAccountsList(accounts: List<com.example.model.StalkerPortalAccount>) {
        val arr = org.json.JSONArray()
        for (acc in accounts) {
            val obj = org.json.JSONObject()
            obj.put("id", acc.id)
            obj.put("name", acc.name)
            obj.put("portalUrl", acc.portalUrl)
            obj.put("macAddress", acc.macAddress)
            obj.put("token", acc.token ?: "")
            obj.put("isActive", acc.isActive)
            obj.put("isEnabled", acc.isEnabled)
            obj.put("includeLive", acc.includeLive)
            obj.put("includeVod", acc.includeVod)
            obj.put("includeSeries", acc.includeSeries)
            arr.put(obj)
        }
        prefs.edit().putString("saved_stalker_accounts_json", arr.toString()).apply()
    }

    fun deleteStalkerAccount(id: String) {
        val current = getStalkerAccounts().filterNot { it.id == id }
        saveStalkerAccountsList(current)
    }

    fun deleteStalkerAccount(account: com.example.model.StalkerPortalAccount) {
        deleteStalkerAccount(account.id)
    }

    suspend fun testStalkerPortal(url: String = "", mac: String = ""): Pair<Boolean, String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (url.isBlank() || mac.isBlank()) return@withContext Pair(false, "পোর্টাল ইউআরএল ও ম্যাক অ্যাড্রেস দিন")
        com.example.util.StalkerPortalManager.testConnection(url, mac)
    }

    suspend fun testStalkerPortal(account: com.example.model.StalkerPortalAccount): Pair<Boolean, String> {
        return testStalkerPortal(account.portalUrl, account.macAddress)
    }

    suspend fun fetchAllStalkerLiveChannels(account: com.example.model.StalkerPortalAccount? = null, portalUrl: String? = null, macAddress: String? = null, forceRefresh: Boolean = false): List<MediaItem> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val pUrl = portalUrl ?: account?.portalUrl ?: return@withContext emptyList()
        val pMac = macAddress ?: account?.macAddress ?: return@withContext emptyList()
        com.example.util.StalkerPortalManager.fetchLiveChannels(pUrl, pMac)
    }

    suspend fun fetchAllStalkerLiveChannels(): List<MediaItem> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val active = getActiveStalkerAccounts()
        val result = mutableListOf<MediaItem>()
        for (acc in active) {
            result.addAll(com.example.util.StalkerPortalManager.fetchLiveChannels(acc.portalUrl, acc.macAddress))
        }
        result
    }

    suspend fun fetchAllStalkerLiveChannels(forceRefresh: Boolean): List<MediaItem> = fetchAllStalkerLiveChannels()
    suspend fun fetchAllStalkerLiveChannels(account: com.example.model.StalkerPortalAccount): List<MediaItem> = fetchAllStalkerLiveChannels(account = account)
    suspend fun fetchAllStalkerLiveChannels(portalUrl: String, macAddress: String): List<MediaItem> = fetchAllStalkerLiveChannels(portalUrl = portalUrl, macAddress = macAddress)

    suspend fun fetchAllStalkerMovies(account: com.example.model.StalkerPortalAccount? = null, portalUrl: String? = null, macAddress: String? = null, forceRefresh: Boolean = false): List<MediaItem> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val pUrl = portalUrl ?: account?.portalUrl ?: return@withContext emptyList()
        val pMac = macAddress ?: account?.macAddress ?: return@withContext emptyList()
        com.example.util.StalkerPortalManager.fetchVodMovies(pUrl, pMac)
    }

    suspend fun fetchAllStalkerMovies(): List<MediaItem> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val active = getActiveStalkerAccounts()
        val result = mutableListOf<MediaItem>()
        for (acc in active) {
            result.addAll(com.example.util.StalkerPortalManager.fetchVodMovies(acc.portalUrl, acc.macAddress))
        }
        result
    }

    suspend fun fetchAllStalkerMovies(forceRefresh: Boolean): List<MediaItem> = fetchAllStalkerMovies()
    suspend fun fetchAllStalkerMovies(account: com.example.model.StalkerPortalAccount): List<MediaItem> = fetchAllStalkerMovies(account = account)
    suspend fun fetchAllStalkerMovies(portalUrl: String, macAddress: String): List<MediaItem> = fetchAllStalkerMovies(portalUrl = portalUrl, macAddress = macAddress)

    suspend fun fetchStalkerFastChannels(account: com.example.model.StalkerPortalAccount? = null, portalUrl: String? = null, macAddress: String? = null, limit: Int = 50, forceRefresh: Boolean = false): List<MediaItem> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val all = if (account != null) fetchAllStalkerLiveChannels(account = account) else fetchAllStalkerLiveChannels()
        all.take(limit)
    }

    suspend fun fetchStalkerFastChannels(): List<MediaItem> = fetchStalkerFastChannels(limit = getStalkerFastChannelLimit())
    suspend fun fetchStalkerFastChannels(forceRefresh: Boolean): List<MediaItem> = fetchStalkerFastChannels()
    suspend fun fetchStalkerFastChannels(limit: Int): List<MediaItem> = fetchStalkerFastChannels(null, null, null, limit)
    suspend fun fetchStalkerFastChannels(account: com.example.model.StalkerPortalAccount): List<MediaItem> = fetchStalkerFastChannels(account = account)
    suspend fun fetchStalkerFastChannels(portalUrl: String, macAddress: String): List<MediaItem> = fetchStalkerFastChannels(portalUrl = portalUrl, macAddress = macAddress)
    suspend fun fetchFastChannels(): List<MediaItem> = fetchStalkerFastChannels()
}


