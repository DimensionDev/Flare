package dev.dimension.flare.ui.presenter.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.atproto.server.CreateSessionRequest
import com.atproto.server.CreateSessionResponse
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.data.platform.BlueskyCredential
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.presenter.PresenterBase
import io.ktor.http.Url
import kotlinx.coroutines.launch
import dev.dimension.flare.di.koinInject
import sh.christian.ozone.api.response.AtpException
import sh.christian.ozone.api.response.AtpResponse

internal class BlueskyLoginPresenter(
    private val toHome: () -> Unit,
) : PresenterBase<BlueskyLoginState>() {
    private val accountService: AccountService by koinInject()

    @Composable
    override fun body(): BlueskyLoginState {
        var error by remember { mutableStateOf<Throwable?>(null) }
        val scope = rememberCoroutineScope()
        var loading by remember { mutableStateOf(false) }
        var require2FA by remember { mutableStateOf(false) }

        return object : BlueskyLoginState {
            override val loading = loading
            override val error = error
            override val errorMessage: String?
                get() =
                    error?.let {
                        if (it is AtpException) {
                            it.error?.message ?: it.message
                        } else {
                            it.message
                        }
                    }
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
                            baseUrl = "https://$baseUrl",
                            username = username,
                            password = password,
                            authFactorToken = authFactorToken?.takeIf { it.isNotEmpty() && require2FA },
                            accountService = accountService,
                        )
                        toHome.invoke()
                    }.onFailure {
                        if (it is AtpException && it.error?.error == "AuthFactorTokenRequired") {
                            require2FA = true
                        } else {
                            error = it
                        }
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
        accountService: AccountService,
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
                    server
                        .maybeResponse()
                        ?.availableUserDomains
                        ?.firstOrNull()
                        ?.let { username.withBlueskyUserDomain(it) }
                        ?: username
                service.createSession(
                    CreateSessionRequest(
                        identifier = actualUserName,
                        password = password,
                        authFactorToken = authFactorToken,
                    ),
                )
            }.let {
                when (it) {
                    is AtpResponse.Failure<CreateSessionResponse> -> {
                        throw AtpException(
                            statusCode = it.statusCode,
                            error = it.error,
                        )
                    }

                    is AtpResponse.Success<CreateSessionResponse> -> {
                        it.response
                    }
                }
            }

        val credential: BlueskyCredential =
            BlueskyCredential.Password(
                baseUrl = baseUrl,
                accessToken = response.accessJwt,
                refreshToken = response.refreshJwt,
            )
        accountService.addAccount(
            account =
                UiAccount(
                    accountKey =
                        MicroBlogKey(
                            id = response.did.did,
                            host = Url(baseUrl).host,
                        ),
                    platformType = PlatformType.Bluesky,
                ),
            credential = credential,
            serializer = BlueskyCredential.serializer(),
        )
    }
}
