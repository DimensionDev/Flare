package dev.dimension.flare.ui.presenter.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.dimension.flare.data.network.bluesky.OAuthCodeChallengeMethodS256
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.platform.BlueskyCredential
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.presenter.PresenterBase
import io.ktor.client.plugins.DefaultRequest
import io.ktor.http.Url
import io.ktor.http.takeFrom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import sh.christian.ozone.oauth.OAuthApi
import sh.christian.ozone.oauth.OAuthAuthorizationRequest
import sh.christian.ozone.oauth.OAuthClient
import sh.christian.ozone.oauth.OAuthScope

private const val CLIENT_METADATA = "https://flareapp.moe/client-metadata.json"
private const val REDIRECT_URI = "https://flareapp.moe/callback"

internal class BlueskyOAuthLoginPresenter(
    private val toHome: () -> Unit,
) : PresenterBase<BlueskyOAuthLoginState>(),
    KoinComponent {
    private val accountService: AccountService by inject()
    private var oauthApi: OAuthApi? = null

    private val oauthClient by lazy {
        OAuthClient(
            clientId = CLIENT_METADATA,
            redirectUri = REDIRECT_URI,
        )
    }

    private var request: OAuthAuthorizationRequest? = null

    @Composable
    override fun body(): BlueskyOAuthLoginState {
        var loading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()
        return object : BlueskyOAuthLoginState {
            override val loading = loading
            override val error = error

            override fun login(
                baseUrl: String,
                userName: String,
                launchUrl: (String) -> Unit,
            ) {
                scope.launch {
                    loading = true
                    error = null
                    request = null
                    oauthApi =
                        OAuthApi(
                            ktorClient {
                                install(DefaultRequest) {
                                    url.takeFrom("https://$baseUrl")
                                }
                            },
                            { OAuthCodeChallengeMethodS256 },
                        )

                    request =
                        try {
                            login(userName)
                        } catch (e: Exception) {
                            error = e.message
                            null
                        }
                    request?.let {
                        if (it.authorizeRequestUrl.isNotEmpty()) {
                            scope.launch {
                                withContext(Dispatchers.Main) {
                                    launchUrl(it.authorizeRequestUrl)
                                }
                            }
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

    private suspend fun login(userName: String): OAuthAuthorizationRequest? =
        oauthApi?.buildAuthorizationRequest(
            oauthClient = oauthClient,
            scopes =
                listOf(
                    OAuthScope("atproto"),
                    OAuthScope("transition:chat.bsky"),
                    OAuthScope("transition:generic"),
                ),
            loginHandleHint = userName.takeIf { !it.contains('@') && it.contains('.') },
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
            oauthApi?.requestToken(
                oauthClient = oauthClient,
                code = code,
                nonce = request.nonce,
                codeVerifier = request.codeVerifier,
            )
        requireNotNull(token) {
            "Failed to obtain access token from $iss"
        }
        val credential: BlueskyCredential =
            BlueskyCredential.OAuthCredential(
                baseUrl = iss,
                oAuthToken = token,
            )
        accountService.addAccount(
            account =
                UiAccount(
                    accountKey =
                        MicroBlogKey(
                            id = token.subject.did,
                            host = host,
                        ),
                    platformType = PlatformType.Bluesky,
                ),
            credential = credential,
            serializer = BlueskyCredential.serializer(),
        )
    }
}
