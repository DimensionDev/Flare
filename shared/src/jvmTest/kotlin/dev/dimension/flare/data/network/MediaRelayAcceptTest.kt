package dev.dimension.flare.data.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MediaRelayAcceptTest {
    @Test
    fun failedRequestAndCloseStillReleasePermitAndAcceptNextConnection() =
        runTest {
            val permits = Semaphore(1)
            var accepted = 0
            var served = 0
            var closed = 0
            assertFailsWith<IOException> {
                runMediaRelay(permits, accept = {
                    if (++accepted > 2) throw IOException("Listener stopped")
                    object : MediaRelayConnection {
                        override fun read(bytes: ByteArray): Int = -1

                        override fun write(
                            bytes: ByteArray,
                            count: Int,
                        ) = Unit

                        override fun close() {
                            closed++
                            throw IOException("Close failed")
                        }
                    }
                }, serve = {
                    served++
                    throw IOException("Client disconnected")
                })
            }
            assertEquals(2, served)
            assertEquals(2, closed)
            assertEquals(1, permits.availablePermits)
        }

    @Test
    fun fatalAcceptFailureReleasesPermit() =
        runTest {
            val permits = Semaphore(1)
            assertFailsWith<AssertionError> {
                runMediaRelay(permits, accept = { throw AssertionError("Fatal accept failure") }, serve = { })
            }
            assertEquals(1, permits.availablePermits)
        }

    @Test
    fun cancellationBeforeRequestStartsStillClosesConnection() =
        runTest {
            val permits = Semaphore(1)
            var closed = false
            assertFailsWith<CancellationException> {
                kotlinx.coroutines.coroutineScope {
                    val relayContext = currentCoroutineContext()
                    runMediaRelay(permits, accept = {
                        relayContext.cancel()
                        object : MediaRelayConnection {
                            override fun read(bytes: ByteArray): Int = -1

                            override fun write(
                                bytes: ByteArray,
                                count: Int,
                            ) = Unit

                            override fun close() {
                                closed = true
                            }
                        }
                    }, serve = { error("Cancelled request must not run") })
                }
            }
            assertEquals(true, closed)
            assertEquals(1, permits.availablePermits)
        }

    @Test
    fun permanentAcceptFailureStopsWithTheOriginalError() =
        runTest {
            var attempts = 0
            val failure = IOException("Listener is closed")
            assertEquals(
                failure.message,
                assertFailsWith<IOException> {
                    withTimeout(2_000) {
                        acceptMediaRelayConnection {
                            attempts++
                            throw failure
                        }
                    }
                }.message,
            )
            assertEquals(3, attempts)
        }

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
