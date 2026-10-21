package com.ddz.android.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddz.android.ui.theme.DdzColors
import com.ddz.shared.game.core.Card
import com.ddz.shared.game.core.Rank
import kotlinx.coroutines.delay
import kotlin.math.min

/**
 * Fan-shaped hand layout: cards rotate around their bottom centre, the outside
 * cards lift slightly, and each card animates in with a 45 ms stagger.
 */
@Composable
fun HandFan(
    cards: List<Card>,
    selected: Set<Int>,
    round: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    maxCardWidth: Dp = 68.dp,
    laiziRanks: Set<Rank> = emptySet(),
    onToggle: (Card) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        if (cards.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(104.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("等待发牌…", color = DdzColors.Muted, fontSize = 13.sp)
            }
        } else {
            val count = cards.size
            val available = maxWidth
            val cardWidth = if (count == 1) {
                maxCardWidth
            } else {
                ((available - 8.dp) / (1f + (count - 1) * 0.55f))
                    .coerceIn(24.dp, maxCardWidth)
            }
            val cardHeight = cardWidth * 1.45f
            val spacing = if (count == 1) {
                0.dp
            } else {
                minOf(cardWidth * 0.55f, (available - cardWidth) / (count - 1))
            }
            val totalWidth = cardWidth + spacing * (count - 1)
            val spread = min(count * 2.1f, 34f)
            val center = (count - 1) / 2f

            Layout(
                modifier = Modifier.fillMaxWidth(),
                content = {
                    cards.forEachIndexed { index, card ->
                        val isSelected = selected.contains(card.code)
                        Box(
                            modifier = Modifier.graphicsLayer {
                                rotationZ = if (count > 1) -spread / 2f + spread * index / (count - 1) else 0f
                                transformOrigin = TransformOrigin(0.5f, 1f)
                                val p = if (center == 0f) 0f else (index - center) / center
                                translationY = -p * p * 10.dp.toPx() - if (isSelected) 20.dp.toPx() else 0f
                            },
                        ) {
                            DealIn(round = round, cardCode = card.code, index = index) {
                                PlayingCard(
                                    card = card,
                                    width = cardWidth,
                                    selected = isSelected,
                                    enabled = enabled,
                                    isLaizi = card.rank in laiziRanks,
                                    onClick = { onToggle(card) },
                                )
                            }
                        }
                    }
                },
            ) { measurables, constraints ->
                val placeables = measurables.map {
                    it.measure(
                        Constraints.fixed(cardWidth.roundToPx(), cardHeight.roundToPx()),
                    )
                }
                val height = (cardHeight + 40.dp).roundToPx()
                layout(constraints.maxWidth, height) {
                    val startX = ((constraints.maxWidth - totalWidth.toPx()) / 2f)
                        .toInt()
                        .coerceAtLeast(0)
                    placeables.forEachIndexed { index, placeable ->
                        placeable.place(
                            x = startX + (spacing * index).roundToPx(),
                            y = 20.dp.roundToPx(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DealIn(
    round: Int,
    cardCode: Int,
    index: Int,
    content: @Composable () -> Unit,
) {
    val anim = remember(round, cardCode) { Animatable(0f) }
    LaunchedEffect(round, cardCode) {
        delay(min(index, 14) * 45L)
        anim.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.78f,
                stiffness = Spring.StiffnessMediumLow,
            ),
        )
    }
    Box(
        modifier = Modifier.graphicsLayer {
            val v = anim.value.coerceIn(0f, 1f)
            alpha = v
            translationY = (1f - v) * 240f
            scaleX = 0.72f + 0.28f * v
            scaleY = 0.72f + 0.28f * v
        },
    ) {
        content()
    }
}
