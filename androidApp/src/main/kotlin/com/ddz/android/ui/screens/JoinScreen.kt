package com.ddz.android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddz.android.ui.LobbyUiState
import com.ddz.android.ui.components.Avatar
import com.ddz.android.ui.components.BackHeader
import com.ddz.android.ui.components.ErrorBanner
import com.ddz.android.ui.components.GoldOutlineButton
import com.ddz.android.ui.components.LoadingRow
import com.ddz.android.ui.components.TagChip
import com.ddz.android.ui.components.WoodBackground
import com.ddz.android.ui.theme.DdzColors
import com.ddz.shared.discovery.DiscoveredRoom

@Composable
fun JoinScreen(
    ui: LobbyUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onJoin: (DiscoveredRoom) -> Unit,
    onManualJoin: (String) -> Unit,
    onClearError: () -> Unit,
) {
    var manual by remember { mutableStateOf("") }
    WoodBackground(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp),
        ) {
            BackHeader(title = "加入房间", onBack = onBack)
            Text(
                text = "以「${ui.playerName}」的身份加入",
                color = DdzColors.Muted,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(12.dp))

            Surface(
                color = Color(0x2AFFFFFF),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, DdzColors.Gold.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("手动连接（自动发现失败时使用）", color = DdzColors.GoldSoft, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = manual,
                            onValueChange = { manual = it.take(40) },
                            singleLine = true,
                            placeholder = { Text("192.168.1.8:9527", color = DdzColors.Muted) },
                            modifier = Modifier.weight(1f),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = DdzColors.Cream,
                                fontSize = 14.sp,
                            ),
                            colors = fieldColors(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { onManualJoin(manual) }),
                        )
                        Spacer(Modifier.width(10.dp))
                        Button(
                            onClick = { onManualJoin(manual) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DdzColors.Gold,
                                contentColor = DdzColors.Ink,
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Text("连接", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("附近的房间", color = DdzColors.Cream, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "重新扫描",
                    color = DdzColors.GoldSoft,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .background(Color(0x22FFFFFF), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
            Spacer(Modifier.height(8.dp))

            if (ui.rooms.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📡", fontSize = 42.sp)
                        Spacer(Modifier.height(10.dp))
                        if (ui.scanning) {
                            LoadingRow("正在扫描 _doudizhu._tcp …")
                        } else {
                            Text("还没有发现房间", color = DdzColors.Muted, fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "请确认房主已创建房间，并连接同一 Wi-Fi",
                            color = DdzColors.Muted.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                ) {
                    items(ui.rooms, key = { "${it.room.hostIp}:${it.room.port}:${it.room.name}" }) { found ->
                        RoomCard(found = found, onJoin = { onJoin(found) })
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "提示：部分手机会限制局域网发现；手动输入 IP 一定可用。",
                color = DdzColors.Muted.copy(alpha = 0.7f),
                fontSize = 10.sp,
            )
            Spacer(Modifier.height(8.dp))
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
private fun RoomCard(found: DiscoveredRoom, onJoin: () -> Unit) {
    val room = found.room
    Surface(
        color = Color(0x2AFFFFFF),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, DdzColors.Gold.copy(alpha = 0.22f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(seed = room.name.hashCode(), size = 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(room.name, color = DdzColors.Cream, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(2.dp))
                Text("${room.hostIp}:${room.port}", color = DdzColors.Muted, fontSize = 11.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TagChip("${room.playerCount}/3 人", color = DdzColors.Success)
                    TagChip(
                        text = if (room.mode == "tian_di_laizi") "天地癞子" else "经典",
                        color = if (room.mode == "tian_di_laizi") DdzColors.Warning else DdzColors.Muted,
                    )
                    if (room.needsPassword) TagChip("需密码", color = DdzColors.Warning)
                    if (room.state == "playing") TagChip("进行中", color = DdzColors.Warning)
                }
            }
            Button(
                onClick = onJoin,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DdzColors.Gold,
                    contentColor = DdzColors.Ink,
                ),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                modifier = Modifier.height(40.dp),
            ) {
                Text("加入", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}
