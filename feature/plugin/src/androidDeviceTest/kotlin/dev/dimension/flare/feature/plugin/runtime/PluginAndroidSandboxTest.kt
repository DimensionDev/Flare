package dev.dimension.flare.feature.plugin.runtime

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PluginAndroidSandboxTest {
    @Test
    fun sandboxRetirementInvalidatesPeersAndReconnectsWithoutReplayingHostWork() =
        runBlocking<Unit> {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val generation = PluginAndroidSandbox.acquire(context)
            val started = CompletableDeferred<Unit>()
            val cancelled = CompletableDeferred<Unit>()
            val calls = AtomicInteger()
            val first =
                createTestPluginJavaScriptEngine { _, _, _ ->
                    calls.incrementAndGet()
                    started.complete(Unit)
                    try {
                        CompletableDeferred<Unit>().await()
                        "unreachable"
                    } finally {
                        cancelled.complete(Unit)
                    }
                }
            val peer = createTestPluginJavaScriptEngine { _, _, _ -> "{}" }
            try {
                withTimeout(15_000) {
                    val pending = async { runCatching { first.evaluate("__flareHostCallV1('id','op','{}')", "pending.js", true) } }
                    started.await()
                    PluginAndroidSandbox.invalidate(generation)
                    assertFailsWith<PluginJavaScriptUnavailableException> { peer.evaluate("'old'", "old.js") }
                    assertTrue(pending.await().isFailure)
                    first.close()
                    cancelled.await()
                    val fresh = createTestPluginJavaScriptEngine { _, _, _ -> "{}" }
                    try {
                        assertEquals("fresh", fresh.evaluate("'fresh'", "fresh.js"))
                        assertEquals(1, calls.get())
                    } finally {
                        fresh.close()
                    }
                }
            } finally {
                first.close()
                peer.close()
            }
        }
}
