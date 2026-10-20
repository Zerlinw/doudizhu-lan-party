package com.ddz.shared.net

/**
 * iOS transport placeholder (M5: compile-only).
 *
 * The shared session/state machine is platform-agnostic; M6 will implement these
 * actuals with Network.framework (or BSD sockets) plus NSNetService discovery.
 */
actual class TcpServer actual constructor(private val requestedPort: Int) {
    actual val boundPort: Int get() = requestedPort

    actual suspend fun accept(): Connection =
        throw UnsupportedOperationException("iOS TCP transport lands in M6 (Network.framework)")

    actual fun close() = Unit
}

actual class TcpConnector actual constructor() {
    actual suspend fun connect(host: String, port: Int, timeoutMs: Long): Connection =
        throw UnsupportedOperationException("iOS TCP transport lands in M6 (Network.framework)")
}
