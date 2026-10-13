package com.example.model

enum class MediaType {
    LIVE_EVENT,
    LIVE_TV,
    MOVIE,
    SERIES
}

data class StreamServer(
    val name: String,
    val url: String
)

typealias MediaServer = StreamServer

data class EpisodeItem(
    val id: String,
    val seriesId: String = "",
    val title: String,
    val seasonNum: Int = 1,
    val episodeNum: Int = 1,
    val streamUrl: String,
    val backupUrl: String? = null,
    val servers: List<StreamServer> = emptyList(),
    val logoUrl: String? = null,
    val overview: String? = null,
    val duration: String? = null,
    val rating: String? = null,
    val releaseDate: String? = null,
    val containerExtension: String = "mp4"
) {
    fun toMediaItem(parentSeries: MediaItem): MediaItem {
        val cleanSeriesId = (parentSeries.seriesId ?: parentSeries.id).removePrefix("xtream_series_").removePrefix("xtream_ep_").trim()
        val cleanServer = parentSeries.xtreamServerUrl?.takeIf { it.isNotBlank() } ?: "http://rgkkw.live:80"
        val cleanUser = parentSeries.xtreamUsername?.takeIf { it.isNotBlank() } ?: "4dfoydR2gZ"
        val cleanPass = parentSeries.xtreamPassword?.takeIf { it.isNotBlank() } ?: "clever3still"
        val ext = containerExtension.ifBlank { "mp4" }
        val finalStreamUrl = if (streamUrl.isNotBlank()) streamUrl else "$cleanServer/series/$cleanUser/$cleanPass/$id.$ext"
        val altServer = if (cleanServer.contains(":80")) cleanServer.replace(":80", "") else "$cleanServer:80"
        val altPlayUrl = "$altServer/series/$cleanUser/$cleanPass/$id.$ext"
        val altExtUrl = if (ext.equals("mp4", ignoreCase = true)) {
            "$cleanServer/series/$cleanUser/$cleanPass/$id.mkv"
        } else {
            "$cleanServer/series/$cleanUser/$cleanPass/$id.mp4"
        }
        val epServers = if (servers.isNotEmpty()) servers else listOf(
            StreamServer("সার্ভার ১ (HD MP4)", finalStreamUrl),
            StreamServer("সার্ভার ২ (বিকল্প পোর্ট)", altPlayUrl),
            StreamServer("সার্ভার ৩ (বিকল্প ফরম্যাট)", altExtUrl)
        )
        val cleanTitle = if (title.isNotBlank()) title else "${parentSeries.title} - S${seasonNum}E${episodeNum}"
        val uniqueEpId = if (id.isNotBlank() && id != "0") "xtream_ep_${id}" else "${parentSeries.id}_s${seasonNum}e${episodeNum}"
        return MediaItem(
            id = uniqueEpId,
            title = cleanTitle,
            category = parentSeries.category,
            type = MediaType.SERIES,
            streamUrl = finalStreamUrl,
            backupUrl = altPlayUrl,
            servers = epServers,
            logoUrl = logoUrl ?: parentSeries.logoUrl,
            description = overview?.takeIf { it.isNotBlank() } ?: parentSeries.description,
            isLive = false,
            rating = rating ?: parentSeries.rating,
            year = releaseDate?.take(4) ?: parentSeries.year,
            seriesId = cleanSeriesId,
            currentSeasonNum = seasonNum,
            currentEpisodeNum = episodeNum,
            seasons = parentSeries.seasons,
            episodes = emptyList(), // Avoid recursive object tree explosion in playlist
            genre = parentSeries.genre,
            cast = parentSeries.cast,
            director = parentSeries.director,
            xtreamServerUrl = cleanServer,
            xtreamUsername = cleanUser,
            xtreamPassword = cleanPass,
            userAgent = parentSeries.userAgent ?: "IPTVSmartersPro"
        )
    }
}

data class SeasonInfo(
    val seasonNumber: Int,
    val name: String,
    val episodeCount: Int = 0,
    val cover: String? = null,
    val overview: String? = null,
    val airDate: String? = null,
    val episodes: List<EpisodeItem> = emptyList()
)

data class XtreamCategory(
    val id: String,
    val name: String,
    val type: String = "live" // "live", "movie", "series"
)

data class MediaItem(
    val id: String,
    val title: String,
    val category: String,
    val type: MediaType,
    val streamUrl: String,
    val backupUrl: String? = null,
    val servers: List<StreamServer> = emptyList(),
    val logoUrl: String? = null,
    val description: String? = null,
    val isLive: Boolean = false,
    val eventTime: String? = null,
    val tournament: String? = null,
    val status: String = "Live Now", // "LIVE NOW", "UPCOMING", "Finished"
    val team1: String? = null,
    val team2: String? = null,
    val team1Logo: String? = null,
    val team2Logo: String? = null,
    val matchTimeFormatted: String? = null, // e.g. "06:30 AM, Aug 13"
    val countdownTargetSeconds: Long? = null, // Remaining seconds or timestamp for live ticking countdown
    val score1: String? = null,
    val score2: String? = null,
    val quality: String = "HD",
    val rating: String? = null,
    val year: String? = null,
    val country: String? = null,
    val userAgent: String? = null,
    val referrer: String? = null,
    val cookie: String? = null,
    val origin: String? = null,
    val customHeaders: Map<String, String>? = null,
    val drmScheme: String? = null,
    val drmLicenseUrl: String? = null,
    val drmLicenseKey: String? = null,
    val drmHeaders: Map<String, String>? = null,
    val manifestType: String? = null,
    val seriesId: String? = null,
    val seasons: List<SeasonInfo> = emptyList(),
    val episodes: List<EpisodeItem> = emptyList(),
    val currentSeasonNum: Int = 1,
    val currentEpisodeNum: Int = 1,
    val genre: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val xtreamServerUrl: String? = null,
    val xtreamUsername: String? = null,
    val xtreamPassword: String? = null,
    val isAdminAdded: Boolean = false,
    val stalkerCmd: String? = null,
    val stalkerPortalUrl: String? = null,
    val stalkerMac: String? = null,
    val stalkerMacAddress: String? = null,
    val isFastChannel: Boolean = false,
    val posterUrl: String? = null,
    val addedTimestamp: Long? = null
) {
    val isSeries: Boolean get() = type == MediaType.SERIES || !seriesId.isNullOrBlank() || seasons.isNotEmpty() || episodes.isNotEmpty() || id.startsWith("xtream_series_") || id.startsWith("series_") || category.contains("JALSHA", ignoreCase = true) || category.contains("SERIAL", ignoreCase = true) || category.contains("DRAMA", ignoreCase = true) || category.contains("SERIES", ignoreCase = true)
    val resolvedSeriesId: String? get() = seriesId?.takeIf { it.isNotBlank() } ?: if (id.startsWith("xtream_series_")) id.removePrefix("xtream_series_") else null
    val isFromAdmin: Boolean get() = isAdminAdded || id.startsWith("sport_") || id.startsWith("match_") || id.startsWith("event_") || id.startsWith("admin_")
    val headers: Map<String, String>? get() = customHeaders

    val movieSortWeight: Long get() {
        if (addedTimestamp != null && addedTimestamp > 0L) {
            return addedTimestamp
        }
        val yr = year?.toIntOrNull() ?: extractYearFromTitle(title)
        if (yr != null && yr in 1900..2099) {
            return (yr - 1970).toLong() * 31536000L
        }
        val sid = id.removePrefix("xtream_vod_").removePrefix("xtream_series_").toLongOrNull()
        if (sid != null) {
            return sid
        }
        return 0L
    }

    private fun extractYearFromTitle(str: String): Int? {
        val regex = Regex("""\b(19\d{2}|20\d{2})\b""")
        val matches = regex.findAll(str).toList()
        return matches.lastOrNull()?.value?.toIntOrNull()
    }
    // Helper to get all available server URLs
    fun getAllServers(): List<StreamServer> {
        val list = mutableListOf<StreamServer>()
        if (servers.isNotEmpty()) {
            list.addAll(servers.filter { it.url.isNotBlank() })
        }
        if (streamUrl.isNotBlank() && list.none { it.url.trim().equals(streamUrl.trim(), ignoreCase = true) }) {
            list.add(0, StreamServer("সার্ভার ১", streamUrl.trim()))
        }
        if (!backupUrl.isNullOrBlank() && 
            !backupUrl.trim().equals(streamUrl.trim(), ignoreCase = true) && 
            list.none { it.url.trim().equals(backupUrl.trim(), ignoreCase = true) }
        ) {
            list.add(StreamServer("সার্ভার ২", backupUrl.trim()))
        }

        // Automatic mirror endpoints for Pixeldrain URLs
        val lower = streamUrl.lowercase()
        if (lower.contains("pixeldrain") || lower.contains("pixeldra.in")) {
            val fileId = streamUrl.substringAfter("/api/file/").substringAfter("/u/").substringBefore("?").substringBefore("/")
            if (fileId.isNotBlank()) {
                val s1 = "https://pixeldrain.dev/api/file/$fileId?download"
                val s2 = "https://pixeldrain.com/api/file/$fileId?download"
                val s3 = "https://pixeldrain.com/api/file/$fileId"
                if (list.none { it.url.equals(s1, ignoreCase = true) }) list.add(StreamServer("Pixeldrain Dev", s1))
                if (list.none { it.url.equals(s2, ignoreCase = true) }) list.add(StreamServer("Pixeldrain Com", s2))
                if (list.none { it.url.equals(s3, ignoreCase = true) }) list.add(StreamServer("Pixeldrain Direct", s3))
            }
        }

        val distinctList = list.distinctBy { it.url.trim() }
        if (distinctList.isEmpty()) {
            return if (streamUrl.isNotBlank()) listOf(StreamServer("সার্ভার ১", streamUrl.trim())) else emptyList()
        }
        return distinctList.mapIndexed { index, server ->
            val num = index + 1
            val rawName = server.name.trim()
            val cleanName = if (rawName.isBlank() || rawName.equals(title.trim(), ignoreCase = true) || rawName.matches(Regex("^(?i)(server|সার্ভার)\\s*\\d*.*"))) {
                "সার্ভার $num"
            } else {
                "সার্ভার $num ($rawName)"
            }
            StreamServer(name = cleanName, url = server.url.trim())
        }
    }
}

data class PlaylistInfo(
    val id: String,
    val title: String,
    val url: String,
    val logoUrl: String? = null,
    val description: String? = null,
    val channelCount: Int = 0,
    val serverUrl: String? = null,
    val username: String? = null,
    val password: String? = null,
    val type: String = "M3U", // "XTREAM" or "M3U"
    val isAdmin: Boolean = false,
    val isReadOnly: Boolean = false
) {
    val isProtected: Boolean get() = isAdmin || isReadOnly
}

data class MovieProvider(
    val id: String,
    val name: String,
    val description: String? = null,
    val iconUrl: String? = null,
    val siteUrl: String = "",
    val searchUrl: String? = null,
    val types: List<String> = listOf("Movie", "Series"), // "Movie", "Series", "Anime", "Live"
    val language: String = "Multi",
    val flag: String? = null,
    val authors: String? = null,
    val version: String? = null,
    val status: String = "Ok",
    val size: String? = null,
    val supported: List<String> = listOf("Movie", "TvSeries"),
    val githubUrl: String? = null,
    val isInstalled: Boolean = true,
    val repoId: String? = null,
    val repoName: String? = null,
    val isCustom: Boolean = false,
    val isEnabled: Boolean = true
) {
    val url: String get() = siteUrl
    val logoUrl: String? get() = iconUrl
    val category: String get() = if (types.isNotEmpty()) types.joinToString(", ") else "Movie & Series"
    val enabled: Boolean get() = isEnabled
}

data class CloudStreamRepo(
    val id: String,
    val name: String,
    val repoUrl: String,
    val description: String? = null,
    val iconUrl: String? = null,
    val providers: List<MovieProvider> = emptyList(),
    val isEnabled: Boolean = true,
    val lastUpdated: Long = System.currentTimeMillis(),
    val manifestVersion: Int = 1
) {
    val url: String get() = repoUrl
    val logoUrl: String? get() = iconUrl
    val enabled: Boolean get() = isEnabled
}

data class AppUpdateInfo(
    val versionCode: Int = 1,
    val versionName: String = "1.0",
    val downloadUrl: String = "",
    val releaseNotes: String = "",
    val isForceUpdate: Boolean = false,
    val minSupportedVersionCode: Int = 1,
    val apkSize: String = "",
    val releaseDate: String = ""
)

data class ActiveUserInfo(
    val id: String,
    val deviceModel: String = "Android Device",
    val brand: String = "Android",
    val appVersion: String = "v2.5.6",
    val versionCode: Int = 28,
    val lastSeen: Long = System.currentTimeMillis(),
    val registeredAt: Long = System.currentTimeMillis(),
    val isOnline: Boolean = true,
    val location: String = "বাংলাদেশ",
    val city: String = "ঢাকা",
    val country: String = "Bangladesh",
    val countryCode: String = "BD",
    val ip: String = "",
    val isp: String = "",
    val networkType: String = "WiFi",
    val currentActivity: String = "ব্রাউজিং"
)

data class LocationTrafficStat(
    val locationName: String,
    val count: Int,
    val percentage: Float,
    val flag: String = "🇧🇩"
)

data class AppUserAnalytics(
    val totalUsers: Int = 1,
    val activeUsers: Int = 1,
    val activeUsersList: List<ActiveUserInfo> = emptyList(),
    val topLocations: List<LocationTrafficStat> = emptyList(),
    val liveLocations: List<LocationTrafficStat> = emptyList(),
    val networkBreakdown: Map<String, Int> = emptyMap(),
    val liveNetworkBreakdown: Map<String, Int> = emptyMap(),
    val lastUpdated: Long = System.currentTimeMillis()
)

data class AppConfigData(
    val liveTvM3uUrl: String = "",
    val sportsM3uUrl: String = "",
    val moviesM3uUrl: String = "",
    val tapmadJsonUrl: String = "",
    val xtreamServerUrl: String = "http://rgkkw.live:80",
    val xtreamUsername: String = "4dfoydR2gZ",
    val xtreamPassword: String = "clever3still"
)

data class XtreamAccount(
    val id: String = "xtream_${System.currentTimeMillis()}",
    val name: String = "Starshare Xtream Server",
    val serverUrl: String = "http://rgkkw.live:80",
    val username: String = "4dfoydR2gZ",
    val password: String = "clever3still",
    val isEnabled: Boolean = true,
    val includeLive: Boolean = true,
    val includeVod: Boolean = true,
    val includeSeries: Boolean = true,
    val logoUrl: String? = null,
    val lastSyncTime: Long = 0L,
    val statusMessage: String? = null
)

data class MatchComment(
    val id: String = "",
    val matchId: String = "",
    val userName: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

data class StalkerPortalAccount(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "",
    val portalUrl: String = "",
    val macAddress: String = "",
    val token: String? = null,
    val isActive: Boolean = true,
    val isEnabled: Boolean = true,
    val includeLive: Boolean = true,
    val includeVod: Boolean = true,
    val includeSeries: Boolean = true,
    val channelCount: Int = 0,
    val movieCount: Int = 0,
    val seriesCount: Int = 0,
    val lastSyncTime: Long = 0L,
    val statusMessage: String? = null
)


