package com.example.player

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object StreamExtractor {

    private const val TAG = "StreamExtractor"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    const val DEFAULT_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    /**
     * Checks whether the given URL is an embed/web player URL or social video link
     * that should be resolved to a direct stream or handled via WebStreamPlayer.
     */
    fun isEmbedUrl(url: String): Boolean {
        val clean = url.lowercase().trim()
        if (clean.isBlank() || clean.startsWith("file:") || clean.startsWith("/") || clean.startsWith("content:")) {
            return false
        }
        
        // Social & Video platforms that are web players
        if (clean.contains("youtube.com") ||
            clean.contains("youtu.be") ||
            clean.contains("facebook.com") ||
            clean.contains("fb.watch") ||
            clean.contains("fb.com") ||
            clean.contains("dailymotion.com") ||
            clean.contains("dai.ly") ||
            clean.contains("vimeo.com") ||
            clean.contains("ok.ru") ||
            clean.contains("drive.google.com") ||
            clean.contains("dropbox.com") ||
            clean.contains("pixeldrain.com/u/") ||
            clean.contains("pixeldrain.dev/u/")
        ) {
            return true
        }

        // Direct stream formats that ExoPlayer plays natively
        val hasDirectMediaExtension = clean.contains(".m3u8") ||
                clean.contains(".mpd") ||
                clean.contains(".mp4") ||
                clean.contains(".mkv") ||
                clean.contains(".webm") ||
                clean.contains(".ts") ||
                clean.contains(".avi") ||
                clean.contains(".mov") ||
                clean.contains(".flv")

        if (hasDirectMediaExtension && !clean.contains("drive.google.com")) {
            return false
        }

        // Standard IPTV live, restream, and portal paths (play directly in ExoPlayer)
        val isDirectLiveStream = clean.contains("/live/") ||
                clean.contains("/movie/") ||
                clean.contains("/series/") ||
                clean.contains("/hls/") ||
                clean.contains("/stream") ||
                clean.contains("/channel") ||
                clean.contains("toffeelive.com") ||
                clean.contains("akamaized.net") ||
                clean.contains("tapmad") ||
                clean.contains("rgkkw") ||
                clean.contains("akr4m") ||
                clean.contains("bdtv") ||
                clean.contains("movielinkbd") ||
                clean.contains("player_api.php") ||
                clean.contains("get.php")

        if (isDirectLiveStream) {
            return false
        }

        // Explicit web embed player URLs
        val isExplicitEmbed = clean.contains("2embed") ||
                clean.contains("vidsrc") ||
                clean.contains("superembed") ||
                clean.contains("autoembed") ||
                clean.contains("/embed/") ||
                clean.contains("embed.html") ||
                clean.contains("player.html") ||
                clean.contains("streamtape.com") ||
                clean.contains("doodstream") ||
                clean.contains("dood.") ||
                clean.contains("mixdrop") ||
                clean.contains("vidcloud") ||
                clean.contains("playertv")

        if (isExplicitEmbed) {
            return true
        }

        // Webpage documents (.html / .htm)
        if (clean.contains(".html") || clean.contains(".htm")) {
            return true
        }

        // All other direct streams adopt ExoPlayer directly for fast playback!
        return false
    }

    /**
     * Resolves an embed/web URL to a direct playable stream (m3u8, mpd, mp4) with necessary request headers.
     */
    suspend fun extractDirectStream(url: String): ExtractedStreamResult? = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = url.trim()
            val lower = cleanUrl.lowercase()

            // 1. Pixeldrain resolver (handles pixeldrain.dev/api/file/..., pixeldrain.com/u/..., etc.)
            if (lower.contains("pixeldrain") || lower.contains("pixeldra.in")) {
                extractPixeldrainStream(cleanUrl)?.let { return@withContext it }
            }

            // 2. YouTube resolver
            if (lower.contains("youtube.com") || lower.contains("youtu.be")) {
                extractYouTubeStream(cleanUrl)?.let { return@withContext it }
            }

            // 3. Facebook resolver
            if (lower.contains("facebook.com") || lower.contains("fb.watch") || lower.contains("fb.com")) {
                extractFacebookStream(cleanUrl)?.let { return@withContext it }
            }

            // 4. Google Drive resolver
            if (lower.contains("drive.google.com")) {
                extractGoogleDriveStream(cleanUrl)?.let { return@withContext it }
            }

            // 5. Vimeo resolver
            if (lower.contains("vimeo.com")) {
                extractVimeoStream(cleanUrl)?.let { return@withContext it }
            }

            // 6. Dailymotion resolver
            if (lower.contains("dailymotion.com") || lower.contains("dai.ly")) {
                extractDailymotionStream(cleanUrl)?.let { return@withContext it }
            }

            // 7. If it's already a direct video stream
            if (lower.contains(".m3u8") || lower.contains(".mpd") || lower.contains(".mp4") || lower.contains(".mkv") ||
                lower.contains(".ts") || lower.contains("video/mp2t") || lower.contains("/live/")
            ) {
                val isXtreamOrTs = lower.contains(".ts") || lower.contains("/live/") || lower.contains("video/mp2t")
                return@withContext ExtractedStreamResult(
                    streamUrl = cleanUrl,
                    headers = if (isXtreamOrTs) {
                        mapOf(
                            "User-Agent" to "VLC/3.0.18 LibVLC/3.0.18",
                            "Accept" to "*/*",
                            "Connection" to "keep-alive"
                        )
                    } else {
                        mapOf("User-Agent" to DEFAULT_UA)
                    }
                )
            }

            // 8. 2Embed resolver (2embed.cc, 2embed.to, 2embed.skin, 2embed.stream)
            if (lower.contains("2embed")) {
                extractFrom2Embed(cleanUrl)?.let { return@withContext it }
            }

            // 9. VidSrc resolver (vidsrc.to, vidsrc.me, vidsrc.net, vidsrc.xyz, vidsrc.in)
            if (lower.contains("vidsrc")) {
                extractFromVidSrc(cleanUrl)?.let { return@withContext it }
            }

            // 10. SuperStream / SmashyStream resolver
            if (lower.contains("superstream") || lower.contains("smashystream")) {
                extractFromGenericEmbed(cleanUrl)?.let { return@withContext it }
            }

            // 11. Generic page scraping for embedded m3u8 / mpd / mp4 / hls sources
            extractFromGenericEmbed(cleanUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Extraction failed for $url: ${e.message}")
            null
        }
    }

    /**
     * Pixeldrain Resolver: Converts web/API links into direct progressive stream URLs
     * with appropriate Referer and User-Agent headers.
     */
    fun extractPixeldrainStream(url: String): ExtractedStreamResult? {
        try {
            val clean = url.trim()
            val idPattern = Pattern.compile("(?:pixeldrain\\.(?:com|dev)|pixeldra\\.in)/(?:api/file/|[uU]/)([a-zA-Z0-9_-]+)")
            val matcher = idPattern.matcher(clean)
            val fileId = if (matcher.find()) {
                matcher.group(1)
            } else {
                clean.substringAfter("/api/file/").substringAfter("/u/").substringBefore("?").substringBefore("/")
            }

            if (!fileId.isNullOrBlank()) {
                // Direct download stream endpoint
                val directUrl = "https://pixeldrain.com/api/file/$fileId?download"
                return ExtractedStreamResult(
                    streamUrl = directUrl,
                    headers = mapOf(
                        "User-Agent" to DEFAULT_UA,
                        "Referer" to "https://pixeldrain.com/",
                        "Origin" to "https://pixeldrain.com",
                        "Accept" to "*/*"
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pixeldrain extraction error: ${e.message}")
        }
        return null
    }

    /**
     * YouTube ID Extractor: Supports standard watch URLs, short youtu.be, live streams, shorts, and embeds.
     */
    fun extractYouTubeId(url: String): String? {
        val pattern = Pattern.compile("(?:youtube\\.com\\/(?:[^\\n\\r]+\\?.*v=|live\\/|shorts\\/|embed\\/|v\\/)|youtu\\.be\\/)([a-zA-Z0-9_-]{11})")
        val matcher = pattern.matcher(url)
        return if (matcher.find()) matcher.group(1) else null
    }

    /**
     * YouTube Stream/Embed Resolver: Prepares direct embed or streaming endpoint.
     */
    suspend fun extractYouTubeStream(url: String): ExtractedStreamResult? = withContext(Dispatchers.IO) {
        val videoId = extractYouTubeId(url) ?: return@withContext null
        // Provide standard YouTube embed URL with auto-play parameters
        val embedUrl = "https://www.youtube.com/embed/$videoId?autoplay=1&playsinline=1&controls=1&rel=0"
        ExtractedStreamResult(
            streamUrl = embedUrl,
            headers = mapOf("User-Agent" to DEFAULT_UA)
        )
    }

    /**
     * Facebook Video Resolver: Scrapes direct MP4 video URLs from the Facebook page HTML,
     * or falls back to the Facebook video player plugin.
     */
    suspend fun extractFacebookStream(url: String): ExtractedStreamResult? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", DEFAULT_UA)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5")
                .build()

            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: ""

            // Regex patterns for Facebook HD/SD CDN video streams
            val patterns = listOf(
                Pattern.compile("\"playable_url_quality_hd\"\\s*:\\s*\"([^\"]+)\""),
                Pattern.compile("\"playable_url\"\\s*:\\s*\"([^\"]+)\""),
                Pattern.compile("hd_src\\s*:\\s*\"([^\"]+)\""),
                Pattern.compile("sd_src\\s*:\\s*\"([^\"]+)\""),
                Pattern.compile("video_url\\s*:\\s*\"([^\"]+)\"")
            )

            for (p in patterns) {
                val m = p.matcher(html)
                if (m.find()) {
                    var directUrl = m.group(1) ?: continue
                    directUrl = directUrl.replace("\\/", "/").replace("\\u0025", "%").replace("&amp;", "&")
                    if (directUrl.startsWith("http://") || directUrl.startsWith("https://")) {
                        return@withContext ExtractedStreamResult(
                            streamUrl = directUrl,
                            headers = mapOf(
                                "User-Agent" to DEFAULT_UA,
                                "Referer" to "https://www.facebook.com/"
                            )
                        )
                    }
                }
            }

            // Fallback to Facebook Video Plugin embed
            val encoded = URLEncoder.encode(url, "UTF-8")
            val embedUrl = "https://www.facebook.com/plugins/video.php?href=$encoded&autoplay=1&show_text=0"
            return@withContext ExtractedStreamResult(
                streamUrl = embedUrl,
                headers = mapOf("User-Agent" to DEFAULT_UA)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Facebook extraction error: ${e.message}")
        }
        null
    }

    /**
     * Google Drive Video Resolver
     */
    fun extractGoogleDriveStream(url: String): ExtractedStreamResult? {
        try {
            val pattern = Pattern.compile("drive\\.google\\.com\\/(?:file\\/d\\/|open\\?id=)([a-zA-Z0-9_-]+)")
            val matcher = pattern.matcher(url)
            if (matcher.find()) {
                val id = matcher.group(1) ?: return null
                return ExtractedStreamResult(
                    streamUrl = "https://drive.google.com/uc?export=download&id=$id",
                    headers = mapOf("User-Agent" to DEFAULT_UA)
                )
            }
        } catch (_: Exception) {}
        return null
    }

    /**
     * Vimeo Video Resolver
     */
    suspend fun extractVimeoStream(url: String): ExtractedStreamResult? = withContext(Dispatchers.IO) {
        try {
            val pattern = Pattern.compile("vimeo\\.com\\/(?:video\\/)?([0-9]+)")
            val matcher = pattern.matcher(url)
            if (matcher.find()) {
                val videoId = matcher.group(1) ?: return@withContext null
                val configUrl = "https://player.vimeo.com/video/$videoId/config"
                val req = Request.Builder().url(configUrl).header("User-Agent", DEFAULT_UA).build()
                val resp = client.newCall(req).execute()
                val body = resp.body?.string() ?: ""
                if (body.startsWith("{")) {
                    val json = JSONObject(body)
                    val files = json.optJSONObject("request")?.optJSONObject("files")
                    val hlsUrl = files?.optJSONObject("hls")?.optJSONObject("cdns")?.optJSONObject("fastly_skyfire")?.optString("url")
                        ?: files?.optJSONObject("hls")?.optJSONObject("default_cdn")?.optString("url")
                    if (!hlsUrl.isNullOrBlank()) {
                        return@withContext ExtractedStreamResult(streamUrl = hlsUrl, headers = mapOf("User-Agent" to DEFAULT_UA))
                    }
                    val progArr = files?.optJSONArray("progressive")
                    if (progArr != null && progArr.length() > 0) {
                        val mp4Url = progArr.optJSONObject(0)?.optString("url")
                        if (!mp4Url.isNullOrBlank()) {
                            return@withContext ExtractedStreamResult(streamUrl = mp4Url, headers = mapOf("User-Agent" to DEFAULT_UA))
                        }
                    }
                }
                return@withContext ExtractedStreamResult(
                    streamUrl = "https://player.vimeo.com/video/$videoId?autoplay=1",
                    headers = mapOf("User-Agent" to DEFAULT_UA)
                )
            }
        } catch (_: Exception) {}
        null
    }

    /**
     * Dailymotion Video Resolver
     */
    suspend fun extractDailymotionStream(url: String): ExtractedStreamResult? = withContext(Dispatchers.IO) {
        try {
            val pattern = Pattern.compile("(?:dailymotion\\.com\\/(?:video\\/|embed\\/video\\/)|dai\\.ly\\/)([a-zA-Z0-9]+)")
            val matcher = pattern.matcher(url)
            if (matcher.find()) {
                val videoId = matcher.group(1) ?: return@withContext null
                val embedUrl = "https://www.dailymotion.com/embed/video/$videoId?autoplay=1"
                return@withContext ExtractedStreamResult(
                    streamUrl = embedUrl,
                    headers = mapOf("User-Agent" to DEFAULT_UA)
                )
            }
        } catch (_: Exception) {}
        null
    }

    private suspend fun extractFrom2Embed(url: String): ExtractedStreamResult? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", DEFAULT_UA)
                .header("Referer", "https://2embed.cc/")
                .build()

            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: return@withContext null

            findStreamInHtml(html, url)?.let { return@withContext it }

            val iframeMatcher = Pattern.compile("<iframe[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(html)
            while (iframeMatcher.find()) {
                var iframeSrc = iframeMatcher.group(1) ?: continue
                if (iframeSrc.startsWith("//")) iframeSrc = "https:$iframeSrc"
                if (iframeSrc.startsWith("/")) {
                    val base = getBaseUrl(url)
                    iframeSrc = "$base$iframeSrc"
                }

                if (iframeSrc.contains(".m3u8") || iframeSrc.contains(".mpd") || iframeSrc.contains(".mp4")) {
                    return@withContext ExtractedStreamResult(
                        streamUrl = iframeSrc,
                        headers = mapOf(
                            "User-Agent" to DEFAULT_UA,
                            "Referer" to url
                        )
                    )
                }

                extractFromGenericEmbed(iframeSrc)?.let { return@withContext it }
            }
        } catch (e: Exception) {
            Log.e(TAG, "2Embed extraction error: ${e.message}")
        }
        null
    }

    private suspend fun extractFromVidSrc(url: String): ExtractedStreamResult? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", DEFAULT_UA)
                .header("Referer", url)
                .build()

            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: return@withContext null

            findStreamInHtml(html, url)?.let { return@withContext it }

            val rcpMatcher = Pattern.compile("src: [\"']([^\"']*(?:rcp|prorpm|player)[^\"']*)[\"']", Pattern.CASE_INSENSITIVE).matcher(html)
            if (rcpMatcher.find()) {
                var rcpUrl = rcpMatcher.group(1) ?: ""
                if (rcpUrl.startsWith("//")) rcpUrl = "https:$rcpUrl"
                extractFromGenericEmbed(rcpUrl)?.let { return@withContext it }
            }
        } catch (e: Exception) {
            Log.e(TAG, "VidSrc extraction error: ${e.message}")
        }
        null
    }

    private suspend fun extractFromGenericEmbed(url: String): ExtractedStreamResult? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", DEFAULT_UA)
                .header("Referer", url)
                .build()

            val resp = client.newCall(req).execute()
            val html = resp.body?.string() ?: return@withContext null

            findStreamInHtml(html, url)
        } catch (e: Exception) {
            Log.e(TAG, "Generic embed error: ${e.message}")
            null
        }
    }

    private fun findStreamInHtml(html: String, pageUrl: String): ExtractedStreamResult? {
        val patterns = listOf(
            Pattern.compile("file[\"']?\\s*:\\s*[\"']([^\"']+\\.(?:m3u8|mpd|mp4)[^\"']*)[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("source[\"']?\\s*:\\s*[\"']([^\"']+\\.(?:m3u8|mpd|mp4)[^\"']*)[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("src[\"']?\\s*:\\s*[\"']([^\"']+\\.(?:m3u8|mpd|mp4)[^\"']*)[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("[\"'](https?://[^\"']+\\.(?:m3u8|mpd)[^\"']*)[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("[\"'](https?://[^\"']+/playlist\\.m3u8[^\"']*)[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("[\"'](https?://[^\"']+/manifest\\.mpd[^\"']*)[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("[\"'](https?://[^\"']+/index\\.m3u8[^\"']*)[\"']", Pattern.CASE_INSENSITIVE),
            Pattern.compile("[\"'](https?://[^\"']+/index_web\\.mpd[^\"']*)[\"']", Pattern.CASE_INSENSITIVE)
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(html)
            while (matcher.find()) {
                val found = matcher.group(1) ?: continue
                if (!isAdOrTrackerUrl(found)) {
                    val base = getBaseUrl(pageUrl)
                    return ExtractedStreamResult(
                        streamUrl = found,
                        headers = mapOf(
                            "User-Agent" to DEFAULT_UA,
                            "Referer" to pageUrl,
                            "Origin" to base
                        )
                    )
                }
            }
        }

        return null
    }

    private fun isAdOrTrackerUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("google") ||
                lower.contains("adsterra") ||
                lower.contains("doubleclick") ||
                lower.contains("analytics") ||
                lower.contains("popcash") ||
                lower.contains("syndication") ||
                lower.contains("trailer") ||
                lower.contains("preview")
    }

    private fun getBaseUrl(url: String): String {
        return try {
            val uri = java.net.URI(url)
            "${uri.scheme}://${uri.host}"
        } catch (_: Exception) {
            url
        }
    }
}

data class ExtractedStreamResult(
    val streamUrl: String,
    val headers: Map<String, String> = emptyMap()
)
