package com.ddz.shared.session

import com.ddz.shared.ai.AiStrategy
import com.ddz.shared.game.GameEngine
import com.ddz.shared.game.core.GameAction
import com.ddz.shared.game.core.GameState
import com.ddz.shared.game.core.Phase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Drives AI / offline seats. One collector watches the authoritative state and,
 * when the active seat is AI-controlled or disconnected, waits a short "thinking"
 * delay and injects the strategy's action.
 */
class AutoPlayController(
    private val engine: GameEngine,
    private val strategy: AiStrategy,
    private val scope: CoroutineScope,
    private val delayMs: Long = 550L,
    private val shouldAuto: (GameState) -> Boolean,
) {
    private var job: Job? = null

    fun start() {
        if (job != null) return
        job = scope.launch {
            engine.state.collectLatest { snapshot ->
                if (snapshot.phase != Phase.BIDDING && snapshot.phase != Phase.PLAYING) return@collectLatest
                if (!shouldAuto(snapshot)) return@collectLatest
                val seat = snapshot.currentSeat
                delay(delayMs)
                val current = engine.snapshot()
                if (current.seq != snapshot.seq || !shouldAuto(current)) return@collectLatest
                val action = withTimeoutOrNull(STRATEGY_TIMEOUT_MS) {
                    runCatching { strategy.decide(current, seat) }.getOrElse {
                        GameAction.Timeout(seat)
                    }
                } ?: GameAction.Timeout(seat)
                runCatching { engine.dispatch(action) }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private companion object {
        const val STRATEGY_TIMEOUT_MS = 1_600L
    }
}
