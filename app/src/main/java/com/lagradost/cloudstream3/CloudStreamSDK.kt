package com.lagradost.cloudstream3

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// =========================================================================
// CloudStream 3 Base SDK Shims for Dynamic DEX Plugin Execution
// Provides classes and interfaces expected by .cs3 plugin bytecode
// =========================================================================

enum class TvType {
    Movie,
    TvSeries,
    Anime,
    AnimeMovie,
    OVA,
    Cartoon,
    Documentary,
    Live,
    NSFW,
    AsianDrama,
    Others
}

enum class Qualities(val value: Int) {
    Unknown(0),
    P144(144),
    P240(240),
    P360(360),
    P480(480),
    P720(720),
    P1080(1080),
    P2160(2160)
}

data class ExtractorLink(
    val source: String,
    val name: String,
    val url: String,
    val referer: String = "",
    val quality: Int = Qualities.P1080.value,
    val isM3u8: Boolean = url.contains(".m3u8", ignoreCase = true),
    val headers: Map<String, String> = emptyMap()
)

data class SubtitleFile(
    val lang: String,
    val url: String
)

interface SearchResponse {
    val name: String
    val url: String
    val apiName: String
    val type: TvType?
    val posterUrl: String?
    val year: Int?
    val quality: String?
}

data class MovieSearchResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override val type: TvType? = TvType.Movie,
    override val posterUrl: String? = null,
    override val year: Int? = null,
    override val quality: String? = "1080p",
    val id: Int? = null
) : SearchResponse

data class TvSeriesSearchResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override val type: TvType? = TvType.TvSeries,
    override val posterUrl: String? = null,
    override val year: Int? = null,
    override val quality: String? = "HD",
    val id: Int? = null
) : SearchResponse

data class AnimeSearchResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override val type: TvType? = TvType.Anime,
    override val posterUrl: String? = null,
    override val year: Int? = null,
    override val quality: String? = "HD",
    val id: Int? = null
) : SearchResponse

data class HomePageList(
    val name: String,
    val list: List<SearchResponse>
)

data class HomePageResponse(
    val items: List<HomePageList>
)

data class Episode(
    val data: String,
    val name: String? = null,
    val season: Int? = 1,
    val episode: Int? = 1,
    val posterUrl: String? = null,
    val rating: Int? = null,
    val description: String? = null
)

interface LoadResponse {
    val name: String
    val url: String
    val apiName: String
    val type: TvType
    val posterUrl: String?
    val year: Int?
    val plot: String?
    val rating: Int?
    val tags: List<String>?
}

data class MovieLoadResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override val type: TvType = TvType.Movie,
    override val posterUrl: String? = null,
    override val year: Int? = null,
    override val plot: String? = null,
    override val rating: Int? = null,
    override val tags: List<String>? = null,
    val dataUrl: String = url
) : LoadResponse

data class TvSeriesLoadResponse(
    override val name: String,
    override val url: String,
    override val apiName: String,
    override val type: TvType = TvType.TvSeries,
    override val posterUrl: String? = null,
    override val year: Int? = null,
    override val plot: String? = null,
    override val rating: Int? = null,
    override val tags: List<String>? = null,
    val episodes: List<Episode> = emptyList()
) : LoadResponse

// MainAPI Base Class implemented by CloudStream plugins
open class MainAPI {
    open var name: String = "CloudStream Provider"
    open var mainUrl: String = ""
    open var lang: String = "en"
    open var supportedTypes: Set<TvType> = setOf(TvType.Movie, TvType.TvSeries)
    open var hasMainPage: Boolean = true
    open var hasQuickSearch: Boolean = false

    open suspend fun getMainPage(page: Int = 1): HomePageResponse? {
        return null
    }

    open suspend fun search(query: String): List<SearchResponse>? {
        return null
    }

    open suspend fun load(url: String): LoadResponse? {
        return null
    }

    open suspend fun loadLinks(
        data: String,
        isCasting: Boolean = false,
        subtitleCallback: (SubtitleFile) -> Unit = {},
        callback: (ExtractorLink) -> Unit = {}
    ): Boolean {
        return false
    }
}

// ExtractorApi for video hosting resolvers
open class ExtractorApi {
    open var name: String = "Extractor"
    open var mainUrl: String = ""
    open var requiresReferer: Boolean = false

    open suspend fun getUrl(url: String, referer: String? = null): List<ExtractorLink> {
        return emptyList()
    }
}

// Global App HTTP Client helper
object app {
    val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    fun get(url: String, headers: Map<String, String> = emptyMap(), referer: String? = null): Response {
        val reqBuilder = Request.Builder().url(url)
        reqBuilder.header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
        referer?.let { reqBuilder.header("Referer", it) }
        headers.forEach { (k, v) -> reqBuilder.header(k, v) }
        return okHttpClient.newCall(reqBuilder.build()).execute()
    }

    fun post(url: String, headers: Map<String, String> = emptyMap(), referer: String? = null, postData: Map<String, String> = emptyMap()): Response {
        val formBody = okhttp3.FormBody.Builder()
        postData.forEach { (k, v) -> formBody.add(k, v) }
        val reqBuilder = Request.Builder().url(url).post(formBody.build())
        reqBuilder.header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
        referer?.let { reqBuilder.header("Referer", it) }
        headers.forEach { (k, v) -> reqBuilder.header(k, v) }
        return okHttpClient.newCall(reqBuilder.build()).execute()
    }
}
