package com.example.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.MediaRepository
import com.example.model.EpisodeItem
import com.example.model.MediaItem
import com.example.model.SeasonInfo
import com.example.util.DownloadState
import com.example.util.MovieDownloadManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesDetailsDialog(
    series: MediaItem,
    repository: MediaRepository,
    isTvMode: Boolean = false,
    onPlayEpisode: (MediaItem) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loadedSeries by remember { mutableStateOf(series) }
    var isLoadingDetails by remember { mutableStateOf(series.seasons.isEmpty() && series.episodes.isEmpty()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedSeasonNum by remember { mutableIntStateOf(1) }
    var isOverviewExpanded by remember { mutableStateOf(false) }

    // Fetch complete season & episode list from Xtream API if not already populated
    LaunchedEffect(series.id, series.seriesId) {
        if (series.seasons.isEmpty() && series.episodes.isEmpty()) {
            isLoadingDetails = true
            errorMessage = null
            try {
                val detailed = repository.fetchSeriesSeasonsAndEpisodes(series)
                loadedSeries = detailed
                if (detailed.seasons.isNotEmpty()) {
                    selectedSeasonNum = detailed.seasons.first().seasonNumber
                } else if (detailed.episodes.isNotEmpty()) {
                    selectedSeasonNum = detailed.episodes.first().seasonNum
                }
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "সিজন ও পর্ব লোড করতে ব্যর্থ হয়েছে: ${e.localizedMessage ?: "সার্ভার রেসপন্স করছে না"}"
            } finally {
                isLoadingDetails = false
            }
        } else {
            if (series.seasons.isNotEmpty()) {
                selectedSeasonNum = series.seasons.first().seasonNumber
            }
        }
    }

    val availableSeasons: List<SeasonInfo> = remember(loadedSeries) {
        if (loadedSeries.seasons.isNotEmpty()) {
            loadedSeries.seasons
        } else if (loadedSeries.episodes.isNotEmpty()) {
            val grouped = loadedSeries.episodes.groupBy { it.seasonNum }
            grouped.map { (sNum, eps) ->
                SeasonInfo(
                    seasonNumber = sNum,
                    name = "Season $sNum",
                    episodeCount = eps.size,
                    episodes = eps
                )
            }.sortedBy { it.seasonNumber }
        } else {
            emptyList()
        }
    }

    val episodesForCurrentSeason: List<EpisodeItem> = remember(loadedSeries, selectedSeasonNum, availableSeasons) {
        val seasonObj = availableSeasons.firstOrNull { it.seasonNumber == selectedSeasonNum }
        if (seasonObj != null && seasonObj.episodes.isNotEmpty()) {
            seasonObj.episodes
        } else {
            loadedSeries.episodes.filter { it.seasonNum == selectedSeasonNum }
        }
    }

    if (isTvMode) {
        // =========================================================================
        // TV FULLSCREEN DIALOG (100% REMOTE D-PAD NAVIGABLE FOR ANDROID TV)
        // =========================================================================
        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            BackHandler { onDismiss() }

            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF070D1E)),
                color = Color(0xFF070D1E)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // LEFT PANE: Series Poster, Info & Season Selector
                    Column(
                        modifier = Modifier
                            .width(320.dp)
                            .fillMaxHeight()
                            .padding(end = 16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Back / Close button for TV Remote
                        var isBackFocused by remember { mutableStateOf(false) }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isBackFocused) Color(0xFF0284C7) else Color(0xFF1E293B),
                            border = BorderStroke(
                                1.5.dp,
                                if (isBackFocused) Color(0xFFFFD600) else Color(0xFF334155)
                            ),
                            modifier = Modifier
                                .onFocusChanged { isBackFocused = it.isFocused }
                                .focusable()
                                .clickable { onDismiss() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "ফিরে যান (Back)",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Poster & Title Row
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 100.dp, height = 145.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1E293B))
                            ) {
                                AsyncImage(
                                    model = loadedSeries.logoUrl
                                        ?: "https://images.unsplash.com/photo-1578022761797-b8636ac1773c?w=400&fit=crop",
                                    contentDescription = loadedSeries.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Surface(
                                    color = Color(0xFFE11D48),
                                    shape = RoundedCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                                    modifier = Modifier.align(Alignment.TopStart)
                                ) {
                                    Text(
                                        text = "SERIES",
                                        color = Color.White,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = loadedSeries.title,
                                    color = Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = loadedSeries.category.ifBlank { "ওয়েব সিরিজ" },
                                    color = Color(0xFF00E5FF),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (!loadedSeries.rating.isNullOrBlank()) {
                                        Surface(
                                            color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Star,
                                                    contentDescription = null,
                                                    tint = Color(0xFFF59E0B),
                                                    modifier = Modifier.size(11.dp)
                                                )
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text(
                                                    text = loadedSeries.rating ?: "8.5",
                                                    color = Color(0xFFF59E0B),
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    if (!loadedSeries.year.isNullOrBlank()) {
                                        Text(
                                            text = loadedSeries.year ?: "",
                                            color = Color(0xFF94A3B8),
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Genres / Cast
                        if (!loadedSeries.genre.isNullOrBlank()) {
                            Text(
                                text = "জনরা: ${loadedSeries.genre}",
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Synopsis
                        val overview = loadedSeries.description?.takeIf { it.isNotBlank() }
                            ?: "এই জনপ্রিয় ড্রামা সিরিজটি উপভোগ করুন সম্পূর্ণ এইচডি কোয়ালিটিতে।"
                        Text(
                            text = overview,
                            color = Color(0xFF94A3B8),
                            fontSize = 10.5.sp,
                            lineHeight = 14.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )

                        HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)

                        // Season Selector Header
                        Text(
                            text = "সিজনসমূহ (Seasons):",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // Vertical List of Season Buttons for Remote D-pad
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            availableSeasons.forEach { season ->
                                val isSelected = selectedSeasonNum == season.seasonNumber
                                var isSeasonFocused by remember { mutableStateOf(false) }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = when {
                                        isSeasonFocused -> Color(0xFF1E3A8A)
                                        isSelected -> Color(0xFF0284C7)
                                        else -> Color(0xFF141F38)
                                    },
                                    border = BorderStroke(
                                        if (isSeasonFocused) 2.dp else 1.dp,
                                        when {
                                            isSeasonFocused -> Color(0xFFFFD600)
                                            isSelected -> Color(0xFF00E5FF)
                                            else -> Color(0xFF223456)
                                        }
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .scale(if (isSeasonFocused) 1.03f else 1.0f)
                                        .onFocusChanged { isSeasonFocused = it.isFocused }
                                        .focusable()
                                        .clickable { selectedSeasonNum = season.seasonNumber }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Layers,
                                                contentDescription = null,
                                                tint = if (isSeasonFocused || isSelected) Color(0xFF00E5FF) else Color(0xFF94A3B8),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = season.name.ifBlank { "সিজন ${season.seasonNumber}" },
                                                color = Color.White,
                                                fontSize = 12.sp,
                                                fontWeight = if (isSelected || isSeasonFocused) FontWeight.Bold else FontWeight.Medium
                                            )
                                        }
                                        if (season.episodeCount > 0) {
                                            Text(
                                                text = "${season.episodeCount}টি পর্ব",
                                                color = if (isSelected) Color(0xFFE0F2FE) else Color(0xFF64748B),
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // RIGHT PANE: Header & Episode Cards Grid for TV
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        // Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(
                                    imageVector = Icons.Rounded.VideoLibrary,
                                    contentDescription = null,
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "সিজন $selectedSeasonNum এর পর্বসমূহ (${episodesForCurrentSeason.size} টি পর্ব)",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                text = "রিমোটের OK বাটন দিয়ে পর্ব চালু করুন",
                                color = Color(0xFF38BDF8),
                                fontSize = 11.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Grid of episodes
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            if (isLoadingDetails) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = Color(0xFF00E5FF),
                                        modifier = Modifier.size(38.dp)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "সিজন ও পর্বসমূহ লোড হচ্ছে...",
                                        color = Color(0xFF00E5FF),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Xtream সার্ভার থেকে পর্বের তালিকা আনা হচ্ছে, অপেক্ষা করুন...",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp
                                    )
                                }
                            } else if (errorMessage != null) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Warning,
                                        contentDescription = null,
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = errorMessage ?: "ত্রুটি ঘটেছে",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                isLoadingDetails = true
                                                errorMessage = null
                                                try {
                                                    val detailed = repository.fetchSeriesSeasonsAndEpisodes(series)
                                                    loadedSeries = detailed
                                                    if (detailed.seasons.isNotEmpty()) {
                                                        selectedSeasonNum = detailed.seasons.first().seasonNumber
                                                    }
                                                } catch (e: Exception) {
                                                    errorMessage = e.localizedMessage
                                                } finally {
                                                    isLoadingDetails = false
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("পুনরায় চেষ্টা করুন (Retry)")
                                    }
                                }
                            } else if (episodesForCurrentSeason.isEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.MovieFilter,
                                        contentDescription = null,
                                        tint = Color(0xFF64748B),
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "এই সিজনে কোনো পর্ব পাওয়া যায়নি",
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Fixed(2),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    itemsIndexed(episodesForCurrentSeason, key = { _, ep -> ep.id }) { index, episode ->
                                        EpisodeCardItem(
                                            episode = episode,
                                            parentSeries = loadedSeries,
                                            isTvMode = true,
                                            onPlay = {
                                                val playableMedia = episode.toMediaItem(loadedSeries)
                                                onPlayEpisode(playableMedia)
                                                onDismiss()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        // =========================================================================
        // MOBILE MODAL BOTTOM SHEET
        // =========================================================================
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            containerColor = Color(0xFF0B132B),
            scrimColor = Color.Black.copy(alpha = 0.8f),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = {
                BottomSheetDefaults.DragHandle(color = Color(0xFF475569))
            },
            modifier = Modifier.fillMaxHeight(0.92f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                // Top Bar with Close button and Title
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
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
                                .size(34.dp)
                                .background(Color(0xFF00E5FF).copy(alpha = 0.15f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tv,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = loadedSeries.title,
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = loadedSeries.category.ifBlank { "ওয়েব সিরিজ" },
                                color = Color(0xFF00E5FF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color(0xFF1E293B), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // Series Poster, Details, and Genres Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 100.dp, height = 145.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E293B))
                    ) {
                        AsyncImage(
                            model = loadedSeries.logoUrl
                                ?: "https://images.unsplash.com/photo-1578022761797-b8636ac1773c?w=400&fit=crop",
                            contentDescription = loadedSeries.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Surface(
                            color = Color(0xFFE11D48),
                            shape = RoundedCornerShape(topStart = 8.dp, bottomEnd = 8.dp),
                            modifier = Modifier.align(Alignment.TopStart)
                        ) {
                            Text(
                                text = "SERIES",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (!loadedSeries.rating.isNullOrBlank()) {
                                Surface(
                                    color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Star,
                                            contentDescription = null,
                                            tint = Color(0xFFF59E0B),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = loadedSeries.rating ?: "8.5",
                                            color = Color(0xFFF59E0B),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            if (!loadedSeries.year.isNullOrBlank()) {
                                Text(
                                    text = loadedSeries.year ?: "",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            if (availableSeasons.isNotEmpty()) {
                                Text(
                                    text = "•  ${availableSeasons.size}টি সিজন",
                                    color = Color(0xFF00E5FF),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (!loadedSeries.genre.isNullOrBlank()) {
                            Text(
                                text = "জনরা: ${loadedSeries.genre}",
                                color = Color(0xFFCBD5E1),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (!loadedSeries.cast.isNullOrBlank()) {
                            Text(
                                text = "অভিনয়ে: ${loadedSeries.cast}",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        val overview = loadedSeries.description?.takeIf { it.isNotBlank() }
                            ?: "রোমাঞ্চকর ও জনপ্রিয় ওয়েব সিরিজটি উপভোগ করুন সম্পূর্ণ এইচডি কোয়ালিটিতে।"

                        Text(
                            text = overview,
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                            maxLines = if (isOverviewExpanded) 6 else 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clickable { isOverviewExpanded = !isOverviewExpanded }
                                .animateContentSize()
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFF1E293B), thickness = 1.dp)

                // Season Selection Tabs / Chips
                if (availableSeasons.isNotEmpty()) {
                    Column(modifier = Modifier.padding(vertical = 10.dp)) {
                        Text(
                            text = "সিজন নির্বাচন করুন (Seasons):",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 2.dp)
                        ) {
                            items(availableSeasons) { season ->
                                val isSelected = selectedSeasonNum == season.seasonNumber
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSelected) Color(0xFF0284C7) else Color(0xFF1E293B),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) Color(0xFF00E5FF) else Color(0xFF334155)
                                    ),
                                    modifier = Modifier.clickable {
                                        selectedSeasonNum = season.seasonNumber
                                    }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Layers,
                                            contentDescription = null,
                                            tint = if (isSelected) Color.White else Color(0xFF94A3B8),
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Text(
                                            text = season.name.ifBlank { "সিজন ${season.seasonNumber}" },
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                        if (season.episodeCount > 0) {
                                            Text(
                                                text = "(${season.episodeCount})",
                                                color = if (isSelected) Color(0xFFE0F2FE) else Color(0xFF64748B),
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Episodes List Section
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (isLoadingDetails) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = Color(0xFF00E5FF),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "সিজন ও পর্বসমূহ লোড হচ্ছে...",
                                color = Color(0xFF00E5FF),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "সার্ভার থেকে পর্বের তালিকা আনা হচ্ছে, অপেক্ষা করুন...",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    } else if (errorMessage != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = errorMessage ?: "ত্রুটি ঘটেছে",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    scope.launch {
                                        isLoadingDetails = true
                                        errorMessage = null
                                        try {
                                            val detailed = repository.fetchSeriesSeasonsAndEpisodes(series)
                                            loadedSeries = detailed
                                            if (detailed.seasons.isNotEmpty()) {
                                                selectedSeasonNum = detailed.seasons.first().seasonNumber
                                            }
                                        } catch (e: Exception) {
                                            errorMessage = e.localizedMessage
                                        } finally {
                                            isLoadingDetails = false
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("পুনরায় চেষ্টা করুন (Retry)")
                            }
                        }
                    } else if (episodesForCurrentSeason.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MovieFilter,
                                contentDescription = null,
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "এই সিজনে কোনো পর্ব পাওয়া যায়নি",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(episodesForCurrentSeason, key = { _, ep -> ep.id }) { index, episode ->
                                EpisodeCardItem(
                                    episode = episode,
                                    parentSeries = loadedSeries,
                                    isTvMode = false,
                                    onPlay = {
                                        val playableMedia = episode.toMediaItem(loadedSeries)
                                        onPlayEpisode(playableMedia)
                                        onDismiss()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EpisodeCardItem(
    episode: EpisodeItem,
    parentSeries: MediaItem,
    isTvMode: Boolean = false,
    onPlay: () -> Unit
) {
    val context = LocalContext.current
    val activeDownloadsMap by MovieDownloadManager.downloadsState.collectAsState()
    val downloadedMovies by MovieDownloadManager.downloadedMoviesFlow.collectAsState()

    val epId = "${parentSeries.id}_s${episode.seasonNum}e${episode.episodeNum}"
    val dlProg = activeDownloadsMap[epId]
    val isDownloading = dlProg?.state == DownloadState.DOWNLOADING || dlProg?.state == DownloadState.PENDING
    val isDownloaded = downloadedMovies.any { it.id == epId && it.fileExists }

    var isCardFocused by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = when {
            isCardFocused -> Color(0xFF1E3A8A)
            else -> Color(0xFF141F38)
        },
        border = BorderStroke(
            if (isCardFocused) 2.dp else 1.dp,
            when {
                isCardFocused -> Color(0xFFFFD600)
                else -> Color(0xFF223456)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .scale(if (isCardFocused) 1.03f else 1.0f)
            .onFocusChanged { isCardFocused = it.isFocused }
            .focusable()
            .clickable { onPlay() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Episode Thumbnail / Number Box
            Box(
                modifier = Modifier
                    .size(width = if (isTvMode) 95.dp else 85.dp, height = if (isTvMode) 62.dp else 55.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F172A))
            ) {
                val epThumbnail = episode.logoUrl?.takeIf { it.isNotBlank() } ?: parentSeries.logoUrl
                if (!epThumbnail.isNullOrBlank()) {
                    AsyncImage(
                        model = epThumbnail,
                        contentDescription = episode.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayCircle,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF).copy(alpha = 0.7f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Play icon overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = if (isCardFocused) 0.15f else 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play",
                        tint = if (isCardFocused) Color(0xFFFFD600) else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Episode Title, Number & Duration
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "পর্ব ${episode.episodeNum}: ${episode.title.ifBlank { "Episode ${episode.episodeNum}" }}",
                    color = if (isCardFocused) Color(0xFFFFD600) else Color.White,
                    fontSize = if (isTvMode) 13.5.sp else 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!episode.duration.isNullOrBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Schedule,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = episode.duration ?: "",
                                color = Color(0xFF94A3B8),
                                fontSize = 10.5.sp
                            )
                        }
                    }

                    if (!episode.releaseDate.isNullOrBlank()) {
                        Text(
                            text = episode.releaseDate ?: "",
                            color = Color(0xFF64748B),
                            fontSize = 10.5.sp
                        )
                    }
                }

                if (!episode.overview.isNullOrBlank()) {
                    Text(
                        text = episode.overview ?: "",
                        color = Color(0xFF94A3B8),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action: Download Episode button
            IconButton(
                onClick = {
                    if (isDownloaded) {
                        Toast.makeText(context, "পর্বটি ইতিমধ্যে ডাউনলোড করা হয়েছে!", Toast.LENGTH_SHORT).show()
                    } else if (isDownloading) {
                        Toast.makeText(context, "ডাউনলোড চলছে (${dlProg?.progressPercent ?: 0}%)...", Toast.LENGTH_SHORT).show()
                    } else {
                        val playable = episode.toMediaItem(parentSeries)
                        MovieDownloadManager.startDownload(
                            context = context,
                            mediaItem = playable,
                            preferredUrl = episode.streamUrl
                        )
                        Toast.makeText(context, "📥 পর্ব ডাউনলোড শুরু হয়েছে!", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .size(34.dp)
                    .background(Color(0xFF1E293B), CircleShape)
            ) {
                Icon(
                    imageVector = when {
                        isDownloaded -> Icons.Rounded.CheckCircle
                        isDownloading -> Icons.Rounded.Downloading
                        else -> Icons.Rounded.FileDownload
                    },
                    contentDescription = "Download Episode",
                    tint = when {
                        isDownloaded -> Color(0xFF10B981)
                        isDownloading -> Color(0xFF00E5FF)
                        else -> Color(0xFF94A3B8)
                    },
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
