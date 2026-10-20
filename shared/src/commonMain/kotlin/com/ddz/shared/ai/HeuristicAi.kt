package com.ddz.shared.ai

import com.ddz.shared.game.core.Card
import com.ddz.shared.game.core.GameAction
import com.ddz.shared.game.core.GameState
import com.ddz.shared.game.core.Hand
import com.ddz.shared.game.core.HandParser
import com.ddz.shared.game.core.HandType
import com.ddz.shared.game.core.Phase
import com.ddz.shared.game.core.Rank

/** Pluggable AI policy. Implementations must be side-effect free and suspend-friendly. */
interface AiStrategy {
    /** Decide the next legal action for [seat]. Never returns an action for another seat. */
    suspend fun decide(state: GameState, seat: Int): GameAction
}

/** Count of each rank in a hand. */
internal fun List<Card>.rankHistogram(): Map<Rank, Int> =
    groupingBy { it.rank }.eachCount()

/**
 * Layer 1: fast heuristic AI.
 *
 * - Bidding scores raw hand power (jokers, 2s, bombs, aces).
 * - Following prefers the cheapest beat, never breaks a bomb or a rocket, and
 *   passes when a teammate is leading.
 * - Leading dumps the cheapest structural group and goes out immediately when
 *   the whole hand is one legal shape.
 *
 * Expected cost per decision: well under 1 ms.
 */
class HeuristicAi : AiStrategy {

    override suspend fun decide(state: GameState, seat: Int): GameAction = when (state.phase) {
        Phase.BIDDING -> GameAction.Bid(seat, decideBid(state, seat))
        Phase.PLAYING -> decidePlay(state, seat)
        else -> GameAction.Timeout(seat)
    }

    /** 0 = pass, 1..3 = bid. */
    fun decideBid(state: GameState, seat: Int): Int {
        val hand = state.player(seat).hand
        val counts = hand.rankHistogram()
        val bombs = counts.count { it.value == 4 }
        val hasRocket = counts.keys.containsAll(listOf(Rank.SMALL_JOKER, Rank.BIG_JOKER))
        var power = 0
        power += bombs * 10
        if (hasRocket) power += 8
        power += (counts[Rank.BIG_JOKER] ?: 0) * 8
        power += (counts[Rank.SMALL_JOKER] ?: 0) * 6
        power += (counts[Rank.TWO] ?: 0) * 2
        power += (counts[Rank.ACE] ?: 0)
        return when {
            power >= 18 -> 3
            power >= 12 -> 2
            power >= 7 -> 1
            else -> 0
        }.takeIf { it > state.highestBid } ?: 0
    }

    private fun decidePlay(state: GameState, seat: Int): GameAction {
        val me = state.player(seat)
        val last = state.lastHand
        if (last == null) return lead(state, seat)

        // Go out in one move whenever possible.
        val whole = HandParser.parse(me.hand)
        if (whole != null && whole.canBeat(last)) return GameAction.Play(seat, me.hand)

        val lastSeat = state.lastHandSeat
        if (lastSeat != null && state.areTeammates(seat, lastSeat)) {
            return GameAction.Pass(seat)
        }

        val options = HandFinder.beating(me.hand, last)
        if (options.isEmpty()) return GameAction.Pass(seat)

        val safe = options.filter { !breaksBombOrRocket(me.hand, it) }
        val plain = safe.filter { it.type != HandType.BOMB && it.type != HandType.ROCKET }
        val cheapest = plain.minWithOrNull(compareBy({ it.mainRank }, { it.size }))
        if (cheapest != null) return GameAction.Play(seat, cheapest.cards)

        val bombs = options.filter { it.type == HandType.BOMB || it.type == HandType.ROCKET }
        val minOpponent = minOpponentCards(state, seat)
        if (bombs.isNotEmpty() && (minOpponent <= 2 || me.cardCount <= 4)) {
            val bomb = bombs.minByOrNull { it.mainRank } ?: bombs.first()
            return GameAction.Play(seat, bomb.cards)
        }
        return GameAction.Pass(seat)
    }

    private fun lead(state: GameState, seat: Int): GameAction {
        val me = state.player(seat)
        if (me.hand.isEmpty()) return GameAction.Pass(seat)

        val whole = HandParser.parse(me.hand)
        if (whole != null) return GameAction.Play(seat, me.hand)

        val groups = HandAnalyzer.leadCandidates(me.hand)
        if (groups.isEmpty()) return GameAction.Play(seat, listOf(me.hand.first()))

        val minOpponent = minOpponentCards(state, seat)
        val plain = groups.filter { it.type != HandType.BOMB && it.type != HandType.ROCKET }
        var pool = if (plain.isNotEmpty()) plain else groups
        if (minOpponent <= 1) {
            val multi = pool.filter { it.cards.size >= 2 }
            if (multi.isNotEmpty()) pool = multi
        }
        val chosen = pool.minWithOrNull(
            compareBy({ leadPriority(it) }, { it.mainRank }, { -it.size }),
        ) ?: pool.first()
        return GameAction.Play(seat, chosen.cards)
    }

    private fun leadPriority(hand: Hand): Int = when (hand.type) {
        HandType.STRAIGHT, HandType.DOUBLE_STRAIGHT, HandType.AIRPLANE, HandType.AIRPLANE_WINGS -> 0
        HandType.TRIPLE_TWO, HandType.TRIPLE_ONE -> 1
        HandType.TRIPLE -> 2
        HandType.PAIR -> 3
        HandType.SINGLE -> 4
        else -> 5
    }

    private fun minOpponentCards(state: GameState, seat: Int): Int {
        val landlord = state.landlordSeat ?: return 17
        val opponents = if (seat == landlord) {
            (0..2).filter { it != landlord }
        } else {
            listOf(landlord)
        }
        return opponents.minOf { state.player(it).cardCount }
    }

    /** True when playing [candidate] takes cards out of an intact bomb or the rocket. */
    private fun breaksBombOrRocket(hand: List<Card>, candidate: Hand): Boolean {
        if (candidate.type == HandType.BOMB || candidate.type == HandType.ROCKET) return false
        val hist = hand.rankHistogram()
        val used = candidate.cards.rankHistogram()
        val bombBroken = used.any { (rank, n) -> (hist[rank] ?: 0) == 4 && n in 1..3 }
        if (bombBroken) return true
        val small = used[Rank.SMALL_JOKER] ?: 0
        val big = used[Rank.BIG_JOKER] ?: 0
        val ownsRocket = (hist[Rank.SMALL_JOKER] ?: 0) > 0 && (hist[Rank.BIG_JOKER] ?: 0) > 0
        return ownsRocket && (small + big) == 1
    }
}
