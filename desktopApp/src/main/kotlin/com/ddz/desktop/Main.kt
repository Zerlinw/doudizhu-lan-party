package com.ddz.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "斗地主 · 局域网") {
        MaterialTheme {
            DesktopPlaceholder()
        }
    }
}

@Composable
private fun DesktopPlaceholder() {
    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF1B1210)).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("斗地主 · 局域网版", color = Color(0xFFD4AF37), style = MaterialTheme.typography.headlineMedium)
        Text("桌面端 M5 预览正在构建中", color = Color(0xFFF5F1E8))
    }
}
