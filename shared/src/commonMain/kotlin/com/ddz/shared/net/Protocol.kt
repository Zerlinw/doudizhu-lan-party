package com.ddz.shared.net

import com.ddz.shared.discovery.RoomInfo
import com.ddz.shared.game.core.GameState
import com.ddz.shared.game.core.Player
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Wire protocol constants shared by host and clients. */
object Protocol {
    /** Bumped whenever the payload schema changes incompatibly. */
    const val VERSION: Int = 1

    /** Default authoritative host TCP port. */
    const val DEFAULT_PORT: Int = 9527

    /** Every frame is `4-byte big-endian length + UTF-8 JSON`. */
    const val MAX_FRAME_BYTES: Int = 64 * 1024

    const val HEARTBEAT_INTERVAL_MS: Long = 5_000
    const val HEARTBEAT_TIMEOUT_MS: Long = 15_000
}

/** Message tags. Kept as an enum so logs and dispatch stay readable. */
@Serializable
enum class MsgType {
    HELLO,
    JOIN,
    ROOM_STATE,
    DEAL,
    BID,
    PLAY,
    PASS,
    SNAPSHOT,
    SYNC_REQ,
    HEARTBEAT,
    ERROR,
    KICK,
    LEAVE,
    ACK,
}

/**
 * The single wire envelope. `payload` is embedded JSON typed by [type]; modules
 * must build messages through [Msgs] and decode payloads through
 * [NetworkCodec.payload] so no one invents their own JSON.
 */
@Serializable
data class Msg(
    val seq: Long,
    val type: MsgType,
    val payload: String = "{}",
)

@Serializable
data class HelloPayload(
    val protocol: Int = Protocol.VERSION,
    val playerName: String,
    val avatarSeed: Int = 0,
)

@Serializable
data class JoinPayload(
    val playerName: String,
    val avatarSeed: Int = 0,
    val protocol: Int = Protocol.VERSION,
)

@Serializable
data class RoomStatePayload(
    val room: RoomInfo,
    val players: List<Player>,
    val yourSeat: Int = -1,
    val hostSeat: Int = 0,
    val phase: String = "WAITING",
)

@Serializable
data class DealPayload(
    val round: Int,
    val seed: Long,
    val dealerSeat: Int,
)

@Serializable
data class BidPayload(val seat: Int, val score: Int)

@Serializable
data class PlayPayload(val seat: Int, val cards: List<Int>)

@Serializable
data class PassPayload(val seat: Int)

@Serializable
data class SnapshotPayload(val state: GameState, val seq: Long)

@Serializable
data class SyncReqPayload(val lastKnownSeq: Long)

@Serializable
data class HeartbeatPayload(val sentAt: Long)

@Serializable
data class ErrorPayload(val code: String, val message: String)

@Serializable
data class KickPayload(val reason: String)

@Serializable
data class LeavePayload(val seat: Int, val reason: String = "")

@Serializable
data class AckPayload(val seq: Long)

/** Thrown for malformed frames, unknown message tags or payload mismatches. */
class ProtocolException(val code: String, message: String) : Exception(message)

/** Typed message builders. Every payload passes through the shared [NetworkJson]. */
object Msgs {

    fun hello(seq: Long, name: String, avatarSeed: Int = 0): Msg =
        wrap(seq, MsgType.HELLO, HelloPayload.serializer(), HelloPayload(playerName = name, avatarSeed = avatarSeed))

    fun join(seq: Long, name: String, avatarSeed: Int = 0): Msg =
        wrap(seq, MsgType.JOIN, JoinPayload.serializer(), JoinPayload(playerName = name, avatarSeed = avatarSeed))

    fun roomState(
        seq: Long,
        room: RoomInfo,
        players: List<Player>,
        yourSeat: Int,
        hostSeat: Int = 0,
        phase: String = "WAITING",
    ): Msg = wrap(
        seq,
        MsgType.ROOM_STATE,
        RoomStatePayload.serializer(),
        RoomStatePayload(room, players, yourSeat, hostSeat, phase),
    )

    fun deal(seq: Long, round: Int, seed: Long, dealerSeat: Int): Msg =
        wrap(seq, MsgType.DEAL, DealPayload.serializer(), DealPayload(round, seed, dealerSeat))

    fun bid(seq: Long, seat: Int, score: Int): Msg =
        wrap(seq, MsgType.BID, BidPayload.serializer(), BidPayload(seat, score))

    fun play(seq: Long, seat: Int, cards: List<Int>): Msg =
        wrap(seq, MsgType.PLAY, PlayPayload.serializer(), PlayPayload(seat, cards))

    fun pass(seq: Long, seat: Int): Msg =
        wrap(seq, MsgType.PASS, PassPayload.serializer(), PassPayload(seat))

    fun snapshot(seq: Long, state: GameState): Msg =
        wrap(seq, MsgType.SNAPSHOT, SnapshotPayload.serializer(), SnapshotPayload(state, seq))

    fun syncReq(seq: Long, lastKnownSeq: Long): Msg =
        wrap(seq, MsgType.SYNC_REQ, SyncReqPayload.serializer(), SyncReqPayload(lastKnownSeq))

    fun heartbeat(seq: Long, sentAt: Long): Msg =
        wrap(seq, MsgType.HEARTBEAT, HeartbeatPayload.serializer(), HeartbeatPayload(sentAt))

    fun error(seq: Long, code: String, message: String): Msg =
        wrap(seq, MsgType.ERROR, ErrorPayload.serializer(), ErrorPayload(code, message))

    fun kick(seq: Long, reason: String): Msg =
        wrap(seq, MsgType.KICK, KickPayload.serializer(), KickPayload(reason))

    fun leave(seq: Long, seat: Int, reason: String = ""): Msg =
        wrap(seq, MsgType.LEAVE, LeavePayload.serializer(), LeavePayload(seat, reason))

    fun ack(seq: Long, ackedSeq: Long): Msg =
        wrap(seq, MsgType.ACK, AckPayload.serializer(), AckPayload(ackedSeq))

    private fun <T> wrap(
        seq: Long,
        type: MsgType,
        serializer: KSerializer<T>,
        payload: T,
    ): Msg = Msg(seq = seq, type = type, payload = NetworkJson.encodeToString(serializer, payload))
}

/** Shared JSON configuration: tolerant on unknown fields, strict on structure. */
val NetworkJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    isLenient = false
}
