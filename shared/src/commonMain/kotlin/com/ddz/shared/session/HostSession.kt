package com.ddz.shared.session

import com.ddz.shared.ai.AiStrategy
import com.ddz.shared.ai.HeuristicAi
import com.ddz.shared.discovery.RoomInfo
import com.ddz.shared.discovery.currentTimeMillis
import com.ddz.shared.discovery.deviceName
import com.ddz.shared.discovery.localIpAddresses
import com.ddz.shared.game.GameEngine
import com.ddz.shared.game.core.Card
import com.ddz.shared.game.core.GameAction
import com.ddz.shared.game.core.GameMode
import com.ddz.shared.game.core.GameState
import com.ddz.shared.game.core.IllegalActionException
import com.ddz.shared.game.core.Phase
import com.ddz.shared.game.core.Player
import com.ddz.shared.net.BidPayload
import com.ddz.shared.net.Connection
import com.ddz.shared.net.HelloPayload
import com.ddz.shared.net.JoinPayload
import com.ddz.shared.net.Msg
import com.ddz.shared.net.MsgType
import com.ddz.shared.net.Msgs
import com.ddz.shared.net.NetworkCodec
import com.ddz.shared.net.PassPayload
import com.ddz.shared.net.PlayPayload
import com.ddz.shared.net.Protocol
import com.ddz.shared.net.ProtocolException
import com.ddz.shared.net.SyncReqPayload
import com.ddz.shared.net.TcpServer
import com.ddz.shared.session.SessionStatus.CONNECTED
import com.ddz.shared.session.SessionStatus.DISCONNECTED
import com.ddz.shared.session.SessionStatus.ERROR
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

/**
 * Authoritative host. Owns the [GameEngine], accepts TCP clients, validates every
 * intent against the seat bound to its connection, and broadcasts full snapshots.
 * Offline / AI seats are driven by [AutoPlayController].
 */
class HostSession(
    private val hostName: String,
    private val scope: CoroutineScope,
    private val requestedPort: Int = Protocol.DEFAULT_PORT,
    private val strategy: AiStrategy = HeuristicAi(),
    private val mode: GameMode = GameMode.CLASSIC,
) : GameSession {

    private val engine = GameEngine(GameState.fresh(mode))

    private val _room = MutableStateFlow<RoomInfo?>(null)
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

    private var server: TcpServer? = null
    private var acceptJob: Job? = null
    private var watchJob: Job? = null
    private var watchdogJob: Job? = null
    private var autoPlay: AutoPlayController? = null
    private var started = false
    private var closed = false

    private val clients = mutableMapOf<Int, ClientHandle>()
    private val clientsLock = Mutex()
    private val seatLock = Mutex()
    private val seqLock = Mutex()
    private var outSeq = 0L

    /** Binds the port, seats the host and starts accepting clients. */
    suspend fun start(): RoomInfo {
        if (started) return _room.value ?: error("HostSession 尚未启动")
        started = true
        engine.dispatch(GameAction.JoinSeat(0, hostName.trim().ifBlank { "房主" }, avatarSeed = 1))
        val bound = bindServer(requestedPort)
        _mySeat.value = 0
        _status.value = CONNECTED
        val ip = localIpAddresses().firstOrNull { !it.startsWith("127.") && it != "0.0.0.0" }
            ?: localIpAddresses().firstOrNull()
            ?: "127.0.0.1"
        val info = RoomInfo(
            name = "${deviceName().take(20)} 的房间",
            hostIp = ip,
            port = bound.boundPort,
            playerCount = 1,
            needsPassword = false,
            protocol = Protocol.VERSION,
            state = "waiting",
            mode = mode.name.lowercase(),
            updatedAt = currentTimeMillis(),
        )
        _room.value = info

        watchJob = scope.launch {
            engine.state.collect { snapshot ->
                val view = snapshot.redactedFor(0)
                _state.value = view
                _seats.value = view.players
                _room.value = roomNow(snapshot)
                broadcast(snapshot)
            }
        }
        acceptJob = scope.launch { acceptLoop() }
        watchdogJob = scope.launch { heartbeatWatchdog() }
        autoPlay = AutoPlayController(
            engine = engine,
            strategy = strategy,
            scope = scope,
            shouldAuto = { snapshot ->
                (snapshot.phase == Phase.BIDDING || snapshot.phase == Phase.PLAYING) &&
                    (snapshot.player(snapshot.currentSeat).isAi ||
                        !snapshot.player(snapshot.currentSeat).online)
            },
        ).also { it.start() }
        return info
    }

    override fun requestStart() {
        if (closed) return
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

    override fun bid(score: Int) = dispatchAsHost(GameAction.Bid(0, score))

    override fun play(cards: List<Card>) = dispatchAsHost(GameAction.Play(0, cards))

    override fun pass() = dispatchAsHost(GameAction.Pass(0))

    /** Host-only: disconnect a seat and release it (only while waiting). */
    fun kick(seat: Int, reason: String = "房主将你移出房间") {
        if (seat == 0) return
        scope.launch {
            val handle = removeClient(seat) ?: return@launch
            runCatching { handle.send(NetworkCodec.encode(Msgs.kick(nextSeq(), reason))) }
            handle.connection.close()
            if (engine.snapshot().phase == Phase.WAITING) {
                runCatching { engine.dispatch(GameAction.LeaveSeat(seat)) }
            }
        }
    }

    fun shutdown() {
        close()
    }

    override fun leave() {
        close()
        _status.value = DISCONNECTED
    }

    override fun close() {
        if (closed) return
        closed = true
        acceptJob?.cancel()
        watchJob?.cancel()
        watchdogJob?.cancel()
        autoPlay?.stop()
        autoPlay = null
        server?.close()
        server = null
        scope.launch {
            val handles = clientsLock.withLock {
                val copy = clients.values.toList()
                clients.clear()
                copy
            }
            handles.forEach { it.connection.close() }
        }
        _status.value = DISCONNECTED
    }

    override fun clearError() {
        _error.value = null
    }

    // ---------------------------------------------------------------- networking

    private fun bindServer(preferred: Int): TcpServer {
        var last: Throwable? = null
        for (port in preferred until preferred + 10) {
            try {
                return TcpServer(port).also { server = it }
            } catch (e: Throwable) {
                last = e
            }
        }
        _status.value = ERROR
        throw IllegalStateException("端口 $preferred..${preferred + 9} 均被占用", last)
    }

    private suspend fun acceptLoop() {
        val srv = server ?: return
        while (true) {
            val connection = try {
                srv.accept()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                break
            }
            scope.launch { handleConnection(connection) }
        }
    }

    private suspend fun handleConnection(connection: Connection) {
        var handle: ClientHandle? = null
        try {
            val first = withTimeoutOrNull(HANDSHAKE_TIMEOUT_MS) { connection.readFrame() }
            if (first == null) {
                connection.close()
                return
            }
            val msg = try {
                NetworkCodec.decodeBody(first)
            } catch (e: ProtocolException) {
                sendAndClose(connection, e.code, e.message ?: "协议错误")
                return
            }
            val join = when (msg.type) {
                MsgType.JOIN -> NetworkCodec.payload<JoinPayload>(msg)
                MsgType.HELLO -> NetworkCodec.payload<HelloPayload>(msg).let {
                    JoinPayload(it.playerName, it.avatarSeed, it.protocol)
                }
                else -> {
                    sendAndClose(connection, "BAD_HANDSHAKE", "首帧必须是 JOIN")
                    return
                }
            }
            if (join.protocol != Protocol.VERSION) {
                sendAndClose(
                    connection,
                    "VERSION_MISMATCH",
                    "协议版本不匹配（房间 ${Protocol.VERSION} / 客户端 ${join.protocol}）",
                )
                return
            }
            val playerName = join.playerName.trim().take(16)
            if (playerName.isEmpty()) {
                sendAndClose(connection, "BAD_NAME", "昵称不能为空")
                return
            }
            // Serialise seat assignment: two phones joining at the same instant must
            // not both see the same free seat and race into SEAT_TAKEN.
            val seat = seatLock.withLock { assignSeat(playerName, join.avatarSeed) }
            if (seat == null) {
                sendAndClose(connection, "ROOM_FULL", "房间已满或牌局已开始")
                return
            }
            val client = ClientHandle(seat, connection)
            addClient(client)
            handle = client

            val initial = engine.snapshot()
            client.send(
                NetworkCodec.encode(
                    Msgs.roomState(nextSeq(), roomNow(initial), publicPlayers(initial), seat, 0, initial.phase.name),
                ),
            )
            if (initial.phase != Phase.WAITING) {
                client.send(NetworkCodec.encode(Msgs.snapshot(nextSeq(), initial.redactedFor(seat))))
            }

            while (true) {
                val frame = connection.readFrame() ?: break
                client.touch()
                val incoming = try {
                    NetworkCodec.decodeBody(frame)
                } catch (e: ProtocolException) {
                    client.sendError(e.code, e.message ?: "协议错误")
                    break
                }
                val keepGoing = onClientMessage(client, incoming)
                if (!keepGoing) break
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Connection reset / timeout: the finally block marks the seat offline.
        } finally {
            val finished = handle
            if (finished != null) {
                removeClient(finished)
                val state = engine.snapshot()
                val player = state.players.firstOrNull { it.seat == finished.seat }
                if (player != null && player.isSeated && player.online) {
                    runCatching { engine.dispatch(GameAction.SetOnline(finished.seat, false)) }
                }
            }
        }
    }

    private suspend fun onClientMessage(client: ClientHandle, msg: Msg): Boolean {
        try {
            when (msg.type) {
                MsgType.BID -> {
                    val payload = NetworkCodec.payload<BidPayload>(msg)
                    requireSeat(client, payload.seat)
                    engine.dispatch(GameAction.Bid(payload.seat, payload.score))
                }

                MsgType.PLAY -> {
                    val payload = NetworkCodec.payload<PlayPayload>(msg)
                    requireSeat(client, payload.seat)
                    engine.dispatch(GameAction.Play(payload.seat, Card.decodeAll(payload.cards)))
                }

                MsgType.PASS -> {
                    val payload = NetworkCodec.payload<PassPayload>(msg)
                    requireSeat(client, payload.seat)
                    engine.dispatch(GameAction.Pass(payload.seat))
                }

                MsgType.SYNC_REQ -> {
                    val payload = NetworkCodec.payload<SyncReqPayload>(msg)
                    val state = engine.snapshot()
                    if (state.seq <= payload.lastKnownSeq) {
                        client.send(NetworkCodec.encode(Msgs.ack(nextSeq(), state.seq)))
                    } else {
                        client.send(
                            NetworkCodec.encode(Msgs.snapshot(nextSeq(), state.redactedFor(client.seat))),
                        )
                    }
                }

                MsgType.HEARTBEAT -> client.touch()

                MsgType.LEAVE -> {
                    val state = engine.snapshot()
                    if (state.phase == Phase.WAITING) {
                        runCatching { engine.dispatch(GameAction.LeaveSeat(client.seat)) }
                    } else {
                        runCatching { engine.dispatch(GameAction.SetOnline(client.seat, false)) }
                    }
                    client.connection.close()
                    return false
                }

                else -> Unit
            }
        } catch (e: IllegalActionException) {
            client.sendError(e.code, e.message ?: "非法操作")
        } catch (e: ProtocolException) {
            client.sendError(e.code, e.message ?: "协议错误")
            return false
        }
        return true
    }

    private suspend fun requireSeat(client: ClientHandle, seat: Int) {
        if (seat != client.seat) {
            throw IllegalActionException("SEAT_MISMATCH", "不能操作座位 ${seat + 1} 的牌")
        }
    }

    private suspend fun assignSeat(name: String, avatarSeed: Int): Int? {
        val state = engine.snapshot()
        val existing = state.players.firstOrNull { it.seat != 0 && it.isSeated && it.name == name }
        if (existing != null) {
            if (existing.online) return null
            runCatching { engine.dispatch(GameAction.SetOnline(existing.seat, true)) }
            return existing.seat
        }
        if (state.phase != Phase.WAITING) return null
        val free = state.players.filter { it.seat != 0 && !it.isSeated }.minByOrNull { it.seat }
            ?: return null
        return runCatching {
            engine.dispatch(GameAction.JoinSeat(free.seat, name, avatarSeed))
            free.seat
        }.getOrNull()
    }

    // ---------------------------------------------------------------- broadcast

    private suspend fun broadcast(state: GameState) {
        val handles = clientsLock.withLock { clients.values.toList() }
        if (handles.isEmpty()) return
        val room = roomNow(state)
        val players = publicPlayers(state)
        for (client in handles) {
            runCatching {
                client.send(
                    NetworkCodec.encode(
                        Msgs.roomState(nextSeq(), room, players, client.seat, 0, state.phase.name),
                    ),
                )
                client.send(
                    NetworkCodec.encode(Msgs.snapshot(nextSeq(), state.redactedFor(client.seat))),
                )
            }
        }
    }

    private suspend fun heartbeatWatchdog() {
        while (true) {
            delay(PROTOCOL_WATCH_INTERVAL_MS)
            val now = currentTimeMillis()
            val stale = clientsLock.withLock {
                clients.values.filter { now - it.lastSeen.value > Protocol.HEARTBEAT_TIMEOUT_MS }
            }
            for (client in stale) {
                removeClient(client)
                client.connection.close()
                val state = engine.snapshot()
                val player = state.players.firstOrNull { it.seat == client.seat }
                if (player != null && player.online) {
                    runCatching { engine.dispatch(GameAction.SetOnline(client.seat, false)) }
                }
            }
        }
    }

    private fun publicPlayers(state: GameState): List<Player> =
        state.players.map { it.copy(hand = emptyList()) }

    private fun roomNow(state: GameState): RoomInfo = (
        _room.value ?: RoomInfo(
            name = "斗地主房间",
            hostIp = "127.0.0.1",
            port = requestedPort,
        )
        ).copy(
        playerCount = state.players.count { it.isSeated },
        state = state.phase.name.lowercase(),
        updatedAt = currentTimeMillis(),
    )

    private suspend fun addClient(client: ClientHandle) {
        val previous = clientsLock.withLock { clients.put(client.seat, client) }
        previous?.connection?.close()
    }

    private suspend fun removeClient(client: ClientHandle): ClientHandle? = clientsLock.withLock {
        if (clients[client.seat] === client) clients.remove(client.seat) else null
    }

    private suspend fun removeClient(seat: Int): ClientHandle? = clientsLock.withLock {
        clients.remove(seat)
    }

    private suspend fun nextSeq(): Long = seqLock.withLock { ++outSeq }

    private suspend fun sendAndClose(connection: Connection, code: String, message: String) {
        runCatching {
            connection.writeFrame(
                NetworkCodec.encode(Msgs.error(nextSeq(), code, message)),
            )
        }
        connection.close()
    }

    private fun dispatchAsHost(action: GameAction) {
        scope.launch {
            runCatching { engine.dispatch(action) }
                .onFailure { _error.value = it.userMessage() }
        }
    }

    private class ClientHandle(
        val seat: Int,
        val connection: Connection,
    ) {
        private val _lastSeen = MutableStateFlow(currentTimeMillis())
        val lastSeen: StateFlow<Long> = _lastSeen.asStateFlow()
        private val writeLock = Mutex()

        fun touch() {
            _lastSeen.value = currentTimeMillis()
        }

        suspend fun send(frame: ByteArray) {
            writeLock.withLock { connection.writeFrame(frame) }
        }

        suspend fun sendError(code: String, message: String) {
            runCatching { send(NetworkCodec.encode(Msgs.error(0, code, message))) }
        }
    }

    private companion object {
        const val HANDSHAKE_TIMEOUT_MS = 10_000L
        const val PROTOCOL_WATCH_INTERVAL_MS = 5_000L
    }
}
