package dev.dimension.flare.ui.presenter.login

import dev.dimension.flare.data.network.nostr.NostrService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NostrQrLoginHandlerTest {
    private val context = LoginContext("nostr", LoginMethodType.QrConnect, onSuccess = { error("Unexpected login") })

    @Test
    fun cancellingPairingDoesNotDisplayAnError() =
        runTest {
            val session = PendingPairing()
            val handler = NostrQrLoginHandler(context) { session }
            launch(start = CoroutineStart.UNDISPATCHED) { handler.perform("start_qr") }
            handler.perform("cancel")
            runCurrent()
            assertTrue(session.closed)
            assertNull(handler.state.value.error)
            assertEquals(
                "start_qr",
                handler.state.value.actions
                    .single()
                    .id,
            )
        }

    @Test
    fun cancelledAttemptCannotCloseAnImmediateRetry() =
        runTest {
            val first = PendingPairing()
            val second = PendingPairing()
            val sessions = ArrayDeque(listOf(first, second))
            val handler = NostrQrLoginHandler(context) { sessions.removeFirst() }
            launch(start = CoroutineStart.UNDISPATCHED) { handler.perform("start_qr") }
            handler.clear()
            launch(start = CoroutineStart.UNDISPATCHED) { handler.perform("start_qr") }
            runCurrent()
            assertFalse(second.closed)
            assertNull(handler.state.value.error)
            assertEquals(
                "cancel",
                handler.state.value.actions
                    .single()
                    .id,
            )
            handler.close()
            runCurrent()
        }

    @Test
    fun timeoutStillReportsFailureAndClosesTheSession() =
        runTest {
            val session = PendingPairing()
            val handler = NostrQrLoginHandler(context) { session }
            launch(start = CoroutineStart.UNDISPATCHED) { handler.perform("start_qr") }
            advanceTimeBy(120_001)
            runCurrent()
            assertTrue(session.closed)
            assertNotNull(handler.state.value.error)
            assertEquals(
                "start_qr",
                handler.state.value.actions
                    .single()
                    .id,
            )
        }
}

private class PendingPairing : NostrService.Companion.PendingQrLogin {
    override val connectUri = "nostrconnect://test"
    private val result = CompletableDeferred<NostrService.ImportedAccount.RemoteSigner>()
    var closed = false
        private set

    override suspend fun awaitAccount(): NostrService.ImportedAccount.RemoteSigner = result.await()

    override fun close() {
        closed = true
        result.completeExceptionally(IllegalStateException("NostrConnect session closed"))
    }
}
