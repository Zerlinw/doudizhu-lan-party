package com.ddz.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Palette: dark walnut wood + casino-green felt + cream cards + antique gold. */
object DdzColors {
    val WoodDark = Color(0xFF1B1210)
    val WoodMid = Color(0xFF2A1C16)
    val WoodLight = Color(0xFF3B281F)
    val Felt = Color(0xFF1E5B3A)
    val FeltLight = Color(0xFF2A7550)
    val FeltDark = Color(0xFF123A25)
    val Cream = Color(0xFFF5F1E8)
    val CardEdge = Color(0xFFC9C0AE)
    val Gold = Color(0xFFD4AF37)
    val GoldSoft = Color(0xFFE7C766)
    val Red = Color(0xFFB02A2A)
    val Ink = Color(0xFF23201D)
    val Muted = Color(0xFFA79A8D)
    val Success = Color(0xFF4CAF7D)
    val Warning = Color(0xFFE08E3C)
}

@Composable
fun DdzTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = DdzColors.Gold,
            onPrimary = DdzColors.Ink,
            secondary = DdzColors.Red,
            onSecondary = DdzColors.Cream,
            background = DdzColors.WoodDark,
            onBackground = DdzColors.Cream,
            surface = DdzColors.WoodMid,
            onSurface = DdzColors.Cream,
            surfaceVariant = DdzColors.WoodLight,
            onSurfaceVariant = DdzColors.Cream,
            error = DdzColors.Red,
        ),
        typography = Typography(),
        content = content,
    )
}
