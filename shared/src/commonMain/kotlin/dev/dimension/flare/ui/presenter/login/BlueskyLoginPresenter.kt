package dev.dimension.flare.ui.presenter.login

import androidx.compose.runtime.*
import com.atproto.server.CreateSessionRequest
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.presenter.PresenterBase
import io.ktor.http.*
import kotlinx.coroutines.launch
import org.koin.compose.rememberKoinInject



class BlueskyLoginPresenter(
    private val toHome: () -> Unit,
): PresenterBase<BlueskyLoginState>() {

    @Composable
    override fun body(): BlueskyLoginState {
        var error by remember { mutableStateOf<Throwable?>(null) }
        val scope = rememberCoroutineScope()
        var loading by remember { mutableStateOf(false) }
        val accountRepository: AccountRepository = rememberKoinInject()

        return object : BlueskyLoginState(
            loading,
            error
        ) {
            override fun login(baseUrl: String, username: String, password: String) {
                scope.launch {
                    loading = true
                    error = null
                    runCatching {
                        blueskyLoginUseCase(
                            baseUrl = baseUrl,
                            username = username,
                            password = password,
                            accountRepository = accountRepository,
                        )
                        toHome.invoke()
                    }.onFailure {
                        error = it
                    }
                    loading = false
                }
            }
        }
    }

    private suspend fun blueskyLoginUseCase(
        baseUrl: String,
        username: String,
        password: String,
        accountRepository: AccountRepository,
    ) {
        val service = BlueskyService(baseUrl)
        // check if username is email
        val response = if (username.contains("@")) {
            service.createSession(CreateSessionRequest(username, password))
        } else {
            val server = service.describeServer()
            val actualUserName = server.maybeResponse()?.availableUserDomains?.firstOrNull()?.let {
                "$username$it"
            } ?: username
            service.createSession(CreateSessionRequest(actualUserName, password))
        }.requireResponse()
        accountRepository.addAccount(
            UiAccount.Bluesky(
                credential = UiAccount.Bluesky.Credential(
                    baseUrl = baseUrl,
                    accessToken = response.accessJwt,
                    refreshToken = response.refreshJwt,
                ),
                accountKey = MicroBlogKey(
                    id = response.did.did,
                    host = Url(baseUrl).host,
                ),
            )
        )
    }
}

abstract class BlueskyLoginState(
    val loading: Boolean,
    val error: Throwable?,
) {
    abstract fun login(baseUrl: String, username: String, password: String)
}