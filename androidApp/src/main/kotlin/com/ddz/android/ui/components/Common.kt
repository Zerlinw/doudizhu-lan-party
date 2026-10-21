package com.ddz.android.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddz.android.ui.theme.DdzColors
import kotlinx.coroutines.delay

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = DdzColors.Gold,
            contentColor = DdzColors.Ink,
            disabledContainerColor = Color(0xFF4A4034),
            disabledContentColor = Color(0xFF8E8478),
        ),
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun GoldOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Int = 50,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(height.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (enabled) DdzColors.Gold.copy(alpha = 0.75f) else Color(0xFF4A4034)),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (enabled) DdzColors.Cream else Color(0xFF8E8478),
        ),
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun TagChip(
    text: String,
    color: Color = DdzColors.Gold,
    filled: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = if (filled) color else color.copy(alpha = 0.14f),
        border = if (filled) null else BorderStroke(1.dp, color.copy(alpha = 0.45f)),
    ) {
        Text(
            text = text,
            color = if (filled) DdzColors.Ink else color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun BackHeader(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "‹  返回",
            color = DdzColors.GoldSoft,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clickable(onClick = onBack)
                .padding(horizontal = 4.dp, vertical = 8.dp),
        )
        Text(
            text = title,
            color = DdzColors.Cream,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
        if (trailing != null) {
            trailing()
        } else {
            Text(" ", fontSize = 15.sp, modifier = Modifier.width(44.dp))
        }
    }
}

@Composable
fun ErrorBanner(
    message: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        LaunchedEffect(message) {
            delay(5_000)
            onDismiss()
        }
        Surface(
            color = Color(0xEE8E2020),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, Color(0x66FFD2C6)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = message.orEmpty(),
                    color = DdzColors.Cream,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "关闭",
                    color = DdzColors.GoldSoft,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable(onClick = onDismiss)
                        .padding(start = 10.dp),
                )
            }
        }
    }
}

@Composable
fun LoadingRow(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CircularProgressIndicator(
            modifier = Modifier.width(16.dp).height(16.dp),
            color = DdzColors.Gold,
            strokeWidth = 2.dp,
        )
        Text(text, color = DdzColors.Muted, fontSize = 13.sp)
    }
}

@Composable
fun DividerLine(modifier: Modifier = Modifier, alpha: Float = 0.14f) {
    Row(modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = alpha))) {}
}

/** Standard page chrome: wood background + safe insets. */
@Composable
fun PageColumn(
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        content = content,
    )
}

/** Small chip that forces portrait/landscape and back. */
@Composable
fun OrientationToggle(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    Surface(
        color = Color(0x22FFFFFF),
        shape = RoundedCornerShape(50),
        modifier = modifier.clickable {
            context.findActivity()?.requestedOrientation = if (landscape) {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        },
    ) {
        Text(
            text = if (landscape) "竖屏" else "横屏",
            color = DdzColors.GoldSoft,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
