package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.MediaItem
import com.example.util.SportComment
import com.example.util.SportsInteractionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// Floating Reaction Item Model for Facebook/YouTube Live reaction physics
private data class FloatingReaction(
    val id: Long,
    val emoji: String,
    val offsetX: Float,
    val isFromRemote: Boolean = false
)

@Composable
fun SportsMatchCommentsAndReactionsView(
    sport: MediaItem,
    isTvMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    LaunchedEffect(sport.id) {
        SportsInteractionManager.init(context)
    }

    // Real-time synchronization loop: Fetches real comments, reactions, and presence from other users
    LaunchedEffect(sport.id) {
        SportsInteractionManager.pingPresence(sport.id)
        SportsInteractionManager.fetchRealLiveViewers(sport.id)
        SportsInteractionManager.fetchRemoteComments(sport.id)

        while (isActive) {
            delay(2800L)
            try {
                SportsInteractionManager.pingPresence(sport.id)
                SportsInteractionManager.fetchRealLiveViewers(sport.id)
                SportsInteractionManager.fetchRemoteComments(sport.id)
            } catch (_: Exception) {}
        }
    }

    val updateTick by SportsInteractionManager.updateTick.collectAsState()
    val viewersMap by SportsInteractionManager.liveViewersMap.collectAsState()
    val incomingReaction by SportsInteractionManager.incomingReactionFlow.collectAsState()

    val reactions = remember(sport.id, updateTick) {
        SportsInteractionManager.getReactions(sport.id)
    }

    val comments = remember(sport.id, updateTick) {
        SportsInteractionManager.getComments(sport.id)
    }

    // User requirement: শুধুমাত্র Tapmad Sports প্লে লিস্ট এর নাম (no 'উৎস' or 'উৎস প্লেলিস্ট' prefix!)
    val playlistName = remember(sport.id, sport.category, sport.tournament, sport.isAdminAdded) {
        SportsInteractionManager.getPlaylistSource(sport.category, sport.tournament, sport.isAdminAdded, sport.id)
    }

    val realViewersText = remember(sport.id, viewersMap) {
        SportsInteractionManager.getLiveViewersDisplay(sport.id)
    }

    var commentInput by remember { mutableStateOf("") }
    var currentUserName by remember {
        mutableStateOf(SportsInteractionManager.getSavedUserName().ifBlank { "ফ্যান" })
    }
    var showNameDialog by remember { mutableStateOf(false) }

    // Floating reaction animations (Facebook Live style physics)
    var floatingReactions by remember { mutableStateOf(listOf<FloatingReaction>()) }

    // Listen to real-time incoming reactions sent by other live users
    LaunchedEffect(incomingReaction) {
        val event = incomingReaction
        if (event != null) {
            val randomOffset = (-55..55).random().toFloat()
            val newReaction = FloatingReaction(
                id = event.id,
                emoji = event.emoji,
                offsetX = randomOffset,
                isFromRemote = event.isFromRemote
            )
            floatingReactions = floatingReactions + newReaction
            coroutineScope.launch {
                delay(1800L)
                floatingReactions = floatingReactions.filterNot { it.id == event.id }
            }
        }
    }

    fun triggerFloatingReaction(emoji: String) {
        val newId = System.currentTimeMillis() + (0..1000).random()
        val randomOffset = (-40..40).random().toFloat()
        floatingReactions = floatingReactions + FloatingReaction(newId, emoji, randomOffset, isFromRemote = false)
        SportsInteractionManager.addReaction(sport.id, emoji)

        coroutineScope.launch {
            delay(1600L)
            floatingReactions = floatingReactions.filterNot { it.id == newId }
        }
    }

    // Auto-scroll to top when a new comment arrives
    LaunchedEffect(comments.size) {
        if (comments.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    // Quick Tap Reaction Phrases (YouTube / Facebook Live feature)
    val quickPhrases = listOf(
        "🔥 আগুন খেলা!",
        "🏏 কী দারুণ শট!",
        "⚽ গোললল!",
        "👏 সাবাশ টাইগার্স!",
        "❤️ লাভ ইউ নাফি ২৪!",
        "🏆 আমরাই জিতব!"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF060D1E),
                        Color(0xFF0A1428),
                        Color(0xFF030712)
                    )
                )
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // -------------------------------------------------------------
            // ১. YOUTUBE / FACEBOOK LIVE STYLE HEADER
            // -------------------------------------------------------------
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF0F1A30).copy(alpha = 0.95f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.35f)),
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // LIVE CHAT Pulsing Indicator + Real Viewers Count
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Pulsing Red Live Badge
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFDC2626).copy(alpha = 0.2f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.6f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                                    val pulseAlpha by infiniteTransition.animateFloat(
                                        initialValue = 0.3f,
                                        targetValue = 1f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(600, easing = LinearEasing),
                                            repeatMode = RepeatMode.Reverse
                                        ),
                                        label = "pulseAlpha"
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFEF4444).copy(alpha = pulseAlpha))
                                    )
                                    Text(
                                        text = "LIVE CHAT",
                                        color = Color(0xFFF87171),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }

                            // Real Viewers Count (রিয়েল দর্শক সংখ্যা - কতজন দেখছে)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Group,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = realViewersText,
                                    color = Color(0xFFE2E8F0),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // User requirement: শুধুমাত্র Tapmad Sports প্লে লিস্ট এর নাম (pure name badge)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF0369A1).copy(alpha = 0.25f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlaylistPlay,
                                    contentDescription = null,
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = playlistName,
                                    color = Color(0xFFE0F2FE),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // PINNED HIGHLIGHT BANNER
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF1E293B).copy(alpha = 0.8f),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFF59E0B).copy(alpha = 0.6f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(text = "📌", fontSize = 11.sp)
                            Text(
                                text = "লাইভ চ্যাটে একজন কমেন্ট করলে অন্য সকল দর্শক রিয়েল-টাইমে দেখতে পাবেন। শালীন ভাষায় মতামত দিন!",
                                color = Color(0xFFFDE68A),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // ২. FACEBOOK / YOUTUBE LIVE FLOATING EMOJI REACTIONS BAR
            // -------------------------------------------------------------
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF0B132B).copy(alpha = 0.9f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E3A8A).copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "লাইভ রিয়েক্ট ⚡",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )

                    // 6 Interactive Emoji Reaction Pills
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SportsInteractionManager.EMOJI_LIST.forEach { emoji ->
                            val count = reactions[emoji] ?: 0
                            var isFocused by remember { mutableStateOf(false) }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isFocused) Color(0xFF1E3A8A) else Color(0xFF1E293B),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isFocused) Color(0xFF00E5FF) else Color(0xFF334155).copy(alpha = 0.5f)
                                ),
                                modifier = Modifier
                                    .onFocusChanged { isFocused = it.isFocused }
                                    .focusable()
                                    .clickable {
                                        triggerFloatingReaction(emoji)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(text = emoji, fontSize = 15.sp)
                                    Text(
                                        text = "$count",
                                        color = Color(0xFF38BDF8),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // ৩. YOUTUBE / FACEBOOK LIVE COMMENTS STREAM (Real & Shared)
            // -------------------------------------------------------------
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF0A1124).copy(alpha = 0.95f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp)
            ) {
                if (comments.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(text = "💬", fontSize = 28.sp)
                            Text(
                                text = "এখনো কোনো কমেন্ট করা হয়নি",
                                color = Color(0xFF94A3B8),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "খেলা নিয়ে প্রথম মন্তব্যটি আপনিই করুন! সব দর্শক তা দেখতে পাবেন।",
                                color = Color(0xFF64748B),
                                fontSize = 11.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(comments, key = { it.id }) { comment ->
                            // Real-time Shared User Comment bubble
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF131D33).copy(alpha = 0.75f))
                                    .border(0.5.dp, Color(0xFF2563EB).copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 9.dp, vertical = 7.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                // High-contrast Gradient Avatar
                                val avatarColor = try {
                                    Color(android.graphics.Color.parseColor(comment.avatarBgColorHex))
                                } catch (_: Exception) {
                                    Color(0xFF0284C7)
                                }
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(avatarColor, Color(0xFF0F172A))
                                            )
                                        )
                                        .border(1.dp, avatarColor.copy(alpha = 0.8f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = comment.userName.take(1).uppercase(),
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }

                                // Comment Content
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                                        ) {
                                            Text(
                                                text = comment.userName,
                                                color = Color(0xFF38BDF8),
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )

                                            // Badge (e.g. TOP FAN, VIP, LIVE FAN)
                                            if (!comment.badge.isNullOrBlank()) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                                                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFF59E0B).copy(alpha = 0.6f))
                                                ) {
                                                    Text(
                                                        text = comment.badge,
                                                        color = Color(0xFFFBBF24),
                                                        fontSize = 8.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Text(
                                            text = comment.timestamp,
                                            color = Color(0xFF64748B),
                                            fontSize = 9.sp
                                        )
                                    }

                                    Text(
                                        text = comment.text,
                                        color = Color(0xFFF1F5F9),
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp
                                    )
                                }

                                // Like / Heart button on individual comments
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            SportsInteractionManager.likeComment(sport.id, comment.id)
                                        }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = if (comment.isLikedByMe) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                        contentDescription = "Like",
                                        tint = if (comment.isLikedByMe) Color(0xFFEF4444) else Color(0xFF64748B),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    if (comment.likesCount > 0) {
                                        Text(
                                            text = "${comment.likesCount}",
                                            color = if (comment.isLikedByMe) Color(0xFFF87171) else Color(0xFF94A3B8),
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // -------------------------------------------------------------
            // ৪. QUICK-TAP REACTION PHRASES
            // -------------------------------------------------------------
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(quickPhrases) { phrase ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E293B).copy(alpha = 0.9f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.35f)),
                        modifier = Modifier.clickable {
                            SportsInteractionManager.addComment(sport.id, currentUserName, phrase)
                            Toast.makeText(context, "কমেন্ট পোস্ট হয়েছে!", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Text(
                            text = phrase,
                            color = Color(0xFFE0F2FE),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.5.dp)
                        )
                    }
                }
            }

            // -------------------------------------------------------------
            // ৫. ULTRA-MODERN FLOATING PILL COMMENT INPUT BAR
            // -------------------------------------------------------------
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF0F172A),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF0284C7).copy(alpha = 0.6f)),
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // User Avatar with edit nickname badge
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF0284C7), Color(0xFF06B6D4))
                                )
                            )
                            .clickable { showNameDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentUserName.take(1).uppercase(),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    // Sleek Text Field
                    TextField(
                        value = commentInput,
                        onValueChange = { commentInput = it },
                        placeholder = {
                            Text(
                                text = "ম্যাচ নিয়ে কিছু লিখুন...",
                                color = Color(0xFF64748B),
                                fontSize = 12.sp
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        singleLine = false,
                        maxLines = 2,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (commentInput.isNotBlank()) {
                                    SportsInteractionManager.addComment(sport.id, currentUserName, commentInput)
                                    commentInput = ""
                                    focusManager.clearFocus()
                                    Toast.makeText(context, "কমেন্ট সফলভাবে পোস্ট হয়েছে!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp, max = 56.dp)
                    )

                    // Glowing Send Button
                    var isSendFocused by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = {
                            if (commentInput.isNotBlank()) {
                                SportsInteractionManager.addComment(sport.id, currentUserName, commentInput)
                                commentInput = ""
                                focusManager.clearFocus()
                                Toast.makeText(context, "কমেন্ট সফলভাবে পোস্ট হয়েছে!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "অনুগ্রহ করে কিছু লিখুন", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF00E5FF), Color(0xFF0284C7))
                                )
                            )
                            .onFocusChanged { isSendFocused = it.isFocused }
                            .focusable()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Send,
                            contentDescription = "Send",
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // -------------------------------------------------------------
        // ৬. FACEBOOK LIVE FLOATING REACTION BUBBLE PARTICLES
        // -------------------------------------------------------------
        floatingReactions.forEach { item ->
            key(item.id) {
                val floatAnim = remember { Animatable(0f) }

                LaunchedEffect(item.id) {
                    floatAnim.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(1700, easing = LinearOutSlowInEasing)
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 36.dp)
                        .offset(
                            x = item.offsetX.dp,
                            y = (-260 * floatAnim.value).dp
                        )
                        .scale(0.85f + (0.45f * (1f - floatAnim.value)))
                        .clip(CircleShape)
                        .background(
                            if (item.isFromRemote) Color(0xFF1E1B4B).copy(alpha = 0.92f * (1f - floatAnim.value))
                            else Color(0xFF0F172A).copy(alpha = 0.88f * (1f - floatAnim.value))
                        )
                        .border(
                            1.dp,
                            if (item.isFromRemote) Color(0xFFF43F5E).copy(alpha = 0.7f * (1f - floatAnim.value))
                            else Color(0xFF00E5FF).copy(alpha = 0.7f * (1f - floatAnim.value)),
                            CircleShape
                        )
                        .padding(horizontal = 9.dp, vertical = 7.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(text = item.emoji, fontSize = 23.sp)
                        if (item.isFromRemote) {
                            Text(
                                text = "LIVE",
                                color = Color(0xFFFDA4AF).copy(alpha = 1f - floatAnim.value),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    // NICKNAME CHANGE MODAL DIALOG
    if (showNameDialog) {
        var tempName by remember { mutableStateOf(currentUserName) }
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Rounded.Person, contentDescription = null, tint = Color(0xFF00E5FF))
                    Text("আপনার ফ্যান নাম পরিবর্তন করুন", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("লাইভ কমেন্টে যে নামটি প্রদর্শন করতে চান তা লিখুন:", color = Color(0xFF94A3B8), fontSize = 12.sp)
                    TextField(
                        value = tempName,
                        onValueChange = { tempName = it },
                        placeholder = { Text("আপনার নাম লিখুন", color = Color(0xFF64748B)) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF1E293B),
                            unfocusedContainerColor = Color(0xFF1E293B),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val finalName = tempName.trim().ifBlank { "ফ্যান" }
                        currentUserName = finalName
                        SportsInteractionManager.saveUserName(finalName)
                        showNameDialog = false
                        Toast.makeText(context, "নাম আপডেট হয়েছে!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("সংরক্ষণ", color = Color(0xFF00E5FF), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) {
                    Text("বাতিল", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF0F172A),
            shape = RoundedCornerShape(16.dp)
        )
    }
}
