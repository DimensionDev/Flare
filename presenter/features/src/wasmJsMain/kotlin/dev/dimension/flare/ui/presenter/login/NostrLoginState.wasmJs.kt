package dev.dimension.flare.ui.presenter.login

import androidx.compose.runtime.Composable

@Composable
@Suppress("UNUSED_PARAMETER")
internal actual fun nostrLoginState(toHome: () -> Unit): NostrLoginState = DisabledNostrLoginState

private data object DisabledNostrLoginState : NostrLoginState {
    override val loading: Boolean = false
    override val error: Throwable? = null
    override val amberAvailable: Boolean = false
    override val qrConnectUri: String? = null
    override val qrWaitingForApproval: Boolean = false

    override fun login(
        @Suppress("UNUSED_PARAMETER") input: String,
    ) {
    }

    override fun connectAmber() {
    }

    override fun startQrLogin() {
    }

    override fun cancelQrLogin() {
    }
}
