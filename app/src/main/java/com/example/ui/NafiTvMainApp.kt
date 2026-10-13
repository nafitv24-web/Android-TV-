package com.example.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.R
import com.example.data.MediaRepository
import com.example.model.ActiveUserInfo
import com.example.model.AppNotification
import com.example.model.AppUserAnalytics
import com.example.ui.components.BreakingNewsTickerBar
import com.example.model.AppUpdateInfo
import com.example.model.CloudStreamRepo
import com.example.model.MediaItem
import com.example.model.MediaType
import com.example.model.MovieProvider
import com.example.model.NotificationType
import com.example.ui.components.NafiLogoLoadingView
import com.example.model.PlaylistInfo
import com.example.model.StreamServer
import com.example.player.VideoPlayerScreen
import com.example.ui.AppUpdateDialog
import com.example.ui.MovieBrowserScreen
import com.example.ui.NotificationCenterDialog
import com.example.util.NotificationHelper
import com.example.util.MovieDownloadManager
import com.example.util.DownloadState
import com.example.util.DownloadedMovie
import com.example.ui.OfflineDownloadsScreen
import com.example.ui.MovieDetailsDownloadDialog
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

enum class AppTab(val title: String, val englishLabel: String) {
    LIVE_TV("টিভি", "Live TV"),
    EVENTS("লাইভ ইভেন্ট", "Live Events"),
    PLAYLIST("প্লেলিস্ট", "Playlist"),
    MENU("মেনু", "Menu")
}

enum class LoadingStage(val title: String, val shortLabel: String, val step: Int, val totalSteps: Int = 3) {
    IDLE("প্রস্তুত", "Ready", 0),
    LIVE_TV("১. লাইভ টিভি চ্যানেল লোড হচ্ছে...", "লাইভ টিভি", 1),
    LIVE_EVENTS("২. লাইভ ইভেন্ট লোড হচ্ছে...", "লাইভ ইভেন্ট", 2),
    PLAYLISTS("৩. প্লেলিস্ট ও চ্যানেল লোড হচ্ছে...", "প্লেলিস্ট", 3),
    COMPLETED("সকল ডেটা সফলভাবে লোড সম্পন্ন!", "সম্পন্ন", 3)
}

enum class AdminTab(val label: String) {
    ANALYTICS("👥 ইউজার ও ট্রাফিক"),
    TICKER("ব্রেকিং নিউজ বার"),
    CHANNELS("Live TV Channels"),
    MOVIES("Movies"),
    PLAYLISTS("Playlists"),
    XTREAM("Xtream Codes API"),
    STALKER("Stalker / Ministra"),
    SPORTS("Sports Matches"),
    BROADCAST("নোটিফিকেশন পাঠান"),
    REPOSITORIES("CloudStream Repos"),
    APP_UPDATE("App Updates"),
    FIREBASE("Firebase Cloud")
}

@Composable
fun customFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color(0xFF00E5FF),
    unfocusedBorderColor = Color(0xFF334155),
    focusedContainerColor = Color(0xFF0F172A),
    unfocusedContainerColor = Color(0xFF0F172A),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    cursorColor = Color(0xFF00E5FF)
)

@Composable
fun NafiTvMainApp(
    deepLinkRepoUrl: String? = null,
    onClearDeepLink: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val repository = remember { MediaRepository(context) }
    val coroutineScope = rememberCoroutineScope()

    var currentTab by remember { mutableStateOf(AppTab.LIVE_TV) }
    var selectedMediaItem by remember { mutableStateOf<MediaItem?>(null) }
    var activePlaybackPlaylist by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    val initialSavedMode = remember {
        when (repository.getSavedUserMode()) {
            "REMOTE" -> AppUserMode.REMOTE
            "MOBILE" -> AppUserMode.MOBILE
            else -> null
        }
    }
    var activeUserMode by remember { mutableStateOf<AppUserMode?>(initialSavedMode) }
    var isTvMode by remember { mutableStateOf(initialSavedMode == AppUserMode.REMOTE) }
    var isAdminViewActive by remember { mutableStateOf(false) }
    var activeMovieBrowserProvider by remember { mutableStateOf<MovieProvider?>(null) }
    var isExtensionsManagementActive by remember { mutableStateOf(false) }
    var isOfflineDownloadsActive by remember { mutableStateOf(false) }
    var cloudStreamRepos by remember { mutableStateOf(repository.getSavedCloudStreamRepos()) }
    var allMovieProviders by remember { mutableStateOf(repository.getAllMovieProviders()) }
    var isAdultHidden by rememberSaveable { mutableStateOf(repository.isAdultContentHidden()) }

    // Deep link repository handler
    LaunchedEffect(deepLinkRepoUrl) {
        if (!deepLinkRepoUrl.isNullOrBlank()) {
            Toast.makeText(context, "রিপোজিটরি প্রসেস করা হচ্ছে...", Toast.LENGTH_SHORT).show()
            val result = repository.installExtensionFromUrl(deepLinkRepoUrl)
            if (result.first) {
                cloudStreamRepos = repository.getSavedCloudStreamRepos()
                allMovieProviders = repository.getAllMovieProviders()
                Toast.makeText(context, result.second, Toast.LENGTH_LONG).show()
                currentTab = AppTab.PLAYLIST
            } else {
                Toast.makeText(context, result.second, Toast.LENGTH_LONG).show()
            }
            onClearDeepLink()
        }
    }

    // App Exit Confirmation State
    var showExitConfirmationDialog by remember { mutableStateOf(false) }

    // Synchronize screen orientation based on selected mode
    LaunchedEffect(activeUserMode, isTvMode) {
        when (activeUserMode) {
            AppUserMode.REMOTE -> {
                isTvMode = true
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
            AppUserMode.MOBILE -> {
                isTvMode = false
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            null -> {
                // On Welcome / Mode Selection Screen
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
    }

    // App Update State
    var availableUpdateInfo by remember { mutableStateOf<AppUpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    // Notification Center State
    var notificationsList by remember { mutableStateOf(repository.getStoredNotifications()) }
    var showNotificationCenterDialog by remember { mutableStateOf(false) }

    // Notification Permission Launcher (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { _ -> }
    )

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        NotificationHelper.initNotificationChannel(context)
        MovieDownloadManager.init(context)
        com.example.util.ChannelStatusManager.init(context)
    }

    // State lists - Instant 0-second startup initialization from persistent cache / built-in defaults + custom streams
    var sportsList by remember {
        mutableStateOf(repository.getInitialSports())
    }
    var liveTvList by remember {
        mutableStateOf(repository.getInitialLiveTv())
    }
    var moviesList by remember {
        mutableStateOf<List<MediaItem>>(emptyList())
    }
    var playlistsList by remember { mutableStateOf(repository.getInitialPlaylists() + repository.getCustomPlaylists()) }
    var adminPlaylistsList by remember { mutableStateOf(repository.getInitialPlaylists() + repository.getAdminPlaylists()) }
    var customList by remember { mutableStateOf(repository.getCustomStreams()) }
    var m3uList by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var favoriteIds by remember { mutableStateOf(repository.getFavoriteIds()) }
    var breakingNewsText by remember { mutableStateOf(repository.getMarqueeTickerText()) }
    var isRefreshing by remember { mutableStateOf(false) }
    var currentLoadingStage by remember { mutableStateOf(LoadingStage.IDLE) }

    fun checkForUpdates(isManualCheck: Boolean = false) {
        coroutineScope.launch {
            try {
                val updateInfo = repository.fetchAppUpdateInfo()
                if (updateInfo != null && com.example.util.AppUpdateHelper.isUpdateAvailable(updateInfo)) {
                    availableUpdateInfo = updateInfo
                    if (isManualCheck || updateInfo.isForceUpdate || !repository.isUpdateDismissed(updateInfo.versionCode, updateInfo.versionName)) {
                        showUpdateDialog = true
                    }
                } else {
                    if (isManualCheck) {
                        Toast.makeText(context, "আপনার অ্যাপটি লেটেস্ট ভার্সনে আছে (v${BuildConfig.VERSION_NAME})", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                if (isManualCheck) {
                    Toast.makeText(context, "আপডেট চেক ব্যর্থ হয়েছে", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // Auto-fetch data: Sequential Staged Loading (1. Live TV -> 2. Live Events -> 3. Movies -> 4. Playlists & Cloud)
    fun refreshAllData() {
        coroutineScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                isRefreshing = true
                currentLoadingStage = LoadingStage.LIVE_TV
            }
            try {
                val deleted = repository.getDeletedIds()

                // Step 0: Sync Remote App Config immediately
                try {
                    repository.fetchAppConfigFromFirebase()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                // -------------------------------------------------------------
                // সমান্তরাল দ্রুত লোডিং (Ultra-Fast Concurrent Staged Loading)
                // সকল ট্যাব (লাইভ টিভি, লাইভ ইভেন্ট, মুভি ও প্লেলিস্ট) একসাথে লোড হবে যেন কোনো বিলম্ব না হয়
                // -------------------------------------------------------------
                kotlinx.coroutines.coroutineScope {
                    // ১. লাইভ টিভি চ্যানেল সমান্তরাল লোডিং (Live TV)
                    launch {
                        try {
                            withContext(Dispatchers.Main) { currentLoadingStage = LoadingStage.LIVE_TV }
                            val liveTvM3uUrl = repository.getSavedLiveTvM3uUrl()
                            val tvM3uJob = async {
                                if (liveTvM3uUrl.isNotBlank()) {
                                    kotlinx.coroutines.withTimeoutOrNull(4000) {
                                        try {
                                            repository.parseM3uFromUrl(liveTvM3uUrl).map {
                                                it.copy(type = MediaType.LIVE_TV, isLive = true)
                                            }.filterNot { deleted.contains(it.id) }
                                        } catch (_: Exception) { emptyList() }
                                    } ?: emptyList()
                                } else emptyList()
                            }

                            val xtreamLiveJob = async {
                                kotlinx.coroutines.withTimeoutOrNull(5000) {
                                    try {
                                        repository.fetchAllXtreamLiveChannels().filterNot { deleted.contains(it.id) }
                                    } catch (_: Exception) { emptyList() }
                                } ?: emptyList()
                            }

                            val customTv = repository.getCustomStreams().filter { it.type == MediaType.LIVE_TV || (it.isLive && it.type != MediaType.LIVE_EVENT) }.filterNot { deleted.contains(it.id) || repository.isDemoChannel(it) }
                            val tvM3u = tvM3uJob.await()
                            val xtreamLiveChannels = xtreamLiveJob.await()

                            val combinedTv = mutableListOf<MediaItem>()
                            combinedTv.addAll(customTv)
                            combinedTv.addAll(tvM3u.filterNot { repository.isDemoChannel(it) })
                            combinedTv.addAll(xtreamLiveChannels.filterNot { repository.isDemoChannel(it) })

                            val sourceTvList = if (combinedTv.isNotEmpty()) combinedTv else repository.getDefaultBuiltinLiveTv()
                            val seenTvIds = HashSet<String>()
                            val updatedTv = sourceTvList.filterNot { repository.isDemoChannel(it) }.mapIndexed { idx, ch ->
                                var uid = ch.id.ifBlank { "tv_${idx}_${Math.abs(ch.title.hashCode())}" }
                                if (seenTvIds.contains(uid)) {
                                    uid = "${uid}_$idx"
                                }
                                seenTvIds.add(uid)
                                ch.copy(id = uid)
                            }

                            if (updatedTv.isNotEmpty()) {
                                withContext(Dispatchers.Main) {
                                    liveTvList = updatedTv
                                }
                                repository.saveCachedLiveTvChannels(liveTvList)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    // ২. লাইভ ইভেন্ট / খেলাধুলা সমান্তরাল লোডিং (Live Events - Only from Admin Sports Match Playlists & APIs)
                    launch {
                        try {
                            withContext(Dispatchers.Main) { currentLoadingStage = LoadingStage.LIVE_EVENTS }
                            val adminPlaylistsSports = repository.fetchAllAdminPlaylistsSports()
                            withContext(Dispatchers.Main) {
                                sportsList = adminPlaylistsSports
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    // ৩. প্লেলিস্ট ও ক্লাউড স্ট্রিম লোডিং (Playlists & Cloud)
                    launch {
                        try {
                            withContext(Dispatchers.Main) { currentLoadingStage = LoadingStage.PLAYLISTS }
                            val initialPlaylists = repository.getInitialPlaylists().filterNot { deleted.contains(it.id) }
                            val adminPlaylists = repository.getAdminPlaylists().filterNot { deleted.contains(it.id) }.map { it.copy(isAdmin = true, isReadOnly = true) }
                            val userPlaylists = repository.getUserPlaylists().filterNot { deleted.contains(it.id) }.map { it.copy(isAdmin = false, isReadOnly = false) }
                            val fbPlaylists = try { repository.fetchPlaylistsFromFirebase() } catch (_: Exception) { emptyList() }
                            val allPlaylists = (adminPlaylists + userPlaylists + fbPlaylists + initialPlaylists)
                                .distinctBy { it.id }
                                .filterNot { deleted.contains(it.id) }

                            withContext(Dispatchers.Main) {
                                adminPlaylistsList = allPlaylists
                                playlistsList = allPlaylists
                            }

                            // Firebase extra items
                            try {
                                val fbItems = try { repository.fetchFromFirebase().filterNot { deleted.contains(it.id) } } catch (_: Exception) { emptyList() }
                                val fbSports = fbItems.filter { it.type == MediaType.LIVE_EVENT }
                                val fbTv = fbItems.filter { it.type == MediaType.LIVE_TV }
                                val fbMov = fbItems.filter { it.type == MediaType.MOVIE || it.type == MediaType.SERIES }

                                withContext(Dispatchers.Main) {
                                    if (fbSports.isNotEmpty()) {
                                        sportsList = (fbSports + sportsList).distinctBy { it.id }.filterNot { repository.isItemDeleted(it) }
                                        repository.saveCachedSportsMatches(sportsList)
                                    }
                                    if (fbTv.isNotEmpty()) {
                                        liveTvList = (liveTvList + fbTv).distinctBy { it.id }.filterNot { deleted.contains(it.id) }
                                        repository.saveCachedLiveTvChannels(liveTvList)
                                    }
                                    if (fbMov.isNotEmpty()) {
                                        moviesList = (moviesList + fbMov).distinctBy { it.id }.filterNot { deleted.contains(it.id) }.sortedByDescending { it.movieSortWeight }
                                        repository.saveCachedMoviesList(moviesList)
                                    }
                                }
                            } catch (_: Exception) {}
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }

                    try { repository.fetchRemoteNotifications() } catch (_: Exception) {}
                    withContext(Dispatchers.Main) {
                        notificationsList = repository.getStoredNotifications()
                        customList = repository.getCustomStreams().filterNot { deleted.contains(it.id) }
                        favoriteIds = repository.getFavoriteIds()
                    }

                    // CloudStream Repos
                    try {
                        val fbRepos = repository.fetchCloudStreamReposFromFirebase()
                        if (fbRepos.isNotEmpty()) {
                            val localRepos = repository.getSavedCloudStreamRepos()
                            val merged = (fbRepos + localRepos).distinctBy { it.id }
                            repository.saveCloudStreamRepos(merged)
                            withContext(Dispatchers.Main) {
                                cloudStreamRepos = merged
                                allMovieProviders = repository.getAllMovieProviders()
                            }
                        }
                    } catch (_: Exception) {}

                    try {
                        val remoteTicker = repository.fetchMarqueeTickerFromFirebase()
                        withContext(Dispatchers.Main) {
                            if (!remoteTicker.isNullOrBlank()) {
                                breakingNewsText = remoteTicker
                            } else {
                                breakingNewsText = repository.getMarqueeTickerText()
                            }
                        }
                    } catch (_: Exception) {}

                    checkForUpdates(isManualCheck = false)

                withContext(Dispatchers.Main) {
                    currentLoadingStage = LoadingStage.COMPLETED
                }
                delay(600)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) {
                    isRefreshing = false
                    currentLoadingStage = LoadingStage.IDLE
                }
            }
        }
    }

    // Periodic user presence heartbeat (Quota optimized: 15-minute pulse)
    LaunchedEffect(currentTab, selectedMediaItem) {
        val activity = when {
            selectedMediaItem != null -> "দেখছেন: ${selectedMediaItem?.title?.take(25)}"
            currentTab == AppTab.EVENTS -> "ইভেন্টস স্ক্রিন"
            currentTab == AppTab.LIVE_TV -> "লাইভ টিভি"
            currentTab == AppTab.PLAYLIST -> "প্লেলিস্ট"
            currentTab == AppTab.MENU -> "মেনু স্ক্রিন"
            else -> "হোম স্ক্রিন"
        }
        while (isActive) {
            try {
                repository.recordUserPresence(activity)
            } catch (_: Exception) {}
            delay(900_000L) // Pulse presence every 15 minutes (quota-safe)
        }
    }

    LaunchedEffect(Unit) {
        // Yield 350ms so Compose renders the immediate cached UI at full 60fps without any startup freeze
        delay(350)
        refreshAllData()
    }

    val handleAddCustomMedia: (MediaItem) -> Unit = { item ->
        repository.saveCustomStream(item)
        customList = repository.getCustomStreams()
        coroutineScope.launch {
            repository.pushToFirebase(item)
            val notif = AppNotification(
                title = if (item.type == MediaType.MOVIE) "🎬 নতুন মুভি: ${item.title}" else "📺 নতুন টিভি চ্যানেল: ${item.title}",
                message = "${item.category} ক্যাটাগরিতে যুক্ত হয়েছে। উপভোগ করুন!",
                type = if (item.type == MediaType.MOVIE) NotificationType.MOVIE else NotificationType.LIVE_TV,
                targetId = item.id,
                imageUrl = item.logoUrl
            )
            repository.broadcastNotification(notif)
            NotificationHelper.showSystemNotification(context, notif)
        }
        refreshAllData()
        Toast.makeText(context, if (item.type == MediaType.MOVIE) "মুভি তালিকায় যুক্ত হয়েছে!" else "চ্যানেল লাইভ তালিকায় যুক্ত হয়েছে!", Toast.LENGTH_SHORT).show()
    }

    if (activeUserMode == null) {
        ModeSelectionScreen(
            repository = repository,
            onSelectMobileMode = {
                isTvMode = false
                activeUserMode = AppUserMode.MOBILE
                repository.saveUserMode("MOBILE")
            },
            onSelectRemoteMode = {
                isTvMode = true
                activeUserMode = AppUserMode.REMOTE
                repository.saveUserMode("REMOTE")
            },
            onExitApp = {
                showExitConfirmationDialog = true
            }
        )
    } else if (selectedMediaItem != null) {
        val currentPlayList = if (activePlaybackPlaylist.isNotEmpty()) {
            activePlaybackPlaylist
        } else {
            when (currentTab) {
                AppTab.LIVE_TV -> mergeChannelsWithServers(liveTvList + customList.filter { it.type == MediaType.LIVE_TV } + m3uList.filter { it.type == MediaType.LIVE_TV || it.isLive })
                AppTab.EVENTS -> sportsList.distinctBy { it.id }
                AppTab.PLAYLIST -> (customList + m3uList).distinctBy { it.id }
                else -> (liveTvList + sportsList + customList + m3uList).distinctBy { it.id }
            }
        }
        VideoPlayerScreen(
            mediaItem = selectedMediaItem!!,
            playlist = currentPlayList,
            isTvMode = isTvMode,
            marqueeTickerText = breakingNewsText,
            onSelectMedia = { selectedMediaItem = it },
            onBack = { selectedMediaItem = null }
        )
    } else if (activeMovieBrowserProvider != null) {
        // IN-APP MOVIE & CLOUDSTREAM WEBSITE BROWSER WITH AD-SHIELD & STREAM DETECTOR
        MovieBrowserScreen(
            repository = repository,
            provider = activeMovieBrowserProvider!!,
            onClose = { activeMovieBrowserProvider = null },
            onPlayDirectMedia = { item ->
                activePlaybackPlaylist = listOf(item)
                selectedMediaItem = item
            }
        )
    } else if (isExtensionsManagementActive) {
        // CLOUDSTREAM EXTENSIONS & REPOSITORIES MANAGEMENT SCREEN
        ExtensionsManagementScreen(
            repository = repository,
            onBack = {
                isExtensionsManagementActive = false
                cloudStreamRepos = repository.getSavedCloudStreamRepos()
                allMovieProviders = repository.getAllMovieProviders()
                refreshAllData()
            },
            onOpenMovieBrowser = { provider ->
                activeMovieBrowserProvider = provider
                isExtensionsManagementActive = false
                currentTab = AppTab.PLAYLIST
                cloudStreamRepos = repository.getSavedCloudStreamRepos()
                allMovieProviders = repository.getAllMovieProviders()
            }
        )
    } else if (isOfflineDownloadsActive) {
        // OFFLINE DOWNLOADED MOVIES & VIDEOS SCREEN
        OfflineDownloadsScreen(
            onPlayOfflineMedia = { item ->
                activePlaybackPlaylist = listOf(item)
                selectedMediaItem = item
            },
            onBack = { isOfflineDownloadsActive = false },
            onExploreMovies = {
                isOfflineDownloadsActive = false
                currentTab = AppTab.PLAYLIST
            }
        )
    } else if (isAdminViewActive) {
        // FULLSCREEN ADMIN CONTROL APP (Exact UI from Screenshot 1 & 3)
        AdminControlAppScreen(
            repository = repository,
            sportsList = sportsList,
            liveTvList = liveTvList + customList.filter { it.type == MediaType.LIVE_TV },
            moviesList = moviesList + customList.filter { it.type == MediaType.MOVIE || it.type == MediaType.SERIES },
            playlistsList = adminPlaylistsList,
            cloudStreamRepos = cloudStreamRepos,
            movieProviders = allMovieProviders,
            onOpenMovieProvider = { activeMovieBrowserProvider = it },
            onExitAdmin = { isAdminViewActive = false },
            onDataChanged = {
                cloudStreamRepos = repository.getSavedCloudStreamRepos()
                allMovieProviders = repository.getAllMovieProviders()
                breakingNewsText = repository.getMarqueeTickerText()
                val initial = repository.getInitialPlaylists()
                val adminPl = repository.getAdminPlaylists().map { it.copy(isAdmin = true, isReadOnly = true) }
                val userPl = repository.getUserPlaylists().map { it.copy(isAdmin = false, isReadOnly = false) }
                adminPlaylistsList = (adminPl + userPl + initial).distinctBy { it.id }
                playlistsList = adminPlaylistsList
                sportsList = repository.getInitialSports().filterNot { repository.isItemDeleted(it) }
                customList = repository.getCustomStreams()
                refreshAllData()
            }
        )
    } else {
        // Intercept back press when at root screens to show Exit Confirmation Dialog
        BackHandler {
            if (currentTab != AppTab.LIVE_TV) {
                currentTab = AppTab.LIVE_TV
            } else {
                showExitConfirmationDialog = true
            }
        }

        if (isTvMode) {
            // TV MODE: Sidebar Menu Rail on Left + Content on Right (Matching uploaded screenshot)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF020617))
            ) {
                // Left Vertical Navigation Rail
                Surface(
                    modifier = Modifier
                        .width(86.dp)
                        .fillMaxHeight()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    color = Color(0xFF0B1328),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 12.dp, horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppTab.values().forEach { tab ->
                            val isSelected = currentTab == tab
                            var isFocused by remember { mutableStateOf(false) }
                            val tabScale by animateFloatAsState(
                                targetValue = if (isFocused) 1.05f else 1.0f,
                                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                                label = "tvTabScale"
                            )
                            val containerColor by animateColorAsState(
                                targetValue = when {
                                    isFocused -> Color(0xFF1E3A8A).copy(alpha = 0.65f)
                                    isSelected -> Color(0xFF2563EB).copy(alpha = 0.35f)
                                    else -> Color.Transparent
                                },
                                animationSpec = tween(150),
                                label = "tvTabColor"
                            )
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = containerColor,
                                border = when {
                                    isFocused -> androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF38BDF8))
                                    isSelected -> androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.6f))
                                    else -> null
                                },
                                shadowElevation = if (isFocused) 4.dp else 0.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .scale(tabScale)
                                    .onFocusChanged { isFocused = it.isFocused }
                                    .focusable()
                                    .clickable { currentTab = tab }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = when (tab) {
                                            AppTab.EVENTS -> Icons.Rounded.EmojiEvents
                                            AppTab.LIVE_TV -> Icons.Rounded.Tv
                                            AppTab.PLAYLIST -> Icons.Rounded.Folder
                                            AppTab.MENU -> Icons.Rounded.Menu
                                        },
                                        contentDescription = tab.englishLabel,
                                        tint = if (isFocused || isSelected) Color(0xFF38BDF8) else Color(0xFF94A3B8),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = tab.englishLabel,
                                        color = if (isFocused || isSelected) Color.White else Color(0xFF94A3B8),
                                        fontSize = 11.sp,
                                        fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1
                                    )
                                    if (isFocused || isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .width(16.dp)
                                                .height(3.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(Color(0xFF38BDF8))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Right Content Area with Top Action Header
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .navigationBarsPadding()
                        .background(Color(0xFF020617))
                ) {
                    // Top Action Bar Header
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A))
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(id = R.drawable.app_logo),
                                    contentDescription = "NAFI TV Logo",
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Fit
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                if (currentTab == AppTab.LIVE_TV) {
                                    Text(
                                        text = "Live TV",
                                        color = Color.White,
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else if (currentTab == AppTab.EVENTS) {
                                    Text(
                                        text = "Live Events",
                                        color = Color.White,
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "NAFI TV 24",
                                                color = Color.White,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFEF4444))
                                            )
                                        }
                                        Text(
                                            text = currentTab.englishLabel,
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }

                            // Action Quick Controls (Mobile / TV Toggle + Refresh)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Switch to Mobile Mode Button (TV -> Mobile)
                                var isTvModeSwitchFocused by remember { mutableStateOf(false) }
                                val tvModeSwitchScale by animateFloatAsState(
                                    targetValue = if (isTvModeSwitchFocused) 1.08f else 1.0f,
                                    animationSpec = tween(180, easing = FastOutSlowInEasing),
                                    label = "tvModeSwitchScale"
                                )
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isTvModeSwitchFocused) Color(0xFF1E3A8A).copy(alpha = 0.8f) else Color(0xFF1E293B),
                                    border = androidx.compose.foundation.BorderStroke(
                                        if (isTvModeSwitchFocused) 2.dp else 1.dp,
                                        if (isTvModeSwitchFocused) Color(0xFF38BDF8) else Color(0xFF00E5FF).copy(alpha = 0.6f)
                                    ),
                                    shadowElevation = if (isTvModeSwitchFocused) 4.dp else 0.dp,
                                    modifier = Modifier
                                        .scale(tvModeSwitchScale)
                                        .onFocusChanged { isTvModeSwitchFocused = it.isFocused }
                                        .focusable()
                                        .clickable {
                                            isTvMode = false
                                            activeUserMode = AppUserMode.MOBILE
                                            repository.saveUserMode("MOBILE")
                                            Toast.makeText(context, "মোবাইল মোডে পরিবর্তন করা হয়েছে!", Toast.LENGTH_SHORT).show()
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.PhoneAndroid,
                                            contentDescription = "মোবাইল মোডে যান",
                                            tint = if (isTvModeSwitchFocused) Color(0xFF38BDF8) else Color(0xFF00E5FF),
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = "মোবাইল মোড",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Notification Bell Button (TV)
                                val tvUnreadNotifCount = remember(notificationsList) { notificationsList.count { !it.isRead } }
                                var isTvNotifFocused by remember { mutableStateOf(false) }
                                val tvNotifScale by animateFloatAsState(
                                    targetValue = if (isTvNotifFocused) 1.08f else 1.0f,
                                    animationSpec = tween(180, easing = FastOutSlowInEasing),
                                    label = "tvNotifScale"
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = if (isTvNotifFocused) Color(0xFF1E3A8A).copy(alpha = 0.6f) else if (tvUnreadNotifCount > 0) Color(0xFF0284C7).copy(alpha = 0.25f) else Color(0xFF1E293B),
                                    border = when {
                                        isTvNotifFocused -> androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF38BDF8))
                                        tvUnreadNotifCount > 0 -> androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8))
                                        else -> null
                                    },
                                    shadowElevation = if (isTvNotifFocused) 4.dp else 0.dp,
                                    modifier = Modifier
                                        .scale(tvNotifScale)
                                        .onFocusChanged { isTvNotifFocused = it.isFocused }
                                        .focusable()
                                        .clickable { showNotificationCenterDialog = true }
                                ) {
                                    Box(modifier = Modifier.padding(7.dp)) {
                                        Icon(
                                            imageVector = if (tvUnreadNotifCount > 0) Icons.Rounded.NotificationsActive else Icons.Rounded.Notifications,
                                            contentDescription = "Notifications",
                                            tint = if (isTvNotifFocused || tvUnreadNotifCount > 0) Color(0xFF38BDF8) else Color.White,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        if (tvUnreadNotifCount > 0) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFEF4444))
                                                    .align(Alignment.TopEnd)
                                            )
                                        }
                                    }
                                }

                                // Refresh Button
                                var isRefreshFocused by remember { mutableStateOf(false) }
                                val refreshScale by animateFloatAsState(
                                    targetValue = if (isRefreshFocused) 1.08f else 1.0f,
                                    animationSpec = tween(180, easing = FastOutSlowInEasing),
                                    label = "refreshScale"
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = if (isRefreshFocused) Color(0xFF1E3A8A).copy(alpha = 0.6f) else Color(0xFF1E293B),
                                    border = if (isRefreshFocused) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF38BDF8)) else null,
                                    shadowElevation = if (isRefreshFocused) 4.dp else 0.dp,
                                    modifier = Modifier
                                        .scale(refreshScale)
                                        .onFocusChanged { isRefreshFocused = it.isFocused }
                                        .focusable()
                                        .clickable { refreshAllData() }
                                ) {
                                    Box(modifier = Modifier.padding(7.dp)) {
                                        if (isRefreshing) {
                                            CircularProgressIndicator(
                                                color = Color(0xFF00E5FF),
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Rounded.Refresh,
                                                contentDescription = "Refresh",
                                                tint = if (isRefreshFocused) Color(0xFF00E5FF) else Color.White,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Phased Sequential Loading Indicator Banner (TV Mode)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isRefreshing && currentLoadingStage != LoadingStage.IDLE,
                        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
                        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically()
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 3.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF0F172A),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    color = Color(0xFF00E5FF),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = currentLoadingStage.title,
                                    color = Color(0xFF38BDF8),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "ধাপ ${currentLoadingStage.step}/4",
                                    color = Color(0xFF10B981),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Breaking News Bar at the very top in TV Mode
                    BreakingNewsTickerBar(
                        tickerText = breakingNewsText,
                        isTvMode = true,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )

                    // Main Content in TV Mode
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                    ) {
                        when (currentTab) {
                            AppTab.EVENTS -> EventsScreen(
                                sports = sportsList.distinctBy { it.id },
                                favoriteIds = favoriteIds,
                                isLoading = isRefreshing,
                                isTvMode = isTvMode,
                                onSelectMedia = {
                                    selectedMediaItem = it
                                    activePlaybackPlaylist = sportsList.distinctBy { sp -> sp.id }
                                },
                                onToggleFavorite = { id ->
                                    repository.toggleFavorite(id)
                                    favoriteIds = repository.getFavoriteIds()
                                }
                            )

                            AppTab.LIVE_TV -> {
                                LiveTvTabScreen(
                                    channels = remember(liveTvList, customList, m3uList) {
                                        liveTvList + customList.filter { it.type == MediaType.LIVE_TV } + m3uList.filter { it.type == MediaType.LIVE_TV || it.isLive }
                                    },
                                    favoriteIds = favoriteIds,
                                    isLoading = isRefreshing,
                                    isTvMode = isTvMode,
                                    isAdultHidden = isAdultHidden,
                                    repository = repository,
                                    onSelectMedia = { item, playlist ->
                                        selectedMediaItem = item
                                        activePlaybackPlaylist = playlist
                                    },
                                    onToggleFavorite = { id ->
                                        repository.toggleFavorite(id)
                                        favoriteIds = repository.getFavoriteIds()
                                    },
                                    onAddChannel = handleAddCustomMedia
                                )
                            }


                            AppTab.PLAYLIST -> PlaylistTabScreen(
                                playlists = playlistsList,
                                repository = repository,
                                isTvMode = isTvMode,
                                isAdultHidden = isAdultHidden,
                                onSelectMedia = { item, playlist ->
                                    selectedMediaItem = item
                                    activePlaybackPlaylist = playlist
                                },
                                onPlaylistsChanged = { refreshAllData() }
                            )

                            AppTab.MENU -> MenuScreen(
                                repository = repository,
                                customList = customList,
                                isTvMode = true,
                                isAdultHidden = isAdultHidden,
                                onToggleAdultHidden = { nextState ->
                                    repository.setHideAdultContent(nextState)
                                    isAdultHidden = nextState
                                    Toast.makeText(
                                        context,
                                        if (nextState) "১৮+ অ্যাডাল্ট কনটেন্ট সফলভাবে লুকানো হয়েছে (Safe Mode সক্রিয়)"
                                        else "অ্যাডাল্ট সিনেমা ও চ্যানেল দৃশ্যমান করা হয়েছে",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                onSwitchToMobileMode = {
                                    isTvMode = false
                                    activeUserMode = AppUserMode.MOBILE
                                    repository.saveUserMode("MOBILE")
                                    Toast.makeText(context, "মোবাইল মোডে পরিবর্তন করা হয়েছে!", Toast.LENGTH_SHORT).show()
                                },
                                onSwitchToTvMode = {
                                    isTvMode = true
                                    activeUserMode = AppUserMode.REMOTE
                                    repository.saveUserMode("REMOTE")
                                    Toast.makeText(context, "টিভি রিমোট মোডে পরিবর্তন করা হয়েছে!", Toast.LENGTH_SHORT).show()
                                },
                                onResetModeSelection = {
                                    repository.saveUserMode(null)
                                    activeUserMode = null
                                },
                                onOpenAdminApp = { isAdminViewActive = true },
                                onOpenOfflineDownloads = { isOfflineDownloadsActive = true },
                                onOpenExtensionManager = { isExtensionsManagementActive = true },
                                onCheckForUpdates = { checkForUpdates(isManualCheck = true) },
                                availableUpdateInfo = availableUpdateInfo,
                                onPlayDirectStream = { url, title ->
                                    val lowerUrl = url.lowercase().trim()
                                    val isMovieOrVod = lowerUrl.contains("movielinkbd") ||
                                            lowerUrl.contains("mlbd") ||
                                            (lowerUrl.contains("/media/") && lowerUrl.contains("token=")) ||
                                            lowerUrl.contains("pixeldrain") ||
                                            lowerUrl.contains("drive.google") ||
                                            lowerUrl.contains("dropbox") ||
                                            lowerUrl.contains("mediafire") ||
                                            lowerUrl.contains(".mp4") ||
                                            lowerUrl.contains(".mkv") ||
                                            lowerUrl.contains(".webm") ||
                                            lowerUrl.contains(".avi") ||
                                            lowerUrl.contains(".mov") ||
                                            lowerUrl.contains("/movie/") ||
                                            lowerUrl.contains("/series/") ||
                                            title.contains("movie", ignoreCase = true) ||
                                            title.contains("মুভি", ignoreCase = true) ||
                                            title.contains("film", ignoreCase = true)

                                    val cleanTitle = if (title.isNotBlank()) title else {
                                        if (lowerUrl.contains("movielinkbd") || (lowerUrl.contains("/media/") && lowerUrl.contains("token="))) {
                                            "MovieLinkBD Video"
                                        } else if (isMovieOrVod) {
                                            "Movie Stream"
                                        } else {
                                            "Direct Stream"
                                        }
                                    }

                                    val directItem = MediaItem(
                                         id = "direct_${System.currentTimeMillis()}",
                                         title = cleanTitle,
                                         category = if (isMovieOrVod) "Movies & Videos" else "Direct Stream",
                                         type = if (isMovieOrVod) MediaType.MOVIE else MediaType.LIVE_TV,
                                         streamUrl = url,
                                         isLive = !isMovieOrVod
                                    )
                                    selectedMediaItem = directItem
                                    activePlaybackPlaylist = listOf(directItem)
                                },
                                onM3uLoaded = { list ->
                                    m3uList = list
                                    currentTab = AppTab.PLAYLIST
                                },
                                onCustomAdded = handleAddCustomMedia,
                                onResetDefaults = {
                                    repository.resetToDefaults()
                                    sportsList = repository.getInitialSports()
                                    liveTvList = repository.getInitialLiveTv()
                                    moviesList = repository.getInitialMoviesSeries()
                                    customList = emptyList()
                                    m3uList = emptyList()
                                    favoriteIds = emptySet()
                                    Toast.makeText(context, "ডিফল্ট রিসেট সফল হয়েছে!", Toast.LENGTH_SHORT).show()
                                }
                            )
                        }
                    }

                    // TV Non-Intrusive Banner Ad (Zero disturbance, collapsible, hidden during video play)
                    NonIntrusiveAdMobBanner(isTvMode = true)
                }
            }
        } else {
            // MOBILE MODE: Standard Scaffold with Top Bar + Content + Bottom Navigation Bar
            Scaffold(
                topBar = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A))
                            .statusBarsPadding()
                    ) {
                        // Top Action Bar Header
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Image(
                                    painter = painterResource(id = R.drawable.app_logo),
                                    contentDescription = "NAFI TV Logo",
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp)),
                                    contentScale = ContentScale.Fit
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                if (currentTab == AppTab.LIVE_TV) {
                                    Text(
                                        text = "Live TV",
                                        color = Color.White,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else if (currentTab == AppTab.EVENTS) {
                                    Text(
                                        text = "Live Events",
                                        color = Color.White,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "NAFI TV 24",
                                                color = Color.White,
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFEF4444))
                                            )
                                        }
                                        Text(
                                            text = currentTab.englishLabel,
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }

                            // Action Quick Controls
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // TV Mode Switch Chip (Mobile -> TV Remote Mode)
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color(0xFF0284C7).copy(alpha = 0.25f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.8f)),
                                    modifier = Modifier.clickable {
                                        isTvMode = true
                                        activeUserMode = AppUserMode.REMOTE
                                        repository.saveUserMode("REMOTE")
                                        Toast.makeText(context, "টিভি রিমোট মোডে পরিবর্তন করা হয়েছে!", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Tv,
                                            contentDescription = "টিভি মোডে যান",
                                            tint = Color(0xFF38BDF8),
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = "টিভি মোড",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Notification Bell Button (Mobile)
                                val mobUnreadNotifCount = remember(notificationsList) { notificationsList.count { !it.isRead } }
                                Surface(
                                    shape = CircleShape,
                                    color = if (mobUnreadNotifCount > 0) Color(0xFF0284C7).copy(alpha = 0.25f) else Color(0xFF1E293B),
                                    border = if (mobUnreadNotifCount > 0) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8)) else null,
                                    modifier = Modifier.clickable { showNotificationCenterDialog = true }
                                ) {
                                    Box(
                                        modifier = Modifier.padding(8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (mobUnreadNotifCount > 0) Icons.Rounded.NotificationsActive else Icons.Rounded.Notifications,
                                            contentDescription = "নোটিফিকেশন",
                                            tint = if (mobUnreadNotifCount > 0) Color(0xFF38BDF8) else Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        if (mobUnreadNotifCount > 0) {
                                            Box(
                                                modifier = Modifier
                                                    .size(7.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFEF4444))
                                                    .align(Alignment.TopEnd)
                                            )
                                        }
                                    }
                                }

                                // Refresh Button
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF1E293B),
                                    modifier = Modifier.clickable { refreshAllData() }
                                ) {
                                    Box(modifier = Modifier.padding(8.dp)) {
                                        if (isRefreshing) {
                                            CircularProgressIndicator(
                                                color = Color(0xFF00E5FF),
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Rounded.Refresh,
                                                contentDescription = "Refresh",
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Phased Sequential Loading Indicator Banner (Mobile Mode)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isRefreshing && currentLoadingStage != LoadingStage.IDLE,
                        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
                        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically()
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF0F172A),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(13.dp),
                                    color = Color(0xFF00E5FF),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    text = currentLoadingStage.title,
                                    color = Color(0xFF38BDF8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "ধাপ ${currentLoadingStage.step}/4",
                                    color = Color(0xFF10B981),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // App-wide Breaking News Ticker Bar at the very top of mobile app
                        BreakingNewsTickerBar(
                            tickerText = breakingNewsText,
                            isTvMode = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 2.dp)
                        )
                    }
                },
                bottomBar = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A))
                    ) {
                        // Mobile Non-Intrusive Banner Ad (Collapses automatically if failed or dismissed)
                        NonIntrusiveAdMobBanner(isTvMode = false)

                        NavigationBar(
                            containerColor = Color(0xFF0F172A),
                            contentColor = Color.White
                        ) {
                            AppTab.values().forEach { tab ->
                                val selected = currentTab == tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { currentTab = tab },
                                    icon = {
                                        Icon(
                                            imageVector = when (tab) {
                                                AppTab.EVENTS -> Icons.Rounded.EmojiEvents
                                                AppTab.LIVE_TV -> Icons.Rounded.Tv
                                                AppTab.PLAYLIST -> Icons.Rounded.Folder
                                                AppTab.MENU -> Icons.Rounded.Menu
                                            },
                                            contentDescription = tab.englishLabel,
                                            tint = if (selected) Color(0xFF00E5FF) else Color(0xFF94A3B8)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = tab.englishLabel,
                                            color = if (selected) Color(0xFF00E5FF) else Color(0xFF94A3B8),
                                            fontSize = 11.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = Color(0xFF1E293B)
                                    )
                                )
                            }
                        }
                    }
                },
                containerColor = Color(0xFF020617)
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    when (currentTab) {
                        AppTab.EVENTS -> EventsScreen(
                            sports = sportsList.distinctBy { it.id },
                            favoriteIds = favoriteIds,
                            isLoading = isRefreshing,
                            isTvMode = isTvMode,
                            onSelectMedia = {
                                selectedMediaItem = it
                                activePlaybackPlaylist = sportsList.distinctBy { sp -> sp.id }
                            },
                            onToggleFavorite = { id ->
                                repository.toggleFavorite(id)
                                favoriteIds = repository.getFavoriteIds()
                            }
                        )

                        AppTab.LIVE_TV -> {
                            LiveTvTabScreen(
                                channels = remember(liveTvList, customList, m3uList) {
                                    liveTvList + customList.filter { it.type == MediaType.LIVE_TV } + m3uList.filter { it.type == MediaType.LIVE_TV || it.isLive }
                                },
                                favoriteIds = favoriteIds,
                                isLoading = isRefreshing,
                                isTvMode = isTvMode,
                                isAdultHidden = isAdultHidden,
                                repository = repository,
                                onSelectMedia = { item, playlist ->
                                    selectedMediaItem = item
                                    activePlaybackPlaylist = playlist
                                },
                                onToggleFavorite = { id ->
                                    repository.toggleFavorite(id)
                                    favoriteIds = repository.getFavoriteIds()
                                },
                                onAddChannel = handleAddCustomMedia
                            )
                        }


                        AppTab.PLAYLIST -> PlaylistTabScreen(
                            playlists = playlistsList,
                            repository = repository,
                            isTvMode = isTvMode,
                            isAdultHidden = isAdultHidden,
                            onSelectMedia = { item, playlist ->
                                selectedMediaItem = item
                                activePlaybackPlaylist = playlist
                            },
                            onPlaylistsChanged = { refreshAllData() }
                        )

                        AppTab.MENU -> MenuScreen(
                            repository = repository,
                            customList = customList,
                            isTvMode = false,
                            isAdultHidden = isAdultHidden,
                            onToggleAdultHidden = { nextState ->
                                repository.setHideAdultContent(nextState)
                                isAdultHidden = nextState
                                Toast.makeText(
                                    context,
                                    if (nextState) "১৮+ অ্যাডাল্ট কনটেন্ট সফলভাবে লুকানো হয়েছে (Safe Mode সক্রিয়)"
                                    else "অ্যাডাল্ট সিনেমা ও চ্যানেল দৃশ্যমান করা হয়েছে",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            onSwitchToMobileMode = {
                                isTvMode = false
                                activeUserMode = AppUserMode.MOBILE
                                repository.saveUserMode("MOBILE")
                                Toast.makeText(context, "মোবাইল মোডে পরিবর্তন করা হয়েছে!", Toast.LENGTH_SHORT).show()
                            },
                            onSwitchToTvMode = {
                                isTvMode = true
                                activeUserMode = AppUserMode.REMOTE
                                repository.saveUserMode("REMOTE")
                                Toast.makeText(context, "টিভি রিমোট মোডে পরিবর্তন করা হয়েছে!", Toast.LENGTH_SHORT).show()
                            },
                            onResetModeSelection = {
                                repository.saveUserMode(null)
                                activeUserMode = null
                            },
                            onOpenAdminApp = { isAdminViewActive = true },
                            onOpenOfflineDownloads = { isOfflineDownloadsActive = true },
                            onOpenExtensionManager = { isExtensionsManagementActive = true },
                            onCheckForUpdates = { checkForUpdates(isManualCheck = true) },
                            availableUpdateInfo = availableUpdateInfo,
                            onPlayDirectStream = { url, title ->
                                val lowerUrl = url.lowercase().trim()
                                val isMovieOrVod = lowerUrl.contains("movielinkbd") ||
                                        lowerUrl.contains("mlbd") ||
                                        (lowerUrl.contains("/media/") && lowerUrl.contains("token=")) ||
                                        lowerUrl.contains("pixeldrain") ||
                                        lowerUrl.contains("drive.google") ||
                                        lowerUrl.contains("dropbox") ||
                                        lowerUrl.contains("mediafire") ||
                                        lowerUrl.contains(".mp4") ||
                                        lowerUrl.contains(".mkv") ||
                                        lowerUrl.contains(".webm") ||
                                        lowerUrl.contains(".avi") ||
                                        lowerUrl.contains(".mov") ||
                                        lowerUrl.contains("/movie/") ||
                                        lowerUrl.contains("/series/") ||
                                        title.contains("movie", ignoreCase = true) ||
                                        title.contains("মুভি", ignoreCase = true) ||
                                        title.contains("film", ignoreCase = true)

                                val cleanTitle = if (title.isNotBlank()) title else {
                                    if (lowerUrl.contains("movielinkbd") || (lowerUrl.contains("/media/") && lowerUrl.contains("token="))) {
                                        "MovieLinkBD Video"
                                    } else if (isMovieOrVod) {
                                        "Movie Stream"
                                    } else {
                                        "Direct Stream"
                                    }
                                }

                                val directItem = MediaItem(
                                    id = "direct_${System.currentTimeMillis()}",
                                    title = cleanTitle,
                                    category = if (isMovieOrVod) "Movies & Videos" else "Direct Stream",
                                    type = if (isMovieOrVod) MediaType.MOVIE else MediaType.LIVE_TV,
                                    streamUrl = url,
                                    isLive = !isMovieOrVod
                                )
                                selectedMediaItem = directItem
                                activePlaybackPlaylist = listOf(directItem)
                            },
                            onM3uLoaded = { list ->
                                m3uList = list
                                currentTab = AppTab.PLAYLIST
                            },
                            onCustomAdded = handleAddCustomMedia,
                            onResetDefaults = {
                                repository.resetToDefaults()
                                sportsList = repository.getInitialSports()
                                liveTvList = repository.getInitialLiveTv()
                                moviesList = repository.getInitialMoviesSeries()
                                customList = emptyList()
                                m3uList = emptyList()
                                favoriteIds = emptySet()
                                Toast.makeText(context, "ডিফল্ট রিসেট সফল হয়েছে!", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }

    // In-App Update Dialog (Beautiful Animated M3 Popup for Auto-Updating App)
    if (showUpdateDialog && availableUpdateInfo != null) {
        val info = availableUpdateInfo!!
        AppUpdateDialog(
            updateInfo = info,
            onDismiss = {
                repository.dismissUpdate(info.versionCode, info.versionName)
                showUpdateDialog = false
            }
        )
    }

    // Notification Center Dialog (In-App notification board for matches, channels, movies and announcements)
    if (showNotificationCenterDialog) {
        NotificationCenterDialog(
            notifications = notificationsList,
            isTvMode = isTvMode,
            onDismiss = { showNotificationCenterDialog = false },
            onSelectNotification = { notif ->
                repository.markNotificationAsRead(notif.id)
                notificationsList = repository.getStoredNotifications()
                showNotificationCenterDialog = false
                when (notif.type) {
                    NotificationType.LIVE_EVENT -> {
                        currentTab = AppTab.EVENTS
                        if (!notif.targetId.isNullOrBlank()) {
                            val matched = sportsList.find { it.id == notif.targetId }
                            if (matched != null) {
                                selectedMediaItem = matched
                                activePlaybackPlaylist = sportsList
                            }
                        }
                    }
                    NotificationType.LIVE_TV -> {
                        currentTab = AppTab.LIVE_TV
                        if (!notif.targetId.isNullOrBlank()) {
                            val allTv = mergeChannelsWithServers(liveTvList + customList.filter { it.type == MediaType.LIVE_TV } + m3uList.filter { it.type == MediaType.LIVE_TV || it.isLive })
                            val matched = allTv.find { it.id == notif.targetId }
                            if (matched != null) {
                                selectedMediaItem = matched
                                activePlaybackPlaylist = allTv
                            }
                        }
                    }
                    NotificationType.MOVIE -> {
                        currentTab = AppTab.LIVE_TV
                    }
                    NotificationType.PLAYLIST -> {
                        currentTab = AppTab.PLAYLIST
                    }
                    NotificationType.APP_UPDATE -> {
                        checkForUpdates(isManualCheck = true)
                    }
                    else -> {}
                }
            },
            onMarkAllRead = {
                repository.markAllNotificationsAsRead()
                notificationsList = repository.getStoredNotifications()
            },
            onClearAll = {
                repository.clearAllNotifications()
                notificationsList = emptyList()
            },
            onDeleteNotification = { id ->
                repository.deleteNotification(id)
                notificationsList = repository.getStoredNotifications()
            }
        )
    }

    // App Exit Confirmation Dialog (User requested: মোবাইলের ব্যাক বাটনে ক্লিক করলে যেন অ্যাপ থেকে বের হওয়ার আগে পারমিশন চায়)
    if (showExitConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showExitConfirmationDialog = false },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color(0xFF1E293B),
            tonalElevation = 8.dp,
            icon = {
                Image(
                    painter = painterResource(id = R.drawable.app_logo),
                    contentDescription = "NAFI TV Logo",
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit
                )
            },
            title = {
                Text(
                    text = "অ্যাপ থেকে বের হতে চান?",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    text = "আপনি কি নিশ্চিতভাবে NAFI TV 24 অ্যাপটি বন্ধ করতে চান?",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            showExitConfirmationDialog = false
                            repository.saveUserMode(null)
                            activeUserMode = null
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                        modifier = Modifier.height(42.dp)
                    ) {
                        Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("মোড পরিবর্তন", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = {
                            showExitConfirmationDialog = false
                            activity?.finish()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFEF4444),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(42.dp)
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("হ্যাঁ, বের হন", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showExitConfirmationDialog = false },
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF475569)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFCBD5E1)),
                    modifier = Modifier.height(42.dp)
                ) {
                    Text("না, থাকুন", fontSize = 13.sp)
                }
            }
        )
    }
}

// -------------------------------------------------------------
// SCREEN: ADMIN CONTROL APP (Exact UI from Screenshot 3)
// -------------------------------------------------------------
@Composable
fun MenuScreen(
    repository: MediaRepository,
    customList: List<MediaItem>,
    isTvMode: Boolean = false,
    isAdultHidden: Boolean = true,
    onToggleAdultHidden: (Boolean) -> Unit = {},
    onSwitchToMobileMode: () -> Unit = {},
    onSwitchToTvMode: () -> Unit = {},
    onResetModeSelection: () -> Unit = {},
    onOpenAdminApp: () -> Unit,
    onOpenExtensionManager: () -> Unit = {},
    onOpenOfflineDownloads: () -> Unit = {},
    onPlayDirectStream: (url: String, title: String) -> Unit,
    onM3uLoaded: (List<MediaItem>) -> Unit,
    onCustomAdded: (MediaItem) -> Unit,
    onResetDefaults: () -> Unit,
    onCheckForUpdates: () -> Unit = {},
    availableUpdateInfo: AppUpdateInfo? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 1. Direct Stream State
    var directUrl by remember { mutableStateOf("") }
    var directTitle by remember { mutableStateOf("") }

    // 2. Load Custom M3U State
    var remoteM3uUrl by remember { mutableStateOf("") }
    var isLoadingM3u by remember { mutableStateOf(false) }

    // File picker launcher for .m3u / .m3u8
    val m3uFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val parsed = repository.parseM3uFromUri(it)
            if (parsed.isNotEmpty()) {
                onM3uLoaded(parsed)
                Toast.makeText(context, "${parsed.size} টি চ্যানেল ফাইল থেকে লোড হয়েছে!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "প্লেলিস্ট ফাইলটি পড়া যায়নি!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 3. Add Custom TV Channel State
    var channelName by remember { mutableStateOf("") }
    var channelCategory by remember { mutableStateOf("Sports") }
    var channelStreamUrl by remember { mutableStateOf("") }
    var channelLogoUrl by remember { mutableStateOf("") }
    val categoryOptions = listOf("Sports", "News", "Entertainment", "Movie", "Music", "Kids", "Infotainment", "Religious")
    var categoryDropdownExpanded by remember { mutableStateOf(false) }

    // 4. Admin Privacy / Login Dialog
    var showAdminLoginDialog by remember { mutableStateOf(false) }
    var adminPinInput by remember { mutableStateOf("") }
    var adminLoginError by remember { mutableStateOf<String?>(null) }

    // 5. Reset Defaults Dialog
    var showResetConfirmDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // CARD -1: App Display Mode Switcher (মোবাইল / টিভি রিমোট মোড নির্বাচন ও পরিবর্তন)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF38BDF8).copy(alpha = 0.15f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isTvMode) Icons.Rounded.Tv else Icons.Rounded.PhoneAndroid,
                                        contentDescription = "App Mode",
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "অ্যাপ ডিসপ্লে মোড (App Mode)",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isTvMode) "বর্তমান মোড: টিভি রিমোট মোড (Landscape)" else "বর্তমান মোড: মোবাইল মোড (Portrait)",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Text(
                        text = "আপনি যেকোনো সময় আপনার সুবিধাজনক মোডে পরিবর্তন করতে পারেন। একবার নির্বাচন করলে পরবর্তীতে সরাসরি সেই মোডেই অ্যাপ চালু হবে।",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!isTvMode) {
                            Button(
                                onClick = onSwitchToTvMode,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(42.dp)
                            ) {
                                Icon(Icons.Rounded.Tv, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("টিভি মোডে যান", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = onSwitchToMobileMode,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f).height(42.dp)
                            ) {
                                Icon(Icons.Rounded.PhoneAndroid, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("মোবাইল মোডে যান", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedButton(
                            onClick = onResetModeSelection,
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF475569)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFCBD5E1)),
                            modifier = Modifier.height(42.dp)
                        ) {
                            Icon(Icons.Rounded.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("মোড চয়েস পেজ", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // CARD -0.5: 🔞 Adult & 18+ Content Safe Mode (হাইড বা আনহাইড করার সেটিংস)
        item {
            var tvFocusedSafeMode by remember { mutableStateOf(false) }
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = androidx.compose.foundation.BorderStroke(
                    if (isTvMode && tvFocusedSafeMode) 2.dp else 1.dp,
                    if (isTvMode && tvFocusedSafeMode) Color(0xFF38BDF8)
                    else if (isAdultHidden) Color(0xFF10B981).copy(alpha = 0.65f)
                    else Color(0xFFEF4444).copy(alpha = 0.65f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isTvMode) {
                            Modifier
                                .onFocusChanged { tvFocusedSafeMode = it.isFocused }
                                .focusable()
                                .clickable {
                                    onToggleAdultHidden(!isAdultHidden)
                                }
                        } else Modifier
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isAdultHidden) Color(0xFF10B981).copy(alpha = 0.18f) else Color(0xFFEF4444).copy(alpha = 0.18f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isAdultHidden) Icons.Rounded.Security else Icons.Rounded.WarningAmber,
                                        contentDescription = "Safe Mode",
                                        tint = if (isAdultHidden) Color(0xFF10B981) else Color(0xFFEF4444),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Column(modifier = Modifier.padding(end = 8.dp)) {
                                Text(
                                    text = "🔞 ১৮+ ও অ্যাডাল্ট কনটেন্ট ফিল্টার",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (isAdultHidden)
                                        "অ্যাডাল্ট সিনেমা, সিরিজ ও চ্যানেল লুকানো আছে (নিরাপদ মোড)"
                                    else
                                        "১৮+ অ্যাডাল্ট সিনেমা ও চ্যানেল দৃশ্যমান আছে",
                                    color = if (isAdultHidden) Color(0xFF6EE7B7) else Color(0xFFFCA5A5),
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Switch(
                            checked = isAdultHidden,
                            onCheckedChange = { nextState ->
                                onToggleAdultHidden(nextState)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF10B981),
                                uncheckedThumbColor = Color(0xFFE2E8F0),
                                uncheckedTrackColor = Color(0xFF475569)
                            )
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF0F172A),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "ℹ️ এই অপশনটি ডিফল্টভাবে চালু থাকবে যাতে পরিবার ও পরিবারের সদস্যদের সাথে নিরাপদে ব্যবহার করা যায়। আপনি সুইচটি অফ করলে ১৮+ সিনেমা ও চ্যানেলগুলো আনলক ও দৃশ্যমান হবে।",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }

        // CARD 0: Offline Downloads Library
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenOfflineDownloads() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF00E5FF).copy(alpha = 0.15f),
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.DownloadForOffline,
                                    contentDescription = null,
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "📥 অফলাইন ডাউনলোডসমূহ (Offline Library)",
                                color = Color.White,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "ডাউনলোডকৃত মুভি ও ভিডিও ইন্টারনেট ছাড়াই উপভোগ করুন",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.5.sp
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        // CARD 1: Play Direct Stream Link (HLS / DASH / MP4)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Play Direct Stream / Movie Link (HLS / MP4 / MKV / MovieLinkBD)",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedTextField(
                        value = directUrl,
                        onValueChange = { directUrl = it },
                        placeholder = { Text("Enter stream or movie URL (m3u8, mp4, mkv, movielinkbd, etc.)", color = Color(0xFF64748B), fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = customFieldColors(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = directTitle,
                            onValueChange = { directTitle = it },
                            placeholder = { Text("Stream Title (Optional)", color = Color(0xFF64748B), fontSize = 13.sp) },
                            modifier = Modifier.weight(1f),
                            colors = customFieldColors(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        Button(
                            onClick = {
                                if (directUrl.isNotBlank()) {
                                    onPlayDirectStream(directUrl.trim(), directTitle.trim())
                                } else {
                                    Toast.makeText(context, "দয়া করে স্ট্রিম লিঙ্ক লিখুন", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2563EB),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Text("Play Now", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // CARD 2: Load Custom M3U Playlist
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Link,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Load Custom M3U Playlist",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = remoteM3uUrl,
                            onValueChange = { remoteM3uUrl = it },
                            placeholder = { Text("Remote M3U URL (e.g. https://.../list.m3u)", color = Color(0xFF64748B), fontSize = 13.sp) },
                            modifier = Modifier.weight(1f),
                            colors = customFieldColors(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        Button(
                            onClick = {
                                if (remoteM3uUrl.isNotBlank()) {
                                    isLoadingM3u = true
                                    repository.saveM3uUrl(remoteM3uUrl.trim())
                                    coroutineScope.launch {
                                        val parsed = repository.parseM3uFromUrl(remoteM3uUrl.trim())
                                        isLoadingM3u = false
                                        if (parsed.isNotEmpty()) {
                                            onM3uLoaded(parsed)
                                            Toast.makeText(context, "${parsed.size} টি চ্যানেল লোড হয়েছে!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "M3U লিঙ্কটি কাজ করছে না", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2563EB),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(52.dp)
                        ) {
                            if (isLoadingM3u) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Load", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Upload .m3u / .m3u8 File from Device button
                    OutlinedButton(
                        onClick = { m3uFileLauncher.launch("*/*") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E5FF)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FileUpload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Upload .m3u / .m3u8 File from Device",
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // CARD 3: Add Custom TV Channel
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.AddCircleOutline,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Add Custom TV Channel",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    OutlinedTextField(
                        value = channelName,
                        onValueChange = { channelName = it },
                        placeholder = { Text("Channel Name (e.g. Sports HD)", color = Color(0xFF64748B), fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = customFieldColors(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = channelCategory,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { categoryDropdownExpanded = !categoryDropdownExpanded }) {
                                    Icon(
                                        imageVector = Icons.Rounded.ArrowDropDown,
                                        contentDescription = "Dropdown",
                                        tint = Color.White
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { categoryDropdownExpanded = true },
                            colors = customFieldColors(),
                            shape = RoundedCornerShape(12.dp)
                        )

                        DropdownMenu(
                            expanded = categoryDropdownExpanded,
                            onDismissRequest = { categoryDropdownExpanded = false },
                            modifier = Modifier.background(Color(0xFF1E293B))
                        ) {
                            categoryOptions.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat, color = Color.White) },
                                    onClick = {
                                        channelCategory = cat
                                        categoryDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = channelStreamUrl,
                        onValueChange = { channelStreamUrl = it },
                        placeholder = { Text("Stream URL (.m3u8 or video link)", color = Color(0xFF64748B), fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = customFieldColors(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    OutlinedTextField(
                        value = channelLogoUrl,
                        onValueChange = { channelLogoUrl = it },
                        placeholder = { Text("Logo Image URL (Optional)", color = Color(0xFF64748B), fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = customFieldColors(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Button(
                        onClick = {
                            if (channelName.isNotBlank() && channelStreamUrl.isNotBlank()) {
                                val lowerStream = channelStreamUrl.lowercase().trim()
                                val isMovie = channelCategory.equals("Movie", true) || channelCategory.equals("Cinema", true) ||
                                        lowerStream.contains("movielinkbd") || lowerStream.contains("mlbd") ||
                                        (lowerStream.contains("/media/") && lowerStream.contains("token=")) ||
                                        lowerStream.contains(".mp4") || lowerStream.contains(".mkv") || lowerStream.contains(".webm") ||
                                        lowerStream.contains("/movie/") || lowerStream.contains("/series/")
                                val item = MediaItem(
                                    id = if (isMovie) "mov_${System.currentTimeMillis()}" else "tv_${System.currentTimeMillis()}",
                                    title = channelName.trim(),
                                    category = channelCategory.trim().ifBlank { if (isMovie) "Movie" else "Live TV" },
                                    type = if (isMovie) MediaType.MOVIE else MediaType.LIVE_TV,
                                    streamUrl = channelStreamUrl.trim(),
                                    servers = listOf(StreamServer("সার্ভার ১", channelStreamUrl.trim())),
                                    logoUrl = channelLogoUrl.trim().ifBlank { null },
                                    isLive = !isMovie
                                )
                                onCustomAdded(item)
                                channelName = ""
                                channelStreamUrl = ""
                                channelLogoUrl = ""
                            } else {
                                Toast.makeText(context, "চ্যানেলের নাম এবং স্ট্রিম লিংক লিখুন", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2563EB),
                            contentColor = Color.White
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlaylistAdd,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Add to Live TV List",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // CARD 4: এক্সটেনশন ম্যানেজার (CloudStream Extensions & Repositories)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF8B5CF6).copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Extension,
                                contentDescription = "Extensions",
                                tint = Color(0xFFC084FC),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "এক্সটেনশন ও রিপোজিটরি ম্যানেজার",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Phisher, CloudStream রিপো, কাস্টম URL ও লোকাল JSON প্লাগইন ম্যানেজ করুন",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                maxLines = 2
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = onOpenExtensionManager,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF8B5CF6),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SettingsSuggest,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Manage",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // CARD 5: App Version & In-App Update Check
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF2563EB).copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.RocketLaunch,
                                    contentDescription = "Update Rocket",
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "অ্যাপ আপডেট ও ভার্সন",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "বর্তমান ভার্সন: v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        val isNewUpdateReady = com.example.util.AppUpdateHelper.isUpdateAvailable(availableUpdateInfo)
                        if (availableUpdateInfo != null && isNewUpdateReady) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFEF4444).copy(alpha = 0.2f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444))
                            ) {
                                Text(
                                    text = "v${availableUpdateInfo.versionName} প্রস্তুত!",
                                    color = Color(0xFFF87171),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "নতুন নতুন টিভি চ্যানেল, ফাস্ট স্পোর্টস সার্ভার এবং উন্নত ভিডিও প্লেয়ার সুবিধার জন্য অ্যাপ নিয়মিত আপডেট রাখুন।",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )

                    val hasUpdate = com.example.util.AppUpdateHelper.isUpdateAvailable(availableUpdateInfo)
                    Button(
                        onClick = onCheckForUpdates,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (hasUpdate) Color(0xFF10B981) else Color(0xFF2563EB),
                            contentColor = if (hasUpdate) Color.Black else Color.White
                        )
                    ) {
                        Icon(
                            imageVector = if (hasUpdate) Icons.Rounded.Download else Icons.Rounded.SystemUpdate,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (hasUpdate && availableUpdateInfo != null) "📥 এখনই নতুন ভার্সন আপডেট করুন (v${availableUpdateInfo.versionName})" else "🚀 নতুন আপডেট চেক করুন (Check Updates)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        // Subtle & Stealthy Admin Entrance (সাধারণ ইউজাররা সহজে বুঝতে পারবে না)
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showAdminLoginDialog = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        tint = Color(0xFF475569).copy(alpha = 0.6f),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "System Core v2.5",
                        color = Color(0xFF475569).copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }
        }

        // CARD 6: About NAFI TV 24
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.app_logo),
                            contentDescription = "NAFI TV Logo",
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Fit
                        )
                        Column {
                            Text(
                                text = "NAFI TV 24",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Ultimate Live TV & Sports Streaming App",
                                color = Color(0xFF00E5FF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Text(
                        text = "NAFI TV 24 is a full-featured Live TV, Sports & M3U Media Streaming application. Features include custom HLS stream decoding, auto-failover servers, mobile & TV remote compatible layouts, M3U file upload parsing, and live matchup countdowns.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )

                    HorizontalDivider(color = Color(0xFF334155), thickness = 0.5.dp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Version ${com.example.BuildConfig.VERSION_NAME} (Build ${com.example.BuildConfig.VERSION_CODE})",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp,
                            modifier = Modifier.clickable { showAdminLoginDialog = true }
                        )

                        // Discreet admin secret trigger (small subtle icon)
                        IconButton(
                            onClick = { showAdminLoginDialog = true },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = Color(0xFF334155),
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // DIALOG: Admin Privacy Password Login (Hidden & Protected)
    if (showAdminLoginDialog) {
        AlertDialog(
            onDismissRequest = {
                showAdminLoginDialog = false
                adminPinInput = ""
                adminLoginError = null
            },
            containerColor = Color(0xFF1E293B),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("এডমিন পাসওয়ার্ড দিন", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "এডমিন প্যানেলে প্রবেশ করার জন্য গোপন পাসওয়ার্ড দিন:",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = adminPinInput,
                        onValueChange = { adminPinInput = it },
                        placeholder = { Text("••••••••", color = Color(0xFF64748B)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                        colors = customFieldColors(),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                    if (adminLoginError != null) {
                        Text(
                            text = adminLoginError ?: "",
                            color = Color(0xFFEF4444),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (repository.verifyAdminPin(adminPinInput)) {
                            showAdminLoginDialog = false
                            adminPinInput = ""
                            adminLoginError = null
                            onOpenAdminApp()
                        } else {
                            adminLoginError = "ভুল পাসওয়ার্ড! পুনরায় চেষ্টা করুন।"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black)
                ) {
                    Text("লগইন", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdminLoginDialog = false }) {
                    Text("বাতিল", color = Color.White)
                }
            }
        )
    }
}

// -------------------------------------------------------------
// TAB 1: EVENTS / SPORTS SCREEN (Exact match with user screenshot)
// -------------------------------------------------------------
@Composable
fun TeamLogoBadge(
    teamName: String,
    logoUrl: String?,
    modifier: Modifier = Modifier
) {
    val fallbackFlagUrl = when {
        teamName.contains("Bangladesh", ignoreCase = true) || teamName.contains("BD", ignoreCase = true) -> "https://flagcdn.com/w160/bd.png"
        teamName.contains("Australia", ignoreCase = true) || teamName.contains("AUS", ignoreCase = true) -> "https://flagcdn.com/w160/au.png"
        teamName.contains("Pakistan", ignoreCase = true) || teamName.contains("PAK", ignoreCase = true) -> "https://flagcdn.com/w160/pk.png"
        teamName.contains("England", ignoreCase = true) || teamName.contains("ENG", ignoreCase = true) -> "https://flagcdn.com/w160/gb-eng.png"
        teamName.contains("India", ignoreCase = true) || teamName.contains("IND", ignoreCase = true) -> "https://flagcdn.com/w160/in.png"
        teamName.contains("Sri Lanka", ignoreCase = true) || teamName.contains("SL", ignoreCase = true) -> "https://flagcdn.com/w160/lk.png"
        teamName.contains("New Zealand", ignoreCase = true) || teamName.contains("NZ", ignoreCase = true) -> "https://flagcdn.com/w160/nz.png"
        teamName.contains("South Africa", ignoreCase = true) || teamName.contains("SA", ignoreCase = true) -> "https://flagcdn.com/w160/za.png"
        teamName.contains("West Indies", ignoreCase = true) || teamName.contains("WI", ignoreCase = true) -> "https://flagcdn.com/w160/jm.png"
        teamName.contains("Afghanistan", ignoreCase = true) || teamName.contains("AFG", ignoreCase = true) -> "https://flagcdn.com/w160/af.png"
        teamName.contains("Ireland", ignoreCase = true) || teamName.contains("IRE", ignoreCase = true) -> "https://flagcdn.com/w160/ie.png"
        teamName.contains("Scotland", ignoreCase = true) || teamName.contains("SCO", ignoreCase = true) -> "https://flagcdn.com/w160/gb-sct.png"
        teamName.contains("Wales", ignoreCase = true) -> "https://flagcdn.com/w160/gb-wls.png"
        teamName.contains("Poland", ignoreCase = true) -> "https://flagcdn.com/w160/pl.png"
        teamName.contains("Hungary", ignoreCase = true) -> "https://flagcdn.com/w160/hu.png"
        teamName.contains("Netherlands", ignoreCase = true) -> "https://flagcdn.com/w160/nl.png"
        teamName.contains("Zimbabwe", ignoreCase = true) || teamName.contains("ZIM", ignoreCase = true) -> "https://flagcdn.com/w160/zw.png"
        teamName.contains("Nepal", ignoreCase = true) -> "https://flagcdn.com/w160/np.png"
        teamName.contains("Oman", ignoreCase = true) -> "https://flagcdn.com/w160/om.png"
        teamName.contains("USA", ignoreCase = true) || teamName.contains("United States", ignoreCase = true) -> "https://flagcdn.com/w160/us.png"
        teamName.contains("Canada", ignoreCase = true) -> "https://flagcdn.com/w160/ca.png"
        teamName.contains("UAE", ignoreCase = true) -> "https://flagcdn.com/w160/ae.png"
        teamName.contains("Germany", ignoreCase = true) -> "https://flagcdn.com/w160/de.png"
        teamName.contains("Spain", ignoreCase = true) -> "https://flagcdn.com/w160/es.png"
        teamName.contains("France", ignoreCase = true) -> "https://flagcdn.com/w160/fr.png"
        teamName.contains("Italy", ignoreCase = true) -> "https://flagcdn.com/w160/it.png"
        teamName.contains("Argentina", ignoreCase = true) -> "https://flagcdn.com/w160/ar.png"
        teamName.contains("Brazil", ignoreCase = true) -> "https://flagcdn.com/w160/br.png"
        teamName.contains("Portugal", ignoreCase = true) -> "https://flagcdn.com/w160/pt.png"
        teamName.contains("Trent Rockets", ignoreCase = true) -> "https://images.unsplash.com/photo-1579952363873-27f3bade9f55?w=160"
        teamName.contains("Southern Brave", ignoreCase = true) -> "https://images.unsplash.com/photo-1517649763962-0c623266ddc0?w=160"
        teamName.contains("Real Madrid", ignoreCase = true) -> "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=160"
        teamName.contains("Barcelona", ignoreCase = true) -> "https://images.unsplash.com/photo-1518091043644-c1d4457512c6?w=160"
        teamName.contains("Bayern", ignoreCase = true) -> "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=160"
        teamName.contains("Aston Villa", ignoreCase = true) -> "https://images.unsplash.com/photo-1517649763962-0c623266ddc0?w=160"
        else -> null
    }

    val finalUrl = logoUrl?.takeIf { it.isNotBlank() } ?: fallbackFlagUrl

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF0F172A),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
        modifier = modifier.size(44.dp)
    ) {
        if (finalUrl != null) {
            AsyncImage(
                model = finalUrl,
                contentDescription = teamName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF2563EB).copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = teamName.take(2).uppercase(),
                    color = Color(0xFF60A5FA),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

fun parseEventTimeStringToEpochMillis(timeStr: String?): Long? {
    if (timeStr.isNullOrBlank()) return null
    var clean = timeStr.trim()
    if (clean.equals("Live", ignoreCase = true) || clean.equals("Live Now", ignoreCase = true) || clean.equals("UPCOMING", ignoreCase = true)) return null

    // Check direct numeric timestamp (epoch seconds or millis)
    val directEpoch = clean.toLongOrNull()
    if (directEpoch != null) {
        return if (directEpoch > 1_000_000_000_000L) directEpoch else directEpoch * 1000L
    }

    // Convert Bangla numerals to English numerals
    val banglaDigits = charArrayOf('০', '১', '২', '৩', '৪', '৫', '৬', '৭', '৮', '৯')
    for (i in banglaDigits.indices) {
        clean = clean.replace(banglaDigits[i], ('0'.code + i).toChar())
    }

    // Clean common prefixes, Bangla noise words, and timezone tags
    clean = clean.replace("সময়:", "", ignoreCase = true)
        .replace("Time:", "", ignoreCase = true)
        .replace("Date:", "", ignoreCase = true)
        .replace("আজ", "", ignoreCase = true)
        .replace("আগামীকাল", "", ignoreCase = true)
        .replace("(BST)", "", ignoreCase = true)
        .replace("(BDT)", "", ignoreCase = true)
        .replace("(UTC)", "", ignoreCase = true)
        .replace("GMT+6", "", ignoreCase = true)
        .replace("টা", "", ignoreCase = true)
        .trim()

    // Normalize Bengali am/pm keywords
    clean = clean.replace("সকাল", "AM ")
        .replace("ভোর", "AM ")
        .replace("রাত", "PM ")
        .replace("সন্ধ্যা", "PM ")
        .replace("বিকাল", "PM ")
        .replace("দুপুর", "PM ")
        .replace("এএম", "AM")
        .replace("পিএম", "PM")
        .trim()

    // Normalize "8:30pm" -> "8:30 PM", "08:30am" -> "08:30 AM"
    clean = clean.replace(Regex("(?i)(\\d+:\\d+(?::\\d+)?)\\s*(am|pm)"), "$1 $2")
    clean = clean.replace(Regex("(?i)(\\b\\d{1,2})\\s*(am|pm)\\b"), "$1:00 $2").uppercase()
    clean = clean.replace(Regex("\\s+"), " ").trim()

    val nowGmt6Cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("GMT+6"))
    val currentYear = nowGmt6Cal.get(java.util.Calendar.YEAR)
    val currentMonth = nowGmt6Cal.get(java.util.Calendar.MONTH)
    val currentDay = nowGmt6Cal.get(java.util.Calendar.DAY_OF_MONTH)

    val patterns = listOf(
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd HH:mm",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd hh:mm a",
        "yyyy-MM-dd",
        "dd-MM-yyyy HH:mm:ss",
        "dd-MM-yyyy HH:mm",
        "dd-MM-yyyy hh:mm a",
        "dd/MM/yyyy HH:mm:ss",
        "dd/MM/yyyy HH:mm",
        "dd/MM/yyyy hh:mm a",
        "hh:mm a, dd MMM yyyy",
        "hh:mm a, MMM dd yyyy",
        "dd MMM yyyy, hh:mm a",
        "dd MMM yyyy hh:mm a",
        "MMM dd yyyy, hh:mm a",
        "MMM dd yyyy hh:mm a",
        "hh:mm a, dd MMM",
        "hh:mm a, MMM dd",
        "dd MMM, hh:mm a",
        "dd MMM hh:mm a",
        "MMM dd, hh:mm a",
        "MMM dd hh:mm a",
        "d MMM yyyy, h:mm a",
        "d MMM, h:mm a",
        "hh:mm:ss a",
        "hh:mm a",
        "h:mm a",
        "HH:mm:ss",
        "HH:mm",
        "a hh:mm",
        "a h:mm",
        "h a",
        "hh a",
        "a h",
        "a hh"
    )

    val timeZonesToCheck = listOf("GMT+6", java.util.TimeZone.getDefault().id, "UTC").distinct()

    for (timeZoneId in timeZonesToCheck) {
        val tz = java.util.TimeZone.getTimeZone(timeZoneId)
        for (pattern in patterns) {
            try {
                val sdf = java.text.SimpleDateFormat(pattern, java.util.Locale.US)
                sdf.timeZone = tz
                sdf.isLenient = false
                val date = sdf.parse(clean)
                if (date != null) {
                    val targetCal = java.util.Calendar.getInstance(tz).apply { time = date }
                    if (!pattern.contains("yyyy") && !pattern.contains("yyyy-")) {
                        targetCal.set(java.util.Calendar.YEAR, currentYear)
                        if (targetCal.timeInMillis < System.currentTimeMillis() - 7 * 86400 * 1000L) {
                            targetCal.add(java.util.Calendar.YEAR, 1)
                        }
                    }
                    if (!pattern.contains("dd") && !pattern.contains("MMM") && !pattern.contains("MM") && !pattern.contains("d")) {
                        targetCal.set(java.util.Calendar.YEAR, currentYear)
                        targetCal.set(java.util.Calendar.MONTH, currentMonth)
                        targetCal.set(java.util.Calendar.DAY_OF_MONTH, currentDay)
                        // If only time was provided (e.g. 02:00 AM) and it has passed by more than 3 hours, target tomorrow
                        if (targetCal.timeInMillis < System.currentTimeMillis() - 3 * 3600 * 1000L) {
                            targetCal.add(java.util.Calendar.DAY_OF_MONTH, 1)
                        }
                    }
                    return targetCal.timeInMillis
                }
            } catch (_: Exception) {}
        }
    }
    return null
}

fun calculateEventRemainingSeconds(sport: MediaItem, tickCount: Long = 0L): Long {
    // If explicitly marked Live, no countdown
    if (sport.isLive || sport.status.equals("LIVE", ignoreCase = true) || sport.status.contains("LIVE NOW", ignoreCase = true)) {
        return 0L
    }

    val nowMillis = System.currentTimeMillis()

    // 1. Check countdownTargetSeconds epoch timestamp first (exact epoch from DB/API)
    val raw = sport.countdownTargetSeconds
    if (raw != null && raw > 0L) {
        if (raw > 1_000_000_000_000L) { // Milliseconds epoch timestamp
            val diff = raw - nowMillis
            return maxOf(0L, diff / 1000L)
        } else if (raw > 1_000_000_000L) { // Seconds epoch timestamp
            val nowSec = nowMillis / 1000L
            return maxOf(0L, raw - nowSec)
        } else {
            // Relative seconds countdown fallback
            return maxOf(0L, raw - tickCount)
        }
    }

    // 2. Parse exact scheduled time string (e.g. "06:00 AM, 23 Aug" or "2026-08-23 06:00:00")
    val timeStr = sport.matchTimeFormatted?.takeIf { it.isNotBlank() } ?: sport.eventTime?.takeIf { it.isNotBlank() }
    val parsedEpoch = parseEventTimeStringToEpochMillis(timeStr)
    if (parsedEpoch != null) {
        val diff = parsedEpoch - nowMillis
        return maxOf(0L, diff / 1000L)
    }

    return 0L
}

fun isEventLiveNow(sport: MediaItem, tickCount: Long = 0L): Boolean {
    if (sport.isLive || sport.status.equals("LIVE", ignoreCase = true) || sport.status.contains("LIVE NOW", ignoreCase = true)) {
        return true
    }
    val rem = calculateEventRemainingSeconds(sport, tickCount)
    if (rem > 0L) {
        return false
    }

    // If remaining seconds is 0, check if event has scheduled time in the recent past (within 8 hours match duration)
    val timeStr = sport.matchTimeFormatted?.takeIf { it.isNotBlank() } ?: sport.eventTime?.takeIf { it.isNotBlank() }
    val scheduledTimeMillis = (sport.countdownTargetSeconds?.takeIf { it > 1_000_000_000_000L })
        ?: (sport.countdownTargetSeconds?.takeIf { it > 1_000_000_000L }?.let { it * 1000L })
        ?: parseEventTimeStringToEpochMillis(timeStr)

    if (scheduledTimeMillis != null) {
        val elapsed = System.currentTimeMillis() - scheduledTimeMillis
        if (elapsed in 0L..(8 * 3600 * 1000L)) {
            return true
        }
    }
    return false
}

@Composable
fun formatEventCountdownString(seconds: Long): String {
    if (seconds <= 0L) return "00h 00m 00s"
    val days = seconds / 86400
    val hours = (seconds % 86400) / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (days > 0) {
        String.format(java.util.Locale.US, "%dd %02dh %02dm %02ds", days, hours, minutes, secs)
    } else {
        String.format(java.util.Locale.US, "%02dh %02dm %02ds", hours, minutes, secs)
    }
}

@Composable
fun EventsScreen(
    sports: List<MediaItem>,
    favoriteIds: Set<String>,
    isLoading: Boolean = false,
    isTvMode: Boolean = false,
    onSelectMedia: (MediaItem) -> Unit,
    onToggleFavorite: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedStatus by remember { mutableStateOf("All") }

    // Dynamic categories extracted from all sports matches
    val categories = remember(sports) {
        val defaultCats = listOf("All", "Cricket", "Football", "Hockey", "More")
        val uniqueCats = sports.map { it.category.trim() }.filter { it.isNotBlank() && !it.equals("All", ignoreCase = true) }.distinct()
        (defaultCats + uniqueCats).distinct()
    }
    val statusFilters = listOf("All", "🔴 Live", "Upcoming", "Today", "Recent Results")

    // Live ticking countdown state (real-time 1-second interval)
    var tickCount by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (isActive) {
            kotlinx.coroutines.delay(1000L)
            tickCount++
        }
    }

    val filteredSports = remember(sports, selectedCategory, selectedStatus, searchQuery, tickCount / 10L) {
        val filtered = sports.filter { item ->
            val isLive = isEventLiveNow(item, tickCount)
            val catMatches = when (selectedCategory) {
                "All" -> true
                "Cricket" -> item.category.contains("Cricket", ignoreCase = true) || item.tournament?.contains("Cricket", ignoreCase = true) == true || item.title.contains("Cricket", ignoreCase = true) || item.team1?.contains("Cricket", ignoreCase = true) == true
                "Football" -> item.category.contains("Football", ignoreCase = true) || item.tournament?.contains("Football", ignoreCase = true) == true || item.title.contains("Football", ignoreCase = true) || item.team1?.contains("Football", ignoreCase = true) == true
                "Hockey" -> item.category.contains("Hockey", ignoreCase = true) || item.tournament?.contains("Hockey", ignoreCase = true) == true || item.title.contains("Hockey", ignoreCase = true)
                "More" -> !item.category.contains("Cricket", ignoreCase = true) && !item.category.contains("Football", ignoreCase = true)
                else -> item.category.contains(selectedCategory, ignoreCase = true) || item.tournament?.contains(selectedCategory, ignoreCase = true) == true || item.title.contains(selectedCategory, ignoreCase = true)
            }
            val statusMatches = when (selectedStatus) {
                "All" -> true
                "🔴 Live" -> isLive || item.isLive || item.status.contains("Live", ignoreCase = true)
                "Upcoming" -> !isLive && !item.isLive
                "Today" -> true
                "Recent Results" -> !isLive && !item.isLive
                else -> true
            }
            val searchMatches = if (searchQuery.isBlank()) {
                true
            } else {
                val q = searchQuery.trim().lowercase()
                item.title.lowercase().contains(q) ||
                    item.category.lowercase().contains(q) ||
                    (item.tournament?.lowercase()?.contains(q) == true) ||
                    (item.team1?.lowercase()?.contains(q) == true) ||
                    (item.team2?.lowercase()?.contains(q) == true) ||
                    item.servers.any { s -> s.name.lowercase().contains(q) } ||
                    (item.description?.lowercase()?.contains(q) == true)
            }
            catMatches && statusMatches && searchMatches
        }.distinctBy { it.id }

        // এডমিন প্যানেল থেকে যেগুলো খেলা এড করা হবে সেগুলো যেন সবার আগে আসে (Admin matches always first)
        val (adminItems, otherItems) = filtered.partition { item ->
            item.isAdminAdded || item.isFromAdmin
        }
        adminItems + otherItems
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF020617))
    ) {
        // TOP SEARCH BAR FOR LIVE EVENTS & MATCHES
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .testTag("events_search_input"),
            placeholder = {
                Text(
                    text = "খেলা, দল, চ্যানেল বা টুর্নামেন্ট সার্চ করুন (যেমন: Sony LIV, Cricket...)",
                    color = Color(0xFF64748B),
                    fontSize = 12.sp
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "Search Matches",
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { searchQuery = "" },
                        modifier = Modifier.size(30.dp).testTag("events_search_clear_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear Search",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF0F172A),
                unfocusedContainerColor = Color(0xFF0F172A),
                focusedBorderColor = Color(0xFF00E5FF),
                unfocusedBorderColor = Color(0xFF334155),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(14.dp),
            singleLine = true
        )

        // TOP FILTER HEADERS (Category chips and Status chips)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Category Filter Row
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { cat ->
                    val isSelected = selectedCategory == cat
                    var isCatFocused by remember { mutableStateOf(false) }
                    val catScale by animateFloatAsState(
                        targetValue = if (isCatFocused) 1.05f else 1.0f,
                        animationSpec = tween(180, easing = FastOutSlowInEasing),
                        label = "catScale"
                    )
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = when {
                            isCatFocused -> Color(0xFF1E3A8A).copy(alpha = 0.8f)
                            isSelected -> Color(0xFF0284C7)
                            else -> Color(0xFF1E293B)
                        },
                        border = when {
                            isCatFocused -> androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF38BDF8))
                            isSelected -> androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8))
                            else -> androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155).copy(alpha = 0.5f))
                        },
                        modifier = Modifier
                            .scale(catScale)
                            .onFocusChanged { isCatFocused = it.isFocused }
                            .focusable()
                            .clickable { selectedCategory = cat }
                    ) {
                        Text(
                            text = cat,
                            color = if (isSelected || isCatFocused) Color.White else Color(0xFF94A3B8),
                            fontSize = 12.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Status Filter Row (All, Live, Upcoming, Today, Recent Results)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(statusFilters) { status ->
                    val isSelected = selectedStatus == status
                    var isStatusFocused by remember { mutableStateOf(false) }
                    val statusScale by animateFloatAsState(
                        targetValue = if (isStatusFocused) 1.05f else 1.0f,
                        animationSpec = tween(180, easing = FastOutSlowInEasing),
                        label = "statusScale"
                    )
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = when {
                            isStatusFocused -> Color(0xFF1E3A8A).copy(alpha = 0.8f)
                            isSelected -> Color(0xFF0284C7)
                            else -> Color(0xFF1E293B)
                        },
                        border = when {
                            isStatusFocused -> androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF38BDF8))
                            isSelected -> androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8))
                            else -> androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155).copy(alpha = 0.5f))
                        },
                        modifier = Modifier
                            .scale(statusScale)
                            .onFocusChanged { isStatusFocused = it.isFocused }
                            .focusable()
                            .clickable { selectedStatus = status }
                    ) {
                        Text(
                            text = status,
                            color = if (isSelected || isStatusFocused) Color.White else Color(0xFF94A3B8),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        // MATCH LISTING
        if (isLoading && sports.isEmpty()) {
            NafiLogoLoadingView(
                title = "লাইভ ম্যাচ ও ইভেন্ট লোড হচ্ছে...",
                subtitle = "অনুগ্রহ করে অপেক্ষা করুন, খেলার লিংক আপডেট হচ্ছে...",
                modifier = Modifier.fillMaxSize()
            )
        } else if (filteredSports.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (searchQuery.isNotBlank()) Icons.Rounded.SearchOff else Icons.Rounded.SportsSoccer,
                        contentDescription = null,
                        tint = Color(0xFF475569),
                        modifier = Modifier.size(54.dp)
                    )
                    Text(
                        text = if (searchQuery.isNotBlank()) "\"$searchQuery\" দিয়ে কোনো খেলা বা চ্যানেল পাওয়া যায়নি" else "এই ফিল্টারে কোনো ম্যাচ পাওয়া যায়নি",
                        color = Color(0xFF94A3B8),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                    if (searchQuery.isNotBlank()) {
                        Button(
                            onClick = { searchQuery = "" },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("সার্চ রিসেট করুন", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredSports, key = { it.id }) { sport ->
                    LiveEventMatchCard(
                        sport = sport,
                        tickCount = tickCount,
                        isTvMode = isTvMode,
                        onSelectMedia = onSelectMedia
                    )
                }
            }
        }
    }
}

@Composable
fun LiveEventMatchCard(
    sport: MediaItem,
    isLiveNow: Boolean = false,
    remainingSecs: Long = 0L,
    tickCount: Long = 0L,
    isTvMode: Boolean = false,
    onSelectMedia: (MediaItem) -> Unit
) {
    val context = LocalContext.current
    var isCardFocused by remember { mutableStateOf(false) }
    var showNoLinkDialog by remember { mutableStateOf(false) }

    // Real-time 1-second ticking ticker guarantee
    var localTick by remember { mutableStateOf(0L) }
    LaunchedEffect(sport.id, sport.countdownTargetSeconds, sport.matchTimeFormatted, sport.eventTime) {
        while (isActive) {
            kotlinx.coroutines.delay(1000L)
            localTick++
        }
    }

    val effectiveTick = if (tickCount > 0L) tickCount else localTick
    val actualRemainingSecs = calculateEventRemainingSeconds(sport, effectiveTick)
    val actualIsLive = isEventLiveNow(sport, effectiveTick) || sport.isLive || sport.status.equals("LIVE", ignoreCase = true) || sport.status.contains("LIVE NOW", ignoreCase = true)
    val isEffectivelyLive = actualIsLive || (actualRemainingSecs <= 0L && (sport.countdownTargetSeconds != null || !sport.matchTimeFormatted.isNullOrBlank() || !sport.eventTime.isNullOrBlank()))

    val stageHeader = if (!sport.status.isNullOrBlank() &&
        !sport.status.equals("LIVE", ignoreCase = true) &&
        !sport.status.equals("UPCOMING", ignoreCase = true) &&
        !sport.status.equals("null", ignoreCase = true)
    ) {
        sport.status.uppercase()
    } else if (!sport.tournament.isNullOrBlank() && sport.tournament!!.contains("Stage", ignoreCase = true)) {
        "GROUP STAGE"
    } else {
        "1ST ROUND"
    }

    val tournamentTag = sport.tournament?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        ?: sport.category.takeIf { it.isNotBlank() && !it.equals("Sports", ignoreCase = true) }
        ?: "Sports Event"

    val displayTitle = when {
        !sport.title.isNullOrBlank() && !sport.title.equals("null", ignoreCase = true) && !sport.title.equals("Live Match", ignoreCase = true) -> sport.title
        !sport.team1.isNullOrBlank() && !sport.team2.isNullOrBlank() -> "${sport.team1} vs ${sport.team2}"
        else -> sport.title.ifBlank { "Live Match" }
    }

    val formattedTime = sport.matchTimeFormatted?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        ?: sport.eventTime?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        ?: "01:00 AM, Today"

    val servers = sport.getAllServers()
    val hasPlayableLink = (sport.streamUrl.isNotBlank() && !sport.streamUrl.equals("null", ignoreCase = true)) ||
            servers.any { it.url.isNotBlank() && !it.url.equals("null", ignoreCase = true) }

    var showServerSelectDialog by remember { mutableStateOf(false) }

    val handlePlayClick: (String?) -> Unit = { targetUrl ->
        val linkToPlay = targetUrl?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        if (!linkToPlay.isNullOrBlank()) {
            // Direct playback of explicitly chosen server/channel
            onSelectMedia(sport.copy(streamUrl = linkToPlay))
        } else if (servers.size > 1) {
            // Multiple channels available: open channel/server selector dialog
            showServerSelectDialog = true
        } else {
            val fallbackLink = sport.streamUrl.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                ?: servers.firstOrNull { it.url.isNotBlank() && !it.url.equals("null", ignoreCase = true) }?.url

            if (!fallbackLink.isNullOrBlank()) {
                onSelectMedia(sport.copy(streamUrl = fallbackLink))
            } else {
                showNoLinkDialog = true
                Toast.makeText(context, "ম্যাচ শুরু হওয়ার সাথে সাথে চ্যানেল আসবে অপেক্ষা করুন ধন্যবাদ", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val cardModifier = if (isTvMode) {
        val cardScale by animateFloatAsState(
            targetValue = if (isCardFocused) 1.025f else 1.0f,
            animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
            label = "eventCardScale"
        )
        Modifier
            .fillMaxWidth()
            .scale(cardScale)
            .onFocusChanged { isCardFocused = it.isFocused }
            .focusable()
            .clickable { handlePlayClick(null) }
    } else {
        Modifier
            .fillMaxWidth()
            .clickable { handlePlayClick(null) }
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        border = androidx.compose.foundation.BorderStroke(
            if (sport.isAdminAdded || sport.isFromAdmin) 1.5.dp else 1.dp,
            if (isTvMode && isCardFocused) Color(0xFF38BDF8)
            else if (sport.isAdminAdded || sport.isFromAdmin) Color(0xFF0284C7)
            else Color(0xFF1E293B)
        ),
        modifier = cardModifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // LEFT COLUMN: Clean Sports Thumbnail / Team Logos + Tournament Tag
            Column(
                modifier = Modifier.width(118.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Thumbnail Box (Clean: NO badge overlapping the match picture, NO VS between logos)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E293B))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                ) {
                    val hasTeam1Logo = !sport.team1Logo.isNullOrBlank() && !sport.team1Logo.equals("null", ignoreCase = true)
                    val hasTeam2Logo = !sport.team2Logo.isNullOrBlank() && !sport.team2Logo.equals("null", ignoreCase = true)
                    val hasLogoUrl = !sport.logoUrl.isNullOrBlank() && !sport.logoUrl.equals("null", ignoreCase = true)

                    if (hasLogoUrl) {
                        // Single Match / Tournament Poster Banner (shown clearly across the frame)
                        AsyncImage(
                            model = sport.logoUrl,
                            contentDescription = sport.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else if (hasTeam1Logo && hasTeam2Logo) {
                        // Two logos side-by-side without "VS"
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF0F172A)),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(Color(0xFF1E293B)),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = sport.team1Logo,
                                    contentDescription = sport.team1,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(4.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .fillMaxHeight()
                                    .background(Color(0xFF334155))
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(Color(0xFF1E293B)),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = sport.team2Logo,
                                    contentDescription = sport.team2,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(4.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                        }
                    } else if (hasTeam1Logo) {
                        AsyncImage(
                            model = sport.team1Logo,
                            contentDescription = sport.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        // Gradient Sport Poster Placeholder
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(
                                        listOf(Color(0xFF1E3A8A), Color(0xFF0F172A))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            val sportEmoji = when {
                                sport.category.contains("Cricket", ignoreCase = true) -> "🏏"
                                sport.category.contains("Football", ignoreCase = true) || sport.category.contains("Soccer", ignoreCase = true) -> "⚽"
                                sport.category.contains("Hockey", ignoreCase = true) -> "🏑"
                                sport.category.contains("Tennis", ignoreCase = true) -> "🎾"
                                else -> "🏆"
                            }
                            Text(text = sportEmoji, fontSize = 26.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Tournament Tag below thumbnail
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF1E293B),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF334155)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "🏆 $tournamentTag",
                            color = Color(0xFFF1F5F9),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // RIGHT COLUMN: Stage / Matchday + Right Status/Time, Title, Status Banner, Action Button
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Top Header: Stage on left & Live text / UPCOMING status with Match Time on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        if (sport.isAdminAdded || sport.isFromAdmin) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFF2563EB).copy(alpha = 0.25f),
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF38BDF8)),
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                Text(
                                    text = "⭐ ADMIN",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = stageHeader,
                            color = Color(0xFFF59E0B),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))

                    if (isEffectivelyLive) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFEF4444).copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFEF4444))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFEF4444))
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "LIVE NOW",
                                    color = Color(0xFFF87171),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFD97706).copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFF59E0B))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "🟡 UPCOMING",
                                    color = Color(0xFFFBBF24),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (formattedTime.isNotBlank()) {
                                    Text(
                                        text = " • $formattedTime",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                // Match Title (News Notice Marquee effect - scrolls continuously horizontally like a breaking news ticker)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                ) {
                    Text(
                        text = displayTitle,
                        color = Color.White,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .basicMarquee(
                                iterations = Int.MAX_VALUE,
                                velocity = 40.dp
                            )
                    )
                }

                // Playlist Source indicator (কোন প্লেলিস্ট থেকে খেলা চলছে তার স্পষ্ট উল্লেখ)
                val playlistSource = remember(sport.id, sport.category, sport.tournament, sport.isAdminAdded) {
                    com.example.util.SportsInteractionManager.getPlaylistSource(sport.category, sport.tournament, sport.isAdminAdded, sport.id)
                }
                Surface(
                    shape = RoundedCornerShape(5.dp),
                    color = Color(0xFF0284C7).copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF38BDF8).copy(alpha = 0.5f))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlaylistPlay,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = playlistSource,
                            color = Color(0xFFBAE6FD),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Status / Countdown Banner (Ticks down in real-time second by second)
                if (isEffectivelyLive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF064E3B).copy(alpha = 0.35f))
                            .border(0.5.dp, Color(0xFF10B981).copy(alpha = 0.5f))
                            .padding(vertical = 3.5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "• ম্যাচটি এখন লাইভ চলছে",
                            color = Color(0xFF34D399),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (actualRemainingSecs > 0L) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1E293B).copy(alpha = 0.7f))
                            .border(0.5.dp, Color(0xFF334155))
                            .padding(vertical = 3.5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "⏳ বাকি: ${formatEventCountdownString(actualRemainingSecs)}",
                            color = Color(0xFFFBBF24),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1E293B).copy(alpha = 0.7f))
                            .border(0.5.dp, Color(0xFF334155))
                            .padding(vertical = 3.5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "⏳ আসন্ন ম্যাচ (খুব শীঘ্রই শুরু হবে)",
                            color = Color(0xFFFBBF24),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Multiple Servers Chips (if available) - Smoothly scrollable horizontally without breaking card height
                if (servers.size > 1) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 1.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = Color(0xFF0369A1).copy(alpha = 0.25f),
                            border = androidx.compose.foundation.BorderStroke(0.6.dp, Color(0xFF0284C7)),
                            modifier = Modifier.clickable { showServerSelectDialog = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Dns,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "${servers.size}টি চ্যানেল",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        servers.forEachIndexed { idx, srv ->
                            val cleanName = srv.name.ifBlank { "সার্ভার ${idx + 1}" }
                            Surface(
                                shape = RoundedCornerShape(5.dp),
                                color = Color(0xFF1E293B),
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF38BDF8).copy(alpha = 0.6f)),
                                modifier = Modifier.clickable { handlePlayClick(srv.url) }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.PlayArrow,
                                        contentDescription = null,
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = cleanName,
                                        color = Color(0xFFF1F5F9),
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                    }
                }

                // Action Button
                if (isLiveNow || hasPlayableLink) {
                    val playButtonText = if (servers.size > 1) "লাইভ দেখুন (${servers.size}টি চ্যানেল)" else "লাইভ দেখুন"
                    Button(
                        onClick = { handlePlayClick(null) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = playButtonText,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1E293B),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF334155)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .clickable { handlePlayClick(null) }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.HourglassTop,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "চ্যানেল লিংক আসছে",
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }

    if (showServerSelectDialog && servers.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showServerSelectDialog = false },
            icon = {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0284C7).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LiveTv,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "📺 লাইভ চ্যানেল নির্বাচন করুন",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = displayTitle,
                        color = Color(0xFF94A3B8),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    servers.forEachIndexed { index, srv ->
                        val cleanName = srv.name.ifBlank { "সার্ভার ${index + 1}" }
                        val isLinkValid = srv.url.isNotBlank() && !srv.url.equals("null", ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF1E293B),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isLinkValid) Color(0xFF0284C7).copy(alpha = 0.7f) else Color(0xFF334155)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = isLinkValid) {
                                    showServerSelectDialog = false
                                    onSelectMedia(sport.copy(streamUrl = srv.url))
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF0284C7).copy(alpha = 0.25f),
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "${index + 1}",
                                                color = Color(0xFF38BDF8),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.5.sp
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = cleanName,
                                            color = Color.White,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = if (isLinkValid) "🟢 সচল লাইভ স্ট্রিম" else "🔴 প্রস্তুত হচ্ছে",
                                            color = if (isLinkValid) Color(0xFF34D399) else Color(0xFFF87171),
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isLinkValid) Color(0xFFDC2626) else Color(0xFF334155)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.PlayArrow,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = "চালান",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showServerSelectDialog = false }) {
                    Text("বন্ধ করুন", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF0F172A),
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showNoLinkDialog) {
        AlertDialog(
            onDismissRequest = { showNoLinkDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "লাইভ স্ট্রিম নোটিশ",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "ম্যাচ শুরু হওয়ার সাথে সাথে চ্যানেল আসবে অপেক্ষা করুন ধন্যবাদ",
                    color = Color(0xFFE2E8F0),
                    fontSize = 13.5.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = { showNoLinkDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Text("ঠিক আছে", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF0F172A),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 8.dp
        )
    }
}

// Backward compatibility alias for legacy callers if any
@Composable
fun AdminEventMatchCard(
    sport: MediaItem,
    isLiveNow: Boolean = false,
    remainingSecs: Long = 0L,
    tickCount: Long = 0L,
    isTvMode: Boolean = false,
    onSelectMedia: (MediaItem) -> Unit
) {
    LiveEventMatchCard(sport, isLiveNow, remainingSecs, tickCount, isTvMode, onSelectMedia)
}

@Composable
fun JsonPosterEventCard(
    sport: MediaItem,
    isLiveNow: Boolean = false,
    remainingSecs: Long = 0L,
    tickCount: Long = 0L,
    isTvMode: Boolean = false,
    onSelectMedia: (MediaItem) -> Unit
) {
    LiveEventMatchCard(sport, isLiveNow, remainingSecs, tickCount, isTvMode, onSelectMedia)
}
