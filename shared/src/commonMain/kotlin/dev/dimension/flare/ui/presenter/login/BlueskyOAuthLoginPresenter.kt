package dev.dimension.flare.ui.presenter.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.presenter.PresenterBase
import io.ktor.client.plugins.DefaultRequest
import io.ktor.http.Url
import io.ktor.http.takeFrom
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import sh.christian.ozone.api.xrpc.BSKY_SOCIAL
import sh.christian.ozone.oauth.OAuthApi
import sh.christian.ozone.oauth.OAuthAuthorizationRequest
import sh.christian.ozone.oauth.OAuthClient
import sh.christian.ozone.oauth.OAuthCodeChallengeMethod
import sh.christian.ozone.oauth.OAuthScope

private const val CLIENT_METADATA = "https://flareapp.moe/client-metadata.json"
private const val REDIRECT_URI = "https://flareapp.moe/callback"

public class BlueskyOAuthLoginPresenter(
    private val toHome: () -> Unit,
) : PresenterBase<BlueskyOAuthLoginPresenter.State>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()
    private val oauthApi by lazy {
        OAuthApi(
            ktorClient {
                install(DefaultRequest) {
                    url.takeFrom(BSKY_SOCIAL)
                }
            },
            { OAuthCodeChallengeMethod.S256 },
        )
    }

    private val oauthClient by lazy {
        OAuthClient(
            clientId = CLIENT_METADATA,
            redirectUri = REDIRECT_URI,
        )
    }

    private var request: OAuthAuthorizationRequest? = null

    public interface State {
        public val loading: Boolean
        public val error: String?

        public fun login(
            userName: String,
            launchUrl: (String) -> Unit,
        )

        public fun resume(url: String)

        public fun clear()
    }

    @Composable
    override fun body(): State {
        var loading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        return object : State {
            override val loading = loading
            override val error = error

            override fun login(
                userName: String,
                launchUrl: (String) -> Unit,
            ) {
                scope.launch {
                    loading = true
                    error = null
                    request = null

                    request =
                        try {
                            login(userName)
                        } catch (e: Exception) {
                            error = e.message
                            null
                        }
                    request?.let {
                        if (it.authorizeRequestUrl.isNotEmpty()) {
                            launchUrl(it.authorizeRequestUrl)
                        } else {
                            error = "Invalid authorization request URL"
                            loading = false
                        }
                    } ?: run {
                        loading = false
                    }
                }
            }

            override fun resume(url: String) {
                scope.launch {
                    if (request == null) {
                        error = "No pending authorization request"
                        return@launch
                    }
                    try {
                        resume(url, request!!)
                        toHome.invoke()
                    } catch (e: Exception) {
                        error = e.message
                    } finally {
                        loading = false
                    }
                }
            }

            override fun clear() {
                error = null
                request = null
                loading = false
            }
        }
    }

    private suspend fun login(userName: String) =
        oauthApi.buildAuthorizationRequest(
            oauthClient = oauthClient,
            scopes =
                listOf(
                    OAuthScope("atproto"),
                    OAuthScope("transition:chat.bsky"),
                    OAuthScope("transition:generic"),
                ),
            loginHandleHint = userName,
        )

    private suspend fun resume(
        url: String,
        request: OAuthAuthorizationRequest,
    ) {
        val parsedUrl = Url(url)
        val code = parsedUrl.parameters["code"]
        val state = parsedUrl.parameters["state"]
        val iss = parsedUrl.parameters["iss"]
        if (code == null || state == null || iss == null) {
            throw IllegalArgumentException("Invalid URL: $url")
        }
        if (state != request.state) {
            throw IllegalArgumentException("State mismatch: expected ${request.state}, got $state")
        }
        val host = Url(iss).host
        val token =
            oauthApi.requestToken(
                oauthClient = oauthClient,
                code = code,
                nonce = request.nonce,
                codeVerifier = request.codeVerifier,
            )
        val credential =
            UiAccount.Bluesky.Credential.OAuthCredential(
                baseUrl = iss,
                oAuthToken = token,
            )
        accountRepository.addAccount(
            UiAccount.Bluesky(
                credential = credential,
                accountKey =
                    MicroBlogKey(
                        id = token.subject.did,
                        host = host,
                    ),
            ),
        )
    }
}
