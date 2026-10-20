package com.ddz.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddz.shared.discovery.DiscoveredRoom
import com.ddz.shared.discovery.RoomDiscovery
import com.ddz.shared.discovery.RoomInfo
import com.ddz.shared.discovery.currentTimeMillis
import com.ddz.shared.discovery.deviceName
import com.ddz.shared.discovery.localIpAddresses
import com.ddz.shared.game.core.Card
import com.ddz.shared.game.core.GameState
import com.ddz.shared.game.core.Phase
import com.ddz.shared.game.core.Player
import com.ddz.shared.net.Protocol
import com.ddz.shared.session.ClientSession
import com.ddz.shared.session.GameSession
import com.ddz.shared.session.HostSession
import com.ddz.shared.session.LocalSession
import com.ddz.shared.session.SessionStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Screen { HOME, JOIN, LOBBY, TABLE }

data class LobbyUiState(
    val screen: Screen = Screen.HOME,
    val playerName: String = "",
    val localIps: List<String> = emptyList(),
    val deviceName: String = "",
    val rooms: List<DiscoveredRoom> = emptyList(),
    val scanning: Boolean = false,
    val status: SessionStatus = SessionStatus.IDLE,
    val room: RoomInfo? = null,
    val players: List<Player> = emptyList(),
    val me: Int = -1,
    val game: GameState? = null,
    val isHost: Boolean = false,
    val isPractice: Boolean = false,
    val busy: Boolean = false,
    val pendingAddress: String = "",
    val error: String? = null,
)

/**
 * Owns the active [GameSession] and the discovery lifecycle. Compose only renders
 * [ui]; rules and networking stay in `:shared`.
 */
class LobbyViewModel : ViewModel() {

    private val deviceLabel = runCatching { deviceName() }.getOrDefault("Android")
    private val ips = runCatching { localIpAddresses() }.getOrDefault(emptyList())

    private val _ui = MutableStateFlow(
        LobbyUiState(
            playerName = deviceLabel.take(10),
            localIps = ips,
            deviceName = deviceLabel,
        ),
    )
    val ui: StateFlow<LobbyUiState> = _ui.asStateFlow()

    private var session: GameSession? = null
    private var observeJob: Job? = null
    private var scanJob: Job? = null
    private var scanner: RoomDiscovery? = null
    private var advertiser: RoomDiscovery? = null
    private var advertisedCount = -1

    fun onNameChange(name: String) {
        _ui.update { it.copy(playerName = name.take(16)) }
    }

    fun showJoin() {
        _ui.update { it.copy(screen = Screen.JOIN, error = null, rooms = emptyList()) }
        beginScan()
    }

    fun backHome() {
        stopScan()
        _ui.update { it.copy(screen = Screen.HOME, error = null) }
    }

    fun createRoom() {
        if (session != null) return
        val name = currentName()
        viewModelScope.launch {
            _ui.update { it.copy(busy = true, error = null) }
            val host = HostSession(name, viewModelScope)
            try {
                val room = host.start()
                session = host
                advertisedCount = 1
                observe(host)
                _ui.update {
                    it.copy(
                        screen = Screen.LOBBY,
                        room = room,
                        isHost = true,
                        isPractice = false,
                        status = SessionStatus.CONNECTED,
                        busy = false,
                        error = null,
                        pendingAddress = "",
                    )
                }
                advertise(room, 1)
            } catch (t: Throwable) {
                runCatching { host.close() }
                _ui.update {
                    it.copy(
                        busy = false,
                        screen = Screen.HOME,
                        error = "创建房间失败：${t.message ?: "端口被占用"}",
                    )
                }
            }
        }
    }

    fun startPractice() {
        if (session != null) return
        val local = LocalSession(currentName(), viewModelScope)
        session = local
        observe(local)
        _ui.update {
            it.copy(
                screen = Screen.TABLE,
                isHost = true,
                isPractice = true,
                status = SessionStatus.CONNECTED,
                error = null,
                busy = false,
                pendingAddress = "",
            )
        }
    }

    fun beginScan() {
        stopScan()
        val discovery = RoomDiscovery()
        scanner = discovery
        _ui.update { it.copy(scanning = true, rooms = emptyList(), error = null) }
        scanJob = viewModelScope.launch {
            val seen = LinkedHashMap<String, DiscoveredRoom>()
            launch {
                delay(7_000)
                if (seen.isEmpty()) {
                    _ui.update { it.copy(error = "暂未发现房间，可让房主告知 IP:端口 手动连接") }
                }
            }
            discovery.discover()
                .catch { error ->
                    _ui.update { it.copy(error = "自动发现不可用：${error.message ?: "NSD 错误"}") }
                }
                .collect { found ->
                    val key = "${found.room.name}@${found.room.hostIp}:${found.room.port}"
                    seen[key] = found
                    val now = currentTimeMillis()
                    val fresh = seen.filterValues { now - it.lastSeen <= 6_000 }
                    seen.clear()
                    seen.putAll(fresh)
                    _ui.update {
                        it.copy(
                            rooms = fresh.values.sortedBy { it.room.name },
                            scanning = true,
                        )
                    }
                }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        scanner?.let { runCatching { it.stop() } }
        scanner = null
        _ui.update { it.copy(scanning = false) }
    }

    fun join(room: DiscoveredRoom) {
        connect(room.room.hostIp, room.room.port)
    }

    fun joinManual(text: String) {
        val parsed = parseEndpoint(text)
        if (parsed == null) {
            _ui.update { it.copy(error = "地址格式应为 IP:端口，例如 192.168.1.8:9527") }
            return
        }
        connect(parsed.first, parsed.second)
    }

    private fun connect(host: String, port: Int) {
        stopScan()
        stopAdvertising()
        val client = ClientSession(host, port, currentName())
        session = client
        observe(client)
        _ui.update {
            it.copy(
                screen = Screen.LOBBY,
                room = null,
                game = null,
                players = emptyList(),
                me = -1,
                isHost = false,
                isPractice = false,
                busy = false,
                status = SessionStatus.CONNECTING,
                pendingAddress = "$host:$port",
                error = null,
            )
        }
        client.start()
    }

    fun startGame() {
        session?.requestStart()
    }

    fun bid(score: Int) {
        session?.bid(score)
    }

    fun play(cards: List<Card>) {
        session?.play(cards)
    }

    fun pass() {
        session?.pass()
    }

    fun kick(seat: Int) {
        (session as? HostSession)?.kick(seat)
    }

    fun clearError() {
        session?.clearError()
        _ui.update { it.copy(error = null) }
    }

    fun leave() {
        stopScan()
        stopAdvertising()
        observeJob?.cancel()
        observeJob = null
        val current = session
        session = null
        runCatching { current?.leave() }
        runCatching { current?.close() }
        _ui.update {
            it.copy(
                screen = Screen.HOME,
                room = null,
                game = null,
                players = emptyList(),
                me = -1,
                status = SessionStatus.IDLE,
                isHost = false,
                isPractice = false,
                busy = false,
                scanning = false,
                rooms = emptyList(),
                pendingAddress = "",
                error = null,
            )
        }
    }

    override fun onCleared() {
        stopScan()
        stopAdvertising()
        observeJob?.cancel()
        runCatching { session?.close() }
        session = null
        super.onCleared()
    }

    private fun currentName(): String =
        _ui.value.playerName.trim().ifBlank { deviceLabel.take(10) }

    private fun observe(target: GameSession) {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            launch {
                target.room.collect { room -> _ui.update { it.copy(room = room) } }
            }
            launch {
                target.state.collect { state ->
                    _ui.update { it.copy(game = state) }
                    if (state != null) {
                        val playing = state.phase == Phase.BIDDING ||
                            state.phase == Phase.PLAYING ||
                            state.phase == Phase.SETTLING
                        _ui.update {
                            it.copy(
                                screen = when {
                                    playing -> Screen.TABLE
                                    it.screen == Screen.TABLE -> Screen.LOBBY
                                    else -> it.screen
                                },
                            )
                        }
                    }
                }
            }
            launch {
                target.seats.collect { seats ->
                    _ui.update { it.copy(players = seats) }
                    maybeAdvertise(seats)
                }
            }
            launch {
                target.status.collect { status -> _ui.update { it.copy(status = status) } }
            }
            launch {
                target.mySeat.collect { seat -> _ui.update { it.copy(me = seat) } }
            }
            launch {
                target.error.collect { error ->
                    if (error != null) _ui.update { it.copy(error = error) }
                }
            }
        }
    }

    private fun maybeAdvertise(players: List<Player>) {
        val state = _ui.value
        if (!state.isHost || state.room == null || state.isPractice) return
        val count = players.count { it.isSeated }
        if (count <= 0 || count == advertisedCount) return
        advertisedCount = count
        advertise(state.room, count)
    }

    private fun advertise(room: RoomInfo, count: Int) {
        val discovery = advertiser ?: RoomDiscovery().also { advertiser = it }
        viewModelScope.launch {
            runCatching {
                discovery.advertise(
                    room.copy(
                        playerCount = count,
                        state = "waiting",
                        updatedAt = currentTimeMillis(),
                    ),
                )
            }.onFailure { error ->
                _ui.update { state ->
                    state.copy(
                        error = "局域网广播失败：${error.message ?: "NSD 不可用"}，可让好友手动输入 IP 连接",
                    )
                }
            }
        }
    }

    private fun stopAdvertising() {
        advertiser?.let { runCatching { it.stop() } }
        advertiser = null
        advertisedCount = -1
    }

    private fun parseEndpoint(text: String): Pair<String, Int>? {
        val value = text.trim().removePrefix("tcp://").removePrefix("TCP://")
        if (value.isEmpty()) return null
        val host: String
        val port: Int
        val colon = value.lastIndexOf(':')
        if (colon > 0) {
            host = value.substring(0, colon).trim()
            port = value.substring(colon + 1).trim().toIntOrNull() ?: return null
        } else {
            host = value
            port = Protocol.DEFAULT_PORT
        }
        if (host.isEmpty() || port !in 1..65535) return null
        if (!host.matches(Regex("[A-Za-z0-9._-]+"))) return null
        return host to port
    }
}
