package dev.dimension.flare.feature.plugin.runtime

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PluginSessionV1Test {
    @Test
    fun retirementCancelsDownstreamWritesAndRunsCleanup() =
        runBlocking {
            val session = PluginSessionV1()
            val returned = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var written = false
            var cleanups = 0
            session.onRetire { cleanups++ }
            val task =
                launch {
                    session.invoke("capabilities.timeline.page") { Unit }
                    returned.complete(Unit)
                    release.await()
                    written = true
                }
            val other = launch { awaitCancellation() }
            returned.await()
            session.retire()
            release.complete(Unit)
            assertTrue(task.isCancelled)
            assertFalse(written)
            assertTrue(other.isActive)
            assertEquals(1, cleanups)
            assertFailsWith<PluginSessionRetiredException> { session.invoke("read") { error("must not enter") } }
            session.retire()
            assertEquals(1, cleanups)
            other.cancel()
        }

    @Test
    fun refusesReloadUntilWriteCompletesWithoutRetiringTheSession() =
        runBlocking<Unit> {
            val session = PluginSessionV1()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val responseReceived = CompletableDeferred<Unit>()
            val uiFinished = CompletableDeferred<Unit>()
            val write =
                async {
                    val result =
                        session.invoke("capabilities.compose.publish") {
                            entered.complete(Unit)
                            release.await()
                            "published once"
                        }
                    responseReceived.complete(Unit)
                    uiFinished.await()
                    result
                }
            entered.await()
            assertFailsWith<IllegalStateException> { session.retire() }
            session.ensureActive()
            release.complete(Unit)
            responseReceived.await()
            assertFailsWith<IllegalStateException> { session.retire() }
            uiFinished.complete(Unit)
            assertEquals("published once", write.await())
            session.retire()
            assertFailsWith<PluginSessionRetiredException> { session.ensureActive() }
        }
}
