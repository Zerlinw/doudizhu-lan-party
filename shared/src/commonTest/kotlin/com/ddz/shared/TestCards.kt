package com.ddz.shared

import com.ddz.shared.game.core.Card
import com.ddz.shared.game.core.Hand
import com.ddz.shared.game.core.HandParser
import com.ddz.shared.game.core.Phase
import com.ddz.shared.game.core.Player
import com.ddz.shared.game.core.Rank
import com.ddz.shared.game.core.Suit
import com.ddz.shared.game.core.GameState

/** Compact card notation for tests: "3s 4h Td Jc Qs Kh Ad 2c X D". */
object TestCards {

    fun parse(token: String): Card {
        val t = token.trim()
        require(t.isNotEmpty()) { "empty card token" }
        return when (t.uppercase()) {
            "X", "SJ" -> Card(Rank.SMALL_JOKER, Suit.JOKER)
            "D", "BJ" -> Card(Rank.BIG_JOKER, Suit.JOKER)
            else -> {
                val rank = when (t[0].uppercaseChar()) {
                    '3' -> Rank.THREE
                    '4' -> Rank.FOUR
                    '5' -> Rank.FIVE
                    '6' -> Rank.SIX
                    '7' -> Rank.SEVEN
                    '8' -> Rank.EIGHT
                    '9' -> Rank.NINE
                    'T' -> Rank.TEN
                    'J' -> Rank.JACK
                    'Q' -> Rank.QUEEN
                    'K' -> Rank.KING
                    'A' -> Rank.ACE
                    '2' -> Rank.TWO
                    else -> error("bad rank in $t")
                }
                val suit = when (t[1].lowercaseChar()) {
                    's' -> Suit.SPADE
                    'h' -> Suit.HEART
                    'c' -> Suit.CLUB
                    'd' -> Suit.DIAMOND
                    else -> error("bad suit in $t")
                }
                Card(rank, suit)
            }
        }
    }

    /** Parse a whitespace-separated hand. */
    fun of(spec: String): List<Card> =
        spec.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.map(::parse)

    /** Parse and assert a valid combination. */
    fun hand(spec: String): Hand =
        HandParser.parse(of(spec)) ?: error("not a legal hand: $spec")

    fun player(seat: Int, spec: String, landlord: Boolean = false, name: String = "P$seat"): Player {
        val cards = of(spec)
        return Player(
            seat = seat,
            name = name,
            hand = cards,
            cardCount = cards.size,
            isLandlord = landlord,
            online = true,
        )
    }

    /**
     * Build a PLAYING state from explicit hands. The bottom is empty and the
     * landlord marker is derived from [landlordSeat].
     */
    fun playing(
        seat0: String,
        seat1: String,
        seat2: String,
        landlordSeat: Int = 0,
        currentSeat: Int = landlordSeat,
        lastHand: Hand? = null,
        lastHandSeat: Int? = null,
        multiplier: Int = 1,
        phase: Phase = Phase.PLAYING,
    ): GameState {
        val hands = listOf(seat0, seat1, seat2).map(::of)
        val players = hands.mapIndexed { seat, cards ->
            Player(
                seat = seat,
                name = "P$seat",
                hand = cards,
                cardCount = cards.size,
                isLandlord = seat == landlordSeat,
            )
        }
        return GameState(
            phase = phase,
            players = players,
            landlordSeat = landlordSeat,
            currentSeat = currentSeat,
            lastHand = lastHand,
            lastHandSeat = lastHandSeat,
            multiplier = multiplier,
            highestBid = 1,
        )
    }

    /** A bidding state with three 17-card hands from a deterministic deal. */
    fun biddingState(seed: Long = 42L): GameState {
        var state = GameState.fresh()
        state = com.ddz.shared.game.core.GameRules.reduce(
            state,
            com.ddz.shared.game.core.GameAction.JoinSeat(0, "P0", 1),
        )
        state = com.ddz.shared.game.core.GameRules.reduce(
            state,
            com.ddz.shared.game.core.GameAction.JoinSeat(1, "P1", 2),
        )
        state = com.ddz.shared.game.core.GameRules.reduce(
            state,
            com.ddz.shared.game.core.GameAction.JoinSeat(2, "P2", 3),
        )
        return com.ddz.shared.game.core.GameRules.reduce(
            state,
            com.ddz.shared.game.core.GameAction.Deal(seed),
        )
    }
}
