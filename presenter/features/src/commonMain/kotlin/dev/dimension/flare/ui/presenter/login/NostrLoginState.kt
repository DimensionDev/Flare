package dev.dimension.flare.ui.presenter.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

@Immutable
public interface NostrLoginState {
    public val loading: Boolean
    public val error: Throwable?
    public val amberAvailable: Boolean
    public val qrConnectUri: String?
    public val qrWaitingForApproval: Boolean

    public fun login(input: String)

    public fun connectAmber()

    public fun startQrLogin()

    public fun cancelQrLogin()
}

@Composable
internal expect fun nostrLoginState(toHome: () -> Unit): NostrLoginState
