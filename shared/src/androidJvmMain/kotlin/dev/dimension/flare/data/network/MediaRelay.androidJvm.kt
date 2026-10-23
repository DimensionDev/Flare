package dev.dimension.flare.data.network

import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal actual class MediaRelayLock actual constructor() {
    private val lock = ReentrantLock()

    actual fun <T> withLock(block: () -> T): T = lock.withLock(block)
}

internal actual class MediaRelayListener actual constructor() {
    private val server = ServerSocket(0, 64, InetAddress.getByName("127.0.0.1"))
    actual val port: Int get() = server.localPort

    actual fun close() = server.close()

    actual fun accept(): MediaRelayConnection {
        val socket = server.accept().apply { soTimeout = 30_000 }
        return object : MediaRelayConnection {
            override fun read(bytes: ByteArray): Int = socket.getInputStream().read(bytes)

            override fun write(
                bytes: ByteArray,
                count: Int,
            ) = socket.getOutputStream().write(bytes, 0, count)

            override fun close() = socket.close()
        }
    }
}
