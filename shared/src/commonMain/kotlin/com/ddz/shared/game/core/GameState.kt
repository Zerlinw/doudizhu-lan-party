package com.ddz.shared.game.core

import kotlinx.serialization.Serializable

/** Lifecycle of a single table. */
@Serializable
enum class Phase {
    /** Players are taking seats; no cards dealt. */
    WAITING,

    /** Reserved for the dealing animation; the reducer jumps straight to [BIDDING]. */
    DEALING,

    /** 叫分 phase (1/2/3 points). */
    BIDDING,

    /** 出牌 phase. */
    PLAYING,

    /** A player emptied their hand; scores are final for this round. */
    SETTLING,
}

/** One of the three fixed seats. */
@Serializable
data class Player(
    val seat: Int,
    val name: String = "",
    val avatarSeed: Int = 0,
    val hand: List<Card> = emptyList(),
    val cardCount: Int = 0,
    val isLandlord: Boolean = false,
    val online: Boolean = true,
    val isAi: Boolean = false,
    val bid: Int? = null,
) {
    /** A seat is taken when a human or an AI has claimed it. */
    val isSeated: Boolean get() = name.isNotBlank() || isAi
}

/** Final scoring for a round. All deltas sum to zero. */
@Serializable
data class Settlement(
    val winnerSeat: Int,
    val landlordSeat: Int,
    val landlordWin: Boolean,
    val baseBid: Int,
    val multiplier: Int,
    val deltas: List<Int>,
    val spring: Boolean,
    val antiSpring: Boolean,
    val reason: String,
)

/**
 * Immutable authoritative game state. Every transition goes through
 * [GameRules.reduce]; `seq` is bumped exactly once per accepted action and is
 * the synchronisation version used by snapshots.
 */
@Serializable
data class GameState(
    val phase: Phase = Phase.WAITING,
    val seq: Long = 0L,
    val round: Int = 0,
    val players: List<Player> = (0..2).map { Player(seat = it) },
    val bottomCards: List<Card> = emptyList(),
    val landlordSeat: Int? = null,
    val currentSeat: Int = 0,
    val lastHand: Hand? = null,
    val lastHandSeat: Int? = null,
    val passStreak: Int = 0,
    val multiplier: Int = 1,
    val highestBid: Int = 0,
    val highestBidder: Int? = null,
    val bidsMade: Int = 0,
    val bombCount: Int = 0,
    val playCounts: List<Int> = listOf(0, 0, 0),
    val winnerSeat: Int? = null,
    val settlement: Settlement? = null,
    val seed: Long = 0L,
    val log: List<String> = emptyList(),
) {

    fun player(seat: Int): Player =
        players.firstOrNull { it.seat == seat } ?: error("No seat $seat")

    val activePlayer: Player? get() = players.firstOrNull { it.seat == currentSeat }

    val allSeated: Boolean get() = players.size == 3 && players.all { it.isSeated }

    /** The three bottom cards are public once a landlord owns them. */
    val bottomVisible: Boolean get() = phase == Phase.PLAYING || phase == Phase.SETTLING

    fun isLandlord(seat: Int): Boolean = landlordSeat == seat

    /** True when both seats are farmers (and a landlord exists). */
    fun areTeammates(a: Int, b: Int): Boolean =
        landlordSeat != null && a != landlordSeat && b != landlordSeat

    fun nextSeat(seat: Int): Int = (seat + 1) % 3

    fun withPlayer(seat: Int, block: (Player) -> Player): GameState =
        copy(players = players.map { if (it.seat == seat) block(it) else it })

    fun log(line: String): GameState = copy(log = (log + line).takeLast(60))

    /**
     * Produces the view a client is allowed to see: only the viewer's own hand
     * is included. Card counts stay public, and the bottom is hidden until a
     * landlord has been chosen.
     */
    fun redactedFor(viewerSeat: Int): GameState = copy(
        players = players.map { p ->
            if (p.seat == viewerSeat) p else p.copy(hand = emptyList())
        },
        bottomCards = if (bottomVisible) bottomCards else emptyList(),
    )

    companion object {
        /** A fresh table with three empty seats. */
        fun fresh(): GameState = GameState()
    }
}
