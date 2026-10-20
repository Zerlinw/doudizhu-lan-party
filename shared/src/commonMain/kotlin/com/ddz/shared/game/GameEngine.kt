package com.ddz.shared.game

import com.ddz.shared.game.core.GameAction
import com.ddz.shared.game.core.GameRules
import com.ddz.shared.game.core.GameState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Single-writer state container. The only way to change [state] is [dispatch],
 * which delegates to the pure [GameRules.reduce]; every accepted action bumps
 * `seq` exactly once, giving clients a monotonic snapshot version.
 */
class GameEngine(initial: GameState = GameState.fresh()) {

    private val _state = MutableStateFlow(initial)
    val state: StateFlow<GameState> = _state.asStateFlow()

    private val writeLock = Mutex()

    /** Applies [action] atomically and returns the new state. Throws on illegal actions. */
    suspend fun dispatch(action: GameAction): GameState = writeLock.withLock {
        val next = GameRules.reduce(_state.value, action)
        _state.value = next
        next
    }

    /** Non-suspending view of the authoritative state. */
    fun snapshot(): GameState = _state.value
}
