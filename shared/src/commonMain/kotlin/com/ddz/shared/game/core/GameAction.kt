package com.ddz.shared.game.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Every legal mutation of [GameState]. */
@Serializable
sealed interface GameAction {

    /** Occupy an empty seat while the table is in [Phase.WAITING]. */
    @Serializable
    @SerialName("join")
    data class JoinSeat(
        val seat: Int,
        val name: String,
        val avatarSeed: Int = 0,
        val isAi: Boolean = false,
    ) : GameAction

    /** Release a seat while the table is in [Phase.WAITING]. */
    @Serializable
    @SerialName("leave")
    data class LeaveSeat(val seat: Int) : GameAction

    /** Mark a seat connected / disconnected (host side). */
    @Serializable
    @SerialName("online")
    data class SetOnline(val seat: Int, val online: Boolean) : GameAction

    /** Shuffle and deal: WAITING -> BIDDING. */
    @Serializable
    @SerialName("deal")
    data class Deal(val seed: Long) : GameAction

    /** 叫分: score 0 means pass, 1..3 is a bid. */
    @Serializable
    @SerialName("bid")
    data class Bid(val seat: Int, val score: Int) : GameAction

    /** Play a legal hand. */
    @Serializable
    @SerialName("play")
    data class Play(val seat: Int, val cards: List<Card>) : GameAction

    /** Pass on the current hand. */
    @Serializable
    @SerialName("pass")
    data class Pass(val seat: Int) : GameAction

    /** Rule-level fallback when a seat does not answer in time. */
    @Serializable
    @SerialName("timeout")
    data class Timeout(val seat: Int) : GameAction

    /** SETTLING -> WAITING, keeping the seated players. */
    @Serializable
    @SerialName("restart")
    data object Restart : GameAction
}

/** Thrown when an action is illegal for the current state. */
class IllegalActionException(
    val code: String,
    message: String,
) : IllegalArgumentException(message)
