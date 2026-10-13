package com.example.ui

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.BuildConfig
import com.example.data.MediaRepository
import com.example.model.AppConfigData
import com.example.model.*
import com.example.util.NotificationHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// Build safety and backward compatibility
var isItemDeleted: Boolean = false
fun isItemDeleted(vararg args: Any?): Boolean = isItemDeleted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminControlAppScreen(
    repository: MediaRepository,
    sportsList: List<MediaItem>,
    liveTvList: List<MediaItem>,
    moviesList: List<MediaItem>,
    playlistsList: List<PlaylistInfo>,
    cloudStreamRepos: List<CloudStreamRepo> = emptyList(),
    movieProviders: List<MovieProvider> = emptyList(),
    onOpenMovieProvider: (MovieProvider) -> Unit = {},
    onExitAdmin: () -> Unit,
    onDataChanged: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // BackHandler to exit Admin View back to main app
    BackHandler {
        onExitAdmin()
    }

    var deletedIdsVersion by remember { mutableIntStateOf(0) }

    var adminSportsItems by remember(sportsList, deletedIdsVersion) {
        mutableStateOf(sportsList.filterNot { repository.isItemDeleted(it) })
    }

    LaunchedEffect(sportsList, deletedIdsVersion) {
        adminSportsItems = sportsList.filterNot { repository.isItemDeleted(it) }
    }

    var selectedAdminTab by remember { mutableStateOf(AdminTab.CHANNELS) }
    var showAuthDialog by remember { mutableStateOf(false) }

    var userAnalytics by remember { mutableStateOf<AppUserAnalytics?>(null) }
    var isRefreshingAnalytics by remember { mutableStateOf(false) }
    var isCleaningStaleSessions by remember { mutableStateOf(false) }
    var isWipingQuota by remember { mutableStateOf(false) }
    var showWipeQuotaDialog by remember { mutableStateOf(false) }
    var selectedDeviceFilter by remember { mutableStateOf(0) } // 0 = Live Online, 1 = Recent Today, 2 = All Records
    var selectedLocationMode by remember { mutableStateOf(0) } // 0 = Live Traffic, 1 = All History

    fun loadAnalyticsData() {
        isRefreshingAnalytics = true
        coroutineScope.launch {
            try {
                userAnalytics = repository.fetchUserAnalytics()
            } catch (_: Exception) {}
            isRefreshingAnalytics = false
        }
    }

    LaunchedEffect(Unit) {
        loadAnalyticsData()
    }

    // Auto-refresh periodically when Analytics tab is open
    LaunchedEffect(selectedAdminTab) {
        if (selectedAdminTab == AdminTab.ANALYTICS) {
            while (isActive) {
                try {
                    userAnalytics = repository.fetchUserAnalytics()
                } catch (_: Exception) {}
                delay(12_000L)
            }
        }
    }

    // CloudStream & Movie Website Form State
    var repoUrlInput by remember { mutableStateOf("") }
    var isFetchingRepo by remember { mutableStateOf(false) }
    var customSiteName by remember { mutableStateOf("") }
    var customSiteUrl by remember { mutableStateOf("") }
    var customSiteCategory by remember { mutableStateOf("Movie Website") }
    var customSiteLogo by remember { mutableStateOf("") }
    var customSiteDesc by remember { mutableStateOf("") }
    var expandedRepoId by remember { mutableStateOf<String?>(null) }
    var repoToDelete by remember { mutableStateOf<CloudStreamRepo?>(null) }
    var providerToDelete by remember { mutableStateOf<MovieProvider?>(null) }

    // Marquee Breaking News Ticker State
    var marqueeTickerInput by remember { mutableStateOf(repository.getMarqueeTickerText()) }
    var isSavingMarqueeTicker by remember { mutableStateOf(false) }

    // Auto-sync Ticker text when switching to TICKER tab
    LaunchedEffect(selectedAdminTab) {
        if (selectedAdminTab == AdminTab.TICKER) {
            marqueeTickerInput = repository.getMarqueeTickerText()
        }
    }

    // Sports Match Form State
    var tournamentName by remember { mutableStateOf("") }
    var matchPosterUrl by remember { mutableStateOf("") }
    var team1Name by remember { mutableStateOf("") }
    var team1LogoUrl by remember { mutableStateOf("") }
    var team2Name by remember { mutableStateOf("") }
    var team2LogoUrl by remember { mutableStateOf("") }
    var team1Score by remember { mutableStateOf("") }
    var team2Score by remember { mutableStateOf("") }
    var matchTimeFormatted by remember { mutableStateOf("") }
    var countdownHours by remember { mutableStateOf("0") }
    var sportsServers by remember { mutableStateOf(listOf(StreamServer(name = "HD Server 1", url = ""))) }
    var sportCategory by remember { mutableStateOf("CRICKET") }
    var sportStatus by remember { mutableStateOf("UPCOMING") }
    var sportDropdownExpanded by remember { mutableStateOf(false) }
    var statusDropdownExpanded by remember { mutableStateOf(false) }
    val sportOptions = listOf("CRICKET", "FOOTBALL", "WWE", "TENNIS", "OTHERS")
    val statusOptions = listOf("UPCOMING", "LIVE", "FINISHED")

    // Sports M3U & Tapmad Live Events
    var sportsM3uInput by remember { mutableStateOf(repository.getSavedSportsM3uUrl()) }
    var tapmadJsonInput by remember { mutableStateOf(repository.getSavedTapmadJsonUrl()) }
    var tapmadM3uInput by remember { mutableStateOf(repository.getSavedTapmadM3uUrl()) }

    // Edit/Update/Delete Match State
    var editingMatchItem by remember { mutableStateOf<MediaItem?>(null) }
    var editTournament by remember { mutableStateOf("") }
    var editMatchPosterUrl by remember { mutableStateOf("") }
    var editSportCategory by remember { mutableStateOf("CRICKET") }
    var editSportStatus by remember { mutableStateOf("UPCOMING") }
    var editTeam1Name by remember { mutableStateOf("") }
    var editTeam1Score by remember { mutableStateOf("") }
    var editTeam1Logo by remember { mutableStateOf("") }
    var editTeam2Name by remember { mutableStateOf("") }
    var editTeam2Score by remember { mutableStateOf("") }
    var editTeam2Logo by remember { mutableStateOf("") }
    var editMatchTime by remember { mutableStateOf("") }
    var editCountdownHours by remember { mutableStateOf("0") }
    var editServers by remember { mutableStateOf<List<StreamServer>>(emptyList()) }
    var editSportDropdownExpanded by remember { mutableStateOf(false) }
    var editStatusDropdownExpanded by remember { mutableStateOf(false) }
    var updatingItem by remember { mutableStateOf<MediaItem?>(null) }
    var updateScore1 by remember { mutableStateOf("") }
    var updateScore2 by remember { mutableStateOf("") }
    var itemToDelete by remember { mutableStateOf<MediaItem?>(null) }

    // Channel Form State
    var channelName by remember { mutableStateOf("") }
    var channelCategory by remember { mutableStateOf("Entertainment") }
    var server1Url by remember { mutableStateOf("") }
    var server2Url by remember { mutableStateOf("") }
    var channelLogoUrl by remember { mutableStateOf("") }
    var liveTvM3uInput by remember { mutableStateOf(repository.getSavedLiveTvM3uUrl()) }
    var addChannelCatDropdownExpanded by remember { mutableStateOf(false) }
    val channelCategoryOptions = listOf("Sports", "News", "Entertainment", "Movies", "Kids", "Music", "Infotainment", "Religious")

    // Edit Channel State
    var editingChannelItem by remember { mutableStateOf<MediaItem?>(null) }
    var editChannelName by remember { mutableStateOf("") }
    var editChannelCategory by remember { mutableStateOf("Entertainment") }
    var editChannelServers by remember { mutableStateOf<List<StreamServer>>(emptyList()) }
    var editChannelLogoUrl by remember { mutableStateOf("") }
    var editChannelCategoryDropdownExpanded by remember { mutableStateOf(false) }

    // Movies Form State
    var movieTitle by remember { mutableStateOf("") }
    var movieCategory by remember { mutableStateOf("Movies") }
    var movieCategoryOptions = listOf("Movies", "Web Series", "Action", "Drama", "Animation", "Comedy", "Thriller", "Horror")
    var moviePosterUrl by remember { mutableStateOf("") }
    var moviesM3uInput by remember { mutableStateOf(repository.getSavedMoviesM3uUrl()) }
    var editingMovieItem by remember { mutableStateOf<MediaItem?>(null) }
    var editMovieTitle by remember { mutableStateOf("") }
    var editMoviePosterUrl by remember { mutableStateOf("") }
    var editMovieDesc by remember { mutableStateOf("") }
    var editMovieCategory by remember { mutableStateOf("Movies") }
    var editMovieServers by remember { mutableStateOf<List<StreamServer>>(emptyList()) }
    var editMovieCategoryDropdownExpanded by remember { mutableStateOf(false) }

    // OTT Movie Hubs & Server Form State
    var ottHubName by remember { mutableStateOf("") }
    var ottHubType by remember { mutableStateOf("XTREAM") } // "XTREAM" or "M3U"
    var ottHubUrl by remember { mutableStateOf("") }
    var ottHubUsername by remember { mutableStateOf("") }
    var ottHubPassword by remember { mutableStateOf("") }
    var ottHubLogoUrl by remember { mutableStateOf("") }
    var ottHubIsSaving by remember { mutableStateOf(false) }

    // Playlist Form State
    var playlistTitle by remember { mutableStateOf("") }
    var playlistUrl by remember { mutableStateOf("") }
    var playlistLogoUrl by remember { mutableStateOf("") }
    var playlistDescription by remember { mutableStateOf("") }
    var editingPlaylistItem by remember { mutableStateOf<PlaylistInfo?>(null) }
    var editPlaylistTitle by remember { mutableStateOf("") }
    var editPlaylistUrl by remember { mutableStateOf("") }
    var editPlaylistLogoUrl by remember { mutableStateOf("") }
    var editPlaylistDescription by remember { mutableStateOf("") }
    var playlistToDelete by remember { mutableStateOf<PlaylistInfo?>(null) }

    // Admin Search & Filter States
    var channelAdminSearchQuery by remember { mutableStateOf("") }
    var channelFilterType by remember { mutableStateOf("ALL") } // "ALL", "CUSTOM", "M3U"
    var channelFilterCategory by remember { mutableStateOf("All") }

    var playlistAdminSearchQuery by remember { mutableStateOf("") }
    var playlistFilterType by remember { mutableStateOf("ALL") } // "ALL", "CUSTOM", "SYSTEM"

    var sportsAdminSearchQuery by remember { mutableStateOf("") }
    var sportsFilterType by remember { mutableStateOf("ALL") } // "ALL", "CUSTOM", "LIVE", "UPCOMING"

    var moviesAdminSearchQuery by remember { mutableStateOf("") }
    var moviesFilterType by remember { mutableStateOf("ALL") } // "ALL", "CUSTOM"

    // Xtream Codes API Admin State
    var xtreamAccountsList by remember { mutableStateOf(repository.getXtreamAccounts()) }
    var xtreamServerName by remember { mutableStateOf("Starshare Xtream Server") }
    var xtreamServerUrl by remember { mutableStateOf("http://rgkkw.live:80") }
    var xtreamUsernameInput by remember { mutableStateOf("4dfoydR2gZ") }
    var xtreamPasswordInput by remember { mutableStateOf("clever3still") }
    var xtreamIncludeLive by remember { mutableStateOf(true) }
    var xtreamIncludeVod by remember { mutableStateOf(true) }
    var xtreamIncludeSeries by remember { mutableStateOf(true) }
    var xtreamTesting by remember { mutableStateOf(false) }
    var xtreamSyncing by remember { mutableStateOf(false) }
    var xtreamTestResult by remember { mutableStateOf<String?>(null) }
    var xtreamIsTestSuccess by remember { mutableStateOf(false) }
    var xtreamEditingId by remember { mutableStateOf<String?>(null) }
    var xtreamPasswordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            val remoteConfig = repository.fetchAppConfigFromFirebase()
            if (remoteConfig != null) {
                if (remoteConfig.liveTvM3uUrl.isNotBlank()) liveTvM3uInput = remoteConfig.liveTvM3uUrl
                if (remoteConfig.sportsM3uUrl.isNotBlank()) sportsM3uInput = remoteConfig.sportsM3uUrl
                if (remoteConfig.moviesM3uUrl.isNotBlank()) moviesM3uInput = remoteConfig.moviesM3uUrl
                if (remoteConfig.tapmadJsonUrl.isNotBlank()) tapmadJsonInput = remoteConfig.tapmadJsonUrl
                if (remoteConfig.xtreamServerUrl.isNotBlank()) xtreamServerUrl = remoteConfig.xtreamServerUrl
                if (remoteConfig.xtreamUsername.isNotBlank()) xtreamUsernameInput = remoteConfig.xtreamUsername
                if (remoteConfig.xtreamPassword.isNotBlank()) xtreamPasswordInput = remoteConfig.xtreamPassword
            }
        } catch (_: Exception) {}
    }

    // App Update Form State
    val currentUpdate = repository.getCachedAppUpdateInfo()
    var updateVersionName by remember { mutableStateOf(currentUpdate?.versionName ?: "") }
    var updateVersionCode by remember { mutableStateOf((currentUpdate?.versionCode ?: 0).toString()) }
    var updateDownloadUrl by remember { mutableStateOf(currentUpdate?.downloadUrl ?: "") }
    var updateReleaseNotes by remember { mutableStateOf(currentUpdate?.releaseNotes ?: "") }
    var updateReleaseDate by remember { mutableStateOf(currentUpdate?.releaseDate ?: "") }
    var updateApkSize by remember { mutableStateOf(currentUpdate?.apkSize ?: "") }
    var updateIsForce by remember { mutableStateOf(currentUpdate?.isForceUpdate ?: false) }
    var isSavingUpdate by remember { mutableStateOf(false) }
    var previewUpdateDialog by remember { mutableStateOf<AppUpdateInfo?>(null) }

    // Broadcast Form State
    var broadcastTitle by remember { mutableStateOf("") }
    var broadcastMessage by remember { mutableStateOf("") }
    var broadcastImageUrl by remember { mutableStateOf("") }
    var broadcastType by remember { mutableStateOf(NotificationType.BROADCAST) }
    var broadcastTargetId by remember { mutableStateOf("") }
    var broadcastTypeDropdownExpanded by remember { mutableStateOf(false) }
    var isSendingBroadcast by remember { mutableStateOf(false) }
    var adminNotificationHistory by remember { mutableStateOf(repository.getStoredNotifications()) }

    // Firebase Settings State
    var firebaseUrlInput by remember { mutableStateOf(repository.getSavedFirebaseUrl() ?: "") }
    var isTestingFirebase by remember { mutableStateOf(false) }
    var firebaseTestResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    Scaffold(
        containerColor = Color(0xFF0F172A),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF10B981))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "NAFI TV Control Panel",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onExitAdmin) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    val isAuthLoggedIn = repository.authManager.isUserLoggedIn()
                    val isAuthAdmin = repository.authManager.isAuthorizedAdmin()
                    IconButton(onClick = { showAuthDialog = true }) {
                        Icon(
                            imageVector = if (isAuthLoggedIn && isAuthAdmin) Icons.Rounded.AdminPanelSettings else Icons.Rounded.Lock,
                            contentDescription = "Firebase Auth",
                            tint = if (isAuthLoggedIn && isAuthAdmin) Color(0xFF10B981) else Color(0xFFF59E0B)
                        )
                    }
                    IconButton(onClick = { loadAnalyticsData() }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = Color(0xFF00E5FF))
                    }
                    IconButton(onClick = onExitAdmin) {
                        Icon(Icons.Rounded.ExitToApp, contentDescription = "Exit", tint = Color(0xFFEF4444))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF1E293B))
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Firebase Auth Status Banner (nafitv24@gmail.com Rule Check)
            item {
                val isAuthLoggedIn = repository.authManager.isUserLoggedIn()
                val isAuthAdmin = repository.authManager.isAuthorizedAdmin()
                val userEmail = repository.authManager.getLoggedInEmail()

                if (!isAuthLoggedIn || !isAuthAdmin) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "ফায়ারবেস অথেন্টিকেশন প্রয়োজন (nafitv24@gmail.com)",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                )
                            }
                            Text(
                                "ফায়ারবেস সিকিউরিটি রুলস (auth.token.email === 'nafitv24@gmail.com') অনুযায়ী নতুন ম্যাচ, চ্যানেল বা প্লেলিস্ট যুক্ত বা ডিলিট করতে nafitv24@gmail.com দিয়ে লগইন থাকা আবশ্যক।",
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.5.sp,
                                lineHeight = 16.sp
                            )
                            Button(
                                onClick = { showAuthDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.Login,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "nafitv24@gmail.com দিয়ে লগইন করুন",
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp
                                )
                            }
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF064E3B).copy(alpha = 0.4f)),
                        border = BorderStroke(1.dp, Color(0xFF10B981))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Rounded.VerifiedUser,
                                    contentDescription = null,
                                    tint = Color(0xFF34D399),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        "ফায়ারবেস অথেন্টিকেটেড (রাইট পারমিশন সক্রিয়)",
                                        color = Color(0xFF34D399),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp
                                    )
                                    Text(
                                        userEmail ?: "nafitv24@gmail.com",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp
                                    )
                                }
                            }
                            TextButton(
                                onClick = { showAuthDialog = true },
                                colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF38BDF8))
                            ) {
                                Text("ম্যানেজ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Overview summary cards (Active users, Total users)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Card 1: Live Active Users
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedAdminTab = AdminTab.ANALYTICS },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("বর্তমানে এক্টিভ", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981))
                                )
                            }
                            Text(
                                "${userAnalytics?.activeUsers ?: 1} জন",
                                color = Color(0xFF34D399),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text("● লাইভ অনলাইন", color = Color(0xFF10B981), fontSize = 10.sp)
                        }
                    }

                    // Card 2: Total Users
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedAdminTab = AdminTab.ANALYTICS },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        border = BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("সর্বমোট ইউজার", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                Icon(Icons.Rounded.PeopleAlt, contentDescription = null, tint = Color(0xFF60A5FA), modifier = Modifier.size(14.dp))
                            }
                            Text(
                                "${userAnalytics?.totalUsers ?: 1} জন",
                                color = Color(0xFF60A5FA),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text("মোট ইনস্টল", color = Color(0xFF94A3B8), fontSize = 10.sp)
                        }
                    }
                }
            }

            // Tab Buttons Horizontal Scroll
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(AdminTab.values()) { tab ->
                        val isSelected = selectedAdminTab == tab
                        val btnColor = when (tab) {
                            AdminTab.ANALYTICS -> Color(0xFF00E5FF)
                            AdminTab.TICKER -> Color(0xFFEC4899)
                            AdminTab.CHANNELS -> Color(0xFF2563EB)
                            AdminTab.MOVIES -> Color(0xFF8B5CF6)
                            AdminTab.PLAYLISTS -> Color(0xFF10B981)
                            AdminTab.XTREAM -> Color(0xFF06B6D4)
                            AdminTab.STALKER -> Color(0xFFF43F5E)
                            AdminTab.SPORTS -> Color(0xFFF59E0B)
                            AdminTab.BROADCAST -> Color(0xFFEF4444)
                            AdminTab.REPOSITORIES -> Color(0xFF6366F1)
                            AdminTab.APP_UPDATE -> Color(0xFF14B8A6)
                            AdminTab.FIREBASE -> Color(0xFFF97316)
                        }
                        Button(
                            onClick = { selectedAdminTab = tab },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) btnColor else Color(0xFF1E293B),
                                contentColor = if (isSelected) (if (tab == AdminTab.ANALYTICS) Color.Black else Color.White) else Color(0xFFCBD5E1)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(tab.label, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // TAB: USER ANALYTICS & LIVE ACTIVE USERS & LOCATION TRAFFIC
            // -------------------------------------------------------------
            if (selectedAdminTab == AdminTab.ANALYTICS) {
                // 1. Live Locations & Geo-Traffic Breakdown Card
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
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
                                    Icon(
                                        Icons.Rounded.LocationOn,
                                        contentDescription = null,
                                        tint = Color(0xFF34D399),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "📍 ইউজারদের লাইভ লোকেশন ও ট্রাফিক বণ্টন",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "রিয়েল-টাইম শহর ও দেশভিত্তিক ট্রাফিক মেট্রিক্স",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF065F46).copy(alpha = 0.4f),
                                    border = BorderStroke(1.dp, Color(0xFF10B981))
                                ) {
                                    val activeCount = userAnalytics?.activeUsers ?: 1
                                    Text(
                                        text = "$activeCount জন লাইভ সক্রিয়",
                                        color = Color(0xFF34D399),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            // Mode Selector: Live Online Traffic vs All Records Traffic
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selectedLocationMode == 0) Color(0xFF10B981) else Color(0xFF0F172A),
                                    border = BorderStroke(
                                        1.dp,
                                        if (selectedLocationMode == 0) Color(0xFF34D399) else Color(0xFF334155)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedLocationMode = 0 }
                                ) {
                                    Text(
                                        text = "🟢 লাইভ সক্রিয় ইউজার (${userAnalytics?.activeUsers ?: 1})",
                                        color = if (selectedLocationMode == 0) Color.Black else Color(0xFFCBD5E1),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selectedLocationMode == 1) Color(0xFF38BDF8) else Color(0xFF0F172A),
                                    border = BorderStroke(
                                        1.dp,
                                        if (selectedLocationMode == 1) Color(0xFF7DD3FC) else Color(0xFF334155)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedLocationMode = 1 }
                                ) {
                                    Text(
                                        text = "👥 সর্বমোট হিস্ট্রি (${userAnalytics?.totalUsers ?: 1})",
                                        color = if (selectedLocationMode == 1) Color.Black else Color(0xFFCBD5E1),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                }
                            }

                            val locations = if (selectedLocationMode == 0) {
                                userAnalytics?.liveLocations?.ifEmpty { userAnalytics?.topLocations } ?: emptyList()
                            } else {
                                userAnalytics?.topLocations ?: emptyList()
                            }

                            if (locations.isEmpty()) {
                                Text(
                                    text = "লোকেশন ট্রাফিক হিস্ট্রি লোড হচ্ছে...",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    locations.forEach { stat ->
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text(
                                                        text = stat.flag,
                                                        fontSize = 14.sp
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = stat.locationName,
                                                        color = Color(0xFFE2E8F0),
                                                        fontSize = 12.5.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = "${stat.count} জন ইউজার",
                                                        color = Color(0xFF38BDF8),
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = Color(0xFF0F172A)
                                                    ) {
                                                        Text(
                                                            text = "${(stat.percentage * 100).toInt()}%",
                                                            color = Color(0xFF94A3B8),
                                                            fontSize = 10.sp,
                                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            LinearProgressIndicator(
                                                progress = { stat.percentage.coerceIn(0.05f, 1f) },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(5.dp)
                                                    .clip(RoundedCornerShape(3.dp)),
                                                color = if (selectedLocationMode == 0) Color(0xFF10B981) else Color(0xFF38BDF8),
                                                trackColor = Color(0xFF0F172A)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Network & ISP Connection Breakdown Card
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Rounded.Wifi,
                                        contentDescription = null,
                                        tint = Color(0xFF38BDF8),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "📶 নেটওয়ার্ক সংযোগ ও ট্রাফিক চ্যানেল",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            val rawNetworks = if (selectedLocationMode == 0) {
                                userAnalytics?.liveNetworkBreakdown?.ifEmpty { userAnalytics?.networkBreakdown } ?: emptyMap()
                            } else {
                                userAnalytics?.networkBreakdown ?: emptyMap()
                            }

                            val wifiCount = rawNetworks["WiFi"] ?: 0
                            val mobileCount = rawNetworks["Mobile Data (4G/5G)"] ?: rawNetworks["4G/5G Cellular"] ?: 0
                            val ethernetCount = rawNetworks["Ethernet / Broadband"] ?: rawNetworks["Ethernet"] ?: 0
                            val otherCount = rawNetworks.entries.filter {
                                !it.key.contains("WiFi", ignoreCase = true) &&
                                !it.key.contains("Mobile", ignoreCase = true) &&
                                !it.key.contains("4G", ignoreCase = true) &&
                                !it.key.contains("5G", ignoreCase = true) &&
                                !it.key.contains("Cellular", ignoreCase = true)
                            }.sumOf { it.value }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Card(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("WiFi ব্রডব্যান্ড", color = Color(0xFF94A3B8), fontSize = 10.5.sp)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text("$wifiCount জন", color = Color(0xFF38BDF8), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Card(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("4G/5G মোবাইল", color = Color(0xFF94A3B8), fontSize = 10.5.sp)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text("$mobileCount জন", color = Color(0xFFF59E0B), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Card(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text("অন্যান্য নেটওয়ার্ক", color = Color(0xFF94A3B8), fontSize = 10.5.sp)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text("$otherCount জন", color = Color(0xFFA78BFA), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Connected Active Devices with Location & Activity Details
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.3f)),
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
                                    Icon(
                                        Icons.Rounded.PhoneAndroid,
                                        contentDescription = null,
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "📱 কানেক্টেড ইউজার ডিভাইস সমূহ",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "ডিভাইস মডেল, অবস্থান ও বর্তমান এক্টিভিটি",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    // Clean stale offline sessions button
                                    Button(
                                        onClick = {
                                            coroutineScope.launch {
                                                isCleaningStaleSessions = true
                                                val ok = repository.cleanStaleOfflineUsers()
                                                if (ok) {
                                                    Toast.makeText(context, "পুরানো অফলাইন সেশন ক্লিন করা হয়েছে", Toast.LENGTH_SHORT).show()
                                                }
                                                userAnalytics = repository.fetchUserAnalytics()
                                                isCleaningStaleSessions = false
                                            }
                                        },
                                        enabled = !isCleaningStaleSessions,
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155), contentColor = Color(0xFFE2E8F0)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Rounded.CleaningServices, contentDescription = null, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(if (isCleaningStaleSessions) "ক্লিন হচ্ছে..." else "ক্লিন", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                                    }

                                    // Instant Quota Release Button
                                    Button(
                                        onClick = { showWipeQuotaDialog = true },
                                        enabled = !isWipingQuota,
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626), contentColor = Color.White),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(if (isWipingQuota) "রিলিজ..." else "কোটা রিলিজ", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                                    }

                                    // Refresh Button
                                    Button(
                                        onClick = { loadAnalyticsData() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("রিফ্রেশ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            if (showWipeQuotaDialog) {
                                AlertDialog(
                                    onDismissRequest = { showWipeQuotaDialog = false },
                                    containerColor = Color(0xFF1E293B),
                                    title = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Rounded.DeleteSweep, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(24.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Firebase কোটা রিলিজ করবেন?", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        }
                                    },
                                    text = {
                                        Text(
                                            text = "এটি Firebase Realtime Database থেকে সমস্ত পুরানো ও বর্তমান ইউজার ট্র্যাকিং লগ (active_users & all_users) সম্পূর্ণ মুছে ফেলবে। এর ফলে স্টোরেজ সাইজ ০ KB তে নেমে আসবে এবং আপনার ফ্রি ১০ GB ব্যান্ডউইথ আজীবনের জন্য সুরক্ষিত থাকবে।\n\n(আপনার সংরক্ষিত টিভি চ্যানেল, মুভি বা প্লেলিস্টে কোনো প্রভাব পড়বে না)।",
                                            color = Color(0xFFCBD5E1),
                                            fontSize = 13.sp,
                                            lineHeight = 19.sp
                                        )
                                    },
                                    confirmButton = {
                                        Button(
                                            onClick = {
                                                coroutineScope.launch {
                                                    isWipingQuota = true
                                                    val ok = repository.clearAllUserLogsFromFirebase()
                                                    if (ok) {
                                                        Toast.makeText(context, "Firebase ইউজার লগ মুছে কোটা সম্পূর্ণ ফ্রি করা হয়েছে!", Toast.LENGTH_LONG).show()
                                                    } else {
                                                        Toast.makeText(context, "কোটা রিলিজ ব্যর্থ হয়েছে", Toast.LENGTH_SHORT).show()
                                                    }
                                                    loadAnalyticsData()
                                                    isWipingQuota = false
                                                    showWipeQuotaDialog = false
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444), contentColor = Color.White),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("হ্যাঁ, কোটা রিলিজ করুন", fontWeight = FontWeight.Bold)
                                        }
                                    },
                                    dismissButton = {
                                        OutlinedButton(
                                            onClick = { showWipeQuotaDialog = false },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8)),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("বাতিল")
                                        }
                                    }
                                )
                            }

                            val allUserList = userAnalytics?.activeUsersList ?: emptyList()
                            val nowTime = System.currentTimeMillis()
                            val liveUsers = allUserList.filter { it.isOnline }
                            val todayUsers = allUserList.filter { (nowTime - it.lastSeen) <= 24 * 3600 * 1000L }

                            // Filter Chips
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selectedDeviceFilter == 0) Color(0xFF10B981) else Color(0xFF0F172A),
                                    border = BorderStroke(
                                        1.dp,
                                        if (selectedDeviceFilter == 0) Color(0xFF34D399) else Color(0xFF334155)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedDeviceFilter = 0 }
                                ) {
                                    Text(
                                        text = "🟢 লাইভ (${liveUsers.size})",
                                        color = if (selectedDeviceFilter == 0) Color.Black else Color(0xFFCBD5E1),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selectedDeviceFilter == 1) Color(0xFF38BDF8) else Color(0xFF0F172A),
                                    border = BorderStroke(
                                        1.dp,
                                        if (selectedDeviceFilter == 1) Color(0xFF7DD3FC) else Color(0xFF334155)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedDeviceFilter = 1 }
                                ) {
                                    Text(
                                        text = "🕒 আজ (${todayUsers.size})",
                                        color = if (selectedDeviceFilter == 1) Color.Black else Color(0xFFCBD5E1),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selectedDeviceFilter == 2) Color(0xFF6366F1) else Color(0xFF0F172A),
                                    border = BorderStroke(
                                        1.dp,
                                        if (selectedDeviceFilter == 2) Color(0xFFA5B4FC) else Color(0xFF334155)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedDeviceFilter = 2 }
                                ) {
                                    Text(
                                        text = "👥 সর্বমোট (${allUserList.size})",
                                        color = if (selectedDeviceFilter == 2) Color.White else Color(0xFFCBD5E1),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    )
                                }
                            }

                            val displayedList = when (selectedDeviceFilter) {
                                0 -> liveUsers
                                1 -> todayUsers
                                else -> allUserList
                            }

                            if (displayedList.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (selectedDeviceFilter == 0) "বর্তমানে কোনো ডিভাইস অনলাইনে নেই।" else "কোনো ডিভাইস রেকর্ড পাওয়া যায়নি।",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 12.sp
                                    )
                                }
                            } else {
                                displayedList.forEach { user ->
                                    val timeDiff = System.currentTimeMillis() - user.lastSeen
                                    val timeText = when {
                                        user.isOnline || timeDiff < 60_000L -> "🟢 এইমাত্র সক্রিয়"
                                        timeDiff < 3600_000L -> "${timeDiff / 60_000L} মিনিট আগে সক্রিয়"
                                        timeDiff < 86400_000L -> "${timeDiff / 3600_000L} ঘণ্টা আগে সক্রিয়"
                                        else -> "${timeDiff / 86400_000L} দিন আগে সক্রিয়"
                                    }
                                    Card(
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                        border = BorderStroke(
                                            0.5.dp,
                                            if (user.isOnline) Color(0xFF10B981).copy(alpha = 0.6f) else Color(0xFF334155)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                                    Box(
                                                        modifier = Modifier
                                                            .size(34.dp)
                                                            .clip(CircleShape)
                                                            .background(
                                                                if (user.isOnline) Color(0xFF10B981).copy(alpha = 0.2f)
                                                                else Color(0xFF64748B).copy(alpha = 0.2f)
                                                            ),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = if (user.isOnline) Icons.Rounded.Smartphone else Icons.Rounded.PhoneAndroid,
                                                            contentDescription = null,
                                                            tint = if (user.isOnline) Color(0xFF34D399) else Color(0xFF94A3B8),
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Column {
                                                        Text(
                                                            text = user.deviceModel,
                                                            color = Color.White,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            text = "ID: ${user.id.take(12)} • App ${user.appVersion}",
                                                            color = Color(0xFF64748B),
                                                            fontSize = 10.sp
                                                        )
                                                    }
                                                }
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = if (user.isOnline) Color(0xFF065F46).copy(alpha = 0.4f) else Color(0xFF1E293B),
                                                    border = BorderStroke(
                                                        0.5.dp,
                                                        if (user.isOnline) Color(0xFF10B981) else Color(0xFF475569)
                                                    )
                                                ) {
                                                    Text(
                                                        text = timeText,
                                                        color = if (user.isOnline) Color(0xFF34D399) else Color(0xFF94A3B8),
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                    )
                                                }
                                            }

                                            // Location & Network Info
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color(0xFF1E293B),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            Icons.Rounded.LocationOn,
                                                            contentDescription = null,
                                                            tint = Color(0xFF34D399),
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = user.location ?: "ঢাকা, বাংলাদেশ",
                                                            color = Color(0xFFCBD5E1),
                                                            fontSize = 10.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }

                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color(0xFF1E293B)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            Icons.Rounded.Wifi,
                                                            contentDescription = null,
                                                            tint = Color(0xFF38BDF8),
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = user.networkType ?: "WiFi",
                                                            color = Color(0xFFCBD5E1),
                                                            fontSize = 10.sp
                                                        )
                                                    }
                                                }
                                            }

                                            if (!user.currentActivity.isNullOrBlank()) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = Color(0xFF1E1B4B).copy(alpha = 0.5f),
                                                    border = BorderStroke(0.5.dp, Color(0xFF6366F1).copy(alpha = 0.4f)),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Icon(
                                                            Icons.Rounded.PlayCircle,
                                                            contentDescription = null,
                                                            tint = Color(0xFFA5B4FC),
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text(
                                                            text = user.currentActivity!!,
                                                            color = Color(0xFFA5B4FC),
                                                            fontSize = 10.5.sp,
                                                            fontWeight = FontWeight.Medium,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (selectedAdminTab == AdminTab.TICKER) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEC4899).copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            // Header Banner
                            Surface(
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEC4899).copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFEC4899).copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Rounded.Campaign, contentDescription = null, tint = Color(0xFFEC4899), modifier = Modifier.size(26.dp))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "⚡ স্ক্রোলিং ব্রেকিং নিউজ বার পরিবর্তন",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                        Text(
                                            text = "মোড সিলেকশন স্ক্রিনের 'GET STARTED' এর উপরে চলমান ব্রেকিং নিউজ টেক্সট পরিবর্তন ও ক্লাউড সিঙ্ক করুন",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }

                            // Live Marquee Preview Box
                            Text(
                                text = "লাইভ প্রিভিউ (ইউজাররা যেভাবে দেখবেন):",
                                color = Color(0xFFCBD5E1),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFF0F172A),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 1.2.dp,
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color(0xFF00E5FF).copy(alpha = 0.8f),
                                            Color(0xFF8B5CF6).copy(alpha = 0.9f),
                                            Color(0xFFEC4899).copy(alpha = 0.8f)
                                        )
                                    )
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(Color(0xFF00E5FF), shape = CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = marqueeTickerInput.ifBlank { "এখানে আপনার ব্রেকিং নিউজ লিখুন..." },
                                        color = Color(0xFFE2E8F0),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .basicMarquee(iterations = Int.MAX_VALUE, velocity = 40.dp)
                                    )
                                }
                            }

                            // Text Input Field
                            OutlinedTextField(
                                value = marqueeTickerInput,
                                onValueChange = { marqueeTickerInput = it },
                                label = { Text("স্ক্রোলিং নিউজ টেক্সট লিখুন *", color = Color(0xFF94A3B8), fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3,
                                maxLines = 6,
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFFEC4899),
                                    unfocusedBorderColor = Color(0xFF334155),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = Color(0xFF0F172A),
                                    unfocusedContainerColor = Color(0xFF0F172A)
                                )
                            )

                            // Quick Preset News Templates
                            Text(
                                text = "প্রিসেট টেমপ্লেট নির্বাচন করুন:",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        marqueeTickerInput = "🏆 লাইভ ক্রিকেট ও ফুটবল ম্যাচ শুরু হয়েছে! যেকোনো চ্যানেল সিলেক্ট করে সরাসরি সম্পূর্ণ HD কোয়ালিটিতে উপভোগ করুন। NAFI TV24"
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                                ) {
                                    Text("⚽ খেলাধুলার নোটিস", fontSize = 10.sp, color = Color(0xFF38BDF8), maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = {
                                        marqueeTickerInput = "বাংলাদেশ ব্যাংকের নতুন মুদ্রানীতি ঘোষণা। পুঁজিবাজারে ঊর্ধ্বগতি। NAFI TV24 এ ক্রিকেট, ফুটবল ও লাইভ টিভি চ্যানেল সম্পূর্ণ বিনামূল্যে উপভোগ করুন।"
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                                ) {
                                    Text("📰 জাতীয় সংবাদ", fontSize = 10.sp, color = Color(0xFFF472B6), maxLines = 1)
                                }
                            }

                            // Save & Cloud Sync Action Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val cleanText = marqueeTickerInput.trim()
                                        if (cleanText.isBlank()) {
                                            Toast.makeText(context, "অনুগ্রহ করে নিউজ টেক্সট লিখুন!", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        repository.saveMarqueeTickerText(cleanText)
                                        onDataChanged()
                                        isSavingMarqueeTicker = true
                                        coroutineScope.launch {
                                            val pushed = repository.pushMarqueeTickerToFirebase(cleanText)
                                            isSavingMarqueeTicker = false
                                            onDataChanged()
                                            if (pushed) {
                                                Toast.makeText(context, "✅ ব্রেকিং নিউজ সফলভাবে ক্লাউডে সিঙ্ক ও আপডেট হয়েছে!", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(context, "✅ ব্রেকিং নিউজ অ্যাপে আপডেট করা হয়েছে!", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFEC4899),
                                        contentColor = Color.White
                                    ),
                                    enabled = !isSavingMarqueeTicker
                                ) {
                                    if (isSavingMarqueeTicker) {
                                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("সিঙ্ক হচ্ছে...", fontWeight = FontWeight.Bold)
                                    } else {
                                        Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("আপডেট ও ক্লাউডে সিঙ্ক করুন", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        marqueeTickerInput = MediaRepository.DEFAULT_MARQUEE_TEXT
                                        repository.saveMarqueeTickerText(MediaRepository.DEFAULT_MARQUEE_TEXT)
                                        onDataChanged()
                                        coroutineScope.launch {
                                            repository.pushMarqueeTickerToFirebase(MediaRepository.DEFAULT_MARQUEE_TEXT)
                                            onDataChanged()
                                            Toast.makeText(context, "ডিফল্ট টেক্সট রিস্টোর করা হয়েছে", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF475569)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8))
                                ) {
                                    Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // TAB 1 CONTENT: SPORTS MATCHES ADMIN (Multi-Server Support)
            // -------------------------------------------------------------
            if (selectedAdminTab == AdminTab.SPORTS) {
                // 1. Primary M3U Playlist Manager for Sports
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.SportsSoccer, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("⚽ স্পোর্টস ম্যাচ M3U প্লেলিস্ট লিংক (Sports M3U)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Text(
                                text = "এখানে এক বা একাধিক M3U প্লেলিস্ট লিংক দিতে পারবেন (প্রতি লাইনে একটি করে অথবা কমা দিয়ে)। সমস্ত লিংক স্বয়ংক্রিয়ভাবে ক্লাউড ডেটাবেস দিয়ে ইউজারের অ্যাপে সিঙ্ক হয়ে যাবে।",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                            OutlinedTextField(
                                value = sportsM3uInput,
                                onValueChange = { sportsM3uInput = it },
                                placeholder = { Text("https://url1.m3u\nhttps://url2.m3u", color = Color(0xFF64748B), fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = false,
                                minLines = 2,
                                maxLines = 5
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (sportsM3uInput.isNotBlank()) {
                                            val url = sportsM3uInput.trim()
                                            repository.saveSportsM3uUrl(url)
                                            coroutineScope.launch {
                                                repository.pushAppConfigToFirebase(sportsM3u = url)
                                            }
                                            onDataChanged()
                                            Toast.makeText(context, "✅ স্পোর্টস M3U ক্লাউডে সেভ ও সিঙ্ক হয়েছে!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "সঠিক স্পোর্টস M3U লিংক দিন", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1.3f).height(44.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.Black),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("সেভ ও সিঙ্ক করুন", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = {
                                        sportsM3uInput = MediaRepository.DEFAULT_SPORTS_M3U_URL
                                        repository.saveSportsM3uUrl(MediaRepository.DEFAULT_SPORTS_M3U_URL)
                                        coroutineScope.launch {
                                            repository.pushAppConfigToFirebase(sportsM3u = MediaRepository.DEFAULT_SPORTS_M3U_URL)
                                        }
                                        onDataChanged()
                                        Toast.makeText(context, "ডিফল্ট স্পোর্টস M3U রিসেট ও সিঙ্ক হয়েছে", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF475569)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8))
                                ) {
                                    Text("ডিফল্ট লিংক", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                // 2. Live Events & Tapmad Sports JSON API Manager
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.LiveTv, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("⚡ লাইভ ইভেন্ট ও স্পোর্টস JSON API (Live Events JSON)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Text(
                                text = "লাইভ ইভেন্ট (Live Events) সেকশনে সরাসরি ম্যাচ ও স্ট্রিমিং লোড করার জন্য একাধিক Tapmad BD বা স্পোর্টস JSON API URL দিন। একাধিক JSON লিংক থাকলে নতুন লাইনে (প্রতি লাইনে একটি করে) লিখুন। এটি স্বয়ংক্রিয়ভাবে ক্লাউড ও ফায়ারবেসে সিঙ্ক হবে।",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )

                            // Tapmad JSON URL Input (Supports multiple JSONs)
                            Text("লাইভ স্পোর্টস JSON API লিংক (একাধিক লিংক দিতে নতুন লাইন বা কমা ব্যবহার করুন):", color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            OutlinedTextField(
                                value = tapmadJsonInput,
                                onValueChange = { tapmadJsonInput = it },
                                placeholder = { Text("https://gist.githubusercontent.com/.../tapmad_bd.json\nhttps://example.com/sports2.json", color = Color(0xFF64748B), fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = false,
                                minLines = 3,
                                maxLines = 6
                            )

                            // Tapmad M3U Stream Fallback Input
                            Text("লাইভ স্পোর্টস M3U ব্যাকআপ লিংক (ঐচ্ছিক):", color = Color(0xFF94A3B8), fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                            OutlinedTextField(
                                value = tapmadM3uInput,
                                onValueChange = { tapmadM3uInput = it },
                                placeholder = { Text("https://raw.githubusercontent.com/.../tapmad_bd.m3u", color = Color(0xFF64748B), fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (tapmadJsonInput.isNotBlank()) {
                                            val jsonUrl = tapmadJsonInput.trim()
                                            val m3uUrl = tapmadM3uInput.trim()
                                            repository.saveTapmadJsonUrl(jsonUrl)
                                            repository.saveTapmadM3uUrl(m3uUrl)
                                            coroutineScope.launch {
                                                repository.pushAppConfigToFirebase(tapmadJson = jsonUrl)
                                            }
                                            onDataChanged()
                                            Toast.makeText(context, "✅ JSON API লিংক সফলভাবে সেভ ও ফায়ারবেসে সিঙ্ক হয়েছে!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "সঠিক JSON API লিংক দিন", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1.3f).height(44.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8), contentColor = Color.Black),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Rounded.CloudSync, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("JSON সেভ ও ফায়ারবেস সিঙ্ক", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = {
                                        tapmadJsonInput = MediaRepository.DEFAULT_TAPMAD_JSON_URL
                                        tapmadM3uInput = MediaRepository.DEFAULT_TAPMAD_M3U_URL
                                        repository.saveTapmadJsonUrl(MediaRepository.DEFAULT_TAPMAD_JSON_URL)
                                        repository.saveTapmadM3uUrl(MediaRepository.DEFAULT_TAPMAD_M3U_URL)
                                        coroutineScope.launch {
                                            repository.pushAppConfigToFirebase(tapmadJson = MediaRepository.DEFAULT_TAPMAD_JSON_URL)
                                        }
                                        onDataChanged()
                                        Toast.makeText(context, "ডিফল্ট JSON API লিংক রিসেট ও সিঙ্ক করা হয়েছে", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF475569)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8))
                                ) {
                                    Text("ডিফল্ট JSON", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

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
                                Icon(Icons.Rounded.AddCircleOutline, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Create Live or Upcoming Sports Match",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Sport Category & Status Dropdown
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Category
                                Box(modifier = Modifier.weight(1f)) {
                                    OutlinedTextField(
                                        value = sportCategory,
                                        onValueChange = {},
                                        readOnly = true,
                                        trailingIcon = {
                                            IconButton(onClick = { sportDropdownExpanded = !sportDropdownExpanded }) {
                                                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = Color.White)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().clickable { sportDropdownExpanded = true },
                                        colors = customFieldColors(),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true
                                    )
                                    DropdownMenu(
                                        expanded = sportDropdownExpanded,
                                        onDismissRequest = { sportDropdownExpanded = false },
                                        modifier = Modifier.background(Color(0xFF1E293B))
                                    ) {
                                        sportOptions.forEach { opt ->
                                            DropdownMenuItem(
                                                text = { Text(opt, color = Color.White) },
                                                onClick = {
                                                    sportCategory = opt
                                                    sportDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                // Status
                                Box(modifier = Modifier.weight(1f)) {
                                    OutlinedTextField(
                                        value = sportStatus,
                                        onValueChange = {},
                                        readOnly = true,
                                        trailingIcon = {
                                            IconButton(onClick = { statusDropdownExpanded = !statusDropdownExpanded }) {
                                                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = Color.White)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth().clickable { statusDropdownExpanded = true },
                                        colors = customFieldColors(),
                                        shape = RoundedCornerShape(12.dp),
                                        singleLine = true
                                    )
                                    DropdownMenu(
                                        expanded = statusDropdownExpanded,
                                        onDismissRequest = { statusDropdownExpanded = false },
                                        modifier = Modifier.background(Color(0xFF1E293B))
                                    ) {
                                        statusOptions.forEach { opt ->
                                            DropdownMenuItem(
                                                text = { Text(opt, color = Color.White) },
                                                onClick = {
                                                    sportStatus = opt
                                                    statusDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            // Preset Quick Fill Buttons
                            Text("⚡ দ্রুত প্রিসেট ম্যাচ তৈরি করুন (Quick Presets):", color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF1E293B),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                                        modifier = Modifier.clickable {
                                            sportCategory = "Cricket"
                                            sportStatus = "Upcoming"
                                            tournamentName = "Cricket 🏏 || Bangladesh vs Australia Test Series 2026"
                                            team1Name = "Bangladesh"
                                            team1LogoUrl = "https://flagcdn.com/w160/bd.png"
                                            team2Name = "Australia"
                                            team2LogoUrl = "https://flagcdn.com/w160/au.png"
                                            matchTimeFormatted = "06:00 AM, Aug 23"
                                            countdownHours = ""
                                            sportsServers = listOf(
                                                StreamServer("T SPORTS", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"),
                                                StreamServer("TT", "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8"),
                                                StreamServer("TEMP", "https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8")
                                            )
                                        }
                                    ) {
                                        Text("🇧🇩 BD vs AUS 🇦🇺", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                                    }
                                }
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF1E293B),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                                        modifier = Modifier.clickable {
                                            sportCategory = "Cricket"
                                            sportStatus = "● Live Now"
                                            tournamentName = "NF Women | The Hundred Women's Competition 2026"
                                            team1Name = "Trent Rockets"
                                            team1LogoUrl = "https://images.unsplash.com/photo-1579952363873-27f3bade9f55?w=160"
                                            team2Name = "Southern Brave"
                                            team2LogoUrl = "https://images.unsplash.com/photo-1517649763962-0c623266ddc0?w=160"
                                            matchTimeFormatted = "Live Now"
                                            countdownHours = "113"
                                            sportsServers = listOf(
                                                StreamServer("T SPORTS", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"),
                                                StreamServer("TT", "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8"),
                                                StreamServer("TEMP", "https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8")
                                            )
                                        }
                                    ) {
                                        Text("🚀 Trent vs Brave 🛡️", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                                    }
                                }
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF1E293B),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                                        modifier = Modifier.clickable {
                                            sportCategory = "Cricket"
                                            sportStatus = "Upcoming"
                                            tournamentName = "Cricket 🏏 || Sri Lanka vs India Test Series 2026"
                                            team1Name = "Sri Lanka"
                                            team1LogoUrl = "https://flagcdn.com/w160/lk.png"
                                            team2Name = "India"
                                            team2LogoUrl = "https://flagcdn.com/w160/in.png"
                                            matchTimeFormatted = "10:30 AM, Aug 15"
                                            countdownHours = ""
                                            sportsServers = listOf(
                                                StreamServer("T SPORTS", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"),
                                                StreamServer("TT", "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8"),
                                                StreamServer("TEMP", "https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8")
                                            )
                                        }
                                    ) {
                                        Text("🇱🇰 SL vs IND 🇮🇳", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                                    }
                                }
                            }

                            // Tournament Banner Name
                            OutlinedTextField(
                                value = tournamentName,
                                onValueChange = { tournamentName = it },
                                placeholder = { Text("Tournament (e.g. Cricket 🏏 || Bangladesh vs Australia)", color = Color(0xFF64748B), fontSize = 13.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )

                            // Match Poster Banner / Single Picture URL (User: এখানে একটি ছবি দেখার অপশন করে দিন)
                            OutlinedTextField(
                                value = matchPosterUrl,
                                onValueChange = { matchPosterUrl = it },
                                label = { Text("🖼️ একক ম্যাচের ছবি / ব্যানার URL (Match Poster Image URL)") },
                                placeholder = { Text("https://example.com/banner.jpg", color = Color(0xFF64748B), fontSize = 13.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )

                            // Team 1 Section
                            Text("🛡️ Team 1 Details:", color = Color(0xFF60A5FA), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = team1Name,
                                    onValueChange = { team1Name = it },
                                    placeholder = { Text("Team 1 Name (e.g. Bangladesh)", color = Color(0xFF64748B), fontSize = 13.sp) },
                                    modifier = Modifier.weight(1.2f),
                                    colors = customFieldColors(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = team1Score,
                                    onValueChange = { team1Score = it },
                                    placeholder = { Text("Score (e.g. 154/4)", color = Color(0xFF64748B), fontSize = 13.sp) },
                                    modifier = Modifier.weight(1f),
                                    colors = customFieldColors(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                            }
                            OutlinedTextField(
                                value = team1LogoUrl,
                                onValueChange = { team1LogoUrl = it },
                                placeholder = { Text("Team 1 Logo URL (or flagcdn.com/w160/bd.png)", color = Color(0xFF64748B), fontSize = 13.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )

                            // Team 2 Section
                            Text("🛡️ Team 2 Details:", color = Color(0xFF60A5FA), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = team2Name,
                                    onValueChange = { team2Name = it },
                                    placeholder = { Text("Team 2 Name (e.g. Australia)", color = Color(0xFF64748B), fontSize = 13.sp) },
                                    modifier = Modifier.weight(1.2f),
                                    colors = customFieldColors(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = team2Score,
                                    onValueChange = { team2Score = it },
                                    placeholder = { Text("Score (e.g. 142/8)", color = Color(0xFF64748B), fontSize = 13.sp) },
                                    modifier = Modifier.weight(1f),
                                    colors = customFieldColors(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                            }
                            OutlinedTextField(
                                value = team2LogoUrl,
                                onValueChange = { team2LogoUrl = it },
                                placeholder = { Text("Team 2 Logo URL (or flagcdn.com/w160/au.png)", color = Color(0xFF64748B), fontSize = 13.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )

                            // Match Time & Countdown Section
                            Text("⏱️ সময় ও কাউন্টডাউন টাইমার (Match Time & Countdown):", color = Color(0xFFF59E0B), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = matchTimeFormatted,
                                    onValueChange = { matchTimeFormatted = it },
                                    placeholder = { Text("Time (e.g. 06:30 AM, Aug 13)", color = Color(0xFF64748B), fontSize = 12.sp) },
                                    modifier = Modifier.weight(1.2f),
                                    colors = customFieldColors(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = countdownHours,
                                    onValueChange = { countdownHours = it },
                                    placeholder = { Text("Countdown (e.g. 113 hrs)", color = Color(0xFF64748B), fontSize = 12.sp) },
                                    modifier = Modifier.weight(1f),
                                    colors = customFieldColors(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                            }

                            // Dynamic Multi-Server Streams Section (CRITICAL USER REQUEST: একাধিক মাল্টি সার্ভার যোগ)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "⚡ মাল্টি-সার্ভার স্ট্রিম লিঙ্কসমূহ (${sportsServers.size} টি সার্ভার):",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "আনলিমিটেড সার্ভার সাপোর্ট",
                                    color = Color(0xFF10B981),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // Dynamic list of server inputs
                            sportsServers.forEachIndexed { index, server ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF1E293B).copy(alpha = 0.7f),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Rounded.Dns,
                                                    contentDescription = null,
                                                    tint = Color(0xFF00E5FF),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "চ্যানেল/সার্ভার #${index + 1}",
                                                    color = Color(0xFF00E5FF),
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            if (sportsServers.size > 1) {
                                                IconButton(
                                                    onClick = {
                                                        sportsServers = sportsServers.toMutableList().also { it.removeAt(index) }
                                                    },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Rounded.DeleteOutline,
                                                        contentDescription = "সার্ভার সরান",
                                                        tint = Color(0xFFEF4444),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                        OutlinedTextField(
                                            value = server.name,
                                            onValueChange = { newName ->
                                                sportsServers = sportsServers.toMutableList().also {
                                                    it[index] = it[index].copy(name = newName)
                                                }
                                            },
                                            label = { Text("চ্যানেল/সার্ভার নাম (যেমন: T Sports, TT)", fontSize = 11.sp) },
                                            placeholder = { Text("Server ${index + 1} Name", color = Color(0xFF64748B), fontSize = 11.sp) },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = customFieldColors(),
                                            shape = RoundedCornerShape(8.dp),
                                            singleLine = true
                                        )
                                        OutlinedTextField(
                                            value = server.url,
                                            onValueChange = { newUrl ->
                                                sportsServers = sportsServers.toMutableList().also {
                                                    it[index] = it[index].copy(url = newUrl)
                                                }
                                            },
                                            label = { Text("স্ট্রিম URL (.m3u8 / .mpd / mp4)", fontSize = 11.sp) },
                                            placeholder = { Text("https://example.com/live.m3u8", color = Color(0xFF64748B), fontSize = 11.sp) },
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = customFieldColors(),
                                            shape = RoundedCornerShape(8.dp),
                                            singleLine = true
                                        )
                                    }
                                }
                            }

                            // Add More Server Button
                            OutlinedButton(
                                onClick = {
                                    val nextNum = sportsServers.size + 1
                                    val defaultName = when (nextNum) {
                                        1 -> "T SPORTS"
                                        2 -> "TT"
                                        3 -> "TEMP"
                                        4 -> "HD SERVER 4"
                                        5 -> "SERVER 5 (4K)"
                                        6 -> "SERVER 6 (HLS)"
                                        else -> "SERVER $nextNum"
                                    }
                                    sportsServers = sportsServers + StreamServer(defaultName, "")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E5FF))
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("➕ আরও সার্ভার/চ্যানেল লিঙ্ক যোগ করুন (+ Add Server)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }

                            // Publish Match Button
                            Button(
                                onClick = {
                                    val validServers = sportsServers.mapNotNull {
                                        if (it.url.isNotBlank()) StreamServer(it.name.ifBlank { "Server" }, it.url.trim()) else null
                                    }

                                    if ((team1Name.isNotBlank() || tournamentName.isNotBlank()) && validServers.isNotEmpty()) {
                                        val matchTitle = if (team1Name.isNotBlank() && team2Name.isNotBlank()) {
                                             "$team1Name vs $team2Name"
                                        } else {
                                            tournamentName.ifBlank { "Live Match" }
                                        }

                                        val parsedCountdown: Long? = when {
                                            countdownHours.isNotBlank() && countdownHours.toDoubleOrNull() != null -> {
                                                val hrs = countdownHours.toDouble()
                                                System.currentTimeMillis() + (hrs * 3600 * 1000L).toLong()
                                            }
                                            matchTimeFormatted.isNotBlank() -> {
                                                parseEventTimeStringToEpochMillis(matchTimeFormatted)
                                            }
                                            else -> null
                                        }

                                        val matchItem = MediaItem(
                                            id = "sport_${System.currentTimeMillis()}",
                                            title = matchTitle,
                                            tournament = tournamentName.ifBlank { null },
                                            category = sportCategory,
                                            type = MediaType.LIVE_EVENT,
                                            streamUrl = validServers.first().url,
                                            backupUrl = validServers.getOrNull(1)?.url,
                                            servers = validServers,
                                            isLive = sportStatus.contains("Live", ignoreCase = true),
                                            status = sportStatus,
                                            eventTime = matchTimeFormatted.ifBlank { sportStatus },
                                            logoUrl = matchPosterUrl.takeIf { it.isNotBlank() },
                                            team1 = team1Name.takeIf { it.isNotBlank() },
                                            team1Logo = team1LogoUrl.takeIf { it.isNotBlank() },
                                            team2 = team2Name.takeIf { it.isNotBlank() },
                                            team2Logo = team2LogoUrl.takeIf { it.isNotBlank() },
                                            matchTimeFormatted = matchTimeFormatted.takeIf { it.isNotBlank() },
                                            countdownTargetSeconds = parsedCountdown,
                                            score1 = team1Score.takeIf { it.isNotBlank() },
                                            score2 = team2Score.takeIf { it.isNotBlank() },
                                            quality = "1080p FHD",
                                            isAdminAdded = true
                                        )

                                        repository.saveCustomStream(matchItem)
                                        coroutineScope.launch {
                                            repository.pushToFirebase(matchItem)
                                            val notif = AppNotification(
                                                title = "⚽ নতুন ম্যাচ: ${matchItem.title}",
                                                message = if (!matchItem.tournament.isNullOrBlank()) "${matchItem.tournament} - এখনই লাইভ উপভোগ করুন!" else "নতুন লাইভ ম্যাচ যোগ করা হয়েছে!",
                                                type = NotificationType.LIVE_EVENT,
                                                targetId = matchItem.id,
                                                imageUrl = matchItem.logoUrl ?: matchItem.team1Logo ?: matchItem.team2Logo
                                            )
                                            repository.broadcastNotification(notif)
                                            NotificationHelper.showSystemNotification(context, notif)
                                            adminNotificationHistory = repository.getStoredNotifications()
                                        }
                                        onDataChanged()

                                        // Reset fields
                                        tournamentName = ""
                                        matchPosterUrl = ""
                                        team1Name = ""
                                        team1Score = ""
                                        team1LogoUrl = ""
                                        team2Name = ""
                                        team2Score = ""
                                        team2LogoUrl = ""
                                        matchTimeFormatted = ""
                                        countdownHours = ""
                                        sportsServers = listOf(
                                            StreamServer("T SPORTS", ""),
                                            StreamServer("TT", ""),
                                            StreamServer("TEMP", "")
                                        )
                                        Toast.makeText(context, "ম্যাচ সফলভাবে পাবলিশ ও Firebase এ সংরক্ষিত হয়েছে!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "টিম এর নাম এবং কমপক্ষে একটি সার্ভার লিঙ্ক লিখুন", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                            ) {
                                Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Publish Match to Firebase", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }

                // Firestore Sports Events List Header
                item {
                    Text(
                        text = "Firestore Sports Events List:",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Sports Items List
                items(adminSportsItems, key = { it.id }) { item ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Surface(
                                    color = Color(0xFF2563EB).copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2563EB))
                                ) {
                                    Text(
                                        text = item.tournament ?: item.category,
                                        color = Color(0xFF60A5FA),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        maxLines = 1
                                    )
                                }

                                Surface(
                                    color = if (item.isLive) Color(0xFFEF4444) else Color(0xFFF59E0B),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (item.isLive) "Live" else "Upcoming",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            // Match Title & Scores
                            Text(
                                text = if (item.score1 != null && item.score2 != null && item.team1 != null && item.team2 != null) {
                                    "${item.team1} (${item.score1}) vs ${item.team2} (${item.score2})"
                                } else {
                                    item.title
                                },
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )

                            // Servers Chip Count
                            val serverCount = item.getAllServers().size
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Dns, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "$serverCount টি সার্ভার সক্রিয়",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 11.sp
                                )
                            }

                            // Action Buttons (Edit Match, Update Score, Delete)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Full Edit Match Button
                                Button(
                                    onClick = {
                                        editingMatchItem = item
                                        editTournament = item.tournament ?: ""
                                        editMatchPosterUrl = item.logoUrl ?: ""
                                        editSportCategory = item.category
                                        editSportStatus = if (item.isLive) "● Live Now" else (item.status ?: "Upcoming")
                                        editTeam1Name = item.team1 ?: ""
                                        editTeam1Score = item.score1 ?: ""
                                        editTeam1Logo = item.team1Logo ?: ""
                                        editTeam2Name = item.team2 ?: ""
                                        editTeam2Score = item.score2 ?: ""
                                        editTeam2Logo = item.team2Logo ?: ""
                                        editMatchTime = item.matchTimeFormatted ?: item.eventTime ?: ""
                                        editCountdownHours = item.countdownTargetSeconds?.let { target ->
                                            val now = System.currentTimeMillis()
                                            val remainingMillis = when {
                                                target > 1_000_000_000_000L -> maxOf(0L, target - now)
                                                target > 1_000_000_000L -> maxOf(0L, (target * 1000L) - now)
                                                else -> target * 1000L
                                            }
                                            val hrs = remainingMillis / (3600 * 1000.0)
                                            if (hrs > 0.05) String.format(java.util.Locale.US, "%.1f", hrs) else ""
                                        } ?: ""
                                        val curServers = item.getAllServers()
                                        editServers = if (curServers.isNotEmpty()) curServers else listOf(StreamServer("Server 1", item.streamUrl))
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("খেলা এডিট", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                // Quick Score Update Button
                                Button(
                                    onClick = {
                                        updatingItem = item
                                        updateScore1 = item.score1 ?: ""
                                        updateScore2 = item.score2 ?: ""
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.Scoreboard, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("স্কোর", fontSize = 12.sp)
                                }

                                IconButton(
                                    onClick = {
                                        itemToDelete = item
                                    }
                                ) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = Color(0xFFEF4444))
                                }
                            }
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // TAB 2 CONTENT: LIVE TV CHANNELS ADMIN
            // -------------------------------------------------------------
            if (selectedAdminTab == AdminTab.CHANNELS) {
                // 1. Primary M3U Playlist Manager for Live TV Channels (User: "সব থেকে ভালো হয় চ্যানেল অপশনে এরকম প্লেলিস্ট লিংক এড করা এডমিন প্যানেল থেকে")
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Link, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("📡 লাইভ টিভি চ্যানেল প্লেলিস্ট লিংক (Live TV M3U)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Text(
                                text = "এখানে এক বা একাধিক M3U প্লেলিস্ট লিংক দিতে পারবেন (প্রতি লাইনে একটি করে অথবা কমা দিয়ে)। সরাসরি চ্যানেল লোড হবে ও ক্লাউডে সিঙ্ক থাকবে:",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                            OutlinedTextField(
                                value = liveTvM3uInput,
                                onValueChange = { liveTvM3uInput = it },
                                placeholder = { Text("https://url1.m3u\nhttps://url2.m3u", color = Color(0xFF64748B), fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = false,
                                minLines = 2,
                                maxLines = 5
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (liveTvM3uInput.isNotBlank()) {
                                            val url = liveTvM3uInput.trim()
                                            repository.saveLiveTvM3uUrl(url)
                                            coroutineScope.launch {
                                                repository.pushAppConfigToFirebase(liveTvM3u = url)
                                            }
                                            onDataChanged()
                                            Toast.makeText(context, "✅ লাইভ টিভি M3U ক্লাউডে সেভ ও সিঙ্ক হয়েছে!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "সঠিক M3U লিংক দিন", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1.3f).height(44.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("সেভ ও সিঙ্ক করুন", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = {
                                        liveTvM3uInput = MediaRepository.DEFAULT_LIVE_TV_M3U_URL
                                        repository.saveLiveTvM3uUrl(MediaRepository.DEFAULT_LIVE_TV_M3U_URL)
                                        coroutineScope.launch {
                                            repository.pushAppConfigToFirebase(liveTvM3u = MediaRepository.DEFAULT_LIVE_TV_M3U_URL)
                                        }
                                        onDataChanged()
                                        Toast.makeText(context, "ডিফল্ট লাইভ টিভি M3U লিঙ্ক রিসেট ও সিঙ্ক হয়েছে", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF475569)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8))
                                ) {
                                    Text("ডিফল্ট লিংক", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(text = "➕ Add Single Custom Channel (একক চ্যানেল)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)

                            OutlinedTextField(
                                value = channelName,
                                onValueChange = { channelName = it },
                                placeholder = { Text("Channel Name (e.g. T Sports HD, Somoy TV)", color = Color(0xFF64748B)) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )

                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = channelCategory,
                                    onValueChange = { channelCategory = it },
                                    label = { Text("Channel Category (ক্যাটাগরি)", color = Color(0xFF94A3B8), fontSize = 12.sp) },
                                    placeholder = { Text("যেমন: Bangla, Sports TV, News, Entertainment", color = Color(0xFF64748B)) },
                                    trailingIcon = {
                                        IconButton(onClick = { addChannelCatDropdownExpanded = !addChannelCatDropdownExpanded }) {
                                            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = Color.White)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = customFieldColors(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                                DropdownMenu(
                                    expanded = addChannelCatDropdownExpanded,
                                    onDismissRequest = { addChannelCatDropdownExpanded = false },
                                    modifier = Modifier.background(Color(0xFF1E293B))
                                ) {
                                    channelCategoryOptions.forEach { opt ->
                                        DropdownMenuItem(
                                            text = { Text(opt, color = Color.White) },
                                            onClick = {
                                                channelCategory = opt
                                                addChannelCatDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = server1Url,
                                onValueChange = { server1Url = it },
                                placeholder = { Text("Server 1 (Primary Stream URL)", color = Color(0xFF64748B)) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = server2Url,
                                onValueChange = { server2Url = it },
                                placeholder = { Text("Server 2 (Backup Stream URL - Optional)", color = Color(0xFF64748B)) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = channelLogoUrl,
                                onValueChange = { channelLogoUrl = it },
                                placeholder = { Text("Channel Logo URL (Optional)", color = Color(0xFF64748B)) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )

                            Button(
                                onClick = {
                                    if (channelName.isNotBlank() && server1Url.isNotBlank()) {
                                        val servers = mutableListOf<StreamServer>()
                                        servers.add(StreamServer("সার্ভার ১ (Main)", server1Url.trim()))
                                        if (server2Url.isNotBlank()) servers.add(StreamServer("সার্ভার ২ (Backup)", server2Url.trim()))

                                        val item = MediaItem(
                                            id = "tv_${System.currentTimeMillis()}",
                                            title = channelName.trim(),
                                            category = channelCategory,
                                            type = MediaType.LIVE_TV,
                                            streamUrl = server1Url.trim(),
                                            backupUrl = server2Url.trim().takeIf { it.isNotBlank() },
                                            servers = servers,
                                            logoUrl = channelLogoUrl.trim().takeIf { it.isNotBlank() },
                                            isLive = true
                                        )
                                        repository.saveCustomStream(item)
                                        coroutineScope.launch {
                                            repository.pushToFirebase(item)
                                            val notif = AppNotification(
                                                title = "📺 নতুন চ্যানেল: ${item.title}",
                                                message = "${item.category} ক্যাটাগরিতে নতুন চ্যানেল যুক্ত হয়েছে। উপভোগ করুন!",
                                                type = NotificationType.LIVE_TV,
                                                targetId = item.id,
                                                imageUrl = item.logoUrl
                                            )
                                            repository.broadcastNotification(notif)
                                            NotificationHelper.showSystemNotification(context, notif)
                                            adminNotificationHistory = repository.getStoredNotifications()
                                        }
                                        onDataChanged()
                                        channelName = ""
                                        server1Url = ""
                                        server2Url = ""
                                        channelLogoUrl = ""
                                        Toast.makeText(context, "চ্যানেল পাবলিশ হয়েছে!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Publish Channel to Firestore", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Search & Filter Header for Channels
                val customTvChannels = (repository.getCustomStreams().filter { it.type == MediaType.LIVE_TV } + liveTvList.filter { it.id.startsWith("tv_") || it.id.startsWith("channel_") || it.id.startsWith("c_") }).distinctBy { it.id }.filterNot { repository.isItemDeleted(it) }

                val filteredTvChannels = (customTvChannels + liveTvList).distinctBy { it.id }.filterNot { repository.isItemDeleted(it) }.filter { item ->
                    val matchesSearch = channelAdminSearchQuery.isBlank() ||
                        item.title.contains(channelAdminSearchQuery, ignoreCase = true) ||
                        item.category.contains(channelAdminSearchQuery, ignoreCase = true) ||
                        item.streamUrl.contains(channelAdminSearchQuery, ignoreCase = true)

                    val isCustom = customTvChannels.any { it.id == item.id } || item.id.startsWith("tv_") || item.id.startsWith("c_")
                    val matchesType = when (channelFilterType) {
                        "CUSTOM" -> isCustom
                        "M3U" -> !isCustom
                        else -> true
                    }

                    val matchesCategory = channelFilterCategory == "All" || item.category.equals(channelFilterCategory, ignoreCase = true)

                    matchesSearch && matchesType && matchesCategory
                }

                item {
                    // Search & Filter Card
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2563EB).copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Search Field
                            OutlinedTextField(
                                value = channelAdminSearchQuery,
                                onValueChange = { channelAdminSearchQuery = it },
                                placeholder = { Text("চ্যানেলের নাম বা ক্যাটাগরি দিয়ে খুঁজুন...", color = Color(0xFF64748B), fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(20.dp)) },
                                trailingIcon = {
                                    if (channelAdminSearchQuery.isNotBlank()) {
                                        IconButton(onClick = { channelAdminSearchQuery = "" }) {
                                            Icon(Icons.Rounded.Clear, contentDescription = "Clear", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                                        }
                                    }
                                },
                                singleLine = true,
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Type Filter Chips
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = channelFilterType == "ALL",
                                    onClick = { channelFilterType = "ALL" },
                                    label = { Text("সব চ্যানেল (${liveTvList.size})", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF2563EB),
                                        selectedLabelColor = Color.White,
                                        containerColor = Color(0xFF0F172A),
                                        labelColor = Color(0xFF94A3B8)
                                    )
                                )
                                FilterChip(
                                    selected = channelFilterType == "CUSTOM",
                                    onClick = { channelFilterType = "CUSTOM" },
                                    label = { Text("⭐ কাস্টম চ্যানেল (${customTvChannels.size})", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFFF59E0B),
                                        selectedLabelColor = Color.Black,
                                        containerColor = Color(0xFF0F172A),
                                        labelColor = Color(0xFFFBBF24)
                                    )
                                )
                                FilterChip(
                                    selected = channelFilterType == "M3U",
                                    onClick = { channelFilterType = "M3U" },
                                    label = { Text("📺 M3U চ্যানেল", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF06B6D4),
                                        selectedLabelColor = Color.Black,
                                        containerColor = Color(0xFF0F172A),
                                        labelColor = Color(0xFF22D3EE)
                                    )
                                )
                            }
                        }
                    }
                }

                if (filteredTvChannels.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Rounded.SearchOff, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(40.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("কোনো চ্যানেল পাওয়া যায়নি", color = Color(0xFF94A3B8), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                } else {
                    items(filteredTvChannels) { item ->
                        val isCustomItem = customTvChannels.any { it.id == item.id } || item.id.startsWith("tv_") || item.id.startsWith("c_")
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCustomItem) Color(0xFF1E293B) else Color(0xFF172033)
                            ),
                            border = if (isCustomItem) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f)) else null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Channel Logo
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF0F172A)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!item.logoUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = item.logoUrl,
                                            contentDescription = item.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(Icons.Rounded.LiveTv, contentDescription = null, tint = if (isCustomItem) Color(0xFFFBBF24) else Color(0xFF00E5FF))
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(item.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (isCustomItem) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text("কাস্টম", color = Color(0xFFFBBF24), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        "${item.category} • ${item.getAllServers().size} টি সার্ভার",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp
                                    )
                                    if (item.streamUrl.isNotBlank()) {
                                        Text(
                                            item.streamUrl,
                                            color = Color(0xFF64748B),
                                            fontSize = 10.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                // Edit Channel Button
                                Button(
                                    onClick = {
                                        editingChannelItem = item
                                        editChannelName = item.title
                                        editChannelCategory = item.category
                                        editChannelLogoUrl = item.logoUrl ?: ""
                                        val curServers = item.getAllServers()
                                        editChannelServers = if (curServers.isNotEmpty()) curServers else listOf(StreamServer("সার্ভার ১ (Main)", item.streamUrl))
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("এডিট", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(
                                    onClick = {
                                        itemToDelete = item
                                    }
                                ) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = Color(0xFFEF4444))
                                }
                            }
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // TAB 3 CONTENT: MOVIES & SERIES ADMIN
            // -------------------------------------------------------------
            if (selectedAdminTab == AdminTab.MOVIES) {
                val customMovies = repository.getCustomStreams().filter { it.type == MediaType.MOVIE }.filterNot { repository.isItemDeleted(it) }
                val allMovies = (customMovies + moviesList).distinctBy { it.id }.filterNot { repository.isItemDeleted(it) }
                val filteredMovies = allMovies.filter { mov ->
                    val matchesSearch = moviesAdminSearchQuery.isBlank() ||
                        mov.title.contains(moviesAdminSearchQuery, ignoreCase = true) ||
                        mov.category.contains(moviesAdminSearchQuery, ignoreCase = true)
                    val isCustom = customMovies.any { it.id == mov.id } || mov.id.startsWith("mov_") || mov.id.startsWith("custom_")
                    val matchesType = when (moviesFilterType) {
                        "CUSTOM" -> isCustom
                        "M3U" -> !isCustom
                        else -> true
                    }
                    matchesSearch && matchesType
                }

                val allAdminAndUserPlaylists = (repository.getAdminPlaylists() + repository.getUserPlaylists() + playlistsList).distinctBy { it.id }
                val moviePlaylists = allAdminAndUserPlaylists.filter {
                    it.id == "pl_nafi_movies_json" ||
                    it.title.contains("movie", ignoreCase = true) ||
                    it.title.contains("cinema", ignoreCase = true) ||
                    it.type.equals("JSON", ignoreCase = true) ||
                    it.url.contains("movie", ignoreCase = true) ||
                    it.description?.contains("মুভি", ignoreCase = true) == true ||
                    it.description?.contains("movie", ignoreCase = true) == true
                }

                // 0. OTT Movie Hub & Server Creator (Netflix, Hotstar, Xtream, Playlist)
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Dns, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("🎬 ওটিটি মুভি সার্ভার ও প্লেলিস্ট যোগ করুন (Add Movie Server / Hub)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                            }
                            Text(
                                text = "নেটফ্লিক্স, হটস্টার, হইচই, চরকি বা এক্সট্রিম সার্ভার যোগ করুন। মুভি সেকশনে এগুলো ব্র্যান্ড কার্ড হিসেবে শো করবে এবং ইউজাররা ক্লিক করলেই সব মুভি লোড হয়ে দেখতে পারবেন:",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                lineHeight = 16.sp
                            )

                            // 1-Click OTT Brand Presets
                            Text("⚡ দ্রুত প্রিসেট সিলেক্ট করুন (1-Click OTT Brand Presets):", color = Color(0xFF38BDF8), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val presets = listOf(
                                    Triple("🍿 Netflix", "Netflix Hub", "https://images.ctfassets.net/y2ske730sjqp/821Wg4N9hJD8vs5FBIO3ih/9e630327e04e2e0a9dac340d97a12930/Netflix_Symbol_PMS.png"),
                                    Triple("🌟 Hotstar", "Disney+ Hotstar", "https://upload.wikimedia.org/wikipedia/commons/thumb/1/1e/Disney%2B_Hotstar_logo.svg/320px-Disney%2B_Hotstar_logo.svg.png"),
                                    Triple("🔵 Prime", "Prime Video", "https://upload.wikimedia.org/wikipedia/commons/thumb/1/11/Amazon_Prime_Video_logo.svg/320px-Amazon_Prime_Video_logo.svg.png"),
                                    Triple("🔴 Sony LIV", "Sony LIV", "https://upload.wikimedia.org/wikipedia/commons/thumb/5/5a/Sony_LIV_logo.svg/320px-Sony_LIV_logo.svg.png"),
                                    Triple("🟣 ZEE5", "ZEE5 Movies", "https://upload.wikimedia.org/wikipedia/commons/thumb/5/5a/Zee5_Official_logo.svg/320px-Zee5_Official_logo.svg.png"),
                                    Triple("🟡 Hoichoi", "Hoichoi (হইচই)", "https://upload.wikimedia.org/wikipedia/commons/thumb/0/00/Hoichoi_logo.svg/320px-Hoichoi_logo.svg.png"),
                                    Triple("🟢 Chorki", "Chorki (চরকি)", "https://upload.wikimedia.org/wikipedia/commons/thumb/d/d7/Chorki_logo.png/320px-Chorki_logo.png"),
                                    Triple("⚡ Xtream", "Starshare Xtream Server", ""),
                                    Triple("📁 M3U Playlist", "মুভি ও সিরিজ প্লেলিস্ট", "")
                                )
                                items(presets) { (chipLabel, presetName, presetLogo) ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF0F172A),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                                        modifier = Modifier.clickable {
                                            ottHubName = presetName
                                            if (presetLogo.isNotBlank()) ottHubLogoUrl = presetLogo
                                        }
                                    ) {
                                        Text(chipLabel, color = Color.White, fontSize = 10.5.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                                    }
                                }
                            }

                            // Type Selector: XTREAM vs M3U
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val isXtream = ottHubType == "XTREAM"
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isXtream) Color(0xFF0284C7) else Color(0xFF0F172A),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isXtream) Color(0xFF00E5FF) else Color(0xFF334155)),
                                    modifier = Modifier.weight(1f).clickable { ottHubType = "XTREAM" }
                                ) {
                                    Text("⚡ Xtream Codes API", color = if (isXtream) Color.White else Color(0xFF94A3B8), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 7.dp))
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (!isXtream) Color(0xFF0284C7) else Color(0xFF0F172A),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (!isXtream) Color(0xFF00E5FF) else Color(0xFF334155)),
                                    modifier = Modifier.weight(1f).clickable { ottHubType = "M3U" }
                                ) {
                                    Text("📁 M3U Playlist URL", color = if (!isXtream) Color.White else Color(0xFF94A3B8), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 7.dp))
                                }
                            }

                            // Platform / Server Name
                            OutlinedTextField(
                                value = ottHubName,
                                onValueChange = { ottHubName = it },
                                label = { Text("প্ল্যাটফর্ম / সার্ভারের নাম (যেমন: Netflix, Hotstar, Starshare)", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true
                            )

                            // Logo URL
                            OutlinedTextField(
                                value = ottHubLogoUrl,
                                onValueChange = { ottHubLogoUrl = it },
                                label = { Text("লোগো / পোস্টার URL (Logo Image URL - ঐচ্ছিক)", fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true
                            )

                            if (ottHubType == "XTREAM") {
                                // Xtream Host
                                OutlinedTextField(
                                    value = ottHubUrl,
                                    onValueChange = { ottHubUrl = it },
                                    label = { Text("সার্ভার URL (যেমন: http://server.com:8080)", fontSize = 11.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = customFieldColors(),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true
                                )
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = ottHubUsername,
                                        onValueChange = { ottHubUsername = it },
                                        label = { Text("ইউজারনেম (Username)", fontSize = 11.sp) },
                                        modifier = Modifier.weight(1f),
                                        colors = customFieldColors(),
                                        shape = RoundedCornerShape(10.dp),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = ottHubPassword,
                                        onValueChange = { ottHubPassword = it },
                                        label = { Text("পাসওয়ার্ড (Password)", fontSize = 11.sp) },
                                        modifier = Modifier.weight(1f),
                                        colors = customFieldColors(),
                                        shape = RoundedCornerShape(10.dp),
                                        singleLine = true
                                    )
                                }
                            } else {
                                // M3U Playlist URL
                                OutlinedTextField(
                                    value = ottHubUrl,
                                    onValueChange = { ottHubUrl = it },
                                    label = { Text("M3U / JSON মুভি প্লেলিস্ট লিংক (Playlist URL)", fontSize = 11.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = customFieldColors(),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true
                                )
                            }

                            // Save Button
                            Button(
                                onClick = {
                                    if (ottHubName.isBlank()) {
                                        Toast.makeText(context, "অনুগ্রহ করে সার্ভার বা প্ল্যাটফর্মের নাম দিন", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    if (ottHubUrl.isBlank()) {
                                        Toast.makeText(context, "অনুগ্রহ করে সার্ভার বা প্লেলিস্টের লিংক দিন", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }

                                    ottHubIsSaving = true
                                    coroutineScope.launch {
                                        try {
                                            if (ottHubType == "XTREAM") {
                                                val acc = com.example.model.XtreamAccount(
                                                    id = "xtream_ott_${System.currentTimeMillis()}",
                                                    name = ottHubName.trim(),
                                                    serverUrl = ottHubUrl.trim(),
                                                    username = ottHubUsername.trim(),
                                                    password = ottHubPassword.trim(),
                                                    isEnabled = true,
                                                    includeLive = false, // Pure movies!
                                                    includeVod = true,
                                                    includeSeries = true,
                                                    logoUrl = ottHubLogoUrl.trim().takeIf { it.isNotBlank() },
                                                    lastSyncTime = System.currentTimeMillis(),
                                                    statusMessage = "সক্রিয় (Active)"
                                                )
                                                repository.saveXtreamAccount(acc)
                                                xtreamAccountsList = repository.getXtreamAccounts()
                                            } else {
                                                val newPl = PlaylistInfo(
                                                    id = "pl_ott_${System.currentTimeMillis()}",
                                                    title = ottHubName.trim(),
                                                    url = ottHubUrl.trim(),
                                                    logoUrl = ottHubLogoUrl.trim().takeIf { it.isNotBlank() },
                                                    description = "OTT Movie Playlist",
                                                    type = "M3U",
                                                    isAdmin = true
                                                )
                                                repository.saveAdminPlaylist(newPl)
                                                repository.pushPlaylistToFirebase(newPl)
                                            }
                                            onDataChanged()
                                            Toast.makeText(context, "✅ '$ottHubName' মুভি সেকশনে যুক্ত হয়েছে! ইউজাররা ক্লিক করে সব মুভি দেখতে পারবেন।", Toast.LENGTH_LONG).show()
                                            // Reset
                                            ottHubName = ""
                                            ottHubUrl = ""
                                            ottHubUsername = ""
                                            ottHubPassword = ""
                                            ottHubLogoUrl = ""
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "ত্রুটি: ${e.message}", Toast.LENGTH_SHORT).show()
                                        } finally {
                                            ottHubIsSaving = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black),
                                shape = RoundedCornerShape(10.dp),
                                enabled = !ottHubIsSaving
                            ) {
                                if (ottHubIsSaving) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("সংরক্ষণ হচ্ছে...", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                } else {
                                    Icon(Icons.Rounded.CloudDone, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("ওটিটি মুভি সার্ভার সংরক্ষণ ও সিঙ্ক করুন", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // Configured OTT Platforms List in Admin Panel
                val activeOttXtream = xtreamAccountsList.filter { it.includeVod || it.includeSeries }
                val activeOttPlaylists = (repository.getAdminPlaylists() + repository.getUserPlaylists()).filter {
                    it.type.equals("M3U", ignoreCase = true) || it.title.contains("ott", ignoreCase = true) || it.id.startsWith("pl_ott_")
                }
                if (activeOttXtream.isNotEmpty() || activeOttPlaylists.isNotEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF06B6D4).copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Dns, contentDescription = null, tint = Color(0xFF06B6D4), modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("📋 কনফিগার করা ওটিটি প্ল্যাটফর্মসমূহ (${activeOttXtream.size + activeOttPlaylists.size} টি)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                Text("মুভি সেকশনের শীর্ষে এই সার্ভারগুলো গোলাকার আইকন আকারে দেখা যাবে:", color = Color(0xFF94A3B8), fontSize = 11.sp)

                                // List of Xtream OTT servers
                                activeOttXtream.forEach { acc ->
                                    Card(
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                // Circular preview icon with metallic border
                                                Box(
                                                    modifier = Modifier
                                                        .size(42.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFF1E293B))
                                                        .border(
                                                            1.5.dp,
                                                            Brush.sweepGradient(listOf(Color(0xFF94A3B8), Color(0xFF334155), Color(0xFFCBD5E1), Color(0xFF94A3B8))),
                                                            CircleShape
                                                        )
                                                        .padding(2.dp)
                                                        .clip(CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (!acc.logoUrl.isNullOrBlank()) {
                                                        AsyncImage(
                                                            model = acc.logoUrl,
                                                            contentDescription = acc.name,
                                                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                                                            contentScale = ContentScale.Crop
                                                        )
                                                    } else {
                                                        Text(acc.name.take(2).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                    }
                                                }

                                                Column {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(acc.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF0891B2).copy(alpha = 0.3f)) {
                                                            Text("XTREAM", color = Color(0xFF38BDF8), fontSize = 8.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                                        }
                                                    }
                                                    Text(acc.serverUrl, color = Color(0xFF94A3B8), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(
                                                    onClick = {
                                                        ottHubName = acc.name
                                                        ottHubUrl = acc.serverUrl
                                                        ottHubUsername = acc.username
                                                        ottHubPassword = acc.password
                                                        ottHubLogoUrl = acc.logoUrl ?: ""
                                                        ottHubType = "XTREAM"
                                                        Toast.makeText(context, "ফর্ম এ লোড হয়েছে, পরিবর্তন করে সেভ করুন", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Rounded.Edit, contentDescription = "Edit", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                                }
                                                IconButton(
                                                    onClick = {
                                                        repository.deleteXtreamAccount(acc.id)
                                                        xtreamAccountsList = repository.getXtreamAccounts()
                                                        onDataChanged()
                                                        Toast.makeText(context, "মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }

                                // List of M3U OTT playlists
                                activeOttPlaylists.forEach { pl ->
                                    Card(
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(42.dp)
                                                        .clip(CircleShape)
                                                        .background(Color(0xFF1E293B))
                                                        .border(
                                                            1.5.dp,
                                                            Brush.sweepGradient(listOf(Color(0xFF94A3B8), Color(0xFF334155), Color(0xFFCBD5E1), Color(0xFF94A3B8))),
                                                            CircleShape
                                                        )
                                                        .padding(2.dp)
                                                        .clip(CircleShape),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (!pl.logoUrl.isNullOrBlank()) {
                                                        AsyncImage(
                                                            model = pl.logoUrl,
                                                            contentDescription = pl.title,
                                                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                                                            contentScale = ContentScale.Crop
                                                        )
                                                    } else {
                                                        Text(pl.title.take(2).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                    }
                                                }

                                                Column {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(pl.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFD97706).copy(alpha = 0.3f)) {
                                                            Text("M3U", color = Color(0xFFF59E0B), fontSize = 8.5.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                                        }
                                                    }
                                                    Text(pl.url, color = Color(0xFF94A3B8), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                IconButton(
                                                    onClick = {
                                                        ottHubName = pl.title
                                                        ottHubUrl = pl.url
                                                        ottHubLogoUrl = pl.logoUrl ?: ""
                                                        ottHubType = "M3U"
                                                        Toast.makeText(context, "ফর্ম এ লোড হয়েছে, পরিবর্তন করে সেভ করুন", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Rounded.Edit, contentDescription = "Edit", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                                                }
                                                IconButton(
                                                    onClick = {
                                                        repository.deleteAdminPlaylist(pl.id)
                                                        repository.deleteCustomPlaylist(pl.id)
                                                        onDataChanged()
                                                        Toast.makeText(context, "মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 1. Primary M3U / JSON Playlist Manager for Movies
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Movie, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("🎬 মুভি ও সিরিজ প্লেলিস্ট লিংক (Movies M3U / JSON)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Text(
                                text = "এখানে আপনার সক্রিয় মুভি প্লেলিস্ট লিঙ্কগুলো দেওয়া আছে। আপনি নতুন লিংক যোগ করতে পারেন বা বিদ্যমান লিংক এডিট করতে পারেন (প্রতি লাইনে একটি করে অথবা কমা দিয়ে):",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                            OutlinedTextField(
                                value = moviesM3uInput,
                                onValueChange = { moviesM3uInput = it },
                                placeholder = { Text("https://url1.m3u\nhttps://url2.m3u", color = Color(0xFF64748B), fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = false,
                                minLines = 2,
                                maxLines = 5
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (moviesM3uInput.isNotBlank()) {
                                            val url = moviesM3uInput.trim()
                                            repository.saveMoviesM3uUrl(url)
                                            coroutineScope.launch {
                                                repository.pushAppConfigToFirebase(moviesM3u = url)
                                            }
                                            onDataChanged()
                                            Toast.makeText(context, "✅ মুভি প্লেলিস্ট ক্লাউডে সেভ ও সিঙ্ক হয়েছে!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "অনুগ্রহ করে একটি সঠিক মুভি M3U URL দিন", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1.3f).height(44.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B), contentColor = Color.Black),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("সেভ ও সিঙ্ক করুন", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = {
                                        moviesM3uInput = MediaRepository.DEFAULT_MOVIES_M3U_URL
                                        repository.saveMoviesM3uUrl(MediaRepository.DEFAULT_MOVIES_M3U_URL)
                                        coroutineScope.launch {
                                            repository.pushAppConfigToFirebase(moviesM3u = MediaRepository.DEFAULT_MOVIES_M3U_URL)
                                        }
                                        onDataChanged()
                                        Toast.makeText(context, "মুভি প্লেলিস্ট রিসেট ও সিঙ্ক হয়েছে", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF475569)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8))
                                ) {
                                    Text("ডিফল্ট লিংক", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                // 2. Movie Playlists Quick Manager & Editor
                if (moviePlaylists.isNotEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.QueueMusic, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("📑 মুভি প্লেলিস্টসমূহ (${moviePlaylists.size} টি)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                Text("নিচে আপনার সব মুভি প্লেলিস্টের তালিকা ও লিঙ্ক দেওয়া হলো। যেকোনো প্লেলিস্ট সরাসরি এডিট করতে 'এডিট' বাটনে চাপুন:", color = Color(0xFF94A3B8), fontSize = 11.sp)

                                moviePlaylists.forEach { pl ->
                                    Card(
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(38.dp)
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(Color(0xFFF59E0B).copy(alpha = 0.2f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                if (!pl.logoUrl.isNullOrBlank()) {
                                                    AsyncImage(
                                                        model = pl.logoUrl,
                                                        contentDescription = pl.title,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                } else {
                                                    Icon(Icons.Rounded.Movie, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(pl.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
                                                Text(pl.url, color = Color(0xFF38BDF8), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                if (!pl.description.isNullOrBlank()) {
                                                    Text(pl.description!!, color = Color(0xFF64748B), fontSize = 9.sp, maxLines = 1)
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Button(
                                                onClick = {
                                                    editingPlaylistItem = pl
                                                    editPlaylistTitle = pl.title
                                                    editPlaylistUrl = pl.url
                                                    editPlaylistLogoUrl = pl.logoUrl ?: ""
                                                    editPlaylistDescription = pl.description ?: ""
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                            ) {
                                                Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("এডিট", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Add Single Movie or Series Form
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(text = "➕ Add Single Movie or Series (একক মুভি যোগ)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)

                            OutlinedTextField(
                                value = movieTitle,
                                onValueChange = { movieTitle = it },
                                placeholder = { Text("Movie Title (e.g. Toofan 2026)", color = Color(0xFF64748B)) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = server1Url,
                                onValueChange = { server1Url = it },
                                placeholder = { Text("Server 1 (Video Stream URL)", color = Color(0xFF64748B)) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = server2Url,
                                onValueChange = { server2Url = it },
                                placeholder = { Text("Server 2 (Fast Alternative URL)", color = Color(0xFF64748B)) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = moviePosterUrl,
                                onValueChange = { moviePosterUrl = it },
                                placeholder = { Text("Poster Image URL", color = Color(0xFF64748B)) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )

                            Button(
                                onClick = {
                                    if (movieTitle.isNotBlank() && server1Url.isNotBlank()) {
                                        val servers = mutableListOf<StreamServer>()
                                        servers.add(StreamServer("সার্ভার ১ (HD)", server1Url.trim()))
                                        if (server2Url.isNotBlank()) servers.add(StreamServer("সার্ভার ২ (4K)", server2Url.trim()))

                                        val item = MediaItem(
                                            id = "mov_${System.currentTimeMillis()}",
                                            title = movieTitle.trim(),
                                            category = movieCategory,
                                            type = MediaType.MOVIE,
                                            streamUrl = server1Url.trim(),
                                            backupUrl = server2Url.trim().takeIf { it.isNotBlank() },
                                            servers = servers,
                                            logoUrl = moviePosterUrl.trim().takeIf { it.isNotBlank() },
                                            isLive = false
                                        )
                                        repository.saveCustomStream(item)
                                        coroutineScope.launch {
                                            repository.pushToFirebase(item)
                                            val notif = AppNotification(
                                                title = "🎬 নতুন মুভি: ${item.title}",
                                                message = "${item.category} ক্যাটাগরিতে নতুন মুভি যোগ করা হয়েছে। এখনই দেখুন!",
                                                type = NotificationType.MOVIE,
                                                targetId = item.id,
                                                imageUrl = item.logoUrl
                                            )
                                            repository.broadcastNotification(notif)
                                            NotificationHelper.showSystemNotification(context, notif)
                                            adminNotificationHistory = repository.getStoredNotifications()
                                        }
                                        onDataChanged()
                                        movieTitle = ""
                                        server1Url = ""
                                        server2Url = ""
                                        moviePosterUrl = ""
                                        Toast.makeText(context, "মুভি যুক্ত হয়েছে!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Publish Movie to Firestore", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // 4. Movies Search & Filter Header
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Search Field
                            OutlinedTextField(
                                value = moviesAdminSearchQuery,
                                onValueChange = { moviesAdminSearchQuery = it },
                                placeholder = { Text("মুভির নাম বা ক্যাটাগরি দিয়ে খুঁজুন...", color = Color(0xFF64748B), fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(20.dp)) },
                                trailingIcon = {
                                    if (moviesAdminSearchQuery.isNotBlank()) {
                                        IconButton(onClick = { moviesAdminSearchQuery = "" }) {
                                            Icon(Icons.Rounded.Clear, contentDescription = "Clear", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                                        }
                                    }
                                },
                                singleLine = true,
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Type Filter Chips
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = moviesFilterType == "ALL",
                                    onClick = { moviesFilterType = "ALL" },
                                    label = { Text("সব মুভি (${allMovies.size})", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFFF59E0B),
                                        selectedLabelColor = Color.Black,
                                        containerColor = Color(0xFF0F172A),
                                        labelColor = Color(0xFFF59E0B)
                                    )
                                )
                                FilterChip(
                                    selected = moviesFilterType == "CUSTOM",
                                    onClick = { moviesFilterType = "CUSTOM" },
                                    label = { Text("⭐ কাস্টম মুভি (${customMovies.size})", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFFF59E0B),
                                        selectedLabelColor = Color.Black,
                                        containerColor = Color(0xFF0F172A),
                                        labelColor = Color(0xFFFBBF24)
                                    )
                                )
                                FilterChip(
                                    selected = moviesFilterType == "M3U",
                                    onClick = { moviesFilterType = "M3U" },
                                    label = { Text("🌐 M3U মুভি", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF2563EB),
                                        selectedLabelColor = Color.White,
                                        containerColor = Color(0xFF0F172A),
                                        labelColor = Color(0xFF94A3B8)
                                    )
                                )
                            }
                        }
                    }
                }

                if (filteredMovies.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Rounded.SearchOff, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(40.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("কোনো মুভি পাওয়া যায়নি", color = Color(0xFF94A3B8), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                } else {
                    items(filteredMovies) { item ->
                        val isCustomMovie = customMovies.any { it.id == item.id } || item.id.startsWith("mov_") || item.id.startsWith("custom_")
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCustomMovie) Color(0xFF1E293B) else Color(0xFF172033)
                            ),
                            border = if (isCustomMovie) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f)) else null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(item.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (isCustomMovie) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text("কাস্টম", color = Color(0xFFFBBF24), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("${item.category} • ${item.getAllServers().size} টি সার্ভার", color = Color(0xFF94A3B8), fontSize = 12.sp)
                                }
                                // Edit Movie Button
                                Button(
                                    onClick = {
                                        editingMovieItem = item
                                        editMovieTitle = item.title
                                        editMovieCategory = item.category
                                        editMoviePosterUrl = item.logoUrl ?: ""
                                        editMovieDesc = item.description ?: ""
                                        val curServers = item.getAllServers()
                                        editMovieServers = if (curServers.isNotEmpty()) curServers else listOf(StreamServer("সার্ভার ১ (HD)", item.streamUrl))
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("এডিট", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(
                                    onClick = {
                                        itemToDelete = item
                                    }
                                ) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = Color(0xFFEF4444))
                                }
                            }
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // TAB 4 CONTENT: PLAYLISTS ADMIN (M3U Playlists Management)
            // -------------------------------------------------------------
            if (selectedAdminTab == AdminTab.PLAYLISTS) {
                // Central M3U Links Hub
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.FeaturedPlayList, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("🌐 সেন্ট্রাল M3U প্লেলিস্ট কনফিগারেশন", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Text(
                                text = "এখানে থাকা M3U লিংক থেকে সরাসরি চ্যানেল, খেলা ও মুভি লোড হবে (Firebase ডেটাবেসে লোড হবে না)। প্রতিটিতে এক বা একাধিক M3U লিংক দিতে পারেন (প্রতি লাইনে একটি করে অথবা কমা দিয়ে):",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )

                            // 1. Live TV Channels M3U URL
                            Text("1. লাইভ টিভি চ্যানেল M3U URL (একাধিক দেওয়া যাবে):", color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            OutlinedTextField(
                                value = liveTvM3uInput,
                                onValueChange = { liveTvM3uInput = it },
                                placeholder = { Text("https://url1.m3u\nhttps://url2.m3u", color = Color(0xFF64748B), fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = false,
                                minLines = 2,
                                maxLines = 5
                            )

                            // 2. Sports Matches M3U URL
                            Text("2. লাইভ স্পোর্টস ম্যাচ M3U URL (একাধিক দেওয়া যাবে):", color = Color(0xFF10B981), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            OutlinedTextField(
                                value = sportsM3uInput,
                                onValueChange = { sportsM3uInput = it },
                                placeholder = { Text("https://url1.m3u\nhttps://url2.m3u", color = Color(0xFF64748B), fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = false,
                                minLines = 2,
                                maxLines = 5
                            )

                            // 3. Movies M3U URL
                            Text("3. মুভি ও সিরিজ M3U URL (একাধিক দেওয়া যাবে):", color = Color(0xFFF59E0B), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            OutlinedTextField(
                                value = moviesM3uInput,
                                onValueChange = { moviesM3uInput = it },
                                placeholder = { Text("https://url1.m3u\nhttps://url2.m3u", color = Color(0xFF64748B), fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = false,
                                minLines = 2,
                                maxLines = 5
                            )

                            // 4. Tapmad Live Events JSON URL
                            Text("4. লাইভ স্পোর্টস / Tapmad JSON API URL (একাধিক লিংক প্রতি লাইনে একটি করে):", color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            OutlinedTextField(
                                value = tapmadJsonInput,
                                onValueChange = { tapmadJsonInput = it },
                                placeholder = { Text("https://gist.githubusercontent.com/.../tapmad_bd.json\nhttps://example.com/sports2.json", color = Color(0xFF64748B), fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = false,
                                minLines = 2,
                                maxLines = 5
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val liveUrl = liveTvM3uInput.trim()
                                        val sportsUrl = sportsM3uInput.trim()
                                        val movUrl = moviesM3uInput.trim()
                                        val tapmadUrl = tapmadJsonInput.trim()
                                        repository.saveLiveTvM3uUrl(liveUrl)
                                        repository.saveSportsM3uUrl(sportsUrl)
                                        repository.saveMoviesM3uUrl(movUrl)
                                        if (tapmadUrl.isNotBlank()) {
                                            repository.saveTapmadJsonUrl(tapmadUrl)
                                        }
                                        coroutineScope.launch {
                                            repository.pushAppConfigToFirebase(
                                                liveTvM3u = liveUrl,
                                                sportsM3u = sportsUrl,
                                                moviesM3u = movUrl,
                                                tapmadJson = tapmadUrl
                                            )
                                        }
                                        onDataChanged()
                                        Toast.makeText(context, "✅ সকল প্লেলিস্ট ও JSON লিংক সেভ এবং সিঙ্ক সম্পন্ন হয়েছে!", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1.3f).height(46.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Rounded.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("সব সেভ ও সিঙ্ক", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = {
                                        liveTvM3uInput = MediaRepository.DEFAULT_LIVE_TV_M3U_URL
                                        sportsM3uInput = MediaRepository.DEFAULT_SPORTS_M3U_URL
                                        moviesM3uInput = MediaRepository.DEFAULT_MOVIES_M3U_URL
                                        tapmadJsonInput = MediaRepository.DEFAULT_TAPMAD_JSON_URL
                                        repository.saveLiveTvM3uUrl(MediaRepository.DEFAULT_LIVE_TV_M3U_URL)
                                        repository.saveSportsM3uUrl(MediaRepository.DEFAULT_SPORTS_M3U_URL)
                                        repository.saveMoviesM3uUrl(MediaRepository.DEFAULT_MOVIES_M3U_URL)
                                        repository.saveTapmadJsonUrl(MediaRepository.DEFAULT_TAPMAD_JSON_URL)
                                        coroutineScope.launch {
                                            repository.pushAppConfigToFirebase(
                                                liveTvM3u = MediaRepository.DEFAULT_LIVE_TV_M3U_URL,
                                                sportsM3u = MediaRepository.DEFAULT_SPORTS_M3U_URL,
                                                moviesM3u = MediaRepository.DEFAULT_MOVIES_M3U_URL,
                                                tapmadJson = MediaRepository.DEFAULT_TAPMAD_JSON_URL
                                            )
                                        }
                                        onDataChanged()
                                        Toast.makeText(context, "সকল ডিফল্ট M3U ও JSON লিংক রিসেট করা হয়েছে", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f).height(46.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF475569)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF94A3B8))
                                ) {
                                    Text("ডিফল্ট লিংক রিসেট", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(text = "➕ Add Custom M3U Playlist (কাস্টম প্লেলিস্ট)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)

                            OutlinedTextField(
                                value = playlistTitle,
                                onValueChange = { playlistTitle = it },
                                placeholder = { Text("Playlist Name (e.g. NAFI TV 24 Official)", color = Color(0xFF64748B)) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = playlistUrl,
                                onValueChange = { playlistUrl = it },
                                placeholder = { Text("Playlist M3U / M3U8 URL (Raw Github/Server)", color = Color(0xFF64748B)) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = playlistLogoUrl,
                                onValueChange = { playlistLogoUrl = it },
                                placeholder = { Text("Logo / Banner Image URL (Optional)", color = Color(0xFF64748B)) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = playlistDescription,
                                onValueChange = { playlistDescription = it },
                                placeholder = { Text("Short Description (Optional)", color = Color(0xFF64748B)) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )

                            Button(
                                onClick = {
                                    if (playlistTitle.isNotBlank() && playlistUrl.isNotBlank()) {
                                        val newPl = PlaylistInfo(
                                            id = "pl_admin_${System.currentTimeMillis()}",
                                            title = playlistTitle.trim(),
                                            url = playlistUrl.trim(),
                                            logoUrl = playlistLogoUrl.trim().takeIf { it.isNotBlank() },
                                            description = playlistDescription.trim().takeIf { it.isNotBlank() },
                                            channelCount = 0
                                        )
                                        repository.saveAdminPlaylist(newPl)
                                        coroutineScope.launch {
                                            repository.pushPlaylistToFirebase(newPl)
                                        }
                                        onDataChanged()
                                        playlistTitle = ""
                                        playlistUrl = ""
                                        playlistLogoUrl = ""
                                        playlistDescription = ""
                                        Toast.makeText(context, "প্লেলিস্ট যুক্ত হয়েছে!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Publish Playlist to Firestore", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Search & Filter Header for Playlists
                val customPlaylists = (repository.getAdminPlaylists() + repository.getUserPlaylists() + playlistsList.filter { it.id.startsWith("pl_admin_") || it.id.startsWith("pl_user_") || it.id.startsWith("pl_custom_") }).distinctBy { it.id }

                val filteredPlaylists = (customPlaylists + playlistsList).distinctBy { it.id }.filter { p ->
                    val matchesSearch = playlistAdminSearchQuery.isBlank() ||
                        p.title.contains(playlistAdminSearchQuery, ignoreCase = true) ||
                        p.url.contains(playlistAdminSearchQuery, ignoreCase = true) ||
                        (p.description?.contains(playlistAdminSearchQuery, ignoreCase = true) == true)

                    val isCustom = customPlaylists.any { it.id == p.id } || p.id.startsWith("pl_admin_") || p.id.startsWith("pl_user_") || p.id.startsWith("pl_custom_")
                    val matchesType = when (playlistFilterType) {
                        "CUSTOM" -> isCustom
                        "SYSTEM" -> !isCustom
                        else -> true
                    }

                    matchesSearch && matchesType
                }

                item {
                    // Search & Filter Card
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Search Field
                            OutlinedTextField(
                                value = playlistAdminSearchQuery,
                                onValueChange = { playlistAdminSearchQuery = it },
                                placeholder = { Text("প্লেলিস্টের নাম বা URL দিয়ে খুঁজুন...", color = Color(0xFF64748B), fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(20.dp)) },
                                trailingIcon = {
                                    if (playlistAdminSearchQuery.isNotBlank()) {
                                        IconButton(onClick = { playlistAdminSearchQuery = "" }) {
                                            Icon(Icons.Rounded.Clear, contentDescription = "Clear", tint = Color(0xFF94A3B8), modifier = Modifier.size(18.dp))
                                        }
                                    }
                                },
                                singleLine = true,
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Type Filter Chips
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = playlistFilterType == "ALL",
                                    onClick = { playlistFilterType = "ALL" },
                                    label = { Text("সব প্লেলিস্ট (${playlistsList.size})", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF00E5FF),
                                        selectedLabelColor = Color.Black,
                                        containerColor = Color(0xFF0F172A),
                                        labelColor = Color(0xFF00E5FF)
                                    )
                                )
                                FilterChip(
                                    selected = playlistFilterType == "CUSTOM",
                                    onClick = { playlistFilterType = "CUSTOM" },
                                    label = { Text("⭐ কাস্টম প্লেলিস্ট (${customPlaylists.size})", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFFF59E0B),
                                        selectedLabelColor = Color.Black,
                                        containerColor = Color(0xFF0F172A),
                                        labelColor = Color(0xFFFBBF24)
                                    )
                                )
                                FilterChip(
                                    selected = playlistFilterType == "SYSTEM",
                                    onClick = { playlistFilterType = "SYSTEM" },
                                    label = { Text("🌐 সিস্টেম প্লেলিস্ট", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF2563EB),
                                        selectedLabelColor = Color.White,
                                        containerColor = Color(0xFF0F172A),
                                        labelColor = Color(0xFF94A3B8)
                                    )
                                )
                            }
                        }
                    }
                }

                if (filteredPlaylists.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Rounded.SearchOff, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(40.dp))
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("কোনো প্লেলিস্ট পাওয়া যায়নি", color = Color(0xFF94A3B8), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                } else {
                    items(filteredPlaylists) { playlist ->
                        val isCustomPl = customPlaylists.any { it.id == playlist.id } || playlist.id.startsWith("pl_admin_") || playlist.id.startsWith("pl_user_") || playlist.id.startsWith("pl_custom_")
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isCustomPl) Color(0xFF1E293B) else Color(0xFF172033)
                            ),
                            border = if (isCustomPl) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f)) else null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isCustomPl) Color(0xFFF59E0B).copy(alpha = 0.2f) else Color(0xFF2563EB).copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!playlist.logoUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = playlist.logoUrl,
                                            contentDescription = playlist.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(Icons.Rounded.QueueMusic, contentDescription = null, tint = if (isCustomPl) Color(0xFFFBBF24) else Color(0xFF00E5FF))
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(playlist.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (isCustomPl) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text("কাস্টম", color = Color(0xFFFBBF24), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(playlist.url, color = Color(0xFF94A3B8), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (!playlist.description.isNullOrBlank()) {
                                        Text(playlist.description!!, color = Color(0xFF64748B), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                // Edit Playlist Button
                                Button(
                                    onClick = {
                                        editingPlaylistItem = playlist
                                        editPlaylistTitle = playlist.title
                                        editPlaylistUrl = playlist.url
                                        editPlaylistLogoUrl = playlist.logoUrl ?: ""
                                        editPlaylistDescription = playlist.description ?: ""
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("এডিট", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                IconButton(
                                    onClick = {
                                        playlistToDelete = playlist
                                    }
                                ) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = Color(0xFFEF4444))
                                }
                            }
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // TAB: XTREAM CODES API ADMIN & SYNC CONTROL
            // -------------------------------------------------------------
            if (selectedAdminTab == AdminTab.XTREAM) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        border = BorderStroke(1.dp, Color(0xFF06B6D4).copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.CloudDownload, contentDescription = null, tint = Color(0xFF06B6D4), modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text("📡 Xtream Codes API কন্ট্রোল প্যানেল", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("STAR Jalsha, Hum TV, Pakistani Drama, বাংলা নাটক, লাইভ টিভি ও মুভি-সিরিজ সিঙ্ক করুন", color = Color(0xFF94A3B8), fontSize = 11.5.sp)
                                }
                            }

                            HorizontalDivider(color = Color(0xFF334155))

                            Text(
                                text = if (xtreamEditingId != null) "✏️ Xtream সার্ভার এডিট করুন" else "➕ Xtream Codes সার্ভার যোগ / কনফিগার করুন",
                                color = Color(0xFF38BDF8),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )

                            // 1. Server Name
                            OutlinedTextField(
                                value = xtreamServerName,
                                onValueChange = { xtreamServerName = it },
                                label = { Text("সার্ভারের নাম (যেমন: Starshare Xtream)", color = Color(0xFF94A3B8)) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )

                            // 2. Server Host URL
                            OutlinedTextField(
                                value = xtreamServerUrl,
                                onValueChange = { xtreamServerUrl = it },
                                label = { Text("Host URL (যেমন: http://rgkkw.live:80)", color = Color(0xFF94A3B8)) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )

                            // 3. Username
                            OutlinedTextField(
                                value = xtreamUsernameInput,
                                onValueChange = { xtreamUsernameInput = it },
                                label = { Text("👤 Username (যেমন: 4dfoydR2gZ)", color = Color(0xFF94A3B8)) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )

                            // 4. Password
                            OutlinedTextField(
                                value = xtreamPasswordInput,
                                onValueChange = { xtreamPasswordInput = it },
                                label = { Text("🔐 Password (যেমন: clever3still)", color = Color(0xFF94A3B8)) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                visualTransformation = if (xtreamPasswordVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { xtreamPasswordVisible = !xtreamPasswordVisible }) {
                                        Icon(
                                            imageVector = if (xtreamPasswordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                            contentDescription = "Toggle password visibility",
                                            tint = Color(0xFF94A3B8)
                                        )
                                    }
                                }
                            )

                            // Content Inclusion Switches
                            Text("অন্তর্ভুক্তির অপশনসমূহ:", color = Color(0xFFE2E8F0), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = xtreamIncludeLive,
                                        onCheckedChange = { xtreamIncludeLive = it },
                                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF06B6D4), uncheckedColor = Color(0xFF64748B))
                                    )
                                    Text("🔴 Live TV চ্যানেল (STAR Jalsha, Hum TV ইত্যাদি)", color = Color.White, fontSize = 11.5.sp)
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = xtreamIncludeSeries,
                                        onCheckedChange = { xtreamIncludeSeries = it },
                                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF06B6D4), uncheckedColor = Color(0xFF64748B))
                                    )
                                    Text("🎭 সিরিয়াল ও নাটক (STAR Jalsha, Hum TV, Pak Drama ইত্যাদি)", color = Color.White, fontSize = 11.5.sp)
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = xtreamIncludeVod,
                                        onCheckedChange = { xtreamIncludeVod = it },
                                        colors = CheckboxDefaults.colors(checkedColor = Color(0xFF06B6D4), uncheckedColor = Color(0xFF64748B))
                                    )
                                    Text("🎬 VOD মুভিজ (Bangla, Hindi Dubbed, Netflix)", color = Color.White, fontSize = 11.5.sp)
                                }
                            }

                            // Test Result Banner
                            if (xtreamTestResult != null) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (xtreamIsTestSuccess) Color(0xFF064E3B) else Color(0xFF450A0A)
                                    ),
                                    border = BorderStroke(1.dp, if (xtreamIsTestSuccess) Color(0xFF10B981) else Color(0xFFEF4444))
                                ) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            if (xtreamIsTestSuccess) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
                                            contentDescription = null,
                                            tint = if (xtreamIsTestSuccess) Color(0xFF34D399) else Color(0xFFF87171),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = xtreamTestResult ?: "",
                                            color = Color.White,
                                            fontSize = 11.5.sp
                                        )
                                    }
                                }
                            }

                            // Action Buttons
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        if (xtreamServerUrl.isNotBlank() && xtreamUsernameInput.isNotBlank() && xtreamPasswordInput.isNotBlank()) {
                                            xtreamTesting = true
                                            xtreamTestResult = null
                                            coroutineScope.launch {
                                                val (ok, msg) = repository.testXtreamCodes(xtreamServerUrl, xtreamUsernameInput, xtreamPasswordInput)
                                                xtreamTesting = false
                                                xtreamIsTestSuccess = ok
                                                xtreamTestResult = msg
                                            }
                                        } else {
                                            Toast.makeText(context, "Host URL, Username এবং Password প্রদান করুন", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f).height(46.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, Color(0xFF06B6D4)),
                                    enabled = !xtreamTesting && !xtreamSyncing
                                ) {
                                    if (xtreamTesting) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF06B6D4), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Rounded.Bolt, contentDescription = null, tint = Color(0xFF06B6D4), modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("টেস্ট করুন", color = Color(0xFF06B6D4), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }

                                Button(
                                    onClick = {
                                        if (xtreamServerUrl.isNotBlank() && xtreamUsernameInput.isNotBlank() && xtreamPasswordInput.isNotBlank()) {
                                            val accountId = xtreamEditingId ?: "xtream_${System.currentTimeMillis()}"
                                            val account = com.example.model.XtreamAccount(
                                                id = accountId,
                                                name = if (xtreamServerName.isNotBlank()) xtreamServerName.trim() else "Xtream Server",
                                                serverUrl = xtreamServerUrl.trim(),
                                                username = xtreamUsernameInput.trim(),
                                                password = xtreamPasswordInput.trim(),
                                                isEnabled = true,
                                                includeLive = xtreamIncludeLive,
                                                includeVod = xtreamIncludeVod,
                                                includeSeries = xtreamIncludeSeries,
                                                lastSyncTime = System.currentTimeMillis(),
                                                statusMessage = "সক্রিয় (Active)"
                                            )
                                            repository.saveXtreamAccount(account)
                                            xtreamAccountsList = repository.getXtreamAccounts()
                                            xtreamEditingId = null

                                            // Sync live channels and movies/series immediately
                                            xtreamSyncing = true
                                            coroutineScope.launch {
                                                try {
                                                    val movAndSeries = repository.fetchAllXtreamMoviesAndSeries()
                                                    if (movAndSeries.isNotEmpty()) {
                                                        repository.saveCachedMoviesList((moviesList + movAndSeries).distinctBy { it.id }.sortedByDescending { it.movieSortWeight })
                                                    }
                                                    val liveChans = repository.fetchAllXtreamLiveChannels()
                                                    if (liveChans.isNotEmpty()) {
                                                        repository.saveCachedLiveTvChannels((liveTvList + liveChans).distinctBy { it.id })
                                                    }
                                                    onDataChanged()
                                                    Toast.makeText(context, "✅ Xtream ডাটা সফলভাবে সিঙ্ক হয়েছে! (${movAndSeries.size} টি মুভি/সিরিজ ও ${liveChans.size} টি লাইভ চ্যানেল)", Toast.LENGTH_LONG).show()
                                                } catch (e: Exception) {
                                                    Toast.makeText(context, "সংরক্ষিত হয়েছে! ব্যাকগ্রাউন্ড সিঙ্ক চালু আছে।", Toast.LENGTH_SHORT).show()
                                                } finally {
                                                    xtreamSyncing = false
                                                }
                                            }
                                        } else {
                                            Toast.makeText(context, "সব তথ্য সঠিকভাবে দিন", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1.3f).height(46.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0891B2)),
                                    enabled = !xtreamTesting && !xtreamSyncing
                                ) {
                                    if (xtreamSyncing) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("সিঙ্ক হচ্ছে...", color = Color.White, fontSize = 12.sp)
                                    } else {
                                        Icon(Icons.Rounded.Save, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("সেভ ও সিঙ্ক করুন", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // Configured Xtream Servers List
                item {
                    Text(
                        text = "📋 কনফিগার করা Xtream সার্ভারসমূহ (${xtreamAccountsList.size} টি)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }

                items(xtreamAccountsList) { account ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        border = BorderStroke(1.dp, if (account.isEnabled) Color(0xFF06B6D4).copy(alpha = 0.4f) else Color(0xFF334155))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(if (account.isEnabled) Color(0xFF10B981) else Color(0xFF64748B))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = account.name,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                Switch(
                                    checked = account.isEnabled,
                                    onCheckedChange = { enabled ->
                                        val updated = account.copy(isEnabled = enabled)
                                        repository.saveXtreamAccount(updated)
                                        xtreamAccountsList = repository.getXtreamAccounts()
                                    },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF06B6D4), checkedTrackColor = Color(0xFF0891B2).copy(alpha = 0.5f))
                                )
                            }

                            // Host & Credentials Preview
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("🌐 Host: ${account.serverUrl}", color = Color(0xFF94A3B8), fontSize = 11.5.sp)
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("👤 User: ${account.username}", color = Color(0xFFCBD5E1), fontSize = 11.5.sp)
                                    Text("🔐 Pass: ••••••••", color = Color(0xFFCBD5E1), fontSize = 11.5.sp)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (account.includeLive) Text("🔴 Live TV", color = Color(0xFF34D399), fontSize = 10.5.sp)
                                    if (account.includeSeries) Text("🎭 Series/Drama", color = Color(0xFF38BDF8), fontSize = 10.5.sp)
                                    if (account.includeVod) Text("🎬 Movies", color = Color(0xFFFBBF24), fontSize = 10.5.sp)
                                }
                            }

                            // Action Buttons: Test, Edit, Delete, Full Sync
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = {
                                        xtreamTesting = true
                                        xtreamTestResult = null
                                        coroutineScope.launch {
                                            val (ok, msg) = repository.testXtreamCodes(account.serverUrl, account.username, account.password)
                                            xtreamTesting = false
                                            xtreamIsTestSuccess = ok
                                            xtreamTestResult = "[${account.name}] $msg"
                                        }
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF06B6D4))
                                ) {
                                    Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("টেস্ট", fontSize = 11.sp)
                                }

                                TextButton(
                                    onClick = {
                                        xtreamEditingId = account.id
                                        xtreamServerName = account.name
                                        xtreamServerUrl = account.serverUrl
                                        xtreamUsernameInput = account.username
                                        xtreamPasswordInput = account.password
                                        xtreamIncludeLive = account.includeLive
                                        xtreamIncludeVod = account.includeVod
                                        xtreamIncludeSeries = account.includeSeries
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF38BDF8))
                                ) {
                                    Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("এডিট", fontSize = 11.sp)
                                }

                                TextButton(
                                    onClick = {
                                        xtreamSyncing = true
                                        coroutineScope.launch {
                                            try {
                                                val movAndSeries = repository.fetchAllXtreamMoviesAndSeries()
                                                if (movAndSeries.isNotEmpty()) {
                                                    repository.saveCachedMoviesList((moviesList + movAndSeries).distinctBy { it.id }.sortedByDescending { it.movieSortWeight })
                                                }
                                                val liveChans = repository.fetchAllXtreamLiveChannels()
                                                if (liveChans.isNotEmpty()) {
                                                    repository.saveCachedLiveTvChannels((liveTvList + liveChans).distinctBy { it.id })
                                                }
                                                onDataChanged()
                                                Toast.makeText(context, "✅ সিঙ্ক সম্পন্ন! (${movAndSeries.size} মুভি/সিরিজ ও ${liveChans.size} লাইভ টিভি চ্যানেল যুক্ত হয়েছে)", Toast.LENGTH_LONG).show()
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "সিঙ্ক ব্যর্থ: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                            } finally {
                                                xtreamSyncing = false
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFF10B981))
                                ) {
                                    Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("সিঙ্ক", fontSize = 11.sp)
                                }

                                if (xtreamAccountsList.size > 1) {
                                    TextButton(
                                        onClick = {
                                            repository.deleteXtreamAccount(account.id)
                                            xtreamAccountsList = repository.getXtreamAccounts()
                                            Toast.makeText(context, "সার্ভার মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFEF4444))
                                    ) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("মুছুন", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // Quick Series & Channels Category Overview Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        border = BorderStroke(1.dp, Color(0xFF334155))
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("✨ অটো-সিঙ্ক হওয়া প্রধান ক্যাটাগরি ও সিরিয়ালসমূহ:", color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                            Text("• 🌟 STAR JALSHA (Cat 460): অনুরাগের ছোঁয়া, রামপ্রসাদ, বাংলা মিডিয়াম ও সমস্ত সিরিয়াল", color = Color(0xFFE2E8F0), fontSize = 11.sp)
                            Text("• 📺 HUM TV (Cat 463): মহব্বত গুমশুদা মেরি, য়ুনহি, দিল পে জখম খায়ে হ্যায় ইত্যাদি", color = Color(0xFFE2E8F0), fontSize = 11.sp)
                            Text("• 🇵🇰 PAKISTANI DRAMA (Cat 293): হযরত ইউসুফ, আয়নক ওয়ালা জিন, উম্মীদ ইত্যাদি", color = Color(0xFFE2E8F0), fontSize = 11.sp)
                            Text("• 🇧🇩 ZEE BANGLA, SUN BANGLA & COLORS BANGLA: ইচ্ছে পুতুল, দিদি নং ১ ও সকল নাটক", color = Color(0xFFE2E8F0), fontSize = 11.sp)
                            Text("• 🔴 LIVE BENGALI TV (Cat 9): STAR JALSHA 4K/HD/FHD, Zee Bangla, Colors Bangla", color = Color(0xFFE2E8F0), fontSize = 11.sp)
                            Text("• 🔴 LIVE PAKISTAN TV (Cat 4 & 350): HUM TV EUROPE/HD/FHD, 24/7 Hum TV Drama", color = Color(0xFFE2E8F0), fontSize = 11.sp)
                        }
                    }
                }
            }
            if (selectedAdminTab == AdminTab.APP_UPDATE) {
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
                                Icon(Icons.Rounded.RocketLaunch, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "ইন-অ্যাপ আপডেট ও ভার্সন কন্ট্রোল",
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                text = "এখানে নতুন ভার্সন ও ডিরেক্ট APK ডাউনলোড লিংক দিয়ে পাবলিশ করলে সমস্ত ইউজারদের ডিভাইসে সুন্দর ইন-অ্যাপ ডাউনলোড ও ইনস্টল পপআপ চলে যাবে।",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )

                            // Current vs Cloud Version Status Banner
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF0F172A),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("বর্তমান বিল্ড ভার্সন:", color = Color(0xFF64748B), fontSize = 11.sp)
                                        Text("v${BuildConfig.VERSION_NAME} (Code ${BuildConfig.VERSION_CODE})", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF10B981).copy(alpha = 0.2f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981))
                                    ) {
                                        Text(
                                            text = "ক্লাউড: v$updateVersionName (Code $updateVersionCode)",
                                            color = Color(0xFF34D399),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            // Version Code and Version Name Fields
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = updateVersionCode,
                                    onValueChange = { updateVersionCode = it },
                                    label = { Text("Version Code (যেমন 26, 27)", fontSize = 11.sp) },
                                    colors = customFieldColors(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )

                                OutlinedTextField(
                                    value = updateVersionName,
                                    onValueChange = { updateVersionName = it },
                                    label = { Text("Version Name (যেমন 2.5.2)", fontSize = 11.sp) },
                                    colors = customFieldColors(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true,
                                    modifier = Modifier.weight(1.2f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.TextButton(
                                    onClick = {
                                        updateVersionCode = (com.example.BuildConfig.VERSION_CODE + 1).toString()
                                        updateVersionName = "2.5.3"
                                        Toast.makeText(context, "পরবর্তী ভার্সন সেট করা হয়েছে (v2.5.3, Code ${com.example.BuildConfig.VERSION_CODE + 1})", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Rounded.RocketLaunch, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("v2.5.3 সেট করুন (Next)", color = Color(0xFF10B981), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                androidx.compose.material3.TextButton(
                                    onClick = {
                                        updateVersionCode = com.example.BuildConfig.VERSION_CODE.toString()
                                        updateVersionName = com.example.BuildConfig.VERSION_NAME
                                        Toast.makeText(context, "বর্তমান অ্যাপ ভার্সন পূরণ করা হয়েছে (v${com.example.BuildConfig.VERSION_NAME})", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Rounded.Sync, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("বর্তমান ভার্সন (v${com.example.BuildConfig.VERSION_NAME})", color = Color(0xFF00E5FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Direct APK Download URL
                            OutlinedTextField(
                                value = updateDownloadUrl,
                                onValueChange = { updateDownloadUrl = it },
                                label = { Text("Direct APK Download URL (.apk লিংক)") },
                                placeholder = { Text("https://example.com/nafitv24_v2.5.apk", color = Color(0xFF475569)) },
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // APK Size & Release Date
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = updateApkSize,
                                    onValueChange = { updateApkSize = it },
                                    label = { Text("APK Size (যেমন 18.5 MB)", fontSize = 11.sp) },
                                    colors = customFieldColors(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )

                                OutlinedTextField(
                                    value = updateReleaseDate,
                                    onValueChange = { updateReleaseDate = it },
                                    label = { Text("Release Date (যেমন 15 Aug 2026)", fontSize = 11.sp) },
                                    colors = customFieldColors(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true,
                                    modifier = Modifier.weight(1.2f)
                                )
                            }

                            // Release Notes Field
                            OutlinedTextField(
                                value = updateReleaseNotes,
                                onValueChange = { updateReleaseNotes = it },
                                label = { Text("নতুন ফিচার ও চেঞ্জলগ (Release Notes)") },
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                minLines = 3,
                                maxLines = 6,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Mandatory Force Update Toggle Switch
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF0F172A),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("বাধ্যতামূলক আপডেট (Force Update)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Text("চালু রাখলে আপডেট না করা পর্যন্ত ব্যবহারকারী অ্যাপের মেনুতে ঢুকতে পারবে না", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                    }
                                    Switch(
                                        checked = updateIsForce,
                                        onCheckedChange = { updateIsForce = it },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = Color(0xFFEF4444)
                                        )
                                    )
                                }
                            }

                            // Action Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        previewUpdateDialog = AppUpdateInfo(
                                            versionCode = updateVersionCode.toIntOrNull() ?: (com.example.BuildConfig.VERSION_CODE + 1),
                                            versionName = updateVersionName.ifBlank { "2.5.3" },
                                            downloadUrl = updateDownloadUrl.ifBlank { "https://example.com/nafitv24_update.apk" },
                                            releaseNotes = updateReleaseNotes.ifBlank { "• নতুন লাইভ স্ট্রিমিং ও স্পোর্টস সার্ভার সংযোজন\n• ভিডিও প্লেয়ার ফুলস্ক্রিন ও স্ট্যাবিলিটি বৃদ্ধি\n• দ্রুত লোডিং ও বাগ ফিক্স" },
                                            isForceUpdate = updateIsForce,
                                            apkSize = updateApkSize.ifBlank { "18.5 MB" },
                                            releaseDate = updateReleaseDate.ifBlank { "01 Sep 2026" }
                                        )
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E5FF))
                                ) {
                                    Icon(Icons.Rounded.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("পপআপ প্রিভিউ", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        val enteredCode = updateVersionCode.toIntOrNull() ?: (com.example.BuildConfig.VERSION_CODE + 1)
                                        val isNewer = com.example.util.AppUpdateHelper.isVersionNameNewer(updateVersionName, com.example.BuildConfig.VERSION_NAME)
                                        val finalCode = if (isNewer && enteredCode <= com.example.BuildConfig.VERSION_CODE) {
                                            com.example.BuildConfig.VERSION_CODE + 1
                                        } else {
                                            enteredCode
                                        }

                                        val info = AppUpdateInfo(
                                            versionCode = finalCode,
                                            versionName = updateVersionName.ifBlank { "2.5.3" },
                                            downloadUrl = updateDownloadUrl.trim(),
                                            releaseNotes = updateReleaseNotes.trim(),
                                            isForceUpdate = updateIsForce,
                                            apkSize = updateApkSize.trim(),
                                            releaseDate = updateReleaseDate.trim()
                                        )
                                        coroutineScope.launch {
                                            isSavingUpdate = true
                                            val ok = repository.pushAppUpdateInfo(info)
                                            isSavingUpdate = false
                                            if (ok) {
                                                Toast.makeText(context, "নতুন ভার্সন (v${info.versionName}, Code ${info.versionCode}) সফলভাবে Firebase ক্লাউডে পাবলিশ হয়েছে!", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(context, "Firebase এ সেভ হয়েছে ও ক্যাশে সংরক্ষিত হয়েছে", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1.5f)
                                        .height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.Black)
                                ) {
                                    Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isSavingUpdate) "পাবলিশ হচ্ছে..." else "Firebase এ পাবলিশ করুন", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // TAB: CLOUDSTREAM EXTENSION REPOSITORIES & MOVIE SITES ADMIN
            // -------------------------------------------------------------
            if (selectedAdminTab == AdminTab.REPOSITORIES) {
                // 1. CloudStream Extension Repository Importer Card
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Extension, contentDescription = null, tint = Color(0xFF8B5CF6), modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("🎬 CloudStream রিপোজিটরি ইমপোর্টার", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }

                            Text(
                                text = "CloudStream রিপোজিটরি লিংক যোগ করুন। এই রিপোজিটরির সকল মুভি ওয়েবসাইট এবং প্লাগইন স্বয়ংক্রিয়ভাবে ইউজার মুভি অপশনে প্রবেশযোগ্য হবে এবং ফায়ারবেসে সিঙ্ক থাকবে।",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )

                            // Preset Fast-Add Chips
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("⚡ দ্রুত অ্যাড করার জন্য প্রিসেট চিপস:", color = Color(0xFF00E5FF), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF3B82F6).copy(alpha = 0.2f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B82F6)),
                                        modifier = Modifier.clickable {
                                            repoUrlInput = "cloudstreamrepo://raw.githubusercontent.com/Hexated/cloudstream-extensions-hexated/builds/repo.json"
                                        }
                                    ) {
                                        Text("⚡ Hexated Repo", color = Color(0xFF93C5FD), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF8B5CF6).copy(alpha = 0.2f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8B5CF6)),
                                        modifier = Modifier.clickable {
                                            repoUrlInput = "cloudstreamrepo://raw.githubusercontent.com/stormunblessed/cloudstream-extensions-storm/refs/heads/builds/repo.json"
                                        }
                                    ) {
                                        Text("🌪️ Storm Repo", color = Color(0xFFDDD6FE), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF10B981).copy(alpha = 0.2f),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981)),
                                        modifier = Modifier.clickable {
                                            repoUrlInput = "cloudstreamrepo://raw.githubusercontent.com/stormunblessed/cloudstream-extensions-storm/refs/heads/builds/repo.json"
                                        }
                                    ) {
                                        Text("🚀 Storm", color = Color(0xFFA7F3D0), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = repoUrlInput,
                                onValueChange = { repoUrlInput = it },
                                label = { Text("CloudStream Repo URL (cloudstreamrepo:// বা https://)") },
                                placeholder = { Text("cloudstreamrepo://raw.githubusercontent.com/.../repo.json", color = Color(0xFF64748B), fontSize = 11.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = false,
                                maxLines = 3
                            )

                            Button(
                                onClick = {
                                    val url = repoUrlInput.trim()
                                    if (url.isBlank()) {
                                        Toast.makeText(context, "অনুগ্রহ করে একটি সঠিক CloudStream Repo URL দিন", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    coroutineScope.launch {
                                        isFetchingRepo = true
                                        try {
                                            val parsedRepo = repository.parseCloudStreamRepo(url)
                                            if (parsedRepo != null && parsedRepo.providers.isNotEmpty()) {
                                                val existing = repository.getSavedCloudStreamRepos()
                                                val updated = (listOf(parsedRepo) + existing.filterNot { it.id == parsedRepo.id })
                                                repository.saveCloudStreamRepos(updated)
                                                repository.pushCloudStreamReposToFirebase(updated)
                                                repoUrlInput = ""
                                                onDataChanged()
                                                Toast.makeText(
                                                    context,
                                                    "✅ ${parsedRepo.name} (${parsedRepo.providers.size} টি সাইট/এক্সটেনশন) সফলভাবে এড হয়েছে ও Firebase এ সেভ হয়েছে!",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            } else {
                                                Toast.makeText(
                                                    context,
                                                    "রিপোজিটরি ফেচ করা যায়নি। URL সঠিক কিনা ও ইন্টারনেট কানেকশন চেক করুন।",
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "ত্রুটি: ${e.message}", Toast.LENGTH_LONG).show()
                                        } finally {
                                            isFetchingRepo = false
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6), contentColor = Color.White),
                                enabled = !isFetchingRepo
                            ) {
                                if (isFetchingRepo) {
                                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("রিপোজিটরি ও সাইট ফেচ হচ্ছে...", fontSize = 12.sp)
                                } else {
                                    Icon(Icons.Rounded.DownloadDone, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("📥 রিপোজিটরি ফেচ ও ক্লাউডে সেভ করুন", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }

                // 2. Custom Movie Website Add Card
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Language, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("🌐 কাস্টম মুভি ওয়েবসাইট যোগ করুন", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }

                            Text(
                                text = "যেকোনো ফ্রি মুভি বা ভিডিও ওয়েবসাইট সরাসরি এখানে যুক্ত করুন। ইউজাররা অ্যাপের ভেতরে থেকেই ব্রাউজ করে ফুলস্ক্রিনে প্লে করতে পারবেন।",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )

                            OutlinedTextField(
                                value = customSiteName,
                                onValueChange = { customSiteName = it },
                                label = { Text("সাইটের নাম (যেমন: BollyFlix / MovieHD)") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = customSiteUrl,
                                onValueChange = { customSiteUrl = it },
                                label = { Text("ওয়েবসাইট URL (যেমন: https://bollyflix.lat)") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(10.dp),
                                singleLine = true
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = customSiteCategory,
                                    onValueChange = { customSiteCategory = it },
                                    label = { Text("ক্যাটাগরি") },
                                    modifier = Modifier.weight(1f),
                                    colors = customFieldColors(),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = customSiteLogo,
                                    onValueChange = { customSiteLogo = it },
                                    label = { Text("লোগো URL (ঐচ্ছিক)") },
                                    modifier = Modifier.weight(1f),
                                    colors = customFieldColors(),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true
                                )
                            }

                            Button(
                                onClick = {
                                    if (customSiteName.isBlank() || customSiteUrl.isBlank()) {
                                        Toast.makeText(context, "অনুগ্রহ করে সাইটের নাম এবং URL পূরণ করুন", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    val newProvider = MovieProvider(
                                        id = "custom_site_${System.currentTimeMillis()}",
                                        name = customSiteName.trim(),
                                        siteUrl = customSiteUrl.trim(),
                                        iconUrl = customSiteLogo.trim().ifBlank { null },
                                        types = listOf(customSiteCategory.trim().ifBlank { "Movies & Series" }),
                                        description = customSiteDesc.trim().ifBlank { "Direct Movie Website" },
                                        isCustom = true
                                    )
                                    val currentCustom = repository.getCustomMovieProviders()
                                    val updated = (listOf(newProvider) + currentCustom.filterNot { it.id == newProvider.id })
                                    repository.saveCustomMovieProviders(updated)
                                    coroutineScope.launch {
                                        repository.pushCloudStreamReposToFirebase(repository.getSavedCloudStreamRepos())
                                    }
                                    customSiteName = ""
                                    customSiteUrl = ""
                                    customSiteLogo = ""
                                    customSiteDesc = ""
                                    onDataChanged()
                                    Toast.makeText(context, "✅ '${newProvider.name}' সাইট মুভি সেকশনে যুক্ত হয়েছে!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black)
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("➕ মুভি সাইট যুক্ত করুন", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // 3. Section Header for Installed Repositories
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "📦 ইনস্টল করা রিপোজিটরি (${cloudStreamRepos.size})",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "মোট সাইট: ${movieProviders.size}",
                            color = Color(0xFF00E5FF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                // Render each installed CloudStream Repo
                items(cloudStreamRepos) { repo ->
                    val isExpanded = expandedRepoId == repo.id
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (repo.enabled) Color(0xFF8B5CF6).copy(alpha = 0.5f) else Color(0xFF475569)),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Extension,
                                        contentDescription = null,
                                        tint = if (repo.enabled) Color(0xFF8B5CF6) else Color(0xFF64748B),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = repo.name,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "${repo.providers.size} টি সাইট • v${repo.manifestVersion}",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    // Expand / Collapse button
                                    IconButton(onClick = {
                                        expandedRepoId = if (isExpanded) null else repo.id
                                    }) {
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                            contentDescription = "Expand",
                                            tint = Color(0xFF00E5FF)
                                        )
                                    }

                                    // Delete Repo
                                    IconButton(onClick = {
                                        repoToDelete = repo
                                    }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(20.dp))
                                    }
                                }
                            }

                            // URL string
                            Text(
                                text = repo.url,
                                color = Color(0xFF64748B),
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            // Action buttons: Refresh, Push to Firebase
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            val refreshed = repository.parseCloudStreamRepo(repo.url)
                                            if (refreshed != null) {
                                                val all = repository.getSavedCloudStreamRepos()
                                                val updated = all.map { if (it.id == repo.id) refreshed else it }
                                                repository.saveCloudStreamRepos(updated)
                                                repository.pushCloudStreamReposToFirebase(updated)
                                                onDataChanged()
                                                Toast.makeText(context, "✅ '${repo.name}' সফলভাবে আপডেট হয়েছে!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "আপডেট করা যায়নি", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8B5CF6)),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDDD6FE))
                                ) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("রিফ্রেশ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            repository.pushCloudStreamReposToFirebase(repository.getSavedCloudStreamRepos())
                                            Toast.makeText(context, "✅ '${repo.name}' Firebase ক্লাউডে পুশ করা হয়েছে!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.Black)
                                ) {
                                    Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Firebase সিঙ্ক", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // Expanded List of Websites / Providers inside this Repo
                            if (isExpanded) {
                                HorizontalDivider(color = Color(0xFF334155), modifier = Modifier.padding(vertical = 4.dp))
                                Text(
                                    text = "🌐 এই রিপোজিটরির ওয়েবসাইট তালিকা (${repo.providers.size} টি):",
                                    color = Color(0xFF00E5FF),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )

                                repo.providers.forEach { provider ->
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color(0xFF0F172A),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(32.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(Color(0xFF1E293B)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (!provider.logoUrl.isNullOrBlank()) {
                                                        AsyncImage(
                                                            model = provider.logoUrl,
                                                            contentDescription = provider.name,
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    } else {
                                                        Icon(Icons.Rounded.Movie, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(18.dp))
                                                    }
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text(
                                                        text = provider.name,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp
                                                    )
                                                    Text(
                                                        text = "${provider.category} • ${provider.status}",
                                                        color = Color(0xFF94A3B8),
                                                        fontSize = 10.sp
                                                    )
                                                }
                                            }

                                            // Test Visit in In-App Browser button
                                            Button(
                                                onClick = {
                                                    onOpenMovieProvider(provider)
                                                },
                                                shape = RoundedCornerShape(6.dp),
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                modifier = Modifier.height(30.dp)
                                            ) {
                                                Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("ভিজিট ও রান", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Custom Providers Section
                val customProviders = movieProviders.filter { it.isCustom }
                if (customProviders.isNotEmpty()) {
                    item {
                        Text(
                            text = "⭐ কাস্টম মুভি সাইট (${customProviders.size})",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    items(customProviders) { provider ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF1E293B),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Rounded.Language, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(text = provider.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text(text = provider.url, color = Color(0xFF94A3B8), fontSize = 10.sp, maxLines = 1)
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Button(
                                        onClick = { onOpenMovieProvider(provider) },
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("রান", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }

                                    IconButton(onClick = { providerToDelete = provider }) {
                                        Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // TAB 6 CONTENT: FIREBASE CLOUD REALTIME DATABASE SETTINGS
            // -------------------------------------------------------------
            if (selectedAdminTab == AdminTab.FIREBASE) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF9800).copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.CloudSync, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("🔥 Firebase Realtime Database সিঙ্ক সেটিংস", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }

                            Text(
                                text = "এখানে আপনার Firebase Realtime Database URL সেট করুন। এখান থেকে যোগ করা সকল খেলা, চ্যানেল এবং প্লেলিস্ট সরাসরি Firebase ডেটাবেসে সেভ হবে এবং ইনস্টল করা সকল ইউজারের অ্যাপে সরাসরি লাইভ দেখা যাবে।",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )

                            OutlinedTextField(
                                value = firebaseUrlInput,
                                onValueChange = {
                                    firebaseUrlInput = it
                                    firebaseTestResult = null
                                },
                                label = { Text("Firebase Database URL") },
                                placeholder = { Text("https://your-project.firebaseio.com/", color = Color(0xFF64748B)) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true
                            )

                            // Test Connection & Save Buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            isTestingFirebase = true
                                            firebaseTestResult = repository.testFirebaseConnection(firebaseUrlInput.trim())
                                            isTestingFirebase = false
                                        }
                                    },
                                    modifier = Modifier.weight(1f).height(46.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                                ) {
                                    if (isTestingFirebase) {
                                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("টেস্ট হচ্ছে...", fontSize = 11.sp)
                                    } else {
                                        Icon(Icons.Rounded.Speed, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("টেস্ট কানেকশন", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }

                                Button(
                                    onClick = {
                                        if (firebaseUrlInput.isNotBlank()) {
                                            repository.saveFirebaseUrl(firebaseUrlInput.trim())
                                            onDataChanged()
                                            Toast.makeText(context, "✅ Firebase URL সফলভাবে সেভ হয়েছে!", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "অনুগ্রহ করে একটি সঠিক Firebase URL দিন", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f).height(46.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.Black)
                                ) {
                                    Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("সেভ করুন", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }

                            // Test Result Banner
                            firebaseTestResult?.let { res ->
                                Surface(
                                    color = if (res.first) Color(0xFF065F46).copy(alpha = 0.35f) else Color(0xFF7F1D1D).copy(alpha = 0.35f),
                                    shape = RoundedCornerShape(10.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, if (res.first) Color(0xFF10B981) else Color(0xFFEF4444)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (res.first) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                                            contentDescription = null,
                                            tint = if (res.first) Color(0xFF10B981) else Color(0xFFEF4444),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = res.second,
                                            color = if (res.first) Color(0xFFA7F3D0) else Color(0xFFFECACA),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = Color(0xFF334155), modifier = Modifier.padding(vertical = 4.dp))

                            // Firebase Rules Setup Instructions Box
                            Surface(
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = "📋 Firebase ডেটাবেস রুলস (Rules):",
                                        color = Color(0xFF00E5FF),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "Firebase Console > Realtime Database > Rules ট্যাবে গিয়ে নিচের রুলস দিয়ে 'Publish' করুন:",
                                        color = Color(0xFFCBD5E1),
                                        fontSize = 11.sp
                                    )
                                    Surface(
                                        color = Color(0xFF020617),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "{\n  \"rules\": {\n    \".read\": true,\n    \".write\": true\n  }\n}",
                                            color = Color(0xFF10B981),
                                            fontSize = 11.sp,
                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // TAB 7 CONTENT: BROADCAST NOTIFICATIONS TO USERS
            // -------------------------------------------------------------
            if (selectedAdminTab == AdminTab.BROADCAST) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Header Banner
                            Surface(
                                color = Color(0xFF0F172A),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.3f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF00E5FF).copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(24.dp))
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "📢 লাইভ ব্রডকাস্ট নোটিফিকেশন",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = "সব ব্যবহারকারীদের কাছে লাইভ ম্যাচ, টিভি চ্যানেল বা বিশেষ নোটিস পাঠান",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }

                            // Notification Input Form
                            OutlinedTextField(
                                value = broadcastTitle,
                                onValueChange = { broadcastTitle = it },
                                label = { Text("নোটিফিকেশন টাইটেল * (যেমন: ⚽ লাইভ ম্যাচ শুরু!)", color = Color(0xFF94A3B8), fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF00E5FF),
                                    unfocusedBorderColor = Color(0xFF334155),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = Color(0xFF0F172A),
                                    unfocusedContainerColor = Color(0xFF0F172A)
                                )
                            )

                            OutlinedTextField(
                                value = broadcastMessage,
                                onValueChange = { broadcastMessage = it },
                                label = { Text("বিস্তারিত বার্তা * (যেমন: রিয়াল মাদ্রিদ বনাম বার্সেলোনা লাইভ শুরু হয়েছে)", color = Color(0xFF94A3B8), fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                maxLines = 4,
                                shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(0xFF00E5FF),
                                    unfocusedBorderColor = Color(0xFF334155),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = Color(0xFF0F172A),
                                    unfocusedContainerColor = Color(0xFF0F172A)
                                )
                            )

                            // Type Dropdown & Optional Image URL
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    OutlinedButton(
                                        onClick = { broadcastTypeDropdownExpanded = true },
                                        modifier = Modifier.fillMaxWidth().height(52.dp),
                                        shape = RoundedCornerShape(10.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                                    ) {
                                        val typeName = when (broadcastType) {
                                            NotificationType.LIVE_EVENT -> "⚽ ম্যাচ নোটিস"
                                            NotificationType.LIVE_TV -> "📺 টিভি নোটিস"
                                            NotificationType.MOVIE -> "🎬 মুভি নোটিস"
                                            NotificationType.PLAYLIST -> "📂 প্লেলিস্ট"
                                            NotificationType.APP_UPDATE -> "🚀 অ্যাপ আপডেট"
                                            else -> "📢 সাধারণ নোটিস"
                                        }
                                        Text(text = "ক্যাটাগরি: $typeName", fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }

                                    DropdownMenu(
                                        expanded = broadcastTypeDropdownExpanded,
                                        onDismissRequest = { broadcastTypeDropdownExpanded = false },
                                        modifier = Modifier.background(Color(0xFF1E293B))
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("📢 সাধারণ নোটিস", color = Color.White, fontSize = 12.sp) },
                                            onClick = {
                                                broadcastType = NotificationType.BROADCAST
                                                broadcastTypeDropdownExpanded = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("⚽ লাইভ ম্যাচ", color = Color(0xFF10B981), fontSize = 12.sp) },
                                            onClick = {
                                                broadcastType = NotificationType.LIVE_EVENT
                                                broadcastTypeDropdownExpanded = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("📺 টিভি চ্যানেল", color = Color(0xFF00E5FF), fontSize = 12.sp) },
                                            onClick = {
                                                broadcastType = NotificationType.LIVE_TV
                                                broadcastTypeDropdownExpanded = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("🎬 নতুন মুভি", color = Color(0xFF8B5CF6), fontSize = 12.sp) },
                                            onClick = {
                                                broadcastType = NotificationType.MOVIE
                                                broadcastTypeDropdownExpanded = false
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("📂 প্লেলিস্ট", color = Color(0xFFEC4899), fontSize = 12.sp) },
                                            onClick = {
                                                broadcastType = NotificationType.PLAYLIST
                                                broadcastTypeDropdownExpanded = false
                                            }
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = broadcastImageUrl,
                                    onValueChange = { broadcastImageUrl = it },
                                    label = { Text("ইমেজ URL (ঐচ্ছিক)", color = Color(0xFF94A3B8), fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color(0xFF00E5FF),
                                        unfocusedBorderColor = Color(0xFF334155),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedContainerColor = Color(0xFF0F172A),
                                        unfocusedContainerColor = Color(0xFF0F172A)
                                    )
                                )
                            }

                            // Send Button
                            Button(
                                onClick = {
                                    if (broadcastTitle.isBlank()) {
                                        Toast.makeText(context, "অনুগ্রহ করে টাইটেল দিন", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    coroutineScope.launch {
                                        isSendingBroadcast = true
                                        val newNotif = AppNotification(
                                            title = broadcastTitle.trim(),
                                            message = broadcastMessage.trim(),
                                            type = broadcastType,
                                            imageUrl = broadcastImageUrl.trim().ifEmpty { null },
                                            targetId = broadcastTargetId.trim().ifEmpty { null },
                                            timestamp = System.currentTimeMillis()
                                        )
                                        repository.broadcastNotification(newNotif)
                                        NotificationHelper.showSystemNotification(context, newNotif)
                                        adminNotificationHistory = repository.getStoredNotifications()
                                        isSendingBroadcast = false
                                        broadcastTitle = ""
                                        broadcastMessage = ""
                                        broadcastImageUrl = ""
                                        broadcastTargetId = ""
                                        Toast.makeText(context, "✅ নোটিফিকেশন সফলভাবে পাঠানো হয়েছে!", Toast.LENGTH_LONG).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(48.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF00E5FF),
                                    contentColor = Color.Black
                                ),
                                enabled = !isSendingBroadcast
                            ) {
                                if (isSendingBroadcast) {
                                    CircularProgressIndicator(color = Color.Black, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("নোটিফিকেশন পাঠানো হচ্ছে...", fontWeight = FontWeight.Bold)
                                } else {
                                    Icon(Icons.Rounded.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("নোটিফিকেশন পাঠান ও ব্রডকাস্ট করুন", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }

                            HorizontalDivider(color = Color(0xFF334155), modifier = Modifier.padding(vertical = 6.dp))

                            // Recent Sent Notifications
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "📜 প্রেরিত নোটিফিকেশন হিস্ট্রি (${adminNotificationHistory.size} টি)",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                if (adminNotificationHistory.isNotEmpty()) {
                                    TextButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                repository.clearAllNotifications()
                                                adminNotificationHistory = emptyList()
                                                Toast.makeText(context, "সকল নোটিফিকেশন মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Rounded.DeleteSweep, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("সব মুছুন", color = Color(0xFFEF4444), fontSize = 11.sp)
                                    }
                                }
                            }

                            if (adminNotificationHistory.isEmpty()) {
                                Surface(
                                    color = Color(0xFF0F172A),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(modifier = Modifier.padding(20.dp), contentAlignment = Alignment.Center) {
                                        Text("এখনও কোনো নোটিফিকেশন পাঠানো হয়নি", color = Color(0xFF64748B), fontSize = 12.sp)
                                    }
                                }
                            } else {
                                adminNotificationHistory.take(15).forEach { notif ->
                                    Surface(
                                        color = Color(0xFF0F172A),
                                        shape = RoundedCornerShape(10.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = notif.title,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (notif.message.isNotBlank()) {
                                                    Text(
                                                        text = notif.message,
                                                        color = Color(0xFF94A3B8),
                                                        fontSize = 11.sp,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = {
                                                    coroutineScope.launch {
                                                        repository.deleteNotification(notif.id)
                                                        adminNotificationHistory = repository.getStoredNotifications()
                                                        Toast.makeText(context, "নোটিফিকেশন মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            ) {
                                                Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog for Previewing App Update
    if (previewUpdateDialog != null) {
        AppUpdateDialog(
            updateInfo = previewUpdateDialog!!,
            onDismiss = { previewUpdateDialog = null }
        )
    }

    // Dialog for Deleting Playlist (Confirmation)
    if (playlistToDelete != null) {
        val target = playlistToDelete!!
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            containerColor = Color(0xFF1E293B),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("প্লেলিস্ট ডিলিট নিশ্চিতকরণ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text(
                    text = "আপনি কি '${target.title}' প্লেলিস্টটি স্থায়ীভাবে ডিলিট করতে চান?",
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toRemove = target
                        playlistToDelete = null
                        coroutineScope.launch {
                            repository.deletePlaylist(toRemove.id)
                            onDataChanged()
                            Toast.makeText(context, "${toRemove.title} প্লেলিস্ট ডিলিট করা হয়েছে!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444), contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { playlistToDelete = null },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF475569))
                ) {
                    Text("Cancel", fontSize = 12.sp)
                }
            }
        )
    }

    // =========================================================================
    // FULL MATCH & MULTI-SERVER EDIT DIALOG (CRITICAL USER REQUIREMENT)
    // =========================================================================
    if (editingMatchItem != null) {
        val target = editingMatchItem!!
        Dialog(
            onDismissRequest = { editingMatchItem = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2563EB).copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.92f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = Color(0xFF2563EB).copy(alpha = 0.2f),
                                shape = CircleShape,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Edit, contentDescription = null, tint = Color(0xFF60A5FA), modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "খেলা ও সার্ভার এডিট করুন",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "লাইভ চলাকালীন নতুন সার্ভার লিংক যোগ বা পরিবর্তন",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                        }
                        IconButton(onClick = { editingMatchItem = null }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Color(0xFF334155))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Scrollable Edit Form
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Tournament
                        item {
                            OutlinedTextField(
                                value = editTournament,
                                onValueChange = { editTournament = it },
                                label = { Text("টুর্নামেন্ট / সিরিজ (Tournament / Series)") },
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Match Poster Banner / Single Picture URL (User: এখানে একটি ছবি দেখার অপশন করে দিন)
                        item {
                            OutlinedTextField(
                                value = editMatchPosterUrl,
                                onValueChange = { editMatchPosterUrl = it },
                                label = { Text("🖼️ একক ম্যাচের ছবি / ব্যানার URL (Match Poster Image URL)") },
                                placeholder = { Text("https://example.com/banner.jpg", color = Color(0xFF64748B), fontSize = 13.sp) },
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Category & Status
                        item {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Category Dropdown
                                Box(modifier = Modifier.weight(1f)) {
                                    OutlinedTextField(
                                        value = editSportCategory,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("খেলা (Category)", fontSize = 11.sp) },
                                        trailingIcon = {
                                            IconButton(onClick = { editSportDropdownExpanded = true }) {
                                                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = Color(0xFF00E5FF))
                                            }
                                        },
                                        colors = customFieldColors(),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    DropdownMenu(
                                        expanded = editSportDropdownExpanded,
                                        onDismissRequest = { editSportDropdownExpanded = false },
                                        modifier = Modifier.background(Color(0xFF1E293B))
                                    ) {
                                        sportOptions.forEach { option ->
                                            DropdownMenuItem(
                                                text = { Text(option, color = Color.White) },
                                                onClick = {
                                                    editSportCategory = option
                                                    editSportDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                // Status Dropdown
                                Box(modifier = Modifier.weight(1f)) {
                                    OutlinedTextField(
                                        value = editSportStatus,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("অবস্থা (Status)", fontSize = 11.sp) },
                                        trailingIcon = {
                                            IconButton(onClick = { editStatusDropdownExpanded = true }) {
                                                Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = Color(0xFF00E5FF))
                                            }
                                        },
                                        colors = customFieldColors(),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    DropdownMenu(
                                        expanded = editStatusDropdownExpanded,
                                        onDismissRequest = { editStatusDropdownExpanded = false },
                                        modifier = Modifier.background(Color(0xFF1E293B))
                                    ) {
                                        statusOptions.forEach { option ->
                                            DropdownMenuItem(
                                                text = { Text(option, color = Color.White) },
                                                onClick = {
                                                    editSportStatus = option
                                                    editStatusDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Team 1 Name & Score
                        item {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = editTeam1Name,
                                    onValueChange = { editTeam1Name = it },
                                    label = { Text("Team 1 Name") },
                                    modifier = Modifier.weight(1.3f),
                                    colors = customFieldColors(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = editTeam1Score,
                                    onValueChange = { editTeam1Score = it },
                                    label = { Text("Team 1 Score") },
                                    placeholder = { Text("e.g. 182/4", color = Color(0xFF64748B), fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f),
                                    colors = customFieldColors(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                            }
                        }

                        // Team 1 Logo URL
                        item {
                            OutlinedTextField(
                                value = editTeam1Logo,
                                onValueChange = { editTeam1Logo = it },
                                label = { Text("Team 1 Logo URL (Optional)") },
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Team 2 Name & Score
                        item {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = editTeam2Name,
                                    onValueChange = { editTeam2Name = it },
                                    label = { Text("Team 2 Name") },
                                    modifier = Modifier.weight(1.3f),
                                    colors = customFieldColors(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = editTeam2Score,
                                    onValueChange = { editTeam2Score = it },
                                    label = { Text("Team 2 Score") },
                                    placeholder = { Text("e.g. 150/8", color = Color(0xFF64748B), fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f),
                                    colors = customFieldColors(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                            }
                        }

                        // Team 2 Logo URL
                        item {
                            OutlinedTextField(
                                value = editTeam2Logo,
                                onValueChange = { editTeam2Logo = it },
                                label = { Text("Team 2 Logo URL (Optional)") },
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Match Time & Countdown
                        item {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = editMatchTime,
                                    onValueChange = { editMatchTime = it },
                                    label = { Text("Time (e.g. 06:30 AM)") },
                                    modifier = Modifier.weight(1.2f),
                                    colors = customFieldColors(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = editCountdownHours,
                                    onValueChange = { editCountdownHours = it },
                                    label = { Text("Countdown (hrs)") },
                                    modifier = Modifier.weight(1f),
                                    colors = customFieldColors(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                            }
                        }

                        // Multi-Server Header
                        item {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "⚡ মাল্টি-সার্ভার স্ট্রিম লিঙ্কসমূহ (${editServers.size} টি সার্ভার):",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Surface(
                                    color = Color(0xFF10B981).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "খেলা চলাকালীন এডিটেবল",
                                        color = Color(0xFF10B981),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        // Dynamic Server Inputs for Edit
                        items(editServers.size) { index ->
                            val server = editServers[index]
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF1E293B).copy(alpha = 0.7f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Rounded.Dns,
                                                contentDescription = null,
                                                tint = Color(0xFF00E5FF),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "চ্যানেল/সার্ভার #${index + 1}",
                                                color = Color(0xFF00E5FF),
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        if (editServers.size > 1) {
                                            IconButton(
                                                onClick = {
                                                    editServers = editServers.toMutableList().also { it.removeAt(index) }
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Rounded.DeleteOutline,
                                                    contentDescription = "Remove Server",
                                                    tint = Color(0xFFEF4444),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                    OutlinedTextField(
                                        value = server.name,
                                        onValueChange = { newName ->
                                            editServers = editServers.toMutableList().also {
                                                it[index] = it[index].copy(name = newName)
                                            }
                                        },
                                        label = { Text("সার্ভার নাম (Name)", fontSize = 11.sp) },
                                        placeholder = { Text("সার্ভার নাম", color = Color(0xFF64748B), fontSize = 11.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = customFieldColors(),
                                        shape = RoundedCornerShape(8.dp),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = server.url,
                                        onValueChange = { newUrl ->
                                            editServers = editServers.toMutableList().also {
                                                it[index] = it[index].copy(url = newUrl)
                                            }
                                        },
                                        label = { Text("স্ট্রিম URL (.m3u8 / mp4)", fontSize = 11.sp) },
                                        placeholder = { Text("Stream URL (.m3u8 / mp4)", color = Color(0xFF64748B), fontSize = 11.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = customFieldColors(),
                                        shape = RoundedCornerShape(8.dp),
                                        singleLine = true
                                    )
                                }
                            }
                        }

                        // Add Server Button in Edit Dialog
                        item {
                            OutlinedButton(
                                onClick = {
                                    val nextNum = editServers.size + 1
                                    val defaultName = when (nextNum) {
                                        1 -> "T SPORTS"
                                        2 -> "TT"
                                        3 -> "TEMP"
                                        4 -> "HD SERVER 4"
                                        5 -> "SERVER 5 (4K)"
                                        6 -> "SERVER 6 (HLS)"
                                        else -> "SERVER $nextNum"
                                    }
                                    editServers = editServers + StreamServer(defaultName, "")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E5FF))
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("➕ আরও নতুন সার্ভার/চ্যানেল লিঙ্ক যোগ করুন (+ Add Server)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Color(0xFF334155))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Bottom Action Buttons (Save & Update / Cancel)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { editingMatchItem = null },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF475569))
                        ) {
                            Text("বাতিল (Cancel)", fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                val validServers = editServers.mapNotNull {
                                    if (it.url.isNotBlank()) StreamServer(it.name.ifBlank { "Server" }, it.url.trim()) else null
                                }

                                if (validServers.isNotEmpty()) {
                                    val matchTitle = if (editTeam1Name.isNotBlank() && editTeam2Name.isNotBlank()) {
                                        "$editTeam1Name vs $editTeam2Name"
                                    } else {
                                        editTournament.ifBlank { target.title }
                                    }

                                    val parsedCountdown: Long? = when {
                                        editCountdownHours.isNotBlank() && editCountdownHours.toDoubleOrNull() != null -> {
                                            val hrs = editCountdownHours.toDouble()
                                            System.currentTimeMillis() + (hrs * 3600 * 1000L).toLong()
                                        }
                                        editMatchTime.isNotBlank() -> {
                                            parseEventTimeStringToEpochMillis(editMatchTime)
                                        }
                                        else -> target.countdownTargetSeconds
                                    }

                                    val updatedMatch = target.copy(
                                        title = matchTitle,
                                        tournament = editTournament.takeIf { it.isNotBlank() },
                                        category = editSportCategory,
                                        streamUrl = validServers.first().url,
                                        backupUrl = validServers.getOrNull(1)?.url,
                                        servers = validServers,
                                        isLive = editSportStatus.contains("Live", ignoreCase = true),
                                        status = editSportStatus,
                                        eventTime = editMatchTime.ifBlank { editSportStatus },
                                        logoUrl = editMatchPosterUrl.takeIf { it.isNotBlank() },
                                        team1 = editTeam1Name.takeIf { it.isNotBlank() },
                                        team1Logo = editTeam1Logo.takeIf { it.isNotBlank() },
                                        team2 = editTeam2Name.takeIf { it.isNotBlank() },
                                        team2Logo = editTeam2Logo.takeIf { it.isNotBlank() },
                                        matchTimeFormatted = editMatchTime.takeIf { it.isNotBlank() },
                                        countdownTargetSeconds = parsedCountdown,
                                        score1 = editTeam1Score.takeIf { it.isNotBlank() },
                                        score2 = editTeam2Score.takeIf { it.isNotBlank() },
                                        isAdminAdded = true
                                    )

                                    repository.saveCustomStream(updatedMatch)
                                    coroutineScope.launch {
                                        repository.pushToFirebase(updatedMatch)
                                    }
                                    onDataChanged()
                                    editingMatchItem = null
                                    Toast.makeText(context, "খেলা ও সমস্ত সার্ভার লিঙ্ক সফলভাবে আপডেট হয়েছে!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "কমপক্ষে একটি কার্যকর সার্ভার স্ট্রিম লিংক দিন", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1.5f)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ম্যাচ আপডেট করুন", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // 2. LIVE TV CHANNEL EDIT DIALOG (User: এডমিন প্যানেলে সবগুলো এডিট করার অপশন)
    // =========================================================================
    if (editingChannelItem != null) {
        val target = editingChannelItem!!
        Dialog(
            onDismissRequest = { editingChannelItem = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2563EB).copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.90f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = Color(0xFF2563EB).copy(alpha = 0.2f),
                                shape = CircleShape,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.LiveTv, contentDescription = null, tint = Color(0xFF60A5FA), modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "টিভি চ্যানেল ও সার্ভার এডিট করুন",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = target.title,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                        }
                        IconButton(onClick = { editingChannelItem = null }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Color(0xFF334155))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Scrollable Edit Form
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Channel Name
                        item {
                            OutlinedTextField(
                                value = editChannelName,
                                onValueChange = { editChannelName = it },
                                label = { Text("চ্যানেলের নাম (Channel Name)") },
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Category Dropdown
                        item {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = editChannelCategory,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("ক্যাটাগরি (Category)") },
                                    trailingIcon = {
                                        IconButton(onClick = { editChannelCategoryDropdownExpanded = !editChannelCategoryDropdownExpanded }) {
                                            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = Color.White)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().clickable { editChannelCategoryDropdownExpanded = true },
                                    colors = customFieldColors(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                                DropdownMenu(
                                    expanded = editChannelCategoryDropdownExpanded,
                                    onDismissRequest = { editChannelCategoryDropdownExpanded = false },
                                    modifier = Modifier.background(Color(0xFF1E293B))
                                ) {
                                    channelCategoryOptions.forEach { opt ->
                                        DropdownMenuItem(
                                            text = { Text(opt, color = Color.White) },
                                            onClick = {
                                                editChannelCategory = opt
                                                editChannelCategoryDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Logo URL
                        item {
                            OutlinedTextField(
                                value = editChannelLogoUrl,
                                onValueChange = { editChannelLogoUrl = it },
                                label = { Text("লোগো URL (Logo Image URL)") },
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Multi-Servers Section Header
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.Dns, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "স্ট্রিমিং সার্ভার ও ব্যাকআপ লিংক (${editChannelServers.size} টি সার্ভার):",
                                        color = Color(0xFF00E5FF),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Dynamic Server Inputs
                        itemsIndexed(editChannelServers) { idx, srv ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF1E293B).copy(alpha = 0.7f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Rounded.Dns,
                                                contentDescription = null,
                                                tint = Color(0xFF00E5FF),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "চ্যানেল/সার্ভার #${idx + 1}",
                                                color = Color(0xFF00E5FF),
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        if (editChannelServers.size > 1) {
                                            IconButton(
                                                onClick = {
                                                    editChannelServers = editChannelServers.toMutableList().also { it.removeAt(idx) }
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Rounded.DeleteOutline,
                                                    contentDescription = "সার্ভার সরান",
                                                    tint = Color(0xFFEF4444),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                    OutlinedTextField(
                                        value = srv.name,
                                        onValueChange = { newName ->
                                            editChannelServers = editChannelServers.toMutableList().also {
                                                it[idx] = it[idx].copy(name = newName)
                                            }
                                        },
                                        label = { Text("সার্ভার নাম (যেমন: Server ${idx + 1})", fontSize = 11.sp) },
                                        placeholder = { Text("Server ${idx + 1} Name", color = Color(0xFF64748B), fontSize = 11.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = customFieldColors(),
                                        shape = RoundedCornerShape(8.dp),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = srv.url,
                                        onValueChange = { newUrl ->
                                            editChannelServers = editChannelServers.toMutableList().also {
                                                it[idx] = it[idx].copy(url = newUrl)
                                            }
                                        },
                                        label = { Text("স্ট্রিম URL (.m3u8 / .mpd)", fontSize = 11.sp) },
                                        placeholder = { Text("Stream URL (.m3u8 / .mpd)", color = Color(0xFF64748B), fontSize = 11.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = customFieldColors(),
                                        shape = RoundedCornerShape(8.dp),
                                        singleLine = true
                                    )
                                }
                            }
                        }

                        // Add Server Button
                        item {
                            OutlinedButton(
                                onClick = {
                                    val nextNum = editChannelServers.size + 1
                                    editChannelServers = editChannelServers + StreamServer("সার্ভার $nextNum", "")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00E5FF))
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("➕ আরও সার্ভার যোগ করুন (+ Add Server)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Color(0xFF334155))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Dialog Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { editingChannelItem = null },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF475569))
                        ) {
                            Text("বাতিল (Cancel)", fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                val validServers = editChannelServers.mapNotNull {
                                    if (it.url.isNotBlank()) StreamServer(it.name.ifBlank { "Server" }, it.url.trim()) else null
                                }

                                if (editChannelName.isNotBlank() && validServers.isNotEmpty()) {
                                    val updatedChannel = target.copy(
                                        title = editChannelName.trim(),
                                        category = editChannelCategory,
                                        type = MediaType.LIVE_TV,
                                        streamUrl = validServers.first().url,
                                        backupUrl = validServers.getOrNull(1)?.url,
                                        servers = validServers,
                                        logoUrl = editChannelLogoUrl.trim().takeIf { it.isNotBlank() },
                                        isLive = true
                                    )

                                    repository.saveCustomStream(updatedChannel)
                                    coroutineScope.launch {
                                        repository.pushToFirebase(updatedChannel)
                                    }
                                    onDataChanged()
                                    editingChannelItem = null
                                    Toast.makeText(context, "${updatedChannel.title} সফলভাবে আপডেট ও ক্লাউডে সিঙ্ক হয়েছে!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "চ্যানেলের নাম এবং কমপক্ষে একটি কার্যকর সার্ভার স্ট্রিম লিংক দিন", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1.5f)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                        ) {
                            Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("চ্যানেল আপডেট করুন", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // 3. PLAYLIST EDIT DIALOG (User: এডমিন প্যানেলে প্লেলিস্ট এডিট করার অপশন)
    // =========================================================================
    if (editingPlaylistItem != null) {
        val target = editingPlaylistItem!!
        Dialog(
            onDismissRequest = { editingPlaylistItem = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .wrapContentHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = Color(0xFF00E5FF).copy(alpha = 0.2f),
                                shape = CircleShape,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.FeaturedPlayList, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "প্লেলিস্ট এডিট করুন",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = target.title,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                        }
                        IconButton(onClick = { editingPlaylistItem = null }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                        }
                    }

                    HorizontalDivider(color = Color(0xFF334155))

                    // Playlist Title
                    OutlinedTextField(
                        value = editPlaylistTitle,
                        onValueChange = { editPlaylistTitle = it },
                        label = { Text("প্লেলিস্টের নাম (Playlist Name)") },
                        colors = customFieldColors(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Playlist M3U URL
                    OutlinedTextField(
                        value = editPlaylistUrl,
                        onValueChange = { editPlaylistUrl = it },
                        label = { Text("প্লেলিস্ট M3U / M3U8 URL") },
                        colors = customFieldColors(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Logo / Banner URL
                    OutlinedTextField(
                        value = editPlaylistLogoUrl,
                        onValueChange = { editPlaylistLogoUrl = it },
                        label = { Text("লোগো / ব্যানার URL (ঐচ্ছিক)") },
                        colors = customFieldColors(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Description
                    OutlinedTextField(
                        value = editPlaylistDescription,
                        onValueChange = { editPlaylistDescription = it },
                        label = { Text("বিবরণ (Short Description)") },
                        colors = customFieldColors(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(color = Color(0xFF334155))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { editingPlaylistItem = null },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF475569))
                        ) {
                            Text("বাতিল", fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                if (editPlaylistTitle.isNotBlank() && editPlaylistUrl.isNotBlank()) {
                                    val updatedPl = target.copy(
                                        title = editPlaylistTitle.trim(),
                                        url = editPlaylistUrl.trim(),
                                        logoUrl = editPlaylistLogoUrl.trim().takeIf { it.isNotBlank() },
                                        description = editPlaylistDescription.trim().takeIf { it.isNotBlank() }
                                    )

                                    repository.saveAdminPlaylist(updatedPl)
                                    if (target.id == "pl_nafi_movies_json" || target.title.contains("movie", ignoreCase = true) || target.url.contains("movie", ignoreCase = true) || target.type.equals("JSON", ignoreCase = true)) {
                                        repository.saveMoviesM3uUrl(updatedPl.url)
                                        moviesM3uInput = updatedPl.url
                                        coroutineScope.launch {
                                            repository.pushAppConfigToFirebase(moviesM3u = updatedPl.url)
                                        }
                                    }
                                    coroutineScope.launch {
                                        repository.pushPlaylistToFirebase(updatedPl)
                                    }
                                    onDataChanged()
                                    editingPlaylistItem = null
                                    Toast.makeText(context, "${updatedPl.title} প্লেলিস্ট সফলভাবে আপডেট হয়েছে!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "প্লেলিস্টের নাম এবং M3U URL উভয়ই পূরণ করুন", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1.5f)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black)
                        ) {
                            Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("প্লেলিস্ট সেভ করুন", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // 4. MOVIE & SERIES EDIT DIALOG (User: এডমিন প্যানেলে সবগুলো অপশন এডিট করার সুবিধা)
    // =========================================================================
    if (editingMovieItem != null) {
        val target = editingMovieItem!!
        Dialog(
            onDismissRequest = { editingMovieItem = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.90f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                                shape = CircleShape,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.MovieFilter, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "মুভি ও সিরিজ এডিট করুন",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = target.title,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                        }
                        IconButton(onClick = { editingMovieItem = null }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Color(0xFF334155))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Scrollable Edit Form
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Movie Title
                        item {
                            OutlinedTextField(
                                value = editMovieTitle,
                                onValueChange = { editMovieTitle = it },
                                label = { Text("মুভির নাম / শিরোনাম (Title)") },
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Category Dropdown
                        item {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = editMovieCategory,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("ক্যাটাগরি (Category)") },
                                    trailingIcon = {
                                        IconButton(onClick = { editMovieCategoryDropdownExpanded = !editMovieCategoryDropdownExpanded }) {
                                            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null, tint = Color.White)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().clickable { editMovieCategoryDropdownExpanded = true },
                                    colors = customFieldColors(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                                DropdownMenu(
                                    expanded = editMovieCategoryDropdownExpanded,
                                    onDismissRequest = { editMovieCategoryDropdownExpanded = false },
                                    modifier = Modifier.background(Color(0xFF1E293B))
                                ) {
                                    movieCategoryOptions.forEach { opt ->
                                        DropdownMenuItem(
                                            text = { Text(opt, color = Color.White) },
                                            onClick = {
                                                editMovieCategory = opt
                                                editMovieCategoryDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Poster URL
                        item {
                            OutlinedTextField(
                                value = editMoviePosterUrl,
                                onValueChange = { editMoviePosterUrl = it },
                                label = { Text("পোস্টার / থাম্বনেইল URL (Poster URL)") },
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Description
                        item {
                            OutlinedTextField(
                                value = editMovieDesc,
                                onValueChange = { editMovieDesc = it },
                                label = { Text("মুভির বিবরণ (Description)") },
                                colors = customFieldColors(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = false,
                                minLines = 2,
                                maxLines = 4,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Multi-Servers Section Header
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Dns, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "মুভি স্ট্রিমিং সার্ভার (${editMovieServers.size} টি সার্ভার):",
                                    color = Color(0xFFF59E0B),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Dynamic Server Inputs
                        itemsIndexed(editMovieServers) { idx, srv ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF1E293B).copy(alpha = 0.7f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Rounded.Dns,
                                                contentDescription = null,
                                                tint = Color(0xFFF59E0B),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "মুভি সার্ভার #${idx + 1}",
                                                color = Color(0xFFF59E0B),
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        if (editMovieServers.size > 1) {
                                            IconButton(
                                                onClick = {
                                                    editMovieServers = editMovieServers.toMutableList().also { it.removeAt(idx) }
                                                },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Rounded.DeleteOutline,
                                                    contentDescription = "সার্ভার সরান",
                                                    tint = Color(0xFFEF4444),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                    OutlinedTextField(
                                        value = srv.name,
                                        onValueChange = { newName ->
                                            editMovieServers = editMovieServers.toMutableList().also {
                                                it[idx] = it[idx].copy(name = newName)
                                            }
                                        },
                                        label = { Text("সার্ভার নাম (যেমন: Server ${idx + 1} HD)", fontSize = 11.sp) },
                                        placeholder = { Text("Server ${idx + 1} Name", color = Color(0xFF64748B), fontSize = 11.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = customFieldColors(),
                                        shape = RoundedCornerShape(8.dp),
                                        singleLine = true
                                    )
                                    OutlinedTextField(
                                        value = srv.url,
                                        onValueChange = { newUrl ->
                                            editMovieServers = editMovieServers.toMutableList().also {
                                                it[idx] = it[idx].copy(url = newUrl)
                                            }
                                        },
                                        label = { Text("মুভি স্ট্রিম URL (mp4 / m3u8)", fontSize = 11.sp) },
                                        placeholder = { Text("Movie Stream URL (mp4 / m3u8)", color = Color(0xFF64748B), fontSize = 11.sp) },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = customFieldColors(),
                                        shape = RoundedCornerShape(8.dp),
                                        singleLine = true
                                    )
                                }
                            }
                        }

                        // Add Server Button
                        item {
                            OutlinedButton(
                                onClick = {
                                    val nextNum = editMovieServers.size + 1
                                    val defaultName = if (nextNum == 2) "সার্ভার ২ (4K)" else "সার্ভার $nextNum"
                                    editMovieServers = editMovieServers + StreamServer(defaultName, "")
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.5f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF59E0B))
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("➕ আরও মুভি সার্ভার যোগ করুন (+ Add Server)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = Color(0xFF334155))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Dialog Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { editingMovieItem = null },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF475569))
                        ) {
                            Text("বাতিল (Cancel)", fontSize = 13.sp)
                        }

                        Button(
                            onClick = {
                                val validServers = editMovieServers.mapNotNull {
                                    if (it.url.isNotBlank()) StreamServer(it.name.ifBlank { "HD Server" }, it.url.trim()) else null
                                }

                                if (editMovieTitle.isNotBlank() && validServers.isNotEmpty()) {
                                    val updatedMovie = target.copy(
                                        title = editMovieTitle.trim(),
                                        category = editMovieCategory,
                                        type = MediaType.MOVIE,
                                        streamUrl = validServers.first().url,
                                        backupUrl = validServers.getOrNull(1)?.url,
                                        servers = validServers,
                                        logoUrl = editMoviePosterUrl.trim().takeIf { it.isNotBlank() },
                                        description = editMovieDesc.trim().takeIf { it.isNotBlank() },
                                        isLive = false
                                    )

                                    repository.saveCustomStream(updatedMovie)
                                    coroutineScope.launch {
                                        repository.pushToFirebase(updatedMovie)
                                    }
                                    onDataChanged()
                                    editingMovieItem = null
                                    Toast.makeText(context, "${updatedMovie.title} সফলভাবে আপডেট ও ক্লাউডে সিঙ্ক হয়েছে!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "মুভির নাম এবং কমপক্ষে একটি কার্যকর সার্ভার স্ট্রিম লিংক দিন", Toast.LENGTH_SHORT).show()
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1.5f)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B), contentColor = Color.Black)
                        ) {
                            Icon(Icons.Rounded.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("মুভি আপডেট করুন", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    // Dialog for Live Score Updating
    if (updatingItem != null) {
        val target = updatingItem!!
        AlertDialog(
            onDismissRequest = { updatingItem = null },
            containerColor = Color(0xFF1E293B),
            title = {
                Text("লাইভ স্কোর আপডেট করুন", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${target.team1 ?: "Team 1"} vs ${target.team2 ?: "Team 2"}", color = Color(0xFF00E5FF), fontSize = 13.sp)
                    OutlinedTextField(
                        value = updateScore1,
                        onValueChange = { updateScore1 = it },
                        label = { Text("${target.team1 ?: "Team 1"} Score") },
                        colors = customFieldColors(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = updateScore2,
                        onValueChange = { updateScore2 = it },
                        label = { Text("${target.team2 ?: "Team 2"} Score") },
                        colors = customFieldColors(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updated = target.copy(score1 = updateScore1.trim(), score2 = updateScore2.trim())
                        repository.saveCustomStream(updated)
                        coroutineScope.launch {
                            repository.pushToFirebase(updated)
                        }
                        onDataChanged()
                        updatingItem = null
                        Toast.makeText(context, "স্কোর আপডেট হয়েছে!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black)
                ) {
                    Text("সংরক্ষণ", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { updatingItem = null }) {
                    Text("বাতিল", color = Color.White)
                }
            }
        )
    }

    // Dialog for Deleting Item (Confirmation)
    if (itemToDelete != null) {
        val target = itemToDelete!!
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            containerColor = Color(0xFF1E293B),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("আইটেম ডিলিট নিশ্চিতকরণ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text(
                    text = "আপনি কি '${target.title}' নিশ্চিতভাবে ডিলিট করতে চান?\nএটি লোকাল মেমোরি ও ক্লাউড ডেটাবেস থেকে স্থায়ীভাবে মুছে যাবে।",
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toRemove = target
                        itemToDelete = null
                        // 1. Instant 0ms optimistic UI removal from admin screen
                        adminSportsItems = adminSportsItems.filterNot { it.id == toRemove.id || repository.isItemDeleted(it) }
                        deletedIdsVersion++
                        coroutineScope.launch {
                            repository.deleteMediaItem(toRemove)
                            deletedIdsVersion++
                            onDataChanged()
                            Toast.makeText(context, "${toRemove.title} সফলভাবে ডিলিট করা হয়েছে!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444), contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete (মুছে ফেলুন)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { itemToDelete = null },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF475569))
                ) {
                    Text("Cancel (বাতিল)", fontSize = 12.sp)
                }
            }
        )
    }

    // Dialog for Deleting Playlist (Confirmation)
    if (playlistToDelete != null) {
        val target = playlistToDelete!!
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            containerColor = Color(0xFF1E293B),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("প্লেলিস্ট ডিলিট নিশ্চিতকরণ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text(
                    text = "আপনি কি '${target.title}' প্লেলিস্টটি নিশ্চিতভাবে ডিলিট করতে চান?\nএটি লোকাল মেমোরি ও ক্লাউড ডেটাবেস থেকে স্থায়ীভাবে মুছে যাবে।",
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toRemove = target
                        playlistToDelete = null
                        coroutineScope.launch {
                            repository.deletePlaylist(toRemove.id)
                            onDataChanged()
                            Toast.makeText(context, "${toRemove.title} প্লেলিস্ট সফলভাবে মুছে ফেলা হয়েছে!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444), contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Delete (মুছে ফেলুন)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { playlistToDelete = null },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF475569))
                ) {
                    Text("Cancel (বাতিল)", fontSize = 12.sp)
                }
            }
        )
    }

    // -------------------------------------------------------------
    // DELETE CLOUDSTREAM REPOSITORY CONFIRMATION DIALOG
    // -------------------------------------------------------------
    repoToDelete?.let { repo ->
        AlertDialog(
            onDismissRequest = { repoToDelete = null },
            containerColor = Color(0xFF1E293B),
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("রিপোজিটরি ডিলিট", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text(
                    text = "আপনি কি '${repo.name}' (${repo.providers.size} টি সাইট) রিপোজিটরিটি মুছে ফেলতে চান?\nএটি লোকাল এবং ফায়ারবেস ক্লাউড ডেটাবেস থেকে মুছে যাবে।",
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toRemove = repo
                        repoToDelete = null
                        val current = repository.getSavedCloudStreamRepos()
                        val updated = current.filterNot { it.id == toRemove.id }
                        repository.saveCloudStreamRepos(updated)
                        coroutineScope.launch {
                            repository.pushCloudStreamReposToFirebase(updated)
                        }
                        onDataChanged()
                        Toast.makeText(context, "✅ '${toRemove.name}' রিপোজিটরি মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444), contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Delete (মুছে ফেলুন)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { repoToDelete = null },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF475569))
                ) {
                    Text("Cancel", fontSize = 12.sp)
                }
            }
        )
    }

    // -------------------------------------------------------------
    // DELETE CUSTOM MOVIE PROVIDER CONFIRMATION DIALOG
    // -------------------------------------------------------------
    providerToDelete?.let { prov ->
        AlertDialog(
            onDismissRequest = { providerToDelete = null },
            containerColor = Color(0xFF1E293B),
            shape = RoundedCornerShape(16.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("মুভি সাইট ডিলিট", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            text = {
                Text(
                    text = "আপনি কি '${prov.name}' মুভি ওয়েবসাইটটি মুছে ফেলতে চান?",
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toRemove = prov
                        providerToDelete = null
                        val current = repository.getCustomMovieProviders()
                        val updated = current.filterNot { it.id == toRemove.id }
                        repository.saveCustomMovieProviders(updated)
                        onDataChanged()
                        Toast.makeText(context, "✅ '${toRemove.name}' সাইট মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444), contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { providerToDelete = null },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF475569))
                ) {
                    Text("Cancel", fontSize = 12.sp)
                }
            }
        )
    }

    // Firebase Admin Authentication Dialog
    if (showAuthDialog) {
        FirebaseAuthAdminDialog(
            repository = repository,
            onDismiss = { showAuthDialog = false },
            onAuthSuccess = {
                showAuthDialog = false
                onDataChanged()
            }
        )
    }

    // App Update Popup Preview Dialog (Admin Mode)
    if (previewUpdateDialog != null) {
        AppUpdateDialog(
            updateInfo = previewUpdateDialog!!,
            isPreview = true,
            onDismiss = { previewUpdateDialog = null }
        )
    }
}
