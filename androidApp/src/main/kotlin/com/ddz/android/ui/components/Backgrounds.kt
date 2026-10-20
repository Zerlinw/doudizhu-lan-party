package com.ddz.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.sin

/** Multi-layer gradient wood grain, no bitmap assets. */
@Composable
fun WoodBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                listOf(Color(0xFF2B1C15), Color(0xFF1B1210), Color(0xFF120C0A)),
            ),
        ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val plankHeight = size.height / 9f
            for (plank in 0 until 9) {
                val top = plank * plankHeight
                drawRect(
                    color = if (plank % 2 == 0) Color(0x0FFFFFFF) else Color(0x08000000),
                    topLeft = Offset(0f, top),
                    size = Size(size.width, plankHeight),
                )
                drawLine(
                    color = Color(0x33000000),
                    start = Offset(0f, top),
                    end = Offset(size.width, top),
                    strokeWidth = 1.5f,
                )
                // Wavy grain inside each plank.
                for (line in 0..3) {
                    val base = top + plankHeight * (line + 1) / 4f
                    var x = 0f
                    while (x < size.width) {
                        val nextX = x + 18f
                        val y = base + sin((x / size.width) * 22f + line + plank) * 2.2f
                        drawLine(
                            color = Color(0x12FFFFFF),
                            start = Offset(x, y),
                            end = Offset(nextX, y + 0.6f),
                            strokeWidth = 1f,
                        )
                        x = nextX
                    }
                }
            }
            // Vignette.
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color(0x66000000)),
                    center = Offset(size.width * 0.5f, size.height * 0.42f),
                    radius = size.maxDimension * 0.72f,
                ),
            )
        }
        content()
    }
}

/** Green felt tabletop with subtle fibre noise. */
@Composable
fun FeltPanel(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit = {}) {
    Box(
        modifier = modifier.background(
            Brush.radialGradient(
                listOf(Color(0xFF2A7550), Color(0xFF1E5B3A), Color(0xFF123A25)),
            ),
        ),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            for (i in 0 until 420) {
                val fx = ((i * 7919) % 1000) / 1000f
                val fy = ((i * 104729) % 1000) / 1000f
                drawCircle(
                    color = Color(0x0DFFFFFF),
                    radius = 1.1f,
                    center = Offset(fx * size.width, fy * size.height),
                )
            }
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color(0x55000000)),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = size.maxDimension * 0.75f,
                ),
            )
        }
        content()
    }
}
