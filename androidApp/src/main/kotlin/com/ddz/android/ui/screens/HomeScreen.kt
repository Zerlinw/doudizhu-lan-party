package com.ddz.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddz.android.ui.LobbyUiState
import com.ddz.android.ui.components.ErrorBanner
import com.ddz.android.ui.components.GoldOutlineButton
import com.ddz.android.ui.components.PlayingCard
import com.ddz.android.ui.components.PrimaryButton
import com.ddz.android.ui.components.WoodBackground
import com.ddz.android.ui.theme.DdzColors
import com.ddz.shared.game.core.Card
import com.ddz.shared.game.core.Rank
import com.ddz.shared.game.core.Suit

@Composable
fun HomeScreen(
    ui: LobbyUiState,
    onNameChange: (String) -> Unit,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    onPractice: () -> Unit,
    onClearError: () -> Unit,
) {
    WoodBackground(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(18.dp))
            HeroCards()
            Text(
                text = "斗地主",
                color = DdzColors.GoldSoft,
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 6.sp,
            )
            Text(
                text = "局域网 · 零服务器 · 三台手机即开一桌",
                color = DdzColors.Muted,
                fontSize = 12.sp,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(22.dp))

            Surface(
                color = Color(0x33211812),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("你的昵称", color = DdzColors.Muted, fontSize = 11.sp)
                    OutlinedTextField(
                        value = ui.playerName,
                        onValueChange = onNameChange,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = DdzColors.Cream,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        placeholder = { Text("输入昵称", color = DdzColors.Muted) },
                        colors = fieldColors(),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            PrimaryButton(
                text = if (ui.busy) "正在创建…" else "创建房间",
                onClick = onCreate,
                enabled = !ui.busy,
            )
            Spacer(Modifier.height(10.dp))
            GoldOutlineButton(text = "加入房间", onClick = onJoin)
            Spacer(Modifier.height(10.dp))
            GoldOutlineButton(text = "单机练习（3 人 AI 同桌）", onClick = onPractice)

            Spacer(Modifier.height(22.dp))
            LocalAddressCard(ui)

            Spacer(Modifier.height(18.dp))
            Text(
                text = "Kotlin Multiplatform · 权威 Host + TCP 快照同步",
                color = DdzColors.Muted.copy(alpha = 0.65f),
                fontSize = 10.sp,
            )
            Spacer(Modifier.height(24.dp))
        }

        ErrorBanner(
            message = ui.error,
            onDismiss = onClearError,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
        )
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = DdzColors.Gold,
    unfocusedBorderColor = Color(0x44FFFFFF),
    focusedLabelColor = DdzColors.Gold,
    unfocusedLabelColor = DdzColors.Muted,
    cursorColor = DdzColors.Gold,
    focusedTextColor = DdzColors.Cream,
    unfocusedTextColor = DdzColors.Cream,
)

@Composable
private fun HeroCards() {
    Box(Modifier.fillMaxWidth().height(196.dp)) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(170.dp)
                .background(
                    Brush.radialGradient(
                        listOf(DdzColors.Gold.copy(alpha = 0.20f), Color.Transparent),
                    ),
                    CircleShape,
                ),
        )
        PlayingCard(
            card = Card(Rank.ACE, Suit.SPADE),
            width = 86.dp,
            modifier = Modifier.align(Alignment.Center).offset(x = (-56).dp).rotate(-13f),
        )
        PlayingCard(
            card = Card(Rank.KING, Suit.HEART),
            width = 96.dp,
            modifier = Modifier.align(Alignment.Center).offset(y = (-8).dp),
        )
        PlayingCard(
            card = Card(Rank.TWO, Suit.DIAMOND),
            width = 86.dp,
            modifier = Modifier.align(Alignment.Center).offset(x = 56.dp).rotate(13f),
        )
    }
}

@Composable
private fun LocalAddressCard(ui: LobbyUiState) {
    Surface(
        color = Color(0x221E5B3A),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("同一 Wi-Fi 提示", color = DdzColors.Success, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            val ips = ui.localIps
            if (ips.isEmpty()) {
                Text("未获取到局域网 IP，请确认已连接 Wi-Fi", color = DdzColors.Muted, fontSize = 12.sp)
            } else {
                Text(
                    text = "本机 IP：${ips.joinToString("  /  ")}",
                    color = DdzColors.Cream,
                    fontSize = 12.sp,
                )
            }
            Text(
                text = "朋友连不上？在「加入房间」里手动输入房主显示的 IP:端口。",
                color = DdzColors.Muted,
                fontSize = 11.sp,
            )
        }
    }
}
