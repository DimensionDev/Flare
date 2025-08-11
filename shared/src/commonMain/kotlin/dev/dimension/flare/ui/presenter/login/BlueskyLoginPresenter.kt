package dev.dimension.flare.ui.presenter.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.atproto.server.CreateSessionRequest
import com.atproto.server.CreateSessionResponse
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.presenter.PresenterBase
import io.ktor.http.Url
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import sh.christian.ozone.api.response.AtpException
import sh.christian.ozone.api.response.AtpResponse
import sh.christian.ozone.api.xrpc.BSKY_SOCIAL

public class BlueskyLoginPresenter(
    private val toHome: () -> Unit,
) : PresenterBase<BlueskyLoginState>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun body(): BlueskyLoginState {
        var error by remember { mutableStateOf<Throwable?>(null) }
        val scope = rememberCoroutineScope()
        var loading by remember { mutableStateOf(false) }
        var require2FA by remember { mutableStateOf(false) }

        return object : BlueskyLoginState {
            override val loading = loading
            override val error = error
            override val require2FA = require2FA

            override fun login(
                baseUrl: String,
                username: String,
                password: String,
                authFactorToken: String?,
            ) {
                scope.launch {
                    loading = true
                    error = null
                    runCatching {
                        blueskyLoginUseCase(
                            baseUrl = BSKY_SOCIAL.toString(),
                            username = username,
                            password = password,
                            authFactorToken = authFactorToken,
                            accountRepository = accountRepository,
                        )
                        toHome.invoke()
                    }.onFailure {
                        if (it is AtpException && it.error?.error == "AuthFactorTokenRequired") {
                            require2FA = true
                        } else {
                            require2FA = false
                        }
                        error = it
                    }
                    loading = false
                }
            }

            override fun clear() {
                error = null
                require2FA = false
                loading = false
            }
        }
    }

    private suspend fun blueskyLoginUseCase(
        baseUrl: String,
        username: String,
        password: String,
        authFactorToken: String?,
        accountRepository: AccountRepository,
    ) {
        val service = BlueskyService(baseUrl)
        val response =
            // check if username is email or custom domain
            if (username.contains("@") || username.contains(".")) {
                service.createSession(
                    CreateSessionRequest(
                        identifier = username,
                        password = password,
                        authFactorToken = authFactorToken,
                    ),
                )
            } else {
                val server = service.describeServer()
                val actualUserName =
                    server.maybeResponse()?.availableUserDomains?.firstOrNull()?.let {
                        "$username$it"
                    } ?: username
                service.createSession(
                    CreateSessionRequest(
                        identifier = actualUserName,
                        password = password,
                        authFactorToken = authFactorToken,
                    ),
                )
            }.let {
                when (it) {
                    is AtpResponse.Failure<CreateSessionResponse> ->
                        throw AtpException(
                            statusCode = it.statusCode,
                            error = it.error,
                        )
                    is AtpResponse.Success<CreateSessionResponse> -> it.response
                }
            }

        accountRepository.addAccount(
            UiAccount.Bluesky(
                accountKey =
                    MicroBlogKey(
                        id = response.did.did,
                        host = Url(baseUrl).host,
                    ),
            ),
            credential =
                UiAccount.Bluesky.Credential.BlueskyCredential(
                    baseUrl = baseUrl,
                    accessToken = response.accessJwt,
                    refreshToken = response.refreshJwt,
                ),
        )
    }
}

@Immutable
public interface BlueskyLoginState {
    public val loading: Boolean
    public val error: Throwable?
    public val require2FA: Boolean

    public fun login(
        baseUrl: String,
        username: String,
        password: String,
        authFactorToken: String? = null,
    )

    public fun clear()
}
