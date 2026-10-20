package com.ddz.shared

import com.ddz.shared.game.core.Card
import com.ddz.shared.game.core.Rank
import com.ddz.shared.game.core.Suit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CardTest {

    @Test
    fun deckHas54UniqueCards() {
        val deck = Card.deck()
        assertEquals(54, deck.size)
        assertEquals(54, deck.distinctBy { it.code }.size)
        assertEquals(4, deck.count { it.rank == Rank.THREE })
        assertEquals(1, deck.count { it.rank == Rank.SMALL_JOKER })
        assertEquals(1, deck.count { it.rank == Rank.BIG_JOKER })
    }

    @Test
    fun encodingRoundTripsForEveryCard() {
        for (card in Card.deck()) {
            assertEquals(card, Card.decode(card.code), "code ${card.code}")
        }
    }

    @Test
    fun rankOrderIsDouDizhuOrder() {
        assertTrue(Rank.TWO.value > Rank.ACE.value)
        assertTrue(Rank.BIG_JOKER.value > Rank.SMALL_JOKER.value)
        assertTrue(Rank.SMALL_JOKER.value > Rank.TWO.value)
        assertTrue(Card(Rank.BIG_JOKER, Suit.JOKER) > Card(Rank.TWO, Suit.SPADE))
    }

    @Test
    fun decodeRejectsUnknownCode() {
        assertFailsWith<IllegalArgumentException> { Card.decode(200) }
        assertFailsWith<IllegalArgumentException> { Card.decode(179) }
    }

    @Test
    fun suitProperties() {
        assertEquals(4, Suit.normalSuits.size)
        assertTrue(Suit.HEART.isRed)
        assertTrue(Suit.DIAMOND.isRed)
        assertTrue(!Suit.SPADE.isRed)
    }
}
