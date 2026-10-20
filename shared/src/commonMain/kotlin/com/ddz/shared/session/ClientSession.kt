package com.ddz.shared.session

import com.ddz.shared.discovery.RoomInfo
import com.ddz.shared.discovery.currentTimeMillis
import com.ddz.shared.game.core.Card
import com.ddz.shared.game.core.GameState
import com.ddz.shared.game.core.Player
import com.ddz.shared.net.AckPayload
import com.ddz.shared.net.Connection
import com.ddz.shared.net.ErrorPayload
import com.ddz.shared.net.KickPayload
import com.ddz.shared.net.Msgs
import com.ddz.shared.net.Msg
import com.ddz.shared.net.MsgType
import com.ddz.shared.net.NetworkCodec
import com.ddz.shared.net.Protocol
import com.ddz.shared.net.ProtocolException
import com.ddz.shared.net.RoomStatePayload
import com.ddz.shared.net.SnapshotPayload
import com.ddz.shared.net.TcpConnector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Client-side session: owns the TCP connection, heartbeats, exponential-backoff
 * reconnection and full-snapshot recovery (`SYNC_REQ(lastKnownSeq)`).
 *
 * The client never runs the reducer; it only renders host snapshots.
 */
class ClientSession(
    private val host: String,
    private val port: Int,
    playerName: String,
    private val avatarSeed: Int = 0,
) : GameSession {

    private val playerName = playerName.trim().ifBlank { "玩家" }
    private val clientScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val connector = TcpConnector()
    private val seqLock = Mutex()

    private val _room = MutableStateFlow<RoomInfo?>(null)
    private val _state = MutableStateFlow<GameState?>(null)
    private val _seats = MutableStateFlow(List(3) { Player(seat = it) })
    private val _status = MutableStateFlow(SessionStatus.CONNECTING)
    private val _mySeat = MutableStateFlow(-1)
    private val _error = MutableStateFlow<String?>(null)

    override val room: StateFlow<RoomInfo?> = _room.asStateFlow()
    override val state: StateFlow<GameState?> = _state.asStateFlow()
    override val seats: StateFlow<List<Player>> = _seats.asStateFlow()
    override val status: StateFlow<SessionStatus> = _status.asStateFlow()
    override val mySeat: StateFlow<Int> = _mySeat.asStateFlow()
    override val error: StateFlow<String?> = _error.asStateFlow()
    override val isHost: Boolean = false

    private var connection: Connection? = null
    private var loopJob: Job? = null
    private var heartbeatJob: Job? = null
    private var lastSeq = 0L
    private var outSeq = 0L
    private var fatal = false
    private var closed = false

    /** Starts the connect/reconnect loop. */
    fun start() {
        if (loopJob != null || closed) return
        loopJob = clientScope.launch { runLoop() }
    }

    override fun requestStart() = Unit

    override fun bid(score: Int) = send { seq -> Msgs.bid(seq, mySeat.value, score) }

    override fun play(cards: List<Card>) = send { seq ->
        Msgs.play(seq, mySeat.value, Card.encodeAll(cards))
    }

    override fun pass() = send { seq -> Msgs.pass(seq, mySeat.value) }

    override fun leave() {
        val conn = connection
        clientScope.launch {
            runCatching {
                val seat = mySeat.value
                if (seat >= 0 && conn != null) {
                    conn.writeFrame(NetworkCodec.encode(Msgs.leave(nextSeq(), seat)))
                }
            }
        }
        close()
    }

    override fun close() {
        if (closed) return
        closed = true
        connection?.close()
        connection = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        loopJob?.cancel()
        loopJob = null
        clientScope.cancel()
        _status.value = SessionStatus.DISCONNECTED
    }

    override fun clearError() {
        _error.value = null
    }

    // ---------------------------------------------------------------- loop

    private suspend fun runLoop() {
        var attempt = 0
        while (currentCoroutineContext().isActive && !fatal) {
            val reconnecting = attempt > 0 || lastSeq > 0
            _status.value = if (reconnecting) SessionStatus.RECONNECTING else SessionStatus.CONNECTING
            var conn: Connection? = null
            try {
                conn = connector.connect(host, port, CONNECT_TIMEOUT_MS)
                connection = conn
                attempt = 0
                conn.writeFrame(
                    NetworkCodec.encode(Msgs.join(nextSeq(), playerName, avatarSeed)),
                )
                if (lastSeq > 0) {
                    conn.writeFrame(NetworkCodec.encode(Msgs.syncReq(nextSeq(), lastSeq)))
                }
                _status.value = SessionStatus.CONNECTED
                heartbeatJob = clientScope.launch { heartbeatLoop() }
                readLoop(conn)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!fatal) _error.value = "连接失败：${e.userMessage()}"
            } finally {
                heartbeatJob?.cancel()
                heartbeatJob = null
                if (connection === conn) connection = null
                conn?.close()
            }
            if (fatal || !currentCoroutineContext().isActive) break
            _status.value = SessionStatus.RECONNECTING
            delay(BACKOFF_MS[attempt.coerceAtMost(BACKOFF_MS.lastIndex)])
            attempt += 1
        }
        if (!fatal) _status.value = SessionStatus.DISCONNECTED
    }

    private suspend fun heartbeatLoop() {
        while (currentCoroutineContext().isActive) {
            delay(Protocol.HEARTBEAT_INTERVAL_MS)
            val conn = connection ?: continue
            runCatching {
                conn.writeFrame(
                    NetworkCodec.encode(Msgs.heartbeat(nextSeq(), currentTimeMillis())),
                )
            }
        }
    }

    private suspend fun readLoop(conn: Connection) {
        while (currentCoroutineContext().isActive) {
            val frame = conn.readFrame() ?: break
            val msg = try {
                NetworkCodec.decodeBody(frame)
            } catch (e: ProtocolException) {
                _error.value = "协议错误：${e.message}"
                break
            }
            handle(msg)
            if (fatal) break
        }
    }

    private fun handle(msg: Msg) {
        when (msg.type) {
            MsgType.ROOM_STATE -> {
                val payload = NetworkCodec.payload<RoomStatePayload>(msg)
                val previousSeat = _mySeat.value
                _room.value = payload.room
                _seats.value = payload.players
                _mySeat.value = payload.yourSeat
                _status.value = SessionStatus.CONNECTED
                if (previousSeat >= 0 && previousSeat != payload.yourSeat) {
                    _error.value = "座位已调整到 ${payload.yourSeat + 1} 号"
                }
            }

            MsgType.SNAPSHOT -> {
                val payload = NetworkCodec.payload<SnapshotPayload>(msg)
                if (payload.seq > lastSeq) {
                    lastSeq = payload.seq
                    _state.value = payload.state
                    _seats.value = payload.state.players
                    _status.value = SessionStatus.CONNECTED
                }
            }

            MsgType.ACK -> {
                val payload = NetworkCodec.payload<AckPayload>(msg)
                if (payload.seq > lastSeq) lastSeq = payload.seq
            }

            MsgType.ERROR -> {
                val payload = NetworkCodec.payload<ErrorPayload>(msg)
                _error.value = payload.message
                if (payload.code in FATAL_CODES) {
                    fatal = true
                    _status.value = SessionStatus.ERROR
                }
            }

            MsgType.KICK -> {
                _error.value = NetworkCodec.payload<KickPayload>(msg).reason
                fatal = true
                _status.value = SessionStatus.ERROR
            }

            MsgType.HEARTBEAT, MsgType.DEAL, MsgType.JOIN, MsgType.HELLO,
            MsgType.BID, MsgType.PLAY, MsgType.PASS, MsgType.LEAVE, MsgType.SYNC_REQ -> Unit
        }
    }

    private fun send(build: (Long) -> Msg) {
        if (closed) return
        clientScope.launch {
            val conn = connection ?: run {
                _error.value = if (_status.value == SessionStatus.RECONNECTING) "正在重连，请稍候" else "尚未连接"
                return@launch
            }
            runCatching {
                conn.writeFrame(NetworkCodec.encode(build(nextSeq())))
            }.onFailure { _error.value = "发送失败：${it.userMessage()}" }
        }
    }

    private suspend fun nextSeq(): Long = seqLock.withLock { ++outSeq }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 6_000L
        val BACKOFF_MS = longArrayOf(500L, 1_000L, 2_000L, 4_000L, 8_000L)
        val FATAL_CODES = setOf("VERSION_MISMATCH", "ROOM_FULL", "BAD_HANDSHAKE", "BAD_NAME")
    }
}
