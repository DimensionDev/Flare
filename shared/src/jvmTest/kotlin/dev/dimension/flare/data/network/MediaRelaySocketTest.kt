package dev.dimension.flare.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.net.Socket
import java.net.SocketTimeoutException
import kotlin.test.Test
import kotlin.test.assertFailsWith

class MediaRelaySocketTest {
    @Test
    fun anUnresponsivePlayerCannotKeepAWriteBlockedIndefinitely(): Unit =
        runBlocking {
            val listener = MediaRelayListener()
            val client =
                Socket().apply {
                    receiveBufferSize = 1024
                    connect(java.net.InetSocketAddress("127.0.0.1", listener.port))
                }
            try {
                val connection = listener.accept()
                try {
                    val writer =
                        async(Dispatchers.IO) {
                            assertFailsWith<SocketTimeoutException> {
                                val chunk = ByteArray(1024 * 1024)
                                repeat(64) { connection.write(chunk) }
                            }
                        }
                    try {
                        withTimeout(40_000) { writer.await() }
                    } finally {
                        client.close()
                        writer.cancel()
                    }
                } finally {
                    connection.close()
                }
            } finally {
                client.close()
                listener.close()
            }
        }
}
