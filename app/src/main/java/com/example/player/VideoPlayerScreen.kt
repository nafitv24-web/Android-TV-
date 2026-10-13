package com.example.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.util.Rational
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.foundation.basicMarquee
import coil.compose.AsyncImage
import com.example.R
import com.example.model.MediaItem as AppMediaItem
import com.example.model.MediaType
import com.example.model.StreamServer
import com.example.model.SeasonInfo
import com.example.model.EpisodeItem
import com.example.ui.SeriesDetailsDialog
import com.example.util.MovieDownloadManager
import com.example.util.DownloadState
import com.example.ui.components.BreakingNewsTickerBar
import com.example.data.MediaRepository
import kotlinx.coroutines.delay

enum class MxDragType { NONE, VERTICAL_LEFT, VERTICAL_RIGHT, HORIZONTAL }

data class VideoQualityOption(
    val label: String,
    val height: Int,
    val bitrate: Int = 0,
    val isAuto: Boolean = false
)

data class AudioTrackOption(
    val id: String = "",
    val language: String = "",
    val displayName: String = "",
    val groupIndex: Int = -1,
    val trackIndex: Int = -1
)

data class SubtitleTrackOption(
    val id: String = "",
    val language: String = "",
    val displayName: String = "",
    val isOff: Boolean = false,
    val groupIndex: Int = -1,
    val trackIndex: Int = -1
)

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    mediaItem: AppMediaItem,
    playlist: List<AppMediaItem> = emptyList(),
    isTvMode: Boolean = false,
    marqueeTickerText: String? = null,
    onSelectMedia: (AppMediaItem) -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val configuration = LocalConfiguration.current
    val isScreenLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val activeTickerText = remember(marqueeTickerText) {
        marqueeTickerText ?: MediaRepository(context).getMarqueeTickerText()
    }

    var currentMedia by remember(mediaItem) { mutableStateOf(mediaItem) }
    val servers = remember(currentMedia) { currentMedia.getAllServers() }
    val initialServerIdx = remember(currentMedia) {
        val target = currentMedia.streamUrl.trim()
        if (target.isBlank()) 0
        else {
            val found = servers.indexOfFirst {
                it.url.trim().equals(target, ignoreCase = true) ||
                (it.url.contains('?') && it.url.substringBefore('?').equals(target.substringBefore('?'), ignoreCase = true)) ||
                (it.url.contains('|') && it.url.substringBefore('|').equals(target.substringBefore('|'), ignoreCase = true))
            }
            if (found >= 0) found else 0
        }
    }
    var selectedServerIndex by remember(currentMedia) { mutableIntStateOf(initialServerIdx) }
    var currentUrl by remember(currentMedia) {
        val target = if (currentMedia.streamUrl.isNotBlank()) {
            currentMedia.streamUrl
        } else {
            servers.getOrNull(selectedServerIndex)?.url.orEmpty()
        }
        mutableStateOf(target)
    }

    // Keep internal player state synced with parent mediaItem updates without recreating screen
    LaunchedEffect(mediaItem.id, mediaItem.streamUrl) {
        if (currentMedia.id != mediaItem.id || currentMedia.streamUrl != mediaItem.streamUrl) {
            currentMedia = mediaItem
            selectedServerIndex = 0
            val targetServers = mediaItem.getAllServers()
            currentUrl = targetServers.firstOrNull()?.url ?: mediaItem.streamUrl
        }
    }

    // Configure lenient SSL globally for live IPTV / HLS streams (matching VLC and Televizo behavior)
    LaunchedEffect(Unit) {
        try {
            val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(object : javax.net.ssl.X509TrustManager {
                override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
            })
            val sslContext = javax.net.ssl.SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
            javax.net.ssl.HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
            javax.net.ssl.HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
        } catch (_: Exception) {}
    }

    val isTvOrLiveChannel = currentMedia.isLive || currentMedia.type == MediaType.LIVE_TV || currentMedia.type == MediaType.LIVE_EVENT
    val isWebEmbedUrl = remember(currentUrl) {
        !isTvOrLiveChannel && StreamExtractor.isEmbedUrl(currentUrl)
    }
    var forceWebEngine by remember(currentMedia.id, currentUrl) { mutableStateOf(false) }
    val useWebPlayer = (!isTvOrLiveChannel && (isWebEmbedUrl || forceWebEngine))

    // Automatically resolve any 2embed/vidsrc/embed streams to native ExoPlayer URLs in the background
    LaunchedEffect(currentUrl) {
        if (StreamExtractor.isEmbedUrl(currentUrl)) {
            val directStream = StreamExtractor.extractDirectStream(currentUrl)
            if (directStream != null && directStream.streamUrl.isNotBlank()) {
                currentUrl = directStream.streamUrl
                forceWebEngine = false
            }
        }
    }

    // Record user live presence & streaming activity
    LaunchedEffect(currentMedia.id, currentMedia.title) {
        try {
            val repo = MediaRepository(context)
            repo.recordUserPresence(currentActivity = "${currentMedia.title} দেখছেন")
        } catch (_: Exception) {}
    }

    // Auto-detect clean movie title from Content-Disposition header for MovieLinkBD and direct stream links
    LaunchedEffect(currentUrl) {
        val rawTitle = currentMedia.title.trim()
        if (rawTitle.isBlank() || rawTitle.equals("Direct Stream", ignoreCase = true) ||
            rawTitle.equals("MovieLinkBD Video", ignoreCase = true) || rawTitle.startsWith("direct_")) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                        .followRedirects(true)
                        .build()
                    val host = try { android.net.Uri.parse(currentUrl).host } catch (_: Exception) { "fast.movielinkbd.app" }
                    val req = okhttp3.Request.Builder()
                        .url(currentUrl)
                        .head()
                        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                        .header("Referer", "https://$host/")
                        .header("Accept", "*/*")
                        .build()
                    val resp = client.newCall(req).execute()
                    val disp = resp.header("Content-Disposition")
                    if (!disp.isNullOrBlank()) {
                        val match = Regex("filename\\*?=['\"]?(?:UTF-8'')?([^;'\"]+)", RegexOption.IGNORE_CASE).find(disp)
                        val rawName = match?.groupValues?.getOrNull(1)
                        if (!rawName.isNullOrBlank()) {
                            val decoded = try { java.net.URLDecoder.decode(rawName, "UTF-8") } catch (_: Exception) { rawName }
                            val cleanName = decoded
                                .replace(Regex("movielinkbd\\.com\\s*-\\s*", RegexOption.IGNORE_CASE), "")
                                .replace(Regex("\\.(?:mkv|mp4|webm|avi)$", RegexOption.IGNORE_CASE), "")
                                .replace(".", " ")
                                .trim()
                            if (cleanName.isNotBlank()) {
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                    currentMedia = currentMedia.copy(title = cleanName)
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    var isFullscreen by rememberSaveable { mutableStateOf(isScreenLandscape || isTvMode) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var isActuallyBuffering by remember { mutableStateOf(true) }
    var hasStartedPlaying by remember(currentMedia.id, currentUrl) { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var currentVideoResolution by remember { mutableStateOf<String?>(null) }
    var playerRetryKey by remember(currentUrl) { mutableIntStateOf(0) }

    // MX Player Gestures: Volume & Brightness & Seeking State
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager }
    val maxAudioVolume = remember(audioManager) { audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15 }

    var currentVolumeFraction by remember {
        val initialVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 7
        val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
        mutableFloatStateOf((initialVol.toFloat() / maxVol.toFloat()).coerceIn(0f, 1f))
    }
    var currentBrightnessFraction by remember {
        val initialBright = activity?.window?.attributes?.screenBrightness ?: 0.5f
        mutableFloatStateOf(if (initialBright < 0f) 0.5f else initialBright.coerceIn(0.01f, 1.0f))
    }

    var gestureVolumePercent by remember { mutableIntStateOf((currentVolumeFraction * 100).toInt()) }
    var gestureBrightnessPercent by remember { mutableIntStateOf((currentBrightnessFraction * 100).toInt()) }
    var isVolumeGestureActive by remember { mutableStateOf(false) }
    var isBrightnessGestureActive by remember { mutableStateOf(false) }
    var isSeekGestureActive by remember { mutableStateOf(false) }
    var volumeGestureKey by remember { mutableLongStateOf(0L) }
    var brightnessGestureKey by remember { mutableLongStateOf(0L) }
    var seekGestureKey by remember { mutableLongStateOf(0L) }
    var seekGestureOffsetSec by remember { mutableIntStateOf(0) }
    var seekGestureTargetMs by remember { mutableLongStateOf(0L) }
    var doubleTapSeekLeft by remember { mutableStateOf(false) }
    var doubleTapSeekRight by remember { mutableStateOf(false) }

    // Video Quality, Audio Track & Subtitles (Screenshot 2: HD 720p, Hindi, English)
    var availableVideoQualities by remember {
        mutableStateOf(
            listOf(
                VideoQualityOption("Auto (Adaptive)", -1, isAuto = true),
                VideoQualityOption("1080p FHD", 1080),
                VideoQualityOption("720p HD", 720),
                VideoQualityOption("480p SD", 480),
                VideoQualityOption("360p Low", 360)
            )
        )
    }
    var selectedVideoQuality by remember { mutableStateOf(availableVideoQualities.first()) }
    var showQualityDialog by remember { mutableStateOf(false) }

    var availableAudioTracks by remember {
        mutableStateOf<List<AudioTrackOption>>(emptyList())
    }
    var selectedAudioTrack by remember { mutableStateOf<AudioTrackOption?>(null) }
    var showAudioDialog by remember { mutableStateOf(false) }

    var availableSubtitles by remember {
        mutableStateOf<List<SubtitleTrackOption>>(
            listOf(SubtitleTrackOption(id = "off", language = "", displayName = "অফ (Off)", isOff = true))
        )
    }
    var selectedSubtitle by remember { mutableStateOf<SubtitleTrackOption?>(null) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showEpisodesSheet by remember { mutableStateOf(false) }

    val playerRepository = remember(context) { MediaRepository(context) }
    var isAutoSwitchOnFailureEnabled by remember {
        mutableStateOf(playerRepository.isAutoSwitchChannelOnFailure())
    }
    var showPlayerSettingsDialog by remember { mutableStateOf(false) }

    var pendingServerSwitchIndex by remember { mutableIntStateOf(-1) }
    var pendingChannelSwitchDelta by remember { mutableIntStateOf(0) }

    // Keep server index, currentUrl, and media in sync whenever user picks a specific server or media item
    LaunchedEffect(mediaItem) {
        currentMedia = mediaItem
        val currentServers = mediaItem.getAllServers()
        val target = mediaItem.streamUrl.trim()
        val foundIdx = if (target.isNotBlank()) {
            val idx = currentServers.indexOfFirst {
                it.url.trim().equals(target, ignoreCase = true) ||
                (it.url.contains('?') && it.url.substringBefore('?').equals(target.substringBefore('?'), ignoreCase = true)) ||
                (it.url.contains('|') && it.url.substringBefore('|').equals(target.substringBefore('|'), ignoreCase = true))
            }
            if (idx >= 0) idx else 0
        } else 0
        selectedServerIndex = foundIdx
        currentUrl = if (target.isNotBlank()) target else currentServers.getOrNull(foundIdx)?.url.orEmpty()
        playerRetryKey = 0
        errorMessage = null
    }

    // Automatic season & episode metadata fetcher for Series
    LaunchedEffect(currentMedia.id) {
        if (currentMedia.isSeries && currentMedia.episodes.isEmpty()) {
            try {
                val detailed = MediaRepository(context).fetchSeriesSeasonsAndEpisodes(currentMedia)
                if (detailed.episodes.isNotEmpty() || detailed.seasons.isNotEmpty()) {
                    currentMedia = currentMedia.copy(
                        episodes = detailed.episodes,
                        seasons = detailed.seasons
                    )
                }
            } catch (_: Exception) {}
        }
    }

    // Auto-dismiss Gesture HUD Overlays after inactivity
    LaunchedEffect(volumeGestureKey) {
        if (volumeGestureKey > 0L && isVolumeGestureActive) {
            delay(1500)
            isVolumeGestureActive = false
        }
    }
    LaunchedEffect(brightnessGestureKey) {
        if (brightnessGestureKey > 0L && isBrightnessGestureActive) {
            delay(1500)
            isBrightnessGestureActive = false
        }
    }
    LaunchedEffect(seekGestureKey) {
        if (seekGestureKey > 0L && isSeekGestureActive) {
            delay(1500)
            isSeekGestureActive = false
        }
    }
    LaunchedEffect(doubleTapSeekLeft) {
        if (doubleTapSeekLeft) {
            delay(750)
            doubleTapSeekLeft = false
        }
    }
    LaunchedEffect(doubleTapSeekRight) {
        if (doubleTapSeekRight) {
            delay(750)
            doubleTapSeekRight = false
        }
    }

    // Shared Bandwidth Meter to accurately estimate network throughput and dynamically adapt video quality
    val bandwidthMeter = remember {
        androidx.media3.exoplayer.upstream.DefaultBandwidthMeter.Builder(context)
            .setResetOnNetworkTypeChange(false)
            .setInitialBitrateEstimate(1_500_000L)
            .build()
    }

    // Smooth debounce for buffering overlay so micro network fluctuations don't flash intrusive cards over active video
    LaunchedEffect(isBuffering, hasStartedPlaying) {
        if (isBuffering) {
            val waitDelay = if (hasStartedPlaying) 800L else 150L
            delay(waitDelay)
            if (isBuffering) {
                isActuallyBuffering = true
            }
        } else {
            isActuallyBuffering = false
        }
    }

    var showControls by remember { mutableStateOf(true) }
    var controlsInteractionKey by remember { mutableLongStateOf(0L) }
    var isMuted by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var showQuickChannelDrawer by remember { mutableStateOf(false) }

    var isInPipMode by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity != null) {
                activity.isInPictureInPictureMode
            } else false
        )
    }

    DisposableEffect(activity) {
        if (activity is androidx.activity.ComponentActivity) {
            val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
                isInPipMode = info.isInPictureInPictureMode
                if (info.isInPictureInPictureMode) {
                    showControls = false
                    showQuickChannelDrawer = false
                }
            }
            activity.addOnPictureInPictureModeChangedListener(listener)
            onDispose {
                activity.removeOnPictureInPictureModeChangedListener(listener)
            }
        } else {
            onDispose {}
        }
    }

    fun enterPictureInPictureMode() {
        if (activity != null) {
            try {
                showControls = false
                showQuickChannelDrawer = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val aspectRatio = Rational(16, 9)
                    val params = PictureInPictureParams.Builder()
                        .setAspectRatio(aspectRatio)
                        .build()
                    activity.enterPictureInPictureMode(params)
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    @Suppress("DEPRECATION")
                    activity.enterPictureInPictureMode()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "PiP মোড এই ডিভাইসে সমর্থিত নয়", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(isPlaying) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && activity != null) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .setAutoEnterEnabled(isPlaying)
                    .build()
                activity.setPictureInPictureParams(params)
            } catch (_: Exception) {}
        }
    }

    // TV Remote OSD & Number Input
    var showChannelOsd by remember { mutableStateOf(false) }
    var channelOsdKey by remember { mutableLongStateOf(0L) }
    var remoteNumberBuffer by remember { mutableStateOf("") }
    var remoteNumberKey by remember { mutableLongStateOf(0L) }

    // Auto-hide Channel OSD banner
    LaunchedEffect(channelOsdKey) {
        if (channelOsdKey > 0L) {
            showChannelOsd = true
            delay(3500)
            showChannelOsd = false
        }
    }

    // Auto-commit remote channel number input (e.g. user types "5" or "12")
    LaunchedEffect(remoteNumberKey) {
        if (remoteNumberKey > 0L && remoteNumberBuffer.isNotEmpty()) {
            delay(1200)
            val channelNum = remoteNumberBuffer.toIntOrNull()
            if (channelNum != null && playlist.isNotEmpty()) {
                val targetIdx = (channelNum - 1).coerceIn(0, playlist.size - 1)
                val nextItem = playlist[targetIdx]
                isBuffering = true
                currentMedia = nextItem
                selectedServerIndex = 0
                val newServers = nextItem.getAllServers()
                currentUrl = newServers.firstOrNull()?.url ?: nextItem.streamUrl
                errorMessage = null
                onSelectMedia(nextItem)
                channelOsdKey = System.currentTimeMillis()
            }
            remoteNumberBuffer = ""
        }
    }

    // Immediately trigger buffering state whenever channel or server URL changes
    LaunchedEffect(currentMedia.id, currentUrl) {
        hasStartedPlaying = false
        isBuffering = true
        isActuallyBuffering = true
        errorMessage = null
        currentPositionMs = 0L
        durationMs = 0L
        sliderPosition = 0f
        isDraggingSlider = false
    }

    fun toggleFullscreen() {
        val targetLandscape = !isFullscreen
        isFullscreen = targetLandscape
        activity?.requestedOrientation = if (targetLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    // Intercept system/mobile back press to safely exit player and return to previous list
    BackHandler {
        if (showQuickChannelDrawer) {
            showQuickChannelDrawer = false
        } else if (isFullscreen && !isTvMode) {
            toggleFullscreen()
        } else {
            onBack()
        }
    }

    // Synchronize fullscreen state with device orientation if physically rotated or in TV Mode
    LaunchedEffect(isScreenLandscape, isTvMode) {
        if ((isScreenLandscape || isTvMode) && !isFullscreen) {
            isFullscreen = true
        }
        if (isTvMode) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    // Reset orientation only when exiting the player screen entirely
    DisposableEffect(isTvMode) {
        onDispose {
            if (isTvMode) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }

    // Hide System Bars (Status Bar & Navigation Bar) completely in fullscreen mode
    DisposableEffect(isFullscreen, activity) {
        val window = activity?.window
        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, !isFullscreen)
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (isFullscreen) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.hide(WindowInsetsCompat.Type.statusBars())
                insetsController.hide(WindowInsetsCompat.Type.navigationBars())
                insetsController.hide(WindowInsetsCompat.Type.captionBar())
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    window.attributes.layoutInDisplayCutoutMode =
                        android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                )
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
        onDispose {
            val w = activity?.window
            if (w != null) {
                WindowCompat.setDecorFitsSystemWindows(w, true)
                val insetsController = WindowCompat.getInsetsController(w, w.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
                w.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
                @Suppress("DEPRECATION")
                w.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
    }

    // Re-enforce system bar hiding when fullscreen or controls change
    LaunchedEffect(isFullscreen, showControls) {
        if (isFullscreen) {
            val window = activity?.window
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.hide(WindowInsetsCompat.Type.statusBars())
                insetsController.hide(WindowInsetsCompat.Type.navigationBars())
                insetsController.hide(WindowInsetsCompat.Type.captionBar())
            }
        }
    }

    // Auto hide controls after 6 seconds of inactivity, reset on user remote interaction
    LaunchedEffect(showControls, isPlaying, controlsInteractionKey) {
        if (showControls && isPlaying) {
            delay(6000)
            showControls = false
        }
    }

    fun getCurrentEpisodeIndex(): Int {
        val eps = currentMedia.episodes
        if (eps.isEmpty()) return -1
        var idx = eps.indexOfFirst {
            it.id.isNotBlank() && (it.id == currentMedia.id || "xtream_ep_${it.id}" == currentMedia.id)
        }
        if (idx == -1 && currentMedia.currentSeasonNum > 0 && currentMedia.currentEpisodeNum > 0) {
            idx = eps.indexOfFirst {
                it.seasonNum == currentMedia.currentSeasonNum && it.episodeNum == currentMedia.currentEpisodeNum
            }
        }
        if (idx == -1 && currentMedia.streamUrl.isNotBlank()) {
            idx = eps.indexOfFirst { it.streamUrl.trim().equals(currentMedia.streamUrl.trim(), ignoreCase = true) }
        }
        return idx
    }

    fun playNextEpisode(): Boolean {
        val eps = currentMedia.episodes
        if (eps.isEmpty()) return false
        val currentIdx = getCurrentEpisodeIndex()
        val nextIdx = if (currentIdx != -1) currentIdx + 1 else 0
        if (nextIdx < eps.size) {
            val nextEp = eps[nextIdx]
            val epMedia = nextEp.toMediaItem(currentMedia)
            isBuffering = true
            isActuallyBuffering = true
            hasStartedPlaying = false
            durationMs = 0L
            currentPositionMs = 0L
            currentMedia = epMedia
            selectedServerIndex = 0
            currentUrl = epMedia.streamUrl
            errorMessage = null
            onSelectMedia(epMedia)
            channelOsdKey = System.currentTimeMillis()
            android.widget.Toast.makeText(context, "পরবর্তী পর্ব: ${epMedia.title}", android.widget.Toast.LENGTH_SHORT).show()
            return true
        } else {
            android.widget.Toast.makeText(context, "এই সিরিজের সব পর্ব শেষ হয়েছে", android.widget.Toast.LENGTH_SHORT).show()
            return false
        }
    }

    fun playPrevEpisode(): Boolean {
        val eps = currentMedia.episodes
        if (eps.isEmpty()) return false
        val currentIdx = getCurrentEpisodeIndex()
        val prevIdx = if (currentIdx > 0) currentIdx - 1 else 0
        if (prevIdx != currentIdx && prevIdx >= 0) {
            val prevEp = eps[prevIdx]
            val epMedia = prevEp.toMediaItem(currentMedia)
            isBuffering = true
            isActuallyBuffering = true
            hasStartedPlaying = false
            durationMs = 0L
            currentPositionMs = 0L
            currentMedia = epMedia
            selectedServerIndex = 0
            currentUrl = epMedia.streamUrl
            errorMessage = null
            onSelectMedia(epMedia)
            channelOsdKey = System.currentTimeMillis()
            android.widget.Toast.makeText(context, "পূর্ববর্তী পর্ব: ${epMedia.title}", android.widget.Toast.LENGTH_SHORT).show()
            return true
        }
        return false
    }

    // Setup ExoPlayer instance with custom http data source, headers and dynamic pipe parsing
    val exoPlayer = remember(currentUrl, currentMedia, playerRetryKey) {
        val streamInfo = com.example.util.DrmHelper.extractStreamInfo(
            rawUrl = currentUrl,
            itemScheme = currentMedia.drmScheme,
            itemLicenseUrl = currentMedia.drmLicenseUrl,
            itemLicenseKey = currentMedia.drmLicenseKey,
            itemHeaders = currentMedia.drmHeaders,
            itemManifestType = currentMedia.manifestType
        )
        // Clean URL: normalize duplicate slashes in path ONLY (never touch query string with tokens/base64)
        val rawClean = streamInfo.cleanUrl.let { url ->
            val queryIdx = url.indexOf('?')
            if (queryIdx != -1) {
                val pathPart = url.substring(0, queryIdx)
                val queryPart = url.substring(queryIdx)
                val scheme = if (pathPart.startsWith("https://", ignoreCase = true)) "https://" else if (pathPart.startsWith("http://", ignoreCase = true)) "http://" else ""
                val rest = if (scheme.isNotEmpty()) pathPart.substring(scheme.length) else pathPart
                val cleanPath = scheme + rest.replace(Regex("/+"), "/")
                cleanPath + queryPart
            } else {
                val scheme = if (url.startsWith("https://", ignoreCase = true)) "https://" else if (url.startsWith("http://", ignoreCase = true)) "http://" else ""
                val rest = if (scheme.isNotEmpty()) url.substring(scheme.length) else url
                scheme + rest.replace(Regex("/+"), "/")
            }
        }
        val isRgkkw = rawClean.contains("rgkkw.live", ignoreCase = true)

        // Only convert specifically rgkkw.live live channels from .m3u8 to .ts on the path component (rgkkw live streams serve .ts)
        // NEVER touch movies (/movie/), series (/series/), .mp4, .mkv, or standard HLS playlists!
        val finalCleanUrl = if (isRgkkw) {
            val pathBeforeQuery = rawClean.substringBefore('?')
            val queryPart = if (rawClean.contains('?')) "?" + rawClean.substringAfter('?') else ""
            if (pathBeforeQuery.contains("/live/", ignoreCase = true) && pathBeforeQuery.endsWith(".m3u8", ignoreCase = true)) {
                pathBeforeQuery.substringBeforeLast(".m3u8") + ".ts" + queryPart
            } else {
                rawClean
            }
        } else {
            rawClean
        }
        val drmConfig = streamInfo.drmConfig

        var extractedUa: String? = currentMedia.userAgent
        var extractedReferer: String? = currentMedia.referrer
        var extractedOrigin: String? = currentMedia.origin
        var extractedCookie: String? = currentMedia.cookie
        val dynamicHeaders = mutableMapOf<String, String>()

        // Apply headers parsed from URL pipe/query syntax
        streamInfo.headers.forEach { (k, v) ->
            when {
                k.equals("User-Agent", ignoreCase = true) || k.equals("http-user-agent", ignoreCase = true) -> extractedUa = v
                k.equals("Referer", ignoreCase = true) || k.equals("Referrer", ignoreCase = true) || k.equals("http-referrer", ignoreCase = true) || k.equals("http-referer", ignoreCase = true) -> extractedReferer = v
                k.equals("Origin", ignoreCase = true) || k.equals("http-origin", ignoreCase = true) -> extractedOrigin = v
                k.equals("Cookie", ignoreCase = true) || k.equals("http-cookie", ignoreCase = true) -> extractedCookie = v
                else -> dynamicHeaders[k] = v
            }
        }

        // Apply custom headers from MediaItem
        currentMedia.customHeaders?.let { dynamicHeaders.putAll(it) }

        // Domain-specific smart headers (Toffee, Bioscope, TSports, Tapmad, Hakuna, etc.)
        val isToffee = finalCleanUrl.contains("toffeelive.com", ignoreCase = true) ||
                finalCleanUrl.contains("toffee", ignoreCase = true) ||
                finalCleanUrl.contains("bldcmprod-cdn", ignoreCase = true) ||
                currentMedia.category.contains("toffee", ignoreCase = true)

        if (isToffee) {
            if (extractedUa.isNullOrBlank()) extractedUa = "Toffee (Linux;Android 14)"
            if (extractedReferer.isNullOrBlank()) extractedReferer = "https://toffeelive.com/"
            if (extractedOrigin.isNullOrBlank()) extractedOrigin = "https://toffeelive.com"
        }

        val isHakuna = finalCleanUrl.contains("hakunaymatata", ignoreCase = true) ||
                finalCleanUrl.contains("sacdn", ignoreCase = true)

        if (isHakuna) {
            if (extractedReferer.isNullOrBlank()) extractedReferer = "https://hakunaymatata.com/"
            if (extractedOrigin.isNullOrBlank()) extractedOrigin = "https://hakunaymatata.com"
        }

        // Tapmad / Akamai smart headers
        val isTapmad = finalCleanUrl.contains("tapmad", ignoreCase = true) ||
                finalCleanUrl.contains("akamaized.net", ignoreCase = true) ||
                currentMedia.category.contains("tapmad", ignoreCase = true)

        if (isTapmad) {
            // CRITICAL: Network Stream (Video) Player & native player compatibility:
            // Akamai CDN blocks requests containing web browser headers (Referer/Origin: tapmad.com).
            // Native Android players (Network Stream Player, VLC, ExoPlayer) play Akamai streams with native UA and NO Referer/Origin!
            extractedReferer = null
            extractedOrigin = null
            if (extractedUa.isNullOrBlank()) {
                extractedUa = "NetworkStreamPlayer/1.0 (Linux; Android 14; Mobile)"
            }
        }

        // akr4m / bdtv IPTV restream server smart headers & compatibility
        val isAkr4m = finalCleanUrl.contains("akr4m.com", ignoreCase = true) ||
                finalCleanUrl.contains("/bdtv/", ignoreCase = true)

        if (isAkr4m) {
            if (extractedUa.isNullOrBlank()) extractedUa = "VLC/3.0.18 LibVLC/3.0.18"
        } else if (isRgkkw) {
            if (extractedUa.isNullOrBlank()) extractedUa = "IPTVSmartersPro"
        }

        val isMovieLinkBd = finalCleanUrl.contains("movielinkbd", ignoreCase = true) ||
                finalCleanUrl.contains("mlbd", ignoreCase = true) ||
                (finalCleanUrl.contains("/media/", ignoreCase = true) && finalCleanUrl.contains("token=", ignoreCase = true))

        val pathBeforeQuery = finalCleanUrl.substringBefore('?').lowercase()
        val isM3u8Pattern = pathBeforeQuery.endsWith(".m3u8") ||
                finalCleanUrl.contains(".m3u8", ignoreCase = true) ||
                finalCleanUrl.contains("/hls/", ignoreCase = true) ||
                isToffee

        val isMovieFormat = pathBeforeQuery.endsWith(".mp4") ||
                pathBeforeQuery.endsWith(".mkv") ||
                pathBeforeQuery.endsWith(".avi") ||
                pathBeforeQuery.endsWith(".webm") ||
                pathBeforeQuery.endsWith(".mov") ||
                pathBeforeQuery.endsWith(".flv") ||
                finalCleanUrl.contains("/movie/", ignoreCase = true) ||
                finalCleanUrl.contains("/series/", ignoreCase = true) ||
                currentMedia.type == MediaType.MOVIE ||
                currentMedia.type == MediaType.SERIES ||
                currentMedia.isSeries ||
                isMovieLinkBd

        val isXtreamStream = finalCleanUrl.contains("/live/", ignoreCase = true) ||
                finalCleanUrl.contains("/movie/", ignoreCase = true) ||
                finalCleanUrl.contains("/series/", ignoreCase = true) ||
                finalCleanUrl.contains("fixtv123", ignoreCase = true) ||
                finalCleanUrl.contains("rgkkw", ignoreCase = true) ||
                finalCleanUrl.contains("growthtry", ignoreCase = true) ||
                finalCleanUrl.contains("my-king", ignoreCase = true) ||
                finalCleanUrl.contains("zerotv", ignoreCase = true) ||
                isRgkkw

        val isTsStream = !isM3u8Pattern && !isMovieFormat && (pathBeforeQuery.endsWith(".ts") ||
                finalCleanUrl.contains("video/mp2t", ignoreCase = true) ||
                (isXtreamStream && !finalCleanUrl.contains(".m3u8", ignoreCase = true)))

        val primaryUa = when {
            isTapmad -> "NetworkStreamPlayer/1.0 (Linux; Android 14; Mobile)"
            !extractedUa.isNullOrBlank() -> extractedUa!!
            isToffee -> "Toffee (Linux;Android 14)"
            isMovieLinkBd -> "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
            isTsStream || isAkr4m || isXtreamStream -> "IPTVSmartersPro/3.1.5.1 (Linux; Android 14)"
            else -> "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        }

        // Multi-Agent fallback list for maximum online stream compatibility
        val fallbackUserAgents = when {
            isTapmad -> listOf(
                "NetworkStreamPlayer/1.0 (Linux; Android 14; Mobile)",
                "ExoPlayerLib/2.19.1 (Linux; Android 14; Mobile)",
                "VLC/3.0.18 LibVLC/3.0.18",
                "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
                "IPTVSmartersPro/3.1.5.1 (Linux; Android 14)"
            ).distinct()
            isMovieLinkBd -> listOf(
                primaryUa,
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
            ).distinct()
            else -> listOf(
                primaryUa,
                "VLC/3.0.18 LibVLC/3.0.18",
                "Televizo/1.9.3 (Linux; Android 14)",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                "IPTVSmartersPro/3.1.5.1 (Linux; Android 14)",
                "TiviMate/4.7.0 (Android TV)",
                "Toffee (Linux;Android 14)"
            ).distinct()
        }
        val finalUserAgent = fallbackUserAgents[playerRetryKey.coerceAtLeast(0) % fallbackUserAgents.size]

        val uriHost = try { android.net.Uri.parse(finalCleanUrl).host } catch (_: Exception) { null }
        val streamHostReferer = if (!uriHost.isNullOrBlank() && !isXtreamStream && !isToffee && !isTapmad) "https://$uriHost/" else null
        val streamHostOrigin = if (!uriHost.isNullOrBlank() && !isXtreamStream && !isToffee && !isTapmad) "https://$uriHost" else null

        val requestHeaders = mutableMapOf<String, String>()
        requestHeaders["User-Agent"] = finalUserAgent
        val finalReferer = when {
            isTapmad -> null
            !extractedReferer.isNullOrBlank() -> extractedReferer
            isToffee -> "https://toffeelive.com/"
            isXtreamStream -> null
            isMovieLinkBd -> streamHostReferer ?: "https://fast.movielinkbd.app/"
            else -> streamHostReferer
        }
        val finalOrigin = when {
            isTapmad -> null
            !extractedOrigin.isNullOrBlank() -> extractedOrigin
            isToffee -> "https://toffeelive.com"
            isXtreamStream -> null
            isMovieLinkBd -> streamHostOrigin ?: "https://fast.movielinkbd.app"
            else -> streamHostOrigin
        }

        val suppressReferer = (playerRetryKey == 1) || isXtreamStream || isTapmad
        if (!finalReferer.isNullOrBlank() && !suppressReferer) {
            requestHeaders["Referer"] = finalReferer
        }
        if (!finalOrigin.isNullOrBlank() && !suppressReferer) {
            requestHeaders["Origin"] = finalOrigin
        }
        if (isTapmad) {
            requestHeaders.remove("Referer")
            requestHeaders.remove("Origin")
        }
        if (!extractedCookie.isNullOrBlank()) {
            requestHeaders["Cookie"] = extractedCookie
        }

        // Special headers for Pixeldrain / file-sharing domains
        val isPixeldrainUrl = finalCleanUrl.contains("pixeldrain", ignoreCase = true) || finalCleanUrl.contains("pixeldra.in", ignoreCase = true)
        if (isPixeldrainUrl) {
            if (!requestHeaders.containsKey("Referer")) requestHeaders["Referer"] = "https://pixeldrain.com/"
            if (!requestHeaders.containsKey("Origin")) requestHeaders["Origin"] = "https://pixeldrain.com"
        }

        if (isMovieLinkBd) {
            if (!requestHeaders.containsKey("Referer")) {
                val host = uriHost ?: "fast.movielinkbd.app"
                requestHeaders["Referer"] = "https://$host/"
            }
            if (!requestHeaders.containsKey("Origin")) {
                val host = uriHost ?: "fast.movielinkbd.app"
                requestHeaders["Origin"] = "https://$host"
            }
        }

        requestHeaders["Accept"] = "*/*"
        requestHeaders["Connection"] = "keep-alive"
        requestHeaders["Cache-Control"] = "no-cache"
        if (isTsStream || isXtreamStream || isMovieLinkBd) {
            requestHeaders["Accept-Encoding"] = "identity"
        }
        requestHeaders.putAll(dynamicHeaders)

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(8000)
            .setReadTimeoutMs(15000)
            .setUserAgent(finalUserAgent)
            .setTransferListener(bandwidthMeter)
            .setDefaultRequestProperties(requestHeaders)

        // DefaultDataSource delegates http/https to httpDataSourceFactory, and local file:// / content:// / assets to FileDataSource
        val defaultDataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(
            context,
            httpDataSourceFactory
        )

        val isLiveStream = !isMovieFormat && !isMovieLinkBd && (
                currentMedia.isLive ||
                currentMedia.type == MediaType.LIVE_TV ||
                currentMedia.type == MediaType.LIVE_EVENT ||
                isTsStream ||
                isXtreamStream ||
                finalCleanUrl.contains(".m3u8", ignoreCase = true)
        )

        // Instant fail-fast error policy: avoids long connection checking waits, fails fast in 2 retries
        val loadErrorHandlingPolicy = object : androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy(3) {
            override fun getRetryDelayMsFor(loadErrorInfo: androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy.LoadErrorInfo): Long {
                return 250L
            }
            override fun getMinimumLoadableRetryCount(dataType: Int): Int {
                return 2
            }
        }

        // TS Extractor Flags for IPTV streams (MP2, AC3, AAC in MPEG-TS with PES packet size variations)
        val tsPayloadReaderFlags = androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS or
                androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM

        val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(!isLiveStream)
            .setTsExtractorFlags(tsPayloadReaderFlags)
            .setTsExtractorMode(if (isTsStream) androidx.media3.extractor.ts.TsExtractor.MODE_SINGLE_PMT else androidx.media3.extractor.ts.TsExtractor.MODE_MULTI_PMT)
            .setTsExtractorTimestampSearchBytes(androidx.media3.extractor.ts.TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES * 4)

        val mediaSourceFactory = DefaultMediaSourceFactory(defaultDataSourceFactory, extractorsFactory)
            .setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)

        if (drmConfig != null) {
            val drmSessionManager = com.example.util.DrmHelper.createDrmSessionManager(drmConfig, httpDataSourceFactory)
            if (drmSessionManager != null) {
                mediaSourceFactory.setDrmSessionManagerProvider { drmSessionManager }
            }
        }

        // Custom AudioSink with Float Output disabled and AudioCapabilities to prevent silent audio on MP2/AC3/EAC3/DTS streams
        val audioSink = androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
            .setEnableFloatOutput(false)
            .setEnableAudioTrackPlaybackParams(true)
            .setAudioCapabilities(androidx.media3.exoplayer.audio.AudioCapabilities.getCapabilities(context))
            .build()

        val renderersFactory = object : androidx.media3.exoplayer.DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink {
                return audioSink
            }

            override fun buildAudioRenderers(
                context: android.content.Context,
                extensionRendererMode: Int,
                mediaCodecSelector: androidx.media3.exoplayer.mediacodec.MediaCodecSelector,
                enableDecoderFallback: Boolean,
                audioSink: androidx.media3.exoplayer.audio.AudioSink,
                eventHandler: android.os.Handler,
                eventListener: androidx.media3.exoplayer.audio.AudioRendererEventListener,
                out: java.util.ArrayList<androidx.media3.exoplayer.Renderer>
            ) {
                // 1. Hardware/Platform MediaCodec Audio Renderer with decoder fallback
                out.add(
                    androidx.media3.exoplayer.audio.MediaCodecAudioRenderer(
                        context,
                        mediaCodecSelector,
                        enableDecoderFallback,
                        eventHandler,
                        eventListener,
                        audioSink
                    )
                )

                // 2. FFmpeg Audio Renderer for MP2, MP1, AC3, EAC3, TrueHD, DTS, Opus, Vorbis, FLAC, ALAC, etc.
                try {
                    if (androidx.media3.decoder.ffmpeg.FfmpegLibrary.isAvailable()) {
                        out.add(
                            androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer(
                                eventHandler,
                                eventListener,
                                audioSink
                            )
                        )
                    }
                } catch (t: Throwable) {
                    android.util.Log.w("VideoPlayer", "FFmpeg audio renderer init fallback", t)
                }
            }

            override fun buildVideoRenderers(
                context: android.content.Context,
                extensionRendererMode: Int,
                mediaCodecSelector: androidx.media3.exoplayer.mediacodec.MediaCodecSelector,
                enableDecoderFallback: Boolean,
                eventHandler: android.os.Handler,
                eventListener: androidx.media3.exoplayer.video.VideoRendererEventListener,
                allowedVideoJoiningTimeMs: Long,
                out: java.util.ArrayList<androidx.media3.exoplayer.Renderer>
            ) {
                // Delegate to super to ensure DefaultMediaCodecAdapterFactory (asynchronous MediaCodec)
                // is properly configured and software extension renderers are registered,
                // while guaranteeing 15 seconds video joining tolerance and enabling decoder fallback.
                super.buildVideoRenderers(
                    context,
                    extensionRendererMode,
                    mediaCodecSelector,
                    true, // enableDecoderFallback
                    eventHandler,
                    eventListener,
                    15000L.coerceAtLeast(allowedVideoJoiningTimeMs),
                    out
                )
            }
        }.apply {
            setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            setEnableDecoderFallback(true)
            setEnableAudioFloatOutput(false)
            setEnableAudioTrackPlaybackParams(true)
            setAllowedVideoJoiningTimeMs(15000L)
        }

        // High-responsiveness Adaptive Bitrate Track Selection:
        // Automatically steps down resolution in 300ms if bandwidth drops to prevent any buffering stalls,
        // and upgrades smoothly to HD when network throughput is proven stable.
        val adaptiveTrackSelectionFactory = androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection.Factory(
            /* minDurationForQualityIncreaseMs = */ 6000, // 6s sustained speed before upgrading
            /* maxDurationForQualityDecreaseMs = */ 300,  // 300ms ultra-rapid downscaling on network drop to prevent stalls
            /* minDurationToRetainAfterDiscardMs = */ 3000,
            /* bandwidthFraction = */ 0.70f               // 70% bandwidth headroom prevents buffer exhaustion
        )

        val trackSelector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(context, adaptiveTrackSelectionFactory).apply {
            setParameters(
                buildUponParameters()
                    .setExceedRendererCapabilitiesIfNecessary(true)
                    .setExceedVideoConstraintsIfNecessary(true)
                    .setExceedAudioConstraintsIfNecessary(true)
                    .setAllowAudioMixedMimeTypeAdaptiveness(true)
                    .setAllowAudioMixedChannelCountAdaptiveness(true)
                    .setAllowAudioMixedSampleRateAdaptiveness(true)
                    .setAllowAudioNonSeamlessAdaptiveness(true)
                    .setAllowVideoMixedMimeTypeAdaptiveness(true)
                    .setAllowMultipleAdaptiveSelections(true)
                    .setTunnelingEnabled(false)
                    .setForceLowestBitrate(false)
                    .setForceHighestSupportedBitrate(false)
            )
        }

        val actManager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        val isLowRamDevice = actManager?.isLowRamDevice == true || (Runtime.getRuntime().maxMemory() / (1024 * 1024)) <= 128

        // Memory-safe, high-speed, anti-buffering LoadControl tuned for all devices down to 512MB RAM
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setAllocator(androidx.media3.exoplayer.upstream.DefaultAllocator(true, if (isLowRamDevice) 32 * 1024 else 64 * 1024))
            .setBufferDurationsMs(
                /* minBufferMs = */ if (isLowRamDevice) 2000 else if (isAkr4m) 8000 else if (isLiveStream) 4000 else 6000,
                /* maxBufferMs = */ if (isLowRamDevice) 6000 else if (isAkr4m) 20000 else if (isLiveStream) 15000 else 25000,
                /* bufferForPlaybackMs = */ 250,
                /* bufferForPlaybackAfterRebufferMs = */ 500
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(if (isLiveStream || isLowRamDevice) 0 else 5000, false)
            .setTargetBufferBytes(
                if (isLowRamDevice) {
                    if (isLiveStream) 4 * 1024 * 1024 else 6 * 1024 * 1024
                } else if (isAkr4m) {
                    18 * 1024 * 1024
                } else if (isLiveStream) {
                    12 * 1024 * 1024
                } else {
                    24 * 1024 * 1024
                }
            )
            .build()

        val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
            .setUsage(androidx.media3.common.C.USAGE_MEDIA)
            .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setBandwidthMeter(bandwidthMeter)
            .setSeekParameters(androidx.media3.exoplayer.SeekParameters.EXACT)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(androidx.media3.common.C.WAKE_MODE_LOCAL)
            .build().apply {
                volume = if (isMuted) 0f else 1.0f
                val finalMediaUri = when {
                    finalCleanUrl.startsWith("file://") -> android.net.Uri.parse(finalCleanUrl)
                    finalCleanUrl.startsWith("content://") -> android.net.Uri.parse(finalCleanUrl)
                    finalCleanUrl.startsWith("/") -> android.net.Uri.fromFile(java.io.File(finalCleanUrl))
                    else -> android.net.Uri.parse(finalCleanUrl)
                }

                val mediaItemBuilder = MediaItem.Builder()
                    .setUri(finalMediaUri)

                val isMp4 = finalCleanUrl.contains(".mp4", ignoreCase = true)
                val isMkv = finalCleanUrl.contains(".mkv", ignoreCase = true) || isMovieLinkBd
                val isWebm = finalCleanUrl.contains(".webm", ignoreCase = true)
                val isTs = (finalCleanUrl.contains(".ts", ignoreCase = true) ||
                        finalCleanUrl.contains("video/mp2t", ignoreCase = true) ||
                        ((isRgkkw && finalCleanUrl.contains("/live/")) && !finalCleanUrl.contains(".m3u8", ignoreCase = true))) && !isMp4 && !isMkv && !isWebm

                val isFileHost = finalCleanUrl.contains("pixeldrain", ignoreCase = true) ||
                        finalCleanUrl.contains("pixeldra.in", ignoreCase = true) ||
                        finalCleanUrl.contains("drive.google.com", ignoreCase = true) ||
                        finalCleanUrl.contains("dropbox.com", ignoreCase = true) ||
                        finalCleanUrl.contains("mediafire.com", ignoreCase = true) ||
                        isMovieLinkBd

                val isMpd = finalCleanUrl.contains(".mpd", ignoreCase = true) ||
                        finalCleanUrl.contains("dash", ignoreCase = true) ||
                        drmConfig?.manifestType?.equals("mpd", ignoreCase = true) == true ||
                        currentMedia.manifestType?.equals("mpd", ignoreCase = true) == true

                val isM3u8 = !isFileHost && !isTs && !isMovieLinkBd && !isMovieFormat && (finalCleanUrl.contains(".m3u8", ignoreCase = true) ||
                        finalCleanUrl.contains("/hls/", ignoreCase = true) ||
                        drmConfig?.manifestType?.equals("hls", ignoreCase = true) == true ||
                        currentMedia.manifestType?.equals("hls", ignoreCase = true) == true ||
                        isToffee ||
                        ((currentMedia.type == MediaType.LIVE_TV || currentMedia.type == MediaType.LIVE_EVENT || currentMedia.isLive) && !isMp4 && !isMkv && !isWebm && !isMpd && !isFileHost))

                if (isLiveStream && !isTs) {
                    // Safe target live offset: only for HLS/DASH manifest streams
                    val targetLiveOffset = if (isAkr4m) 22000L else 15000L
                    mediaItemBuilder.setLiveConfiguration(
                        androidx.media3.common.MediaItem.LiveConfiguration.Builder()
                            .setTargetOffsetMs(targetLiveOffset)
                            .setMinOffsetMs(if (isAkr4m) 10000L else 6000L)
                            .setMaxOffsetMs(60000L)
                            .setMinPlaybackSpeed(1.0f)
                            .setMaxPlaybackSpeed(1.01f)
                            .build()
                    )
                }

                if (drmConfig != null) {
                    val drmConfigBuilder = MediaItem.DrmConfiguration.Builder(drmConfig.schemeUuid)
                    if (!drmConfig.licenseUrl.isNullOrBlank()) {
                        drmConfigBuilder.setLicenseUri(drmConfig.licenseUrl)
                    }
                    if (drmConfig.headers.isNotEmpty()) {
                        drmConfigBuilder.setLicenseRequestHeaders(drmConfig.headers)
                    }
                    drmConfigBuilder.setMultiSession(true)
                    mediaItemBuilder.setDrmConfiguration(drmConfigBuilder.build())
                }

                if (isMpd) {
                    mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_MPD)
                } else if (isM3u8) {
                    mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
                } else if (isTs) {
                    mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.VIDEO_MP2T)
                } else if (isMp4) {
                    mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.VIDEO_MP4)
                } else if (isMkv) {
                    mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.VIDEO_MATROSKA)
                } else if (isWebm) {
                    mediaItemBuilder.setMimeType(androidx.media3.common.MimeTypes.VIDEO_WEBM)
                }

                setMediaItem(mediaItemBuilder.build())
                playWhenReady = true
                prepare()

                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> {
                                isBuffering = true
                                errorMessage = null
                            }
                            Player.STATE_READY -> {
                                isBuffering = false
                                isActuallyBuffering = false
                                hasStartedPlaying = true
                                errorMessage = null
                                isPlaying = playWhenReady
                                val dur = duration
                                if (dur > 0 && dur != C.TIME_UNSET) {
                                    durationMs = dur
                                }
                            }
                            Player.STATE_ENDED -> {
                                isBuffering = false
                                isPlaying = false
                                if (currentMedia.isSeries || currentMedia.episodes.isNotEmpty()) {
                                    playNextEpisode()
                                } else if (isLiveStream || isTs || isTsStream || isXtreamStream) {
                                    // Seamless live stream auto-reconnect: IPTV servers may disconnect TCP after a few seconds or on micro-packet drops.
                                    // A live broadcast never has an EOF! Reconnect in 250ms without freezing or stopping.
                                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                        try {
                                            seekToDefaultPosition()
                                            prepare()
                                            playWhenReady = true
                                        } catch (_: Exception) {}
                                    }, 250L)
                                }
                            }
                            Player.STATE_IDLE -> {
                                isBuffering = false
                            }
                        }
                    }

                    override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                        val dur = duration
                        if (dur > 0 && dur != C.TIME_UNSET) {
                            durationMs = dur
                        }
                    }

                    override fun onRenderedFirstFrame() {
                        isBuffering = false
                        isActuallyBuffering = false
                        hasStartedPlaying = true
                        com.example.util.ChannelStatusManager.markChannelSuccess(currentMedia.id)
                        com.example.util.ChannelStatusManager.markServerSuccess(currentUrl)
                    }

                    override fun onTracksChanged(tracks: Tracks) {
                        val qualities = mutableListOf<VideoQualityOption>()
                        qualities.add(VideoQualityOption("অটো (Auto)", -1, isAuto = true))
                        val seenHeights = mutableSetOf<Int>()

                        val audios = mutableListOf<AudioTrackOption>()
                        val subs = mutableListOf<SubtitleTrackOption>()
                        subs.add(SubtitleTrackOption(id = "off", language = "", displayName = "বন্ধ (Off)", isOff = true))

                        for (groupIndex in 0 until tracks.groups.size) {
                            val group = tracks.groups[groupIndex]
                            when (group.type) {
                                C.TRACK_TYPE_VIDEO -> {
                                    for (trackIndex in 0 until group.length) {
                                        val format = group.getTrackFormat(trackIndex)
                                        if (format.height > 0 && seenHeights.add(format.height)) {
                                            val label = when {
                                                format.height >= 1080 -> "1080p FHD"
                                                format.height >= 720 -> "720p HD"
                                                format.height >= 480 -> "480p SD"
                                                format.height >= 360 -> "360p Low"
                                                else -> "${format.height}p"
                                            }
                                            qualities.add(VideoQualityOption(label, format.height))
                                        }
                                    }
                                }
                                C.TRACK_TYPE_AUDIO -> {
                                    for (trackIndex in 0 until group.length) {
                                        val format = group.getTrackFormat(trackIndex)
                                        val lang = format.language ?: ""
                                        val langName = when (lang.lowercase()) {
                                            "hi", "hin", "hindi" -> "Hindi (হিন্দি)"
                                            "bn", "ben", "bangla", "bengali" -> "Bengali (বাংলা)"
                                            "en", "eng", "english" -> "English (ইংরেজি)"
                                            "ja", "jpn", "japanese" -> "Japanese (জাপানি)"
                                            "ko", "kor", "korean" -> "Korean (কোরিয়ান)"
                                            "ta", "tam", "tamil" -> "Tamil (তামিল)"
                                            "te", "tel", "telugu" -> "Telugu (তেলেগু)"
                                            "ur", "urd", "urdu" -> "Urdu (উর্দু)"
                                            "ar", "ara", "arabic" -> "Arabic (আরবি)"
                                            else -> format.label?.ifBlank { null } ?: if (lang.isNotBlank()) lang.uppercase() else "Audio Track ${audios.size + 1}"
                                        }
                                        audios.add(AudioTrackOption(id = "$groupIndex-$trackIndex", language = lang, displayName = langName, groupIndex = groupIndex, trackIndex = trackIndex))
                                    }
                                }
                                C.TRACK_TYPE_TEXT -> {
                                    for (trackIndex in 0 until group.length) {
                                        val format = group.getTrackFormat(trackIndex)
                                        val lang = format.language ?: ""
                                        val langName = when (lang.lowercase()) {
                                            "en", "eng", "english" -> "English Subtitles"
                                            "bn", "ben", "bangla" -> "Bangla Subtitles"
                                            "hi", "hin" -> "Hindi Subtitles"
                                            else -> format.label?.ifBlank { null } ?: if (lang.isNotBlank()) lang.uppercase() else "Subtitle ${subs.size}"
                                        }
                                        subs.add(SubtitleTrackOption(id = "$groupIndex-$trackIndex", language = lang, displayName = langName, isOff = false, groupIndex = groupIndex, trackIndex = trackIndex))
                                    }
                                }
                            }
                        }

                        if (qualities.size <= 1) {
                            qualities.clear()
                            qualities.add(VideoQualityOption("অটো (Auto)", -1, isAuto = true))
                            qualities.add(VideoQualityOption("1080p FHD", 1080))
                            qualities.add(VideoQualityOption("720p HD", 720))
                            qualities.add(VideoQualityOption("480p SD", 480))
                            qualities.add(VideoQualityOption("360p Low", 360))
                        }

                        availableVideoQualities = qualities.sortedByDescending { it.height }
                        if (audios.isNotEmpty()) {
                            availableAudioTracks = audios
                            if (selectedAudioTrack == null) {
                                selectedAudioTrack = audios.firstOrNull()
                            }
                        }
                        if (subs.size > 1) {
                            availableSubtitles = subs
                        }
                    }

                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                    }

                    override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                        if (videoSize.width > 0 && videoSize.height > 0) {
                            val label = when {
                                videoSize.height >= 1080 -> "1080p HD"
                                videoSize.height >= 720 -> "720p HD"
                                videoSize.height >= 480 -> "480p SD"
                                videoSize.height >= 360 -> "360p Low"
                                else -> "${videoSize.height}p"
                            }
                            currentVideoResolution = label
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        isBuffering = false
                        val currentServers = currentMedia.getAllServers()
                        val httpEx = error.cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
                        val is405 = httpEx?.responseCode == 405
                        val is403Or401 = httpEx?.responseCode == 403 || httpEx?.responseCode == 401
                        val isParsingOrContainerError = error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                                error.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED ||
                                error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
                                error.cause is androidx.media3.common.ParserException ||
                                error.cause is androidx.media3.exoplayer.source.UnrecognizedInputFormatException

                        // 1. Xtream MPEG-TS vs HLS fast swap (single immediate fallback)
                        val isProtectHls = currentUrl.contains("toffeelive.com", ignoreCase = true) ||
                                currentUrl.contains("toffee", ignoreCase = true) ||
                                currentUrl.contains("akamaized.net", ignoreCase = true) ||
                                currentUrl.contains("tapmad", ignoreCase = true)
                        if (!isProtectHls && currentUrl.contains("/live/") && currentUrl.contains(".ts", ignoreCase = true) && playerRetryKey == 0) {
                            currentUrl = currentUrl.replace(".ts", ".m3u8", ignoreCase = true)
                            playerRetryKey = 1
                            errorMessage = null
                            return
                        } else if (!isProtectHls && currentUrl.contains("/live/") && currentUrl.contains(".m3u8", ignoreCase = true) && playerRetryKey == 0) {
                            currentUrl = currentUrl.replace(".m3u8", ".ts", ignoreCase = true)
                            playerRetryKey = 1
                            errorMessage = null
                            return
                        }

                        // 2. Multi-Server Fast Auto-Switch:
                        // If this channel has alternative servers (e.g. Sports 1 failed -> immediately try Sports 2),
                        // directly switch to the next server without waiting!
                        val allServers = currentMedia.getAllServers()
                        val isAkamai403 = (is403Or401 || isParsingOrContainerError) &&
                                (currentUrl.contains("akamaized.net", ignoreCase = true) || currentUrl.contains("tapmad", ignoreCase = true))

                        // Allow Tapmad stream to retry with native fallback User-Agent before auto-switching
                        if (isAkamai403 && playerRetryKey < 1) {
                            playerRetryKey = 1
                            errorMessage = null
                            return
                        }

                        if (allServers.size > 1 && selectedServerIndex < allServers.size - 1) {
                            val nextIdx = selectedServerIndex + 1
                            val nextServerName = allServers.getOrNull(nextIdx)?.name ?: "সার্ভার ${nextIdx + 1}"
                            val switchMsg = if (isAkamai403) {
                                "ট্যাপমাড আকামাই সার্ভার টোকেন সুরক্ষিত। সক্রিয় বিকল্প সার্ভারে ($nextServerName) সুইচ করা হচ্ছে..."
                            } else {
                                "বিকল্প সার্ভার ($nextServerName) চালু হচ্ছে..."
                            }
                            android.widget.Toast.makeText(
                                context,
                                switchMsg,
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            errorMessage = null
                            playerRetryKey = 0
                            forceWebEngine = false
                            pendingServerSwitchIndex = nextIdx
                            return
                        }

                        // 2.1 Dynamic Match Server Resolution for single-stream Tapmad / Sports items
                        if (isAkamai403 || currentMedia.category.contains("cricket", ignoreCase = true) || currentMedia.type == MediaType.LIVE_EVENT) {
                            try {
                                val resolved = com.example.data.MediaRepository(context).resolveWorkingSportsServers(currentMedia.title, currentUrl, currentMedia.id)
                                val fallbackServer = resolved.firstOrNull {
                                    !it.url.contains("akamaized.net", ignoreCase = true) && !it.url.equals(currentUrl, ignoreCase = true)
                                }
                                if (fallbackServer != null && playerRetryKey < 2) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "ট্যাপমাড আকামাই ৪0৩ সুরক্ষিত। লাইভ বিকল্প সার্ভার (${fallbackServer.name}) চালু হচ্ছে...",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                    currentUrl = fallbackServer.url
                                    playerRetryKey++
                                    errorMessage = null
                                    return
                                }
                            } catch (_: Exception) {}
                        }

                        // 3. All servers exhausted or single server failed -> Instant Auto-Skip to next channel (only for Live TV/Events):
                        val activeServerName = currentServers.getOrNull(selectedServerIndex)?.name?.takeIf { it.isNotBlank() } ?: "সার্ভার ${selectedServerIndex + 1}"
                        if (isAutoSwitchOnFailureEnabled && (currentMedia.isLive || currentMedia.type == MediaType.LIVE_TV || currentMedia.type == MediaType.LIVE_EVENT)) {
                            android.widget.Toast.makeText(
                                context,
                                "⚠️ '${currentMedia.title}' সংযোগ করা যায়নি। পরবর্তী চ্যানেলে যাওয়া হচ্ছে...",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                            errorMessage = null
                            playerRetryKey = 0
                            forceWebEngine = false
                            pendingChannelSwitchDelta = 1
                            return
                        }
                        errorMessage = "$activeServerName: ভিডিও সংযোগ করা সম্ভব হয়নি। বিকল্প সার্ভার বেছে নিন অথবা পুনরায় চেষ্টা করুন।"
                    }
                })
            }
    }

    // Dead / Unplayable Channel Auto-Skip Watchdog:
    // If a selected channel hangs or fails to play within 4.5 seconds (or if stream link is blank),
    // automatically and forcibly skips to the next server or next channel without waiting for user permission!
    val isChannelOrLive = currentMedia.isLive || currentMedia.type == MediaType.LIVE_TV || currentMedia.type == MediaType.LIVE_EVENT
    LaunchedEffect(currentMedia.id, currentUrl, hasStartedPlaying, isAutoSwitchOnFailureEnabled) {
        if (!hasStartedPlaying && isAutoSwitchOnFailureEnabled && isChannelOrLive) {
            // Immediate check: if stream URL is blank, skip without waiting
            if (currentUrl.isBlank()) {
                delay(800)
                if (!hasStartedPlaying) {
                    val allServers = currentMedia.getAllServers()
                    if (allServers.size > 1 && selectedServerIndex < allServers.size - 1) {
                        val nextIdx = selectedServerIndex + 1
                        val nextServerName = allServers.getOrNull(nextIdx)?.name ?: "সার্ভার ${nextIdx + 1}"
                        android.widget.Toast.makeText(context, "বিকল্প সার্ভার ($nextServerName) চেষ্টা করা হচ্ছে...", android.widget.Toast.LENGTH_SHORT).show()
                        pendingServerSwitchIndex = nextIdx
                    } else {
                        android.widget.Toast.makeText(context, "⚠️ '${currentMedia.title}' লিংক নেই। পরবর্তী চ্যানেলে যাওয়া হচ্ছে...", android.widget.Toast.LENGTH_SHORT).show()
                        pendingChannelSwitchDelta = 1
                    }
                }
                return@LaunchedEffect
            }

            // Normal timeout: 4.5 seconds
            delay(4500)
            if (!hasStartedPlaying) {
                val allServers = currentMedia.getAllServers()
                if (allServers.size > 1 && selectedServerIndex < allServers.size - 1) {
                    val nextIdx = selectedServerIndex + 1
                    val nextServerName = allServers.getOrNull(nextIdx)?.name ?: "সার্ভার ${nextIdx + 1}"
                    android.widget.Toast.makeText(context, "বিকল্প সার্ভার ($nextServerName) চেষ্টা করা হচ্ছে...", android.widget.Toast.LENGTH_SHORT).show()
                    pendingServerSwitchIndex = nextIdx
                } else {
                    android.widget.Toast.makeText(
                        context,
                        "⚠️ '${currentMedia.title}' চ্যানেলটি সচল নয়। স্বয়ংক্রিয়ভাবে পরবর্তী চ্যানেলে যাওয়া হচ্ছে...",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    errorMessage = null
                    playerRetryKey = 0
                    forceWebEngine = false
                    pendingChannelSwitchDelta = 1
                }
            }
        }
    }

    // Periodic time progress tracker & Live Stream Anti-Stall Auto-Recovery Watchdog
    val isCurrentItemLive = currentMedia.isLive || currentMedia.type == MediaType.LIVE_TV || currentMedia.type == MediaType.LIVE_EVENT
    LaunchedEffect(exoPlayer, isCurrentItemLive, showControls) {
        var lastPlayPos = -1L
        var frozenTicks = 0
        while (true) {
            val cur = try { exoPlayer.currentPosition.coerceAtLeast(0L) } catch (_: Exception) { 0L }
            val dur = try { exoPlayer.duration } catch (_: Exception) { C.TIME_UNSET }
            if (dur > 0 && dur != C.TIME_UNSET) {
                durationMs = dur
            }
            if (!isDraggingSlider) {
                currentPositionMs = cur
            }

            // Live Stream Anti-Buffering & Anti-Stall Watchdog:
            // Detects if server stopped sending packets after a few seconds and automatically resumes playback
            if (isCurrentItemLive && hasStartedPlaying) {
                val isBuff = try { exoPlayer.playbackState == Player.STATE_BUFFERING } catch (_: Exception) { false }
                val isPlay = try { exoPlayer.isPlaying } catch (_: Exception) { false }
                val isEnded = try { exoPlayer.playbackState == Player.STATE_ENDED } catch (_: Exception) { false }

                if (isEnded) {
                    try {
                        exoPlayer.seekToDefaultPosition()
                        exoPlayer.prepare()
                        exoPlayer.playWhenReady = true
                    } catch (_: Exception) {}
                } else if (isBuff || (isPlay && cur == lastPlayPos && cur > 0)) {
                    frozenTicks++
                    if (frozenTicks >= 6) { // 3 seconds of freeze detected
                        frozenTicks = 0
                        try {
                            exoPlayer.seekToDefaultPosition()
                            exoPlayer.prepare()
                            exoPlayer.playWhenReady = true
                        } catch (_: Exception) {}
                    }
                } else {
                    frozenTicks = 0
                }
                lastPlayPos = cur
            }

            delay(500)
        }
    }

    // Cleanup on dispose
    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    val focusRequester = remember { FocusRequester() }
    val initialControlFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    LaunchedEffect(showControls) {
        try {
            if (showControls) {
                delay(80)
                initialControlFocusRequester.requestFocus()
            } else {
                delay(50)
                focusRequester.requestFocus()
            }
        } catch (_: Exception) {}
    }

    // Switch stream server by index
    fun switchServer(targetIndex: Int) {
        val allServers = currentMedia.getAllServers()
        if (allServers.isEmpty()) return
        val newIndex = targetIndex.coerceIn(0, allServers.size - 1)
        selectedServerIndex = newIndex
        val targetServer = allServers[newIndex]
        val oldUrl = currentUrl
        currentUrl = targetServer.url
        isBuffering = true
        isActuallyBuffering = true
        hasStartedPlaying = false
        durationMs = 0L
        currentPositionMs = 0L
        errorMessage = null
        playerRetryKey = 0
        forceWebEngine = false
        channelOsdKey = System.currentTimeMillis()
        if (oldUrl == targetServer.url) {
            try {
                exoPlayer.seekTo(0)
                exoPlayer.prepare()
                exoPlayer.play()
            } catch (_: Exception) {}
        }
        android.widget.Toast.makeText(context, "${targetServer.name} চালু হচ্ছে...", android.widget.Toast.LENGTH_SHORT).show()
    }

    // Cycle to next available stream server
    fun cycleNextServer() {
        val allServers = currentMedia.getAllServers()
        if (allServers.size > 1) {
            val nextIdx = (selectedServerIndex + 1) % allServers.size
            switchServer(nextIdx)
        } else {
            android.widget.Toast.makeText(context, "এই চ্যানেলে ১টি মাত্র সার্ভার রয়েছে", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Handle Direct Channel Number input from Remote (0-9)
    fun handleNumberInput(digit: String) {
        remoteNumberBuffer = (remoteNumberBuffer + digit).take(4)
        remoteNumberKey = System.currentTimeMillis()
    }

    // Switch to Next / Previous Channel cleanly and reliably without trapping inside multi-server loops
    fun switchChannel(delta: Int) {
        if ((currentMedia.isSeries || currentMedia.episodes.isNotEmpty()) && currentMedia.episodes.size > 1) {
            val didSwitch = if (delta > 0) playNextEpisode() else playPrevEpisode()
            if (didSwitch) return
        }

        val list = when {
            playlist.size > 1 -> playlist
            else -> {
                val cached = playerRepository.getCachedLiveTvChannels().ifEmpty {
                    playerRepository.getCachedSportsMatches().ifEmpty {
                        playerRepository.getDefaultBuiltinLiveTv()
                    }
                }
                if (cached.size > 1) cached else playlist.ifEmpty { listOf(currentMedia) }
            }
        }
        if (list.isEmpty()) return

        var currentIndex = list.indexOfFirst { it.id == currentMedia.id }
        if (currentIndex == -1) {
            currentIndex = list.indexOfFirst { it.title.trim().equals(currentMedia.title.trim(), ignoreCase = true) }
        }
        if (currentIndex == -1) {
            currentIndex = list.indexOfFirst { it.streamUrl.trim().equals(currentMedia.streamUrl.trim(), ignoreCase = true) }
        }
        if (currentIndex == -1) {
            val currentServers = currentMedia.getAllServers()
            val allCurrentUrls = currentServers.map { it.url.trim() }.toSet()
            currentIndex = list.indexOfFirst { item ->
                item.getAllServers().any { allCurrentUrls.contains(it.url.trim()) }
            }
        }
        if (currentIndex == -1) {
            currentIndex = 0
        }

        val targetIndex = if (delta > 0) {
            (currentIndex + 1) % list.size
        } else {
            if (currentIndex - 1 < 0) list.size - 1 else currentIndex - 1
        }

        val targetItem = list[targetIndex]
        isBuffering = true
        isActuallyBuffering = true
        hasStartedPlaying = false
        durationMs = 0L
        currentPositionMs = 0L
        currentMedia = targetItem
        selectedServerIndex = 0
        val targetServers = targetItem.getAllServers()
        currentUrl = targetServers.firstOrNull()?.url ?: targetItem.streamUrl
        errorMessage = null
        playerRetryKey = 0
        forceWebEngine = false
        if (isFullscreen && !isTvMode) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
        onSelectMedia(targetItem)
        channelOsdKey = System.currentTimeMillis()
        android.widget.Toast.makeText(context, "${targetItem.title} চালু হচ্ছে...", android.widget.Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(pendingServerSwitchIndex) {
        if (pendingServerSwitchIndex >= 0) {
            val idx = pendingServerSwitchIndex
            pendingServerSwitchIndex = -1
            switchServer(idx)
        }
    }

    LaunchedEffect(pendingChannelSwitchDelta) {
        if (pendingChannelSwitchDelta != 0) {
            val delta = pendingChannelSwitchDelta
            pendingChannelSwitchDelta = 0
            switchChannel(delta)
        }
    }

    fun handleRemoteKeyEvent(keyEvent: androidx.compose.ui.input.key.KeyEvent): Boolean {
        if (keyEvent.type != KeyEventType.KeyDown) return false
        val nativeEvent = keyEvent.nativeKeyEvent
        val keyCode = nativeEvent.keyCode

        // Reset control auto-hide timer on any remote interaction
        if (showControls) {
            controlsInteractionKey = System.currentTimeMillis()
        }

        // When controls are visible, let D-pad focus navigate naturally across UI elements!
        if (showControls) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    // Return false so Compose FocusManager can navigate focus to top bar, server row, or bottom buttons!
                    return false
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    // Let the currently focused Compose component consume the click!
                    return false
                }
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                    if (showQuickChannelDrawer) {
                        showQuickChannelDrawer = false
                        return true
                    }
                    onBack()
                    return true
                }
            }
        }

        // Handle keys when controls are hidden or for global actions
        return when (keyCode) {
            // Dedicated Server Switching shortcuts (Yellow/Blue/Green keys, Button X, 'S' key)
            KeyEvent.KEYCODE_PROG_YELLOW, KeyEvent.KEYCODE_PROG_GREEN, KeyEvent.KEYCODE_PROG_BLUE,
            KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_S -> {
                cycleNextServer()
                true
            }

            // Play / Pause & Toggle Controls
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_SPACE -> {
                if (showControls) {
                    if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                } else {
                    showControls = true
                }
                true
            }

            // UP / DOWN -> Change Channel (or Channel Up/Down buttons)
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_PAGE_UP -> {
                switchChannel(-1)
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> {
                switchChannel(1)
                true
            }

            // LEFT -> Rewind / Seekbar Control
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD -> {
                val effDuration = if (durationMs > 0) durationMs else exoPlayer.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0L
                if (effDuration > 0) {
                    val target = maxOf(0L, currentPositionMs - 10000L)
                    exoPlayer.seekTo(target)
                    currentPositionMs = target
                    seekGestureTargetMs = target
                    seekGestureOffsetSec = -10
                    seekGestureKey = System.currentTimeMillis()
                    isSeekGestureActive = true
                    doubleTapSeekLeft = true
                    doubleTapSeekRight = false
                } else if (exoPlayer.isCurrentMediaItemSeekable) {
                    val cur = exoPlayer.currentPosition
                    val target = maxOf(0L, cur - 10000L)
                    exoPlayer.seekTo(target)
                    currentPositionMs = target
                    doubleTapSeekLeft = true
                    doubleTapSeekRight = false
                } else {
                    doubleTapSeekLeft = true
                    doubleTapSeekRight = false
                }
                showControls = true
                true
            }

            // RIGHT -> Forward / Seekbar Control
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_STEP_FORWARD -> {
                val effDuration = if (durationMs > 0) durationMs else exoPlayer.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0L
                if (effDuration > 0) {
                    val target = minOf(effDuration, currentPositionMs + 10000L)
                    exoPlayer.seekTo(target)
                    currentPositionMs = target
                    seekGestureTargetMs = target
                    seekGestureOffsetSec = 10
                    seekGestureKey = System.currentTimeMillis()
                    isSeekGestureActive = true
                    doubleTapSeekRight = true
                    doubleTapSeekLeft = false
                } else if (exoPlayer.isCurrentMediaItemSeekable) {
                    val cur = exoPlayer.currentPosition
                    val target = cur + 10000L
                    exoPlayer.seekTo(target)
                    currentPositionMs = target
                    doubleTapSeekRight = true
                    doubleTapSeekLeft = false
                } else {
                    doubleTapSeekRight = true
                    doubleTapSeekLeft = false
                }
                showControls = true
                true
            }

            // Media Buttons
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                exoPlayer.play()
                isPlaying = true
                true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                exoPlayer.pause()
                isPlaying = false
                true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                true
            }
            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                switchChannel(1)
                true
            }
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                switchChannel(-1)
                true
            }

            // Remote Number Keys (0-9) to directly jump to channel
            KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> { handleNumberInput("0"); true }
            KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> { handleNumberInput("1"); true }
            KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> { handleNumberInput("2"); true }
            KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> { handleNumberInput("3"); true }
            KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> { handleNumberInput("4"); true }
            KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> { handleNumberInput("5"); true }
            KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> { handleNumberInput("6"); true }
            KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> { handleNumberInput("7"); true }
            KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> { handleNumberInput("8"); true }
            KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> { handleNumberInput("9"); true }

            // TV Menu / Info / Guide -> Toggle Channel Drawer
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_PROG_RED -> {
                showQuickChannelDrawer = !showQuickChannelDrawer
                true
            }

            // Back / Escape - Single click exits video player cleanly on TV remote
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE -> {
                if (showQuickChannelDrawer) {
                    showQuickChannelDrawer = false
                    true
                } else {
                    onBack()
                    true
                }
            }
            else -> false
        }
    }

    // Formatting milliseconds to mm:ss
    fun formatTime(ms: Long): String {
        if (ms <= 0L) return "00:00"
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    fun adjustBrightness(delta: Float) {
        currentBrightnessFraction = (currentBrightnessFraction + delta).coerceIn(0.01f, 1.0f)
        val lp = activity?.window?.attributes
        if (lp != null) {
            lp.screenBrightness = currentBrightnessFraction
            activity.window.attributes = lp
        }
        gestureBrightnessPercent = (currentBrightnessFraction * 100).toInt().coerceIn(1, 100)
        brightnessGestureKey = System.currentTimeMillis()
        isBrightnessGestureActive = true
        isVolumeGestureActive = false
        isSeekGestureActive = false
    }

    fun adjustVolume(delta: Float) {
        if (isMuted) {
            isMuted = false
            exoPlayer.volume = 1f
        }
        currentVolumeFraction = (currentVolumeFraction + delta).coerceIn(0f, 1f)
        val newVol = kotlin.math.round(currentVolumeFraction * maxAudioVolume).toInt().coerceIn(0, maxAudioVolume)
        try {
            audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
        } catch (_: Exception) {}
        gestureVolumePercent = (currentVolumeFraction * 100).toInt().coerceIn(0, 100)
        volumeGestureKey = System.currentTimeMillis()
        isVolumeGestureActive = true
        isBrightnessGestureActive = false
        isSeekGestureActive = false
    }

    fun adjustSeekDelta(deltaSec: Float) {
        val effDuration = if (durationMs > 0) durationMs else exoPlayer.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0L
        if (effDuration > 0) {
            seekGestureOffsetSec += deltaSec.toInt()
            val target = (currentPositionMs + seekGestureOffsetSec * 1000L).coerceIn(0L, effDuration)
            seekGestureTargetMs = target
            seekGestureKey = System.currentTimeMillis()
            isSeekGestureActive = true
            isVolumeGestureActive = false
            isBrightnessGestureActive = false
        }
    }

    fun confirmSeek() {
        val effDuration = if (durationMs > 0) durationMs else exoPlayer.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0L
        if (isSeekGestureActive && effDuration > 0) {
            exoPlayer.seekTo(seekGestureTargetMs)
            currentPositionMs = seekGestureTargetMs
            seekGestureOffsetSec = 0
        }
    }

    fun selectVideoQuality(quality: VideoQualityOption) {
        selectedVideoQuality = quality
        if (quality.height <= 0) {
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .clearVideoSizeConstraints()
                .build()
            android.widget.Toast.makeText(context, "ভিডিও কোয়ালিটি: অটো অ্যাডাপটিভ (Auto)", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setMaxVideoSize(Int.MAX_VALUE, quality.height)
                .setMinVideoSize(0, quality.height)
                .build()
            android.widget.Toast.makeText(context, "ভিডিও কোয়ালিটি: ${quality.label}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun selectAudioTrack(audio: AudioTrackOption) {
        selectedAudioTrack = audio
        val builder = exoPlayer.trackSelectionParameters.buildUpon()
        if (audio.groupIndex >= 0 && audio.trackIndex >= 0) {
            val currentTracks = exoPlayer.currentTracks
            if (audio.groupIndex < currentTracks.groups.size) {
                val group = currentTracks.groups[audio.groupIndex]
                val override = androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, listOf(audio.trackIndex))
                builder.setOverrideForType(override)
            }
        }
        if (audio.language.isNotBlank()) {
            builder.setPreferredAudioLanguage(audio.language)
        }
        exoPlayer.trackSelectionParameters = builder.build()
        android.widget.Toast.makeText(context, "অডিও ভাষা: ${audio.displayName}", android.widget.Toast.LENGTH_SHORT).show()
    }

    fun selectSubtitle(sub: SubtitleTrackOption) {
        selectedSubtitle = sub
        if (sub.isOff) {
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            android.widget.Toast.makeText(context, "সাবটাইটেল বন্ধ করা হয়েছে", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setPreferredTextLanguage(sub.language)
                .build()
            android.widget.Toast.makeText(context, "সাবটাইটেল: ${sub.displayName}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val playerModifier = modifier
        .focusRequester(focusRequester)
        .focusable()
        .onKeyEvent { handleRemoteKeyEvent(it) }

    // Dialogs for Quality, Audio Track, and Subtitle Selection
    if (showQualityDialog) {
        VideoQualitySelectionDialog(
            currentSelection = selectedVideoQuality,
            availableOptions = availableVideoQualities,
            onSelect = { selectVideoQuality(it) },
            onDismiss = { showQualityDialog = false }
        )
    }

    if (showAudioDialog) {
        AudioTrackSelectionDialog(
            currentSelection = selectedAudioTrack,
            availableOptions = availableAudioTracks,
            onSelect = { selectAudioTrack(it) },
            onDismiss = { showAudioDialog = false }
        )
    }

    if (showSubtitleDialog) {
        SubtitleSelectionDialog(
            currentSelection = selectedSubtitle,
            availableOptions = availableSubtitles,
            onSelect = { selectSubtitle(it) },
            onDismiss = { showSubtitleDialog = false }
        )
    }

    if (showEpisodesSheet) {
        SeriesDetailsDialog(
            series = currentMedia,
            repository = MediaRepository(context),
            isTvMode = isTvMode,
            onPlayEpisode = { epMedia ->
                currentMedia = epMedia
                onSelectMedia(epMedia)
                showEpisodesSheet = false
            },
            onDismiss = { showEpisodesSheet = false }
        )
    }

    if (showPlayerSettingsDialog) {
        PlayerSettingsDialog(
            isAutoSwitchEnabled = isAutoSwitchOnFailureEnabled,
            onToggleAutoSwitch = { nextState ->
                isAutoSwitchOnFailureEnabled = nextState
                playerRepository.setAutoSwitchChannelOnFailure(nextState)
            },
            currentResolution = currentVideoResolution,
            onDismiss = { showPlayerSettingsDialog = false }
        )
    }

    // Picture-in-Picture (PiP) Window Layout - Clean, clutter-free floating player
    if (isInPipMode) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            if (useWebPlayer) {
                WebStreamPlayer(
                    embedUrl = currentUrl,
                    title = currentMedia.title,
                    modifier = Modifier.fillMaxSize(),
                    onDirectStreamDetected = { directStream: String ->
                        currentUrl = directStream
                        forceWebEngine = false
                    },
                    onClose = {}
                )
            } else {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            this.resizeMode = resizeMode
                            setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                            setKeepContentOnPlayerReset(true)
                            keepScreenOn = true
                            try {
                                setEnableComposeSurfaceSyncWorkaround(true)
                            } catch (_: Throwable) {}
                            try {
                                (videoSurfaceView as? android.view.SurfaceView)?.setZOrderMediaOverlay(true)
                            } catch (_: Throwable) {}
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = { playerView ->
                        playerView.player = exoPlayer
                        playerView.resizeMode = resizeMode
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        return
    }

    if (isFullscreen) {
        // FULLSCREEN LANDSCAPE VIEW (Edge-to-Edge)
        Box(
            modifier = playerModifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { showControls = !showControls },
                        onDoubleTap = { offset ->
                            if (offset.x < size.width / 2) {
                                if (durationMs > 0) {
                                    exoPlayer.seekTo(maxOf(0L, currentPositionMs - 10000L))
                                    doubleTapSeekLeft = true
                                    doubleTapSeekRight = false
                                }
                            } else {
                                if (durationMs > 0) {
                                    exoPlayer.seekTo(minOf(durationMs, currentPositionMs + 10000L))
                                    doubleTapSeekRight = true
                                    doubleTapSeekLeft = false
                                }
                            }
                        }
                    )
                }
                .pointerInput(Unit) {
                    var dragType = MxDragType.NONE
                    var dragStartX = 0f
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragStartX = offset.x
                            dragType = MxDragType.NONE
                            val curVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 7
                            currentVolumeFraction = (curVol.toFloat() / maxAudioVolume.toFloat()).coerceIn(0f, 1f)
                            val curBright = activity?.window?.attributes?.screenBrightness ?: -1f
                            currentBrightnessFraction = if (curBright < 0f) 0.5f else curBright.coerceIn(0.01f, 1.0f)
                        },
                        onDragEnd = {
                            if (dragType == MxDragType.HORIZONTAL) {
                                confirmSeek()
                            }
                            dragType = MxDragType.NONE
                        },
                        onDragCancel = { dragType = MxDragType.NONE },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            if (dragType == MxDragType.NONE) {
                                if (kotlin.math.abs(dragAmount.y) > kotlin.math.abs(dragAmount.x)) {
                                    dragType = if (dragStartX < size.width / 2) MxDragType.VERTICAL_LEFT else MxDragType.VERTICAL_RIGHT
                                } else {
                                    dragType = MxDragType.HORIZONTAL
                                }
                            }

                            when (dragType) {
                                MxDragType.VERTICAL_LEFT -> {
                                    val deltaPercent = -dragAmount.y / (size.height * 0.75f)
                                    adjustBrightness(deltaPercent)
                                }
                                MxDragType.VERTICAL_RIGHT -> {
                                    val deltaPercent = -dragAmount.y / (size.height * 0.75f)
                                    adjustVolume(deltaPercent)
                                }
                                MxDragType.HORIZONTAL -> {
                                    val deltaSec = (dragAmount.x / (size.width * 0.4f)) * 45f
                                    adjustSeekDelta(deltaSec)
                                }
                                MxDragType.NONE -> {}
                            }
                        }
                    )
                }
        ) {
            if (useWebPlayer) {
                WebStreamPlayer(
                    embedUrl = currentUrl,
                    title = currentMedia.title,
                    modifier = Modifier.fillMaxSize(),
                    onDirectStreamDetected = { directStream ->
                        currentUrl = directStream
                        forceWebEngine = false
                    },
                    onClose = { toggleFullscreen() }
                )
            } else {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            this.resizeMode = resizeMode
                            setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                            setKeepContentOnPlayerReset(true)
                            keepScreenOn = true
                            try {
                                setEnableComposeSurfaceSyncWorkaround(true)
                            } catch (_: Throwable) {}
                            try {
                                (videoSurfaceView as? android.view.SurfaceView)?.setZOrderMediaOverlay(true)
                            } catch (_: Throwable) {}
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                    },
                    update = { playerView ->
                        playerView.player = exoPlayer
                        playerView.resizeMode = resizeMode
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Buffering Indicator with NAFI TV Logo & Bengali Loading text
            if (isActuallyBuffering) {
                PlayerBufferingIndicator(
                    mediaTitle = currentMedia.title,
                    isInitialLoad = !hasStartedPlaying,
                    isCompact = false
                )
            }

            // MX Player Gesture HUD Overlays (Left: Brightness, Right: Volume, Center: Seek, DoubleTap Ripple)
            if (isBrightnessGestureActive) {
                MxPlayerBrightnessHud(
                    percent = gestureBrightnessPercent,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 32.dp)
                )
            }

            if (isVolumeGestureActive) {
                MxPlayerVolumeHud(
                    percent = gestureVolumePercent,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 32.dp)
                )
            }

            if (isSeekGestureActive) {
                MxPlayerSeekHud(
                    targetMs = seekGestureTargetMs,
                    durationMs = durationMs,
                    offsetSec = seekGestureOffsetSec,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            if (doubleTapSeekLeft) {
                MxDoubleTapRipple(isForward = false, modifier = Modifier.align(Alignment.CenterStart))
            }
            if (doubleTapSeekRight) {
                MxDoubleTapRipple(isForward = true, modifier = Modifier.align(Alignment.CenterEnd))
            }

            // TV Remote Number Dialing Overlay
            if (remoteNumberBuffer.isNotEmpty()) {
                TvNumberDialOverlay(
                    dialedNumber = remoteNumberBuffer,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }

            // Error Overlay
            if (errorMessage != null) {
                FullscreenErrorOverlay(
                    message = errorMessage ?: "",
                    onRetry = {
                        errorMessage = null
                        playerRetryKey = 0
                        exoPlayer.seekTo(0)
                        exoPlayer.prepare()
                        exoPlayer.play()
                    },
                    onNextServer = if (servers.size > 1) {
                        { cycleNextServer() }
                    } else null,
                    onNextChannel = if (playlist.size > 1) {
                        { switchChannel(1) }
                    } else null,
                    isAutoSwitchEnabled = isAutoSwitchOnFailureEnabled,
                    onToggleAutoSwitch = { nextState ->
                        isAutoSwitchOnFailureEnabled = nextState
                        playerRepository.setAutoSwitchChannelOnFailure(nextState)
                    }
                )
            }

            // Controls Overlay
            if (showControls) {
                FullscreenControlsOverlay(
                    media = currentMedia,
                    currentVideoResolution = currentVideoResolution,
                    servers = servers,
                    selectedServerIndex = selectedServerIndex,
                    onSelectServer = { index -> switchServer(index) },
                    onCycleNextServer = { cycleNextServer() },
                    playPauseFocusRequester = initialControlFocusRequester,
                    isPlaying = isPlaying,
                    isMuted = isMuted,
                    playbackSpeed = playbackSpeed,
                    currentPositionMs = currentPositionMs,
                    durationMs = durationMs,
                    isDraggingSlider = isDraggingSlider,
                    sliderPosition = sliderPosition,
                    selectedVideoQuality = selectedVideoQuality,
                    availableVideoQualities = availableVideoQualities,
                    onOpenQualityDialog = { showQualityDialog = true },
                    selectedAudioTrack = selectedAudioTrack,
                    availableAudioTracks = availableAudioTracks,
                    onOpenAudioDialog = { showAudioDialog = true },
                    selectedSubtitle = selectedSubtitle,
                    availableSubtitles = availableSubtitles,
                    onOpenSubtitleDialog = { showSubtitleDialog = true },
                    onSeekRewind10 = {
                        val cur = exoPlayer.currentPosition.coerceAtLeast(0L)
                        val seekTarget = maxOf(0L, cur - 10000L)
                        exoPlayer.seekTo(seekTarget)
                        currentPositionMs = seekTarget
                    },
                    onSeekForward10 = {
                        val cur = exoPlayer.currentPosition.coerceAtLeast(0L)
                        val effDuration = if (durationMs > 0) durationMs else exoPlayer.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0L
                        val seekTarget = if (effDuration > 0) minOf(effDuration, cur + 10000L) else (cur + 10000L)
                        exoPlayer.seekTo(seekTarget)
                        currentPositionMs = seekTarget
                    },
                    onSliderChange = {
                        isDraggingSlider = true
                        sliderPosition = it
                    },
                    onSliderChangeFinished = {
                        isDraggingSlider = false
                        val effDuration = if (durationMs > 0) durationMs else exoPlayer.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0L
                        if (effDuration > 0) {
                            val seekTo = (sliderPosition * effDuration).toLong()
                            exoPlayer.seekTo(seekTo)
                            currentPositionMs = seekTo
                        }
                    },
                    onPlayPause = {
                        if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                    },
                    onToggleMute = {
                        isMuted = !isMuted
                        exoPlayer.volume = if (isMuted) 0f else 1f
                    },
                    onToggleSpeed = {
                        playbackSpeed = when (playbackSpeed) {
                            1.0f -> 1.25f
                            1.25f -> 1.5f
                            1.5f -> 2.0f
                            else -> 1.0f
                        }
                        exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
                    },
                    onPrevChannel = { switchChannel(-1) },
                    onNextChannel = { switchChannel(1) },
                    onOpenEpisodes = { showEpisodesSheet = true },
                    onNextEpisode = if (currentMedia.isSeries && currentMedia.episodes.isNotEmpty()) { { playNextEpisode() } } else null,
                    onToggleAspect = {
                        resizeMode = when (resizeMode) {
                            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                            AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                    },
                    onEnterPip = { enterPictureInPictureMode() },
                    onToggleFullscreen = { toggleFullscreen() },
                    onToggleChannelDrawer = { showQuickChannelDrawer = !showQuickChannelDrawer },
                    onOpenSettings = { showPlayerSettingsDialog = true },
                    onClose = { toggleFullscreen() }
                )
            }

            // Quick Channel / Episode Drawer in Fullscreen / TV Mode
            val hasSeriesEpisodes = (currentMedia.isSeries || currentMedia.episodes.isNotEmpty()) && currentMedia.episodes.isNotEmpty()
            if (showQuickChannelDrawer && (playlist.isNotEmpty() || hasSeriesEpisodes)) {
                var drawerSearchQuery by remember { mutableStateOf("") }
                var drawerSelectedCategory by remember { mutableStateOf("All") }
                var drawerTab by remember(currentMedia.id) {
                    mutableStateOf(if (hasSeriesEpisodes) "EPISODES" else "CHANNELS")
                }
                var drawerSeasonNum by remember(currentMedia.id) {
                    mutableIntStateOf(if (currentMedia.currentSeasonNum > 0) currentMedia.currentSeasonNum else 1)
                }

                val drawerCategories = remember(playlist) {
                    val cats = playlist.map { it.category.trim() }
                        .filter { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
                        .distinct()
                        .sorted()
                    listOf("All") + cats
                }

                val filteredDrawerPlaylist = remember(playlist, drawerSearchQuery, drawerSelectedCategory) {
                    playlist.filter { item ->
                        val matchesSearch = if (drawerSearchQuery.isBlank()) true else {
                            item.title.contains(drawerSearchQuery, ignoreCase = true) ||
                            item.category.contains(drawerSearchQuery, ignoreCase = true)
                        }
                        val matchesCat = if (drawerSelectedCategory == "All") true else {
                            item.category.trim().equals(drawerSelectedCategory.trim(), ignoreCase = true)
                        }
                        matchesSearch && matchesCat
                    }
                }

                // Filtered Series Episodes for Current Season
                val filteredEpisodes = remember(currentMedia.episodes, drawerSeasonNum, drawerSearchQuery) {
                    currentMedia.episodes.filter { ep ->
                        val matchesSeason = ep.seasonNum == drawerSeasonNum || currentMedia.seasons.isEmpty()
                        val matchesSearch = if (drawerSearchQuery.isBlank()) true else {
                            ep.title.contains(drawerSearchQuery, ignoreCase = true) ||
                            "পর্ব ${ep.episodeNum}".contains(drawerSearchQuery, ignoreCase = true) ||
                            "${ep.episodeNum}".contains(drawerSearchQuery)
                        }
                        matchesSeason && matchesSearch
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(320.dp)
                        .align(Alignment.CenterEnd)
                        .background(Color(0xFF0F172A).copy(alpha = 0.98f))
                        .padding(10.dp)
                ) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (drawerTab == "EPISODES") "📺 পর্বসমূহ (${currentMedia.episodes.size})" else "📺 চ্যানেল তালিকা (${filteredDrawerPlaylist.size})",
                                color = Color(0xFF00E5FF),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp
                            )
                            IconButton(
                                onClick = { showQuickChannelDrawer = false },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }

                        // Tab Switch if both Series Episodes and Playlist are available
                        if (hasSeriesEpisodes && playlist.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (drawerTab == "EPISODES") Color(0xFF0284C7) else Color(0xFF1E293B),
                                    border = BorderStroke(1.dp, if (drawerTab == "EPISODES") Color(0xFF00E5FF) else Color(0xFF334155)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { drawerTab = "EPISODES" }
                                ) {
                                    Text(
                                        text = "📺 পর্বসমূহ",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (drawerTab == "CHANNELS") Color(0xFF0284C7) else Color(0xFF1E293B),
                                    border = BorderStroke(1.dp, if (drawerTab == "CHANNELS") Color(0xFF00E5FF) else Color(0xFF334155)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { drawerTab = "CHANNELS" }
                                ) {
                                    Text(
                                        text = "📡 চ্যানেল/মুভি",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        // Search Box inside Drawer
                        OutlinedTextField(
                            value = drawerSearchQuery,
                            onValueChange = { drawerSearchQuery = it },
                            placeholder = { Text(if (drawerTab == "EPISODES") "পর্ব খুঁজুন (যেমন: ১, ২)..." else "চ্যানেল খুঁজুন...", color = Color(0xFF94A3B8), fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(16.dp)) },
                            trailingIcon = {
                                if (drawerSearchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { drawerSearchQuery = "" },
                                        modifier = Modifier.size(20.dp)
                                    ) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = Color.White, modifier = Modifier.size(13.dp))
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00E5FF),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedContainerColor = Color(0xFF1E293B),
                                unfocusedContainerColor = Color(0xFF1E293B),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Color(0xFF00E5FF)
                            ),
                            singleLine = true
                        )

                        if (drawerTab == "EPISODES") {
                            // Season Chips for Series
                            if (currentMedia.seasons.size > 1) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    items(currentMedia.seasons) { season ->
                                        val isSelected = drawerSeasonNum == season.seasonNumber
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isSelected) Color(0xFF0284C7) else Color(0xFF1E293B),
                                            border = BorderStroke(1.dp, if (isSelected) Color(0xFF00E5FF) else Color(0xFF334155)),
                                            modifier = Modifier.clickable { drawerSeasonNum = season.seasonNumber }
                                        ) {
                                            Text(
                                                text = season.name.ifBlank { "সিজন ${season.seasonNumber}" },
                                                color = Color.White,
                                                fontSize = 10.5.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Episode List in Drawer
                            if (filteredEpisodes.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "কোনো পর্ব পাওয়া যায়নি",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.5.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(filteredEpisodes, key = { it.id }) { ep ->
                                        val isCurrent = ep.id == currentMedia.id || (ep.seasonNum == currentMedia.currentSeasonNum && ep.episodeNum == currentMedia.currentEpisodeNum)
                                        var isFocused by remember { mutableStateOf(false) }

                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .scale(if (isFocused) 1.02f else 1.0f)
                                                .onFocusChanged { isFocused = it.isFocused }
                                                .focusable()
                                                .clickable {
                                                    val epMedia = ep.toMediaItem(currentMedia)
                                                    isBuffering = true
                                                    isActuallyBuffering = true
                                                    hasStartedPlaying = false
                                                    durationMs = 0L
                                                    currentPositionMs = 0L
                                                    currentMedia = epMedia
                                                    selectedServerIndex = 0
                                                    currentUrl = epMedia.streamUrl
                                                    errorMessage = null
                                                    onSelectMedia(epMedia)
                                                    channelOsdKey = System.currentTimeMillis()
                                                    showQuickChannelDrawer = false
                                                    android.widget.Toast.makeText(context, "পর্ব ${ep.episodeNum} শুরু হয়েছে", android.widget.Toast.LENGTH_SHORT).show()
                                                },
                                            shape = RoundedCornerShape(8.dp),
                                            border = when {
                                                isFocused -> BorderStroke(2.dp, Color(0xFFFFD600))
                                                isCurrent -> BorderStroke(1.5.dp, Color(0xFF00E5FF))
                                                else -> BorderStroke(1.dp, Color(0xFF223456))
                                            },
                                            colors = CardDefaults.cardColors(
                                                containerColor = when {
                                                    isFocused -> Color(0xFF1E3A8A)
                                                    isCurrent -> Color(0xFF0284C7).copy(alpha = 0.35f)
                                                    else -> Color(0xFF141F38)
                                                }
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(width = 50.dp, height = 34.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(Color(0xFF0F172A)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = if (isCurrent) Icons.Rounded.PlayArrow else Icons.Rounded.PlayCircle,
                                                        contentDescription = null,
                                                        tint = if (isCurrent) Color(0xFF00E5FF) else Color.White,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(8.dp))

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = "পর্ব ${ep.episodeNum}: ${ep.title.ifBlank { "Episode ${ep.episodeNum}" }}",
                                                            color = if (isFocused) Color(0xFFFFD600) else if (isCurrent) Color(0xFF00E5FF) else Color.White,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 11.5.sp,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.weight(1f, fill = false)
                                                        )
                                                        if (isCurrent) {
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(
                                                                text = "▶ চলছে",
                                                                color = Color(0xFF00E5FF),
                                                                fontWeight = FontWeight.ExtraBold,
                                                                fontSize = 9.sp
                                                            )
                                                        }
                                                    }
                                                    if (!ep.duration.isNullOrBlank()) {
                                                        Text(
                                                            text = "সময়: ${ep.duration}",
                                                            color = Color(0xFF94A3B8),
                                                            fontSize = 9.5.sp
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Categories Horizontal Scroll inside Drawer for Channels
                            if (drawerCategories.size > 1) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    items(drawerCategories) { cat ->
                                        val isSelected = drawerSelectedCategory == cat
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isSelected) Color(0xFF2563EB) else Color(0xFF1E293B),
                                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF00E5FF)) else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                                            modifier = Modifier.clickable { drawerSelectedCategory = cat }
                                        ) {
                                            Text(
                                                text = cat,
                                                color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                                fontSize = 10.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            // Channels List
                            if (filteredDrawerPlaylist.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "কোনো চ্যানেল পাওয়া যায়নি",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.5.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(filteredDrawerPlaylist) { item ->
                                        val isCurrent = item.id == currentMedia.id
                                        var isFocused by remember { mutableStateOf(false) }
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .onFocusChanged { focusState ->
                                                    isFocused = focusState.isFocused
                                                }
                                                .focusable()
                                                .clickable {
                                                    isBuffering = true
                                                    isActuallyBuffering = true
                                                    hasStartedPlaying = false
                                                    durationMs = 0L
                                                    currentPositionMs = 0L
                                                    currentMedia = item
                                                    selectedServerIndex = 0
                                                    val newServers = item.getAllServers()
                                                    val oldUrl = currentUrl
                                                    val nextUrl = newServers.firstOrNull()?.url ?: item.streamUrl
                                                    currentUrl = nextUrl
                                                    errorMessage = null
                                                    playerRetryKey = 0
                                                    forceWebEngine = false
                                                    if (isFullscreen && !isTvMode) {
                                                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                                    }
                                                    onSelectMedia(item)
                                                    showQuickChannelDrawer = false
                                                    channelOsdKey = System.currentTimeMillis()
                                                    android.widget.Toast.makeText(context, "${item.title} চালু হচ্ছে...", android.widget.Toast.LENGTH_SHORT).show()
                                                    if (oldUrl == nextUrl) {
                                                        try {
                                                            exoPlayer.seekTo(0)
                                                            exoPlayer.prepare()
                                                            exoPlayer.play()
                                                        } catch (_: Exception) {}
                                                    }
                                                },
                                            shape = RoundedCornerShape(8.dp),
                                            border = when {
                                                isFocused -> androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF00E5FF))
                                                isCurrent -> androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF38BDF8))
                                                else -> null
                                            },
                                            colors = CardDefaults.cardColors(
                                                containerColor = when {
                                                    isFocused -> Color(0xFF2563EB).copy(alpha = 0.85f)
                                                    isCurrent -> Color(0xFF00E5FF).copy(alpha = 0.25f)
                                                    else -> Color(0xFF1E293B)
                                                }
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                AsyncImage(
                                                    model = item.logoUrl ?: "https://images.unsplash.com/photo-1585829365295-ab7cd400c167?w=100",
                                                    contentDescription = item.title,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(30.dp)
                                                        .clip(CircleShape)
                                                        .background(Color.White)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            text = item.title,
                                                            color = if (isFocused || isCurrent) Color(0xFF00E5FF) else Color.White,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 11.5.sp,
                                                            maxLines = 1,
                                                            modifier = Modifier.weight(1f, fill = false)
                                                        )
                                                        if (isCurrent) {
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text(
                                                                text = "▶ PLAYING",
                                                                color = Color(0xFF00E5FF),
                                                                fontWeight = FontWeight.ExtraBold,
                                                                fontSize = 8.5.sp
                                                            )
                                                        }
                                                    }
                                                    Text(
                                                        text = item.category,
                                                        color = if (isFocused) Color.White.copy(alpha = 0.9f) else Color(0xFF94A3B8),
                                                        fontSize = 9.5.sp
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
    } else {
        // PORTRAIT EMBEDDED PLAYER VIEW (Screenshot 4 layout)
        Column(
            modifier = playerModifier
                .fillMaxSize()
                .background(Color(0xFF0B1120))
        ) {
            // Top App Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF0F172A))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "NAFI TV 24",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFF00E5FF).copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = if (currentMedia.type == com.example.model.MediaType.MOVIE) "Movies" else "Live TV",
                            color = Color(0xFF00E5FF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            toggleFullscreen()
                            android.widget.Toast.makeText(context, "টিভি মোড (TV Mode) সক্রিয় করা হয়েছে", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Tv,
                            contentDescription = "TV Mode",
                            tint = if (isFullscreen) Color(0xFF00E5FF) else Color(0xFF94A3B8),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(Icons.Rounded.VerifiedUser, contentDescription = "Protected", tint = Color(0xFF00E5FF), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = {
                            errorMessage = null
                            exoPlayer.seekTo(0)
                            exoPlayer.prepare()
                            exoPlayer.play()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh", tint = Color.White)
                    }
                }
            }

            // Embedded 16:9 Video Frame
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { showControls = !showControls },
                            onDoubleTap = { offset ->
                                val cur = exoPlayer.currentPosition.coerceAtLeast(0L)
                                val effDuration = if (durationMs > 0) durationMs else exoPlayer.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0L
                                if (offset.x < size.width / 2) {
                                    val seekTarget = maxOf(0L, cur - 10000L)
                                    exoPlayer.seekTo(seekTarget)
                                    currentPositionMs = seekTarget
                                    doubleTapSeekLeft = true
                                    doubleTapSeekRight = false
                                } else {
                                    val seekTarget = if (effDuration > 0) minOf(effDuration, cur + 10000L) else (cur + 10000L)
                                    exoPlayer.seekTo(seekTarget)
                                    currentPositionMs = seekTarget
                                    doubleTapSeekRight = true
                                    doubleTapSeekLeft = false
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        var dragType = MxDragType.NONE
                        var dragStartX = 0f
                        detectDragGestures(
                            onDragStart = { offset ->
                                dragStartX = offset.x
                                dragType = MxDragType.NONE
                                val curVol = audioManager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 7
                                currentVolumeFraction = (curVol.toFloat() / maxAudioVolume.toFloat()).coerceIn(0f, 1f)
                                val curBright = activity?.window?.attributes?.screenBrightness ?: -1f
                                currentBrightnessFraction = if (curBright < 0f) 0.5f else curBright.coerceIn(0.01f, 1.0f)
                            },
                            onDragEnd = {
                                if (dragType == MxDragType.HORIZONTAL) {
                                    confirmSeek()
                                }
                                dragType = MxDragType.NONE
                            },
                            onDragCancel = { dragType = MxDragType.NONE },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                if (dragType == MxDragType.NONE) {
                                    if (kotlin.math.abs(dragAmount.y) > kotlin.math.abs(dragAmount.x)) {
                                        dragType = if (dragStartX < size.width / 2) MxDragType.VERTICAL_LEFT else MxDragType.VERTICAL_RIGHT
                                    } else {
                                        dragType = MxDragType.HORIZONTAL
                                    }
                                }

                                when (dragType) {
                                    MxDragType.VERTICAL_LEFT -> {
                                        val deltaPercent = -dragAmount.y / (size.height * 0.75f)
                                        adjustBrightness(deltaPercent)
                                    }
                                    MxDragType.VERTICAL_RIGHT -> {
                                        val deltaPercent = -dragAmount.y / (size.height * 0.75f)
                                        adjustVolume(deltaPercent)
                                    }
                                    MxDragType.HORIZONTAL -> {
                                        val deltaSec = (dragAmount.x / (size.width * 0.4f)) * 45f
                                        adjustSeekDelta(deltaSec)
                                    }
                                    MxDragType.NONE -> Unit
                                }
                            }
                        )
                    }
            ) {
                if (useWebPlayer) {
                    WebStreamPlayer(
                        embedUrl = currentUrl,
                        title = currentMedia.title,
                        modifier = Modifier.fillMaxSize(),
                        onDirectStreamDetected = { directStream ->
                            currentUrl = directStream
                            forceWebEngine = false
                        },
                        onClose = onBack
                    )
                } else {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false
                                this.resizeMode = resizeMode
                                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                                setKeepContentOnPlayerReset(true)
                                keepScreenOn = true
                                try {
                                    setEnableComposeSurfaceSyncWorkaround(true)
                                } catch (_: Throwable) {}
                                try {
                                    (videoSurfaceView as? android.view.SurfaceView)?.setZOrderMediaOverlay(true)
                                } catch (_: Throwable) {}
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            }
                        },
                        update = { playerView ->
                            playerView.player = exoPlayer
                            playerView.resizeMode = resizeMode
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Top bar overlay inside video player: Close (X) circle button + Server tag + Quality / Audio Badges
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF00E5FF).copy(alpha = 0.2f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(11.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = servers.getOrNull(selectedServerIndex)?.name?.take(10) ?: "MAIN",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Picture-in-Picture (PiP) Button
                        IconButton(
                            onClick = { enterPictureInPictureMode() },
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E293B).copy(alpha = 0.85f))
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PictureInPictureAlt,
                                contentDescription = "PiP Mode",
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(15.dp)
                            )
                        }

                        // Quality quick badge
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF1E293B).copy(alpha = 0.85f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f)),
                            modifier = Modifier.clickable { showQualityDialog = true }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Rounded.HighQuality, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(11.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = selectedVideoQuality?.label ?: if (currentVideoResolution != null) currentVideoResolution ?: "AUTO" else "AUTO",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Audio track quick badge
                        if (availableAudioTracks.size > 1 || (availableAudioTracks.isNotEmpty() && availableAudioTracks.first().language.isNotEmpty())) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF1E293B).copy(alpha = 0.85f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f)),
                                modifier = Modifier.clickable { showAudioDialog = true }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Rounded.Audiotrack, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(11.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = selectedAudioTrack?.displayName?.take(6) ?: "Audio",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Gesture Overlays
                if (isBrightnessGestureActive) {
                    MxPlayerBrightnessHud(
                        percent = gestureBrightnessPercent,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 16.dp)
                    )
                }

                if (isVolumeGestureActive) {
                    MxPlayerVolumeHud(
                        percent = gestureVolumePercent,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 16.dp)
                    )
                }

                if (isSeekGestureActive && durationMs > 0) {
                    MxPlayerSeekHud(
                        targetMs = seekGestureTargetMs,
                        durationMs = durationMs,
                        offsetSec = seekGestureOffsetSec,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                if (doubleTapSeekLeft) {
                    MxDoubleTapRipple(isForward = false, modifier = Modifier.align(Alignment.CenterStart))
                }

                if (doubleTapSeekRight) {
                    MxDoubleTapRipple(isForward = true, modifier = Modifier.align(Alignment.CenterEnd))
                }

                // TV Remote Number Dialing Overlay
                if (remoteNumberBuffer.isNotEmpty()) {
                    TvNumberDialOverlay(
                        dialedNumber = remoteNumberBuffer,
                        modifier = Modifier.align(Alignment.TopEnd)
                    )
                }

                // Buffering Overlay with NAFI TV Logo & Bengali Loading text
                if (isActuallyBuffering) {
                    PlayerBufferingIndicator(
                        mediaTitle = currentMedia.title,
                        isInitialLoad = !hasStartedPlaying,
                        isCompact = true
                    )
                }

                // Error Overlay
                if (errorMessage != null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.padding(16.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.95f))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Rounded.Warning, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(28.dp))
                                Text(text = errorMessage ?: "", color = Color.White, fontSize = 11.sp, textAlign = TextAlign.Center)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Button(
                                        onClick = {
                                            errorMessage = null
                                            playerRetryKey = 0
                                            exoPlayer.seekTo(0)
                                            exoPlayer.prepare()
                                            exoPlayer.play()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text("পুনরায় চেষ্টা", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    if (servers.size > 1) {
                                        Button(
                                            onClick = { cycleNextServer() },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7), contentColor = Color.White),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Text("বিকল্প সার্ভার (${(selectedServerIndex + 1) % servers.size + 1}/${servers.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }

                                    if (playlist.size > 1) {
                                        Button(
                                            onClick = { switchChannel(1) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.White),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                        ) {
                                            Text("পরবর্তী চ্যানেল", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Controls Bar inside Embedded Player (Screenshot 4 style)
                if (showControls) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                                )
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            // Time stamps & Scrubber Slider
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatTime(currentPositionMs),
                                    color = Color(0xFF00E5FF),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                val progressFraction = if (durationMs > 0) {
                                    (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                                } else 0f

                                Slider(
                                    value = if (isDraggingSlider) sliderPosition else progressFraction,
                                    onValueChange = {
                                        isDraggingSlider = true
                                        sliderPosition = it
                                    },
                                    onValueChangeFinished = {
                                        isDraggingSlider = false
                                        val effDuration = if (durationMs > 0) durationMs else exoPlayer.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0L
                                        if (effDuration > 0) {
                                            val seekTo = (sliderPosition * effDuration).toLong()
                                            exoPlayer.seekTo(seekTo)
                                            currentPositionMs = seekTo
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 8.dp)
                                        .height(18.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color(0xFF00E5FF),
                                        activeTrackColor = Color(0xFF00E5FF),
                                        inactiveTrackColor = Color(0xFF334155)
                                    )
                                )

                                Text(
                                    text = if (durationMs > 0) formatTime(durationMs) else if (currentMedia.isLive) "LIVE" else "00:00",
                                    color = if (currentMedia.isLive && durationMs <= 0) Color.Red else Color.LightGray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            // Interactive Controls Row (Screenshot 4 icons)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // Mute / Volume
                                IconButton(
                                    onClick = {
                                        isMuted = !isMuted
                                        exoPlayer.volume = if (isMuted) 0f else 1f
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isMuted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                                        contentDescription = "Mute",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // Playback Speed
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF1E293B),
                                    modifier = Modifier.clickable {
                                        playbackSpeed = when (playbackSpeed) {
                                            1.0f -> 1.25f
                                            1.25f -> 1.5f
                                            1.5f -> 2.0f
                                            else -> 1.0f
                                        }
                                        exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
                                    }
                                ) {
                                    Text(
                                        text = "${playbackSpeed}x",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }

                                // Previous Item
                                IconButton(
                                    onClick = { switchChannel(-1) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SkipPrevious,
                                        contentDescription = "Previous",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // Play / Pause (Large Center Cyan Button)
                                IconButton(
                                    onClick = {
                                        if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00E5FF))
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        tint = Color.Black,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                // Next Item
                                IconButton(
                                    onClick = { switchChannel(1) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.SkipNext,
                                        contentDescription = "Next",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                // Download Button in Control Bar (Strictly for Movie / OTT VOD, not live TV channels)
                                val isPortraitMovie = !currentMedia.isLive &&
                                        currentMedia.type != MediaType.LIVE_TV &&
                                        currentMedia.type != MediaType.LIVE_EVENT &&
                                        (currentMedia.type == MediaType.MOVIE ||
                                         currentMedia.type == MediaType.SERIES ||
                                         currentMedia.tournament == "NAFI_OTT" ||
                                         currentMedia.isSeries)
                                if (isPortraitMovie) {
                                    val ctrlDlContext = LocalContext.current
                                    val ctrlActiveDlMap by MovieDownloadManager.downloadsState.collectAsState()
                                    val ctrlDlProg = ctrlActiveDlMap[currentMedia.id]
                                    val ctrlIsDownloading = ctrlDlProg?.state == DownloadState.DOWNLOADING || ctrlDlProg?.state == DownloadState.PENDING
                                    val ctrlIsDownloaded = MovieDownloadManager.isMovieDownloaded(ctrlDlContext, currentMedia.id)

                                    IconButton(
                                        onClick = {
                                            if (ctrlIsDownloaded) {
                                                Toast.makeText(ctrlDlContext, "মুভিটি ইতিমধ্যে ডাউনলোড করা আছে!", Toast.LENGTH_SHORT).show()
                                            } else if (ctrlIsDownloading) {
                                                Toast.makeText(ctrlDlContext, "মুভি ডাউনলোড হচ্ছে (${ctrlDlProg?.progressPercent ?: 0}%)...", Toast.LENGTH_SHORT).show()
                                            } else {
                                                val dlUrl = servers.getOrNull(selectedServerIndex)?.url ?: currentMedia.streamUrl
                                                MovieDownloadManager.startDownload(
                                                    context = ctrlDlContext,
                                                    mediaItem = currentMedia,
                                                    preferredUrl = dlUrl
                                                )
                                                Toast.makeText(ctrlDlContext, "📥 মুভি ডাউনলোড শুরু হয়েছে!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (ctrlIsDownloaded) Icons.Rounded.CheckCircle else if (ctrlIsDownloading) Icons.Rounded.Downloading else Icons.Rounded.FileDownload,
                                            contentDescription = "Download Movie",
                                            tint = if (ctrlIsDownloaded) Color(0xFF10B981) else if (ctrlIsDownloading) Color(0xFF00E5FF) else Color(0xFF10B981),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                // Previous Episode Button for Series
                                val hasPrevEp = remember(currentMedia) {
                                    val idx = currentMedia.episodes.indexOfFirst {
                                        it.id == currentMedia.id || (it.seasonNum == currentMedia.currentSeasonNum && it.episodeNum == currentMedia.currentEpisodeNum)
                                    }
                                    idx > 0
                                }
                                if (hasPrevEp) {
                                    IconButton(
                                        onClick = {
                                            val idx = currentMedia.episodes.indexOfFirst {
                                                it.id == currentMedia.id || (it.seasonNum == currentMedia.currentSeasonNum && it.episodeNum == currentMedia.currentEpisodeNum)
                                            }
                                            if (idx > 0) {
                                                val prevEp = currentMedia.episodes[idx - 1]
                                                val epMedia = prevEp.toMediaItem(currentMedia)
                                                currentMedia = epMedia
                                                onSelectMedia(epMedia)
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.SkipPrevious,
                                            contentDescription = "Previous Episode",
                                            tint = Color(0xFF00E5FF),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                // Next Episode Button for Series
                                val hasNextEp = remember(currentMedia) {
                                    val idx = currentMedia.episodes.indexOfFirst {
                                        it.id == currentMedia.id || (it.seasonNum == currentMedia.currentSeasonNum && it.episodeNum == currentMedia.currentEpisodeNum)
                                    }
                                    idx != -1 && idx + 1 < currentMedia.episodes.size
                                }
                                if (hasNextEp) {
                                    IconButton(
                                        onClick = {
                                            val idx = currentMedia.episodes.indexOfFirst {
                                                it.id == currentMedia.id || (it.seasonNum == currentMedia.currentSeasonNum && it.episodeNum == currentMedia.currentEpisodeNum)
                                            }
                                            if (idx != -1 && idx + 1 < currentMedia.episodes.size) {
                                                val nextEp = currentMedia.episodes[idx + 1]
                                                val epMedia = nextEp.toMediaItem(currentMedia)
                                                currentMedia = epMedia
                                                onSelectMedia(epMedia)
                                            }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.SkipNext,
                                            contentDescription = "Next Episode",
                                            tint = Color(0xFF00E5FF),
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                // Episodes List Button for Series
                                if (currentMedia.isSeries || currentMedia.episodes.isNotEmpty() || currentMedia.seasons.isNotEmpty()) {
                                    IconButton(
                                        onClick = { showEpisodesSheet = true },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.VideoLibrary,
                                            contentDescription = "All Episodes",
                                            tint = Color(0xFF00E5FF),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                // Aspect Ratio (Tv/Crop)
                                IconButton(
                                    onClick = {
                                        resizeMode = when (resizeMode) {
                                            AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                            AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                        }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.AspectRatio,
                                        contentDescription = "Aspect Ratio",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                // Picture in Picture (PiP)
                                IconButton(
                                    onClick = { enterPictureInPictureMode() },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PictureInPictureAlt,
                                        contentDescription = "Picture in Picture",
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                // Fullscreen Toggle (Auto Rotates to Landscape)
                                IconButton(
                                    onClick = { toggleFullscreen() },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Fullscreen,
                                        contentDescription = "Fullscreen",
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            val isLiveEvent = (currentMedia.type == MediaType.LIVE_EVENT)
            val liveEventScrollState = rememberScrollState()

            // Below Player Content in Portrait: Info, Servers, and Media Switcher
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (isLiveEvent) Modifier.verticalScroll(liveEventScrollState) else Modifier)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                // Live TV check: All Live TV channels (including movie channels like Zee Cinema, Sony Max, Star Movies) must remain in Live TV layout
                val isLiveTvChannel = currentMedia.type == MediaType.LIVE_TV ||
                        currentMedia.isLive ||
                        currentMedia.type == MediaType.LIVE_EVENT ||
                        playlist.any { it.type == MediaType.LIVE_TV || it.isLive }

                val isMovieOrSeries = !isLiveTvChannel && (
                        currentMedia.type == MediaType.MOVIE ||
                        currentMedia.type == MediaType.SERIES ||
                        currentMedia.tournament == "NAFI_OTT" ||
                        currentMedia.isSeries
                )

                var isMovieSimplifiedView by rememberSaveable { mutableStateOf(false) }

                // Title & Details Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentMedia.title,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isMovieOrSeries) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                                listOf(Color(0xFFE50914), Color(0xFFFF3D00))
                                            )
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = when {
                                            currentMedia.isSeries || currentMedia.type == MediaType.SERIES -> "SERIES • S${currentMedia.currentSeasonNum} E${currentMedia.currentEpisodeNum}"
                                            currentMedia.tournament == "NAFI_OTT" || (currentMedia.category ?: "").contains("OTT", ignoreCase = true) -> "NAFI OTT"
                                            else -> "MOVIE"
                                        },
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${currentMedia.category ?: "Cinema"} • ${if (!currentMedia.quality.isNullOrBlank() && currentMedia.quality != "Default") currentMedia.quality else "1080p Full HD"}",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                if (currentMedia.isLive) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(Color.Red)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "LIVE NOW",
                                        color = Color.Red,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = "${currentMedia.category} • ${currentMedia.quality}",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isMovieOrSeries) {
                            Button(
                                onClick = { isMovieSimplifiedView = !isMovieSimplifiedView },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isMovieSimplifiedView) Color(0xFF00E5FF) else Color(0xFF1E293B),
                                    contentColor = if (isMovieSimplifiedView) Color.Black else Color.White
                                ),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                border = if (isMovieSimplifiedView) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                Icon(
                                    imageVector = if (isMovieSimplifiedView) Icons.Rounded.ViewStream else Icons.Rounded.VisibilityOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isMovieSimplifiedView) "স্বাভাবিক ভিউ" else "মুভি লিস্ট ভিউ",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Fullscreen Button Shortcut
                        Button(
                            onClick = { toggleFullscreen() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Rounded.Fullscreen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("ফুল স্ক্রিন", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // =========================================================================
                // PROMINENT DOWNLOAD OPTION BELOW PLAYER (Strictly for Movie Option)
                // =========================================================================
                AnimatedVisibility(visible = !isMovieSimplifiedView) {
                    if (isMovieOrSeries) {
                        val downloadCtx = LocalContext.current
                        val activeDlMap by MovieDownloadManager.downloadsState.collectAsState()
                        val curDlProg = activeDlMap[currentMedia.id]
                        val isCurDownloading = curDlProg?.state == DownloadState.DOWNLOADING || curDlProg?.state == DownloadState.PENDING
                        val isCurDownloaded = MovieDownloadManager.isMovieDownloaded(downloadCtx, currentMedia.id)
                        val downloadedMovieInfo = remember(isCurDownloaded, currentMedia.id) {
                            if (isCurDownloaded) MovieDownloadManager.getDownloadedMovie(downloadCtx, currentMedia.id) else null
                        }

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isCurDownloaded) Color(0xFF064E3B).copy(alpha = 0.55f)
                                else if (isCurDownloading) Color(0xFF0C4A6E).copy(alpha = 0.65f)
                                else Color(0xFF1E293B).copy(alpha = 0.85f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isCurDownloaded) Color(0xFF10B981)
                            else if (isCurDownloading) Color(0xFF00E5FF)
                            else Color(0xFF38BDF8).copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (isCurDownloaded) {
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
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF10B981).copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Rounded.CheckCircle,
                                                contentDescription = null,
                                                tint = Color(0xFF10B981),
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "মুভিটি অফলাইনে সংরক্ষিত আছে",
                                                color = Color(0xFF10B981),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = "ইন্টারনেট ছাড়া চলবে (${downloadedMovieInfo?.fileSizeFormatted ?: "সংরক্ষিত"})",
                                                color = Color(0xFFCBD5E1),
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            val offlineFile = MovieDownloadManager.getDownloadedFile(downloadCtx, currentMedia.id)
                                            if (offlineFile != null && offlineFile.exists()) {
                                                currentUrl = Uri.fromFile(offlineFile).toString()
                                                exoPlayer.setMediaItem(androidx.media3.common.MediaItem.fromUri(Uri.fromFile(offlineFile)))
                                                exoPlayer.prepare()
                                                exoPlayer.play()
                                                Toast.makeText(downloadCtx, "অফলাইন ফাইল থেকে প্লে হচ্ছে...", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(downloadCtx, "ফাইলটি পাওয়া যায়নি!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.White),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("অফলাইন প্লে", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            } else if (isCurDownloading) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        CircularProgressIndicator(
                                            progress = { curDlProg?.progress ?: 0f },
                                            modifier = Modifier.size(28.dp),
                                            color = Color(0xFF00E5FF),
                                            strokeWidth = 3.dp
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = "মুভি ডাউনলোড হচ্ছে... ${curDlProg?.progressPercent ?: 0}%",
                                                color = Color(0xFF00E5FF),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                            Text(
                                                text = "${curDlProg?.downloadedSizeFormatted ?: "0 MB"} / ${curDlProg?.totalSizeFormatted ?: "0 MB"} • ${curDlProg?.speedFormatted ?: "স্পিড গণনা হচ্ছে..."}",
                                                color = Color(0xFFCBD5E1),
                                                fontSize = 10.5.sp
                                            )
                                        }
                                    }
                                    Button(
                                        onClick = {
                                            MovieDownloadManager.cancelDownload(currentMedia.id)
                                            Toast.makeText(downloadCtx, "ডাউনলোড বাতিল করা হয়েছে", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444).copy(alpha = 0.85f), contentColor = Color.White),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("বাতিল", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { curDlProg?.progress ?: 0f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = Color(0xFF00E5FF),
                                    trackColor = Color(0xFF1E293B)
                                )
                            } else {
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
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF10B981).copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.Rounded.FileDownload,
                                                contentDescription = null,
                                                tint = Color(0xFF10B981),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "ডাউনলোড অপশন",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.5.sp
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            val dlUrl = servers.getOrNull(selectedServerIndex)?.url ?: currentMedia.streamUrl
                                            MovieDownloadManager.startDownload(
                                                context = downloadCtx,
                                                mediaItem = currentMedia,
                                                preferredUrl = dlUrl
                                            )
                                            Toast.makeText(downloadCtx, "📥 মুভি ডাউনলোড শুরু হয়েছে!", Toast.LENGTH_SHORT).show()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Color(0xFF10B981),
                                            contentColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                                    ) {
                                        Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "ডাউনলোড",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Multi-Server Chips (Only display when more than 1 server available)
                AnimatedVisibility(visible = !isMovieSimplifiedView) {
                    if (servers.size > 1) {
                        Column {
                            Text(
                                text = "সার্ভার নির্বাচন (${servers.size} টি সার্ভার উপলব্ধ):",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                itemsIndexed(servers) { index, server ->
                                    val isSelected = selectedServerIndex == index
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF1E293B),
                                        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                                        modifier = Modifier.clickable {
                                            switchServer(index)
                                        }
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isSelected) Icons.Rounded.CheckCircle else if (isMovieOrSeries) Icons.Rounded.Movie else Icons.Rounded.Dns,
                                                contentDescription = null,
                                                tint = if (isSelected) Color.Black else Color(0xFF00E5FF),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = server.name,
                                                color = if (isSelected) Color.Black else Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                    }
                }

                // Related / Other Items in Portrait Mode
                if (isLiveEvent) {
                    // Breaking News Bar situated right above the match fan interaction section
                    BreakingNewsTickerBar(
                        tickerText = activeTickerText,
                        isTvMode = isTvMode,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // User requirement: খেলা প্লে করলে নিচের দিকে আগের মতো অন্যান্য খেলা থাকবে না সেখানে কমেন্ট অপশন থাকবে সকল ইউজার সেখানে খেলার রিয়েক্ট করতে পারবে
                    com.example.ui.components.SportsMatchCommentsAndReactionsView(
                        sport = currentMedia,
                        isTvMode = isTvMode,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else if (isMovieOrSeries) {
                    // DEDICATED MOVIE & OTT PLATFORM DETAILS & POSTER GRID VIEW
                    var moviePlayerSearchQuery by rememberSaveable { mutableStateOf("") }
                    val rawMoviePlaylist = playlist.filter {
                        !it.isLive && it.type != MediaType.LIVE_TV && it.type != MediaType.LIVE_EVENT &&
                        (it.type == MediaType.MOVIE ||
                        it.type == MediaType.SERIES ||
                        it.tournament == "NAFI_OTT" ||
                        it.isSeries)
                    }
                    val moviePlaylist = remember(rawMoviePlaylist, moviePlayerSearchQuery) {
                        if (moviePlayerSearchQuery.isBlank()) rawMoviePlaylist else {
                            rawMoviePlaylist.filter {
                                it.title.contains(moviePlayerSearchQuery, ignoreCase = true) ||
                                (it.category ?: "").contains(moviePlayerSearchQuery, ignoreCase = true)
                            }
                        }
                    }

                    // Breaking News Bar situated right above the in-player movie search bar (Always visible as requested)
                    BreakingNewsTickerBar(
                        tickerText = activeTickerText,
                        isTvMode = isTvMode,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    // In-Player Movie Search Bar
                    AnimatedVisibility(visible = !isMovieSimplifiedView) {
                        OutlinedTextField(
                            value = moviePlayerSearchQuery,
                            onValueChange = { moviePlayerSearchQuery = it },
                            placeholder = {
                                Text(
                                    text = "মুভি বা সিরিজ খুঁজুন...",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = "Search Movies",
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            trailingIcon = {
                                if (moviePlayerSearchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { moviePlayerSearchQuery = "" },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = "Clear",
                                            tint = Color(0xFF94A3B8),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFF00E5FF),
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedContainerColor = Color(0xFF0F172A),
                                unfocusedContainerColor = Color(0xFF0F172A),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                        )
                    }

                    // Synopsis / Storyline
                    AnimatedVisibility(visible = !isMovieSimplifiedView && !currentMedia.description.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF1E293B).copy(alpha = 0.7f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "📖 কাহিনী সংক্ষেপ (Storyline):",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = currentMedia.description!!,
                                    color = Color(0xFFCBD5E1),
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Dedicated Series Seasons & Episodes Section
                    if (currentMedia.isSeries || currentMedia.seasons.isNotEmpty() || currentMedia.episodes.isNotEmpty()) {
                        val seriesSeasons = remember(currentMedia) {
                            if (currentMedia.seasons.isNotEmpty()) currentMedia.seasons
                            else {
                                val grp = currentMedia.episodes.groupBy { it.seasonNum }
                                grp.map { (sNum, eps) ->
                                    SeasonInfo(seasonNumber = sNum, name = "Season $sNum", episodeCount = eps.size, episodes = eps)
                                }.sortedBy { it.seasonNumber }
                            }
                        }
                        var selectedSeasonNumber by rememberSaveable(currentMedia.id) {
                            mutableIntStateOf(currentMedia.currentSeasonNum)
                        }
                        val currentSeasonEpisodes = remember(seriesSeasons, selectedSeasonNumber, currentMedia.episodes) {
                            val sObj = seriesSeasons.firstOrNull { it.seasonNumber == selectedSeasonNumber }
                            if (sObj != null && sObj.episodes.isNotEmpty()) sObj.episodes
                            else currentMedia.episodes.filter { it.seasonNum == selectedSeasonNumber }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.VideoLibrary,
                                        contentDescription = null,
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "🍿 সিজন ও পর্বসমূহ (Episodes):",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                                if (currentMedia.currentSeasonNum > 0 && currentMedia.currentEpisodeNum > 0) {
                                    Text(
                                        text = "S${currentMedia.currentSeasonNum} E${currentMedia.currentEpisodeNum}",
                                        color = Color(0xFF00E5FF),
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Season selector chips
                            if (seriesSeasons.size > 1) {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                ) {
                                    items(seriesSeasons) { sInfo ->
                                        val isSel = sInfo.seasonNumber == selectedSeasonNumber
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isSel) Color(0xFF0284C7) else Color(0xFF1E293B),
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (isSel) Color(0xFF00E5FF) else Color(0xFF334155)
                                            ),
                                            modifier = Modifier.clickable { selectedSeasonNumber = sInfo.seasonNumber }
                                        ) {
                                            Text(
                                                text = sInfo.name,
                                                color = if (isSel) Color.White else Color(0xFF94A3B8),
                                                fontSize = 11.5.sp,
                                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            // Episodes list
                            if (currentSeasonEpisodes.isEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF1E293B).copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "এই সিজনে কোনো পর্ব পাওয়া যায়নি বা লোড হচ্ছে...",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.5.sp,
                                        modifier = Modifier.padding(12.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    items(currentSeasonEpisodes) { ep ->
                                        val isEpCurrent = ep.id == currentMedia.id || (ep.seasonNum == currentMedia.currentSeasonNum && ep.episodeNum == currentMedia.currentEpisodeNum)
                                        Card(
                                            shape = RoundedCornerShape(10.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isEpCurrent) Color(0xFF0F2B48) else Color(0xFF141F38)
                                            ),
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                if (isEpCurrent) Color(0xFF00E5FF) else Color(0xFF223456)
                                            ),
                                            modifier = Modifier
                                                .width(160.dp)
                                                .clickable {
                                                    val epMedia = ep.toMediaItem(currentMedia)
                                                    currentMedia = epMedia
                                                    onSelectMedia(epMedia)
                                                }
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp)) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(80.dp)
                                                        .clip(RoundedCornerShape(6.dp))
                                                        .background(Color(0xFF0B132B)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (!ep.logoUrl.isNullOrBlank()) {
                                                        AsyncImage(
                                                            model = ep.logoUrl,
                                                            contentDescription = ep.title,
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    }
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .background(Color.Black.copy(alpha = 0.35f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isEpCurrent) Icons.Rounded.Equalizer else Icons.Rounded.PlayArrow,
                                                            contentDescription = null,
                                                            tint = if (isEpCurrent) Color(0xFF00E5FF) else Color.White,
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = "পর্ব ${ep.episodeNum}: ${ep.title.substringAfter(" - ").substringAfter(" -")}",
                                                    color = if (isEpCurrent) Color(0xFF00E5FF) else Color.White,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                if (!ep.duration.isNullOrBlank()) {
                                                    Text(
                                                        text = "⏱️ ${ep.duration}",
                                                        color = Color(0xFF94A3B8),
                                                        fontSize = 9.5.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Text(
                        text = if (currentMedia.tournament == "NAFI_OTT" || (currentMedia.category ?: "").contains("OTT", ignoreCase = true))
                            "🎬 NAFI OTT প্ল্যাটফর্ম মুভি সমূহ (${moviePlaylist.size}):"
                        else
                            "🎬 আরও মুভি ও সিরিজসমূহ (${moviePlaylist.size}):",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(if (moviePlaylist.isNotEmpty()) moviePlaylist else playlist) { movie ->
                            val isCurrent = movie.id == currentMedia.id || movie.streamUrl == currentMedia.streamUrl
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (!isCurrent) {
                                            isBuffering = true
                                            currentMedia = movie
                                            selectedServerIndex = 0
                                            val newServers = movie.getAllServers()
                                            currentUrl = newServers.firstOrNull()?.url ?: movie.streamUrl
                                            errorMessage = null
                                            onSelectMedia(movie)
                                        }
                                    },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isCurrent) Color(0xFF1E3A8A) else Color(0xFF131D33)
                                ),
                                border = if (isCurrent)
                                    androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF00E5FF))
                                else
                                    androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // Movie Vertical Poster Box
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(135.dp)
                                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                                            .background(Color(0xFF1E293B)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!movie.logoUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = movie.logoUrl,
                                                contentDescription = movie.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Rounded.Movie,
                                                contentDescription = null,
                                                tint = Color(0xFF00E5FF),
                                                modifier = Modifier.size(36.dp)
                                            )
                                        }

                                        // Language Badge on top left (বাংলা, হিন্দি, etc., or NFT)
                                        val movieLang = remember(movie.id, movie.title, movie.category) {
                                            com.example.ui.getMovieLanguageBadge(movie)
                                        }
                                        Surface(
                                            color = when (movieLang) {
                                                "বাংলা", "বাংলা ডাবড" -> Color(0xFF059669)
                                                "হিন্দি", "হিন্দি ডাবড" -> Color(0xFFD97706)
                                                "তামিল", "তামিল ডাবড" -> Color(0xFFE11D48)
                                                "তেলেগু", "তেলেগু ডাবড" -> Color(0xFFEA580C)
                                                "মালায়ালাম" -> Color(0xFF0891B2)
                                                "কন্নড়", "সাউথ" -> Color(0xFFCA8A04)
                                                "ইংরেজি", "ইংরেজি ডাবড" -> Color(0xFF2563EB)
                                                "কোরিয়ান" -> Color(0xFF7C3AED)
                                                "অ্যানিমে" -> Color(0xFFDB2777)
                                                "তুর্কি" -> Color(0xFF0D9488)
                                                "NFT" -> Color(0xFF6366F1)
                                                else -> Color(0xFF6366F1)
                                            },
                                            shape = RoundedCornerShape(bottomEnd = 6.dp),
                                            modifier = Modifier.align(Alignment.TopStart)
                                        ) {
                                            Text(
                                                text = movieLang,
                                                color = Color.White,
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                            )
                                        }

                                        // Currently Playing Indicator Overlay
                                        if (isCurrent) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(Color(0xFF00E5FF).copy(alpha = 0.25f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = Color(0xFF00E5FF)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.PlayArrow,
                                                        contentDescription = null,
                                                        tint = Color.Black,
                                                        modifier = Modifier
                                                            .padding(6.dp)
                                                            .size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Movie Title & Category below poster
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 6.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = movie.title,
                                            color = if (isCurrent) Color(0xFF00E5FF) else Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = movie.category ?: "Cinema",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 9.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Live TV Channels Grid with In-Player Search Bar & Dynamic Category Chips
                    var playerSearchQuery by remember { mutableStateOf("") }
                    var playerSelectedCategory by remember { mutableStateOf("All") }

                    val liveTvPlaylist = remember(playlist, currentMedia) {
                        if (playlist.isNotEmpty()) playlist else listOf(currentMedia)
                    }

                    // Dynamically extract distinct categories from active playlist
                    val playerCategories = remember(liveTvPlaylist) {
                        val dynamicCats = liveTvPlaylist.map { it.category.trim() }
                            .filter { it.isNotBlank() && !it.equals("Unknown", ignoreCase = true) }
                            .distinct()
                            .sorted()
                        listOf("All") + dynamicCats
                    }

                    // Filter channels by in-player search and category
                    val filteredPlayerChannels = remember(liveTvPlaylist, playerSearchQuery, playerSelectedCategory) {
                        liveTvPlaylist.filter { item ->
                            val matchesSearch = if (playerSearchQuery.isBlank()) true else {
                                item.title.contains(playerSearchQuery, ignoreCase = true) ||
                                item.category.contains(playerSearchQuery, ignoreCase = true) ||
                                (item.country != null && item.country.contains(playerSearchQuery, ignoreCase = true))
                            }
                            val matchesCategory = when (playerSelectedCategory) {
                                "All" -> true
                                else -> item.category.trim().equals(playerSelectedCategory.trim(), ignoreCase = true)
                            }
                            matchesSearch && matchesCategory
                        }
                    }

                    // 0. Breaking News Ticker Bar situated right above the Search Bar
                    BreakingNewsTickerBar(
                        tickerText = activeTickerText,
                        isTvMode = isTvMode,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    // 1. Sleek In-Player Search Bar
                    OutlinedTextField(
                        value = playerSearchQuery,
                        onValueChange = { playerSearchQuery = it },
                        placeholder = {
                            Text(
                                text = "টিভি চ্যানেল খুঁজুন (যেমন: DBC, Somoy, T Sports)...",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = "Search",
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (playerSearchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { playerSearchQuery = "" },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Clear",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF1E293B),
                            unfocusedContainerColor = Color(0xFF0F172A),
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Color(0xFF00E5FF)
                        ),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                    )

                    // 2. Dynamic Category Filter Chips
                    if (playerCategories.size > 1) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            items(playerCategories) { category ->
                                val isSelected = playerSelectedCategory == category
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isSelected) Color(0xFF2563EB) else Color(0xFF1E293B),
                                    border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF00E5FF)) else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
                                    modifier = Modifier.clickable { playerSelectedCategory = category }
                                ) {
                                    Text(
                                        text = category,
                                        color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 3. Category & Count Header with Reset Option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📺 ${if (playerSelectedCategory != "All") playerSelectedCategory else "অন্যান্য"} টিভি চ্যানেলসমূহ (${filteredPlayerChannels.size}):",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        if (playerSearchQuery.isNotBlank() || playerSelectedCategory != "All") {
                            Text(
                                text = "সব দেখুন (Reset)",
                                color = Color(0xFF00E5FF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable {
                                    playerSearchQuery = ""
                                    playerSelectedCategory = "All"
                                }
                            )
                        }
                    }

                    // 4. Channels Grid or Empty State
                    if (filteredPlayerChannels.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.TvOff,
                                    contentDescription = null,
                                    tint = Color(0xFF64748B),
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    text = "কোনো চ্যানেল পাওয়া যায়নি",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "অন্য কি-ওয়ার্ড দিয়ে সার্চ করুন অথবা ক্যাটাগরি রিসেট করুন",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filteredPlayerChannels) { item ->
                                val isCurrent = item.id == currentMedia.id || item.streamUrl == currentMedia.streamUrl
                                var isFocused by remember { mutableStateOf(false) }
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(130.dp)
                                        .onFocusChanged { focusState ->
                                            isFocused = focusState.isFocused
                                            if (focusState.isFocused && !isCurrent) {
                                                isBuffering = true
                                                currentMedia = item
                                                selectedServerIndex = 0
                                                val newServers = item.getAllServers()
                                                currentUrl = newServers.firstOrNull()?.url ?: item.streamUrl
                                                errorMessage = null
                                                onSelectMedia(item)
                                            }
                                        }
                                        .focusable()
                                        .clickable {
                                            if (!isCurrent) {
                                                isBuffering = true
                                                currentMedia = item
                                                selectedServerIndex = 0
                                                val newServers = item.getAllServers()
                                                currentUrl = newServers.firstOrNull()?.url ?: item.streamUrl
                                                errorMessage = null
                                                onSelectMedia(item)
                                            }
                                        },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = when {
                                            isFocused -> Color(0xFF2563EB).copy(alpha = 0.85f)
                                            isCurrent -> Color(0xFF1E3A8A)
                                            else -> Color(0xFF1E293B)
                                        }
                                    ),
                                    border = when {
                                        isFocused -> androidx.compose.foundation.BorderStroke(2.5.dp, Color(0xFF00E5FF))
                                        isCurrent -> androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF00E5FF))
                                        else -> androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155).copy(alpha = 0.8f))
                                    }
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 6.dp, vertical = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(CircleShape)
                                                .background(Color.White),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (!item.logoUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = item.logoUrl,
                                                    contentDescription = item.title,
                                                    contentScale = ContentScale.Fit,
                                                    modifier = Modifier.size(38.dp).clip(CircleShape)
                                                )
                                            } else {
                                                val initials = item.title.take(3).uppercase()
                                                Text(
                                                    text = initials,
                                                    color = Color(0xFF0F172A),
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(24.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = item.title,
                                                color = if (isCurrent) Color(0xFF00E5FF) else Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFF0F172A),
                                            border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF334155))
                                        ) {
                                            Text(
                                                text = item.country ?: item.category.take(8).ifBlank { "Live" },
                                                color = if (isCurrent) Color(0xFF00E5FF) else Color(0xFF94A3B8),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
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

// ---------------------------------------------------------------------
// Fullscreen & Embedded Buffering / Loading Overlay Components
// (User Requested: কিছু চ্যানেল প্লে হতে সময় নেয় সেই গুলো প্লে হবার আগে আ্যপ লোগো দেখাবেন লোডিং হচ্ছে এই লেখা লোগোর নিচে থাকবে)
// ---------------------------------------------------------------------
@Composable
private fun PlayerBufferingIndicator(
    mediaTitle: String = "",
    isInitialLoad: Boolean = true,
    isCompact: Boolean = false
) {
    if (isInitialLoad) {
        // Initial channel load: Show branded animated NAFI TV logo with "লোডিং হচ্ছে..."
        PlayerBufferingLogoOverlay(mediaTitle = mediaTitle, isCompact = isCompact)
    } else {
        // Mid-stream micro buffering: Transparent, non-intrusive glowing circular spinner
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFF0F172A).copy(alpha = 0.75f),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF00E5FF).copy(alpha = 0.7f)),
                shadowElevation = 8.dp,
                modifier = Modifier.size(if (isCompact) 48.dp else 60.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(if (isCompact) 26.dp else 34.dp),
                        color = Color(0xFF00E5FF),
                        strokeWidth = 3.dp,
                        trackColor = Color(0xFF334155)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerBufferingLogoOverlay(
    mediaTitle: String = "",
    isCompact: Boolean = false
) {
    val infiniteTransition = rememberInfiniteTransition(label = "player_buffering_anim")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logoPulse"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.92f)),
            border = androidx.compose.foundation.BorderStroke(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    listOf(
                        Color(0xFF00E5FF).copy(alpha = glowAlpha),
                        Color(0xFF3B82F6).copy(alpha = glowAlpha),
                        Color(0xFFA855F7).copy(alpha = glowAlpha * 0.7f)
                    )
                )
            ),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = if (isCompact) 20.dp else 32.dp,
                    vertical = if (isCompact) 14.dp else 22.dp
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 12.dp)
            ) {
                // Animated NAFI TV Logo with Glowing Ambient Aura
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(if (isCompact) 56.dp else 76.dp)
                ) {
                    // Outer Soft Halo
                    Box(
                        modifier = Modifier
                            .size(if (isCompact) 56.dp else 76.dp)
                            .scale(pulseScale * 1.12f)
                            .alpha(glowAlpha * 0.4f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                Brush.radialGradient(
                                    listOf(Color(0xFF00E5FF), Color(0xFF3B82F6), Color.Transparent)
                                )
                            )
                    )

                    // Sharp Logo
                    Image(
                        painter = painterResource(id = R.drawable.app_logo),
                        contentDescription = "NAFI TV Logo",
                        modifier = Modifier
                            .size(if (isCompact) 48.dp else 64.dp)
                            .scale(pulseScale)
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Fit
                    )
                }

                // Bengali "লোডিং হচ্ছে..." text (As requested: "লোডিং হচ্ছে এই লেখা লোগোর নিচে থাকবে")
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "লোডিং হচ্ছে...",
                        color = Color.White,
                        fontSize = if (isCompact) 14.sp else 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    if (mediaTitle.isNotBlank()) {
                        Text(
                            text = mediaTitle,
                            color = Color(0xFF00E5FF),
                            fontSize = if (isCompact) 11.sp else 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Smooth Glowing Progress Bar
                LinearProgressIndicator(
                    modifier = Modifier
                        .width(if (isCompact) 100.dp else 130.dp)
                        .height(3.dp)
                        .clip(CircleShape),
                    color = Color(0xFF00E5FF),
                    trackColor = Color(0xFF334155)
                )

                // Subtitle Badge
                Text(
                    text = "NAFI TV 24 • অটো অ্যাডাপটিভ স্ট্রিমিং",
                    color = Color(0xFF94A3B8),
                    fontSize = if (isCompact) 9.sp else 10.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun FullscreenErrorOverlay(
    message: String,
    onRetry: () -> Unit,
    onSwitchToWebPlayer: (() -> Unit)? = null,
    onNextServer: (() -> Unit)? = null,
    onNextChannel: (() -> Unit)? = null,
    isAutoSwitchEnabled: Boolean = true,
    onToggleAutoSwitch: ((Boolean) -> Unit)? = null
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.padding(24.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B).copy(alpha = 0.95f))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(36.dp))
                Text(text = message, color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center)
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onRetry,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("পুনরায় চেষ্টা", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    if (onNextServer != null) {
                        Button(
                            onClick = onNextServer,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7), contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("বিকল্প সার্ভার", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (onNextChannel != null) {
                        Button(
                            onClick = onNextChannel,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Rounded.SkipNext, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("পরবর্তী চ্যানেল", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Auto-Switch Toggle Chip on Error Screen for full user transparency & control
                if (onToggleAutoSwitch != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF0F172A),
                        border = androidx.compose.foundation.BorderStroke(1.dp, if (isAutoSwitchEnabled) Color(0xFF10B981).copy(alpha = 0.6f) else Color(0xFF64748B)),
                        modifier = Modifier.clickable { onToggleAutoSwitch(!isAutoSwitchEnabled) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isAutoSwitchEnabled) Icons.Rounded.Autorenew else Icons.Rounded.Block,
                                contentDescription = null,
                                tint = if (isAutoSwitchEnabled) Color(0xFF34D399) else Color(0xFF94A3B8),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "চ্যানেল প্লে না হলে জোরপূর্বক পরবর্তী চ্যানেলে যাওয়া: " + if (isAutoSwitchEnabled) "চালু (ON)" else "বন্ধ (OFF)",
                                color = if (isAutoSwitchEnabled) Color(0xFFF1F5F9) else Color(0xFF94A3B8),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerSettingsDialog(
    isAutoSwitchEnabled: Boolean,
    onToggleAutoSwitch: (Boolean) -> Unit,
    currentResolution: String?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Rounded.Settings, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(32.dp))
        },
        title = {
            Text("⚙️ প্লেয়ার সেটিংস (Player Settings)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Auto switch on failure toggle
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "চ্যানেল প্লে না হলে পরবর্তী চ্যানেলে যাওয়া",
                                color = Color.White,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "কোন চ্যানেল প্লে না হলে বা আটকে থাকলে অনুমতি ছাড়াই জোরপূর্বক পরবর্তী চ্যানেলে চলে যাবে।",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.5.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = isAutoSwitchEnabled,
                            onCheckedChange = onToggleAutoSwitch,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color(0xFF00E5FF),
                                checkedTrackColor = Color(0xFF0284C7)
                            )
                        )
                    }
                }

                // Compatibility Engine Info
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.Verified, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                            Text("Televizo ও VLC সামঞ্জস্যপূর্ণ ইঞ্জিন", color = Color(0xFF34D399), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            "Akamai, Tapmad, Toffee ও HLS/TS স্ট্রিমগুলো নির্বিঘ্নে চালানোর জন্য মাল্টি-স্টেজ অটো রিট্রাই এবং রিল্যাক্সড SSL সিকিউরিটি সক্রিয় রয়েছে।",
                            color = Color(0xFF94A3B8),
                            fontSize = 10.5.sp
                        )
                    }
                }

                if (currentResolution != null) {
                    Text(
                        "বর্তমান ভিডিও রেজোলিউশন: $currentResolution",
                        color = Color(0xFFBAE6FD),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF), contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("ঠিক আছে", fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Color(0xFF1E293B)
    )
}

@Composable
fun TvPlayerIconButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    tint: Color = Color.White,
    activeBackground: Color? = null,
    size: androidx.compose.ui.unit.Dp = 42.dp,
    iconSize: androidx.compose.ui.unit.Dp = 24.dp
) {
    var isFocused by remember { mutableStateOf(false) }
    var mod = modifier
        .size(size)
        .clip(CircleShape)
        .onFocusChanged { isFocused = it.isFocused }
        .focusable()

    if (focusRequester != null) {
        mod = mod.focusRequester(focusRequester)
    }

    val animatedScale by animateFloatAsState(
        targetValue = if (isFocused) 1.18f else 1.0f,
        label = "btnScale"
    )

    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = when {
            isFocused -> Color(0xFF00E5FF)
            activeBackground != null -> activeBackground
            else -> Color.Black.copy(alpha = 0.5f)
        },
        border = if (isFocused) androidx.compose.foundation.BorderStroke(2.dp, Color.White) else null,
        modifier = mod.scale(animatedScale)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (isFocused) Color.Black else tint,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
fun TvPlayerServerChip(
    server: StreamServer,
    index: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    var mod = modifier
        .onFocusChanged { isFocused = it.isFocused }
        .focusable()

    if (focusRequester != null) {
        mod = mod.focusRequester(focusRequester)
    }

    val animatedScale by animateFloatAsState(
        targetValue = if (isFocused) 1.10f else 1.0f,
        label = "serverChipScale"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = when {
            isFocused -> Color(0xFF00E5FF)
            isSelected -> Color(0xFF00E5FF).copy(alpha = 0.9f)
            else -> Color(0xFF1E293B).copy(alpha = 0.95f)
        },
        border = when {
            isFocused -> androidx.compose.foundation.BorderStroke(2.5.dp, Color.White)
            isSelected -> androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF00E5FF))
            else -> androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF475569))
        },
        modifier = mod.scale(animatedScale)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.Dns,
                contentDescription = null,
                tint = if (isFocused || isSelected) Color.Black else Color(0xFF00E5FF),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = server.name,
                color = if (isFocused || isSelected) Color.Black else Color.White,
                fontSize = 13.sp,
                fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.Medium
            )
            if (isSelected) {
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Black.copy(alpha = 0.25f)
                ) {
                    Text(
                        text = "সক্রিয়",
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TvPlayerActionChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accentColor: Color = Color(0xFF00E5FF),
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val animatedScale by animateFloatAsState(
        targetValue = if (isFocused) 1.10f else 1.0f,
        label = "actionChipScale"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (isFocused) accentColor else Color(0xFF0F172A).copy(alpha = 0.85f),
        border = if (isFocused) androidx.compose.foundation.BorderStroke(2.dp, Color.White)
                 else androidx.compose.foundation.BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .scale(animatedScale)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isFocused) Color.Black else accentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                color = if (isFocused) Color.Black else Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun FullscreenControlsOverlay(
    media: AppMediaItem,
    currentVideoResolution: String? = null,
    servers: List<StreamServer>,
    selectedServerIndex: Int,
    onSelectServer: (Int) -> Unit,
    onCycleNextServer: () -> Unit = {},
    playPauseFocusRequester: FocusRequester? = null,
    isPlaying: Boolean,
    isMuted: Boolean,
    playbackSpeed: Float,
    currentPositionMs: Long,
    durationMs: Long,
    isDraggingSlider: Boolean,
    sliderPosition: Float,
    selectedVideoQuality: VideoQualityOption?,
    availableVideoQualities: List<VideoQualityOption>,
    onOpenQualityDialog: () -> Unit,
    selectedAudioTrack: AudioTrackOption?,
    availableAudioTracks: List<AudioTrackOption>,
    onOpenAudioDialog: () -> Unit,
    selectedSubtitle: SubtitleTrackOption?,
    availableSubtitles: List<SubtitleTrackOption>,
    onOpenSubtitleDialog: () -> Unit,
    onSeekRewind10: () -> Unit,
    onSeekForward10: () -> Unit,
    onSliderChange: (Float) -> Unit,
    onSliderChangeFinished: () -> Unit,
    onPlayPause: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeed: () -> Unit,
    onPrevChannel: () -> Unit,
    onNextChannel: () -> Unit,
    onOpenEpisodes: () -> Unit = {},
    onNextEpisode: (() -> Unit)? = null,
    onToggleAspect: () -> Unit,
    onEnterPip: () -> Unit = {},
    onToggleFullscreen: () -> Unit,
    onToggleChannelDrawer: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onClose: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.90f), Color.Transparent)
                    )
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f, fill = false)) {
                TvPlayerIconButton(
                    onClick = onClose,
                    icon = Icons.Rounded.ArrowBack,
                    contentDescription = "Back",
                    size = 38.dp,
                    iconSize = 22.dp
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = media.title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = if (currentVideoResolution != null) "${media.category} • $currentVideoResolution" else "${media.category} • AUTO (${media.quality})",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                }
            }

            // Top action buttons: Quality, Audio Track, Subtitles, Aspect Ratio, Channel List, Exit
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Episodes Selector Button for Series
                if (media.isSeries || media.episodes.isNotEmpty() || media.seasons.isNotEmpty()) {
                    TvPlayerActionChip(
                        icon = Icons.Rounded.VideoLibrary,
                        label = "সকল পর্ব (${if (media.episodes.isNotEmpty()) "${media.episodes.size}" else "Episodes"})",
                        accentColor = Color(0xFFFFD600),
                        onClick = onOpenEpisodes
                    )
                }

                // Next Episode Button for Series
                if (onNextEpisode != null) {
                    TvPlayerActionChip(
                        icon = Icons.Rounded.SkipNext,
                        label = "পরবর্তী পর্ব",
                        accentColor = Color(0xFF00E5FF),
                        onClick = onNextEpisode
                    )
                }

                // Quality Selector Button
                TvPlayerActionChip(
                    icon = Icons.Rounded.HighQuality,
                    label = selectedVideoQuality?.label ?: if (availableVideoQualities.isNotEmpty()) "Quality" else "Auto",
                    accentColor = Color(0xFF00E5FF),
                    onClick = onOpenQualityDialog
                )

                // Audio Language Button
                if (availableAudioTracks.size > 1 || (availableAudioTracks.isNotEmpty() && availableAudioTracks.first().language.isNotEmpty())) {
                    TvPlayerActionChip(
                        icon = Icons.Rounded.Audiotrack,
                        label = selectedAudioTrack?.displayName?.take(10) ?: "Audio",
                        accentColor = Color(0xFF38BDF8),
                        onClick = onOpenAudioDialog
                    )
                }

                // Subtitle / Closed Caption Button
                if (availableSubtitles.isNotEmpty()) {
                    TvPlayerActionChip(
                        icon = Icons.Rounded.Subtitles,
                        label = if (selectedSubtitle?.isOff == false) selectedSubtitle.displayName.take(8) else "CC",
                        accentColor = Color(0xFFA855F7),
                        onClick = onOpenSubtitleDialog
                    )
                }

                // Download Movie / Video Button
                val downloadContext = LocalContext.current
                val activeDownloadsMap by MovieDownloadManager.downloadsState.collectAsState()
                val currentProg = activeDownloadsMap[media.id]
                val isDownloading = currentProg?.state == DownloadState.DOWNLOADING || currentProg?.state == DownloadState.PENDING
                val isDownloaded = MovieDownloadManager.isMovieDownloaded(downloadContext, media.id)

                if (!media.isLive && media.type != MediaType.LIVE_TV && media.type != MediaType.LIVE_EVENT && (media.type == MediaType.MOVIE || media.type == MediaType.SERIES)) {
                    TvPlayerActionChip(
                        icon = if (isDownloaded) Icons.Rounded.CheckCircle else if (isDownloading) Icons.Rounded.Downloading else Icons.Rounded.FileDownload,
                        label = if (isDownloaded) "Saved" else if (isDownloading) "${currentProg?.progressPercent ?: 0}%" else "Download",
                        accentColor = if (isDownloaded) Color(0xFF10B981) else if (isDownloading) Color(0xFF00E5FF) else Color(0xFF10B981),
                        onClick = {
                            if (isDownloaded) {
                                android.widget.Toast.makeText(downloadContext, "মুভিটি ইতিমধ্যে ডিভাইসে সংরক্ষিত আছে!", android.widget.Toast.LENGTH_SHORT).show()
                            } else if (isDownloading) {
                                android.widget.Toast.makeText(downloadContext, "মুভিটি ডাউনলোড হচ্ছে (${currentProg?.progressPercent ?: 0}%)...", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                val currentUrl = servers.getOrNull(selectedServerIndex)?.url ?: media.streamUrl
                                MovieDownloadManager.startDownload(
                                    context = downloadContext,
                                    mediaItem = media,
                                    preferredUrl = currentUrl
                                )
                            }
                        }
                    )
                }

                // Quick Channel Drawer Toggle
                TvPlayerIconButton(
                    onClick = onToggleChannelDrawer,
                    icon = Icons.Rounded.FormatListBulleted,
                    contentDescription = "Channel List",
                    tint = Color(0xFF00E5FF),
                    size = 38.dp,
                    iconSize = 22.dp
                )

                // Aspect Ratio
                TvPlayerIconButton(
                    onClick = onToggleAspect,
                    icon = Icons.Rounded.AspectRatio,
                    contentDescription = "Aspect Ratio",
                    size = 38.dp,
                    iconSize = 22.dp
                )

                // Picture-in-Picture (PiP)
                TvPlayerIconButton(
                    onClick = onEnterPip,
                    icon = Icons.Rounded.PictureInPictureAlt,
                    contentDescription = "Picture in Picture",
                    tint = Color(0xFF00E5FF),
                    size = 38.dp,
                    iconSize = 22.dp
                )

                // Player Settings
                TvPlayerIconButton(
                    onClick = onOpenSettings,
                    icon = Icons.Rounded.Settings,
                    contentDescription = "Player Settings",
                    tint = Color(0xFF00E5FF),
                    size = 38.dp,
                    iconSize = 22.dp
                )

                // Exit Fullscreen
                TvPlayerIconButton(
                    onClick = onToggleFullscreen,
                    icon = Icons.Rounded.FullscreenExit,
                    contentDescription = "Exit Fullscreen",
                    tint = Color(0xFF00E5FF),
                    size = 38.dp,
                    iconSize = 22.dp
                )
            }
        }

        // Center Quick Skip Buttons
        if (durationMs > 0) {
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 60.dp),
                horizontalArrangement = Arrangement.spacedBy(80.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TvPlayerIconButton(
                    onClick = onSeekRewind10,
                    icon = Icons.Rounded.Replay10,
                    contentDescription = "Rewind 10s",
                    size = 52.dp,
                    iconSize = 30.dp
                )

                TvPlayerIconButton(
                    onClick = onSeekForward10,
                    icon = Icons.Rounded.Forward10,
                    contentDescription = "Forward 10s",
                    size = 52.dp,
                    iconSize = 30.dp
                )
            }
        }

        // Bottom Controls Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.95f))
                    )
                )
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Dedicated High-Visibility TV Multi-Server Switcher Bar
                if (servers.size > 1) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A).copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Dns,
                                    contentDescription = null,
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "সার্ভার নির্বাচন (${servers.size} টি সার্ভার উপলব্ধ):",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "রিমোটের হলুদ/সবুজ বাটন দিয়ে পরিবর্তন করুন",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp
                            )
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            itemsIndexed(servers) { index, server ->
                                val isSelected = selectedServerIndex == index
                                TvPlayerServerChip(
                                    server = server,
                                    index = index,
                                    isSelected = isSelected,
                                    onClick = { onSelectServer(index) }
                                )
                            }
                        }
                    }
                }

                // Progress Scrubber
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val formatTime = { ms: Long ->
                        if (ms <= 0L) "00:00"
                        else String.format("%02d:%02d", (ms / 1000) / 60, (ms / 1000) % 60)
                    }

                    Text(
                        text = formatTime(currentPositionMs),
                        color = Color(0xFF00E5FF),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )

                    val progressFraction = if (durationMs > 0) {
                        (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    } else 0f

                    Slider(
                        value = if (isDraggingSlider) sliderPosition else progressFraction,
                        onValueChange = onSliderChange,
                        onValueChangeFinished = onSliderChangeFinished,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                            .height(20.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00E5FF),
                            activeTrackColor = Color(0xFF00E5FF),
                            inactiveTrackColor = Color(0xFF334155)
                        )
                    )

                    Text(
                        text = if (durationMs > 0) formatTime(durationMs) else if (media.isLive) "LIVE" else "00:00",
                        color = if (media.isLive && durationMs <= 0) Color.Red else Color.LightGray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Main Controls row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Mute Toggle
                    TvPlayerIconButton(
                        onClick = onToggleMute,
                        icon = if (isMuted) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                        contentDescription = "Mute",
                        size = 38.dp,
                        iconSize = 22.dp
                    )

                    // Speed Toggle
                    TvPlayerActionChip(
                        icon = Icons.Rounded.Speed,
                        label = "${playbackSpeed}x",
                        accentColor = Color.White,
                        onClick = onToggleSpeed
                    )

                    // Prev Channel
                    TvPlayerIconButton(
                        onClick = onPrevChannel,
                        icon = Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous Channel",
                        size = 40.dp,
                        iconSize = 26.dp
                    )

                    // 10s Rewind
                    TvPlayerIconButton(
                        onClick = onSeekRewind10,
                        icon = Icons.Rounded.Replay10,
                        contentDescription = "Rewind 10s",
                        size = 40.dp,
                        iconSize = 24.dp
                    )

                    // Center Play/Pause (Auto-focused on remote open)
                    TvPlayerIconButton(
                        onClick = onPlayPause,
                        icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Play/Pause",
                        focusRequester = playPauseFocusRequester,
                        activeBackground = Color(0xFF00E5FF),
                        tint = Color.Black,
                        size = 50.dp,
                        iconSize = 30.dp
                    )

                    // 10s Forward
                    TvPlayerIconButton(
                        onClick = onSeekForward10,
                        icon = Icons.Rounded.Forward10,
                        contentDescription = "Forward 10s",
                        size = 40.dp,
                        iconSize = 24.dp
                    )

                    // Next Channel
                    TvPlayerIconButton(
                        onClick = onNextChannel,
                        icon = Icons.Rounded.SkipNext,
                        contentDescription = "Next Channel",
                        size = 40.dp,
                        iconSize = 26.dp
                    )

                    // Download Button on Fullscreen Control Bar (Strictly for Movie / OTT option, not live streams)
                    val isFsMovie = !media.isLive &&
                            media.type != MediaType.LIVE_TV &&
                            media.type != MediaType.LIVE_EVENT &&
                            (media.type == MediaType.MOVIE ||
                             media.type == MediaType.SERIES ||
                             media.tournament == "NAFI_OTT" ||
                             media.isSeries)
                    if (isFsMovie) {
                        val fsDlContext = LocalContext.current
                        val fsActiveDlMap by MovieDownloadManager.downloadsState.collectAsState()
                        val fsDlProg = fsActiveDlMap[media.id]
                        val fsIsDownloading = fsDlProg?.state == DownloadState.DOWNLOADING || fsDlProg?.state == DownloadState.PENDING
                        val fsIsDownloaded = MovieDownloadManager.isMovieDownloaded(fsDlContext, media.id)

                        TvPlayerActionChip(
                            icon = if (fsIsDownloaded) Icons.Rounded.CheckCircle else if (fsIsDownloading) Icons.Rounded.Downloading else Icons.Rounded.FileDownload,
                            label = if (fsIsDownloaded) "সংরক্ষিত" else if (fsIsDownloading) "${fsDlProg?.progressPercent ?: 0}%" else "ডাউনলোড করুন",
                            accentColor = if (fsIsDownloaded) Color(0xFF10B981) else if (fsIsDownloading) Color(0xFF00E5FF) else Color(0xFF10B981),
                            onClick = {
                                if (fsIsDownloaded) {
                                    Toast.makeText(fsDlContext, "মুভিটি ইতিমধ্যে ডাউনলোড করা আছে!", Toast.LENGTH_SHORT).show()
                                } else if (fsIsDownloading) {
                                    Toast.makeText(fsDlContext, "মুভি ডাউনলোড হচ্ছে (${fsDlProg?.progressPercent ?: 0}%)...", Toast.LENGTH_SHORT).show()
                                } else {
                                    val dlUrl = servers.getOrNull(selectedServerIndex)?.url ?: media.streamUrl
                                    MovieDownloadManager.startDownload(
                                        context = fsDlContext,
                                        mediaItem = media,
                                        preferredUrl = dlUrl
                                    )
                                    Toast.makeText(fsDlContext, "📥 মুভি ডাউনলোড শুরু হয়েছে!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }

                    // Server switch shortcut button on control bar
                    if (servers.size > 1) {
                        TvPlayerActionChip(
                            icon = Icons.Rounded.Dns,
                            label = "সার্ভার ${selectedServerIndex + 1}/${servers.size}",
                            accentColor = Color(0xFF00E5FF),
                            onClick = onCycleNextServer
                        )
                    }

                    // Aspect Ratio
                    TvPlayerIconButton(
                        onClick = onToggleAspect,
                        icon = Icons.Rounded.AspectRatio,
                        contentDescription = "Aspect Ratio",
                        size = 38.dp,
                        iconSize = 22.dp
                    )

                    // Fullscreen exit
                    TvPlayerIconButton(
                        onClick = onToggleFullscreen,
                        icon = Icons.Rounded.FullscreenExit,
                        contentDescription = "Exit Fullscreen",
                        tint = Color(0xFF00E5FF),
                        size = 38.dp,
                        iconSize = 22.dp
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------
// MX PLAYER GESTURE HUDs & DIALOGS
// -------------------------------------------------------------

@Composable
fun MxPlayerVolumeHud(percent: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF0F172A).copy(alpha = 0.85f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (percent == 0) Icons.Rounded.VolumeMute else if (percent < 50) Icons.Rounded.VolumeDown else Icons.Rounded.VolumeUp,
                contentDescription = null,
                tint = Color(0xFF00E5FF),
                modifier = Modifier.size(26.dp)
            )
            LinearProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier
                    .width(60.dp)
                    .height(4.dp)
                    .clip(CircleShape),
                color = Color(0xFF00E5FF),
                trackColor = Color(0xFF334155)
            )
            Text(
                text = "ভলিউম $percent%",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun MxPlayerBrightnessHud(percent: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF0F172A).copy(alpha = 0.85f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFACC15).copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.BrightnessHigh,
                contentDescription = null,
                tint = Color(0xFFFACC15),
                modifier = Modifier.size(26.dp)
            )
            LinearProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier
                    .width(60.dp)
                    .height(4.dp)
                    .clip(CircleShape),
                color = Color(0xFFFACC15),
                trackColor = Color(0xFF334155)
            )
            Text(
                text = "ব্রাইটনেস $percent%",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun MxPlayerSeekHud(
    targetMs: Long,
    durationMs: Long,
    offsetSec: Int,
    modifier: Modifier = Modifier
) {
    val formatTime = { ms: Long ->
        if (ms <= 0L) "00:00"
        else String.format("%02d:%02d", (ms / 1000) / 60, (ms / 1000) % 60)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF0F172A).copy(alpha = 0.9f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = if (offsetSec >= 0) Icons.Rounded.FastForward else Icons.Rounded.FastRewind,
                contentDescription = null,
                tint = Color(0xFF00E5FF),
                modifier = Modifier.size(28.dp)
            )
            Column {
                Text(
                    text = "${if (offsetSec >= 0) "+$offsetSec" else "$offsetSec"} সেকেন্ড",
                    color = Color(0xFF00E5FF),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${formatTime(targetMs)} / ${formatTime(durationMs)}",
                    color = Color.White,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun MxDoubleTapRipple(isForward: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(horizontal = 40.dp),
        shape = CircleShape,
        color = Color(0xFF00E5FF).copy(alpha = 0.25f)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (isForward) Icons.Rounded.Forward10 else Icons.Rounded.Replay10,
                contentDescription = null,
                tint = Color(0xFF00E5FF),
                modifier = Modifier.size(36.dp)
            )
            Text(
                text = if (isForward) "+10s" else "-10s",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun VideoQualitySelectionDialog(
    currentSelection: VideoQualityOption?,
    availableOptions: List<VideoQualityOption>,
    onSelect: (VideoQualityOption) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E293B),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.HighQuality, contentDescription = null, tint = Color(0xFF00E5FF))
                Spacer(modifier = Modifier.width(8.dp))
                Text("ভিডিও কোয়ালিটি নির্বাচন করুন", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Auto / Adaptive option
                val isAutoSelected = currentSelection == null || currentSelection.height <= 0
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isAutoSelected) Color(0xFF00E5FF).copy(alpha = 0.2f) else Color(0xFF0F172A),
                    border = if (isAutoSelected) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF00E5FF)) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelect(VideoQualityOption(label = "অটো (Auto Adapt)", height = 0, bitrate = 0, isAuto = true))
                            onDismiss()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("অটো অ্যাডাপটিভ (Auto)", color = if (isAutoSelected) Color(0xFF00E5FF) else Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("ইন্টারনেট স্পিড অনুযায়ী স্বয়ংক্রিয় অ্যাডজাস্ট", color = Color(0xFF94A3B8), fontSize = 11.sp)
                        }
                        if (isAutoSelected) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
                        }
                    }
                }

                // Explicit heights
                availableOptions.forEach { opt ->
                    val isSelected = currentSelection?.height == opt.height
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) Color(0xFF00E5FF).copy(alpha = 0.2f) else Color(0xFF0F172A),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF00E5FF)) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(opt)
                                onDismiss()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(opt.label, color = if (isSelected) Color(0xFF00E5FF) else Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                if (opt.bitrate > 0) {
                                    Text("${opt.bitrate / 1000} kbps", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                }
                            }
                            if (isSelected) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("বন্ধ করুন", color = Color(0xFF00E5FF))
            }
        }
    )
}

@Composable
fun AudioTrackSelectionDialog(
    currentSelection: AudioTrackOption?,
    availableOptions: List<AudioTrackOption>,
    onSelect: (AudioTrackOption) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E293B),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Audiotrack, contentDescription = null, tint = Color(0xFF38BDF8))
                Spacer(modifier = Modifier.width(8.dp))
                Text("অডিও ভাষা পরিবর্তন করুন", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (availableOptions.isEmpty()) {
                    Text("এই ভিডিওতে অন্য কোনো অডিও ট্র্যাক নেই। ডিফল্ট অডিও চালু রয়েছে।", color = Color(0xFF94A3B8), fontSize = 12.sp)
                } else {
                    availableOptions.forEach { opt ->
                        val isSelected = currentSelection?.groupIndex == opt.groupIndex && currentSelection?.trackIndex == opt.trackIndex
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) Color(0xFF38BDF8).copy(alpha = 0.2f) else Color(0xFF0F172A),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF38BDF8)) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelect(opt)
                                    onDismiss()
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(opt.displayName, color = if (isSelected) Color(0xFF38BDF8) else Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("ভাষা কোড: ${opt.language.ifBlank { "Default" }}", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                }
                                if (isSelected) {
                                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("বন্ধ করুন", color = Color(0xFF38BDF8))
            }
        }
    )
}

@Composable
fun SubtitleSelectionDialog(
    currentSelection: SubtitleTrackOption?,
    availableOptions: List<SubtitleTrackOption>,
    onSelect: (SubtitleTrackOption) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E293B),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Subtitles, contentDescription = null, tint = Color(0xFFA855F7))
                Spacer(modifier = Modifier.width(8.dp))
                Text("সাবটাইটেল নির্বাচন করুন", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Off option
                val isOffSelected = currentSelection == null || currentSelection.isOff
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isOffSelected) Color(0xFFA855F7).copy(alpha = 0.2f) else Color(0xFF0F172A),
                    border = if (isOffSelected) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFA855F7)) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelect(SubtitleTrackOption(displayName = "বন্ধ (Off)", isOff = true))
                            onDismiss()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("সাবটাইটেল বন্ধ (Off)", color = if (isOffSelected) Color(0xFFA855F7) else Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        if (isOffSelected) {
                            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFFA855F7), modifier = Modifier.size(20.dp))
                        }
                    }
                }

                // Available subtitles
                availableOptions.forEach { opt ->
                    val isSelected = !isOffSelected && currentSelection?.groupIndex == opt.groupIndex && currentSelection?.trackIndex == opt.trackIndex
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) Color(0xFFA855F7).copy(alpha = 0.2f) else Color(0xFF0F172A),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFA855F7)) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(opt)
                                onDismiss()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(opt.displayName, color = if (isSelected) Color(0xFFA855F7) else Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            if (isSelected) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFFA855F7), modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("বন্ধ করুন", color = Color(0xFFA855F7))
            }
        }
    )
}

@Composable
fun TvChannelOsdBanner(
    media: com.example.model.MediaItem,
    currentIndex: Int,
    totalCount: Int,
    selectedServerIndex: Int = 0,
    totalServersCount: Int = 1,
    selectedServerName: String?,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF0F172A).copy(alpha = 0.95f),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF00E5FF).copy(alpha = 0.8f)),
        shadowElevation = 12.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Channel Logo
                AsyncImage(
                    model = media.logoUrl ?: "https://images.unsplash.com/photo-1585829365295-ab7cd400c167?w=100",
                    contentDescription = media.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )

                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF00E5FF)
                        ) {
                            Text(
                                text = if (totalCount > 0) "CH ${currentIndex + 1}/$totalCount" else "LIVE",
                                color = Color.Black,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (media.category.isNotBlank()) {
                            Text(
                                text = media.category,
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = media.title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    if (selectedServerName != null) {
                        val serverBadgeText = if (totalServersCount > 1) {
                            "সার্ভার: $selectedServerName (${selectedServerIndex + 1}/$totalServersCount)"
                        } else {
                            "সার্ভার: $selectedServerName"
                        }
                        Text(
                            text = serverBadgeText,
                            color = Color(0xFF38BDF8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Remote hint legend bar
            Row(
                modifier = Modifier
                    .background(Color(0xFF1E293B).copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (totalServersCount > 1) "▲ ▼ চ্যানেল ও সার্ভার পরিবর্তন (${selectedServerIndex + 1}/$totalServersCount)  •  ◀ ▶ সিকবার  •  OK প্লে/পজ"
                           else "▲ ▼ চ্যানেল পরিবর্তন  •  ◀ ▶ সিকবার  •  OK প্লে/পজ  •  MENU চ্যানেল তালিকা",
                    color = Color(0xFFCBD5E1),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun TvNumberDialOverlay(
    dialedNumber: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.padding(24.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF0F172A).copy(alpha = 0.95f),
        border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF00E5FF))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Rounded.Tv, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(28.dp))
            Column {
                Text(
                    text = "চ্যানেল নং [ $dialedNumber ]",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "চ্যানেলে যাওয়া হচ্ছে...",
                    color = Color(0xFF00E5FF),
                    fontSize = 11.sp
                )
            }
        }
    }
}


