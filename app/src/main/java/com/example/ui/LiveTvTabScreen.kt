package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import coil.compose.AsyncImage
import com.example.model.MediaItem
import com.example.model.MediaType
import com.example.model.MediaServer

import androidx.compose.ui.platform.LocalContext
import com.example.ui.components.NafiLogoLoadingView
import com.example.util.ChannelStatusManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val SERVER_CLEAN_REGEX = Regex("^(?i)(server|সার্ভার)\\s*\\d*.*")

fun mergeChannelsWithServers(channels: List<MediaItem>): List<MediaItem> {
    if (channels.isEmpty()) return emptyList()
    val nonDemo = channels.filterNot { item ->
        val id = item.id.lowercase()
        val title = item.title.lowercase()
        val cat = item.category.lowercase()
        val url = item.streamUrl.lowercase()
        val allUrls = (item.servers.map { it.url.lowercase() } + listOf(url)).joinToString(" ")

        id.startsWith("demo_") ||
                id.contains("demo_sample") ||
                title.contains("demo channel") ||
                title.contains("ডেমো চ্যানেল") ||
                title.contains("test stream") ||
                title.contains("পরীক্ষামূলক টেস্ট") ||
                allUrls.contains("test-streams.mux.dev") ||
                allUrls.contains("akamaized.net/hls/live/2000341/test") ||
                allUrls.contains("bitdash-a.akamaihd.net") ||
                allUrls.contains("bipbop") ||
                allUrls.contains("bigbuckbunny")
    }
    // User requirement: "লাইভ টিভি অপশনে সকল চ্যানেল আসবে এক নামে দুটি চ্যানেল থাকলেও"
    // Keep all channels intact even with identical names, guaranteeing unique IDs for Compose rendering
    val seenIds = HashSet<String>()
    return nonDemo.mapIndexed { index, item ->
        val safeServers = item.getAllServers()
        var uniqueId = item.id.ifBlank { "ch_${index}_${Math.abs(item.title.hashCode())}" }
        if (seenIds.contains(uniqueId)) {
            uniqueId = "${uniqueId}_${index}"
        }
        seenIds.add(uniqueId)
        item.copy(
            id = uniqueId,
            servers = safeServers
        )
    }
}

@Composable
fun LiveTvTabScreen(
    channels: List<MediaItem>,
    favoriteIds: Set<String>,
    isLoading: Boolean = false,
    isTvMode: Boolean = false,
    isAdultHidden: Boolean = true,
    repository: com.example.data.MediaRepository? = null,
    onSelectMedia: (MediaItem, List<MediaItem>) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onAddChannel: (MediaItem) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ALL") }
    var showOnlyActive by rememberSaveable { mutableStateOf(false) }

    val statusTick by ChannelStatusManager.statusUpdateTick.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val firstChannelFocusRequester = remember { FocusRequester() }

    // Instantaneous 0ms channel preparation from incoming channels
    var isAsyncPreparing by remember { mutableStateOf(false) }
    var preparedChannels by remember { mutableStateOf(channels) }
    var preparedCategories by remember {
        mutableStateOf(
            listOf("ALL", "FAVORITE") + channels.mapNotNull { it.category?.takeIf { c -> c.isNotBlank() } }.distinct()
        )
    }

    LaunchedEffect(channels, isAdultHidden, repository) {
        if (channels.isEmpty()) {
            preparedChannels = emptyList()
            preparedCategories = listOf("ALL")
            isAsyncPreparing = false
            return@LaunchedEffect
        }
        withContext(Dispatchers.Default) {
            val merged = mergeChannelsWithServers(channels)
            val shouldFilterAdult = isAdultHidden || (repository?.isAdultContentHidden() == true)
            val adultFiltered = if (shouldFilterAdult && repository != null) {
                merged.filterNot { repository.isMediaHidden(it) || repository.isAdultMedia(it) || repository.isAdultCategory(it.category) }
            } else if (shouldFilterAdult) {
                val adultWords = listOf("adult", "adults", "18+", "18 +", "xxx", "nsfw", "erotic", "porn", "sex", "sensual", "mature", "brazzers", "playboy", "hustler", "for adults")
                merged.filterNot { ch ->
                    val txt = "${ch.title} ${ch.category} ${ch.genre ?: ""}".lowercase()
                    adultWords.any { txt.contains(it) }
                }
            } else {
                merged
            }
            val cats = listOf("ALL", "FAVORITE") + adultFiltered.mapNotNull { it.category?.takeIf { c -> c.isNotBlank() } }.distinct()

            withContext(Dispatchers.Main) {
                preparedChannels = adultFiltered
                preparedCategories = cats
                isAsyncPreparing = false
            }
        }
    }

    // Trigger non-blocking background health check on unverified channels ONLY if user turned on showOnlyActive filter
    LaunchedEffect(preparedChannels, showOnlyActive) {
        if (showOnlyActive && preparedChannels.isNotEmpty()) {
            ChannelStatusManager.enqueueChannelsForProbing(preparedChannels)
        }
    }

    LaunchedEffect(showOnlyActive) {
        ChannelStatusManager.setOnlyActiveEnabled(showOnlyActive)
    }

    // Auto-focus first channel in TV mode so D-Pad lands on the channel list instead of stealing focus to Search field
    LaunchedEffect(isTvMode, preparedChannels.isNotEmpty()) {
        if (isTvMode && preparedChannels.isNotEmpty()) {
            delay(180)
            try {
                firstChannelFocusRequester.requestFocus()
            } catch (_: Exception) {}
        }
    }

    val categories = preparedCategories

    val filteredChannels = remember(preparedChannels, searchQuery, selectedCategory, favoriteIds, showOnlyActive, if (showOnlyActive) statusTick else 0L) {
        preparedChannels.filter { channel ->
            val matchesSearch = searchQuery.isBlank() || channel.title.contains(searchQuery, ignoreCase = true)
            val matchesCategory = when (selectedCategory) {
                "ALL" -> true
                "FAVORITE" -> favoriteIds.contains(channel.id)
                else -> channel.category.equals(selectedCategory, ignoreCase = true)
            }
            val matchesActive = if (showOnlyActive) ChannelStatusManager.isChannelActive(channel) else true
            matchesSearch && matchesCategory && matchesActive
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (isLoading && preparedChannels.isNotEmpty()) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = Color(0xFF00E5FF),
                trackColor = Color(0xFF1E293B)
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Search and Filter Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("লাইভ টিভি চ্যানেল খুঁজুন...", color = Color(0xFF94A3B8), fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Color(0xFF00E5FF)) },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = Color(0xFF94A3B8))
                        }
                    }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00E5FF),
                    unfocusedBorderColor = Color(0xFF334155),
                    focusedContainerColor = Color(0xFF1E293B),
                    unfocusedContainerColor = Color(0xFF1E293B),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier.weight(1f)
            )

            // 'Only Active Channel' Toggle Switch
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (showOnlyActive) Color(0xFF065F46).copy(alpha = 0.4f) else Color(0xFF1E293B),
                border = BorderStroke(
                    1.dp,
                    if (showOnlyActive) Color(0xFF10B981) else Color(0xFF334155)
                ),
                modifier = Modifier
                    .clickable { showOnlyActive = !showOnlyActive }
                    .padding(vertical = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (showOnlyActive) Color(0xFF10B981) else Color(0xFF64748B))
                    )
                    Text(
                        text = "Only Active Channel",
                        color = if (showOnlyActive) Color(0xFF34D399) else Color(0xFFCBD5E1),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Switch(
                        checked = showOnlyActive,
                        onCheckedChange = { showOnlyActive = it },
                        modifier = Modifier.scale(0.75f),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF10B981),
                            uncheckedThumbColor = Color(0xFF94A3B8),
                            uncheckedTrackColor = Color(0xFF334155)
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Categories Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(categories) { cat ->
                val isSelected = selectedCategory == cat
                val label = when (cat) {
                    "ALL" -> if (isLoading && channels.isEmpty()) "সকল চ্যানেল (লোড হচ্ছে...)" else "সকল চ্যানেল (${channels.size})"
                    "FAVORITE" -> "⭐ প্রিয় (${favoriteIds.size})"
                    else -> cat
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) Color(0xFF00E5FF) else Color(0xFF1E293B),
                    border = BorderStroke(
                        1.dp,
                        if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155)
                    ),
                    modifier = Modifier.clickable { selectedCategory = cat }
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) Color.Black else Color(0xFFE2E8F0),
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Channels Grid or Loading View
        if ((isLoading || isAsyncPreparing) && preparedChannels.isEmpty()) {
            NafiLogoLoadingView(
                title = "লাইভ টিভি চ্যানেল লোড হচ্ছে...",
                subtitle = "অনুগ্রহ করে অপেক্ষা করুন, টিভি চ্যানেল ও সার্ভার প্রস্তুত হচ্ছে...",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        } else if (filteredChannels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.TvOff, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("কোনো চ্যানেল পাওয়া যায়নি", color = Color(0xFF94A3B8), fontSize = 14.sp)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 130.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(filteredChannels, key = { _, it -> it.id }) { index, channel ->
                    val isFav = favoriteIds.contains(channel.id)
                    var isFocused by remember { mutableStateOf(false) }
                    val isActive = ChannelStatusManager.isChannelActive(channel)

                    val baseModifier = if (isTvMode) {
                        Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .then(if (index == 0) Modifier.focusRequester(firstChannelFocusRequester) else Modifier)
                            .onFocusChanged { isFocused = it.isFocused }
                            .focusable()
                            .clickable { onSelectMedia(channel, filteredChannels) }
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clickable { onSelectMedia(channel, filteredChannels) }
                    }
                    val cardModifier = if (!isActive) {
                        baseModifier.alpha(0.72f)
                    } else {
                        baseModifier
                    }

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        border = BorderStroke(
                            1.dp,
                            when {
                                isTvMode && isFocused -> Color(0xFF00E5FF)
                                isActive -> Color(0xFF10B981).copy(alpha = 0.45f)
                                else -> Color(0xFFEF4444).copy(alpha = 0.45f)
                            }
                        ),
                        modifier = cardModifier
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                if (!channel.logoUrl.isNullOrBlank()) {
                                    val context = LocalContext.current
                                    AsyncImage(
                                        model = coil.request.ImageRequest.Builder(context)
                                            .data(channel.logoUrl)
                                            .crossfade(false)
                                            .size(128, 128)
                                            .build(),
                                        contentDescription = channel.title,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF0F172A)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Rounded.LiveTv, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(28.dp))
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = channel.title,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                if (!channel.category.isNullOrBlank()) {
                                    Text(
                                        text = channel.category!!,
                                        color = Color(0xFF94A3B8),
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Top-Start Corner: Active green indicator / Offline red indicator
                            if (isActive) {
                                Surface(
                                    shape = RoundedCornerShape(topStart = 12.dp, bottomEnd = 8.dp),
                                    color = Color(0xFF064E3B).copy(alpha = 0.95f),
                                    border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.8f)),
                                    modifier = Modifier.align(Alignment.TopStart)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFF10B981))
                                        )
                                        Text(
                                            text = "সচল",
                                            color = Color(0xFF6EE7B7),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            } else {
                                Surface(
                                    shape = RoundedCornerShape(topStart = 12.dp, bottomEnd = 8.dp),
                                    color = Color(0xFF450A0A).copy(alpha = 0.95f),
                                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f)),
                                    modifier = Modifier.align(Alignment.TopStart)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFEF4444))
                                        )
                                        Text(
                                            text = "অফলাইন",
                                            color = Color(0xFFFCA5A5),
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Favorite Icon Button
                            IconButton(
                                onClick = { onToggleFavorite(channel.id) },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFav) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                    contentDescription = "Favorite",
                                    tint = if (isFav) Color(0xFFF59E0B) else Color(0xFF64748B),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
