package com.ddz.shared

import com.ddz.shared.game.core.GameAction
import com.ddz.shared.game.core.GameRules
import com.ddz.shared.game.core.GameState
import com.ddz.shared.game.core.IllegalActionException
import com.ddz.shared.game.core.Phase
import com.ddz.shared.game.core.Rank
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReducerTest {

    private fun biddingState(seed: Long = 42L): GameState {
        var state = GameState.fresh()
        state = GameRules.reduce(state, GameAction.JoinSeat(0, "Zerlin", 1))
        state = GameRules.reduce(state, GameAction.JoinSeat(1, "阿豆", 2, isAi = true))
        state = GameRules.reduce(state, GameAction.JoinSeat(2, "小九", 3, isAi = true))
        return GameRules.reduce(state, GameAction.Deal(seed))
    }

    @Test
    fun dealGives17CardsEachAnd3BottomCards() {
        val state = biddingState()
        assertEquals(Phase.BIDDING, state.phase)
        assertEquals(4L, state.seq)
        state.players.forEach { assertEquals(17, it.cardCount); assertEquals(17, it.hand.size) }
        assertEquals(3, state.bottomCards.size)
        val all = state.players.flatMap { it.hand } + state.bottomCards
        assertEquals(54, all.size)
        assertEquals(54, all.distinctBy { it.code }.size)
        assertEquals(0, state.bidsMade)
        assertEquals(1, state.round)
        assertEquals(1, state.currentSeat)
    }

    @Test
    fun biddingRoundChoosesHighestBidderAsLandlord() {
        var state = biddingState()
        val first = state.currentSeat
        state = GameRules.reduce(state, GameAction.Bid(first, 1))
        state = GameRules.reduce(state, GameAction.Bid(state.currentSeat, 2))
        state = GameRules.reduce(state, GameAction.Bid(state.currentSeat, 3))

        assertEquals(Phase.PLAYING, state.phase)
        assertEquals(3, state.highestBid)
        assertNotNull(state.landlordSeat)
        assertEquals(20, state.player(state.landlordSeat!!).hand.size)
        assertTrue(state.players.single { it.isLandlord }.seat == state.landlordSeat)
        val bottomHasJoker = state.bottomCards.any { it.rank.isJoker }
        assertEquals(if (bottomHasJoker) 6 else 3, state.multiplier)
        assertEquals(state.landlordSeat, state.currentSeat)
    }

    @Test
    fun onlyHigherBidsAreAllowed() {
        var state = biddingState()
        val seat = state.currentSeat
        state = GameRules.reduce(state, GameAction.Bid(seat, 2))
        val next = state.currentSeat
        assertFailsWith<IllegalActionException> {
            GameRules.reduce(state, GameAction.Bid(next, 2))
        }
        assertFailsWith<IllegalActionException> {
            GameRules.reduce(state, GameAction.Bid(next, 1))
        }
    }

    @Test
    fun allPassRedeals() {
        var state = biddingState()
        repeat(3) {
            state = GameRules.reduce(state, GameAction.Bid(state.currentSeat, 0))
        }
        assertEquals(Phase.BIDDING, state.phase)
        assertEquals(2, state.round)
        assertEquals(0, state.bidsMade)
        assertEquals(0, state.highestBid)
        assertEquals(17, state.player(0).hand.size)
    }

    @Test
    fun playPassFlowReturnsLeadToLastPlayer() {
        var state = TestCards.playing(
            seat0 = "3s 8s",
            seat1 = "4s 5s",
            seat2 = "6s 7s",
            landlordSeat = 0,
            currentSeat = 0,
        )
        state = GameRules.reduce(state, GameAction.Play(0, TestCards.of("3s")))
        assertEquals(1, state.currentSeat)
        assertEquals(Rank.THREE.value, state.lastHand?.mainRank)
        assertEquals(0, state.lastHandSeat)

        state = GameRules.reduce(state, GameAction.Pass(1))
        assertEquals(2, state.currentSeat)
        state = GameRules.reduce(state, GameAction.Pass(2))
        assertEquals(0, state.currentSeat)
        assertNull(state.lastHand)
        assertEquals(0, state.passStreak)

        // After two passes, seat 0 must lead again and cannot pass.
        assertFailsWith<IllegalActionException> {
            GameRules.reduce(state, GameAction.Pass(0))
        }
    }

    @Test
    fun passIsRejectedWhenLeading() {
        val state = TestCards.playing("3s 8s", "4s 5s", "6s 7s", currentSeat = 0)
        assertFailsWith<IllegalActionException> {
            GameRules.reduce(state, GameAction.Pass(0))
        }
    }

    @Test
    fun cannotPlayCardsNotInHandOrOutOfTurn() {
        val state = TestCards.playing("3s 8s", "4s 5s", "6s 7s", currentSeat = 0)
        assertFailsWith<IllegalActionException> {
            GameRules.reduce(state, GameAction.Play(0, TestCards.of("9s")))
        }
        assertFailsWith<IllegalActionException> {
            GameRules.reduce(state, GameAction.Play(1, TestCards.of("4s")))
        }
        assertFailsWith<IllegalActionException> {
            GameRules.reduce(state, GameAction.Play(0, TestCards.of("3s 3h")))
        }
    }

    @Test
    fun cannotBeatHigherHand() {
        val state = TestCards.playing(
            seat0 = "5s 9s",
            seat1 = "3s 4s",
            seat2 = "6s 7s",
            currentSeat = 1,
            lastHand = TestCards.hand("5s"),
            lastHandSeat = 0,
        )
        assertFailsWith<IllegalActionException> {
            GameRules.reduce(state, GameAction.Play(1, TestCards.of("3s")))
        }
    }

    @Test
    fun bombDoublesMultiplier() {
        var state = TestCards.playing(
            seat0 = "3s 3h 3c 3d 8s",
            seat1 = "4s 4h",
            seat2 = "6s 7s",
            currentSeat = 0,
        )
        state = GameRules.reduce(state, GameAction.Play(0, TestCards.of("3s 3h 3c 3d")))
        assertEquals(2, state.multiplier)
        assertEquals(1, state.bombCount)
        assertEquals(1, state.player(0).cardCount)
    }

    @Test
    fun rocketDoublesMultiplier() {
        var state = TestCards.playing(
            seat0 = "X D 3s",
            seat1 = "4s 4h",
            seat2 = "6s 7s",
            currentSeat = 0,
        )
        state = GameRules.reduce(state, GameAction.Play(0, TestCards.of("X D")))
        assertEquals(2, state.multiplier)
        assertEquals(1, state.bombCount)
    }

    @Test
    fun settlementAndSpringScoring() {
        var state = TestCards.playing(
            seat0 = "3s",
            seat1 = "4s 4h",
            seat2 = "6s 7s",
            landlordSeat = 0,
            currentSeat = 0,
        )
        state = GameRules.reduce(state, GameAction.Play(0, TestCards.of("3s")))
        assertEquals(Phase.SETTLING, state.phase)
        val settlement = assertNotNull(state.settlement)
        assertTrue(settlement.landlordWin)
        assertTrue(settlement.spring)
        assertEquals(Phase.SETTLING, state.phase)
        assertEquals(0, settlement.deltas.sum())
        assertEquals(2, settlement.multiplier)
        assertEquals(4, settlement.deltas[0])
        assertEquals(-2, settlement.deltas[1])
        assertEquals(-2, settlement.deltas[2])
        assertEquals(0, state.winnerSeat)
    }

    @Test
    fun antiSpringDoublesMultiplierForFarmers() {
        var state = TestCards.playing(
            seat0 = "3s 9s",
            seat1 = "5s",
            seat2 = "6s 7s",
            landlordSeat = 0,
            currentSeat = 1,
        ).copy(playCounts = listOf(1, 0, 0), multiplier = 2)
        state = GameRules.reduce(state, GameAction.Play(1, TestCards.of("5s")))
        assertEquals(Phase.SETTLING, state.phase)
        val settlement = assertNotNull(state.settlement)
        assertTrue(!settlement.landlordWin)
        assertTrue(settlement.antiSpring)
        assertEquals(0, settlement.deltas.sum())
        assertEquals(-8, settlement.deltas[0])
        assertEquals(4, settlement.deltas[1])
        assertEquals(4, settlement.deltas[2])
    }

    @Test
    fun timeoutBidsPassWhenLeadingPlayLowestCard() {
        var state = biddingState()
        val bidder = state.currentSeat
        state = GameRules.reduce(state, GameAction.Timeout(bidder))
        assertEquals(Phase.BIDDING, state.phase)
        assertEquals(0, state.player(bidder).bid)

        var playing = TestCards.playing("9s 3s", "4s 4h", "6s 7s", currentSeat = 0)
        playing = GameRules.reduce(playing, GameAction.Timeout(0))
        assertEquals(1, playing.player(0).cardCount)
        assertTrue(playing.player(0).hand.none { it.rank == Rank.THREE })
    }

    @Test
    fun joinAndSeatRules() {
        var state = GameState.fresh()
        state = GameRules.reduce(state, GameAction.JoinSeat(0, "Zerlin", 1))
        assertEquals("Zerlin", state.player(0).name)
        assertFailsWith<IllegalActionException> {
            GameRules.reduce(state, GameAction.JoinSeat(0, "Other", 2))
        }
        assertFailsWith<IllegalActionException> {
            GameRules.reduce(state, GameAction.JoinSeat(1, "Zerlin", 2))
        }
        assertFailsWith<IllegalActionException> {
            GameRules.reduce(state, GameAction.JoinSeat(1, "  ", 2))
        }
        state = GameRules.reduce(state, GameAction.LeaveSeat(0))
        assertEquals("", state.player(0).name)
    }

    @Test
    fun cannotDealWithTwoPlayers() {
        var state = GameState.fresh()
        state = GameRules.reduce(state, GameAction.JoinSeat(0, "A", 1))
        state = GameRules.reduce(state, GameAction.JoinSeat(1, "B", 2))
        assertFailsWith<IllegalActionException> {
            GameRules.reduce(state, GameAction.Deal(1L))
        }
    }

    @Test
    fun redactionHidesOtherHandsAndBottomUntilLandlordChosen() {
        val state = biddingState()
        val view = state.redactedFor(1)
        assertEquals(0, view.player(0).hand.size)
        assertEquals(0, view.player(2).hand.size)
        assertEquals(17, view.player(1).hand.size)
        assertEquals(17, view.player(1).cardCount)
        assertTrue(view.bottomCards.isEmpty())

        val playing = state.copy(phase = Phase.PLAYING, landlordSeat = 1)
        assertTrue(playing.redactedFor(0).bottomCards.size == 3)
    }

    @Test
    fun everyAcceptedActionBumpsSeqByOne() {
        var state = GameState.fresh()
        val before = state.seq
        state = GameRules.reduce(state, GameAction.JoinSeat(0, "A", 1))
        assertEquals(before + 1, state.seq)
        state = GameRules.reduce(state, GameAction.JoinSeat(1, "B", 2))
        assertEquals(before + 2, state.seq)
        val rejected = GameRules.validate(state, GameAction.Play(0, TestCards.of("3s")))
        assertNotNull(rejected)
    }
}
