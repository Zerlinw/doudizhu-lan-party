package com.ddz.shared

import com.ddz.shared.game.core.HandParser
import com.ddz.shared.game.core.HandType
import com.ddz.shared.game.core.Rank
import com.ddz.shared.game.core.WingKind
import com.ddz.shared.game.core.parseHand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HandParserTest {

    private fun assertHand(
        spec: String,
        type: HandType,
        chain: Int = 1,
        mainRank: Int,
        wing: WingKind = WingKind.NONE,
    ) {
        val hand = assertNotNull(HandParser.parse(TestCards.of(spec)), "should parse: $spec")
        assertEquals(type, hand.type, "type of $spec")
        assertEquals(chain, hand.chain, "chain of $spec")
        assertEquals(mainRank, hand.mainRank, "mainRank of $spec")
        assertEquals(wing, hand.wing, "wing of $spec")
    }

    @Test
    fun parsesSingles() {
        assertHand("3s", HandType.SINGLE, mainRank = 3)
        assertHand("2c", HandType.SINGLE, mainRank = 15)
        assertHand("X", HandType.SINGLE, mainRank = 16)
        assertHand("D", HandType.SINGLE, mainRank = 17)
    }

    @Test
    fun parsesPairs() {
        assertHand("5s 5h", HandType.PAIR, mainRank = 5)
        assertHand("Ks Kd", HandType.PAIR, mainRank = 13)
        assertHand("2s 2h", HandType.PAIR, mainRank = 15)
    }

    @Test
    fun parsesTriplesAndKickers() {
        assertHand("5s 5h 5c", HandType.TRIPLE, mainRank = 5)
        assertHand("5s 5h 5c 9d", HandType.TRIPLE_ONE, mainRank = 5)
        assertHand("5s 5h 5c 9d 9h", HandType.TRIPLE_TWO, mainRank = 5)
        assertHand("As Ah Ac Kd Kh", HandType.TRIPLE_TWO, mainRank = 14)
    }

    @Test
    fun parsesStraights() {
        assertHand("3s 4h 5c 6d 7s", HandType.STRAIGHT, chain = 5, mainRank = 7)
        assertHand("3s 4h 5c 6d 7s 8h 9c Td", HandType.STRAIGHT, chain = 8, mainRank = 10)
        assertHand("Ts Jh Qc Kd As", HandType.STRAIGHT, chain = 5, mainRank = 14)
    }

    @Test
    fun parsesDoubleStraights() {
        assertHand("3s 3h 4c 4d 5s 5h", HandType.DOUBLE_STRAIGHT, chain = 3, mainRank = 5)
        assertHand(
            "3s 3h 4c 4d 5s 5h 6c 6d 7s 7h 8c 8d 9s 9h",
            HandType.DOUBLE_STRAIGHT,
            chain = 7,
            mainRank = 9,
        )
    }

    @Test
    fun parsesAirplanes() {
        assertHand("3s 3h 3c 4s 4h 4c", HandType.AIRPLANE, chain = 2, mainRank = 4)
        assertHand("3s 3h 3c 4s 4h 4c 5s 5h 5c", HandType.AIRPLANE, chain = 3, mainRank = 5)
        // A pure airplane must win over the 3+3 kicker reading.
        assertHand(
            "3s 3h 3c 4s 4h 4c 5s 5h 5c 6s 6h 6c",
            HandType.AIRPLANE,
            chain = 4,
            mainRank = 6,
        )
    }

    @Test
    fun parsesAirplaneWings() {
        assertHand(
            "3s 3h 3c 4s 4h 4c 9d Ts",
            HandType.AIRPLANE_WINGS,
            chain = 2,
            mainRank = 4,
            wing = WingKind.SINGLE,
        )
        assertHand(
            "3s 3h 3c 4s 4h 4c 9d 9h Ts Th",
            HandType.AIRPLANE_WINGS,
            chain = 2,
            mainRank = 4,
            wing = WingKind.PAIR,
        )
        assertHand(
            "3s 3h 3c 4s 4h 4c 5s 5h 5c 9d Ts Jd",
            HandType.AIRPLANE_WINGS,
            chain = 3,
            mainRank = 5,
            wing = WingKind.SINGLE,
        )
        // Four-of-a-kind can donate a triple and keep the fourth card as a wing.
        assertHand(
            "3s 3h 3c 3d 4s 4h 4c 4d",
            HandType.AIRPLANE_WINGS,
            chain = 2,
            mainRank = 4,
            wing = WingKind.SINGLE,
        )
    }

    @Test
    fun parsesFourWithKickers() {
        assertHand("3s 3h 3c 3d 9s Th", HandType.FOUR_TWO, mainRank = 3)
        assertHand("3s 3h 3c 3d 9s 9h Ts Th", HandType.FOUR_TWO_PAIR, mainRank = 3)
        assertHand("2s 2h 2c 2d As Ah Ks Kh", HandType.FOUR_TWO_PAIR, mainRank = 15)
    }

    @Test
    fun parsesBombsAndRocket() {
        assertHand("3s 3h 3c 3d", HandType.BOMB, mainRank = 3)
        assertHand("2s 2h 2c 2d", HandType.BOMB, mainRank = 15)
        assertHand("X D", HandType.ROCKET, mainRank = 17)
    }

    @Test
    fun rejectsInvalidShapes() {
        assertNull(parseHand(emptyList()))
        assertNull(HandParser.parse(TestCards.of("3s 4h 5c 6d"))) // short straight
        assertNull(HandParser.parse(TestCards.of("As 2h 3c 4d 5s"))) // 2 cannot be in a straight
        assertNull(HandParser.parse(TestCards.of("3s 4h 5c 6d 7s 8h 9c Td Js Qh Kc Ad 2s"))) // 2 in long run
        assertNull(HandParser.parse(TestCards.of("3s 3h 3c 5s 5h 5c"))) // non-consecutive triples
        assertNull(HandParser.parse(TestCards.of("3s 5h 7c"))) // junk
        assertNull(HandParser.parse(TestCards.of("3s 3h 3c 3d 9s"))) // four + one kicker
        assertNull(HandParser.parse(TestCards.of("3s 3h 3c 3d 9s 9h 9c 9d"))) // 4 + two same-rank pairs
        assertNull(HandParser.parse(TestCards.of("3s 3h 4c 4d 5s"))) // incomplete double straight
        assertNull(HandParser.parse(TestCards.of("3s 3h 3c 4s 4h 4c 9d"))) // airplane wings count
        assertNull(HandParser.parse(TestCards.of("3s 3h 3c 4s 4h 4c 9d 9h Ts"))) // pair wings incomplete
    }

    @Test
    fun jokerRules() {
        val rocket = TestCards.hand("X D")
        assertEquals(HandType.ROCKET, rocket.type)
        assertTrue(TestCards.hand("X").canBeat(TestCards.hand("2s")))
        assertFalse(TestCards.hand("2s").canBeat(TestCards.hand("X")))
    }

    @Test
    fun comparisons() {
        assertTrue(TestCards.hand("5s").canBeat(TestCards.hand("4s")))
        assertFalse(TestCards.hand("4s").canBeat(TestCards.hand("5s")))
        assertTrue(TestCards.hand("5s 5h").canBeat(TestCards.hand("4s 4h")))
        assertFalse(TestCards.hand("5s 5h").canBeat(TestCards.hand("5s"))) // pair cannot follow single
        assertTrue(
            TestCards.hand("6s 6h 6c 7d 7h 7c").canBeat(
                TestCards.hand("5s 5h 5c 6d 6h 6c"),
            ),
        )
        assertFalse(
            TestCards.hand("4s 5h 6c 7d 8s").canBeat(
                TestCards.hand("3s 4h 5c 6d 7s 8h"),
            ),
        )
        assertTrue(TestCards.hand("3s 3h 3c 3d").canBeat(TestCards.hand("4s 5h 6c 7d 8s")))
        assertTrue(TestCards.hand("3s 3h 3c 3d").canBeat(TestCards.hand("2s 2h")))
        assertTrue(TestCards.hand("4s 4h 4c 4d").canBeat(TestCards.hand("3s 3h 3c 3d")))
        assertTrue(TestCards.hand("X D").canBeat(TestCards.hand("2s 2h 2c 2d")))
        assertFalse(TestCards.hand("2s 2h 2c 2d").canBeat(TestCards.hand("X D")))
        assertFalse(
            TestCards.hand("3s 3h 3c 4s 4h 4c 9d Ts").canBeat(
                TestCards.hand("5s 5h 5c 6s 6h 6c 9d 9h Ts Th"),
            ),
        )
        assertTrue(TestCards.hand("5s 5h 5c 6s 6h 6c 9d 9h Ts Th").canBeat(
            TestCards.hand("3s 3h 3c 4s 4h 4c 9d 9h Ts Th"),
        ))
    }

    @Test
    fun straightCannotCrossAceAndTwo() {
        assertNull(HandParser.parse(TestCards.of("Ts Jh Qc Kd As 2s")))
        assertNotNull(HandParser.parse(TestCards.of("Ts Jh Qc Kd As")))
    }

    @Test
    fun handKeepsOriginalCards() {
        val cards = TestCards.of("5s 5h 5c 9d")
        val hand = TestCards.hand("5s 5h 5c 9d")
        assertEquals(4, hand.size)
        assertEquals(cards.toSet(), hand.cards.toSet())
        assertEquals(Rank.NINE, hand.cards.last().rank)
    }
}
