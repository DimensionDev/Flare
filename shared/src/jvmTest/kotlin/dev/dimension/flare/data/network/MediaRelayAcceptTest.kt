package dev.dimension.flare.data.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MediaRelayAcceptTest {
    @Test
    fun acceptRecoversAfterTransientFailures() =
        runTest {
            var attempts = 0
            val connection =
                object : MediaRelayConnection {
                    override fun read(bytes: ByteArray): Int = -1

                    override fun write(
                        bytes: ByteArray,
                        count: Int,
                    ) = Unit

                    override fun close() = Unit
                }
            assertSame(
                connection,
                acceptMediaRelayConnection {
                    if (++attempts < 3) throw IOException("Temporarily out of descriptors")
                    connection
                },
            )
            assertEquals(3, attempts)
            assertEquals(500, testScheduler.currentTime)
        }

    @Test
    fun cancellationDoesNotRestartAccept() =
        runTest {
            var attempts = 0
            assertFailsWith<CancellationException> {
                acceptMediaRelayConnection {
                    attempts++
                    throw CancellationException("Shutting down")
                }
            }
            assertEquals(1, attempts)
        }
}
