package com.ddz.shared.session

import com.ddz.shared.ai.AiStrategy
import com.ddz.shared.ai.HeuristicAi
import com.ddz.shared.discovery.RoomInfo
import com.ddz.shared.game.GameEngine
import com.ddz.shared.game.core.Card
import com.ddz.shared.game.core.GameAction
import com.ddz.shared.game.core.GameState
import com.ddz.shared.game.core.Phase
import com.ddz.shared.game.core.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Offline practice: the local device is the authority and two heuristic AIs fill
 * seats 1 and 2. No sockets, but the same reducer and state flow as online play.
 */
class LocalSession(
    playerName: String,
    private val scope: CoroutineScope,
    seed: Long = Random.nextLong(),
    strategy: AiStrategy = HeuristicAi(),
) : GameSession {

    private val engine = GameEngine()
    private val name = playerName.trim().ifBlank { "我" }

    private val _room = MutableStateFlow<RoomInfo?>(
        RoomInfo(name = "单机练习", hostIp = "127.0.0.1", port = 0, playerCount = 3),
    )
    private val _state = MutableStateFlow<GameState?>(null)
    private val _seats = MutableStateFlow(List(3) { Player(seat = it) })
    private val _status = MutableStateFlow(SessionStatus.IDLE)
    private val _mySeat = MutableStateFlow(0)
    private val _error = MutableStateFlow<String?>(null)

    override val room: StateFlow<RoomInfo?> = _room.asStateFlow()
    override val state: StateFlow<GameState?> = _state.asStateFlow()
    override val seats: StateFlow<List<Player>> = _seats.asStateFlow()
    override val status: StateFlow<SessionStatus> = _status.asStateFlow()
    override val mySeat: StateFlow<Int> = _mySeat.asStateFlow()
    override val error: StateFlow<String?> = _error.asStateFlow()
    override val isHost: Boolean = true

    private var watchJob: Job? = null
    private var autoPlay: AutoPlayController? = null
    private var closed = false

    init {
        watchJob = scope.launch {
            engine.state.collect { snapshot ->
                val view = snapshot.redactedFor(0)
                _state.value = view
                _seats.value = view.players
                _room.value = _room.value?.copy(
                    playerCount = snapshot.players.count { it.isSeated },
                    state = snapshot.phase.name.lowercase(),
                )
                _status.value = SessionStatus.CONNECTED
            }
        }
        autoPlay = AutoPlayController(
            engine = engine,
            strategy = strategy,
            scope = scope,
            shouldAuto = { snapshot ->
                (snapshot.phase == Phase.BIDDING || snapshot.phase == Phase.PLAYING) &&
                    snapshot.player(snapshot.currentSeat).isAi
            },
        ).also { it.start() }

        scope.launch {
            runCatching {
                engine.dispatch(GameAction.JoinSeat(0, name, avatarSeed = 7))
                engine.dispatch(GameAction.JoinSeat(1, "阿豆", avatarSeed = 2, isAi = true))
                engine.dispatch(GameAction.JoinSeat(2, "小九", avatarSeed = 3, isAi = true))
                engine.dispatch(GameAction.Deal(seed))
            }.onFailure { _error.value = it.userMessage() }
        }
    }

    override fun requestStart() {
        scope.launch {
            runCatching {
                val current = engine.snapshot()
                if (current.phase == Phase.SETTLING) engine.dispatch(GameAction.Restart)
                if (!engine.snapshot().allSeated) {
                    _error.value = "需要 3 位玩家才能开始"
                    return@runCatching
                }
                if (engine.snapshot().phase == Phase.WAITING) {
                    engine.dispatch(GameAction.Deal(Random.nextLong()))
                }
            }.onFailure { _error.value = it.userMessage() }
        }
    }

    override fun bid(score: Int) = dispatch(GameAction.Bid(0, score))

    override fun play(cards: List<Card>) = dispatch(GameAction.Play(0, cards))

    override fun pass() = dispatch(GameAction.Pass(0))

    override fun leave() {
        close()
        _status.value = SessionStatus.DISCONNECTED
    }

    override fun close() {
        if (closed) return
        closed = true
        watchJob?.cancel()
        watchJob = null
        autoPlay?.stop()
        autoPlay = null
    }

    override fun clearError() {
        _error.value = null
    }

    private fun dispatch(action: GameAction) {
        scope.launch {
            runCatching { engine.dispatch(action) }
                .onFailure { _error.value = it.userMessage() }
        }
    }
}
