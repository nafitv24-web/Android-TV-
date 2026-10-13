import sys

with open('app/src/main/java/com/example/ui/NafiTvMainApp.kt', 'r', encoding='utf-8') as f:
    text = f.read()

idx = text.find('fun EventsScreen(')
if idx == -1:
    print('Error: EventsScreen not found')
    sys.exit(1)

new_events_section = """fun formatEventCountdownString(seconds: Long): String {
    if (seconds <= 0L) return "00h 00m 00s"
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return String.format("%02dh %02dm %02ds", hours, minutes, secs)
}

@Composable
fun EventsScreen(
    sports: List<MediaItem>,
    favoriteIds: Set<String>,
    isTvMode: Boolean = false,
    onSelectMedia: (MediaItem) -> Unit,
    onToggleFavorite: (String) -> Unit
) {
    var selectedCategory by remember { mutableStateOf("All") }
    var selectedStatus by remember { mutableStateOf("All") }

    // Dynamic categories extracted from all sports matches
    val categories = remember(sports) {
        val defaultCats = listOf("All", "Cricket", "Football", "Hockey", "More")
        val uniqueCats = sports.map { it.category.trim() }.filter { it.isNotBlank() && !it.equals("All", ignoreCase = true) }.distinct()
        (defaultCats + uniqueCats).distinct()
    }
    val statusFilters = listOf("All", "🔴 Live", "Upcoming", "Today", "Recent Results")

    // Live ticking countdown state
    var tickCount by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000L)
            tickCount++
        }
    }

    val filteredSports = sports.filter { item ->
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
        catMatches && statusMatches
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF020617))
    ) {
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
        if (filteredSports.isEmpty()) {
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
                        imageVector = Icons.Rounded.SportsSoccer,
                        contentDescription = null,
                        tint = Color(0xFF475569),
                        modifier = Modifier.size(54.dp)
                    )
                    Text(
                        text = "এই ফিল্টারে কোনো ম্যাচ পাওয়া যায়নি",
                        color = Color(0xFF94A3B8),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredSports, key = { it.id }) { sport ->
                    val remainingSecs = calculateEventRemainingSeconds(sport, tickCount)
                    val isLiveNow = isEventLiveNow(sport, tickCount)
                    LiveEventMatchCard(
                        sport = sport,
                        isLiveNow = isLiveNow,
                        remainingSecs = remainingSecs,
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
    isLiveNow: Boolean,
    remainingSecs: Long,
    isTvMode: Boolean = false,
    onSelectMedia: (MediaItem) -> Unit
) {
    val context = LocalContext.current
    var isCardFocused by remember { mutableStateOf(false) }
    var showNoLinkDialog by remember { mutableStateOf(false) }

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

    val handlePlayClick: (String?) -> Unit = { targetUrl ->
        val linkToPlay = targetUrl?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            ?: sport.streamUrl.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            ?: servers.firstOrNull { it.url.isNotBlank() && !it.url.equals("null", ignoreCase = true) }?.url

        if (!linkToPlay.isNullOrBlank()) {
            onSelectMedia(sport.copy(streamUrl = linkToPlay))
        } else {
            showNoLinkDialog = true
            Toast.makeText(context, "ম্যাচ শুরু হওয়ার সাথে সাথে চ্যানেল আসবে অপেক্ষা করুন ধন্যবাদ", Toast.LENGTH_SHORT).show()
        }
    }

    val cardScale by animateFloatAsState(
        targetValue = if (isCardFocused) 1.025f else 1.0f,
        animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
        label = "eventCardScale"
    )

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isCardFocused) Color(0xFF38BDF8) else Color(0xFF1E293B)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .scale(cardScale)
            .onFocusChanged { isCardFocused = it.isFocused }
            .focusable()
            .clickable { handlePlayClick(null) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // LEFT COLUMN (Thumbnail + Badge + Tournament Tag)
            Column(
                modifier = Modifier.width(115.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Thumbnail Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(68.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E293B))
                ) {
                    val hasTeam1Logo = !sport.team1Logo.isNullOrBlank() && !sport.team1Logo.equals("null", ignoreCase = true)
                    val hasTeam2Logo = !sport.team2Logo.isNullOrBlank() && !sport.team2Logo.equals("null", ignoreCase = true)
                    val hasLogoUrl = !sport.logoUrl.isNullOrBlank() && !sport.logoUrl.equals("null", ignoreCase = true)

                    if (hasTeam1Logo && hasTeam2Logo) {
                        // Two logos VS presentation
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF0F172A))
                                    )
                                )
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            AsyncImage(
                                model = sport.team1Logo,
                                contentDescription = sport.team1,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF334155)),
                                contentScale = ContentScale.Fit
                            )
                            Text(
                                text = "VS",
                                color = Color(0xFFF59E0B),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                            AsyncImage(
                                model = sport.team2Logo,
                                contentDescription = sport.team2,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF334155)),
                                contentScale = ContentScale.Fit
                            )
                        }
                    } else if (hasLogoUrl || hasTeam1Logo) {
                        AsyncImage(
                            model = if (hasLogoUrl) sport.logoUrl else sport.team1Logo,
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
                            Text(text = sportEmoji, fontSize = 24.sp)
                        }
                    }

                    // Top-right status badge
                    Surface(
                        shape = RoundedCornerShape(topEnd = 8.dp, bottomStart = 6.dp),
                        color = if (isLiveNow) Color(0xFFDC2626) else Color(0xFFD97706),
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Text(
                            text = if (isLiveNow) "• LIVE" else "• UPCOMING",
                            color = Color.White,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
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
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "🏆 $tournamentTag",
                            color = Color(0xFFF1F5F9),
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // RIGHT COLUMN (Stage / Time, Match Title, Live / Countdown Banner, Action Button)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Top Row (Stage / Time)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stageHeader,
                        color = Color(0xFFF59E0B),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (isLiveNow) {
                        Text(
                            text = "🔴 LIVE",
                            color = Color(0xFFEF4444),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    } else {
                        Text(
                            text = formattedTime,
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Match Title
                Text(
                    text = displayTitle,
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 17.sp
                )

                // Status / Countdown Banner
                if (isLiveNow) {
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
                            text = "⏳ বাকি: ${formatEventCountdownString(remainingSecs)}",
                            color = Color(0xFFFBBF24),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Multiple Servers Chips (if available)
                if (servers.size > 1) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                    ) {
                        servers.take(3).forEach { srv ->
                            Surface(
                                shape = RoundedCornerShape(5.dp),
                                color = Color(0xFF1E293B),
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF38BDF8).copy(alpha = 0.5f)),
                                modifier = Modifier.clickable { handlePlayClick(srv.url) }
                            ) {
                                Text(
                                    text = srv.name,
                                    color = Color(0xFF38BDF8),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                                )
                            }
                        }
                    }
                }

                // Action Button
                if (isLiveNow || hasPlayableLink) {
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
                                text = "লাইভ দেখুন",
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
    isLiveNow: Boolean,
    remainingSecs: Long,
    isTvMode: Boolean = false,
    onSelectMedia: (MediaItem) -> Unit
) {
    LiveEventMatchCard(sport, isLiveNow, remainingSecs, isTvMode, onSelectMedia)
}

@Composable
fun JsonPosterEventCard(
    sport: MediaItem,
    isLiveNow: Boolean,
    remainingSecs: Long,
    isTvMode: Boolean = false,
    onSelectMedia: (MediaItem) -> Unit
) {
    LiveEventMatchCard(sport, isLiveNow, remainingSecs, isTvMode, onSelectMedia)
}
"""

new_text = text[:idx] + new_events_section
with open('app/src/main/java/com/example/ui/NafiTvMainApp.kt', 'w', encoding='utf-8') as f:
    f.write(new_text)

print('Updated NafiTvMainApp.kt successfully!')
