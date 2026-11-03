package dev.dimension.flare.ui.screen.login

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import dev.dimension.flare.compose.ui.Res
import dev.dimension.flare.compose.ui.nostr_login_qr_button
import dev.dimension.flare.ui.component.res
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.presenter.login.LoginAction
import dev.dimension.flare.ui.presenter.login.LoginEffect
import dev.dimension.flare.ui.presenter.login.LoginFlowPresenter
import dev.dimension.flare.ui.presenter.login.LoginFlowState
import dev.dimension.flare.ui.presenter.login.LoginMethodHandler
import io.github.composefluent.FluentTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.jetbrains.compose.resources.stringResource
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

class QrLoginCancellationTest {
    @get:Rule
    val compose = createComposeRule()

    private val handler = PairingHandler()
    private lateinit var state: LoginFlowPresenter.State
    private var qrContent by mutableStateOf<String?>("nostrconnect://test?secret=test")
    private var cancelLabel = ""
    private var qrLabel = ""

    @Test
    fun cancellingBelowTheQrRejectsLateApproval() = assertCancelled(button = 0)

    @Test
    fun cancellingTheLoginActionAlsoRemovesTheQr() = assertCancelled(button = 1)

    @Test
    fun failedPairingDoesNotKeepShowingAWaitingQr() {
        showLogin()
        compose.runOnIdle { handler.fail() }
        compose.onNodeWithContentDescription(qrLabel).assertDoesNotExist()
        compose.onNodeWithText("Pairing expired").assertExists()
        compose.runOnIdle { handler.approval.complete(Unit) }
        compose.waitForIdle()
        assertEquals(0, handler.accountsAdded)
    }

    @Test
    fun acceptedPairingDoesNotKeepOfferingQrCancellation() {
        showLogin()
        compose.runOnIdle {
            handler.state.value = handler.state.value.copy(loading = true)
        }
        compose.onNodeWithContentDescription(qrLabel).assertDoesNotExist()
    }

    private fun assertCancelled(button: Int) {
        showLogin()
        compose.onAllNodesWithText(cancelLabel)[button].performClick()
        compose.runOnIdle { handler.approval.complete(Unit) }
        compose.waitForIdle()
        assertEquals(0, handler.accountsAdded, "A cancelled login must not accept a late signer approval")
        compose.onNodeWithContentDescription(qrLabel).assertDoesNotExist()
    }

    private fun showLogin() {
        compose.setContent {
            FluentTheme {
                cancelLabel = stringResource(UiStrings.Cancel.res)
                qrLabel = stringResource(Res.string.nostr_login_qr_button)
                state = LoginFlowPresenter(handler).body()
                Box(Modifier.size(360.dp, 700.dp)) {
                    LoginFlowContent(state, qrContent) { qrContent = null }
                }
            }
        }
        compose.runOnIdle { state.perform("start") }
        compose.waitForIdle()
    }
}

private class PairingHandler : LoginMethodHandler {
    val approval = CompletableDeferred<Unit>()
    var accountsAdded = 0
        private set
    override val state = MutableStateFlow(LoginFlowState(actions = listOf(LoginAction("cancel", UiStrings.Cancel))))
    override val effects = emptyFlow<LoginEffect>()

    override suspend fun perform(actionId: String) {
        when (actionId) {
            "start" -> {
                approval.await()
                accountsAdded += 1
            }

            "cancel" -> {
                clear()
            }
        }
    }

    override fun updateField(
        id: String,
        value: String,
    ) = Unit

    override suspend fun resume(value: String) = Unit

    override fun clear() {
        approval.cancel()
        state.value = LoginFlowState()
    }

    fun fail() {
        clear()
        state.value = LoginFlowState(error = "Pairing expired")
    }
}
