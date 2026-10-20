package com.ddz.shared.game.core

import kotlinx.serialization.Serializable

/**
 * Card suit. Jokers use [Suit.JOKER] because they have no real suit.
 */
@Serializable
enum class Suit(val symbol: String, val isRed: Boolean) {
    SPADE("♠", false),
    HEART("♥", true),
    CLUB("♣", false),
    DIAMOND("♦", true),
    JOKER("★", false),
    ;

    companion object {
        /** The four ordinary suits, in a fixed order used by deck construction. */
        val normalSuits: List<Suit> = listOf(SPADE, HEART, CLUB, DIAMOND)
    }
}

/**
 * Card rank with 斗地主 ordering: 3 < 4 < ... < K < A < 2 < 小王 < 大王.
 *
 * [value] is the comparison weight. It is also used by the wire encoding
 * `code = value * 10 + suit.ordinal`.
 */
@Serializable
enum class Rank(val value: Int, val label: String) {
    THREE(3, "3"),
    FOUR(4, "4"),
    FIVE(5, "5"),
    SIX(6, "6"),
    SEVEN(7, "7"),
    EIGHT(8, "8"),
    NINE(9, "9"),
    TEN(10, "10"),
    JACK(11, "J"),
    QUEEN(12, "Q"),
    KING(13, "K"),
    ACE(14, "A"),
    TWO(15, "2"),
    SMALL_JOKER(16, "小王"),
    BIG_JOKER(17, "大王"),
    ;

    /** True for 小王 / 大王. */
    val isJoker: Boolean get() = this == SMALL_JOKER || this == BIG_JOKER

    /** True for ranks that may appear in straights (3..A). */
    val isChainable: Boolean get() = value in 3..14

    companion object {
        /** All ranks in ascending order. */
        fun ascending(): List<Rank> = entries.sortedBy { it.value }

        /** Ordinary ranks only (no jokers). */
        fun normal(): List<Rank> = entries.filterNot { it.isJoker }.sortedBy { it.value }

        /** Resolve a rank by its [value]. */
        fun fromValue(value: Int): Rank =
            entries.firstOrNull { it.value == value }
                ?: throw IllegalArgumentException("Unknown rank value: $value")
    }
}

/**
 * A single playable card.
 *
 * Wire format: `rank.value * 10 + suit.ordinal` (e.g. ♠3 = 30, 大王 = 174).
 */
@Serializable
data class Card(val rank: Rank, val suit: Suit) : Comparable<Card> {

    /** Stable integer encoding used by network payloads and save files. */
    val code: Int get() = rank.value * 10 + suit.ordinal

    val isJoker: Boolean get() = rank.isJoker

    override fun compareTo(other: Card): Int =
        compareValuesBy(this, other, { it.rank.value }, { it.suit.ordinal })

    override fun toString(): String = if (isJoker) rank.label else "${suit.symbol}${rank.label}"

    companion object {
        /** Decode a [code] produced by [Card.code]. */
        fun decode(code: Int): Card {
            val rank = Rank.fromValue(code / 10)
            val suitIndex = code % 10
            require(suitIndex in Suit.entries.indices) { "Unknown suit index: $suitIndex" }
            val suit = Suit.entries[suitIndex]
            require(!rank.isJoker || suit == Suit.JOKER) { "Joker must use Suit.JOKER" }
            return Card(rank, suit)
        }

        /** The full 54-card deck. */
        fun deck(): List<Card> = buildList(54) {
            for (suit in Suit.normalSuits) {
                for (rank in Rank.normal()) {
                    add(Card(rank, suit))
                }
            }
            add(Card(Rank.SMALL_JOKER, Suit.JOKER))
            add(Card(Rank.BIG_JOKER, Suit.JOKER))
        }

        /** Decode a list of wire codes. */
        fun decodeAll(codes: List<Int>): List<Card> = codes.map(::decode)

        /** Encode a list of cards. */
        fun encodeAll(cards: List<Card>): List<Int> = cards.map { it.code }

        /** Convention for a face-down / unknown card shown by the UI. */
        fun back(rank: Rank = Rank.THREE, suit: Suit = Suit.SPADE): Card = Card(rank, suit)
    }
}
