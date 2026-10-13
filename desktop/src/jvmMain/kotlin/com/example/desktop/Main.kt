package com.example.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import java.awt.Dimension

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "NAFI TV 24 - Live Sports, Live TV & Movies",
        state = WindowState(width = 1280.dp, height = 800.dp)
    ) {
        window.minimumSize = Dimension(960, 600)
        DesktopNafiTvApp()
    }
}
