package com.example.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.MediaItem
import com.example.model.StreamServer
import com.example.util.DownloadState
import com.example.util.MovieDownloadManager
import com.example.util.MovieDownloadProgress

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MovieDetailsDownloadDialog(
    movie: MediaItem,
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onPlayOnline: (MediaItem, String?) -> Unit,
    onPlayOffline: (MediaItem) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val allServers = remember(movie) { movie.getAllServers() }
    var selectedServerIndex by remember { mutableIntStateOf(0) }

    val activeDownloadsMap by MovieDownloadManager.downloadsState.collectAsState()
    val downloadedMovies by MovieDownloadManager.downloadedMoviesFlow.collectAsState()

    val currentProgress: MovieDownloadProgress? = activeDownloadsMap[movie.id]
    val isDownloading = currentProgress?.state == DownloadState.DOWNLOADING || currentProgress?.state == DownloadState.PENDING
    val isPaused = currentProgress?.state == DownloadState.PAUSED || (!isDownloading && MovieDownloadManager.hasPartialDownload(context, movie.id))
    val partialBytes = if (currentProgress != null && currentProgress.downloadedBytes > 0) currentProgress.downloadedBytes else MovieDownloadManager.getPartialDownloadBytes(context, movie.id)
    val downloadedMovie = remember(downloadedMovies, movie.id) {
        downloadedMovies.firstOrNull { it.id == movie.id && it.fileExists }
    }
    val isDownloaded = downloadedMovie != null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F172A),
        scrimColor = Color.Black.copy(alpha = 0.7f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = Color(0xFF475569))
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 6.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Poster & Basic Info
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Poster
                Box(
                    modifier = Modifier
                        .size(width = 95.dp, height = 135.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E293B))
                ) {
                    AsyncImage(
                        model = movie.logoUrl ?: "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=400&q=80",
                        contentDescription = movie.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = movie.title,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Badges row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF2563EB)
                        ) {
                            Text(
                                text = movie.quality.ifBlank { "HD" },
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if (!movie.year.isNullOrBlank()) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF334155)
                            ) {
                                Text(
                                    text = movie.year,
                                    color = Color(0xFFE2E8F0),
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 10.5.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF1E293B),
                            border = BorderStroke(1.dp, Color(0xFF334155))
                        ) {
                            Text(
                                text = movie.category,
                                color = Color(0xFF00E5FF),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.5.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Status / Favorite action
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier
                                .size(34.dp)
                                .background(Color(0xFF1E293B), CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (isFavorite) Color(0xFFEF4444) else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        if (isDownloaded) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, Color(0xFF10B981))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("অফলাইন সংরক্ষিত (${downloadedMovie?.fileSizeFormatted})", color = Color(0xFF10B981), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Stream Servers Option (if more than 1 server exists)
            if (allServers.size > 1) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "সার্ভার নির্বাচন করুন:",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(allServers) { idx, server ->
                            val isSel = selectedServerIndex == idx
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSel) Color(0xFF2563EB) else Color(0xFF1E293B),
                                border = BorderStroke(1.dp, if (isSel) Color(0xFF00E5FF) else Color(0xFF334155)),
                                modifier = Modifier.clickable { selectedServerIndex = idx }
                            ) {
                                Text(
                                    text = server.name,
                                    color = if (isSel) Color.White else Color(0xFFCBD5E1),
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 11.5.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Description
            if (!movie.description.isNullOrBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "মুভি পরিচিতি:",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = movie.description,
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF334155), thickness = 0.8.dp)

            // Dynamic Downloading Progress Bar Box (if active)
            if (isDownloading && currentProgress != null) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                    border = BorderStroke(1.dp, Color(0xFF00E5FF)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = Color(0xFF00E5FF),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "ডাউনলোড হচ্ছে... (${currentProgress.progressPercent}%)",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.5.sp
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(
                                    onClick = {
                                        MovieDownloadManager.pauseDownload(movie.id)
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("⏸️ পজ", color = Color(0xFFF59E0B), fontSize = 11.5.sp)
                                }
                                TextButton(
                                    onClick = {
                                        MovieDownloadManager.cancelDownload(movie.id, deletePartialFile = true, context = context)
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text("বাতিল", color = Color(0xFFEF4444), fontSize = 11.5.sp)
                                }
                            }
                        }

                        LinearProgressIndicator(
                            progress = { if (currentProgress.totalBytes > 0) currentProgress.progress else 0.5f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = Color(0xFF00E5FF),
                            trackColor = Color(0xFF334155)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${currentProgress.downloadedSizeFormatted} / ${currentProgress.totalSizeFormatted}",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp
                            )
                            Text(
                                text = "⚡ ${currentProgress.speedFormatted}",
                                color = Color(0xFF10B981),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            } else if (isPaused && !isDownloaded && partialBytes > 0L) {
                // Paused Banner
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2438)),
                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.7f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.PauseCircle,
                                    contentDescription = null,
                                    tint = Color(0xFFF59E0B),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "ডাউনলোড স্থগিত (${MovieDownloadManager.formatBytes(partialBytes)} সংরক্ষিত)",
                                    color = Color(0xFFF59E0B),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            TextButton(
                                onClick = {
                                    MovieDownloadManager.deletePartialDownload(context, movie.id)
                                    Toast.makeText(context, "আংশিক ডাউনলোড মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text("🗑️ মুছুন", color = Color(0xFFEF4444), fontSize = 11.sp)
                            }
                        }
                        Text(
                            text = "ইন্টারনেট ড্রপ বা স্থগিত করা হয়েছিল। পুনরায় শুরু বাটনে চাপলে এখান থেকেই শুরু হবে।",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // PRIMARY ACTION BUTTONS
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Button 1: Play Online or Offline
                if (isDownloaded && downloadedMovie != null) {
                    Button(
                        onClick = {
                            onDismiss()
                            onPlayOffline(downloadedMovie.toMediaItem())
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                    ) {
                        Icon(Icons.Rounded.PlayCircleFilled, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "▶️ অফলাইনে প্লে করুন (ইন্টারনেট ছাড়া)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            onDismiss()
                            val selectedServerUrl = allServers.getOrNull(selectedServerIndex)?.url ?: movie.streamUrl
                            onPlayOnline(movie, selectedServerUrl)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "▶️ অনলাইনে প্লে করুন",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Button 2: Download / Delete Action
                if (isDownloaded) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                val selectedServerUrl = allServers.getOrNull(selectedServerIndex)?.url ?: movie.streamUrl
                                onPlayOnline(movie, selectedServerUrl)
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, Color(0xFF00E5FF))
                        ) {
                            Icon(Icons.Rounded.Language, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("অনলাইন লাইভ", color = Color.White, fontSize = 12.5.sp)
                        }

                        Button(
                            onClick = {
                                MovieDownloadManager.deleteDownloadedMovie(context, movie.id)
                                Toast.makeText(context, "সংরক্ষিত মুভি মুছে ফেলা হয়েছে", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                        ) {
                            Icon(Icons.Rounded.DeleteForever, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ডিলিট করুন", fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (!isDownloading) {
                    Button(
                        onClick = {
                            val selectedServerUrl = allServers.getOrNull(selectedServerIndex)?.url ?: movie.streamUrl
                            MovieDownloadManager.startDownload(
                                context = context,
                                mediaItem = movie,
                                preferredUrl = selectedServerUrl
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPaused && partialBytes > 0L) Color(0xFF0284C7) else Color(0xFF0D9488)
                        )
                    ) {
                        Icon(
                            if (isPaused && partialBytes > 0L) Icons.Rounded.PlayArrow else Icons.Rounded.FileDownload,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isPaused && partialBytes > 0L) {
                                "▶️ ডাউনলোড চালিয়ে যান (${MovieDownloadManager.formatBytes(partialBytes)} থেকে)"
                            } else {
                                "📥 ডাউনলোড করুন"
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Button 3: Browser / External Downloader
                OutlinedButton(
                    onClick = {
                        val selectedServerUrl = allServers.getOrNull(selectedServerIndex)?.url ?: movie.streamUrl
                        MovieDownloadManager.openExternalOrSystemDownload(context, movie, selectedServerUrl)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155))
                ) {
                    Icon(
                        imageVector = Icons.Rounded.OpenInBrowser,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "🌐 ব্রাউজার / 1DM / ADM দিয়ে ডাউনলোড",
                        color = Color(0xFFCBD5E1),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
