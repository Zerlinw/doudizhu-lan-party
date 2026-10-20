package com.ddz.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddz.android.ui.theme.DdzColors
import com.ddz.shared.game.core.Card
import com.ddz.shared.game.core.Suit
import kotlin.math.min

/**
 * A single card face. All visuals are drawn with Compose primitives; no bitmap
 * assets are used, which keeps the same code usable on every Compose target.
 */
@Composable
fun PlayingCard(
    card: Card,
    width: Dp,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val height = width * 1.45f
    val shape = RoundedCornerShape(width * 0.15f)
    val ink = if (card.suit.isRed) DdzColors.Red else DdzColors.Ink
    val borderWidth = if (selected) 2.dp else 1.dp
    val faceBrush = if (card.isJoker) {
        if (card.rank.label == "大王") {
            Brush.verticalGradient(listOf(Color(0xFFF7E7B0), Color(0xFFD4AF37), Color(0xFFF5F1E8)))
        } else {
            Brush.verticalGradient(listOf(Color(0xFFEDEDED), Color(0xFFB9B9C2), Color(0xFFF5F1E8)))
        }
    } else {
        Brush.verticalGradient(listOf(DdzColors.Cream, Color(0xFFEFE8D8)))
    }

    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .shadow(if (selected) 10.dp else 3.dp, shape, clip = false)
            .clip(shape)
            .background(faceBrush)
            .border(borderWidth, if (selected) DdzColors.Gold else DdzColors.CardEdge, shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled) { onClick() }
                } else {
                    Modifier
                },
            ),
    ) {
        if (card.isJoker) {
            JokerFace(card, width, ink)
        } else {
            NormalFace(card, width, ink)
        }
    }
}

@Composable
private fun NormalFace(card: Card, width: Dp, ink: Color) {
    val rankSize = (width.value * 0.30f).coerceAtLeast(11f).sp
    val pipSize = (width.value * 0.65f).coerceAtLeast(15f).sp
    val mini = (width.value * 0.24f).coerceAtLeast(9f).sp
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(Alignment.TopStart).padding(width * 0.05f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = card.rank.label,
                color = ink,
                fontSize = rankSize,
                lineHeight = rankSize,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = card.suit.symbol,
                color = ink,
                fontSize = mini,
                lineHeight = mini,
            )
        }
        Text(
            text = card.suit.symbol,
            color = ink.copy(alpha = 0.92f),
            fontSize = pipSize,
            modifier = Modifier.align(Alignment.Center),
        )
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(width * 0.05f).rotate(180f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = card.rank.label,
                color = ink,
                fontSize = rankSize,
                lineHeight = rankSize,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = card.suit.symbol,
                color = ink,
                fontSize = mini,
                lineHeight = mini,
            )
        }
    }
}

@Composable
private fun JokerFace(card: Card, width: Dp, ink: Color) {
    val isBig = card.rank.label == "大王"
    val charSize = (width.value * 0.36f).coerceAtLeast(12f).sp
    val label = if (isBig) "大王" else "小王"
    Column(
        modifier = Modifier.fillMaxSize().padding(width * 0.04f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "JOKER",
            color = if (isBig) DdzColors.Red else DdzColors.Ink,
            fontSize = (width.value * 0.16f).coerceAtLeast(7f).sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp,
        )
        Text(
            text = label,
            color = if (isBig) DdzColors.Red else DdzColors.Ink,
            fontSize = charSize,
            lineHeight = charSize,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "★",
            color = if (isBig) DdzColors.Gold else Color(0xFF6E6E78),
            fontSize = (width.value * 0.24f).coerceAtLeast(9f).sp,
        )
    }
}

/** Face-down card with a woven pattern and a gold 斗 seal. */
@Composable
fun CardBack(width: Dp, modifier: Modifier = Modifier) {
    val height = width * 1.45f
    val shape = RoundedCornerShape(width * 0.14f)
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .shadow(2.dp, shape, clip = false)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF7A1F24), Color(0xFF421014))))
            .border(1.dp, DdzColors.Gold.copy(alpha = 0.7f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val step = size.minDimension / 5f
            var offset = -size.height
            while (offset < size.width + size.height) {
                drawLine(
                    color = Color(0x22F5F1E8),
                    start = Offset(offset, 0f),
                    end = Offset(offset + size.height, size.height),
                    strokeWidth = 1f,
                )
                drawLine(
                    color = Color(0x22F5F1E8),
                    start = Offset(offset, size.height),
                    end = Offset(offset + size.height, 0f),
                    strokeWidth = 1f,
                )
                offset += step
            }
        }
        Text(
            text = "斗",
            color = DdzColors.Gold.copy(alpha = 0.92f),
            fontSize = (width.value * 0.42f).coerceAtLeast(10f).sp,
            fontWeight = FontWeight.Black,
        )
    }
}

/** Overlapping stack of face-down cards plus the remaining count. */
@Composable
fun CardBackStack(
    count: Int,
    modifier: Modifier = Modifier,
    width: Dp = 22.dp,
    showCount: Boolean = true,
) {
    val visible = min(count.coerceAtLeast(0), 3)
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(if (visible == 0) width else width + width * 0.55f * (visible - 1))
                .height(width * 1.45f),
        ) {
            repeat(visible) { index ->
                CardBack(
                    width = width,
                    modifier = Modifier.padding(start = width * 0.55f * index),
                )
            }
        }
        if (showCount) {
            Text(
                text = "×$count",
                color = DdzColors.Cream,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

/** Small avatar disc driven by a deterministic seed. */
@Composable
fun Avatar(seed: Int, size: Dp = 40.dp, isLandlord: Boolean = false) {
    val emojis = listOf("🦊", "🐼", "🐯", "🐵", "🐸", "🐷", "🐺", "🐨")
    val emoji = emojis[((seed % emojis.size) + emojis.size) % emojis.size]
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(size / 2))
            .background(
                Brush.linearGradient(
                    listOf(
                        if (isLandlord) DdzColors.GoldSoft else Color(0xFF3B5A48),
                        if (isLandlord) DdzColors.Gold else Color(0xFF203729),
                    ),
                ),
            )
            .border(if (isLandlord) 2.dp else 1.dp, if (isLandlord) DdzColors.Gold else Color(0x55FFFFFF), RoundedCornerShape(size / 2)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = emoji, fontSize = (size.value * 0.5f).sp)
    }
}

/** A generic suit watermark used by the home screen. */
@Composable
fun SuitWatermark(suit: Suit, color: Color, modifier: Modifier = Modifier) {
    Text(text = suit.symbol, color = color, fontSize = 48.sp, modifier = modifier)
}
