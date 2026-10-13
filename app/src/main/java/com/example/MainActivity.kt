package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.ui.NafiTvMainApp
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private var incomingRepoUrl by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        extractDeepLink(intent)

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF020617)
                ) {
                    NafiTvMainApp(
                        deepLinkRepoUrl = incomingRepoUrl,
                        onClearDeepLink = { incomingRepoUrl = null }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            enableEdgeToEdge()
        } catch (_: Exception) {}
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractDeepLink(intent)
    }

    private fun extractDeepLink(intent: Intent?) {
        if (intent == null) return
        val data: Uri? = intent.data
        if (data != null) {
            val urlString = data.toString()
            if (urlString.isNotBlank()) {
                incomingRepoUrl = urlString
            }
        }
    }
}
