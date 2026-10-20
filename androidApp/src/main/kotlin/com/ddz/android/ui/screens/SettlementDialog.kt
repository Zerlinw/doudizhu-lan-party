package com.ddz.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ddz.android.ui.components.Avatar
import com.ddz.android.ui.components.DividerLine
import com.ddz.android.ui.components.GoldOutlineButton
import com.ddz.android.ui.components.PrimaryButton
import com.ddz.android.ui.components.TagChip
import com.ddz.android.ui.theme.DdzColors
import com.ddz.shared.game.core.GameState

@Composable
fun SettlementDialog(
    state: GameState,
    isHost: Boolean,
    onRestart: () -> Unit,
    onLeave: () -> Unit,
) {
    val settlement = state.settlement ?: return
    Dialog(onDismissRequest = {}) {
        Surface(
            color = DdzColors.WoodMid,
            shape = RoundedCornerShape(26.dp),
            border = BorderStroke(1.dp, DdzColors.Gold.copy(alpha = 0.55f)),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (settlement.landlordWin) "地主胜利" else "农民胜利",
                    color = DdzColors.GoldSoft,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 3.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = settlement.reason,
                    color = DdzColors.Muted,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TagChip("倍数 ×${settlement.multiplier}", color = DdzColors.Gold, filled = true)
                    TagChip("底分 ${settlement.baseBid}", color = DdzColors.Cream)
                    if (settlement.spring) TagChip("春天", color = DdzColors.Warning)
                    if (settlement.antiSpring) TagChip("反春天", color = DdzColors.Warning)
                }

                Spacer(Modifier.height(12.dp))
                DividerLine()
                Spacer(Modifier.height(6.dp))

                state.players.sortedBy { it.seat }.forEach { player ->
                    val delta = settlement.deltas.getOrElse(player.seat) { 0 }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(
                            seed = player.avatarSeed,
                            size = 30.dp,
                            isLandlord = player.isLandlord,
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = player.name.ifBlank { "座位 ${player.seat + 1}" },
                                color = DdzColors.Cream,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = if (player.isLandlord) "地主" else "农民",
                                color = DdzColors.Muted,
                                fontSize = 10.sp,
                            )
                        }
                        Text(
                            text = if (delta > 0) "+$delta" else "$delta",
                            color = if (delta > 0) DdzColors.Success else DdzColors.Red,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))
                DividerLine()
                Text(
                    text = "地主输赢双倍，农民单倍；炸弹 / 王炸 / 春天各自翻倍",
                    color = DdzColors.Muted.copy(alpha = 0.75f),
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )

                Spacer(Modifier.height(14.dp))
                if (isHost) {
                    PrimaryButton(text = "再来一局", onClick = onRestart)
                } else {
                    Text(
                        text = "等待房主发起下一局…",
                        color = DdzColors.Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 14.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                GoldOutlineButton(
                    text = "退出房间",
                    onClick = onLeave,
                    height = 44,
                )
            }
        }
    }
}
