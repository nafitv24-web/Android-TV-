package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.model.AppNotification
import com.example.model.NotificationType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotificationCenterDialog(
    notifications: List<AppNotification>,
    isTvMode: Boolean = false,
    onDismiss: () -> Unit,
    onSelectNotification: (AppNotification) -> Unit,
    onMarkAllRead: () -> Unit,
    onClearAll: () -> Unit,
    onDeleteNotification: (String) -> Unit
) {
    var selectedFilter by remember { mutableStateOf<NotificationType?>(null) }

    val filteredNotifications = remember(notifications, selectedFilter) {
        if (selectedFilter == null) {
            notifications
        } else {
            notifications.filter { it.type == selectedFilter }
        }
    }

    val unreadCount = remember(notifications) {
        notifications.count { !it.isRead }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0F172A),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier
                .fillMaxWidth(if (isTvMode) 0.75f else 0.94f)
                .fillMaxHeight(if (isTvMode) 0.85f else 0.82f)
                .padding(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF00E5FF).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsActive,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "নোটিফিকেশন সেন্টার",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (unreadCount > 0) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = Color(0xFFEF4444)
                                    ) {
                                        Text(
                                            text = "$unreadCount নতুন",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "লাইভ ম্যাচ, নতুন টিভি চ্যানেল, মুভি ও আপডেট",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (unreadCount > 0) {
                            IconButton(
                                onClick = onMarkAllRead,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DoneAll,
                                    contentDescription = "সব পড়া হয়েছে",
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        if (notifications.isNotEmpty()) {
                            IconButton(
                                onClick = onClearAll,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteSweep,
                                    contentDescription = "সব মুছুন",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "বন্ধ করুন",
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Filter Chips
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedFilter == null,
                            onClick = { selectedFilter = null },
                            label = { Text("সকল (${notifications.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF0284C7),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF1E293B),
                                labelColor = Color(0xFF94A3B8)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    item {
                        FilterChip(
                            selected = selectedFilter == NotificationType.LIVE_EVENT,
                            onClick = { selectedFilter = NotificationType.LIVE_EVENT },
                            label = { Text("⚽ ম্যাচ", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF10B981),
                                selectedLabelColor = Color.Black,
                                containerColor = Color(0xFF1E293B),
                                labelColor = Color(0xFF94A3B8)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    item {
                        FilterChip(
                            selected = selectedFilter == NotificationType.LIVE_TV,
                            onClick = { selectedFilter = NotificationType.LIVE_TV },
                            label = { Text("📺 টিভি চ্যানেল", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF00E5FF),
                                selectedLabelColor = Color.Black,
                                containerColor = Color(0xFF1E293B),
                                labelColor = Color(0xFF94A3B8)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    item {
                        FilterChip(
                            selected = selectedFilter == NotificationType.MOVIE,
                            onClick = { selectedFilter = NotificationType.MOVIE },
                            label = { Text("🎬 মুভি ও সিরিজ", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF8B5CF6),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF1E293B),
                                labelColor = Color(0xFF94A3B8)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    item {
                        FilterChip(
                            selected = selectedFilter == NotificationType.BROADCAST,
                            onClick = { selectedFilter = NotificationType.BROADCAST },
                            label = { Text("📢 অ্যাডমিন নোটিস", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFF59E0B),
                                selectedLabelColor = Color.Black,
                                containerColor = Color(0xFF1E293B),
                                labelColor = Color(0xFF94A3B8)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Notification List / Empty State
                if (filteredNotifications.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsNone,
                                contentDescription = null,
                                tint = Color(0xFF475569),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "কোনো নোটিফিকেশন পাওয়া যায়নি",
                                color = Color(0xFF94A3B8),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "নতুন ম্যাচ, চ্যানেল বা আপডেট আসলে এখানে দেখা যাবে",
                                color = Color(0xFF64748B),
                                fontSize = 11.sp
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredNotifications, key = { it.id }) { notif ->
                            NotificationItemCard(
                                notification = notif,
                                onSelect = { onSelectNotification(notif) },
                                onDelete = { onDeleteNotification(notif.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationItemCard(
    notification: AppNotification,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    val (typeColor, typeIcon, typeLabel) = when (notification.type) {
        NotificationType.LIVE_EVENT -> Triple(Color(0xFF10B981), Icons.Rounded.SportsSoccer, "লাইভ ম্যাচ")
        NotificationType.LIVE_TV -> Triple(Color(0xFF00E5FF), Icons.Rounded.Tv, "টিভি চ্যানেল")
        NotificationType.MOVIE -> Triple(Color(0xFF8B5CF6), Icons.Rounded.Movie, "মুভি")
        NotificationType.PLAYLIST -> Triple(Color(0xFFEC4899), Icons.Rounded.Folder, "প্লেলিস্ট")
        NotificationType.APP_UPDATE -> Triple(Color(0xFFF97316), Icons.Rounded.RocketLaunch, "অ্যাপ আপডেট")
        else -> Triple(Color(0xFFF59E0B), Icons.Rounded.Campaign, "অফিশিয়াল নোটিস")
    }

    val timeFormatted = remember(notification.timestamp) {
        val diff = System.currentTimeMillis() - notification.timestamp
        when {
            diff < 60 * 1000L -> "এইমাত্র"
            diff < 60 * 60 * 1000L -> "${diff / (60 * 1000L)} মিনিট আগে"
            diff < 24 * 60 * 60 * 1000L -> "${diff / (60 * 60 * 1000L)} ঘন্টা আগে"
            else -> {
                val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                sdf.format(Date(notification.timestamp))
            }
        }
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (!notification.isRead) Color(0xFF1E293B) else Color(0xFF131D31)
        ),
        border = if (!notification.isRead) {
            androidx.compose.foundation.BorderStroke(1.dp, typeColor.copy(alpha = 0.5f))
        } else {
            androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1E293B))
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Icon / Image
            if (!notification.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = notification.imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F172A)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(typeColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = typeIcon,
                        contentDescription = null,
                        tint = typeColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Content
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = typeColor.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = typeLabel,
                            color = typeColor,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }

                    Text(
                        text = timeFormatted,
                        color = Color(0xFF64748B),
                        fontSize = 10.sp
                    )
                }

                Text(
                    text = notification.title,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = if (!notification.isRead) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (notification.message.isNotBlank()) {
                    Text(
                        text = notification.message,
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Action Pill
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (notification.targetType != null || notification.targetId != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF0284C7).copy(alpha = 0.2f),
                            modifier = Modifier.clickable { onSelect() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "সরাসরি দেখুন",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "মুছুন",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
