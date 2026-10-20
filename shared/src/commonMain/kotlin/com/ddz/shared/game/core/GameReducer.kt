package com.ddz.shared.game.core

/**
 * The single legal transition gateway for [GameState].
 *
 * `reduce` is a pure function: it validates, applies, and bumps `seq` by one.
 * Invalid actions raise [IllegalActionException]; hosts catch it and answer with
 * a protocol ERROR instead of mutating state.
 */
object GameRules {

    /** Human-readable validation result, or null when the action is legal. */
    fun validate(state: GameState, action: GameAction): String? = try {
        requireLegal(state, action)
        null
    } catch (e: IllegalActionException) {
        e.message
    }

    /** Apply [action] or throw [IllegalActionException]. */
    fun reduce(state: GameState, action: GameAction): GameState {
        requireLegal(state, action)
        val next = apply(state, action)
        return next.copy(seq = state.seq + 1)
    }

    private fun requireLegal(state: GameState, action: GameAction) {
        when (action) {
            is GameAction.JoinSeat -> {
                checkPhase(state, Phase.WAITING, "加入")
                checkSeat(action.seat)
                val name = action.name.trim()
                if (name.isEmpty()) throw IllegalActionException("BAD_NAME", "昵称不能为空")
                if (state.player(action.seat).isSeated) {
                    throw IllegalActionException("SEAT_TAKEN", "座位 ${action.seat + 1} 已有人")
                }
                if (state.players.any { it.seat != action.seat && it.name.equals(name, ignoreCase = true) }) {
                    throw IllegalActionException("NAME_TAKEN", "昵称「$name」已被使用")
                }
            }

            is GameAction.LeaveSeat -> {
                checkPhase(state, Phase.WAITING, "离开")
                checkSeat(action.seat)
                if (!state.player(action.seat).isSeated) {
                    throw IllegalActionException("EMPTY_SEAT", "座位 ${action.seat + 1} 是空的")
                }
            }

            is GameAction.SetOnline -> {
                checkSeat(action.seat)
                if (!state.player(action.seat).isSeated) {
                    throw IllegalActionException("EMPTY_SEAT", "座位 ${action.seat + 1} 是空的")
                }
            }

            is GameAction.Deal -> {
                checkPhase(state, Phase.WAITING, "发牌")
                if (!state.allSeated) {
                    throw IllegalActionException("NOT_ENOUGH_PLAYERS", "需要 3 位玩家才能开始")
                }
            }

            is GameAction.Bid -> {
                checkPhase(state, Phase.BIDDING, "叫分")
                checkTurn(state, action.seat)
                if (action.score !in 0..3) {
                    throw IllegalActionException("BAD_BID", "叫分只能是 0/1/2/3")
                }
                if (action.score > 0 && action.score <= state.highestBid) {
                    throw IllegalActionException("BAD_BID", "叫分必须高于当前 ${state.highestBid} 分")
                }
            }

            is GameAction.Play -> {
                checkPhase(state, Phase.PLAYING, "出牌")
                checkTurn(state, action.seat)
                if (action.cards.isEmpty()) {
                    throw IllegalActionException("BAD_CARDS", "没有选择牌")
                }
                if (action.cards.distinctBy { it.code }.size != action.cards.size) {
                    throw IllegalActionException("BAD_CARDS", "选择的牌有重复")
                }
                val hand = state.player(action.seat).hand
                if (!hand.containsAll(action.cards)) {
                    throw IllegalActionException("BAD_CARDS", "手牌中没有这些牌")
                }
                val parsed = HandParser.parse(action.cards)
                    ?: throw IllegalActionException("BAD_CARDS", "这不是合法的牌型")
                if (!parsed.canBeat(state.lastHand)) {
                    throw IllegalActionException("CANNOT_BEAT", "管不上上家的牌")
                }
            }

            is GameAction.Pass -> {
                checkPhase(state, Phase.PLAYING, "不出")
                checkTurn(state, action.seat)
                if (state.lastHand == null) {
                    throw IllegalActionException("PASS_NOT_ALLOWED", "你是本轮先手，必须出牌")
                }
            }

            is GameAction.Timeout -> {
                if (state.phase != Phase.BIDDING && state.phase != Phase.PLAYING) {
                    throw IllegalActionException("WRONG_PHASE", "当前阶段不能超时托管")
                }
                checkTurn(state, action.seat)
            }

            GameAction.Restart -> checkPhase(state, Phase.SETTLING, "重新开始")
        }
    }

    private fun apply(state: GameState, action: GameAction): GameState = when (action) {
        is GameAction.JoinSeat -> applyJoin(state, action)
        is GameAction.LeaveSeat -> applyLeave(state, action)
        is GameAction.SetOnline -> state.withPlayer(action.seat) { it.copy(online = action.online) }
            .log("${state.player(action.seat).name} ${if (action.online) "已上线" else "掉线，AI 托管"}")
        is GameAction.Deal -> applyDeal(state, action.seed)
        is GameAction.Bid -> applyBid(state, action.seat, action.score)
        is GameAction.Play -> applyPlay(state, action.seat, action.cards)
        is GameAction.Pass -> applyPass(state, action.seat)
        is GameAction.Timeout -> applyTimeout(state, action.seat)
        GameAction.Restart -> applyRestart(state)
    }

    private fun applyJoin(state: GameState, action: GameAction.JoinSeat): GameState {
        val updated = state.withPlayer(action.seat) {
            it.copy(
                name = action.name.trim(),
                avatarSeed = action.avatarSeed,
                isAi = action.isAi,
                online = true,
                bid = null,
            )
        }
        return updated.log("${action.name.trim()} 加入了房间")
    }

    private fun applyLeave(state: GameState, action: GameAction.LeaveSeat): GameState {
        val player = state.player(action.seat)
        val updated = state.withPlayer(action.seat) { Player(seat = it.seat) }
        return updated.log("${player.name} 离开了房间")
    }

    private fun applyDeal(state: GameState, seed: Long): GameState {
        val dealt = dealCards(seed)
        val round = state.round + 1
        val players = state.players.map { player ->
            player.copy(
                hand = dealt.hands[player.seat],
                cardCount = dealt.hands[player.seat].size,
                isLandlord = false,
                bid = null,
            )
        }
        return state.copy(
            phase = Phase.BIDDING,
            round = round,
            players = players,
            bottomCards = dealt.bottomCards,
            landlordSeat = null,
            currentSeat = round % 3,
            lastHand = null,
            lastHandSeat = null,
            passStreak = 0,
            multiplier = 1,
            highestBid = 0,
            highestBidder = null,
            bidsMade = 0,
            bombCount = 0,
            playCounts = listOf(0, 0, 0),
            winnerSeat = null,
            settlement = null,
            seed = seed,
            log = (state.log + "第 $round 局开始 · 发牌完成").takeLast(60),
        )
    }

    private fun applyBid(state: GameState, seat: Int, score: Int): GameState {
        val actor = state.player(seat)
        var next = state
            .withPlayer(seat) { it.copy(bid = score) }
            .copy(
                bidsMade = state.bidsMade + 1,
                highestBid = maxOf(state.highestBid, score),
                highestBidder = if (score > 0) seat else state.highestBidder,
            )
            .log("${actor.name} ${if (score == 0) "不叫" else "叫 $score 分"}")

        if (score == 3) {
            return assignLandlord(next, seat, score)
        }
        if (next.bidsMade >= 3) {
            val bidder = next.highestBidder
            return if (bidder != null) {
                assignLandlord(next, bidder, next.highestBid)
            } else {
                next = next.log("三家都不叫，重新发牌")
                applyDeal(next, next.seed + 1)
            }
        }
        return next.copy(currentSeat = state.nextSeat(seat))
    }

    private fun assignLandlord(state: GameState, seat: Int, bid: Int): GameState {
        val bottom = state.bottomCards
        val bottomFactor = if (bottom.any { it.rank.isJoker }) 2 else 1
        val landlord = state.player(seat)
        val hand = (landlord.hand + bottom).sorted()
        val players = state.players.map { player ->
            if (player.seat == seat) {
                player.copy(hand = hand, cardCount = hand.size, isLandlord = true)
            } else {
                player.copy(isLandlord = false)
            }
        }
        val bonusText = if (bottomFactor > 1) "，底牌含王 ×2" else ""
        return state.copy(
            phase = Phase.PLAYING,
            players = players,
            landlordSeat = seat,
            currentSeat = seat,
            lastHand = null,
            lastHandSeat = null,
            passStreak = 0,
            multiplier = bid * bottomFactor,
            log = (state.log +
                "${landlord.name} 成为地主（$bid 分$bonusText），底牌 ${bottom.joinToString(" ")}").takeLast(60),
        )
    }

    private fun applyPlay(state: GameState, seat: Int, cards: List<Card>): GameState {
        val actor = state.player(seat)
        val hand = HandParser.parse(cards) ?: error("validated play must parse")
        val remaining = actor.hand.toMutableList()
        for (card in cards) {
            if (!remaining.remove(card)) {
                throw IllegalActionException("BAD_CARDS", "手牌中没有这些牌")
            }
        }
        val isBomb = hand.type == HandType.BOMB || hand.type == HandType.ROCKET
        val multiplier = if (isBomb) state.multiplier * 2 else state.multiplier
        val bombs = if (isBomb) state.bombCount + 1 else state.bombCount
        val playCounts = state.playCounts.toMutableList().also { it[seat] = it[seat] + 1 }

        var next = state
            .withPlayer(seat) { it.copy(hand = remaining.sorted(), cardCount = remaining.size) }
            .copy(
                lastHand = hand,
                lastHandSeat = seat,
                passStreak = 0,
                multiplier = multiplier,
                bombCount = bombs,
                playCounts = playCounts,
            )
            .log("${actor.name} 出 ${hand.type.label}：${hand.cards.joinToString(" ")}")
        if (isBomb) next = next.log("炸弹！倍数 ×2 → ${multiplier}")

        if (remaining.isEmpty()) {
            return settle(next, seat)
        }
        return next.copy(currentSeat = state.nextSeat(seat))
    }

    private fun applyPass(state: GameState, seat: Int): GameState {
        val actor = state.player(seat)
        val lastSeat = state.lastHandSeat ?: return state
        if (state.passStreak + 1 >= 2) {
            return state.copy(
                passStreak = 0,
                lastHand = null,
                lastHandSeat = null,
                currentSeat = lastSeat,
            ).log("${actor.name} 不出，两家都过，${state.player(lastSeat).name} 重新领出")
        }
        return state.copy(
            passStreak = state.passStreak + 1,
            currentSeat = state.nextSeat(seat),
        ).log("${actor.name} 不出")
    }

    private fun applyTimeout(state: GameState, seat: Int): GameState = when (state.phase) {
        Phase.BIDDING -> applyBid(state, seat, 0)
        Phase.PLAYING -> {
            if (state.lastHand == null) {
                val card = state.player(seat).hand.minByOrNull { it.rank.value }
                    ?: return state
                applyPlay(state, seat, listOf(card))
            } else {
                applyPass(state, seat)
            }
        }
        else -> state
    }

    private fun applyRestart(state: GameState): GameState {
        val players = state.players.map {
            it.copy(hand = emptyList(), cardCount = 0, isLandlord = false, bid = null)
        }
        return state.copy(
            phase = Phase.WAITING,
            players = players,
            bottomCards = emptyList(),
            landlordSeat = null,
            currentSeat = 0,
            lastHand = null,
            lastHandSeat = null,
            passStreak = 0,
            multiplier = 1,
            highestBid = 0,
            highestBidder = null,
            bidsMade = 0,
            bombCount = 0,
            playCounts = listOf(0, 0, 0),
            winnerSeat = null,
            settlement = null,
            log = (state.log + "本局结束，回到房间").takeLast(60),
        )
    }

    private fun settle(state: GameState, winnerSeat: Int): GameState {
        val landlordSeat = state.landlordSeat ?: return state
        val landlordWin = winnerSeat == landlordSeat
        val farmers = (0..2).filter { it != landlordSeat }
        val spring = landlordWin && farmers.all { state.playCounts[it] == 0 }
        val antiSpring = !landlordWin && state.playCounts[landlordSeat] <= 1
        var multiplier = state.multiplier
        if (spring) multiplier *= 2
        if (antiSpring) multiplier *= 2

        val baseBid = state.highestBid.coerceAtLeast(1)
        val deltas = (0..2).map { seat ->
            if (landlordWin) {
                if (seat == landlordSeat) 2 * multiplier else -multiplier
            } else {
                if (seat == landlordSeat) -2 * multiplier else multiplier
            }
        }
        val winner = state.player(winnerSeat)
        val reason = when {
            spring -> "春天 · 地主获胜"
            antiSpring -> "反春天 · 农民获胜"
            landlordWin -> "地主获胜"
            else -> "农民获胜"
        }
        return state.copy(
            phase = Phase.SETTLING,
            multiplier = multiplier,
            winnerSeat = winnerSeat,
            settlement = Settlement(
                winnerSeat = winnerSeat,
                landlordSeat = landlordSeat,
                landlordWin = landlordWin,
                baseBid = baseBid,
                multiplier = multiplier,
                deltas = deltas,
                spring = spring,
                antiSpring = antiSpring,
                reason = reason,
            ),
        ).log("${winner.name} 出完了手牌，$reason，倍数 ×$multiplier")
    }

    private fun checkPhase(state: GameState, expected: Phase, label: String) {
        if (state.phase != expected) {
            throw IllegalActionException(
                "WRONG_PHASE",
                "现在不能$label（当前阶段 ${state.phase}）",
            )
        }
    }

    private fun checkTurn(state: GameState, seat: Int) {
        if (state.currentSeat != seat) {
            throw IllegalActionException(
                "NOT_YOUR_TURN",
                "还没轮到座位 ${seat + 1}（当前是座位 ${state.currentSeat + 1}）",
            )
        }
    }

    private fun checkSeat(seat: Int) {
        if (seat !in 0..2) {
            throw IllegalActionException("BAD_SEAT", "座位号必须是 0..2")
        }
    }
}
