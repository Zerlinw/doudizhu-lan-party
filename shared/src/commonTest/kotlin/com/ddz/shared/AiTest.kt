package com.ddz.shared

import com.ddz.shared.ai.HandFinder
import com.ddz.shared.ai.HeuristicAi
import com.ddz.shared.ai.MctsAi
import com.ddz.shared.game.core.GameAction
import com.ddz.shared.game.core.GameRules
import com.ddz.shared.game.core.GameState
import com.ddz.shared.game.core.HandParser
import com.ddz.shared.game.core.Phase
import com.ddz.shared.game.core.Player
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AiTest {

    private val ai = HeuristicAi()

    private fun bidState(hand: String, highestBid: Int = 0): GameState {
        val me = TestCards.player(0, hand)
        return GameState(
            phase = Phase.BIDDING,
            players = listOf(me, Player(1, "B"), Player(2, "C")),
            currentSeat = 0,
            highestBid = highestBid,
        )
    }

    @Test
    fun strongHandBidsHighWeakHandPasses() = runTest {
        assertEquals(3, ai.decideBid(bidState("X D 2s 2h 2c 2d As Ah"), 0))
        assertEquals(0, ai.decideBid(bidState("3s 4h 6c 7d 9s"), 0))
        // Cannot equal or lower the current bid.
        assertEquals(0, ai.decideBid(bidState("X D 2s 2h", highestBid = 3), 0))
    }

    @Test
    fun followsWithCheapestCardAndKeepsBombIntact() = runTest {
        val state = TestCards.playing(
            seat0 = "6s 7s",
            seat1 = "3s 3h 3c 3d 5s",
            seat2 = "8s 9s",
            landlordSeat = 0,
            currentSeat = 1,
            lastHand = TestCards.hand("4s"),
            lastHandSeat = 0,
        )
        val action = ai.decide(state, 1)
        assertTrue(action is GameAction.Play, "expected play, got $action")
        assertEquals(listOf(TestCards.parse("5s").code), (action as GameAction.Play).cards.map { it.code })
    }

    @Test
    fun passesWhenTeammateIsLeading() = runTest {
        val state = TestCards.playing(
            seat0 = "6s 7s",
            seat1 = "5s 8s",
            seat2 = "9s Ts",
            landlordSeat = 0,
            currentSeat = 2,
            lastHand = TestCards.hand("5s"),
            lastHandSeat = 1,
        )
        assertTrue(ai.decide(state, 2) is GameAction.Pass)
    }

    @Test
    fun goesOutWhenWholeHandIsOneCombo() = runTest {
        val state = TestCards.playing(
            seat0 = "6s 7s",
            seat1 = "5s 5h",
            seat2 = "9s Ts",
            landlordSeat = 0,
            currentSeat = 1,
            lastHand = TestCards.hand("4s 4h"),
            lastHandSeat = 0,
        )
        val action = ai.decide(state, 1)
        assertTrue(action is GameAction.Play)
        assertEquals(2, (action as GameAction.Play).cards.size)
        assertEquals(TestCards.parse("5s").rank, action.cards.first().rank)
    }

    @Test
    fun leadsLowestSingleWhenNoCombo() = runTest {
        val state = TestCards.playing(
            seat0 = "9s 3s",
            seat1 = "4s 5h",
            seat2 = "6s 7s",
            landlordSeat = 0,
            currentSeat = 0,
        )
        val action = ai.decide(state, 0)
        assertTrue(action is GameAction.Play)
        assertEquals(TestCards.parse("3s").code, (action as GameAction.Play).cards.single().code)
    }

    @Test
    fun beatsWithBombWhenOpponentIsAboutToWin() = runTest {
        val state = TestCards.playing(
            seat0 = "4s",
            seat1 = "3s 3h 3c 3d",
            seat2 = "6s 7s",
            landlordSeat = 0,
            currentSeat = 1,
            lastHand = TestCards.hand("5s"),
            lastHandSeat = 0,
        )
        val action = ai.decide(state, 1)
        assertTrue(action is GameAction.Play)
        assertTrue(
            (action as GameAction.Play).cards.map { it.rank }.distinct().size == 1,
            "expected bomb, got ${action.cards}",
        )
    }

    @Test
    fun finderGeneratesOnlyLegalBeats() {
        val hand = TestCards.of("3s 4s 5s 5h 6s 7s X D")
        val last = TestCards.hand("4h")
        val options = HandFinder.beating(hand, last)
        assertTrue(options.isNotEmpty())
        options.forEach { option ->
            assertNotNull(HandParser.parse(option.cards))
            assertTrue(option.canBeat(last))
        }
        assertTrue(options.any { it.cards.size == 2 && it.cards.all { c -> c.rank.isJoker } })
    }

    @Test
    fun heuristicSelfPlayTerminatesAndScoresZeroSum() = runTest {
        for (seed in 1L..5L) {
            var state = TestCards.biddingState(seed)
            var steps = 0
            while (state.phase != Phase.SETTLING && steps < 500) {
                val action = ai.decide(state, state.currentSeat)
                state = GameRules.reduce(state, action)
                steps += 1
            }
            assertEquals(Phase.SETTLING, state.phase, "seed $seed did not settle in $steps steps")
            val settlement = assertNotNull(state.settlement)
            assertEquals(0, settlement.deltas.sum(), "seed $seed deltas")
            assertEquals(0, state.log.size.coerceAtMost(0))
            assertTrue(state.log.isNotEmpty())
        }
    }

    @Test
    fun mctsReturnsLegalActionWithinBudget() = runTest {
        val state = TestCards.playing(
            seat0 = "3s 4s 5s 6s 7s 8s",
            seat1 = "4h 4c",
            seat2 = "9s Ts",
            landlordSeat = 0,
            currentSeat = 0,
        )
        val action = MctsAi(budgetMs = 80).decide(state, 0)
        assertEquals(null, GameRules.validate(state, action))
    }
}
