package dev.dimension.flare.ui.presenter.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.dimension.flare.data.repository.ApplicationRepository
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public class ServiceSelectPresenter(
    private val toHome: () -> Unit,
) : PresenterBase<ServiceSelectState>(),
    KoinComponent {
    private val applicationRepository: ApplicationRepository by inject()

    @Composable
    override fun body(): ServiceSelectState {
        val nodeInfoState = remember { NodeInfoPresenter() }.body()
        val blueskyLoginState = remember { BlueskyLoginPresenter(toHome) }.body()
        val blueskyOauthLoginState = remember { BlueskyOAuthLoginPresenter(toHome) }.body()
        val mastodonLoginState = mastodonLoginPresenter(toHome)
        val misskeyLoginState = misskeyLoginPresenter(toHome)
        val loading =
            blueskyLoginState.loading ||
                mastodonLoginState.loading ||
                mastodonLoginState.resumedState is UiState.Loading ||
                misskeyLoginState.loading ||
                misskeyLoginState.resumedState is UiState.Loading

        return object : ServiceSelectState, NodeInfoState by nodeInfoState {
            override val blueskyLoginState = blueskyLoginState
            override val blueskyOauthLoginState = blueskyOauthLoginState
            override val mastodonLoginState = mastodonLoginState
            override val misskeyLoginState = misskeyLoginState
            override val loading = loading
        }
    }

    @Composable
    private fun misskeyLoginPresenter(onBack: (() -> Unit)?): MisskeyLoginState {
        var loading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        var session by remember { mutableStateOf<String?>(null) }
        val resumedState =
            session?.let {
                remember {
                    MisskeyCallbackPresenter(
                        session = session,
                        toHome = {
                            session = null
                            loading = false
                            error = null
                            onBack?.invoke()
                        },
                    )
                }.body()
            }
        return object : MisskeyLoginState {
            override val loading = loading
            override val error = error
            override val resumedState = resumedState

            override fun login(
                host: String,
                launchUrl: (String) -> Unit,
            ) {
                scope.launch {
                    loading = true
                    error = null
                    misskeyLoginUseCase(
                        host = host,
                        applicationRepository = applicationRepository,
                        launchOAuth = launchUrl,
                    ).onFailure {
                        error = it.message
                    }
                    loading = false
                }
            }

            override fun resume(url: String) {
                session = url.substringAfter("session=")
            }
        }
    }

    @Composable
    private fun mastodonLoginPresenter(onBack: (() -> Unit)?): MastodonLoginState {
        var loading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        var code by remember { mutableStateOf<String?>(null) }
        val resumedState =
            code?.let {
                remember {
                    MastodonCallbackPresenter(
                        code = code,
                        toHome = {
                            code = null
                            loading = false
                            error = null
                            onBack?.invoke()
                        },
                    )
                }.body()
            }
        return object : MastodonLoginState {
            override val loading = loading
            override val error = error
            override val resumedState = resumedState

            override fun login(
                host: String,
                launchUrl: (String) -> Unit,
            ) {
                scope.launch {
                    loading = true
                    error = null
                    mastodonLoginUseCase(
                        domain = host,
                        applicationRepository = applicationRepository,
                        launchOAuth = launchUrl,
                    ).onFailure {
                        error = it.message
                        loading = false
                    }
                }
            }

            override fun resume(url: String) {
                code = url.substringAfter("code=").substringBeforeLast('&')
            }
        }
    }
}

@Immutable
public interface ServiceSelectState : NodeInfoState {
    public val blueskyLoginState: BlueskyLoginState
    public val blueskyOauthLoginState: BlueskyOAuthLoginPresenter.State
    public val mastodonLoginState: MastodonLoginState
    public val misskeyLoginState: MisskeyLoginState
    public val loading: Boolean
}

@Immutable
public interface MastodonLoginState {
    public val loading: Boolean
    public val error: String?
    public val resumedState: UiState<Nothing>?

    public fun login(
        host: String,
        launchUrl: (String) -> Unit,
    )

    public fun resume(url: String)
}

@Immutable
public interface MisskeyLoginState {
    public val loading: Boolean
    public val error: String?
    public val resumedState: UiState<Nothing>?

    public fun login(
        host: String,
        launchUrl: (String) -> Unit,
    )

    public fun resume(url: String)
}
