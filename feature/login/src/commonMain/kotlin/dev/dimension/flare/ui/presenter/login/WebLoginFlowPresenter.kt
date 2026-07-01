package dev.dimension.flare.ui.presenter.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.web.shared.WebPresenter
import kotlinx.coroutines.launch

@WebPresenter("loginFlow")
public class WebLoginFlowPresenter(
    private val platformType: PlatformType,
    private val host: String,
    private val methodType: LoginMethodType,
    private val redirectUri: String?,
    private val onOpenUrl: (String) -> Unit,
    private val onOpenWebCookieLogin: (String) -> Unit,
    private val onShowQr: (String) -> Unit,
    private val onSuccess: () -> Unit,
) : PresenterBase<WebLoginFlowPresenter.State>() {
    private val loginPlatformRegistry: LoginPlatformRegistry by koinInject()

    @Immutable
    public interface State {
        public val fields: List<WebLoginField>
        public val actions: List<WebLoginAction>
        public val loading: Boolean
        public val error: String?

        public fun updateField(
            id: String,
            value: String,
        )

        public fun perform(actionId: String)

        public fun resume(value: String)

        public fun clear()
    }

    @Composable
    override fun body(): State {
        val scope = rememberCoroutineScope()
        val handler =
            remember(platformType, host, methodType, redirectUri) {
                loginPlatformRegistry.require(platformType).createHandler(
                    LoginContext(
                        host = host,
                        methodType = methodType,
                        redirectUri = redirectUri?.takeIf { it.isNotBlank() },
                        onSuccess = {
                            onSuccess()
                        },
                    ),
                )
            }
        val flowState by handler.state.collectAsState()

        DisposableEffect(handler) {
            onDispose {
                handler.close()
            }
        }

        LaunchedEffect(handler) {
            handler.effects.collect { effect ->
                when (effect) {
                    is LoginEffect.OpenUrl -> onOpenUrl(effect.url)
                    is LoginEffect.OpenWebCookieLogin -> onOpenWebCookieLogin(effect.url)
                    is LoginEffect.ShowQr -> onShowQr(effect.content)
                }
            }
        }

        return object : State {
            override val fields: List<WebLoginField> = flowState.fields.map { it.toWeb() }
            override val actions: List<WebLoginAction> = flowState.actions.map { it.toWeb() }
            override val loading: Boolean = flowState.loading
            override val error: String? = flowState.error

            override fun updateField(
                id: String,
                value: String,
            ) {
                handler.updateField(id, value)
            }

            override fun perform(actionId: String) {
                scope.launch {
                    handler.perform(actionId)
                }
            }

            override fun resume(value: String) {
                scope.launch {
                    handler.resume(value)
                }
            }

            override fun clear() {
                handler.clear()
            }
        }
    }
}

public data class WebLoginField(
    val id: String,
    val type: LoginFieldType,
    val label: String,
    val placeholder: String?,
    val value: String,
    val readOnly: Boolean,
    val error: String?,
)

public data class WebLoginAction(
    val id: String,
    val label: String,
    val enabled: Boolean,
)

private fun LoginField.toWeb(): WebLoginField =
    WebLoginField(
        id = id,
        type = type,
        label = label.webLabel(),
        placeholder = placeholder?.webLabel(),
        value = value,
        readOnly = readOnly,
        error = error,
    )

private fun LoginAction.toWeb(): WebLoginAction =
    WebLoginAction(
        id = id,
        label = label.webLabel(),
        enabled = enabled,
    )

private fun UiStrings.webLabel(): String =
    when (this) {
        UiStrings.Cancel -> "Cancel"
        UiStrings.Login -> "Login"
        UiStrings.Next -> "Next"
        UiStrings.Username -> "Username"
        UiStrings.Password -> "Password"
        UiStrings.OAuthLogin -> "OAuth"
        UiStrings.PasswordLogin -> "Password"
        UiStrings.QrConnect -> "QR connect"
        UiStrings.CredentialImport -> "Import key"
        UiStrings.ExternalSigner -> "External signer"
        UiStrings.WebCookieLogin -> "Cookie login"
        UiStrings.BlueskyFixDelegationScopes -> "Fix Tranquil permissions"
        else -> name
    }
