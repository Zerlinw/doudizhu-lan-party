package com.ddz.shared.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

/**
 * Length-prefixed JSON codec. Frame layout:
 *
 * ```
 * +--------------------+----------------------------+
 * | 4 bytes BE length  | length bytes UTF-8 JSON    |
 * +--------------------+----------------------------+
 * ```
 *
 * Decoding is fail-fast: unknown tags, oversized frames and payloads that do
 * not match their tag raise [ProtocolException], so a host can answer
 * ERROR(UNKNOWN_TYPE) and drop a poisoned connection.
 */
object NetworkCodec {

    /** Encode [msg] as a complete frame (prefix included). */
    fun encode(msg: Msg): ByteArray = encodeFrame(msg)

    fun encodeFrame(msg: Msg): ByteArray {
        val body = encodeBody(msg)
        val out = ByteArray(4 + body.size)
        writeInt(out, 0, body.size)
        body.copyInto(out, 4)
        return out
    }

    /** Payload JSON only, no length prefix. */
    fun encodeBody(msg: Msg): ByteArray {
        val text = NetworkJson.encodeToString(Msg.serializer(), msg)
        val body = text.encodeToByteArray()
        if (body.size > Protocol.MAX_FRAME_BYTES) {
            throw ProtocolException("FRAME_TOO_LARGE", "帧大小 ${body.size} 超过上限 ${Protocol.MAX_FRAME_BYTES}")
        }
        return body
    }

    /** Decode a complete frame (prefix included). */
    fun decode(frame: ByteArray): Msg = decodeFrame(frame)

    fun decodeFrame(frame: ByteArray): Msg {
        if (frame.size < 4) throw ProtocolException("BAD_FRAME", "帧头不足 4 字节")
        val length = readInt(frame, 0)
        if (length < 0 || length > Protocol.MAX_FRAME_BYTES) {
            throw ProtocolException("BAD_FRAME", "非法帧长度 $length")
        }
        if (frame.size != length + 4) {
            throw ProtocolException("BAD_FRAME", "帧长度不匹配：声明 $length，实际 ${frame.size - 4}")
        }
        return decodeBody(frame.copyOfRange(4, frame.size))
    }

    fun decodeBody(body: ByteArray): Msg {
        if (body.isEmpty()) throw ProtocolException("BAD_FRAME", "空帧")
        if (body.size > Protocol.MAX_FRAME_BYTES) {
            throw ProtocolException("FRAME_TOO_LARGE", "帧过大")
        }
        val text = body.decodeToString()
        val msg = try {
            NetworkJson.decodeFromString(Msg.serializer(), text)
        } catch (e: Exception) {
            throw ProtocolException("BAD_FRAME", "JSON 解析失败：${e.message}")
        }
        if (msg.seq < 0) throw ProtocolException("BAD_FRAME", "seq 不能为负数")
        validatePayload(msg)
        return msg
    }

    /** Decode the typed payload of [msg]. */
    inline fun <reified T> payload(msg: Msg): T = NetworkJson.decodeFromString<T>(msg.payload)

    /** Decode payload with an explicit serializer (used where reified types are not available). */
    fun <T> payload(msg: Msg, deserializer: kotlinx.serialization.DeserializationStrategy<T>): T =
        NetworkJson.decodeFromString(deserializer, msg.payload)

    /** Throws [ProtocolException] when the payload does not parse for its tag. */
    fun validatePayload(msg: Msg) {
        try {
            when (msg.type) {
                MsgType.HELLO -> payload<HelloPayload>(msg)
                MsgType.JOIN -> payload<JoinPayload>(msg)
                MsgType.ROOM_STATE -> payload<RoomStatePayload>(msg)
                MsgType.DEAL -> payload<DealPayload>(msg)
                MsgType.BID -> payload<BidPayload>(msg)
                MsgType.PLAY -> payload<PlayPayload>(msg)
                MsgType.PASS -> payload<PassPayload>(msg)
                MsgType.SNAPSHOT -> payload<SnapshotPayload>(msg)
                MsgType.SYNC_REQ -> payload<SyncReqPayload>(msg)
                MsgType.HEARTBEAT -> payload<HeartbeatPayload>(msg)
                MsgType.ERROR -> payload<ErrorPayload>(msg)
                MsgType.KICK -> payload<KickPayload>(msg)
                MsgType.LEAVE -> payload<LeavePayload>(msg)
                MsgType.ACK -> payload<AckPayload>(msg)
            }
        } catch (e: ProtocolException) {
            throw e
        } catch (e: Exception) {
            throw ProtocolException("BAD_PAYLOAD", "${msg.type} 载荷不合法：${e.message}")
        }
    }

    /** 4-byte big-endian length prefix. */
    fun encodeLength(length: Int): ByteArray = ByteArray(4).also { writeInt(it, 0, length) }

    fun decodeLength(header: ByteArray): Int {
        if (header.size < 4) throw ProtocolException("BAD_FRAME", "帧头不足 4 字节")
        return readInt(header, 0)
    }

    fun writeInt(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value ushr 24).toByte()
        target[offset + 1] = (value ushr 16).toByte()
        target[offset + 2] = (value ushr 8).toByte()
        target[offset + 3] = value.toByte()
    }

    fun readInt(source: ByteArray, offset: Int): Int =
        ((source[offset].toInt() and 0xFF) shl 24) or
            ((source[offset + 1].toInt() and 0xFF) shl 16) or
            ((source[offset + 2].toInt() and 0xFF) shl 8) or
            (source[offset + 3].toInt() and 0xFF)
}

/** Json used by the protocol; exposed for tests but not for ad-hoc module formats. */
val ProtocolJson: Json get() = NetworkJson
