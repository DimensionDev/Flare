package dev.dimension.flare.common

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SwitchingServiceManagerTest {
    @Test
    fun signerSwitchDrainsOldCallsAndClosesBothServicesExactlyOnce() =
        runTest {
            val credentials = MutableStateFlow("old signer")
            val instances = mutableListOf<Service>()
            val manager = SwitchingServiceManager(credentials, backgroundScope) { Service(it).also(instances::add) }
            val release = CompletableDeferred<Unit>()
            val oldCall =
                async {
                    manager.withService {
                        release.await()
                        it.identity
                    }
                }
            runCurrent()
            credentials.value = "new signer"
            runCurrent()
            assertEquals("new signer", manager.withService { it.identity })
            assertEquals(0, instances[0].closeCount)
            release.complete(Unit)
            assertEquals("old signer", oldCall.await())
            runCurrent()
            assertEquals(1, instances[0].closeCount)
            manager.close()
            assertEquals(listOf(1, 1), instances.map { it.closeCount })
        }

    @Test
    fun waitsForCredentialAndReleasesReferenceWhenAnOperationFails() =
        runTest {
            val credentials = MutableStateFlow<String?>(null)
            val instances = mutableListOf<Service>()
            val manager = SwitchingServiceManager(credentials, backgroundScope) { Service(it).also(instances::add) }
            try {
                val pending = async { manager.withService { it.identity } }
                runCurrent()
                assertFalse(pending.isCompleted)
                credentials.value = "signer"
                assertEquals("signer", pending.await())
                assertFailsWith<IllegalStateException> { manager.withService { error("operation failed") } }
                credentials.value = null
                runCurrent()
                assertTrue(instances.single().closeCount == 1)
            } finally {
                manager.close()
            }
        }

    private class Service(
        val identity: String,
    ) : AutoCloseable {
        var closeCount = 0

        override fun close() {
            closeCount++
        }
    }
}
