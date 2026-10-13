import os

with open('app/src/main/java/com/example/ui/NafiTvMainApp.kt', 'r', encoding='utf-8') as f:
    text = f.read()

# 1. Update AdminTab enum to add TICKER
old_enum = """enum class AdminTab(val label: String) {
    CHANNELS("Live TV Channels"),
    MOVIES("Movies"),
    PLAYLISTS("Playlists"),
    SPORTS("Sports Matches"),
    BROADCAST("নোটিফিকেশন পাঠান"),
    REPOSITORIES("CloudStream Repos"),
    APP_UPDATE("App Updates"),
    FIREBASE("Firebase Cloud")
}"""

new_enum = """enum class AdminTab(val label: String) {
    TICKER("ব্রেকিং নিউজ বার"),
    CHANNELS("Live TV Channels"),
    MOVIES("Movies"),
    PLAYLISTS("Playlists"),
    SPORTS("Sports Matches"),
    BROADCAST("নোটিফিকেশন পাঠান"),
    REPOSITORIES("CloudStream Repos"),
    APP_UPDATE("App Updates"),
    FIREBASE("Firebase Cloud")
}"""

if old_enum in text:
    text = text.replace(old_enum, new_enum, 1)
    print("AdminTab enum updated with TICKER!")
else:
    print("Could not find old_enum")

# 2. Update ModeSelectionScreen call to pass repository
old_mode_call = """    if (activeUserMode == null) {
        ModeSelectionScreen(
            onSelectMobileMode = {
                isTvMode = false
                activeUserMode = AppUserMode.MOBILE
            },
            onSelectRemoteMode = {
                isTvMode = true
                activeUserMode = AppUserMode.REMOTE
            },
            onExitApp = {
                showExitConfirmationDialog = true
            }
        )"""

new_mode_call = """    if (activeUserMode == null) {
        ModeSelectionScreen(
            repository = repository,
            onSelectMobileMode = {
                isTvMode = false
                activeUserMode = AppUserMode.MOBILE
            },
            onSelectRemoteMode = {
                isTvMode = true
                activeUserMode = AppUserMode.REMOTE
            },
            onExitApp = {
                showExitConfirmationDialog = true
            }
        )"""

if old_mode_call in text:
    text = text.replace(old_mode_call, new_mode_call, 1)
    print("ModeSelectionScreen call updated with repository!")
else:
    print("Could not find old_mode_call")

# 3. Add ticker form state variables in AdminView
old_admin_state = """    // Broadcast Notifications Form State
    var broadcastTitle by remember { mutableStateOf("") }"""

new_admin_state = """    // Marquee News Ticker Form State
    var marqueeTickerInput by remember { mutableStateOf(repository.getMarqueeTickerText()) }
    var isSavingMarqueeTicker by remember { mutableStateOf(false) }

    // Broadcast Notifications Form State
    var broadcastTitle by remember { mutableStateOf("") }"""

if old_admin_state in text:
    text = text.replace(old_admin_state, new_admin_state, 1)
    print("AdminView state variables updated with marquee ticker!")
else:
    print("Could not find old_admin_state")

# 4. Add TICKER button in Admin Tab row
old_admin_tabs_row = """                    item {
                        Button(
                            onClick = { selectedAdminTab = AdminTab.CHANNELS },"""

new_admin_tabs_row = """                    item {
                        Button(
                            onClick = { selectedAdminTab = AdminTab.TICKER },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selectedAdminTab == AdminTab.TICKER) Color(0xFFEC4899) else Color(0xFF1E293B),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Rounded.Campaign, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("⚡ ব্রেকিং নিউজ বার", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    item {
                        Button(
                            onClick = { selectedAdminTab = AdminTab.CHANNELS },"""

if old_admin_tabs_row in text:
    text = text.replace(old_admin_tabs_row, new_admin_tabs_row, 1)
    print("AdminTab row updated with TICKER tab button!")
else:
    print("Could not find old_admin_tabs_row")

# 5. Add TAB CONTENT for AdminTab.TICKER
ticker_tab_content = """            // -------------------------------------------------------------
            // TAB: SCROLLING BREAKING NEWS TICKER MANAGEMENT
            // -------------------------------------------------------------
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
                                        if (marqueeTickerInput.isBlank()) {
                                            Toast.makeText(context, "অনুগ্রহ করে নিউজ টেক্সট লিখুন!", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        isSavingMarqueeTicker = true
                                        coroutineScope.launch {
                                            repository.saveMarqueeTickerText(marqueeTickerInput.trim())
                                            repository.pushMarqueeTickerToFirebase(marqueeTickerInput.trim())
                                            isSavingMarqueeTicker = false
                                            Toast.makeText(context, "✅ নিউজ বার সফলভাবে আপডেট ও ক্লাউডে সিঙ্ক হয়েছে!", Toast.LENGTH_LONG).show()
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
                                        Text("সংরক্ষণ হচ্ছে...", fontWeight = FontWeight.Bold)
                                    } else {
                                        Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("আপডেট ও ক্লাউডে সিঙ্ক করুন", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        marqueeTickerInput = MediaRepository.DEFAULT_MARQUEE_TEXT
                                        coroutineScope.launch {
                                            repository.saveMarqueeTickerText(MediaRepository.DEFAULT_MARQUEE_TEXT)
                                            repository.pushMarqueeTickerToFirebase(MediaRepository.DEFAULT_MARQUEE_TEXT)
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

"""

sports_tab_marker = """            // -------------------------------------------------------------
            // TAB 1 CONTENT: LIVE SPORTS MATCHES (CRITICAL REQUIREMENT)
            // -------------------------------------------------------------"""

if sports_tab_marker in text:
    text = text.replace(sports_tab_marker, ticker_tab_content + sports_tab_marker, 1)
    print("TICKER tab content added successfully before Sports Tab!")
else:
    print("Could not find sports_tab_marker")

with open('app/src/main/java/com/example/ui/NafiTvMainApp.kt', 'w', encoding='utf-8') as f:
    f.write(text)

print("NafiTvMainApp.kt updated successfully!")
