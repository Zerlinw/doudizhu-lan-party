package com.ddz.shared.session

import com.ddz.shared.discovery.RoomInfo
import com.ddz.shared.game.core.Card
import com.ddz.shared.game.core.GameState
import com.ddz.shared.game.core.Player
import kotlinx.coroutines.flow.StateFlow

/** Connection lifecycle shown by the lobby / table UI. */
enum class SessionStatus {
    IDLE,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    DISCONNECTED,
    ERROR,
}

/**
 * UI-facing game session. Host and client expose the same reactive surface, so
 * Compose screens never know whether the authority is local or remote.
 */
interface GameSession {

    val room: StateFlow<RoomInfo?>
    val state: StateFlow<GameState?>
    val seats: StateFlow<List<Player>>
    val status: StateFlow<SessionStatus>
    val mySeat: StateFlow<Int>
    val error: StateFlow<String?>

    val isHost: Boolean

    /** Host only: deal (or deal again after settlement). */
    fun requestStart()

    fun bid(score: Int)
    fun play(cards: List<Card>)
    fun pass()
    fun leave()
    fun close()
    fun clearError()
}

/** Format a throwable for the snackbar-style error banner. */
internal fun Throwable.userMessage(): String =
    message?.takeIf { it.isNotBlank() } ?: this::class.simpleName ?: "未知错误"
