package com.ddz.shared.ai

import com.ddz.shared.game.core.GameAction
import com.ddz.shared.game.core.GameRules
import com.ddz.shared.game.core.GameState
import com.ddz.shared.game.core.HandParser
import com.ddz.shared.game.core.IllegalActionException
import com.ddz.shared.game.core.Phase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Layer 2 (optional difficulty): budget-limited flat Monte Carlo search.
 *
 * Each legal candidate gets random playouts; the candidate with the best average
 * team outcome wins. The time budget is hard-capped at 800 ms and the search runs
 * on [Dispatchers.Default] so it never blocks the UI, and hosts fall back to
 * [HeuristicAi] if anything goes wrong.
 */
class MctsAi(
    private val budgetMs: Long = 800L,
    private val randomSeed: Long = 11L,
) : AiStrategy {

    private val fallback = HeuristicAi()

    override suspend fun decide(state: GameState, seat: Int): GameAction {
        if (state.phase != Phase.PLAYING) return fallback.decide(state, seat)
        val budget = budgetMs.coerceIn(50L, 800L)
        return withContext(Dispatchers.Default) {
            runCatching { search(state, seat, budget) }
                .getOrElse { fallback.decide(state, seat) }
        }
    }

    private suspend fun search(state: GameState, seat: Int, budget: Long): GameAction {
        val candidates = candidateActions(state, seat)
        if (candidates.isEmpty()) return GameAction.Pass(seat)
        if (candidates.size == 1) return candidates.first()

        val deadline = TimeSource.Monotonic.markNow() + budget.milliseconds
        val random = Random(randomSeed + state.seq)
        val sums = DoubleArray(candidates.size)
        val visits = IntArray(candidates.size)
        var total = 0

        while (!deadline.hasPassedNow() && total < MAX_ITERATIONS) {
            val index = pick(sums, visits, total)
            val score = rollout(state, candidates[index], seat, random)
            sums[index] += score
            visits[index] += 1
            total += 1
        }

        val best = candidates.indices
            .filter { visits[it] > 0 }
            .maxByOrNull { sums[it] / visits[it] }
            ?: return fallback.decide(state, seat)
        return candidates[best]
    }

    private fun pick(sums: DoubleArray, visits: IntArray, total: Int): Int {
        for (i in visits.indices) {
            if (visits[i] == 0) return i
        }
        val logTotal = kotlin.math.ln(total.toDouble().coerceAtLeast(1.0))
        return visits.indices.maxByOrNull { i ->
            sums[i] / visits[i] + UCB_C * kotlin.math.sqrt(logTotal / visits[i])
        } ?: 0
    }

    private fun candidateActions(state: GameState, seat: Int): List<GameAction> {
        val hand = state.player(seat).hand
        if (hand.isEmpty()) return emptyList()
        val last = state.lastHand
        val plays = if (last == null) {
            HandAnalyzer.leadCandidates(hand)
        } else {
            HandFinder.beating(hand, last)
        }
        val actions = mutableListOf<GameAction>()
        val whole = HandParser.parse(hand)
        if (whole != null && (last == null || whole.canBeat(last))) {
            actions += GameAction.Play(seat, hand)
        }
        for (play in plays.sortedBy { it.mainRank }) {
            if (play.cards.size == hand.size) continue
            actions += GameAction.Play(seat, play.cards)
        }
        if (last != null) actions += GameAction.Pass(seat)
        return actions.distinctBy { it.toString() }.take(MAX_CANDIDATES)
    }

    private fun rollout(
        start: GameState,
        action: GameAction,
        me: Int,
        random: Random,
    ): Double {
        var state = try {
            GameRules.reduce(start, action)
        } catch (e: IllegalActionException) {
            return -1.0
        }
        val landlordSeat = start.landlordSeat ?: return 0.0
        var steps = 0
        while (state.phase == Phase.PLAYING && steps < MAX_PLAYOUT_STEPS) {
            val actor = state.currentSeat
            val move = randomAction(state, actor, random) ?: break
            state = try {
                GameRules.reduce(state, move)
            } catch (e: IllegalActionException) {
                break
            }
            steps += 1
        }
        val settlement = state.settlement ?: return 0.0
        val iAmLandlord = me == landlordSeat
        return if (settlement.landlordWin == iAmLandlord) 1.0 else -1.0
    }

    private fun randomAction(state: GameState, seat: Int, random: Random): GameAction? {
        val hand = state.player(seat).hand
        if (hand.isEmpty()) return null
        val last = state.lastHand
        if (last == null) {
            val leads = HandAnalyzer.leadCandidates(hand)
            val cards = leads.randomOrNull(random)?.cards ?: listOf(hand.first())
            return GameAction.Play(seat, cards)
        }
        val beats = HandFinder.beating(hand, last)
        if (beats.isEmpty()) return GameAction.Pass(seat)
        if (random.nextInt(100) < 30) return GameAction.Pass(seat)
        return GameAction.Play(seat, beats.random(random).cards)
    }

    private companion object {
        const val MAX_CANDIDATES = 16
        const val MAX_ITERATIONS = 4000
        const val MAX_PLAYOUT_STEPS = 120
        const val UCB_C = 0.7
    }
}
