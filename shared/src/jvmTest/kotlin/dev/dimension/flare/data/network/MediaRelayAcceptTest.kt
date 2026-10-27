package dev.dimension.flare.data.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class MediaRelayAcceptTest {
    @Test
    fun terminalAcceptFailureClosesBlockingRequestsBeforeWaitingForChildren(): Unit =
        runBlocking {
            val started = CountDownLatch(1)
            val finishRead = CountDownLatch(1)
            val failedAccept = CompletableDeferred<Unit>()
            val reported = CompletableDeferred<Throwable>()
            var accepts = 0
            val permits = Semaphore(2)
            val relay =
                async(Dispatchers.IO) {
                    assertFailsWith<IOException> {
                        runMediaRelay(permits, accept = {
                            if (++accepts > 1) {
                                check(started.await(5, TimeUnit.SECONDS))
                                if (accepts == 4) failedAccept.complete(Unit)
                                throw IOException("Listener stopped")
                            }
                            object : MediaRelayConnection {
                                override fun interrupt() {
                                    assertTrue(reported.isCompleted, "Publish the failure before interrupting active requests")
                                    finishRead.countDown()
                                }

                                override fun read(bytes: ByteArray): Int = -1

                                override fun write(
                                    bytes: ByteArray,
                                    count: Int,
                                ) = Unit

                                override fun close() = Unit
                            }
                        }, serve = {
                            started.countDown()
                            check(finishRead.await(5, TimeUnit.SECONDS))
                        }, onFailure = { reported.complete(it) })
                    }
                }
            val finished =
                try {
                    withTimeout(5_000) { failedAccept.await() }
                    withTimeoutOrNull(1_000) {
                        relay.await()
                        true
                    }
                } finally {
                    finishRead.countDown()
                    relay.await()
                }
            assertTrue(finished == true, "A blocked request must not postpone terminal failure reporting")
            assertEquals(2, permits.availablePermits)
        }

    @Test
    fun cancellationInterruptsBlockedIoAndClosesTheConnectionOnce(): Unit =
        runBlocking {
            val started = CompletableDeferred<Unit>()
            val finish = CountDownLatch(1)
            val permits = Semaphore(1)
            var closed = 0
            val relay =
                launch(Dispatchers.IO) {
                    runMediaRelay(permits, accept = {
                        object : MediaRelayConnection {
                            override fun interrupt() {
                                finish.countDown()
                            }

                            override fun read(bytes: ByteArray): Int = -1

                            override fun write(
                                bytes: ByteArray,
                                count: Int,
                            ) = Unit

                            override fun close() {
                                closed++
                            }
                        }
                    }, serve = {
                        started.complete(Unit)
                        check(finish.await(5, TimeUnit.SECONDS))
                    })
                }
            try {
                withTimeout(5_000) { started.await() }
                relay.cancel()
                withTimeout(1_000) { relay.join() }
                assertEquals(1, closed)
                assertEquals(1, permits.availablePermits)
            } finally {
                finish.countDown()
                relay.cancel()
                relay.join()
            }
        }

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
                        override fun interrupt() = Unit

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
                            override fun interrupt() = Unit

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
                    override fun interrupt() = Unit

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
