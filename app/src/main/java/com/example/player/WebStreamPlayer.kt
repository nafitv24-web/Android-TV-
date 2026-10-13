package com.example.player

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebStreamPlayer(
    embedUrl: String,
    title: String,
    modifier: Modifier = Modifier,
    onDirectStreamDetected: (String) -> Unit = {},
    onClose: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isExtractingNative by remember { mutableStateOf(true) }
    var extractionStatus by remember { mutableStateOf("স্ট্রিম এক্সট্রাক্ট করা হচ্ছে (Bypassing Ads)...") }
    var loadProgress by remember { mutableFloatStateOf(0f) }
    var customViewContainer by remember { mutableStateOf<FrameLayout?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var blockedAdsCount by remember { mutableIntStateOf(0) }

    // Comprehensive list of ad and popup tracking networks to completely block
    val adFilterKeywords = remember {
        listOf(
            "adsterra", "popcash", "propellerads", "onclick", "syndication", "exoclick",
            "juicyads", "trafficjunky", "doubleclick", "googleadservices", "taboola",
            "outbrain", "bet365", "1xbet", "parimatch", "melbet", "adtraffic", "hilltopads",
            "yandex.ru/ads", "adtrue", "monetag", "adnxs", "popads", "revenuehits", "adservice",
            "histats", "coinimp", "coinhive", "clickadu", "adcash", "adcolony", "admob",
            "banner", "popup", "adserver", "adskeeper", "trafficstars", "propeller",
            "vidoza", "openload", "gounlimited", "whomever"
        )
    }

    // Try background OkHttp extraction first before rendering WebView
    LaunchedEffect(embedUrl) {
        val lower = embedUrl.lowercase()
        // If it's a social/embed player that plays best in web engine, skip native extraction
        if (!lower.contains("youtube.com") && !lower.contains("youtu.be")) {
            isExtractingNative = true
            extractionStatus = "অ্যাডহীন নেটিভ প্লেয়ারের জন্য স্ট্রিম এক্সট্রাক্ট হচ্ছে..."
            val directResult = StreamExtractor.extractDirectStream(embedUrl)
            if (directResult != null && directResult.streamUrl.isNotBlank() &&
                (directResult.streamUrl.contains(".m3u8", ignoreCase = true) ||
                 directResult.streamUrl.contains(".mpd", ignoreCase = true) ||
                 directResult.streamUrl.contains(".mp4", ignoreCase = true) ||
                 directResult.streamUrl.contains(".mkv", ignoreCase = true) ||
                 directResult.streamUrl.contains("pixeldrain", ignoreCase = true))) {
                onDirectStreamDetected(directResult.streamUrl)
                return@LaunchedEffect
            }
            isExtractingNative = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.destroy()
            webViewInstance = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Custom Fullscreen View if HTML5 video requests full screen
        if (customViewContainer != null) {
            AndroidView(
                factory = { customViewContainer!! },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Main Web Player View with Ad-Shield & Stream Sniffer
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewInstance = this
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setBackgroundColor(android.graphics.Color.BLACK)

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            mediaPlaybackRequiresUserGesture = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            allowFileAccess = false
                            allowContentAccess = false
                            javaScriptCanOpenWindowsAutomatically = false
                            setSupportMultipleWindows(false)
                            userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 CloudStream/4.0"
                        }

                        // Inject JS Interface for immediate stream URL bridging
                        addJavascriptInterface(object {
                            @JavascriptInterface
                            fun onFoundStream(url: String) {
                                if (url.isNotBlank() && !url.startsWith("blob:", ignoreCase = true) && !adFilterKeywords.any { url.contains(it, ignoreCase = true) }) {
                                    post {
                                        onDirectStreamDetected(url)
                                    }
                                }
                            }

                            @JavascriptInterface
                            fun onAdBlocked() {
                                post {
                                    blockedAdsCount++
                                }
                            }
                        }, "NativeStreamBridge")

                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val reqUrl = request?.url?.toString() ?: return null
                                val lower = reqUrl.lowercase()

                                // Block known aggressive ad networks
                                if (adFilterKeywords.any { lower.contains(it) }) {
                                    post { blockedAdsCount++ }
                                    return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream("".toByteArray()))
                                }

                                // Sniff direct streams (.m3u8, .mpd, .mp4, /dash/, /hls/, blob/stream endpoints)
                                val isDirectVideo = (lower.contains(".m3u8") ||
                                        lower.contains(".mpd") ||
                                        lower.contains("/dash/") ||
                                        lower.contains("/hls/") ||
                                        (lower.contains(".mp4") && !lower.contains("thumb") && !lower.contains("preview"))) &&
                                        !lower.startsWith("blob:")

                                if (isDirectVideo) {
                                    post {
                                        onDirectStreamDetected(reqUrl)
                                    }
                                }

                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val targetUrl = request?.url?.toString()?.lowercase() ?: return false
                                // Prevent popups and ad redirects to external domains
                                if (adFilterKeywords.any { targetUrl.contains(it) } ||
                                    targetUrl.startsWith("intent:") ||
                                    targetUrl.startsWith("market:") ||
                                    targetUrl.startsWith("tel:")) {
                                    post { blockedAdsCount++ }
                                    return true // Block navigation
                                }
                                return false
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false

                                // Advanced DOM Script: Strip ads, remove overlays/iframes with popups, and hook HTML5 video tag
                                val adBlockAndSniffScript = """
                                    (function() {
                                        // 1. Remove all popup overlays, transparent click hijackers, and ad banners
                                        function removeAds() {
                                            var adSelectors = [
                                                'iframe[src*="ad"]', 'iframe[src*="pop"]', 'div[class*="popup"]',
                                                'div[id*="popup"]', 'div[class*="overlay"]', 'div[id*="ad-"]',
                                                'div[class*="ad-"]', 'a[target="_blank"]', 'div[style*="z-index: 2147483647"]'
                                            ];
                                            adSelectors.forEach(function(sel) {
                                                try {
                                                    var elements = document.querySelectorAll(sel);
                                                    elements.forEach(function(el) {
                                                        if (el.tagName !== 'VIDEO') {
                                                            el.remove();
                                                            if (window.NativeStreamBridge) window.NativeStreamBridge.onAdBlocked();
                                                        }
                                                    });
                                                } catch(e) {}
                                            });
                                            
                                            // Neutralize window.open popups
                                            window.open = function() {
                                                if (window.NativeStreamBridge) window.NativeStreamBridge.onAdBlocked();
                                                return null;
                                            };
                                        }
                                        removeAds();
                                        setInterval(removeAds, 1000);

                                        // 2. Sniff video elements directly from DOM
                                        function sniffVideo() {
                                            var videos = document.querySelectorAll('video');
                                            videos.forEach(function(v) {
                                                var src = v.currentSrc || v.src;
                                                if (src && (src.includes('.m3u8') || src.includes('.mpd') || src.includes('.mp4') || src.includes('blob:'))) {
                                                    if (window.NativeStreamBridge) {
                                                        window.NativeStreamBridge.onFoundStream(src);
                                                    }
                                                }
                                                v.play().catch(function(){});
                                            });
                                        }
                                        sniffVideo();
                                        setInterval(sniffVideo, 800);

                                        // 3. Intercept XMLHttpRequest and Fetch for HLS/DASH manifest URLs
                                        var origOpen = XMLHttpRequest.prototype.open;
                                        XMLHttpRequest.prototype.open = function(method, url) {
                                            if (typeof url === 'string' && (url.includes('.m3u8') || url.includes('.mpd') || url.includes('master.txt'))) {
                                                if (window.NativeStreamBridge) {
                                                    window.NativeStreamBridge.onFoundStream(url);
                                                }
                                            }
                                            return origOpen.apply(this, arguments);
                                        };

                                        if (window.fetch) {
                                            var origFetch = window.fetch;
                                            window.fetch = function(input, init) {
                                                var url = typeof input === 'string' ? input : (input ? input.url : '');
                                                if (url && (url.includes('.m3u8') || url.includes('.mpd'))) {
                                                    if (window.NativeStreamBridge) {
                                                        window.NativeStreamBridge.onFoundStream(url);
                                                    }
                                                }
                                                return origFetch.apply(this, arguments);
                                            };
                                        }
                                    })();
                                """.trimIndent()

                                view?.evaluateJavascript(adBlockAndSniffScript, null)
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadProgress = newProgress / 100f
                                if (newProgress >= 100) isLoading = false
                            }

                            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                customViewCallback = callback
                                if (view is FrameLayout) {
                                    customViewContainer = view
                                } else if (view != null) {
                                    val frame = FrameLayout(ctx).apply {
                                        addView(view)
                                    }
                                    customViewContainer = frame
                                }
                            }

                            override fun onHideCustomView() {
                                customViewContainer = null
                                customViewCallback?.onCustomViewHidden()
                                customViewCallback = null
                            }

                            override fun onCreateWindow(
                                view: WebView?,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: android.os.Message?
                            ): Boolean {
                                // Block new popup window requests completely
                                blockedAdsCount++
                                return false
                            }
                        }

                        loadSmartEmbed(this, embedUrl)
                    }
                },
                update = { view ->
                    val currentTag = view.tag as? String
                    if (currentTag != embedUrl) {
                        view.tag = embedUrl
                        loadSmartEmbed(view, embedUrl)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Progress bar
        if (isLoading) {
            LinearProgressIndicator(
                progress = { loadProgress },
                color = Color(0xFF00E5FF),
                trackColor = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.TopCenter)
            )
        }

        // Top Shield Badge Overlay (Shows AdBlock status and Refresh)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black.copy(alpha = 0.75f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.6f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Shield, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ad-Shield Active ($blockedAdsCount Ads Blocked)", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                IconButton(
                    onClick = { webViewInstance?.reload() },
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                ) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Reload", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

private fun loadSmartEmbed(webView: WebView, rawUrl: String) {
    val clean = rawUrl.trim()
    val lower = clean.lowercase()

    if (lower.contains("youtube.com") || lower.contains("youtu.be")) {
        val ytId = StreamExtractor.extractYouTubeId(clean) ?: clean
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    * { margin:0; padding:0; box-sizing:border-box; background:#000; overflow:hidden; }
                    html, body, iframe { width:100vw; height:100vh; border:none; display:block; }
                </style>
            </head>
            <body>
                <iframe src="https://www.youtube-nocookie.com/embed/$ytId?autoplay=1&playsinline=1&rel=0&modestbranding=1&controls=1" 
                    allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; fullscreen" 
                    allowfullscreen>
                </iframe>
            </body>
            </html>
        """.trimIndent()
        webView.loadDataWithBaseURL("https://www.youtube-nocookie.com", html, "text/html", "UTF-8", null)
    } else if (lower.contains("facebook.com") || lower.contains("fb.watch") || lower.contains("fb.com")) {
        val encoded = try {
            java.net.URLEncoder.encode(clean, "UTF-8")
        } catch (_: Exception) {
            clean
        }
        val fbPluginUrl = "https://www.facebook.com/plugins/video.php?href=$encoded&autoplay=1&show_text=0"
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    * { margin:0; padding:0; box-sizing:border-box; background:#000; overflow:hidden; }
                    html, body, iframe { width:100vw; height:100vh; border:none; display:block; }
                </style>
            </head>
            <body>
                <iframe src="$fbPluginUrl" 
                    allow="autoplay; clipboard-write; encrypted-media; picture-in-picture; web-share; fullscreen" 
                    allowfullscreen>
                </iframe>
            </body>
            </html>
        """.trimIndent()
        webView.loadDataWithBaseURL("https://www.facebook.com", html, "text/html", "UTF-8", null)
    } else if (lower.contains(".m3u8") || lower.contains("/hls/")) {
        val uriHost = try { android.net.Uri.parse(clean).host } catch (_: Exception) { null }
        val baseUrl = if (!uriHost.isNullOrBlank()) "https://$uriHost/" else "https://www.tapmad.com/"
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                <style>
                    * { margin:0; padding:0; box-sizing:border-box; background:#000; overflow:hidden; }
                    html, body, video { width:100vw; height:100vh; border:none; display:block; object-fit:contain; background:#000; }
                </style>
                <script src="https://cdn.jsdelivr.net/npm/hls.js@1.5.8/dist/hls.min.js"></script>
            </head>
            <body>
                <video id="video" controls autoplay playsinline webkit-playsinline></video>
                <script>
                    var video = document.getElementById('video');
                    var src = '$clean';
                    if (window.Hls && Hls.isSupported()) {
                        var hls = new Hls({ enableWorker: true, lowLatencyMode: true });
                        hls.loadSource(src);
                        hls.attachMedia(video);
                        hls.on(Hls.Events.MANIFEST_PARSED, function() {
                            video.play().catch(function(e) { console.log(e); });
                        });
                    } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
                        video.src = src;
                        video.addEventListener('loadedmetadata', function() {
                            video.play().catch(function(e) { console.log(e); });
                        });
                    }
                </script>
            </body>
            </html>
        """.trimIndent()
        webView.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
    } else {
        webView.loadUrl(clean)
    }
}
