package com.example.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material.icons.rounded.Web
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.MediaRepository
import com.example.model.MediaItem
import com.example.model.MediaType
import com.example.model.MovieProvider
import com.example.model.StreamServer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream

// Extension Movie Item Model
data class ExtMovie(
    val id: String,
    val title: String,
    val posterUrl: String,
    val backdropUrl: String? = null,
    val rating: String = "8.5",
    val year: String = "2024",
    val quality: String = "HD",
    val duration: String = "2h 15m",
    val category: String = "Movie",
    val genres: List<String> = listOf("Action", "Drama"),
    val description: String = "",
    val streamServers: List<StreamServer> = emptyList(),
    val isSeries: Boolean = false,
    val episodes: List<String> = emptyList()
)

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieBrowserScreen(
    repository: MediaRepository,
    provider: MovieProvider,
    onClose: () -> Unit,
    onPlayDirectMedia: (MediaItem) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Mode Toggle: Native CloudStream Catalog View vs In-App Web Browser View
    var isWebViewMode by remember { mutableStateOf(false) }

    // API Host Selector (Especially for Microtv / MovieBox matching Screenshot 1)
    val availableHosts = remember(provider.name) {
        if (provider.name.contains("Microtv", ignoreCase = true) || provider.name.contains("MovieBox", ignoreCase = true)) {
            listOf(
                "api6.aoneroom.com",
                "api5.aoneroom.com",
                "api4.aoneroom.com",
                "api4sg.aoneroom.com",
                "api3.aoneroom.com"
            )
        } else {
            listOf("Default Host", "Fast CDN Server", "Cloudflare Edge", "Backup Mirror")
        }
    }
    var selectedHost by remember { mutableStateOf(availableHosts.first()) }
    var showHostDialog by remember { mutableStateOf(false) }

    // Search and category filter state in catalog
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    val categoryFilters = listOf("All", "Trending", "Movies", "TV Series", "Hindi Dubbed", "Bangla", "Anime", "4K UHD")

    // Selected Movie for Details/Episodes Dialog
    var selectedDetailMovie by remember { mutableStateOf<ExtMovie?>(null) }
    var isHeroInMyList by remember { mutableStateOf(false) }

    // Live remote catalog data
    var liveMediaItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var isCatalogLoading by remember { mutableStateOf(true) }
    var catalogError by remember { mutableStateOf<String?>(null) }

    fun refreshCatalog() {
        coroutineScope.launch {
            isCatalogLoading = true
            catalogError = null
            try {
                val items = repository.fetchLiveProviderCatalog(
                    provider = provider,
                    query = searchQuery,
                    typeFilter = selectedCategoryFilter
                )
                liveMediaItems = items
            } catch (e: Exception) {
                e.printStackTrace()
                catalogError = e.localizedMessage
            } finally {
                isCatalogLoading = false
            }
        }
    }

    LaunchedEffect(provider.id, selectedHost, searchQuery, selectedCategoryFilter) {
        refreshCatalog()
    }

    val providerMovies = remember(liveMediaItems) {
        liveMediaItems.map { mediaItemToExtMovie(it) }
    }

    // WEBVIEW STATE
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf(provider.siteUrl) }
    var pageTitle by remember { mutableStateOf(provider.name) }
    var loadProgress by remember { mutableFloatStateOf(0f) }
    var isWebLoading by remember { mutableStateOf(true) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var detectedStreamUrl by remember { mutableStateOf<String?>(null) }
    var detectedStreamType by remember { mutableStateOf("HLS Stream") }
    var blockedAdsCount by remember { mutableIntStateOf(0) }
    var customViewContainer by remember { mutableStateOf<FrameLayout?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    // Ad blocker keywords
    val adFilterKeywords = remember {
        listOf(
            "adsterra", "popcash", "propellerads", "onclick", "syndication", "exoclick",
            "juicyads", "trafficjunky", "doubleclick", "googleadservices", "taboola",
            "outbrain", "bet365", "1xbet", "parimatch", "melbet", "adtraffic", "hilltopads",
            "yandex.ru/ads", "adtrue", "monetag", "adnxs", "popads", "revenuehits"
        )
    }

    // Hardware Back Button Navigation
    BackHandler {
        when {
            selectedDetailMovie != null -> {
                selectedDetailMovie = null
            }
            isWebViewMode -> {
                if (customViewContainer != null) {
                    customViewCallback?.onCustomViewHidden()
                    customViewContainer = null
                } else if (webViewInstance?.canGoBack() == true) {
                    webViewInstance?.goBack()
                } else {
                    isWebViewMode = false
                }
            }
            searchQuery.isNotEmpty() -> {
                searchQuery = ""
            }
            else -> {
                onClose()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.destroy()
            webViewInstance = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF020617))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // TOP ACTION BAR & PROVIDER HEADER (CloudStream & Phisher Extension Bar)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A))
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = {
                                if (isWebViewMode) isWebViewMode = false else onClose()
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Extension Icon & Title with Host selector
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF8B5CF6).copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!provider.iconUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = provider.iconUrl,
                                    contentDescription = provider.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.Extension,
                                    contentDescription = null,
                                    tint = Color(0xFFDDD6FE),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = provider.name,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color(0xFF8B5CF6)
                                ) {
                                    Text(
                                        text = "Phisher Extension",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // Host selector button
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { showHostDialog = true }
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Dns,
                                    contentDescription = null,
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Host: $selectedHost ▾",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Action Buttons on Right
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Toggle between Native Catalog UI and Web Source Browser
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isWebViewMode) Color(0xFF2563EB) else Color(0xFF1E293B),
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isWebViewMode) Color(0xFF60A5FA) else Color(0xFF475569)),
                            modifier = Modifier.clickable { isWebViewMode = !isWebViewMode }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (isWebViewMode) Icons.Rounded.Layers else Icons.Rounded.Web,
                                    contentDescription = null,
                                    tint = if (isWebViewMode) Color.White else Color(0xFF94A3B8),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isWebViewMode) "মুভি তালিকা" else "ওয়েব ভিউ",
                                    color = if (isWebViewMode) Color.White else Color(0xFFE2E8F0),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // External Browser Launcher
                        IconButton(
                            onClick = {
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(provider.siteUrl.ifBlank { "https://google.com" }))
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.OpenInBrowser,
                                contentDescription = "Open External",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Close button
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // MAIN CONTENT BODY: Native Extension Movie Catalog vs In-App Web Browser
            if (!isWebViewMode) {
                // =============================================================
                // 1. NATIVE EXTENSION MOVIE CATALOG (Exact CloudStream Phisher UI)
                // =============================================================
                if (isCatalogLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF020617)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF8B5CF6),
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(38.dp)
                            )
                            Text(
                                text = "${provider.name} থেকে মুভি ও সিরিজ লোড হচ্ছে...",
                                color = Color(0xFFE2E8F0),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Host: $selectedHost",
                                color = Color(0xFF00E5FF),
                                fontSize = 11.sp
                            )
                        }
                    }
                } else if (providerMovies.isEmpty()) {
                    var isDownloadingLocal by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF020617))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFF1E293B),
                                modifier = Modifier.size(72.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (!provider.isInstalled) Icons.Rounded.FileDownload else Icons.Rounded.Movie,
                                        contentDescription = null,
                                        tint = if (!provider.isInstalled) Color(0xFF38BDF8) else Color(0xFF8B5CF6),
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                            Text(
                                text = if (!provider.isInstalled) "${provider.name} ডাউনলোড করা হয়নি" else "${provider.name} থেকে কোনো কন্টেন্ট লোড হয়নি",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (!provider.isInstalled)
                                    "এই এক্সটেনশনের মুভি ও সিরিজ দেখার জন্য এক্সটেনশন ফাইলটি ডাউনলোড ও লোড করুন।"
                                else
                                    "হোস্টিং সার্ভার থেকে সরাসরি ব্রাউজ করতে 'ওয়েব ভিউ' খুলুন অথবা রিফ্রেশ করুন।",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )

                            if (!provider.isInstalled) {
                                Button(
                                    onClick = {
                                        isDownloadingLocal = true
                                        coroutineScope.launch {
                                            val (ok, msg) = repository.downloadAndInstallProvider(provider)
                                            isDownloadingLocal = false
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                            refreshCatalog()
                                        }
                                    },
                                    enabled = !isDownloadingLocal,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8)),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    if (isDownloadingLocal) {
                                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("ডাউনলোড হচ্ছে...", color = Color.White)
                                    } else {
                                        Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("📥 এক্সটেনশনটি ডাউনলোড করুন", color = Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Button(
                                        onClick = { refreshCatalog() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("রিফ্রেশ (Retry)")
                                    }
                                    Button(
                                        onClick = { isWebViewMode = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(Icons.Rounded.Web, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("ওয়েব ভিউ খুলুন")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val heroMovie = providerMovies.first()
                    val filteredList = providerMovies.filter { item ->
                        val matchesQuery = searchQuery.isBlank() ||
                                item.title.contains(searchQuery, ignoreCase = true) ||
                                item.category.contains(searchQuery, ignoreCase = true) ||
                                item.genres.any { it.contains(searchQuery, ignoreCase = true) }

                        val matchesCategory = when (selectedCategoryFilter) {
                            "All" -> true
                            "Trending" -> true
                            "Movies" -> !item.isSeries
                            "TV Series" -> item.isSeries
                            "Hindi Dubbed" -> item.genres.any { it.contains("Hindi", ignoreCase = true) } || item.title.contains("Hindi", ignoreCase = true)
                            "Bangla" -> item.genres.any { it.contains("Bangla", ignoreCase = true) } || item.category.contains("Bangla", ignoreCase = true)
                            "Anime" -> item.category.contains("Anime", ignoreCase = true) || item.genres.any { it.contains("Anime", ignoreCase = true) }
                            "4K UHD" -> item.quality.contains("4K", ignoreCase = true) || item.quality.contains("UHD", ignoreCase = true)
                            else -> true
                        }

                        matchesQuery && matchesCategory
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF020617)),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        // Search bar inside catalog
                        item {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = {
                                    Text(
                                        "${provider.name} এ যেকোনো মুভি/সিরিজ খুঁজুন...",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 12.sp
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Search, contentDescription = null, tint = Color(0xFF8B5CF6))
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = Color.White)
                                        }
                                    }
                                },
                                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF8B5CF6),
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

                        // Category Filter Chips
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 4.dp)
                            ) {
                                items(categoryFilters) { cat ->
                                    val isSelected = selectedCategoryFilter == cat
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (isSelected) Color(0xFF8B5CF6) else Color(0xFF1E293B),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) Color(0xFFDDD6FE) else Color(0xFF334155)),
                                        modifier = Modifier.clickable { selectedCategoryFilter = cat }
                                    ) {
                                        Text(
                                            text = cat,
                                            color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // If user is searching, display a Search Grid
                        if (searchQuery.isNotBlank() || selectedCategoryFilter != "All") {
                            item {
                                Text(
                                    text = "অনুসন্ধান ফলাফল (${filteredList.size} টি মুভি)",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                )
                            }

                            items(filteredList.chunked(3)) { rowItems ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    rowItems.forEach { movie ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            MovieCardItem(
                                                movie = movie,
                                                onClick = { selectedDetailMovie = movie },
                                                onPlay = {
                                                    val mediaItem = movieToMediaItem(movie, provider)
                                                    onPlayDirectMedia(mediaItem)
                                                }
                                            )
                                        }
                                    }
                                    // Fill empty slots if last row has less than 3
                                    repeat(3 - rowItems.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        } else {
                            // =================================================
                            // FEATURED HERO BANNER (Exact UI from Screenshot 2)
                            // =================================================
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(280.dp)
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(Color(0xFF0F172A))
                                ) {
                                    // Hero Poster Background Image
                                    AsyncImage(
                                        model = heroMovie.backdropUrl ?: heroMovie.posterUrl,
                                        contentDescription = heroMovie.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    // Gradient Overlay for smooth text readability
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.verticalGradient(
                                                    colors = listOf(
                                                        Color.Transparent,
                                                        Color(0xFF020617).copy(alpha = 0.5f),
                                                        Color(0xFF020617).copy(alpha = 0.95f)
                                                    ),
                                                    startY = 60f
                                                )
                                            )
                                    )

                                    // Hero Content Details & Action Buttons
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(16.dp),
                                        verticalArrangement = Arrangement.Bottom
                                    ) {
                                        // Category / Genre Tag
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFFEF4444)
                                            ) {
                                                Text(
                                                    text = "FEATURED",
                                                    color = Color.White,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Black,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFF00E5FF).copy(alpha = 0.2f),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF))
                                            ) {
                                                Text(
                                                    text = heroMovie.category,
                                                    color = Color(0xFF00E5FF),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }

                                            Text(
                                                text = "⭐ ${heroMovie.rating} • ${heroMovie.year}",
                                                color = Color(0xFFFBBF24),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(4.dp))

                                        // Big Title
                                        Text(
                                            text = heroMovie.title,
                                            color = Color.White,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        // Short Synopsis
                                        Text(
                                            text = heroMovie.description.ifBlank { "Full HD Streaming from ${provider.name} with multiple fast servers and subtitles." },
                                            color = Color(0xFFCBD5E1),
                                            fontSize = 11.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            lineHeight = 14.sp
                                        )

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Hero Action Buttons (Exact Buttons from Screenshot 2: [+ None] [▶ Play] [ⓘ Info])
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            // [+ None / My List] Button
                                            OutlinedButton(
                                                onClick = {
                                                    isHeroInMyList = !isHeroInMyList
                                                    Toast.makeText(
                                                        context,
                                                        if (isHeroInMyList) "${heroMovie.title} ফেভারিট লিস্টে যোগ হয়েছে" else "লিস্ট থেকে সরানো হয়েছে",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF475569)),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    containerColor = Color(0xFF1E293B).copy(alpha = 0.7f),
                                                    contentColor = Color.White
                                                ),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(38.dp),
                                                contentPadding = PaddingValues(horizontal = 6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (isHeroInMyList) Icons.Rounded.Check else Icons.Rounded.Add,
                                                    contentDescription = null,
                                                    tint = if (isHeroInMyList) Color(0xFF10B981) else Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = if (isHeroInMyList) "Added" else "My List",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            // [▶ Play] Button (Glowing Blue/Cyan)
                                            Button(
                                                onClick = {
                                                    val mediaItem = movieToMediaItem(heroMovie, provider)
                                                    onPlayDirectMedia(mediaItem)
                                                },
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color(0xFF00E5FF),
                                                    contentColor = Color.Black
                                                ),
                                                modifier = Modifier
                                                    .weight(1.2f)
                                                    .height(38.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp)
                                            ) {
                                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Play", fontSize = 12.sp, fontWeight = FontWeight.Black)
                                            }

                                            // [ⓘ Info] Button
                                            Button(
                                                onClick = { selectedDetailMovie = heroMovie },
                                                shape = RoundedCornerShape(10.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color(0xFF334155),
                                                    contentColor = Color.White
                                                ),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(38.dp),
                                                contentPadding = PaddingValues(horizontal = 6.dp)
                                            ) {
                                                Icon(Icons.Rounded.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Info", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }

                            // =================================================
                            // SECTION 1: TRENDING (Exact Row from Screenshot 2)
                            // =================================================
                            item {
                                HorizontalMovieSection(
                                    title = "Trending",
                                    icon = Icons.Rounded.Star,
                                    iconColor = Color(0xFFFBBF24),
                                    movies = providerMovies.take(10),
                                    onMovieClick = { selectedDetailMovie = it },
                                    onPlayClick = {
                                        val mediaItem = movieToMediaItem(it, provider)
                                        onPlayDirectMedia(mediaItem)
                                    }
                                )
                            }

                            // =================================================
                            // SECTION 2: TRENDING IN CINEMA (Exact from Screenshot 2)
                            // =================================================
                            item {
                                HorizontalMovieSection(
                                    title = "Trending in Cinema",
                                    icon = Icons.Rounded.Movie,
                                    iconColor = Color(0xFFEC4899),
                                    movies = providerMovies.drop(4).take(10),
                                    onMovieClick = { selectedDetailMovie = it },
                                    onPlayClick = {
                                        val mediaItem = movieToMediaItem(it, provider)
                                        onPlayDirectMedia(mediaItem)
                                    }
                                )
                            }

                            // =================================================
                            // SECTION 3: LATEST BOLLYWOOD & HINDI DUBBED
                            // =================================================
                            item {
                                HorizontalMovieSection(
                                    title = "Latest Bollywood & Hindi Dubbed",
                                    icon = Icons.Rounded.Tv,
                                    iconColor = Color(0xFF8B5CF6),
                                    movies = providerMovies.filter { it.genres.contains("Bollywood") || it.genres.contains("Hindi") || it.category == "Bollywood" },
                                    onMovieClick = { selectedDetailMovie = it },
                                    onPlayClick = {
                                        val mediaItem = movieToMediaItem(it, provider)
                                        onPlayDirectMedia(mediaItem)
                                    }
                                )
                            }

                            // =================================================
                            // SECTION 4: POPULAR WEB SERIES & ORIGINALS
                            // =================================================
                            item {
                                HorizontalMovieSection(
                                    title = "Top Web Series & Originals",
                                    icon = Icons.Rounded.Layers,
                                    iconColor = Color(0xFF10B981),
                                    movies = providerMovies.filter { it.isSeries },
                                    onMovieClick = { selectedDetailMovie = it },
                                    onPlayClick = {
                                        val mediaItem = movieToMediaItem(it, provider)
                                        onPlayDirectMedia(mediaItem)
                                    }
                                )
                            }

                            // =================================================
                            // SECTION 5: BANGLA CINEMA & DRAMA
                            // =================================================
                            item {
                                HorizontalMovieSection(
                                    title = "Bangla Cinema & Originals",
                                    icon = Icons.Rounded.Public,
                                    iconColor = Color(0xFF00E5FF),
                                    movies = providerMovies.filter { it.category == "Bangla" || it.genres.contains("Bangla") },
                                    onMovieClick = { selectedDetailMovie = it },
                                    onPlayClick = {
                                        val mediaItem = movieToMediaItem(it, provider)
                                        onPlayDirectMedia(mediaItem)
                                    }
                                )
                            }

                            // =================================================
                            // SECTION 6: ANIME & ANIMATION
                            // =================================================
                            item {
                                HorizontalMovieSection(
                                    title = "Anime & Animation Hub",
                                    icon = Icons.Rounded.Star,
                                    iconColor = Color(0xFFF59E0B),
                                    movies = providerMovies.filter { it.category == "Anime" || it.genres.contains("Anime") },
                                    onMovieClick = { selectedDetailMovie = it },
                                    onPlayClick = {
                                        val mediaItem = movieToMediaItem(it, provider)
                                        onPlayDirectMedia(mediaItem)
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                // =============================================================
                // 2. IN-APP WEBVIEW (With Ad-Shield & Video Stream Sniffer)
                // =============================================================
                if (isWebLoading && loadProgress < 1f) {
                    LinearProgressIndicator(
                        progress = { loadProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = Color(0xFF00E5FF),
                        trackColor = Color(0xFF1E293B)
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )

                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    databaseEnabled = true
                                    loadWithOverviewMode = true
                                    useWideViewPort = true
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                    allowFileAccess = false
                                    allowContentAccess = false
                                    setSupportMultipleWindows(false)
                                    javaScriptCanOpenWindowsAutomatically = false
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    mediaPlaybackRequiresUserGesture = false
                                    userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile; rv:109.0) Gecko/118.0 Firefox/118.0"
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                        val url = request?.url?.toString() ?: return false
                                        if (url.startsWith("intent:") || url.startsWith("market:") || url.startsWith("tg:") || url.startsWith("whatsapp:")) {
                                            return true
                                        }
                                        if (adFilterKeywords.any { url.contains(it, ignoreCase = true) }) {
                                            blockedAdsCount++
                                            return true
                                        }
                                        currentUrl = url
                                        return false
                                    }

                                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                                        val url = request?.url?.toString() ?: return null
                                        if (url.contains(".m3u8", ignoreCase = true) ||
                                            url.contains(".mpd", ignoreCase = true) ||
                                            url.contains(".mkv", ignoreCase = true) ||
                                            url.contains("movielinkbd", ignoreCase = true) ||
                                            (url.contains("/media/", ignoreCase = true) && url.contains("token=", ignoreCase = true)) ||
                                            (url.contains(".mp4", ignoreCase = true) && !url.contains("thumb") && !url.contains("preview")) ||
                                            url.contains("/master.m3u8", ignoreCase = true) ||
                                            url.contains("/playlist.m3u8", ignoreCase = true) ||
                                            url.contains("/index.m3u8", ignoreCase = true)) {
                                            if (!adFilterKeywords.any { url.contains(it, ignoreCase = true) }) {
                                                detectedStreamUrl = url
                                                detectedStreamType = when {
                                                    url.contains("movielinkbd", true) || (url.contains("/media/", true) && url.contains("token=")) -> "MovieLinkBD Movie (MKV/HD)"
                                                    url.contains(".mkv", true) -> "MKV High-Definition Video"
                                                    url.contains(".m3u8", true) -> "HLS Live Stream (M3U8)"
                                                    url.contains(".mpd", true) -> "DASH Stream (MPD)"
                                                    else -> "MP4 Direct Video"
                                                }
                                            }
                                        }
                                        if (adFilterKeywords.any { url.contains(it, ignoreCase = true) }) {
                                            blockedAdsCount++
                                            return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream("".toByteArray()))
                                        }
                                        return super.shouldInterceptRequest(view, request)
                                    }

                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        super.onPageStarted(view, url, favicon)
                                        isWebLoading = true
                                        url?.let { currentUrl = it }
                                        canGoBack = view?.canGoBack() == true
                                        canGoForward = view?.canGoForward() == true
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        isWebLoading = false
                                        url?.let { currentUrl = it }
                                        pageTitle = view?.title ?: provider.name
                                        canGoBack = view?.canGoBack() == true
                                        canGoForward = view?.canGoForward() == true
                                    }
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        loadProgress = newProgress / 100f
                                        if (newProgress >= 100) isWebLoading = false
                                    }
                                    override fun onReceivedTitle(view: WebView?, title: String?) {
                                        if (!title.isNullOrBlank()) pageTitle = title
                                    }
                                }

                                loadUrl(provider.siteUrl.ifBlank { "https://showflix.in" })
                                webViewInstance = this
                            }
                        }
                    )
                }
            }
        }

        // =============================================================
        // HOST SELECTOR DIALOG (For switching API hosts: api6, api5, api4, etc.)
        // =============================================================
        if (showHostDialog) {
            AlertDialog(
                onDismissRequest = { showHostDialog = false },
                shape = RoundedCornerShape(18.dp),
                containerColor = Color(0xFF0F172A),
                icon = {
                    Icon(Icons.Rounded.Dns, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(32.dp))
                },
                title = {
                    Text(
                        text = "Select Provider Host",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Choose the API endpoint to load movies and video streams:",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        )

                        availableHosts.forEach { host ->
                            val isSelected = selectedHost == host
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) Color(0xFF8B5CF6).copy(alpha = 0.3f) else Color(0xFF1E293B),
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) Color(0xFF8B5CF6) else Color(0xFF334155)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedHost = host
                                        showHostDialog = false
                                        Toast.makeText(context, "Host switched to $host", Toast.LENGTH_SHORT).show()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = host,
                                        color = if (isSelected) Color(0xFF00E5FF) else Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (isSelected) {
                                        Icon(Icons.Rounded.Check, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showHostDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6))
                    ) {
                        Text("Close", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }

        // =============================================================
        // MOVIE DETAIL & EPISODES / SERVERS MODAL DIALOG
        // =============================================================
        if (selectedDetailMovie != null) {
            val movie = selectedDetailMovie!!
            Dialog(
                onDismissRequest = { selectedDetailMovie = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF8B5CF6).copy(alpha = 0.7f)),
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .fillMaxHeight(0.88f)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Header Backdrop
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        ) {
                            AsyncImage(
                                model = movie.backdropUrl ?: movie.posterUrl,
                                contentDescription = movie.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color(0xFF0F172A)),
                                            startY = 50f
                                        )
                                    )
                            )

                            IconButton(
                                onClick = { selectedDetailMovie = null },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .size(34.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }

                        // Scrollable Detail Body
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Poster
                                    AsyncImage(
                                        model = movie.posterUrl,
                                        contentDescription = movie.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .width(85.dp)
                                            .height(125.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(10.dp))
                                    )

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = movie.title,
                                            color = Color.White,
                                            fontSize = 17.sp,
                                            fontWeight = FontWeight.Bold
                                        )

                                        Spacer(modifier = Modifier.height(4.dp))

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFFFBBF24)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(Icons.Rounded.Star, contentDescription = null, tint = Color.Black, modifier = Modifier.size(11.dp))
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                    Text(movie.rating, color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Black)
                                                }
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color(0xFF1E293B)
                                            ) {
                                                Text(movie.quality, color = Color(0xFF00E5FF), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                            }

                                            Text(movie.year, color = Color(0xFF94A3B8), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                            Text("• ${movie.duration}", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        // Genres
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            movie.genres.forEach { g ->
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = Color(0xFF8B5CF6).copy(alpha = 0.2f),
                                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.5f))
                                                ) {
                                                    Text(g, color = Color(0xFFDDD6FE), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            item {
                                Text(
                                    text = "বিবরণ ও কাহিনী সংক্ষেপ (Storyline):",
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = movie.description.ifBlank { "উচ্চ মানের আল্ট্রা এইচডি প্লেব্যাকে ${movie.title} উপভোগ করুন। একাধিক ফাস্ট স্ট্রিম সার্ভার ও সাবটাইটেল সাপোর্ট অন্তর্ভুক্ত।" },
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }

                            // Servers List
                            item {
                                Text(
                                    text = "স্ট্রিম সার্ভারসমূহ (Fast Servers):",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            items(movie.streamServers) { srv ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF1E293B),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val singleItem = movieToMediaItem(movie, provider).copy(
                                                streamUrl = srv.url,
                                                servers = listOf(srv) + movie.streamServers.filter { it.url != srv.url }
                                            )
                                            selectedDetailMovie = null
                                            onPlayDirectMedia(singleItem)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(srv.name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Text("প্লে করুন ▶", color = Color(0xFF00E5FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // If Series: Episodes
                            if (movie.isSeries && movie.episodes.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "পর্বসমূহ (Episodes):",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                items(movie.episodes) { ep ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF1E293B),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                val mediaItem = movieToMediaItem(movie, provider).copy(
                                                    title = "${movie.title} - $ep"
                                                )
                                                selectedDetailMovie = null
                                                onPlayDirectMedia(mediaItem)
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(ep, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }

                        // Bottom Big Play Button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0F172A))
                                .padding(12.dp)
                        ) {
                            Button(
                                onClick = {
                                    val mediaItem = movieToMediaItem(movie, provider)
                                    selectedDetailMovie = null
                                    onPlayDirectMedia(mediaItem)
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("এখনই প্লেয়ারে চালান (Play Movie)", fontSize = 13.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                }
            }
        }
    }
}

// -----------------------------------------------------------------------------
// HORIZONTAL MOVIE SECTION (Exact Row Design from Screenshot 2)
// -----------------------------------------------------------------------------
@Composable
fun HorizontalMovieSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    movies: List<ExtMovie>,
    onMovieClick: (ExtMovie) -> Unit,
    onPlayClick: (ExtMovie) -> Unit
) {
    if (movies.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Section Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "See all >",
                color = Color(0xFF00E5FF),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Horizontal Carousel of Posters (Matching Screenshot 2)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            items(movies) { movie ->
                MovieCardItem(
                    movie = movie,
                    onClick = { onMovieClick(movie) },
                    onPlay = { onPlayClick(movie) }
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// MOVIE CARD ITEM (Poster + Star Rating Badge + Title from Screenshot 2)
// -----------------------------------------------------------------------------
@Composable
fun MovieCardItem(
    movie: ExtMovie,
    onClick: () -> Unit,
    onPlay: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .width(115.dp)
            .scale(if (isFocused) 1.06f else 1.0f)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onPlay() }
    ) {
        Box(
            modifier = Modifier
                .width(115.dp)
                .height(165.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
                .border(
                    width = if (isFocused) 2.5.dp else 1.dp,
                    color = if (isFocused) Color(0xFF00E5FF) else Color(0xFF334155),
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            // Poster Image - full visibility
            AsyncImage(
                model = movie.posterUrl,
                contentDescription = movie.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )

            // Rating Badge on Top-Right (e.g. "6.8★", "9.1★" as in Screenshot 2)
            Surface(
                shape = RoundedCornerShape(bottomStart = 6.dp, topEnd = 12.dp),
                color = Color(0xFFFBBF24),
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${movie.rating}★",
                        color = Color.Black,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            // Quality Pill on Bottom-Left
            Surface(
                shape = RoundedCornerShape(topEnd = 6.dp, bottomStart = 12.dp),
                color = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier.align(Alignment.BottomStart)
            ) {
                Text(
                    text = movie.quality,
                    color = Color(0xFF00E5FF),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Title below poster
        Text(
            text = movie.title,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// -----------------------------------------------------------------------------
// HELPER CONVERTER: ExtMovie -> MediaItem for NAFI TV Video Player
// -----------------------------------------------------------------------------
fun movieToMediaItem(movie: ExtMovie, provider: MovieProvider): MediaItem {
    val servers = if (movie.streamServers.isNotEmpty()) {
        movie.streamServers
    } else {
        listOf(
            StreamServer("Server 1 (${provider.name})", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8")
        )
    }

    return MediaItem(
        id = movie.id,
        title = movie.title,
        category = "${provider.name} • ${movie.category}",
        type = if (movie.isSeries) MediaType.SERIES else MediaType.MOVIE,
        streamUrl = servers.first().url,
        backupUrl = servers.getOrNull(1)?.url,
        servers = servers,
        logoUrl = movie.posterUrl,
        description = if (movie.description.isNotBlank()) movie.description else "প্রোভাইডার: ${provider.name} | রেটিং: ${movie.rating}",
        quality = movie.quality,
        rating = movie.rating,
        year = movie.year,
        isLive = false
    )
}

// -----------------------------------------------------------------------------
// HELPER CONVERTER: MediaItem -> ExtMovie for Provider Screen UI
// -----------------------------------------------------------------------------
fun mediaItemToExtMovie(item: MediaItem): ExtMovie {
    val servers = if (item.servers.isNotEmpty()) {
        item.servers
    } else if (item.streamUrl.isNotBlank()) {
        listOf(StreamServer("Server 1 (HD Live)", item.streamUrl))
    } else {
        listOf(StreamServer("Server 1 (Default)", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"))
    }

    val isSeries = item.type == MediaType.SERIES
    val episodes = if (isSeries) {
        listOf(
            "Episode 1", "Episode 2", "Episode 3", "Episode 4",
            "Episode 5", "Episode 6", "Episode 7", "Episode 8"
        )
    } else {
        emptyList()
    }

    return ExtMovie(
        id = item.id,
        title = item.title,
        posterUrl = item.logoUrl ?: "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=500&auto=format&fit=crop&q=60",
        backdropUrl = item.logoUrl,
        rating = item.rating?.replace("★", "")?.trim() ?: "8.5",
        year = item.year ?: "2024",
        quality = item.quality ?: "1080p HD",
        duration = "2h 15m",
        category = item.category ?: "Movie",
        genres = listOf(item.category ?: "Cinema", "HD Stream"),
        description = item.description ?: "",
        streamServers = servers,
        isSeries = isSeries,
        episodes = episodes
    )
}
