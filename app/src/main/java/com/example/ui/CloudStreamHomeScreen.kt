package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.example.model.StreamServer
import kotlinx.coroutines.launch

@Composable
fun CloudStreamHomeScreen(
    repository: MediaRepository,
    movieProviders: List<MovieProvider>,
    activeProvider: MovieProvider?,
    onSelectProvider: (MovieProvider?) -> Unit,
    onOpenExtensions: () -> Unit,
    onPlayMovie: (MediaItem) -> Unit,
    onOpenMovieBrowser: (MovieProvider) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    var selectedMovieForDetail by remember { mutableStateOf<MediaItem?>(null) }
    var showProviderFilterMenu by remember { mutableStateOf(false) }
    var myWatchlistIds by remember { mutableStateOf(setOf<String>()) }

    // Live Dynamic Movies fetched in real-time from Extension / Provider APIs
    var liveMoviesList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var fetchErrorMessage by remember { mutableStateOf<String?>(null) }

    val categoryTabs = listOf("All", "NAFI OTT PLATFORM", "Movies", "TV Series", "Anime", "Asian Dramas")

    // Function to fetch live movies from provider endpoints
    fun loadLiveMovies() {
        coroutineScope.launch {
            isLoading = true
            fetchErrorMessage = null
            try {
                if (searchQuery.isNotBlank() && (searchQuery.contains("csredirect") || searchQuery.contains("csshare:") || searchQuery.contains("aoneroom.com"))) {
                    val resolved = repository.resolveCloudStreamShareLink(searchQuery)
                    if (resolved != null) {
                        liveMoviesList = listOf(resolved)
                        isLoading = false
                        return@launch
                    }
                }
                val fetched = repository.fetchLiveProviderCatalog(
                    provider = activeProvider,
                    query = searchQuery,
                    typeFilter = selectedCategoryFilter
                )
                liveMoviesList = fetched
            } catch (e: Exception) {
                e.printStackTrace()
                fetchErrorMessage = e.localizedMessage ?: "মুভি লোড করতে সমস্যা হয়েছে"
            } finally {
                isLoading = false
            }
        }
    }

    // Load data dynamically when screen appears or when provider/category/search changes
    LaunchedEffect(activeProvider, selectedCategoryFilter, searchQuery) {
        loadLiveMovies()
    }

    // Top Live Hero Movie (first dynamic movie from the real list)
    val heroMovie: MediaItem? = liveMoviesList.firstOrNull()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (isLoading && liveMoviesList.isEmpty()) {
            // Authentic Loading State
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    color = Color(0xFF00E5FF),
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (activeProvider != null) "${activeProvider.name} থেকে লাইভ মুভি লোড হচ্ছে..." else "এক্সটেনশন ও রিপোজিটরি থেকে মুভি ফেচ হচ্ছে...",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        } else if (liveMoviesList.isEmpty()) {
            // Clean Empty State with Action Button to Extensions
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Rounded.MovieFilter,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (searchQuery.isNotBlank()) "কোনো মুভি বা সিরিজ পাওয়া যায়নি" else "কোনো এক্সটেনশন ডেটা পাওয়া যায়নি",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (searchQuery.isNotBlank()) "অন্য কি-ওয়ার্ড দিয়ে আবার সার্চ করুন" else "CloudStream রিপোজিটরি ও এক্সটেনশন ইনস্টল করে আসল মুভি উপভোগ করুন।",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { onOpenExtensions() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Extension, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("এক্সটেনশন ম্যানেজার")
                    }
                    OutlinedButton(
                        onClick = { loadLiveMovies() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E5FF)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("রিফ্রেশ")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // HERO BANNER (Dynamic from real top movie)
                if (heroMovie != null) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(420.dp)
                        ) {
                            // Backdrop Poster
                            AsyncImage(
                                model = heroMovie.logoUrl,
                                contentDescription = heroMovie.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Gradient Overlay on bottom & top
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Black.copy(alpha = 0.55f),
                                                Color.Transparent,
                                                Color.Black.copy(alpha = 0.85f),
                                                Color.Black
                                            )
                                        )
                                    )
                            )

                            // Top Action Bar Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .statusBarsPadding()
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                IconButton(
                                    onClick = { isSearchActive = !isSearchActive },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.5f))
                                ) {
                                    Icon(
                                        imageVector = if (isSearchActive) Icons.Rounded.Close else Icons.Rounded.Search,
                                        contentDescription = "Search",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Provider Switcher Pill Indicator
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = Color.Black.copy(alpha = 0.7f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.7f)),
                                    modifier = Modifier.clickable { showProviderFilterMenu = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF10B981))
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = activeProvider?.name ?: "All Providers (Phisher)",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Rounded.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = Color(0xFF38BDF8),
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }

                                // Extensions Management Action Button
                                IconButton(
                                    onClick = { onOpenExtensions() },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF2563EB))
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Extension,
                                        contentDescription = "Extensions Manager",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            // Hero Content Text & Action Buttons
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = heroMovie.title,
                                    color = Color.White,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${heroMovie.category ?: "Cinema"} • ${heroMovie.year ?: "2026"} • ${heroMovie.quality ?: "HD 1080p"}",
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                // 3 Action Buttons: [+ My List], [▶ Play], [ⓘ Info]
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val inList = myWatchlistIds.contains(heroMovie.id)
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clickable {
                                                myWatchlistIds = if (inList) myWatchlistIds - heroMovie.id else myWatchlistIds + heroMovie.id
                                                Toast.makeText(context, if (inList) "Removed from My List" else "Added to My List!", Toast.LENGTH_SHORT).show()
                                            }
                                            .padding(horizontal = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (inList) Icons.Rounded.Check else Icons.Rounded.Add,
                                            contentDescription = "My List",
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (inList) "Added" else "None",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    // Play Pill Button
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.White,
                                        modifier = Modifier.clickable { onPlayMovie(heroMovie) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.PlayArrow,
                                                contentDescription = "Play",
                                                tint = Color.Black,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Play",
                                                color = Color.Black,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    // Info Button
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .clickable { selectedMovieForDetail = heroMovie }
                                            .padding(horizontal = 8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Info,
                                            contentDescription = "Info",
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Info",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Search Bar
                if (isSearchActive) {
                    item {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search movies, series across extensions...", color = Color(0xFF64748B), fontSize = 13.sp) },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Color(0xFF38BDF8)) },
                            trailingIcon = {
                                if (searchQuery.isNotBlank()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = Color.White)
                                    }
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF38BDF8),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedContainerColor = Color(0xFF0F172A),
                                unfocusedContainerColor = Color(0xFF0F172A),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }

                // Category Filter Pills
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(categoryTabs) { tab ->
                            val isSelected = selectedCategoryFilter == tab
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) Color(0xFF2563EB) else Color(0xFF1E293B),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isSelected) Color(0xFF00E5FF) else Color(0xFF334155)
                                ),
                                modifier = Modifier.clickable { selectedCategoryFilter = tab }
                            ) {
                                Text(
                                    text = tab,
                                    color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }

                // DEDICATED OTT SECTION 0: NAFI OTT PLATFORM (Your Provided Playlist Movies & Series)
                val nafiOttItems = liveMoviesList.filter { it.tournament == "NAFI_OTT" || (it.category ?: "").contains("NAFI OTT", ignoreCase = true) }
                if (nafiOttItems.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp, bottom = 6.dp)
                        ) {
                            // Exclusive NAFI OTT PLATFORM Header Banner
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                    listOf(Color(0xFFE50914), Color(0xFFFF3D00))
                                                )
                                            )
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = "ORIGINAL",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            letterSpacing = 1.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "NAFI OTT PLATFORM",
                                        color = Color.White,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFE50914).copy(alpha = 0.15f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE50914).copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = "${nafiOttItems.size} Movies",
                                        color = Color(0xFFFF5252),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            LiveMovieRow(
                                sectionTitle = "",
                                movies = nafiOttItems,
                                onMovieClick = { selectedMovieForDetail = it },
                                onPlayClick = { onPlayMovie(it) }
                            )
                        }
                    }
                }

                // DYNAMIC CAROUSEL SECTION 1: Trending & Top Releases
                val otherMovies = liveMoviesList.filterNot { it.tournament == "NAFI_OTT" || (it.category ?: "").contains("NAFI OTT", ignoreCase = true) }
                val trendingItems = if (otherMovies.isNotEmpty()) otherMovies.take(12) else liveMoviesList.take(12)
                if (trendingItems.isNotEmpty()) {
                    item {
                        LiveMovieRow(
                            sectionTitle = "Trending & New Releases",
                            movies = trendingItems,
                            onMovieClick = { selectedMovieForDetail = it },
                            onPlayClick = { onPlayMovie(it) }
                        )
                    }
                }

                // DYNAMIC CAROUSEL SECTION 2: Action & Cinema
                val actionItems = liveMoviesList.filter { (it.category ?: "").contains("Action", ignoreCase = true) || (it.description ?: "").contains("Action", ignoreCase = true) }
                if (actionItems.isNotEmpty()) {
                    item {
                        LiveMovieRow(
                            sectionTitle = "Action & Adventure",
                            movies = actionItems,
                            onMovieClick = { selectedMovieForDetail = it },
                            onPlayClick = { onPlayMovie(it) }
                        )
                    }
                }

                // DYNAMIC CAROUSEL SECTION 3: TV Series & Seasons
                val seriesItems = liveMoviesList.filter { it.type == MediaType.SERIES || (it.category ?: "").contains("Series", ignoreCase = true) }
                if (seriesItems.isNotEmpty()) {
                    item {
                        LiveMovieRow(
                            sectionTitle = "Popular TV Series & Shows",
                            movies = seriesItems,
                            onMovieClick = { selectedMovieForDetail = it },
                            onPlayClick = { onPlayMovie(it) }
                        )
                    }
                }

                // DYNAMIC CAROUSEL SECTION 4: Drama & Romance
                val dramaItems = liveMoviesList.filter { (it.category ?: "").contains("Drama", ignoreCase = true) || (it.category ?: "").contains("Romance", ignoreCase = true) }
                if (dramaItems.isNotEmpty()) {
                    item {
                        LiveMovieRow(
                            sectionTitle = "Drama & Cinema Originals",
                            movies = dramaItems,
                            onMovieClick = { selectedMovieForDetail = it },
                            onPlayClick = { onPlayMovie(it) }
                        )
                    }
                }

                // DYNAMIC CAROUSEL SECTION 5: All Dynamic Movies
                val remainingItems = liveMoviesList.drop(12)
                if (remainingItems.isNotEmpty()) {
                    item {
                        LiveMovieRow(
                            sectionTitle = "Explore More Movies & Series",
                            movies = remainingItems,
                            onMovieClick = { selectedMovieForDetail = it },
                            onPlayClick = { onPlayMovie(it) }
                        )
                    }
                }
            }
        }

        // Floating Filter & Refresh Button
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FloatingActionButton(
                onClick = { loadLiveMovies() },
                containerColor = Color(0xFF1E293B),
                contentColor = Color(0xFF00E5FF),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Refresh Live Feed",
                    modifier = Modifier.size(22.dp)
                )
            }

            FloatingActionButton(
                onClick = { showProviderFilterMenu = true },
                containerColor = Color(0xFF2563EB),
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MenuOpen,
                    contentDescription = "Providers Filter",
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }

    // Provider Selector Modal Sheet
    if (showProviderFilterMenu) {
        AlertDialog(
            onDismissRequest = { showProviderFilterMenu = false },
            containerColor = Color(0xFF0F172A),
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Select Movie Source", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    IconButton(onClick = { onOpenExtensions(); showProviderFilterMenu = false }) {
                        Icon(Icons.Rounded.Extension, contentDescription = "Extensions", tint = Color(0xFF38BDF8))
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Choose which CloudStream extension to source live movies from:",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    // Option 1: All Providers
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (activeProvider == null) Color(0xFF2563EB) else Color(0xFF1E293B),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectProvider(null)
                                showProviderFilterMenu = false
                                Toast.makeText(context, "Sourcing from All Extensions", Toast.LENGTH_SHORT).show()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("⚡", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("All Installed Extensions", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Text("All", color = Color(0xFF00E5FF), fontSize = 11.sp)
                        }
                    }

                    // Installed Providers List
                    val installedProviders = movieProviders.filter { it.isInstalled }
                    installedProviders.take(8).forEach { prov ->
                        val isSelected = activeProvider?.id == prov.id
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFF2563EB) else Color(0xFF1E293B),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectProvider(prov)
                                    showProviderFilterMenu = false
                                    Toast.makeText(context, "Sourced from ${prov.name}", Toast.LENGTH_SHORT).show()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(prov.flag ?: "🎬", fontSize = 14.sp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(prov.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Text(prov.language, color = Color(0xFF94A3B8), fontSize = 11.sp)
                            }
                        }
                    }

                    Button(
                        onClick = {
                            onOpenExtensions()
                            showProviderFilterMenu = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Manage Extensions & Repositories", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProviderFilterMenu = false }) {
                    Text("Close", color = Color(0xFF38BDF8))
                }
            }
        )
    }

    // Movie Detail Dialog / Modal
    if (selectedMovieForDetail != null) {
        val movie = selectedMovieForDetail!!
        AlertDialog(
            onDismissRequest = { selectedMovieForDetail = null },
            containerColor = Color(0xFF0F172A),
            shape = RoundedCornerShape(20.dp),
            title = null,
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(14.dp))
                    ) {
                        AsyncImage(
                            model = movie.logoUrl,
                            contentDescription = movie.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color.Black.copy(alpha = 0.75f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = movie.rating ?: "8.0★",
                                color = Color(0xFFFFD700),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Text(
                        text = movie.title,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Text(
                        text = "${movie.category ?: "Cinema"} • ${movie.year ?: "2026"} • ${movie.quality ?: "1080p"}",
                        color = Color(0xFF38BDF8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = movie.description ?: "Full high-speed streaming available across multiple backup servers.",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    // Multi-Server selector
                    if (movie.servers.isNotEmpty()) {
                        Text("Available Streaming Servers:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        movie.servers.forEach { server ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF1E293B),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onPlayMovie(movie.copy(streamUrl = server.url))
                                        selectedMovieForDetail = null
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(server.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                    Icon(Icons.Rounded.PlayCircle, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onPlayMovie(movie)
                        selectedMovieForDetail = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Watch Now (সরাসরি দেখুন)", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedMovieForDetail = null }) {
                    Text("Close", color = Color(0xFF94A3B8))
                }
            }
        )
    }
}

@Composable
private fun LiveMovieRow(
    sectionTitle: String,
    movies: List<MediaItem>,
    onMovieClick: (MediaItem) -> Unit,
    onPlayClick: (MediaItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = sectionTitle,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Icon(
                imageVector = Icons.Rounded.ArrowForward,
                contentDescription = "See all",
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(movies, key = { it.id }) { item ->
                var isFocused by remember { mutableStateOf(false) }
                val rowItemScale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (isFocused) 1.10f else 1.0f,
                    animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.62f, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
                    label = "csRowItemScale"
                )

                Column(
                    modifier = Modifier
                        .width(115.dp)
                        .scale(rowItemScale)
                        .onFocusChanged { isFocused = it.isFocused }
                        .focusable()
                        .clickable { onMovieClick(item) }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(165.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1E293B))
                            .then(
                                if (isFocused) Modifier.border(3.dp, Color(0xFF00E5FF), RoundedCornerShape(10.dp))
                                else Modifier
                            )
                    ) {
                        AsyncImage(
                            model = item.logoUrl,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        if (isFocused) {
                            Surface(
                                shape = RoundedCornerShape(topStart = 10.dp, bottomEnd = 8.dp),
                                color = Color(0xFF00E5FF),
                                modifier = Modifier.align(Alignment.TopStart)
                            ) {
                                Text(
                                    text = "▶ OK",
                                    color = Color.Black,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(bottomStart = 6.dp),
                            color = Color.Black.copy(alpha = 0.85f),
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Text(
                                text = item.rating ?: "8.0★",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(5.dp))

                    Text(
                        text = item.title,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
