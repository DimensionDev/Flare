package dev.dimension.flare.ui.presenter.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.network.tumblr.TumblrOAuth2Config
import dev.dimension.flare.data.network.tumblr.TumblrOAuth2Service
import dev.dimension.flare.data.network.tumblr.TumblrService
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.ApplicationRepository
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiApplication
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.presenter.PresenterBase
import io.ktor.http.Url
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.uuid.Uuid

internal class TumblrCallbackPresenter(
    private val callbackUrl: String?,
    private val toHome: () -> Unit,
) : PresenterBase<UiState<Nothing>>(),
    KoinComponent {
    private val applicationRepository: ApplicationRepository by inject()
    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun body(): UiState<Nothing> {
        if (callbackUrl == null) {
            return UiState.Error(Exception("No callback URL"))
        }
        var error by remember { mutableStateOf<Throwable?>(null) }
        LaunchedEffect(callbackUrl) {
            val pendingOAuth = applicationRepository.getPendingOAuth()
            if (pendingOAuth !is UiApplication.Tumblr) {
                error = Exception("Invalid pending OAuth: $pendingOAuth")
                return@LaunchedEffect
            }
            runCatching {
                val parsed = Url(callbackUrl)
                val code = parsed.parameters["code"] ?: error("No code")
                val state = parsed.parameters["state"] ?: error("No state")
                val expectedState = pendingOAuth.credential.authState ?: error("No pending OAuth state")
                require(state == expectedState) { "Tumblr OAuth state mismatch" }
                val token =
                    TumblrOAuth2Service().requestToken(
                        code = code,
                        clientId = pendingOAuth.credential.consumerKey,
                        clientSecret = pendingOAuth.credential.consumerSecret,
                        redirectUri = TumblrOAuth2Config.REDIRECT_URI,
                    )
                val userInfo =
                    TumblrService(
                        consumerKey = pendingOAuth.credential.consumerKey,
                        accessToken = token.accessToken,
                    ).userInfo()
                val primaryBlog =
                    userInfo.user.blogs.firstOrNull { it.primary }
                        ?: userInfo.user.blogs.firstOrNull()
                        ?: error("Tumblr user has no blogs")
                accountRepository.addAccount(
                    UiAccount.Tumblr(
                        accountKey = MicroBlogKey(primaryBlog.name, "tumblr.com"),
                        blogIdentifier = "${primaryBlog.name}.tumblr.com",
                        blogName = primaryBlog.name,
                        blogUrl = primaryBlog.url,
                        userName = userInfo.user.name,
                    ),
                    credential =
                        UiAccount.Tumblr.Credential(
                            consumerKey = pendingOAuth.credential.consumerKey,
                            accessToken = token.accessToken,
                            refreshToken = token.refreshToken,
                            expiresIn = token.expiresIn,
                            scope = token.scope,
                            blogIdentifier = "${primaryBlog.name}.tumblr.com",
                            blogName = primaryBlog.name,
                            blogUrl = primaryBlog.url,
                            userName = userInfo.user.name,
                        ),
                )
                applicationRepository.setPendingOAuth(pendingOAuth.host, false)
                toHome()
            }.onFailure {
                error = it
            }
        }
        return error?.let { UiState.Error(it) } ?: UiState.Loading()
    }
}

internal suspend fun tumblrLoginUseCase(
    applicationRepository: ApplicationRepository,
    launchOAuth: (String) -> Unit,
): Result<Unit> =
    runCatching {
        val state = Uuid.random().toString()
        val credential = TumblrOAuth2Config.applicationCredential(state = state)
        applicationRepository.addApplication(
            host = TumblrOAuth2Config.HOST,
            credentialJson = credential.encodeJson(),
            platformType = PlatformType.Tumblr,
        )
        applicationRepository.clearPendingOAuth()
        applicationRepository.setPendingOAuth(TumblrOAuth2Config.HOST, true)
        launchOAuth(TumblrOAuth2Config.buildAuthorizeUrl(state = state))
    }
