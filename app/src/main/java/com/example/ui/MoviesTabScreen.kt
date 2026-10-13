package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.MediaRepository
import com.example.model.MediaItem
import com.example.model.MediaType
import com.example.model.MovieProvider
import com.example.model.PlaylistInfo
import com.example.model.XtreamAccount
import com.example.ui.components.NafiLogoLoadingView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Representation of an Admin-configured Xtream server, Playlist, or OTT Platform Hub
 * displayed in circular icon style matching IMG_20261011_152459.jpg
 */
enum class HubCategory {
    XTREAM, PLAYLIST, OTT_BRAND, PROVIDER
}

data class MovieHubItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val hubCategory: HubCategory,
    val logoUrl: String? = null,
    val brandTag: String = "",
    val serverHost: String = "",
    val xtreamAccount: XtreamAccount? = null,
    val playlist: PlaylistInfo? = null,
    val provider: MovieProvider? = null,
    val gradientColors: List<Color> = listOf(Color(0xFF1E1B4B), Color(0xFF0F172A)),
    val accentColor: Color = Color(0xFF00E5FF)
)

/**
 * Strict Gatekeeper: Guarantees that absolutely NO Live TV channel, Sports event,
 * or TV broadcast ever appears in the Movies section. ONLY movies and web series are allowed.
 */
fun isPureMovieOrSeries(item: MediaItem): Boolean {
    if (item.isLive) return false
    if (item.type == MediaType.LIVE_TV || item.type == MediaType.LIVE_EVENT) return false

    val cat = item.category.lowercase()
    val liveKeywords = listOf(
        "live tv", "sports", "cricket", "football", "news", "bangladesh tv", "bd tv",
        "indian tv", "telecom", "channel", "live-event", "live matches", "sport"
    )
    if (liveKeywords.any { cat.contains(it) }) return false

    val title = item.title.lowercase()
    if (title.contains(" vs ") || title.contains(" v ") || (title.contains(" live ") && !title.contains("alive"))) return false

    val url = item.streamUrl.lowercase()
    if (url.contains("/live/") && !url.contains("/movie/") && !url.contains("/series/")) return false
    if (url.endsWith(".m3u8") && (cat.contains("tv") || title.contains("tv") || cat.contains("channel"))) {
        if (!url.contains("movie") && !url.contains("vod") && !url.contains("series")) return false
    }

    return true
}

@Composable
fun MoviesTabScreen(
    movies: List<MediaItem>,
    favoriteIds: Set<String>,
    isLoading: Boolean = false,
    isTvMode: Boolean = false,
    isAdultHidden: Boolean = true,
    repository: MediaRepository? = null,
    onSelectMedia: (MediaItem) -> Unit,
    onSelectMediaWithPlaylist: ((MediaItem, List<MediaItem>) -> Unit)? = null,
    onToggleFavorite: (String) -> Unit,
    onOpenOfflineDownloads: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedTypeFilter by remember { mutableStateOf("ALL") }
    var isGridViewActive by remember { mutableStateOf(false) }

    // Dialog state
    var selectedSeriesForDialog by remember { mutableStateOf<MediaItem?>(null) }
    var selectedMovieForDetails by remember { mutableStateOf<MediaItem?>(null) }

    // Active Selected Server or OTT Platform Hub
    var selectedHub by remember { mutableStateOf<MovieHubItem?>(null) }
    var isHubLoading by remember { mutableStateOf(false) }
    val hubMoviesCache = remember { mutableStateMapOf<String, List<MediaItem>>() }

    // BackHandler: If inside a selected server, back press pops back to All Movies showcase
    BackHandler(enabled = selectedHub != null) {
        selectedHub = null
        selectedCategory = "All"
        searchQuery = ""
    }

    val handleItemSelect: (MediaItem) -> Unit = { item ->
        if ((item.isSeries || item.type == MediaType.SERIES) && repository != null) {
            selectedSeriesForDialog = item
        } else {
            onSelectMedia(item)
        }
    }

    // Build the list of Admin-added Xtream Servers, Playlists, and OTT Platforms
    // Only includes what was added from the Admin Panel (No extra dummy demos like unconfigured Hotstar/Netflix)
    val adminHubs = remember(repository) {
        val hubs = mutableListOf<MovieHubItem>()

        if (repository != null) {
            // 1. Xtream Codes Accounts configured in Admin Panel
            val xtreamAccounts = repository.getXtreamAccounts().filter { it.isEnabled }
            val xtreamGradients = listOf(
                listOf(Color(0xFF0F766E), Color(0xFF042F2E)), // Emerald/Cyan
                listOf(Color(0xFF1D4ED8), Color(0xFF0F172A)), // Royal Blue
                listOf(Color(0xFF7C3AED), Color(0xFF3B0764)), // Purple/Violet
                listOf(Color(0xFFBE123C), Color(0xFF4C0519))  // Crimson
            )
            xtreamAccounts.forEachIndexed { idx, acc ->
                val grad = xtreamGradients[idx % xtreamGradients.size]
                val cleanHost = acc.serverUrl.removePrefix("http://").removePrefix("https://").substringBefore("/")
                val nameLower = acc.name.lowercase()
                val logo = acc.logoUrl?.takeIf { it.isNotBlank() } ?: when {
                    nameLower.contains("netflix") -> "https://images.ctfassets.net/y2ske730sjqp/821Wg4N9hJD8vs5FBIO3ih/9e630327e04e2e0a9dac340d97a12930/Netflix_Symbol_PMS.png"
                    nameLower.contains("hotstar") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1e/Disney%2B_Hotstar_logo.svg/320px-Disney%2B_Hotstar_logo.svg.png"
                    nameLower.contains("sony") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/5/5a/Sony_LIV_logo.svg/320px-Sony_LIV_logo.svg.png"
                    nameLower.contains("zee") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/5/5a/Zee5_Official_logo.svg/320px-Zee5_Official_logo.svg.png"
                    nameLower.contains("prime") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/1/11/Amazon_Prime_Video_logo.svg/320px-Amazon_Prime_Video_logo.svg.png"
                    nameLower.contains("hoichoi") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/0/00/Hoichoi_logo.svg/320px-Hoichoi_logo.svg.png"
                    nameLower.contains("chorki") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d7/Chorki_logo.png/320px-Chorki_logo.png"
                    else -> null
                }
                val accent = when {
                    nameLower.contains("netflix") -> Color(0xFFE50914)
                    nameLower.contains("hotstar") -> Color(0xFF38BDF8)
                    nameLower.contains("sony") -> Color(0xFFC084FC)
                    nameLower.contains("zee") -> Color(0xFFA855F7)
                    nameLower.contains("prime") -> Color(0xFF00A8E1)
                    nameLower.contains("hoichoi") -> Color(0xFFEF4444)
                    nameLower.contains("chorki") -> Color(0xFFF59E0B)
                    else -> Color(0xFF06B6D4)
                }
                hubs.add(
                    MovieHubItem(
                        id = acc.id,
                        title = acc.name.ifBlank { "Xtream Server ${idx + 1}" },
                        subtitle = "Xtream Codes • VOD & Series",
                        hubCategory = HubCategory.XTREAM,
                        serverHost = cleanHost,
                        xtreamAccount = acc,
                        logoUrl = logo,
                        gradientColors = grad,
                        accentColor = accent
                    )
                )
            }

            // 2. Custom & Admin Movie Playlists configured in Admin Panel
            val customPlaylists = repository.getCustomPlaylists()
            val plGradients = listOf(
                listOf(Color(0xFFC2410C), Color(0xFF431407)), // Amber/Orange
                listOf(Color(0xFF0369A1), Color(0xFF082F49)), // Sky Blue
                listOf(Color(0xFF4338CA), Color(0xFF1E1B4B))  // Indigo
            )
            customPlaylists.forEachIndexed { idx, pl ->
                val grad = plGradients[idx % plGradients.size]
                val cleanHost = if (!pl.serverUrl.isNullOrBlank()) {
                    pl.serverUrl.removePrefix("http://").removePrefix("https://").substringBefore("/")
                } else if (pl.url.isNotBlank()) {
                    pl.url.removePrefix("http://").removePrefix("https://").substringBefore("/")
                } else "Playlist"
                val nameLower = pl.title.lowercase()
                val logo = pl.logoUrl?.takeIf { it.isNotBlank() } ?: when {
                    nameLower.contains("netflix") -> "https://images.ctfassets.net/y2ske730sjqp/821Wg4N9hJD8vs5FBIO3ih/9e630327e04e2e0a9dac340d97a12930/Netflix_Symbol_PMS.png"
                    nameLower.contains("hotstar") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1e/Disney%2B_Hotstar_logo.svg/320px-Disney%2B_Hotstar_logo.svg.png"
                    nameLower.contains("sony") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/5/5a/Sony_LIV_logo.svg/320px-Sony_LIV_logo.svg.png"
                    nameLower.contains("zee") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/5/5a/Zee5_Official_logo.svg/320px-Zee5_Official_logo.svg.png"
                    nameLower.contains("prime") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/1/11/Amazon_Prime_Video_logo.svg/320px-Amazon_Prime_Video_logo.svg.png"
                    nameLower.contains("hoichoi") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/0/00/Hoichoi_logo.svg/320px-Hoichoi_logo.svg.png"
                    nameLower.contains("chorki") -> "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d7/Chorki_logo.png/320px-Chorki_logo.png"
                    else -> null
                }
                val accent = when {
                    nameLower.contains("netflix") -> Color(0xFFE50914)
                    nameLower.contains("hotstar") -> Color(0xFF38BDF8)
                    nameLower.contains("sony") -> Color(0xFFC084FC)
                    nameLower.contains("zee") -> Color(0xFFA855F7)
                    nameLower.contains("prime") -> Color(0xFF00A8E1)
                    nameLower.contains("hoichoi") -> Color(0xFFEF4444)
                    nameLower.contains("chorki") -> Color(0xFFF59E0B)
                    else -> Color(0xFFF59E0B)
                }
                hubs.add(
                    MovieHubItem(
                        id = pl.id,
                        title = pl.title.ifBlank { "Playlist ${idx + 1}" },
                        subtitle = if (pl.type.equals("XTREAM", ignoreCase = true)) "Xtream Playlist" else "M3U Movie Playlist",
                        hubCategory = HubCategory.PLAYLIST,
                        serverHost = cleanHost,
                        playlist = pl,
                        logoUrl = logo,
                        gradientColors = grad,
                        accentColor = accent
                    )
                )
            }

            // 3. Saved Movie M3U URL if set in Admin panel
            val savedMoviesM3u = repository.getSavedMoviesM3uUrl()
            if (savedMoviesM3u.isNotBlank() && hubs.none { it.serverHost.contains("movies.json") || it.id == "saved_movies_m3u" }) {
                hubs.add(
                    MovieHubItem(
                        id = "saved_movies_m3u",
                        title = "NAFI OTT VOD",
                        subtitle = "Cloud Movie Library",
                        hubCategory = HubCategory.PLAYLIST,
                        serverHost = savedMoviesM3u.substringBefore("?").takeLast(25),
                        playlist = PlaylistInfo(
                            id = "saved_movies_m3u",
                            title = "NAFI OTT VOD",
                            url = savedMoviesM3u,
                            type = "M3U",
                            isAdmin = true
                        ),
                        gradientColors = listOf(Color(0xFF0284C7), Color(0xFF0B132B)),
                        accentColor = Color(0xFF00E5FF)
                    )
                )
            }
        }

        hubs.distinctBy { it.id }
    }

    // Load Hub Movies on-demand when user clicks an Xtream Server, Playlist, or OTT Platform Hub
    LaunchedEffect(selectedHub) {
        val hub = selectedHub ?: return@LaunchedEffect
        if (!hubMoviesCache.containsKey(hub.id) && repository != null) {
            isHubLoading = true
            withContext(Dispatchers.IO) {
                try {
                    val hubMoviesList = when (hub.hubCategory) {
                        HubCategory.XTREAM -> {
                            hub.xtreamAccount?.let { repository.fetchXtreamAccountMovies(it) } ?: emptyList()
                        }
                        HubCategory.PLAYLIST -> {
                            hub.playlist?.let { repository.fetchPlaylistMoviesOnly(it) } ?: emptyList()
                        }
                        HubCategory.OTT_BRAND -> {
                            // 1. Check if Admin added any custom playlist or Xtream account for this brand (e.g. Sony LIV, Hotstar, Netflix)
                            val brandTag = hub.brandTag.lowercase()
                            val hubTitleLower = hub.title.lowercase()
                            val adminPl = repository.getCustomPlaylists().firstOrNull {
                                val t = it.title.lowercase()
                                t.contains(brandTag) || t.contains(hubTitleLower) ||
                                        (brandTag.contains("sony") && t.contains("sony")) ||
                                        (brandTag.contains("zee") && t.contains("zee"))
                            }
                            val adminXtream = repository.getXtreamAccounts().firstOrNull {
                                val t = it.name.lowercase()
                                t.contains(brandTag) || t.contains(hubTitleLower) ||
                                        (brandTag.contains("sony") && t.contains("sony"))
                            }
                            val adminMovies = when {
                                adminPl != null -> repository.fetchPlaylistMoviesOnly(adminPl)
                                adminXtream != null -> repository.fetchXtreamAccountMovies(adminXtream)
                                else -> emptyList()
                            }

                            // 2. Filter current movies matching this OTT Brand tag
                            val allAvailable = (movies.ifEmpty { repository.getCachedMoviesList() })
                            val taggedMovies = allAvailable.filter { item ->
                                val cat = item.category.lowercase()
                                val title = item.title.lowercase()
                                val desc = (item.description ?: "").lowercase()
                                cat.contains(brandTag) || title.contains(brandTag) || desc.contains(brandTag)
                            }
                            val combined = (adminMovies + taggedMovies).distinctBy { it.id }
                            if (combined.isNotEmpty()) {
                                combined
                            } else {
                                when (hub.id) {
                                    "ott_hoichoi", "ott_chorki" -> allAvailable.filter {
                                        it.category.lowercase().contains("bangla") || it.title.lowercase().contains("bangla")
                                    }
                                    "ott_sonyliv", "ott_zee5" -> allAvailable.filter {
                                        it.category.lowercase().contains("hindi") || it.category.lowercase().contains("bollywood") || it.category.lowercase().contains("sony")
                                    }
                                    else -> allAvailable
                                }
                            }
                        }
                        HubCategory.PROVIDER -> {
                            emptyList()
                        }
                    }
                    val strictlyPure = hubMoviesList.filter { isPureMovieOrSeries(it) }
                    withContext(Dispatchers.Main) {
                        hubMoviesCache[hub.id] = strictlyPure
                        isHubLoading = false
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    withContext(Dispatchers.Main) {
                        hubMoviesCache[hub.id] = emptyList()
                        isHubLoading = false
                    }
                }
            }
        }
    }

    // The active source of movies: either the selected Hub's movies, or the general movies list
    val currentActiveSourceMovies = remember(selectedHub, hubMoviesCache, movies, repository) {
        val rawList = if (selectedHub != null) {
            hubMoviesCache[selectedHub!!.id] ?: repository?.getCachedXtreamAccountMovies(selectedHub!!.id) ?: repository?.getCachedPlaylistMovies(selectedHub!!.id) ?: emptyList()
        } else {
            if (movies.isNotEmpty()) movies else repository?.getInitialMoviesSeries() ?: repository?.getDefaultBuiltinMovies() ?: emptyList()
        }
        // STRICT FILTER: Absolutely NO Live TV!
        rawList.filter { isPureMovieOrSeries(it) }.map {
            if (it.type != MediaType.SERIES && !it.isSeries) it.copy(type = MediaType.MOVIE, isLive = false) else it.copy(isLive = false)
        }.distinctBy { it.id }.sortedByDescending { it.movieSortWeight }
    }

    // Filter adult content and type (Movie / Series)
    var preparedMovies by remember { mutableStateOf(currentActiveSourceMovies) }
    var preparedCategorizedMovies by remember { mutableStateOf<List<Pair<String, List<MediaItem>>>>(emptyList()) }
    var preparedCategories by remember { mutableStateOf<List<String>>(listOf("All", "ডাউনলোডসমূহ")) }
    var preparedFeaturedMovies by remember { mutableStateOf<List<MediaItem>>(emptyList()) }

    val priorityList = remember {
        listOf(
            "BANGLA (2026)", "BANGLA (2025)", "BANGLA (2024)", "Bangla Movies 2026", "Bangla Movies 2025", "Bangla Movies 2024", "Bangla Movies", "BANGLA",
            "STAR JALSHA", "ZEE BANGLA", "SUN BANGLA", "COLORS BANGLA", "HOICHOI", "Chorki/Bangla",
            "HUM TV", "PAKISTANI DRAMA", "HINDI TV SERIES", "STAR PLUS", "STAR BHARAT",
            "COLORS HINDI", "ZEE TV", "NETFLIX", "AMAZON PRIME", "DISNEY+HOTSTAR", "SONY LIV"
        )
    }

    LaunchedEffect(currentActiveSourceMovies, isAdultHidden, selectedTypeFilter, repository) {
        withContext(Dispatchers.Default) {
            val shouldFilterAdult = isAdultHidden || (repository?.isAdultContentHidden() == true)
            val adultFiltered = if (shouldFilterAdult && repository != null) {
                val hiddenIds = repository.getHiddenItemIds()
                val hiddenKeywords = repository.getHiddenKeywords().map { it.lowercase() }
                currentActiveSourceMovies.filterNot { m ->
                    if (repository.isAdultCategory(m.category) || repository.isAdultMedia(m) || repository.isMediaHidden(m)) return@filterNot true
                    if (hiddenIds.contains(m.id)) return@filterNot true
                    if (hiddenKeywords.isNotEmpty()) {
                        val txt = "${m.title} ${m.category} ${m.genre ?: ""} ${m.description ?: ""}".lowercase()
                        if (hiddenKeywords.any { txt.contains(it) }) return@filterNot true
                    }
                    false
                }
            } else if (shouldFilterAdult) {
                val adultWords = listOf("adult", "adults", "18+", "18 +", "xxx", "nsfw", "erotic", "porn", "sex", "sensual", "mature", "ullu", "kooku", "primeshots", "rabbit", "besharams", "hotx")
                currentActiveSourceMovies.filterNot { m ->
                    val txt = "${m.title} ${m.category} ${m.genre ?: ""}".lowercase()
                    adultWords.any { txt.contains(it) }
                }
            } else {
                currentActiveSourceMovies
            }

            val typeFiltered = when (selectedTypeFilter) {
                "MOVIE" -> adultFiltered.filter { !it.isSeries && it.type != MediaType.SERIES }
                "SERIES" -> adultFiltered.filter { it.isSeries || it.type == MediaType.SERIES }
                else -> adultFiltered
            }.sortedByDescending { it.movieSortWeight }

            // Group into Categories
            val groupedMap = LinkedHashMap<String, MutableList<MediaItem>>()
            for (m in typeFiltered) {
                val cat = m.category.trim()
                if (cat.isNotBlank() && !cat.equals("Unknown", ignoreCase = true)) {
                    groupedMap.getOrPut(cat) { mutableListOf() }.add(m)
                }
            }
            val catPairs = groupedMap.map { (cat, list) ->
                cat to list.distinctBy { it.id }.sortedByDescending { it.movieSortWeight }
            }

            val sortedCatPairs = catPairs.sortedWith(
                compareBy<Pair<String, List<MediaItem>>> { pair ->
                    val cat = pair.first
                    val idx = priorityList.indexOfFirst { cat.contains(it, ignoreCase = true) || it.contains(cat, ignoreCase = true) }
                    if (idx != -1) idx else 999
                }.thenByDescending { pair ->
                    pair.second.firstOrNull()?.movieSortWeight ?: 0L
                }
            )

            val uniqueCats = sortedCatPairs.map { it.first }
            val highPriority = uniqueCats.filter { cat -> priorityList.any { cat.contains(it, ignoreCase = true) || it.contains(cat, ignoreCase = true) } }
            val others = uniqueCats.filterNot { cat -> priorityList.any { cat.contains(it, ignoreCase = true) || it.contains(cat, ignoreCase = true) } }
            val allCats = listOf("All", "ডাউনলোডসমূহ") + highPriority + others

            val withLogos = typeFiltered.filter { !it.logoUrl.isNullOrBlank() }.sortedByDescending { it.movieSortWeight }
            val featured = if (withLogos.isNotEmpty()) withLogos.take(8) else typeFiltered.take(6)

            withContext(Dispatchers.Main) {
                preparedMovies = typeFiltered
                preparedCategories = allCats
                preparedCategorizedMovies = sortedCatPairs
                preparedFeaturedMovies = featured
            }
        }
    }

    val categories = preparedCategories
    val featuredMovies = preparedFeaturedMovies
    val categorizedMovies = preparedCategorizedMovies

    // Filtered movies when search query or category filter is active
    val filteredMovies = remember(preparedMovies, searchQuery, selectedCategory, favoriteIds) {
        if (selectedCategory == "ডাউনলোডসমূহ") {
            emptyList()
        } else {
            preparedMovies.filter { movie ->
                val matchesSearch = if (searchQuery.isBlank()) true else {
                    movie.title.contains(searchQuery, ignoreCase = true) ||
                            movie.category.contains(searchQuery, ignoreCase = true) ||
                            (movie.description != null && movie.description.contains(searchQuery, ignoreCase = true)) ||
                            (movie.cast != null && movie.cast.contains(searchQuery, ignoreCase = true)) ||
                            (movie.genre != null && movie.genre.contains(searchQuery, ignoreCase = true))
                }
                val matchesCategory = when (selectedCategory) {
                    "All" -> true
                    "❤️ Favorites" -> favoriteIds.contains(movie.id)
                    else -> movie.category.trim().equals(selectedCategory.trim(), ignoreCase = true)
                }
                matchesSearch && matchesCategory
            }.distinctBy { it.id }.sortedByDescending { it.movieSortWeight }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF020617))
    ) {
        // -------------------------------------------------------------
        // 1. TOP HEADER: SEARCH BAR + DOWNLOAD + VIEW MODE TOGGLE
        // -------------------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        if (selectedHub != null) "খুঁজুন: ${selectedHub!!.title}..." else "মুভি ও সিরিজ খুঁজুন (যেমন: Jawan, Toofan, Leo)",
                        color = Color(0xFF64748B),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear",
                                tint = Color(0xFF94A3B8)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00E5FF),
                    unfocusedBorderColor = Color(0xFF1E293B),
                    focusedContainerColor = Color(0xFF0F172A),
                    unfocusedContainerColor = Color(0xFF0F172A),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp)
            )

            // Grid / Shelves toggle
            IconButton(
                onClick = { isGridViewActive = !isGridViewActive },
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0F172A))
            ) {
                Icon(
                    imageVector = if (isGridViewActive) Icons.Rounded.ViewCarousel else Icons.Rounded.GridView,
                    contentDescription = "Toggle View",
                    tint = if (isGridViewActive) Color(0xFF00E5FF) else Color(0xFF94A3B8)
                )
            }

            // Offline Downloads Button
            IconButton(
                onClick = onOpenOfflineDownloads,
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0F172A))
            ) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = "Downloads",
                    tint = Color(0xFF10B981)
                )
            }
        }

        // -------------------------------------------------------------
        // 2. HORIZONTAL SCROLLABLE OTT PLATFORMS CIRCULAR ROW (ডানে-বামে স্ক্রোল)
        // Only shown when Admin has configured movie servers, playlists or OTT platforms
        // -------------------------------------------------------------
        if (adminHubs.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // "সব" (All Platforms) circular icon
                    item(key = "all_ott_platforms") {
                        val isAllSelected = selectedHub == null
                        OttCircularBrandIcon(
                            title = "সকল",
                            logoUrl = null,
                            isSelected = isAllSelected,
                            accentColor = Color(0xFF00E5FF),
                            isTvMode = isTvMode,
                            isAllIcon = true,
                            onClick = {
                                selectedHub = null
                                selectedCategory = "All"
                            }
                        )
                    }

                    // Dynamic OTT Platforms & Movie Servers (Only Admin Configured)
                    items(adminHubs, key = { it.id }) { hub ->
                        val isSelected = selectedHub?.id == hub.id
                        OttCircularBrandIcon(
                            title = hub.title,
                            logoUrl = hub.logoUrl,
                            isSelected = isSelected,
                            accentColor = hub.accentColor,
                            isTvMode = isTvMode,
                            isAllIcon = false,
                            onClick = {
                                selectedHub = if (selectedHub?.id == hub.id) null else hub
                                selectedCategory = "All"
                            }
                        )
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // 3. ACTIVE HUB TOP INDICATOR BANNER (Visible when user clicked a Hub)
        // -------------------------------------------------------------
        AnimatedVisibility(visible = selectedHub != null) {
            if (selectedHub != null) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF0F172A),
                    border = BorderStroke(1.dp, selectedHub!!.accentColor.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF10B981))
                            )
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = selectedHub!!.title,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = selectedHub!!.accentColor.copy(alpha = 0.25f),
                                        border = BorderStroke(0.5.dp, selectedHub!!.accentColor)
                                    ) {
                                        Text(
                                            text = when (selectedHub!!.hubCategory) {
                                                HubCategory.XTREAM -> "XTREAM"
                                                HubCategory.PLAYLIST -> "PLAYLIST"
                                                HubCategory.OTT_BRAND -> "OTT HUB"
                                                else -> "HUB"
                                            },
                                            color = selectedHub!!.accentColor,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = if (isHubLoading) "সার্ভার থেকে মুভি সিঙ্ক হচ্ছে..." else "${preparedMovies.size} টি মুভি ও সিরিজ প্রস্তুত",
                                    color = if (isHubLoading) Color(0xFFF59E0B) else Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Back to all hubs button
                        Button(
                            onClick = {
                                selectedHub = null
                                selectedCategory = "All"
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("সকল প্ল্যাটফর্ম", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // 4. TYPE FILTER CHIPS: ALL / MOVIES / SERIES
        // -------------------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val typeOptions = listOf(
                "ALL" to "সকল কনটেন্ট",
                "MOVIE" to "🎬 শুধুমাত্র মুভি",
                "SERIES" to "🍿 ওয়েব সিরিজ"
            )
            typeOptions.forEach { (typeKey, typeLabel) ->
                val isSelected = selectedTypeFilter == typeKey
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) Color(0xFF0284C7) else Color(0xFF0F172A),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) Color(0xFF00E5FF) else Color(0xFF1E293B)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            selectedTypeFilter = typeKey
                            selectedCategory = "All"
                        }
                ) {
                    Text(
                        text = typeLabel,
                        color = if (isSelected) Color.White else Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
            }
        }

        // -------------------------------------------------------------
        // 5. CATEGORY FILTER CHIPS ROW (Horizontal Scroll)
        // -------------------------------------------------------------
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 4.dp)
        ) {
            items(categories) { cat ->
                val isSelected = selectedCategory == cat
                val isDownloadTab = cat == "ডাউনলোডসমূহ"

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = when {
                        isDownloadTab -> Color(0xFF065F46)
                        isSelected -> Color(0xFF00E5FF)
                        else -> Color(0xFF0F172A)
                    },
                    border = BorderStroke(
                        1.dp,
                        when {
                            isDownloadTab -> Color(0xFF10B981)
                            isSelected -> Color(0xFF00E5FF)
                            else -> Color(0xFF1E293B)
                        }
                    ),
                    modifier = Modifier
                        .clickable {
                            if (isDownloadTab) {
                                onOpenOfflineDownloads()
                            } else {
                                selectedCategory = cat
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (isDownloadTab) {
                            Icon(Icons.Rounded.CloudDone, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(13.dp))
                        }
                        Text(
                            text = cat,
                            color = if (isSelected && !isDownloadTab) Color.Black else Color.White,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected || isDownloadTab) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // 6. MAIN CONTENT AREA (Netflix / Disney+ Hotstar UI)
        // -------------------------------------------------------------
        if (isHubLoading) {
            NafiLogoLoadingView(
                title = "${selectedHub?.title ?: "সার্ভার"} থেকে মুভি লোড হচ্ছে...",
                subtitle = "অনুগ্রহ করে অপেক্ষা করুন, হাই-স্পিড VOD ডাটা সিঙ্ক হচ্ছে...",
                modifier = Modifier.fillMaxSize()
            )
        } else if (preparedMovies.isEmpty() && selectedCategory == "All" && searchQuery.isBlank() && !isLoading && selectedHub != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Movie,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(54.dp)
                    )
                    Text(
                        text = "${selectedHub!!.title} এ কোনো মুভি পাওয়া যায়নি",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "এডমিন প্যানেল থেকে এই সার্ভারের ক্রেডেনশিয়াল বা প্লেলিস্ট চেক করুন।",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                    Button(
                        onClick = { selectedHub = null },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("সকল ওটিটি সার্ভারে ফিরে যান", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else if (searchQuery.isBlank() && selectedCategory == "All" && !isGridViewActive) {
            // =========================================================
            // NETFLIX / DISNEY+ HOTSTAR HOME BROWSING VIEW
            // =========================================================
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // -----------------------------------------------------
                // (A) NETFLIX HERO SPOTLIGHT BILLBOARD (Featured Banner)
                // -----------------------------------------------------
                if (featuredMovies.isNotEmpty() && selectedHub == null) {
                    item {
                        val pagerState = rememberPagerState(pageCount = { featuredMovies.size })

                        LaunchedEffect(pagerState.pageCount) {
                            if (pagerState.pageCount > 1) {
                                while (true) {
                                    kotlinx.coroutines.delay(6500L)
                                    if (!pagerState.isScrollInProgress) {
                                        val nextPage = (pagerState.currentPage + 1) % pagerState.pageCount
                                        pagerState.animateScrollToPage(nextPage)
                                    }
                                }
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(230.dp)
                            ) { page ->
                                val currentMovie = featuredMovies[page]
                                var isBannerFocused by remember { mutableStateOf(false) }

                                Surface(
                                    shape = RoundedCornerShape(18.dp),
                                    color = Color(0xFF0F172A),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isTvMode && isBannerFocused) Color(0xFFFFD600) else Color(0xFF1E293B)
                                    ),
                                    shadowElevation = 4.dp,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .onFocusChanged { isBannerFocused = it.isFocused }
                                        .focusable()
                                        .clickable { handleItemSelect(currentMovie) }
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        // Backdrop Poster Image
                                        if (!currentMovie.logoUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = currentMovie.logoUrl,
                                                contentDescription = currentMovie.title,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(
                                                        Brush.linearGradient(
                                                            listOf(Color(0xFF1E1B4B), Color(0xFF0F172A))
                                                        )
                                                    )
                                            )
                                        }

                                        // Dark Gradient Cinematic Vignette (Netflix Style)
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Brush.verticalGradient(
                                                        colors = listOf(
                                                            Color.Transparent,
                                                            Color(0xFF020617).copy(alpha = 0.4f),
                                                            Color(0xFF020617).copy(alpha = 0.95f)
                                                        )
                                                    )
                                                )
                                        )

                                        // Badge: TOP 10 / TRENDING
                                        Surface(
                                            shape = RoundedCornerShape(bottomEnd = 10.dp),
                                            color = Color(0xFFE50914),
                                            modifier = Modifier.align(Alignment.TopStart)
                                        ) {
                                            Text(
                                                text = if (currentMovie.isSeries) "🔥 শীর্ষ ট্রেন্ডিং সিরিজ" else "🔥 শীর্ষ ব্লকবাস্টার মুভি",
                                                color = Color.White,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                            )
                                        }

                                        // Movie Info at Bottom
                                        Column(
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(14.dp)
                                        ) {
                                            Text(
                                                text = currentMovie.title,
                                                color = Color.White,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Black,
                                                maxLines = 1,
                                                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                                            )

                                            Spacer(modifier = Modifier.height(2.dp))

                                            // Metadata Row: Rating, Year, Category
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Rounded.Star, contentDescription = null, tint = Color(0xFFFFD600), modifier = Modifier.size(13.dp))
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text(
                                                        text = currentMovie.rating ?: "8.5",
                                                        color = Color(0xFFFFD600),
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }

                                                Text(
                                                    text = "•",
                                                    color = Color(0xFF64748B),
                                                    fontSize = 11.sp
                                                )

                                                Text(
                                                    text = currentMovie.year ?: "2026",
                                                    color = Color(0xFFCBD5E1),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Medium
                                                )

                                                Text(
                                                    text = "•",
                                                    color = Color(0xFF64748B),
                                                    fontSize = 11.sp
                                                )

                                                Text(
                                                    text = currentMovie.category,
                                                    color = Color(0xFF38BDF8),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))

                                            // Action Buttons (Play Now + Details)
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Button(
                                                    onClick = { handleItemSelect(currentMovie) },
                                                    shape = RoundedCornerShape(10.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                                    modifier = Modifier.height(34.dp)
                                                ) {
                                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("প্লে করুন", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                }

                                                Button(
                                                    onClick = { selectedMovieForDetails = currentMovie },
                                                    shape = RoundedCornerShape(10.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.9f)),
                                                    border = BorderStroke(1.dp, Color(0xFF334155)),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                    modifier = Modifier.height(34.dp)
                                                ) {
                                                    Icon(Icons.Rounded.Info, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("বিবরণ", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp)
                                                }

                                                IconButton(
                                                    onClick = { onToggleFavorite(currentMovie.id) },
                                                    modifier = Modifier.size(34.dp)
                                                ) {
                                                    val isFav = favoriteIds.contains(currentMovie.id)
                                                    Icon(
                                                        imageVector = if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                                        contentDescription = "Favorite",
                                                        tint = if (isFav) Color(0xFFEF4444) else Color.White,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Carousel Dots Indicator
                            if (featuredMovies.size > 1) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    repeat(featuredMovies.size) { index ->
                                        val isSelected = pagerState.currentPage == index
                                        Box(
                                            modifier = Modifier
                                                .padding(horizontal = 3.dp)
                                                .height(4.dp)
                                                .width(if (isSelected) 18.dp else 5.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(if (isSelected) Color(0xFF00E5FF) else Color(0xFF475569).copy(alpha = 0.6f))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // -----------------------------------------------------
                // (B) CATEGORIZED HORIZONTAL CAROUSELS (Netflix Shelves)
                // -----------------------------------------------------
                items(categorizedMovies, key = { it.first }) { (categoryName, catMovies) ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Section Header: "🎬 BANGLA (2026)" ----- "সব (15)"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "🎬 $categoryName",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.5.sp
                                )
                            }

                            Text(
                                text = "সব (${catMovies.size})",
                                color = Color(0xFF00E5FF),
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable {
                                        selectedCategory = categoryName
                                        isGridViewActive = true
                                    }
                                    .padding(vertical = 4.dp, horizontal = 6.dp)
                            )
                        }

                        // Horizontal Poster Row
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(catMovies, key = { it.id }) { movie ->
                                MoviePosterCard(
                                    movie = movie,
                                    isFav = favoriteIds.contains(movie.id),
                                    isTvMode = isTvMode,
                                    onSelect = { handleItemSelect(movie) },
                                    onToggleFav = { onToggleFavorite(movie.id) }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // =========================================================
            // FILTERED, SEARCH, OR DEDICATED GRID VIEW
            // =========================================================
            if (filteredMovies.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Movie,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "কোনো মুভি বা সিরিজ পাওয়া যায়নি",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "অন্য কি-ওয়ার্ড দিয়ে সার্চ করুন অথবা ক্যাটাগরি ফিল্টার পরিবর্তন করুন।",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(if (isTvMode) 5 else 3),
                    contentPadding = PaddingValues(horizontal = if (isTvMode) 14.dp else 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (isTvMode) 12.dp else 8.dp),
                    verticalArrangement = Arrangement.spacedBy(if (isTvMode) 14.dp else 10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredMovies, key = { it.id }) { movie ->
                        MoviePosterCard(
                            movie = movie,
                            isFav = favoriteIds.contains(movie.id),
                            isTvMode = isTvMode,
                            onSelect = { handleItemSelect(movie) },
                            onToggleFav = { onToggleFavorite(movie.id) }
                        )
                    }
                }
            }
        }
    }

    // Series Dialog (Episodes & Seasons selection)
    selectedSeriesForDialog?.let { seriesItem ->
        if (repository != null) {
            SeriesDetailsDialog(
                series = seriesItem,
                repository = repository,
                isTvMode = isTvMode,
                onDismiss = { selectedSeriesForDialog = null },
                onPlayEpisode = { episodeItem ->
                    selectedSeriesForDialog = null
                    onSelectMedia(episodeItem)
                }
            )
        }
    }

    // Movie Details Dialog
    selectedMovieForDetails?.let { movieItem ->
        MovieDetailsDownloadDialog(
            movie = movieItem,
            isFavorite = favoriteIds.contains(movieItem.id),
            onToggleFavorite = { onToggleFavorite(movieItem.id) },
            onDismiss = { selectedMovieForDetails = null },
            onPlayOnline = { mediaToPlay, _ ->
                selectedMovieForDetails = null
                onSelectMedia(mediaToPlay)
            },
            onPlayOffline = { mediaToPlay ->
                selectedMovieForDetails = null
                onSelectMedia(mediaToPlay)
            }
        )
    }
}

/**
 * Circular OTT Brand Icon matching IMG_20261011_152459.jpg
 * Double metallic ring, circular clipped logo/icon, text underneath, left-right scrollable.
 */
@Composable
fun OttCircularBrandIcon(
    title: String,
    logoUrl: String?,
    isSelected: Boolean,
    accentColor: Color,
    isTvMode: Boolean,
    isAllIcon: Boolean = false,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(if (isTvMode) 78.dp else 68.dp)
            .clickable { onClick() }
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
    ) {
        // Double metallic/illuminated glowing ring as seen in the uploaded reference photo
        Box(
            modifier = Modifier
                .size(if (isTvMode) 68.dp else 60.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected) accentColor.copy(alpha = 0.25f)
                    else Color(0xFF0F172A)
                )
                .border(
                    width = if (isSelected) 3.dp else if (isFocused) 2.5.dp else 2.dp,
                    brush = if (isSelected) {
                        Brush.sweepGradient(listOf(Color(0xFF00E5FF), Color.White, Color(0xFF00E5FF)))
                    } else if (isFocused) {
                        Brush.sweepGradient(listOf(Color(0xFFFFD600), Color.White, Color(0xFFFFD600)))
                    } else {
                        // Sleek metallic silver rim from IMG_20261011_152459.jpg
                        Brush.sweepGradient(
                            listOf(
                                Color(0xFF94A3B8),
                                Color(0xFF334155),
                                Color(0xFFE2E8F0),
                                Color(0xFF475569),
                                Color(0xFF94A3B8)
                            )
                        )
                    },
                    shape = CircleShape
                )
                .padding(3.dp)
                .clip(CircleShape)
                .background(Color(0xFF0A0F1D)),
            contentAlignment = Alignment.Center
        ) {
            if (isAllIcon) {
                // All Hubs icon
                Icon(
                    imageVector = Icons.Rounded.Movie,
                    contentDescription = null,
                    tint = if (isSelected) Color(0xFF00E5FF) else Color.White,
                    modifier = Modifier.size(24.dp)
                )
            } else if (!logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = logoUrl,
                    contentDescription = title,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = title.take(2).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp
                )
            }

            // Glow overlay when selected
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF00E5FF).copy(alpha = 0.18f))
                )
            }
        }

        Spacer(modifier = Modifier.height(5.dp))

        // Text below: "Netflix", "Hotstar", "SonyLIV", etc.
        Text(
            text = title,
            color = if (isSelected) Color(0xFF00E5FF) else if (isFocused) Color(0xFFFFD600) else Color.White,
            fontSize = 11.5.sp,
            fontWeight = if (isSelected || isFocused) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Intelligent Language Badge Extractor for Posters
 */
fun getMovieLanguageBadge(movie: MediaItem): String {
    val textToScan = "${movie.title} ${movie.category} ${movie.genre ?: ""} ${movie.description ?: ""}".lowercase()
    return when {
        textToScan.contains("bangla dubbed") || textToScan.contains("বাংলা ডাবড") -> "বাংলা ডাবড"
        textToScan.contains("hindi dubbed") || textToScan.contains("হিন্দি ডাবড") -> "হিন্দি ডাবড"
        textToScan.contains("bangla") || textToScan.contains("বাংলা") || textToScan.contains("bd") || textToScan.contains("kolkata") -> "বাংলা"
        textToScan.contains("hindi") || textToScan.contains("হিন্দি") || textToScan.contains("bollywood") -> "হিন্দি"
        textToScan.contains("tamil") || textToScan.contains("তামিল") -> "তামিল"
        textToScan.contains("telugu") || textToScan.contains("তেলেগু") -> "তেলেগু"
        textToScan.contains("malayalam") || textToScan.contains("মালায়ালাম") -> "মালায়ালাম"
        textToScan.contains("south") || textToScan.contains("সাউথ") -> "সাউথ"
        textToScan.contains("korean") || textToScan.contains("কোরিয়ান") || textToScan.contains("k-drama") -> "কোরিয়ান"
        textToScan.contains("anime") || textToScan.contains("অ্যানিমে") -> "অ্যানিমে"
        textToScan.contains("turkish") || textToScan.contains("তুর্কি") -> "তুর্কি"
        textToScan.contains("english") || textToScan.contains("ইংরেজি") || textToScan.contains("hollywood") ||
                textToScan.contains("marvel") || textToScan.contains("dc comics") || textToScan.contains("hbo") ||
                textToScan.contains("netflix original") -> "ইংরেজি"
        else -> "NFT"
    }
}

@Composable
fun MoviePosterCard(
    movie: MediaItem,
    isFav: Boolean,
    isTvMode: Boolean,
    onSelect: () -> Unit,
    onToggleFav: () -> Unit
) {
    var isCardFocused by remember { mutableStateOf(false) }

    val langBadge = remember(movie.id, movie.title, movie.category, movie.description) {
        getMovieLanguageBadge(movie)
    }

    val cardModifier = if (isTvMode) {
        val cardScale by animateFloatAsState(
            targetValue = if (isCardFocused) 1.06f else 1.0f,
            animationSpec = tween(150, easing = FastOutSlowInEasing),
            label = "movieCardScale"
        )
        Modifier
            .scale(cardScale)
            .width(140.dp)
            .onFocusChanged { isCardFocused = it.isFocused }
            .focusable()
            .clickable { onSelect() }
    } else {
        Modifier
            .width(115.dp)
            .clickable { onSelect() }
    }

    Column(
        modifier = cardModifier
    ) {
        // Poster Box (Full poster display with badge & favorite button)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isTvMode && isCardFocused) Color(0xFF1E293B) else Color(0xFF0F172A),
            border = when {
                isTvMode && isCardFocused -> BorderStroke(2.5.dp, Color(0xFFFFD600))
                isFav -> BorderStroke(1.5.dp, Color(0xFFEF4444).copy(alpha = 0.7f))
                else -> BorderStroke(1.dp, Color(0xFF1E293B))
            },
            shadowElevation = if (isTvMode && isCardFocused) 6.dp else 1.dp,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isTvMode) 195.dp else 160.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Poster Image
                if (!movie.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = movie.logoUrl,
                        contentDescription = movie.title,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF1E293B)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Movie,
                            contentDescription = null,
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Top Left: Language Badge
                Row(
                    modifier = Modifier.align(Alignment.TopStart),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    if (movie.isSeries || movie.type == MediaType.SERIES) {
                        Surface(
                            shape = RoundedCornerShape(
                                topStart = 12.dp,
                                bottomEnd = 8.dp,
                                topEnd = 4.dp,
                                bottomStart = 4.dp
                            ),
                            color = Color(0xFFE11D48)
                        ) {
                            Text(
                                text = "SERIES",
                                color = Color.White,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(
                            topStart = 12.dp,
                            bottomEnd = 8.dp,
                            topEnd = 4.dp,
                            bottomStart = 4.dp
                        ),
                        color = when (langBadge) {
                            "বাংলা", "বাংলা ডাবড" -> Color(0xFF059669)
                            "হিন্দি", "হিন্দি ডাবড" -> Color(0xFFD97706)
                            "ইংরেজি" -> Color(0xFF2563EB)
                            "তামিল", "তেলেগু", "মালায়ালাম", "সাউথ" -> Color(0xFFEA580C)
                            "কোরিয়ান" -> Color(0xFF7C3AED)
                            "অ্যানিমে" -> Color(0xFFDB2777)
                            "তুর্কি" -> Color(0xFF0D9488)
                            else -> Color(0xFF6366F1)
                        }
                    ) {
                        Text(
                            text = langBadge,
                            color = Color.White,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                // Top Right Favorite Button
                IconButton(
                    onClick = onToggleFav,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(30.dp)
                ) {
                    Icon(
                        imageVector = if (isFav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFav) Color(0xFFEF4444) else Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Movie Title situated clearly below the poster card
        Text(
            text = movie.title,
            color = if (isCardFocused) Color(0xFFFFD600) else Color.White,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            lineHeight = 15.sp,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp)
        )
    }
}
