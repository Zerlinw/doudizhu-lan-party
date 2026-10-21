package com.ddz.shared

import com.ddz.shared.game.core.GameAction
import com.ddz.shared.game.core.GameMode
import com.ddz.shared.game.core.GameRules
import com.ddz.shared.game.core.GameState
import com.ddz.shared.game.core.HandParser
import com.ddz.shared.game.core.HandType
import com.ddz.shared.game.core.LaiziInfo
import com.ddz.shared.game.core.Phase
import com.ddz.shared.game.core.Rank
import com.ddz.shared.net.Msgs
import com.ddz.shared.net.NetworkCodec
import com.ddz.shared.net.SnapshotPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LaiziTest {

    private val five = setOf(Rank.FIVE)

    @Test
    fun recognisesSoftHardAndPureBombs() {
        val soft = HandParser.parse(TestCards.of("3s 3h 3c 5d"), five)
        assertNotNull(soft)
        assertEquals(HandType.SOFT_BOMB, soft.type)
        assertEquals(3, soft.mainRank)
        assertEquals(4, soft.cards.size)

        val hard = HandParser.parse(TestCards.of("7s 7h 7c 7d"), five)
        assertNotNull(hard)
        assertEquals(HandType.BOMB, hard.type)

        val pure = HandParser.parse(TestCards.of("5s 5h 5c 5d"), five)
        assertNotNull(pure)
        assertEquals(HandType.PURE_LAIZI_BOMB, pure.type)
    }

    @Test
    fun supportsLargeSoftBombs() {
        val soft = HandParser.parse(TestCards.of("3s 3h 3c 5s 5h"), five)
        assertNotNull(soft)
        assertEquals(HandType.SOFT_BOMB, soft.type)
        assertEquals(5, soft.cards.size)
        assertEquals(3, soft.mainRank)
    }

    @Test
    fun bombLadderUsesSizeThenTier() {
        val soft4 = HandParser.parse(TestCards.of("3s 3h 3c 5d"), five)!!
        val hard4 = HandParser.parse(TestCards.of("7s 7h 7c 7d"), five)!!
        val pure4 = HandParser.parse(TestCards.of("5s 5h 5c 5d"), five)!!
        val soft5 = HandParser.parse(TestCards.of("3s 3h 3c 5s 5h"), five)!!
        val rocket = HandParser.parse(TestCards.of("X D"), five)!!

        assertTrue(hard4.canBeat(soft4))
        assertTrue(pure4.canBeat(hard4))
        assertTrue(soft5.canBeat(pure4))
        assertTrue(rocket.canBeat(soft5))
        assertTrue(!soft4.canBeat(hard4))
    }

    @Test
    fun wildCardsCompletePairsTriplesAndStraights() {
        val pair = HandParser.parse(TestCards.of("5s 5h"), five)
        assertNotNull(pair)
        assertEquals(HandType.PAIR, pair.type)

        val triple = HandParser.parse(TestCards.of("3s 3h 5d"), five)
        assertNotNull(triple)
        assertEquals(HandType.TRIPLE, triple.type)
        assertEquals(3, triple.mainRank)

        val tripleOne = HandParser.parse(TestCards.of("3s 3h 5d 7s"), five)
        assertNotNull(tripleOne)
        assertEquals(HandType.TRIPLE_ONE, tripleOne.type)
        assertEquals(3, tripleOne.mainRank)

        val straight = HandParser.parse(TestCards.of("3s 4h 5c 6d 9s"), setOf(Rank.NINE))
        assertNotNull(straight)
        assertEquals(HandType.STRAIGHT, straight.type)
        assertEquals(5, straight.chain)
        assertEquals(7, straight.mainRank)

        val pairStraight = HandParser.parse(
            TestCards.of("3s 3h 4c 4d 5s 9h"),
            setOf(Rank.NINE),
        )
        assertNotNull(pairStraight)
        assertEquals(HandType.DOUBLE_STRAIGHT, pairStraight.type)
        assertEquals(3, pairStraight.chain)
    }

    @Test
    fun laiziCannotStandInForJoker() {
        assertEquals(null, HandParser.parse(TestCards.of("X 5d"), five))
    }

    @Test
    fun dealSelectsTianAndRevealsDiAfterLandlord() {
        var state = GameState.fresh(GameMode.TIAN_DI_LAIZI)
        state = GameRules.reduce(state, GameAction.JoinSeat(0, "A", 1))
        state = GameRules.reduce(state, GameAction.JoinSeat(1, "B", 2))
        state = GameRules.reduce(state, GameAction.JoinSeat(2, "C", 3))
        state = GameRules.reduce(state, GameAction.Deal(1234L))

        val tian = assertNotNull(state.laizi).tianRank
        assertEquals(1, state.laiziRanks.size)
        val allBefore = state.players.flatMap { it.hand } + state.bottomCards
        assertEquals(4, allBefore.count { it.rank == tian })

        state = GameRules.reduce(state, GameAction.Bid(state.currentSeat, 3))
        val info = assertNotNull(state.laizi)
        val di = assertNotNull(info.diRank)
        assertTrue(di != tian)
        assertEquals(2, state.laiziRanks.size)
        val allAfter = state.players.flatMap { it.hand }
        assertEquals(4, allAfter.count { it.rank == tian })
        assertEquals(4, allAfter.count { it.rank == di })
    }

    private fun laiziPlaying(
        seat0: String,
        tian: Rank = Rank.FIVE,
        di: Rank = Rank.NINE,
        multiplier: Int = 1,
    ): GameState = TestCards.playing(
        seat0 = seat0,
        seat1 = "4s 4h",
        seat2 = "6s 7s",
        landlordSeat = 0,
        currentSeat = 0,
        multiplier = multiplier,
    ).copy(
        mode = GameMode.TIAN_DI_LAIZI,
        laizi = LaiziInfo(tian, di),
    )

    @Test
    fun softBombDoublesHardBombQuadruples() {
        var soft = laiziPlaying("3s 3h 3c 5d 8s")
        soft = GameRules.reduce(soft, GameAction.Play(0, TestCards.of("3s 3h 3c 5d")))
        assertEquals(Phase.PLAYING, soft.phase)
        assertEquals(HandType.SOFT_BOMB, soft.lastHand?.type)
        assertEquals(2, soft.multiplier)

        var hard = laiziPlaying("7s 7h 7c 7d 8s")
        hard = GameRules.reduce(hard, GameAction.Play(0, TestCards.of("7s 7h 7c 7d")))
        assertEquals(HandType.BOMB, hard.lastHand?.type)
        assertEquals(4, hard.multiplier)

        var pure = laiziPlaying("5s 5h 5c 5d 8s")
        pure = GameRules.reduce(pure, GameAction.Play(0, TestCards.of("5s 5h 5c 5d")))
        assertEquals(HandType.PURE_LAIZI_BOMB, pure.lastHand?.type)
        assertEquals(4, pure.multiplier)

        var big = laiziPlaying("3s 3h 3c 5s 5h 8s")
        big = GameRules.reduce(big, GameAction.Play(0, TestCards.of("3s 3h 3c 5s 5h")))
        assertEquals(5, big.lastHand?.cards?.size)
        assertEquals(6, big.multiplier)

        var rocket = laiziPlaying("X D 8s")
        rocket = GameRules.reduce(rocket, GameAction.Play(0, TestCards.of("X D")))
        assertEquals(HandType.ROCKET, rocket.lastHand?.type)
        assertEquals(6, rocket.multiplier)
    }

    @Test
    fun snapshotRoundTripsLaiziState() {
        val state = TestCards.playing(
            seat0 = "3s 3h 3c 5d",
            seat1 = "4s 4h",
            seat2 = "6s 7s",
            landlordSeat = 0,
            currentSeat = 0,
        ).copy(mode = GameMode.TIAN_DI_LAIZI, laizi = LaiziInfo(Rank.FIVE, Rank.NINE))

        val decoded = NetworkCodec.payload<SnapshotPayload>(
            NetworkCodec.decodeFrame(NetworkCodec.encodeFrame(Msgs.snapshot(7, state))),
        )
        assertEquals(GameMode.TIAN_DI_LAIZI, decoded.state.mode)
        assertEquals(setOf(Rank.FIVE, Rank.NINE), decoded.state.laiziRanks)
        assertEquals(state.seed, decoded.state.seed)
        assertEquals(7, decoded.seq)
    }

    @Test
    fun softBombBeatsOrdinaryHandAndWrongBombLoses() {
        var state = laiziPlaying("3s 3h 3c 5d 8s")
        state = GameRules.reduce(state, GameAction.Play(0, TestCards.of("3s 3h 3c 5d")))
        assertEquals(2, state.multiplier)

        val soft = state.lastHand!!
        val hard = HandParser.parse(TestCards.of("7s 7h 7c 7d"), state.laiziRanks)!!
        assertTrue(hard.canBeat(soft))
    }
}
