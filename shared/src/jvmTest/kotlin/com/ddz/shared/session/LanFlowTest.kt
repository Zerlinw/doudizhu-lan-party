package com.ddz.shared.session

import com.ddz.shared.game.core.Phase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Loopback integration test: real TCP sockets, real codec, real host/client
 * session state machines. Runs as part of `:shared:jvmTest`.
 */
class LanFlowTest {

    @Test
    fun twoClientsJoinHostAndReceiveRedactedSnapshots() = runBlocking {
        val hostScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val host = HostSession(hostName = "Zerlin", scope = hostScope, requestedPort = 19527)
        val room = host.start()
        val clientA = ClientSession("127.0.0.1", room.port, "PlayerA")
        val clientB = ClientSession("127.0.0.1", room.port, "PlayerB")
        try {
            withTimeout(20_000) {
                clientA.start()
                clientB.start()

                val seatA = clientA.mySeat.first { it >= 1 }
                val seatB = clientB.mySeat.first { it >= 1 && it != seatA }
                assertEquals(setOf(1, 2), setOf(seatA, seatB))

                host.seats.first { seats -> seats.count { it.isSeated } == 3 }
                host.requestStart()

                val stateA = clientA.state.first { it != null && it.phase == Phase.BIDDING }
                val stateB = clientB.state.first { it != null && it.phase == Phase.BIDDING }
                assertNotNull(stateA)
                assertNotNull(stateB)
                assertEquals(17, stateA.player(seatA).cardCount)
                assertEquals(17, stateA.player(seatA).hand.size)
                assertTrue(stateA.player((seatA + 1) % 3).hand.isEmpty())
                assertEquals(17, stateB.player(seatB).hand.size)
                assertTrue(stateA.bottomCards.isEmpty(), "bottom must stay hidden during bidding")
                assertTrue(stateA.seq > 0)
                assertTrue(stateB.seq > 0)
            }
        } finally {
            clientA.close()
            clientB.close()
            host.close()
            hostScope.cancel()
        }
    }
}
