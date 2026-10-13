package com.example.util

import android.net.Uri
import com.example.NafiTvApp
import com.example.model.MediaItem
import com.example.model.MediaType
import com.example.model.StreamServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * High-performance Stalker / Ministra Portal (MAG250 API) Client & Manager.
 * Connects to MAG Stalker Portals via MAC Address authentication,
 * extracts Live TV channels, VOD Movies, and Series with direct streaming links.
 */
object StalkerPortalManager {

    private const val STALKER_USER_AGENT = "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3"

    private val httpClient = NafiTvApp.sharedOkHttpClient.newBuilder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Converts a user-provided Portal URL (e.g. "http://zerotv.eu:8080/c/" or "http://zerotv.eu:8080/c")
     * into the standard Stalker API endpoint: "http://zerotv.eu:8080/server/load.php"
     */
    fun normalizeLoadPhpUrl(portalUrl: String): String {
        var clean = portalUrl.trim()
        if (clean.endsWith("/")) clean = clean.substring(0, clean.length - 1)
        return when {
            clean.endsWith("/server/load.php", ignoreCase = true) -> clean
            clean.endsWith("/c", ignoreCase = true) -> clean.substring(0, clean.length - 2) + "/server/load.php"
            clean.contains("/c/", ignoreCase = true) -> clean.replace("/c/", "/server/load.php")
            else -> "$clean/server/load.php"
        }
    }

    /**
     * Extracts base host and port URL, e.g. "http://zerotv.eu:8080"
     */
    fun extractBaseHost(portalUrl: String): String {
        return try {
            val uri = Uri.parse(portalUrl.trim())
            val portPart = if (uri.port != -1 && uri.port != 80 && uri.port != 443) ":${uri.port}" else if (uri.port == 8080 || uri.port == 7070 || uri.port == 2095) ":${uri.port}" else ""
            "${uri.scheme}://${uri.host}$portPart"
        } catch (_: Exception) {
            portalUrl.substringBefore("/server/").substringBefore("/c")
        }
    }

    /**
     * Handshake request with MAC address cookie.
     * Returns session token (e.g. "5C6C0A2168F4F0530E3EA1241E2876F3")
     */
    suspend fun handshake(portalUrl: String, macAddress: String): Result<String> = withContext(Dispatchers.IO) {
        val endpoint = normalizeLoadPhpUrl(portalUrl)
        val mac = macAddress.trim().uppercase()
        val url = "$endpoint?type=stb&action=handshake&token=&JsHttpRequest=1-xml"

        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", STALKER_USER_AGENT)
                .header("X-User-Agent", "Model: MAG250; Link: Ethernet")
                .header("Cookie", "mac=$mac; stb_lang=en; timezone=Europe/Kiev;")
                .header("Referer", portalUrl)
                .get()
                .build()

            val res = httpClient.newCall(req).execute()
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful || body.isBlank()) {
                return@withContext Result.failure(Exception("HTTP ${res.code}: সার্ভার থেকে কোনো ডেটা আসেনি"))
            }

            val json = JSONObject(body)
            val js = json.optJSONObject("js") ?: return@withContext Result.failure(Exception("অবৈধ রেসপন্স ফরম্যাট"))
            val token = js.optString("token", "")
            if (token.isNotBlank()) {
                Result.success(token)
            } else {
                Result.failure(Exception("টোকেন পাওয়া যায়নি। MAC Address সঠিক কি না যাচাই করুন।"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get user profile status from Stalker Portal.
     */
    suspend fun getProfile(portalUrl: String, macAddress: String, token: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        val endpoint = normalizeLoadPhpUrl(portalUrl)
        val mac = macAddress.trim().uppercase()
        val url = "$endpoint?type=stb&action=get_profile&JsHttpRequest=1-xml"

        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", STALKER_USER_AGENT)
                .header("X-User-Agent", "Model: MAG250; Link: Ethernet")
                .header("Authorization", "Bearer $token")
                .header("Cookie", "mac=$mac; stb_lang=en; timezone=Europe/Kiev;")
                .get()
                .build()

            val res = httpClient.newCall(req).execute()
            val body = res.body?.string().orEmpty()
            val json = JSONObject(body)
            val js = json.optJSONObject("js") ?: return@withContext Result.failure(Exception("প্রোফাইল লোড হয়নি"))
            Result.success(js)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Fetches Live TV category/genres map (genre_id -> title)
     */
    suspend fun getGenres(portalUrl: String, macAddress: String, token: String): Map<String, String> = withContext(Dispatchers.IO) {
        val endpoint = normalizeLoadPhpUrl(portalUrl)
        val mac = macAddress.trim().uppercase()
        val url = "$endpoint?type=itv&action=get_genres&JsHttpRequest=1-xml"
        val map = mutableMapOf<String, String>()

        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", STALKER_USER_AGENT)
                .header("Authorization", "Bearer $token")
                .header("Cookie", "mac=$mac; stb_lang=en; timezone=Europe/Kiev;")
                .get()
                .build()

            val res = httpClient.newCall(req).execute()
            val body = res.body?.string().orEmpty()
            val json = JSONObject(body)
            val js = json.optJSONArray("js")
            if (js != null) {
                for (i in 0 until js.length()) {
                    val obj = js.getJSONObject(i)
                    val id = obj.optString("id", "")
                    val title = obj.optString("title", "")
                    if (id.isNotBlank() && title.isNotBlank()) {
                        map[id] = title
                    }
                }
            }
        } catch (_: Exception) {}
        map
    }

    /**
     * Tests Stalker Portal connection and returns details.
     */
    suspend fun testConnection(portalUrl: String, macAddress: String): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (portalUrl.isBlank() || macAddress.isBlank()) {
            return@withContext Pair(false, "❌ পোর্টাল URL এবং MAC Address উভয়ই প্রদান করুন")
        }

        val handshakeRes = handshake(portalUrl, macAddress)
        val token = handshakeRes.getOrNull()
        if (token == null) {
            val err = handshakeRes.exceptionOrNull()?.localizedMessage ?: "সংযোগ ব্যর্থ"
            return@withContext Pair(false, "❌ হ্যান্ডশেক ব্যর্থ: $err")
        }

        val profileRes = getProfile(portalUrl, macAddress, token)
        val profile = profileRes.getOrNull()
        val status = profile?.optInt("status", 0) ?: 0
        val expDate = profile?.optString("expire_billing_date", "")?.takeIf { it.isNotBlank() && !it.startsWith("0000") } ?: "আনলিমিটেড (Unlimited)"

        // Quick check channel count
        var channelCount = 0
        try {
            val endpoint = normalizeLoadPhpUrl(portalUrl)
            val mac = macAddress.trim().uppercase()
            val chUrl = "$endpoint?type=itv&action=get_all_channels&JsHttpRequest=1-xml"
            val req = Request.Builder()
                .url(chUrl)
                .header("User-Agent", STALKER_USER_AGENT)
                .header("Authorization", "Bearer $token")
                .header("Cookie", "mac=$mac; stb_lang=en; timezone=Europe/Kiev;")
                .get()
                .build()
            val res = httpClient.newCall(req).execute()
            val body = res.body?.string().orEmpty()
            val json = JSONObject(body)
            val js = json.optJSONObject("js")
            channelCount = js?.optInt("total_items", 0) ?: 0
        } catch (_: Exception) {}

        val countInfo = if (channelCount > 0) " | $channelCount টি চ্যানেল উপলব্ধ" else ""
        Pair(true, "✅ Stalker Portal সক্রিয়! টোকেন: ${token.take(8)}... (স্ট্যাটাস: ${if (status == 1) "সক্রিয়" else "অনলাইন"}, মেয়াদ: $expDate$countInfo)")
    }

    /**
     * Fetches all Live TV channels from the Stalker Portal and converts them to MediaItem.
     */
    suspend fun fetchLiveChannels(portalUrl: String, macAddress: String): List<MediaItem> = withContext(Dispatchers.IO) {
        val handshakeRes = handshake(portalUrl, macAddress)
        val token = handshakeRes.getOrNull() ?: return@withContext emptyList()
        val endpoint = normalizeLoadPhpUrl(portalUrl)
        val baseHost = extractBaseHost(portalUrl)
        val mac = macAddress.trim().uppercase()

        val genres = getGenres(portalUrl, macAddress, token)
        val url = "$endpoint?type=itv&action=get_all_channels&JsHttpRequest=1-xml"
        val items = mutableListOf<MediaItem>()

        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", STALKER_USER_AGENT)
                .header("Authorization", "Bearer $token")
                .header("Cookie", "mac=$mac; stb_lang=en; timezone=Europe/Kiev;")
                .get()
                .build()

            val res = httpClient.newCall(req).execute()
            val body = res.body?.string().orEmpty()
            val json = JSONObject(body)
            val js = json.optJSONObject("js") ?: return@withContext emptyList()
            val dataArray = js.optJSONArray("data") ?: return@withContext emptyList()

            for (i in 0 until dataArray.length()) {
                val obj = dataArray.getJSONObject(i)
                val chId = obj.optString("id", "")
                val name = obj.optString("name", "Channel $i").trim()
                if (chId.isBlank() || name.isBlank()) continue

                val logo = obj.optString("logo", "")
                val genreId = obj.optString("tv_genre_id", "")
                val categoryName = genres[genreId] ?: "Stalker Live TV"

                // Direct streaming link pattern for Stalker / Ministra live streams
                val fullCmd = obj.optString("cmd", "")
                val streamUrl = "$baseHost/play/live.php?mac=$mac&stream=$chId&extension=ts"

                // Additional server option with raw cmd if available
                val rawCmd = fullCmd.replace("ffmpeg ", "").replace("ffrt ", "").trim()
                val servers = mutableListOf(StreamServer(name = "সার্ভার ১ (TS Stream)", url = streamUrl))
                if (rawCmd.isNotBlank() && rawCmd.startsWith("http", ignoreCase = true)) {
                    servers.add(StreamServer(name = "সার্ভার ২ (Direct)", url = rawCmd))
                }

                items.add(
                    MediaItem(
                        id = "stalker_ch_${baseHost.hashCode()}_$chId",
                        title = name,
                        streamUrl = streamUrl,
                        logoUrl = logo.ifBlank { null },
                        category = categoryName,
                        tournament = "Stalker Portal",
                        type = MediaType.LIVE_TV,
                        isLive = true,
                        servers = servers,
                        stalkerPortalUrl = portalUrl,
                        stalkerMacAddress = macAddress,
                        stalkerCmd = fullCmd,
                        userAgent = STALKER_USER_AGENT,
                        cookie = "mac=$mac; stb_lang=en; timezone=Europe/Kiev;"
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        items
    }

    /**
     * Resolves a dynamic live streaming token and direct URL for a Stalker channel
     */
    suspend fun resolveStreamUrl(portalUrl: String, macAddress: String, cmd: String): String? = withContext(Dispatchers.IO) {
        if (portalUrl.isBlank() || macAddress.isBlank() || cmd.isBlank()) return@withContext null
        try {
            val handshakeRes = handshake(portalUrl, macAddress)
            val token = handshakeRes.getOrNull() ?: return@withContext null
            val endpoint = normalizeLoadPhpUrl(portalUrl)
            val mac = macAddress.trim().uppercase()
            val linkUrl = "$endpoint?type=itv&action=create_link&cmd=${URLEncoder.encode(cmd, "UTF-8")}&JsHttpRequest=1-xml"

            val req = Request.Builder()
                .url(linkUrl)
                .header("User-Agent", STALKER_USER_AGENT)
                .header("Authorization", "Bearer $token")
                .header("Cookie", "mac=$mac; stb_lang=en; timezone=Europe/Kiev;")
                .get()
                .build()

            val res = httpClient.newCall(req).execute()
            val body = res.body?.string().orEmpty()
            val json = JSONObject(body)
            val js = json.optJSONObject("js") ?: return@withContext null
            val generatedCmd = js.optString("cmd", "").replace("ffmpeg ", "").replace("ffrt ", "").trim()
            if (generatedCmd.startsWith("http", ignoreCase = true)) {
                generatedCmd
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Fetches VOD Movies from Stalker Portal.
     */
    suspend fun fetchVodMovies(portalUrl: String, macAddress: String, page: Int = 1): List<MediaItem> = withContext(Dispatchers.IO) {
        val handshakeRes = handshake(portalUrl, macAddress)
        val token = handshakeRes.getOrNull() ?: return@withContext emptyList()
        val endpoint = normalizeLoadPhpUrl(portalUrl)
        val baseHost = extractBaseHost(portalUrl)
        val mac = macAddress.trim().uppercase()

        val url = "$endpoint?type=vod&action=get_ordered_list&sortby=added&p=$page&JsHttpRequest=1-xml"
        val items = mutableListOf<MediaItem>()

        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", STALKER_USER_AGENT)
                .header("Authorization", "Bearer $token")
                .header("Cookie", "mac=$mac; stb_lang=en; timezone=Europe/Kiev;")
                .get()
                .build()

            val res = httpClient.newCall(req).execute()
            val body = res.body?.string().orEmpty()
            val json = JSONObject(body)
            val js = json.optJSONObject("js") ?: return@withContext emptyList()
            val dataArray = js.optJSONArray("data") ?: return@withContext emptyList()

            for (i in 0 until dataArray.length()) {
                val obj = dataArray.getJSONObject(i)
                val movId = obj.optString("id", "")
                val name = obj.optString("name", "Movie $i").trim()
                if (movId.isBlank() || name.isBlank()) continue

                val pic = obj.optString("pic", "")
                val year = obj.optString("year", "")
                val rating = obj.optString("rating_imdb", "")
                val cmd = obj.optString("cmd", "")

                // Standard VOD streaming link
                val streamUrl = "$baseHost/play/movie.php?mac=$mac&stream=$movId.mkv&type=movie"

                items.add(
                    MediaItem(
                        id = "stalker_mov_${baseHost.hashCode()}_$movId",
                        title = name,
                        streamUrl = streamUrl,
                        logoUrl = pic.ifBlank { null },
                        category = "Stalker Movies",
                        tournament = "Stalker Portal",
                        type = MediaType.MOVIE,
                        isLive = false,
                        description = obj.optString("description", ""),
                        servers = listOf(StreamServer(name = "এইচডি প্লেয়ার", url = streamUrl)),
                        stalkerPortalUrl = portalUrl,
                        stalkerMacAddress = macAddress,
                        stalkerCmd = cmd,
                        userAgent = STALKER_USER_AGENT,
                        cookie = "mac=$mac; stb_lang=en; timezone=Europe/Kiev;"
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        items
    }

    /**
     * Resolves dynamic VOD stream link with token
     */
    suspend fun resolveVodUrl(portalUrl: String, macAddress: String, cmd: String): String? = withContext(Dispatchers.IO) {
        if (portalUrl.isBlank() || macAddress.isBlank() || cmd.isBlank()) return@withContext null
        try {
            val handshakeRes = handshake(portalUrl, macAddress)
            val token = handshakeRes.getOrNull() ?: return@withContext null
            val endpoint = normalizeLoadPhpUrl(portalUrl)
            val mac = macAddress.trim().uppercase()
            val linkUrl = "$endpoint?type=vod&action=create_link&cmd=${URLEncoder.encode(cmd, "UTF-8")}&JsHttpRequest=1-xml"

            val req = Request.Builder()
                .url(linkUrl)
                .header("User-Agent", STALKER_USER_AGENT)
                .header("Authorization", "Bearer $token")
                .header("Cookie", "mac=$mac; stb_lang=en; timezone=Europe/Kiev;")
                .get()
                .build()

            val res = httpClient.newCall(req).execute()
            val body = res.body?.string().orEmpty()
            val json = JSONObject(body)
            val js = json.optJSONObject("js") ?: return@withContext null
            val generatedCmd = js.optString("cmd", "").replace("ffmpeg ", "").replace("ffrt ", "").trim()
            if (generatedCmd.startsWith("http", ignoreCase = true)) {
                generatedCmd
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Fetches category/genres list with (id, title)
     */
    suspend fun fetchGenresList(portalUrl: String, macAddress: String): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val handshakeRes = handshake(portalUrl, macAddress)
        val token = handshakeRes.getOrNull() ?: return@withContext emptyList()
        val endpoint = normalizeLoadPhpUrl(portalUrl)
        val mac = macAddress.trim().uppercase()
        val url = "$endpoint?type=itv&action=get_genres&JsHttpRequest=1-xml"
        val list = mutableListOf<Pair<String, String>>()
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", STALKER_USER_AGENT)
                .header("Authorization", "Bearer $token")
                .header("Cookie", "mac=$mac; stb_lang=en; timezone=Europe/Kiev;")
                .get()
                .build()
            val res = httpClient.newCall(req).execute()
            val body = res.body?.string().orEmpty()
            val json = JSONObject(body)
            val js = json.optJSONArray("js")
            if (js != null) {
                for (i in 0 until js.length()) {
                    val obj = js.getJSONObject(i)
                    val id = obj.optString("id", "")
                    val title = obj.optString("title", "")
                    if (id.isNotBlank() && id != "*" && title.isNotBlank()) {
                        list.add(Pair(id, title))
                    }
                }
            }
        } catch (_: Exception) {}
        list
    }

    /**
     * Fetches channels for a specific genre ID (lightning fast, <0.2s)
     */
    suspend fun fetchChannelsByGenre(portalUrl: String, macAddress: String, genreId: String, page: Int = 1): List<MediaItem> = withContext(Dispatchers.IO) {
        val handshakeRes = handshake(portalUrl, macAddress)
        val token = handshakeRes.getOrNull() ?: return@withContext emptyList()
        val endpoint = normalizeLoadPhpUrl(portalUrl)
        val baseHost = extractBaseHost(portalUrl)
        val mac = macAddress.trim().uppercase()

        val genres = getGenres(portalUrl, macAddress, token)
        val url = "$endpoint?type=itv&action=get_ordered_list&genre=$genreId&p=$page&JsHttpRequest=1-xml"
        val items = mutableListOf<MediaItem>()

        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", STALKER_USER_AGENT)
                .header("Authorization", "Bearer $token")
                .header("Cookie", "mac=$mac; stb_lang=en; timezone=Europe/Kiev;")
                .get()
                .build()
            val res = httpClient.newCall(req).execute()
            val body = res.body?.string().orEmpty()
            val json = JSONObject(body)
            val js = json.optJSONObject("js") ?: return@withContext emptyList()
            val dataArray = js.optJSONArray("data") ?: return@withContext emptyList()

            for (i in 0 until dataArray.length()) {
                val obj = dataArray.getJSONObject(i)
                val chId = obj.optString("id", "")
                val name = obj.optString("name", "Channel $i").trim()
                if (chId.isBlank() || name.isBlank()) continue

                val logo = obj.optString("logo", "")
                val categoryName = genres[genreId] ?: "Stalker Live TV"
                val fullCmd = obj.optString("cmd", "")
                val streamUrl = "$baseHost/play/live.php?mac=$mac&stream=$chId&extension=ts"
                val rawCmd = fullCmd.replace("ffmpeg ", "").replace("ffrt ", "").trim()
                val servers = mutableListOf(StreamServer(name = "সার্ভার ১ (TS Stream)", url = streamUrl))
                if (rawCmd.isNotBlank() && rawCmd.startsWith("http", ignoreCase = true)) {
                    servers.add(StreamServer(name = "সার্ভার ২ (Direct)", url = rawCmd))
                }

                items.add(
                    MediaItem(
                        id = "stalker_ch_${baseHost.hashCode()}_$chId",
                        title = name,
                        streamUrl = streamUrl,
                        logoUrl = logo.ifBlank { null },
                        category = categoryName,
                        tournament = "Stalker Portal",
                        type = MediaType.LIVE_TV,
                        isLive = true,
                        servers = servers,
                        stalkerPortalUrl = portalUrl,
                        stalkerMacAddress = macAddress,
                        stalkerCmd = fullCmd,
                        userAgent = STALKER_USER_AGENT,
                        cookie = "mac=$mac; stb_lang=en; timezone=Europe/Kiev;"
                    )
                )
            }
        } catch (_: Exception) {}
        items
    }

    /**
     * Fast channels fetch: pulls the most popular categories in ~1-2 seconds
     */
    suspend fun fetchFastChannels(portalUrl: String, macAddress: String, maxChannels: Int = 300): List<MediaItem> = withContext(Dispatchers.IO) {
        val handshakeRes = handshake(portalUrl, macAddress)
        val token = handshakeRes.getOrNull() ?: return@withContext emptyList()
        val genres = fetchGenresList(portalUrl, macAddress)
        val priorityKeywords = listOf("spor", "sport", "ulusal", "sinema", "cinema", "haber", "news", "dizi", "film", "movie", "hd", "fhd")
        val priorityGenres = genres.filter { g -> priorityKeywords.any { k -> g.second.contains(k, ignoreCase = true) } }
        val targetGenres = if (priorityGenres.isNotEmpty()) priorityGenres else genres.take(15)

        val result = mutableListOf<MediaItem>()
        for (genre in targetGenres) {
            if (result.size >= maxChannels) break
            val genreChannels = fetchChannelsByGenre(portalUrl, macAddress, genre.first)
            result.addAll(genreChannels)
        }

        if (result.isEmpty()) {
            fetchLiveChannels(portalUrl, macAddress).take(maxChannels)
        } else {
            result.take(maxChannels)
        }
    }
}
