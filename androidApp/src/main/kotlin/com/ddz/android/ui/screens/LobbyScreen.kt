package com.ddz.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddz.android.ui.LobbyUiState
import com.ddz.android.ui.components.Avatar
import com.ddz.android.ui.components.BackHeader
import com.ddz.android.ui.components.ErrorBanner
import com.ddz.android.ui.components.LoadingRow
import com.ddz.android.ui.components.PrimaryButton
import com.ddz.android.ui.components.TagChip
import com.ddz.android.ui.components.WoodBackground
import com.ddz.android.ui.theme.DdzColors
import com.ddz.shared.game.core.Player
import com.ddz.shared.session.SessionStatus

@Composable
fun LobbyScreen(
    ui: LobbyUiState,
    onStart: () -> Unit,
    onLeave: () -> Unit,
    onKick: (Int) -> Unit,
    onClearError: () -> Unit,
) {
    val players = ui.players
    val seated = players.count { it.isSeated }
    WoodBackground(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp),
        ) {
            BackHeader(title = "房间 · 等待中", onBack = onLeave)

            RoomHeader(ui)

            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("入座玩家", color = DdzColors.Cream, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                TagChip("$seated / 3", color = DdzColors.Gold)
            }
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for (seat in 0..2) {
                    SeatSlot(
                        player = players.firstOrNull { it.seat == seat },
                        seat = seat,
                        me = ui.me,
                        isHost = ui.isHost,
                        onKick = onKick,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(18.dp))
            StatusPanel(ui, seated)

            Spacer(Modifier.weight(1f))
            when {
                ui.status == SessionStatus.RECONNECTING -> LoadingRow("连接断开，正在重连…")
                ui.status == SessionStatus.CONNECTING -> LoadingRow("正在连接房主…")
                ui.status == SessionStatus.ERROR -> Text(
                    ui.error ?: "连接失败",
                    color = DdzColors.Warning,
                    fontSize = 12.sp,
                )
                ui.isHost -> {
                    PrimaryButton(
                        text = if (seated >= 3) "开始游戏" else "还差 ${3 - seated} 位玩家",
                        onClick = onStart,
                        enabled = seated >= 3,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                else -> {
                    Surface(
                        color = Color(0x221E5B3A),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            LoadingRow("等待房主开始游戏…")
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
            Text(
                text = "房主无需服务器，牌局由房主设备权威计算并同步快照。",
                color = DdzColors.Muted.copy(alpha = 0.7f),
                fontSize = 10.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
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
private fun RoomHeader(ui: LobbyUiState) {
    val room = ui.room
    Surface(
        color = Color(0x2AFFFFFF),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, DdzColors.Gold.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = room?.name ?: "正在连接…",
                    color = DdzColors.Cream,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TagChip(
                    text = if (room?.mode == "tian_di_laizi") "天地癞子" else "经典",
                    color = if (room?.mode == "tian_di_laizi") DdzColors.Warning else DdzColors.Muted,
                )
                Spacer(Modifier.width(6.dp))
                if (ui.isHost) TagChip("你是房主", color = DdzColors.Gold, filled = true)
            }
            Spacer(Modifier.height(8.dp))
            val address = room?.let { "${it.hostIp}:${it.port}" }
                ?: ui.pendingAddress.ifBlank { "—" }
            Text(
                text = address,
                color = DdzColors.GoldSoft,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (ui.isHost) {
                    "朋友连同一 Wi-Fi，在「加入房间」里选择此房间；搜不到就手动输入上面的地址。"
                } else {
                    "已连接房主 ${room?.hostIp ?: ""}，等待牌局开始。"
                },
                color = DdzColors.Muted,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun SeatSlot(
    player: Player?,
    seat: Int,
    me: Int,
    isHost: Boolean,
    onKick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val occupied = player?.isSeated == true
    val isMe = seat == me
    Surface(
        color = if (isMe) Color(0x332A5B40) else Color(0x22FFFFFF),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            if (isMe) DdzColors.Gold.copy(alpha = 0.6f) else Color(0x22FFFFFF),
        ),
        modifier = modifier.height(168.dp),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(contentAlignment = Alignment.BottomEnd) {
                if (occupied) {
                    Avatar(seed = player.avatarSeed, size = 46.dp)
                } else {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .padding(2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            color = Color(0x1AFFFFFF),
                            shape = CircleShape,
                            border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                            modifier = Modifier.size(44.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("+", color = DdzColors.Muted, fontSize = 20.sp)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (occupied) player.name.ifBlank { "玩家" } else "空位",
                color = if (occupied) DdzColors.Cream else DdzColors.Muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = when {
                    !occupied -> "等待加入"
                    !player.online -> "掉线 · AI 托管"
                    isMe -> "你"
                    seat == 0 -> "房主"
                    else -> "玩家"
                },
                color = if (occupied && player.online) DdzColors.Muted else DdzColors.Warning,
                fontSize = 10.sp,
                maxLines = 1,
            )
            Spacer(Modifier.height(6.dp))
            if (isHost && occupied && seat != 0) {
                Text(
                    text = "移出",
                    color = DdzColors.Red.copy(alpha = 0.9f),
                    fontSize = 10.sp,
                    modifier = Modifier
                        .clickable { onKick(seat) }
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusPanel(ui: LobbyUiState, seated: Int) {
    Surface(
        color = Color(0x1AFFFFFF),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .background(
                        if (ui.status == SessionStatus.CONNECTED) DdzColors.Success else DdzColors.Warning,
                        CircleShape,
                    ),
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = when {
                        ui.status == SessionStatus.RECONNECTING -> "连接中断，正在自动重连"
                        ui.status == SessionStatus.CONNECTING -> "正在连接房主设备"
                        ui.status == SessionStatus.ERROR -> "连接失败"
                        seated >= 3 -> "三位玩家已就位，等待房主开始"
                        else -> "等待玩家加入（$seated/3）"
                    },
                    color = DdzColors.Cream,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "房间会保留座位，掉线后 10 秒内可自动重连恢复牌局",
                    color = DdzColors.Muted,
                    fontSize = 10.sp,
                )
            }
        }
    }
}
