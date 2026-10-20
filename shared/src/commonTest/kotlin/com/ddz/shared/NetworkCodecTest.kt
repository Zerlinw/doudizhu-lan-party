package com.ddz.shared

import com.ddz.shared.discovery.RoomInfo
import com.ddz.shared.game.core.Card
import com.ddz.shared.game.core.GameState
import com.ddz.shared.game.core.Phase
import com.ddz.shared.net.AckPayload
import com.ddz.shared.net.BidPayload
import com.ddz.shared.net.DealPayload
import com.ddz.shared.net.ErrorPayload
import com.ddz.shared.net.HeartbeatPayload
import com.ddz.shared.net.HelloPayload
import com.ddz.shared.net.JoinPayload
import com.ddz.shared.net.KickPayload
import com.ddz.shared.net.Msg
import com.ddz.shared.net.MsgType
import com.ddz.shared.net.Msgs
import com.ddz.shared.net.NetworkCodec
import com.ddz.shared.net.NetworkJson
import com.ddz.shared.net.PlayPayload
import com.ddz.shared.net.Protocol
import com.ddz.shared.net.ProtocolException
import com.ddz.shared.net.PassPayload
import com.ddz.shared.net.RoomStatePayload
import com.ddz.shared.net.SnapshotPayload
import com.ddz.shared.net.SyncReqPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NetworkCodecTest {

    private val room = RoomInfo(name = "Zerlin 的房间", hostIp = "192.168.1.8", port = 9527, playerCount = 3)

    private fun snapshotState(): GameState = TestCards.playing(
        seat0 = "3s 4s 5s",
        seat1 = "6s 7s 8s",
        seat2 = "9s Ts Js",
        landlordSeat = 0,
        currentSeat = 0,
    ).copy(phase = Phase.PLAYING)

    @Test
    fun roundTripsEveryMessageType() {
        val messages = listOf(
            Msgs.hello(1, "Zerlin", 3),
            Msgs.join(2, "Zerlin", 3),
            Msgs.roomState(3, room, snapshotState().players, yourSeat = 1),
            Msgs.deal(4, round = 1, seed = 99, dealerSeat = 1),
            Msgs.bid(5, seat = 1, score = 2),
            Msgs.play(6, seat = 1, cards = listOf(30, 31)),
            Msgs.pass(7, seat = 1),
            Msgs.snapshot(8, snapshotState()),
            Msgs.syncReq(9, 7),
            Msgs.heartbeat(10, 123456),
            Msgs.error(11, "CANNOT_BEAT", "管不上"),
            Msgs.kick(12, "房主移出"),
            Msgs.leave(13, 1, "退出"),
            Msgs.ack(14, 13),
        )
        for (original in messages) {
            val decoded = NetworkCodec.decodeFrame(NetworkCodec.encodeFrame(original))
            assertEquals(original.type, decoded.type)
            assertEquals(original.seq, decoded.seq)
            assertEquals(original.payload, decoded.payload)
        }
    }

    @Test
    fun typedPayloadsDecode() {
        val msg = NetworkCodec.decodeFrame(NetworkCodec.encodeFrame(Msgs.bid(5, 2, 3)))
        val payload = NetworkCodec.payload<BidPayload>(msg)
        assertEquals(2, payload.seat)
        assertEquals(3, payload.score)

        val hello = NetworkCodec.payload<HelloPayload>(Msgs.hello(1, "Zerlin", 9))
        assertEquals(Protocol.VERSION, hello.protocol)
        assertEquals("Zerlin", hello.playerName)
        assertEquals(9, hello.avatarSeed)
    }

    @Test
    fun snapshotPreservesRedactedState() {
        val state = snapshotState().redactedFor(1)
        val msg = NetworkCodec.decodeFrame(NetworkCodec.encodeFrame(Msgs.snapshot(1, state)))
        val payload = NetworkCodec.payload<SnapshotPayload>(msg)
        assertEquals(1, payload.seq)
        assertEquals(0, payload.state.player(0).hand.size)
        assertEquals(3, payload.state.player(1).hand.size)
        assertEquals(3, payload.state.player(0).cardCount)
    }

    @Test
    fun roomStateCarriesSeatAndPlayers() {
        val msg = Msgs.roomState(1, room, snapshotState().players, yourSeat = 2)
        val payload = NetworkCodec.payload<RoomStatePayload>(NetworkCodec.decodeFrame(NetworkCodec.encodeFrame(msg)))
        assertEquals(2, payload.yourSeat)
        assertEquals("192.168.1.8", payload.room.hostIp)
        assertEquals(3, payload.players.size)
    }

    @Test
    fun frameUsesBigEndianLengthPrefix() {
        val frame = NetworkCodec.encodeFrame(Msgs.pass(1, 0))
        val declared = NetworkCodec.decodeLength(frame.copyOfRange(0, 4))
        assertEquals(frame.size - 4, declared)
        assertEquals(byteArrayOf(0, 0, 0, 5).toList(), NetworkCodec.encodeLength(5).toList())
        assertEquals(0x01020304, NetworkCodec.decodeLength(byteArrayOf(1, 2, 3, 4)))
    }

    @Test
    fun rejectsUnknownMessageType() {
        val body = """{"seq":1,"type":"ALIEN","payload":"{}"}""".encodeToByteArray()
        val error = assertFailsWith<ProtocolException> { NetworkCodec.decodeBody(body) }
        assertEquals("BAD_FRAME", error.code)
    }

    @Test
    fun rejectsMismatchedPayload() {
        val msg = Msg(1, MsgType.PLAY, "{}")
        val error = assertFailsWith<ProtocolException> { NetworkCodec.validatePayload(msg) }
        assertEquals("BAD_PAYLOAD", error.code)
    }

    @Test
    fun rejectsNegativeSeqAndBrokenFrames() {
        val negative = """{"seq":-1,"type":"HEARTBEAT","payload":"{\"sentAt\":1}"}""".encodeToByteArray()
        assertFailsWith<ProtocolException> { NetworkCodec.decodeBody(negative) }

        val valid = NetworkCodec.encodeFrame(Msgs.pass(1, 0))
        assertFailsWith<ProtocolException> { NetworkCodec.decodeFrame(valid.copyOf(valid.size - 1)) }
        assertFailsWith<ProtocolException> { NetworkCodec.decodeFrame(byteArrayOf(1, 2)) }
        assertFailsWith<ProtocolException> { NetworkCodec.decodeFrame(NetworkCodec.encodeLength(999) + ByteArray(4)) }
    }

    @Test
    fun rejectsOversizedFrames() {
        val msg = Msg(1, MsgType.HEARTBEAT, "\"" + "x".repeat(Protocol.MAX_FRAME_BYTES) + "\"")
        val error = assertFailsWith<ProtocolException> { NetworkCodec.encodeBody(msg) }
        assertEquals("FRAME_TOO_LARGE", error.code)
    }

    @Test
    fun payloadsArePracticalSizes() {
        val state = TestCards.biddingState(7)
        val frame = NetworkCodec.encodeFrame(
            Msgs.snapshot(1, state.redactedFor(0)),
        )
        assertTrue(frame.size < 4096, "snapshot frame was ${frame.size} bytes")
    }

    @Test
    fun roomInfoRoundTripsThroughJson() {
        val text = NetworkJson.encodeToString(RoomInfo.serializer(), room)
        val decoded = NetworkJson.decodeFromString(RoomInfo.serializer(), text)
        assertEquals(room, decoded)
    }

    @Test
    fun cardCodesSurvivePlayPayload() {
        val cards = TestCards.of("3s 3h X D")
        val msg = Msgs.play(1, 2, Card.encodeAll(cards))
        val payload = NetworkCodec.payload<PlayPayload>(msg)
        assertEquals(cards.map { it.code }, payload.cards)
        assertEquals(cards, Card.decodeAll(payload.cards))
    }

    @Test
    fun decodeAckAndErrorPayloads() {
        assertEquals(7, NetworkCodec.payload<AckPayload>(Msgs.ack(1, 7)).seq)
        assertEquals("X", NetworkCodec.payload<ErrorPayload>(Msgs.error(1, "X", "m")).code)
        assertEquals(1, NetworkCodec.payload<PassPayload>(Msgs.pass(1, 1)).seat)
        assertEquals(2, NetworkCodec.payload<DealPayload>(Msgs.deal(1, 2, 3, 4)).round)
        assertEquals(4, NetworkCodec.payload<DealPayload>(Msgs.deal(1, 2, 3, 4)).dealerSeat)
        assertEquals("bye", NetworkCodec.payload<KickPayload>(Msgs.kick(1, "bye")).reason)
    }

    @Test
    fun joinPayloadDefaultsProtocolVersion() {
        val msg = Msgs.join(1, "Zerlin")
        assertEquals(Protocol.VERSION, NetworkCodec.payload<JoinPayload>(msg).protocol)
    }

    @Test
    fun parseSnapshotAfterTransport() {
        val state = TestCards.biddingState(3)
        val frame = NetworkCodec.encode(Msgs.snapshot(9, state))
        val decoded = NetworkCodec.decode(frame)
        val payload = NetworkCodec.payload<SnapshotPayload>(decoded)
        assertNotNull(payload.state.settlement == null)
        assertEquals(state.seq, payload.state.seq)
        assertEquals(9, payload.seq)
        assertEquals(3, payload.state.players.size)
    }
}
