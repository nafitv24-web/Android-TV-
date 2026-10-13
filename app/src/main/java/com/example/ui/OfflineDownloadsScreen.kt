package com.example.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.model.MediaItem
import com.example.util.DownloadState
import com.example.util.DownloadedMovie
import com.example.util.MovieDownloadManager
import com.example.util.MovieDownloadProgress
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineDownloadsScreen(
    onPlayOfflineMedia: (MediaItem) -> Unit,
    onBack: () -> Unit,
    onExploreMovies: () -> Unit = {}
) {
    val context = LocalContext.current
    val downloadedMovies by MovieDownloadManager.downloadedMoviesFlow.collectAsState()
    val activeDownloadsMap by MovieDownloadManager.downloadsState.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var movieToDelete by remember { mutableStateOf<DownloadedMovie?>(null) }

    LaunchedEffect(Unit) {
        MovieDownloadManager.refreshDownloadedMoviesList(context)
    }

    val activeProgressList = remember(activeDownloadsMap) {
        activeDownloadsMap.values.filter {
            it.state == DownloadState.DOWNLOADING || it.state == DownloadState.PENDING
        }
    }

    val pausedProgressList = remember(activeDownloadsMap) {
        activeDownloadsMap.values.filter {
            it.state == DownloadState.PAUSED
        }
    }

    val filteredList = remember(downloadedMovies, searchQuery) {
        if (searchQuery.isBlank()) downloadedMovies else {
            downloadedMovies.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.category.contains(searchQuery, ignoreCase = true) ||
                (it.year != null && it.year.contains(searchQuery, ignoreCase = true))
            }
        }
    }

    val totalStorageSize = remember(downloadedMovies) {
        MovieDownloadManager.getTotalDownloadedStorageSize(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "📥 অফলাইন ডাউনলোডসমূহ",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${downloadedMovies.size} টি মুভি সংরক্ষিত • স্টোরেজ: $totalStorageSize",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.5.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            MovieDownloadManager.refreshDownloadedMoviesList(context)
                            Toast.makeText(context, "ডাউনলোড তালিকা রিফ্রেশ করা হয়েছে", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Refresh",
                            tint = Color(0xFF00E5FF)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0F172A))
            )
        },
        containerColor = Color(0xFF020617)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Active Downloads Progress Section (if any is downloading)
            if (activeProgressList.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0B1329))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = null,
                                tint = Color(0xFF00E5FF),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "ডাউনলোড চলছে (${activeProgressList.size} টি)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    activeProgressList.forEach { progress ->
                        ActiveDownloadCard(
                            progress = progress,
                            onPause = {
                                MovieDownloadManager.pauseDownload(progress.movieId)
                            },
                            onCancel = {
                                MovieDownloadManager.cancelDownload(progress.movieId, deletePartialFile = true, context = context)
                            }
                        )
                    }
                }
            }

            // Paused / Interrupted Downloads Section
            if (pausedProgressList.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF131C31))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.PauseCircle,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "স্থগিত ডাউনলোডসমূহ (${pausedProgressList.size} টি)",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    pausedProgressList.forEach { progress ->
                        PausedDownloadCard(
                            progress = progress,
                            onResume = {
                                MovieDownloadManager.resumeDownload(context, progress.movieId)
                            },
                            onDelete = {
                                MovieDownloadManager.deletePartialDownload(context, progress.movieId)
                            }
                        )
                    }
                }
            }

            // Search Box (if items exist)
            if (downloadedMovies.isNotEmpty()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            "সংরক্ষিত মুভি খুঁজুন...",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear", tint = Color.White)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1E293B),
                        unfocusedContainerColor = Color(0xFF1E293B),
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )
            }

            // Downloaded Movies List
            if (filteredList.isEmpty() && activeProgressList.isEmpty() && pausedProgressList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
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
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.DownloadForOffline,
                                    contentDescription = null,
                                    tint = Color(0xFF00E5FF),
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                        }
                        Text(
                            text = if (searchQuery.isNotEmpty()) "কোনো সংরক্ষিত মুভি পাওয়া যায়নি" else "কোনো অফলাইন মুভি নেই",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = if (searchQuery.isNotEmpty()) "ভিন্ন কি-ওয়ার্ড দিয়ে খুঁজুন।" else "যেকোনো মুভির বিস্তারিত থেকে 'ডাউনলোড করুন' বাটনে ক্লিক করে অফলাইনে সংরক্ষণ করুন। ইন্টারনেট ছাড়াই দেখতে পারবেন!",
                            color = Color(0xFF94A3B8),
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        if (searchQuery.isEmpty()) {
                            Button(
                                onClick = onExploreMovies,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                            ) {
                                Icon(Icons.Rounded.Movie, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("🎬 মুভি ক্যাটাগরি ব্রাউজ করুন", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredList, key = { it.id }) { movie ->
                        DownloadedMovieCard(
                            movie = movie,
                            onPlay = {
                                val mediaItem = movie.toMediaItem()
                                onPlayOfflineMedia(mediaItem)
                            },
                            onDelete = {
                                movieToDelete = movie
                            },
                            onShareOrOpenExternal = {
                                shareOrOpenExternalFile(context, movie)
                            }
                        )
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (movieToDelete != null) {
        val target = movieToDelete!!
        AlertDialog(
            onDismissRequest = { movieToDelete = null },
            icon = {
                Icon(Icons.Rounded.DeleteForever, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(32.dp))
            },
            title = {
                Text(
                    text = "মুভিটি ডিলিট করবেন?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Text(
                    text = "'${target.title}' ফাইলটি (${target.fileSizeFormatted}) আপনার ডিভাইস স্টোরেজ থেকে স্থায়ীভাবে মুছে ফেলা হবে।",
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val deleted = MovieDownloadManager.deleteDownloadedMovie(context, target.id)
                        if (deleted) {
                            Toast.makeText(context, "মুভিটি সফলভাবে ডিলিট করা হয়েছে", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "ডিলিট করতে সমস্যা হয়েছে", Toast.LENGTH_SHORT).show()
                        }
                        movieToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("ডিলিট করুন", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { movieToDelete = null }) {
                    Text("বাতিল", color = Color(0xFF94A3B8))
                }
            },
            containerColor = Color(0xFF1E293B),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun ActiveDownloadCard(
    progress: MovieDownloadProgress,
    onPause: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        border = BorderStroke(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f)),
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
                Text(
                    text = progress.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onPause,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Pause,
                            contentDescription = "Pause",
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Cancel",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            LinearProgressIndicator(
                progress = { if (progress.totalBytes > 0) progress.progress else 0.5f },
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
                    text = "${progress.downloadedSizeFormatted} / ${progress.totalSizeFormatted} (${progress.progressPercent}%)",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
                Text(
                    text = "⚡ ${progress.speedFormatted}",
                    color = Color(0xFF10B981),
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun PausedDownloadCard(
    progress: MovieDownloadProgress,
    onResume: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2438)),
        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.6f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⏸️ ${progress.title}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = "Delete Partial",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            LinearProgressIndicator(
                progress = { if (progress.totalBytes > 0) progress.progress else 0.5f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = Color(0xFFF59E0B),
                trackColor = Color(0xFF334155)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${progress.downloadedSizeFormatted} সংরক্ষিত (${progress.progressPercent}%)",
                    color = Color(0xFFF59E0B),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "স্থগিত (Resume করুন)",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                )
            }

            Button(
                onClick = onResume,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.fillMaxWidth().height(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "পুনরায় চালু করুন (${progress.downloadedSizeFormatted} থেকে)",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun DownloadedMovieCard(
    movie: DownloadedMovie,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    onShareOrOpenExternal: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1.0f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow),
        label = "downloadedCardScale"
    )

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        border = if (isFocused) BorderStroke(2.dp, Color(0xFF00E5FF)) else BorderStroke(1.dp, Color(0xFF334155)),
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onPlay() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Poster Thumbnail
            Box(
                modifier = Modifier
                    .size(width = 75.dp, height = 100.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F172A))
            ) {
                AsyncImage(
                    model = movie.logoUrl ?: "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=400&q=80",
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Surface(
                    shape = RoundedCornerShape(topStart = 8.dp, bottomEnd = 6.dp),
                    color = Color(0xFF10B981),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        text = "OFFLINE",
                        color = Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 8.5.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Movie Details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = movie.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF334155)
                    ) {
                        Text(
                            text = movie.quality,
                            color = Color(0xFF94A3B8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                    if (!movie.year.isNullOrBlank()) {
                        Text(
                            text = movie.year,
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                }

                Text(
                    text = "💾 ${movie.fileSizeFormatted} • ${movie.downloadDateFormatted}",
                    color = Color(0xFF64748B),
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Play Button & Actions
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onPlay,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("প্লে করুন", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }

                    IconButton(
                        onClick = onShareOrOpenExternal,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.OpenInNew,
                            contentDescription = "Open in External Player",
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

fun shareOrOpenExternalFile(context: Context, movie: DownloadedMovie) {
    try {
        val file = File(movie.localFilePath)
        if (!file.exists()) {
            Toast.makeText(context, "মুভি ফাইলটি পাওয়া যায়নি!", Toast.LENGTH_SHORT).show()
            return
        }
        val fileUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "মুভি চালান: ${movie.title}"))
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "প্লেয়ার খোলা সম্ভব হয়নি: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
