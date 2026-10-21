package com.ddz.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddz.android.ui.LobbyUiState
import com.ddz.android.ui.components.Avatar
import com.ddz.android.ui.components.CardBack
import com.ddz.android.ui.components.CardBackStack
import com.ddz.android.ui.components.ErrorBanner
import com.ddz.android.ui.components.FeltPanel
import com.ddz.android.ui.components.HandFan
import com.ddz.android.ui.components.OrientationToggle
import com.ddz.android.ui.components.PlayingCard
import com.ddz.android.ui.components.TagChip
import com.ddz.android.ui.components.WoodBackground
import com.ddz.android.ui.theme.DdzColors
import com.ddz.shared.ai.HandAnalyzer
import com.ddz.shared.ai.HandFinder
import com.ddz.shared.game.core.Card
import com.ddz.shared.game.core.GameState
import com.ddz.shared.game.core.Hand
import com.ddz.shared.game.core.HandParser
import com.ddz.shared.game.core.Phase
import com.ddz.shared.game.core.Player
import com.ddz.shared.game.core.Rank
import com.ddz.shared.session.SessionStatus
import kotlinx.coroutines.delay
import kotlin.math.min

@Composable
fun GameScreen(
    ui: LobbyUiState,
    onPlay: (List<Card>) -> Unit,
    onPass: () -> Unit,
    onBid: (Int) -> Unit,
    onRestart: () -> Unit,
    onLeave: () -> Unit,
    onClearError: () -> Unit,
) {
    val state = ui.game
    if (state == null) {
        WoodBackground(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DdzColors.Gold)
            }
        }
        return
    }

    val me = if (ui.me >= 0) ui.me else 0
    val myPlayer = state.players.firstOrNull { it.seat == me }
    val myHand = myPlayer?.hand.orEmpty()
    val laiziRanks = state.laiziRanks
    val isMyTurn = state.currentSeat == me &&
        (state.phase == Phase.BIDDING || state.phase == Phase.PLAYING)

    var selected by remember(state.round) { mutableStateOf(emptySet<Int>()) }
    var hintIndex by remember(state.round, state.currentSeat) { mutableIntStateOf(-1) }
    var bombFlash by remember { mutableFloatStateOf(0f) }
    var lastBombCount by remember { mutableIntStateOf(state.bombCount) }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(state.seq, state.currentSeat) {
        if (state.currentSeat != me) selected = emptySet()
    }
    LaunchedEffect(state.bombCount) {
        if (state.bombCount > lastBombCount) {
            lastBombCount = state.bombCount
            bombFlash = 1f
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(340)
            bombFlash = 0f
        } else {
            lastBombCount = state.bombCount
        }
    }

    val selectedCards = myHand.filter { selected.contains(it.code) }
    val selectedHand = if (selectedCards.isEmpty()) null else HandParser.parse(selectedCards, laiziRanks)
    val canPlay = isMyTurn && state.phase == Phase.PLAYING &&
        selectedHand != null && selectedHand.canBeat(state.lastHand)

    fun toggle(card: Card) {
        selected = if (selected.contains(card.code)) {
            selected - card.code
        } else {
            selected + card.code
        }
        hintIndex = -1
    }

    fun nextHint() {
        val last = state.lastHand
        val options: List<Hand> = if (last == null) {
            HandAnalyzer.leadCandidates(myHand, laiziRanks)
        } else {
            HandFinder.beating(myHand, last, laiziRanks)
        }
        if (options.isEmpty()) return
        hintIndex = (hintIndex + 1) % options.size
        selected = options[hintIndex].cards.map { it.code }.toSet()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = maxWidth > maxHeight
        Box(Modifier.fillMaxSize()) {
            WoodBackground(Modifier.fillMaxSize()) {
                if (landscape) {
                    LandscapeTable(
                        state = state,
                        roomName = ui.room?.name ?: "单机练习",
                        me = me,
                        myPlayer = myPlayer,
                        myHand = myHand,
                        selected = selected,
                        selectedCards = selectedCards,
                        selectedHand = selectedHand,
                        isMyTurn = isMyTurn,
                        canPlay = canPlay,
                        onToggle = ::toggle,
                        onHint = ::nextHint,
                        onBid = onBid,
                        onPass = onPass,
                        onPlay = { onPlay(selectedCards) },
                        onLeave = onLeave,
                    )
                } else {
                    PortraitTable(
                        state = state,
                        roomName = ui.room?.name ?: "单机练习",
                        me = me,
                        myPlayer = myPlayer,
                        myHand = myHand,
                        selected = selected,
                        selectedCards = selectedCards,
                        selectedHand = selectedHand,
                        isMyTurn = isMyTurn,
                        canPlay = canPlay,
                        onToggle = ::toggle,
                        onHint = ::nextHint,
                        onBid = onBid,
                        onPass = onPass,
                        onPlay = { onPlay(selectedCards) },
                        onLeave = onLeave,
                    )
                }
            }

            ErrorBanner(
                message = ui.error,
                onDismiss = onClearError,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp, start = 16.dp, end = 16.dp),
            )

            AnimatedVisibility(
                visible = ui.status == SessionStatus.RECONNECTING,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
            ) {
                Surface(color = Color(0xF0B36B1E), shape = RoundedCornerShape(50)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(13.dp),
                            color = DdzColors.Cream,
                            strokeWidth = 2.dp,
                        )
                        Text("网络断开，正在重连并恢复快照…", color = DdzColors.Cream, fontSize = 11.sp)
                    }
                }
            }

            if (bombFlash > 0f) {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.radialGradient(
                            listOf(
                                Color(0xFFFFB347).copy(alpha = 0.55f * bombFlash),
                                Color(0x33FF6A00).copy(alpha = 0.25f * bombFlash),
                                Color.Transparent,
                            ),
                        ),
                    ),
                )
            }

            val settlement = state.settlement
            if (state.phase == Phase.SETTLING && settlement != null) {
                SettlementDialog(
                    state = state,
                    isHost = ui.isHost,
                    onRestart = onRestart,
                    onLeave = onLeave,
                )
            }
        }
    }
}

@Composable
private fun PortraitTable(
    state: GameState,
    roomName: String,
    me: Int,
    myPlayer: Player?,
    myHand: List<Card>,
    selected: Set<Int>,
    selectedCards: List<Card>,
    selectedHand: Hand?,
    isMyTurn: Boolean,
    canPlay: Boolean,
    onToggle: (Card) -> Unit,
    onHint: () -> Unit,
    onBid: (Int) -> Unit,
    onPass: () -> Unit,
    onPlay: () -> Unit,
    onLeave: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        GameTopBar(state, roomName, compact = false, onLeave = onLeave)
        FeltPanel(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 4.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    OpponentSeat(
                        player = state.players.firstOrNull { it.seat == (me + 2) % 3 },
                        isTurn = state.currentSeat == (me + 2) % 3,
                        modifier = Modifier.width(114.dp),
                    )
                    OpponentSeat(
                        player = state.players.firstOrNull { it.seat == (me + 1) % 3 },
                        isTurn = state.currentSeat == (me + 1) % 3,
                        modifier = Modifier.width(114.dp),
                    )
                }

                CenterTable(
                    state = state,
                    isMyTurn = isMyTurn,
                    compact = false,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )

                MyStatusRow(state = state, me = me, myPlayer = myPlayer, selectedHand = selectedHand)
                HandFan(
                    cards = myHand,
                    selected = selected,
                    round = state.round,
                    enabled = isMyTurn && state.phase == Phase.PLAYING,
                    laiziRanks = state.laiziRanks,
                    onToggle = onToggle,
                )
                ActionBar(
                    state = state,
                    isMyTurn = isMyTurn,
                    selectedCount = selectedCards.size,
                    selectedHand = selectedHand,
                    canPlay = canPlay,
                    onBid = onBid,
                    onPass = onPass,
                    onPlay = onPlay,
                    onHint = onHint,
                )
            }
        }
    }
}

@Composable
private fun LandscapeTable(
    state: GameState,
    roomName: String,
    me: Int,
    myPlayer: Player?,
    myHand: List<Card>,
    selected: Set<Int>,
    selectedCards: List<Card>,
    selectedHand: Hand?,
    isMyTurn: Boolean,
    canPlay: Boolean,
    onToggle: (Card) -> Unit,
    onHint: () -> Unit,
    onBid: (Int) -> Unit,
    onPass: () -> Unit,
    onPlay: () -> Unit,
    onLeave: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        // Full-bleed felt makes the landscape table independent of the flexible
        // Column height, which otherwise squeezes the felt on short screens.
        FeltPanel(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().padding(6.dp)) {
            GameTopBar(
                state = state,
                roomName = roomName,
                compact = true,
                selfCardCount = myHand.size,
                onLeave = onLeave,
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Row(
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    OpponentSeat(
                        player = state.players.firstOrNull { it.seat == (me + 2) % 3 },
                        isTurn = state.currentSeat == (me + 2) % 3,
                        compact = true,
                        modifier = Modifier.width(154.dp),
                    )
                    OpponentSeat(
                        player = state.players.firstOrNull { it.seat == (me + 1) % 3 },
                        isTurn = state.currentSeat == (me + 1) % 3,
                        compact = true,
                        modifier = Modifier.width(154.dp),
                    )
                }
                CenterTable(
                    state = state,
                    isMyTurn = isMyTurn,
                    compact = true,
                    modifier = Modifier.fillMaxSize().padding(top = 30.dp),
                )
            }
            HandFan(
                cards = myHand,
                selected = selected,
                round = state.round,
                enabled = isMyTurn && state.phase == Phase.PLAYING,
                maxCardWidth = 32.dp,
                laiziRanks = state.laiziRanks,
                onToggle = onToggle,
            )
            ActionBar(
                state = state,
                isMyTurn = isMyTurn,
                selectedCount = selectedCards.size,
                selectedHand = selectedHand,
                canPlay = canPlay,
                onBid = onBid,
                onPass = onPass,
                onPlay = onPlay,
                onHint = onHint,
                compact = true,
            )
        }
    }
}

@Composable
private fun GameTopBar(
    state: GameState,
    roomName: String,
    compact: Boolean,
    selfCardCount: Int? = null,
    onLeave: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = if (compact) 1.dp else 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "退出",
            color = DdzColors.GoldSoft,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clickable(onClick = onLeave)
                .background(Color(0x22FFFFFF), RoundedCornerShape(50))
                .padding(horizontal = 9.dp, vertical = if (compact) 4.dp else 6.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = roomName,
                color = DdzColors.Cream,
                fontSize = if (compact) 12.sp else 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = "第 ${state.round} 局 · ${state.phase.label()}" +
                    if (compact && selfCardCount != null) {
                        " · 剩 $selfCardCount 张"
                    } else if (state.isLaiziMode && !compact) {
                        " · 天${state.laizi?.tianRank?.label ?: "?"}" +
                            "/地${state.laizi?.diRank?.label ?: "?"}"
                    } else {
                        ""
                    },
                color = DdzColors.Muted,
                fontSize = 9.sp,
                maxLines = 1,
            )
        }
        if (state.isLaiziMode) {
            TagChip("天地癞子", color = DdzColors.Warning)
            Spacer(Modifier.width(4.dp))
        }
        TagChip("倍 ×${state.multiplier}", color = DdzColors.Gold, filled = true)
        Spacer(Modifier.width(4.dp))
        OrientationToggle()
    }
}

@Composable
private fun CenterTable(
    state: GameState,
    isMyTurn: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val laiziRanks = state.laiziRanks
    val cardWidth = if (compact) 20.dp else 30.dp
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (compact) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BottomCardsRow(state = state, cardWidth = cardWidth, laiziRanks = laiziRanks)
                if (state.isLaiziMode) LaiziChips(state)
            }
        } else {
            Spacer(Modifier.height(2.dp))
            Text("底牌", color = DdzColors.GoldSoft.copy(alpha = 0.8f), fontSize = 9.sp)
            BottomCardsRow(state = state, cardWidth = cardWidth, laiziRanks = laiziRanks)
            if (state.isLaiziMode) {
                Spacer(Modifier.height(4.dp))
                LaiziChips(state)
            }
        }

        Spacer(Modifier.height(if (compact) 2.dp else 10.dp))
        val lastHand = state.lastHand
        if (lastHand != null) {
            val owner = state.players.firstOrNull { it.seat == state.lastHandSeat }
            Text(
                text = "${owner?.name ?: "上家"} · ${lastHand.type.label}",
                color = DdzColors.Cream.copy(alpha = 0.85f),
                fontSize = if (compact) 8.sp else 10.sp,
            )
            Spacer(Modifier.height(if (compact) 1.dp else 3.dp))
            MiniHand(cards = lastHand.cards, maxCardWidth = cardWidth, laiziRanks = laiziRanks)
        } else if (state.phase == Phase.PLAYING) {
            Text(
                text = if (state.passStreak > 0) "两家不要，重新领出" else "自由领出",
                color = DdzColors.Cream.copy(alpha = if (compact) 0.8f else 0.6f),
                fontSize = if (compact) 10.sp else 11.sp,
            )
        }

        if (!compact) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "斗",
                    color = DdzColors.Gold.copy(alpha = 0.06f),
                    fontSize = 120.sp,
                    fontWeight = FontWeight.Black,
                )
            }
            Text(
                text = when {
                    state.phase == Phase.BIDDING -> if (isMyTurn) "轮到你叫分" else "叫分中…"
                    state.phase == Phase.PLAYING -> if (isMyTurn) "轮到你出牌" else "等待出牌…"
                    state.phase == Phase.SETTLING -> "本局结束"
                    else -> ""
                },
                color = if (isMyTurn) DdzColors.GoldSoft else DdzColors.Cream.copy(alpha = 0.75f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = state.log.lastOrNull().orEmpty(),
                color = DdzColors.Cream.copy(alpha = 0.5f),
                fontSize = 9.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun BottomCardsRow(
    state: GameState,
    cardWidth: Dp,
    laiziRanks: Set<Rank>,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        if (state.bottomVisible && state.bottomCards.isNotEmpty()) {
            state.bottomCards.forEach { card ->
                PlayingCard(
                    card = card,
                    width = cardWidth,
                    isLaizi = card.rank in laiziRanks,
                )
            }
        } else {
            repeat(3) { CardBack(width = cardWidth) }
        }
    }
}

@Composable
private fun LaiziChips(state: GameState) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TagChip(
            "天癞 ${state.laizi?.tianRank?.label ?: "?"}",
            color = DdzColors.Gold,
        )
        val di = state.laizi?.diRank
        TagChip(
            "地癞 ${di?.label ?: "待定"}",
            color = if (di != null) DdzColors.Warning else DdzColors.Muted,
        )
    }
}

@Composable
private fun OpponentSeat(
    player: Player?,
    isTurn: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    if (player == null) {
        Spacer(modifier)
        return
    }
    Surface(
        color = if (isTurn) Color(0x332A5B40) else Color(0x1A000000),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (isTurn) DdzColors.Gold.copy(alpha = 0.7f) else Color(0x22FFFFFF)),
        modifier = modifier,
    ) {
        if (compact) {
            Row(
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(seed = player.avatarSeed, size = 27.dp, isLandlord = player.isLandlord)
                Spacer(Modifier.width(5.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = player.name,
                        color = DdzColors.Cream,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (player.isLandlord) TagChip("地主", color = DdzColors.Gold)
                        if (!player.online) TagChip("托管", color = DdzColors.Warning)
                        if (isTurn) Text("…", color = DdzColors.GoldSoft, fontSize = 9.sp)
                    }
                }
                CardBackStack(count = player.cardCount, width = 11.dp, showCount = false)
                Text(
                    text = "×${player.cardCount}",
                    color = DdzColors.Cream,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        } else {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(seed = player.avatarSeed, size = 34.dp, isLandlord = player.isLandlord)
                    Spacer(Modifier.width(6.dp))
                    Column(Modifier.weight(1f, fill = false)) {
                        Text(
                            text = player.name,
                            color = DdzColors.Cream,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(3.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            if (player.isLandlord) TagChip("地主", color = DdzColors.Gold)
                            if (!player.online) TagChip("托管", color = DdzColors.Warning)
                        }
                    }
                }
                Spacer(Modifier.height(7.dp))
                CardBackStack(count = player.cardCount, width = 17.dp)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when {
                        isTurn -> "思考中…"
                        player.bid == 0 -> "不叫"
                        player.bid != null -> "叫 ${player.bid} 分"
                        !player.online -> "AI 托管中"
                        else -> " "
                    },
                    color = if (isTurn) DdzColors.GoldSoft else DdzColors.Muted,
                    fontSize = 9.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun MyStatusRow(
    state: GameState,
    me: Int,
    myPlayer: Player?,
    selectedHand: Hand?,
    compact: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = if (compact) 1.dp else 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            seed = myPlayer?.avatarSeed ?: 1,
            size = if (compact) 24.dp else 30.dp,
            isLandlord = myPlayer?.isLandlord == true,
        )
        Spacer(Modifier.width(7.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "${myPlayer?.name ?: "我"} · 剩 ${myPlayer?.cardCount ?: 0} 张",
                color = DdzColors.Cream,
                fontSize = if (compact) 11.sp else 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (myPlayer?.isLandlord == true) "地主" else "农民",
                color = DdzColors.Muted,
                fontSize = 9.sp,
            )
        }
        if (state.phase == Phase.PLAYING && state.currentSeat == me) {
            Text(
                text = selectedHand?.let { "${it.type.label} · ${it.cards.size} 张" } ?: "点击手牌选择",
                color = if (selectedHand != null) DdzColors.GoldSoft else DdzColors.Muted,
                fontSize = if (compact) 10.sp else 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ActionBar(
    state: GameState,
    isMyTurn: Boolean,
    selectedCount: Int,
    selectedHand: Hand?,
    canPlay: Boolean,
    onBid: (Int) -> Unit,
    onPass: () -> Unit,
    onPlay: () -> Unit,
    onHint: () -> Unit,
    compact: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (state.phase) {
            Phase.BIDDING -> {
                if (isMyTurn) {
                    ActionButton("不叫", Modifier.weight(1f), compact = compact) { onBid(0) }
                    for (score in 1..3) {
                        ActionButton(
                            text = "${score}分",
                            modifier = Modifier.weight(1f),
                            enabled = score > state.highestBid,
                            primary = score == 3,
                            compact = compact,
                        ) { onBid(score) }
                    }
                } else {
                    WaitingLabel(state, compact)
                }
            }

            Phase.PLAYING -> {
                if (isMyTurn) {
                    ActionButton("提示", Modifier.weight(1f), compact = compact) { onHint() }
                    ActionButton(
                        text = "不要",
                        modifier = Modifier.weight(1f),
                        enabled = state.lastHand != null,
                        compact = compact,
                    ) { onPass() }
                    ActionButton(
                        text = if (selectedCount > 0) "出牌 $selectedCount" else "出牌",
                        modifier = Modifier.weight(1.45f),
                        enabled = canPlay,
                        primary = true,
                        compact = compact,
                    ) { onPlay() }
                } else {
                    WaitingLabel(state, compact)
                }
            }

            Phase.SETTLING -> {
                Text(
                    text = "本局结束，等待结算…",
                    color = DdzColors.Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }

            else -> WaitingLabel(state, compact)
        }
    }
    if (isMyTurn && state.phase == Phase.PLAYING && selectedCount > 0 && selectedHand == null) {
        Text(
            text = "这手牌不是合法牌型",
            color = DdzColors.Warning,
            fontSize = 10.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    } else if (isMyTurn && state.phase == Phase.PLAYING && selectedHand != null &&
        !selectedHand.canBeat(state.lastHand)
    ) {
        Text(
            text = "管不上上家的牌",
            color = DdzColors.Warning,
            fontSize = 10.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun WaitingLabel(state: GameState, compact: Boolean) {
    val actor = state.players.firstOrNull { it.seat == state.currentSeat }
    val text = when (state.phase) {
        Phase.BIDDING -> "等待 ${actor?.name ?: ""} 叫分…"
        Phase.PLAYING -> "等待 ${actor?.name ?: ""} 出牌…"
        else -> ""
    }
    Surface(
        color = Color(0x1A000000),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 14.dp,
                vertical = if (compact) 8.dp else 11.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = DdzColors.Gold,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(8.dp))
            Text(text, color = DdzColors.Cream.copy(alpha = 0.8f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun ActionButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    compact: Boolean = false,
    onClick: () -> Unit,
) {
    val height = if (compact) 36.dp else 46.dp
    val fontSize = if (compact) 12.sp else 13.sp
    if (compact) {
        Surface(
            color = when {
                primary && enabled -> DdzColors.Gold
                primary -> Color(0xFF4A4034)
                else -> Color(0x1A000000)
            },
            shape = RoundedCornerShape(12.dp),
            border = if (primary) null else BorderStroke(
                1.dp,
                if (enabled) DdzColors.Gold.copy(alpha = 0.7f) else Color(0xFF4A4034),
            ),
            modifier = modifier
                .height(height)
                .clickable(enabled = enabled, onClick = onClick),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = text,
                    color = when {
                        primary && enabled -> DdzColors.Ink
                        enabled -> DdzColors.Cream
                        else -> Color(0xFF8E8478)
                    },
                    fontSize = fontSize,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
        return
    }
    if (primary) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.height(height),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = DdzColors.Gold,
                contentColor = DdzColors.Ink,
                disabledContainerColor = Color(0xFF4A4034),
                disabledContentColor = Color(0xFF8E8478),
            ),
            contentPadding = PaddingValues(horizontal = 6.dp),
        ) {
            Text(text, fontSize = fontSize, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.height(height),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(
                1.dp,
                if (enabled) DdzColors.Gold.copy(alpha = 0.7f) else Color(0xFF4A4034),
            ),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = if (enabled) DdzColors.Cream else Color(0xFF8E8478),
            ),
            contentPadding = PaddingValues(horizontal = 6.dp),
        ) {
            Text(text, fontSize = fontSize, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun MiniHand(
    cards: List<Card>,
    maxCardWidth: Dp,
    laiziRanks: Set<Rank> = emptySet(),
) {
    if (cards.isEmpty()) return
    BoxWithConstraints {
        val n = cards.size
        val available = maxWidth
        val width = if (n == 1) {
            maxCardWidth
        } else {
            ((available - 4.dp) / (1f + (n - 1) * 0.45f)).coerceIn(16.dp, maxCardWidth)
        }
        val spacing = if (n == 1) 0.dp else minOf(width * 0.45f, (available - width) / (n - 1))
        Box(
            modifier = Modifier
                .width(width + spacing * (n - 1))
                .height(width * 1.45f),
        ) {
            cards.forEachIndexed { index, card ->
                PlayingCard(
                    card = card,
                    width = width,
                    isLaizi = card.rank in laiziRanks,
                    modifier = Modifier.offset(x = spacing * index),
                )
            }
        }
    }
}

private fun Phase.label(): String = when (this) {
    Phase.WAITING -> "等待"
    Phase.DEALING -> "发牌"
    Phase.BIDDING -> "叫分"
    Phase.PLAYING -> "出牌"
    Phase.SETTLING -> "结算"
}
