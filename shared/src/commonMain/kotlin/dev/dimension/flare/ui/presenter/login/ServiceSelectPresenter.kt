package dev.dimension.flare.ui.presenter.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.dimension.flare.data.repository.ApplicationRepository
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.launch
import org.koin.compose.rememberKoinInject

class ServiceSelectPresenter(
    private val toHome: () -> Unit,
    private val launchUrl: (String) -> Unit,
) : PresenterBase<ServiceSelectState>() {
    @Composable
    override fun body(): ServiceSelectState {
        val nodeInfoState = remember { NodeInfoPresenter() }.body()
        val blueskyLoginState = remember { BlueskyLoginPresenter(toHome) }.body()
        val mastodonLoginState = mastodonLoginPresenter(launchUrl, toHome)
        val misskeyLoginState = misskeyLoginPresenter(launchUrl, toHome)
        val loading =
            blueskyLoginState.loading ||
                mastodonLoginState.loading ||
                mastodonLoginState.resumedState is UiState.Loading ||
                misskeyLoginState.loading ||
                misskeyLoginState.resumedState is UiState.Loading

        return object : ServiceSelectState, NodeInfoState by nodeInfoState {
            override val blueskyLoginState = blueskyLoginState
            override val mastodonLoginState = mastodonLoginState
            override val misskeyLoginState = misskeyLoginState
            override val loading = loading
        }
    }

    @Composable
    private fun misskeyLoginPresenter(
        launchUrl: (String) -> Unit,
        onBack: (() -> Unit)?,
    ): MisskeyLoginState {
        val applicationRepository: ApplicationRepository = rememberKoinInject()
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

            override fun login(host: String) {
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
    private fun mastodonLoginPresenter(
        launchUrl: (String) -> Unit,
        onBack: (() -> Unit)?,
    ): MastodonLoginState {
        val applicationRepository: ApplicationRepository = rememberKoinInject()
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

            override fun login(host: String) {
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
                code = url.substringAfter("code=")
            }
        }
    }
}

interface ServiceSelectState : NodeInfoState {
    val blueskyLoginState: BlueskyLoginState
    val mastodonLoginState: MastodonLoginState
    val misskeyLoginState: MisskeyLoginState
    val loading: Boolean
}

interface MastodonLoginState {
    val loading: Boolean
    val error: String?
    val resumedState: UiState<Nothing>?

    fun login(host: String)

    fun resume(url: String)
}

interface MisskeyLoginState {
    val loading: Boolean
    val error: String?
    val resumedState: UiState<Nothing>?

    fun login(host: String)

    fun resume(url: String)
}
