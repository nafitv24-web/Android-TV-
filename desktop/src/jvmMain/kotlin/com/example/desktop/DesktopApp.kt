package com.example.desktop

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.desktop.player.DesktopVlcPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.awt.Desktop
import java.net.URI

data class DesktopMediaItem(
    val id: String,
    val title: String,
    val category: String,
    val streamUrl: String,
    val logoUrl: String? = null,
    val isLive: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DesktopNafiTvApp() {
    var selectedTab by remember { mutableStateOf(0) } // 0: Live Events, 1: Live TV, 2: Sports Channels, 3: Movies
    var searchQuery by remember { mutableStateOf("") }
    var currentPlayingItem by remember { mutableStateOf<DesktopMediaItem?>(null) }
    var mediaList by remember { mutableStateOf<List<DesktopMediaItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()

    fun loadContent(tab: Int) {
        isLoading = true
        coroutineScope.launch(Dispatchers.IO) {
            val client = OkHttpClient()
            val items = mutableListOf<DesktopMediaItem>()

            // Default fallback URLs (Matches official Android App)
            var liveTvM3u = "https://raw.githubusercontent.com/nfiptv24-max/NAFITV/refs/heads/main/Nafitv24.m3u"
            var sportsM3u = "https://raw.githubusercontent.com/nfiptv24-max/NAFITV/refs/heads/main/NAFI%20Sports.m3u"
            var moviesM3u = "https://raw.githubusercontent.com/abusaeeidx/Movie-Playlist-Auto-update/refs/heads/main/Mix_Movies.m3u"
            var tapmadJson = "https://raw.githubusercontent.com/srhady/tapmad-bd/refs/heads/main/tapmad_bd.json"

            try {
                // 1. Fetch remote Firebase Realtime Database config
                val rtdbUrls = listOf(
                    "https://nafitv24-live-default-rtdb.firebaseio.com/app_config.json",
                    "https://nafitv24-default-rtdb.asia-southeast1.firebasedatabase.app/app_config.json"
                )

                for (fbUrl in rtdbUrls) {
                    try {
                        val configReq = Request.Builder().url(fbUrl).build()
                        val configResp = client.newCall(configReq).execute()
                        if (configResp.isSuccessful) {
                            val body = configResp.body?.string()
                            if (!body.isNullOrBlank() && body.startsWith("{")) {
                                val obj = JSONObject(body)
                                if (obj.has("liveTvM3uUrl") && obj.getString("liveTvM3uUrl").isNotBlank()) {
                                    liveTvM3u = obj.getString("liveTvM3uUrl")
                                }
                                if (obj.has("sportsM3uUrl") && obj.getString("sportsM3uUrl").isNotBlank()) {
                                    sportsM3u = obj.getString("sportsM3uUrl")
                                }
                                if (obj.has("moviesM3uUrl") && obj.getString("moviesM3uUrl").isNotBlank()) {
                                    moviesM3u = obj.getString("moviesM3uUrl")
                                }
                                if (obj.has("tapmadJsonUrl") && obj.getString("tapmadJsonUrl").isNotBlank()) {
                                    tapmadJson = obj.getString("tapmadJsonUrl")
                                }
                                break
                            }
                        }
                    } catch (_: Exception) {}
                }

                // 2. Fetch data according to the selected tab
                if (tab == 0) {
                    // LIVE EVENTS - Multi-JSON Support
                    val jsonUrls = tapmadJson.split("\n", ",").map { it.trim() }.filter { it.isNotBlank() }
                    for (jUrl in jsonUrls) {
                        try {
                            val jsonReq = Request.Builder()
                                .url(jUrl)
                                .header("User-Agent", "NAFITV24-Desktop/2.6.8")
                                .build()
                            val jsonResp = client.newCall(jsonReq).execute()
                            if (jsonResp.isSuccessful) {
                                val jsonStr = jsonResp.body?.string()?.trim() ?: ""
                                val matchesArray = if (jsonStr.startsWith("[")) {
                                    JSONArray(jsonStr)
                                } else if (jsonStr.startsWith("{")) {
                                    val root = JSONObject(jsonStr)
                                    root.optJSONArray("Matches")
                                        ?: root.optJSONArray("matches")
                                        ?: root.optJSONArray("events")
                                        ?: root.optJSONArray("data")
                                        ?: JSONArray()
                                } else {
                                    JSONArray()
                                }

                                for (i in 0 until matchesArray.length()) {
                                    val obj = matchesArray.getJSONObject(i)
                                    val vName = obj.optString("VideoName", obj.optString("title", obj.optString("name", "Match $i")))
                                    val sUrl = obj.optString("stream_url", obj.optString("streamUrl", obj.optString("url", "")))
                                    val cat = obj.optString("CategoryName", obj.optString("category", "লাইভ ম্যাচ"))
                                    val thumb = obj.optString("ThumbnailStandard", obj.optString("logo", obj.optString("image", null)))
                                    val status = obj.optString("Status", "Live")

                                    if (sUrl.isNotBlank()) {
                                        items.add(
                                            DesktopMediaItem(
                                                id = "event_${items.size}_$i",
                                                title = vName,
                                                category = cat,
                                                streamUrl = sUrl,
                                                logoUrl = thumb,
                                                isLive = status.contains("live", ignoreCase = true)
                                            )
                                        )
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } else {
                    // M3U PLAYLISTS: Live TV, Sports Channels, or Movies
                    val targetM3u = when (tab) {
                        1 -> liveTvM3u
                        2 -> sportsM3u
                        else -> moviesM3u
                    }

                    val m3uReq = Request.Builder()
                        .url(targetM3u.trim())
                        .header("User-Agent", "NAFITV24-Desktop/2.6.8")
                        .build()
                    val m3uResp = client.newCall(m3uReq).execute()
                    if (m3uResp.isSuccessful) {
                        val lines = m3uResp.body?.string()?.lines() ?: emptyList()
                        var curTitle = ""
                        var curLogo: String? = null
                        var curGroup = when (tab) {
                            1 -> "লাইভ টিভি"
                            2 -> "স্পোর্টস টিভি"
                            else -> "মুভিজ"
                        }

                        for (line in lines) {
                            val trimmed = line.trim()
                            if (trimmed.startsWith("#EXTINF:")) {
                                curTitle = trimmed.substringAfterLast(",").trim()
                                if (trimmed.contains("tvg-logo=\"")) {
                                    curLogo = trimmed.substringAfter("tvg-logo=\"").substringBefore("\"")
                                }
                                if (trimmed.contains("group-title=\"")) {
                                    curGroup = trimmed.substringAfter("group-title=\"").substringBefore("\"")
                                }
                            } else if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                                if (curTitle.isNotBlank()) {
                                    items.add(
                                        DesktopMediaItem(
                                            id = "ch_${items.size}",
                                            title = curTitle,
                                            category = curGroup,
                                            streamUrl = trimmed,
                                            logoUrl = curLogo,
                                            isLive = tab != 3
                                        )
                                    )
                                    curTitle = ""
                                    curLogo = null
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            withContext(Dispatchers.Main) {
                mediaList = items
                isLoading = false
            }
        }
    }

    LaunchedEffect(selectedTab) {
        loadContent(selectedTab)
    }

    // Filtered by Search
    val filteredList = remember(mediaList, searchQuery) {
        if (searchQuery.isBlank()) mediaList
        else mediaList.filter {
            it.title.contains(searchQuery, ignoreCase = true) || it.category.contains(searchQuery, ignoreCase = true)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F172A)
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // 1. Sidebar Navigation (PC Widescreen)
            NavigationRail(
                modifier = Modifier.width(220.dp).fillMaxHeight(),
                containerColor = Color(0xFF1E293B),
                header = {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF00E5FF),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.LiveTv, contentDescription = null, tint = Color.Black, modifier = Modifier.size(28.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("NAFI TV 24", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("PC Edition v2.6.8", color = Color(0xFF94A3B8), fontSize = 11.sp)
                    }
                }
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                NavigationRailItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Rounded.SportsCricket, contentDescription = null) },
                    label = { Text("লাইভ ইভেন্ট", fontWeight = FontWeight.SemiBold) }
                )
                NavigationRailItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Rounded.Tv, contentDescription = null) },
                    label = { Text("লাইভ টিভি", fontWeight = FontWeight.SemiBold) }
                )
                NavigationRailItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Rounded.SportsScore, contentDescription = null) },
                    label = { Text("স্পোর্টস টিভি", fontWeight = FontWeight.SemiBold) }
                )
                NavigationRailItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Rounded.Movie, contentDescription = null) },
                    label = { Text("মুভিজ", fontWeight = FontWeight.SemiBold) }
                )

                Spacer(modifier = Modifier.weight(1f))

                // Refresh Button
                OutlinedButton(
                    onClick = { loadContent(selectedTab) },
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("রিফ্রেশ", fontSize = 12.sp)
                }
            }

            // 2. Main Content Area
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight().padding(20.dp)
            ) {
                // Top Search Bar & Info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("ম্যাচ বা চ্যানেলের নাম খুঁজুন...", color = Color(0xFF64748B)) },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Color(0xFF38BDF8)) },
                        modifier = Modifier.width(420.dp),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true
                    )

                    Text(
                        text = "মোট চ্যানেল / ইভেন্ট: ${filteredList.size}",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Active Video Player Area (When an item is clicked)
                if (currentPlayingItem != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth().height(400.dp).padding(bottom = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.Black)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // Video Player with full controller controls
                            DesktopVlcPlayer(
                                title = currentPlayingItem!!.title,
                                streamUrl = currentPlayingItem!!.streamUrl,
                                isLive = currentPlayingItem!!.isLive,
                                category = currentPlayingItem!!.category,
                                onClose = { currentPlayingItem = null },
                                modifier = Modifier.fillMaxSize()
                            )

                            // Title Overlay Top Bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xCC0F172A))
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Rounded.PlayCircle, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = currentPlayingItem!!.title,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            try {
                                                if (Desktop.isDesktopSupported()) {
                                                    Desktop.getDesktop().browse(URI(currentPlayingItem!!.streamUrl))
                                                }
                                            } catch (_: Exception) {}
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Rounded.OpenInNew, contentDescription = "Open Stream", tint = Color(0xFF00E5FF))
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

                                    IconButton(
                                        onClick = { currentPlayingItem = null },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Close Player", tint = Color.White)
                                    }
                                }
                            }
                        }
                    }
                }

                // Grid Content
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF00E5FF))
                    }
                } else if (filteredList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("কোনো কনটেন্ট পাওয়া যায়নি", color = Color(0xFF94A3B8))
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 180.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredList) { item ->
                            Card(
                                onClick = { currentPlayingItem = item },
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                modifier = Modifier.fillMaxWidth().height(160.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize().padding(12.dp),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (item.isLive) Color(0xFFEF4444) else Color(0xFF334155)
                                        ) {
                                            Text(
                                                text = if (item.isLive) "LIVE" else item.category,
                                                color = Color.White,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(20.dp))
                                    }

                                    Spacer(modifier = Modifier.weight(1f))

                                    Text(
                                        text = item.title,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp,
                                        maxLines = 2,
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
