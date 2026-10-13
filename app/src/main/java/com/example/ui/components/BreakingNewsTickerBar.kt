package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast

@Composable
fun BreakingNewsTickerBar(
    tickerText: String,
    modifier: Modifier = Modifier,
    isTvMode: Boolean = false,
    showBorder: Boolean = true,
    customTitle: String = "ব্রেকিং নিউজ"
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var showDetailDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { showDetailDialog = true },
        shape = RoundedCornerShape(if (isTvMode) 10.dp else 8.dp),
        color = Color(0xFF0F172A),
        border = if (showBorder) BorderStroke(1.dp, Color(0xFFDC2626).copy(alpha = 0.55f)) else null,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF991B1B).copy(alpha = 0.9f),
                            Color(0xFF7F1D1D).copy(alpha = 0.5f),
                            Color(0xFF0F172A)
                        )
                    )
                )
                .padding(horizontal = 8.dp, vertical = if (isTvMode) 6.dp else 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Live Red Badge (Clean, battery-friendly static indicator)
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFFDC2626),
                border = BorderStroke(0.5.dp, Color(0xFFFCA5A5))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                    Text(
                        text = customTitle,
                        color = Color.White,
                        fontSize = if (isTvMode) 11.sp else 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.3.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Scrolling Marquee Text
            Text(
                text = tickerText.ifBlank { "NAFI TV24 এ ক্রিকেট, ফুটবল ও লাইভ টিভি চ্যানেল সম্পূর্ণ বিনামূল্যে উপভোগ করুন।" },
                color = Color(0xFFF8FAFC),
                fontSize = if (isTvMode) 12.5.sp else 11.5.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .weight(1f)
                    .basicMarquee(
                        iterations = Int.MAX_VALUE,
                        velocity = if (isTvMode) 40.dp else 35.dp,
                        initialDelayMillis = 600
                    )
            )
        }
    }

    if (showDetailDialog) {
        AlertDialog(
            onDismissRequest = { showDetailDialog = false },
            containerColor = Color(0xFF1E293B),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Campaign,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "🚨 $customTitle",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = tickerText.ifBlank { "কোনো চলমান ব্রেকিং নিউজ নেই।" },
                        color = Color(0xFFE2E8F0),
                        fontSize = 13.5.sp,
                        lineHeight = 20.sp
                    )
                    Text(
                        text = "📌 NAFI TV24 লাইভ আপডেট",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(tickerText))
                        Toast.makeText(context, "সংবাদ কপি করা হয়েছে!", Toast.LENGTH_SHORT).show()
                        showDetailDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFDC2626),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("কপি করুন", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDetailDialog = false }) {
                    Text("বন্ধ করুন", color = Color(0xFF94A3B8), fontSize = 12.sp)
                }
            }
        )
    }
}
