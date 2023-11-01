package dev.dimension.flare.ui.presenter.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import co.touchlab.kermit.Logger
import com.moriatsushi.koject.compose.rememberInject
import com.moriatsushi.koject.inject
import dev.dimension.flare.common.AppDeepLink
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.network.mastodon.MastodonOAuthService
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.ApplicationRepository
import dev.dimension.flare.mingwgen.annotation.MinGWPresenter
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiApplication
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.presenter.PresenterBase
import io.ktor.http.Url

@MinGWPresenter
class MastodonCallbackPresenter(
    private val code: String?,
    private val toHome: () -> Unit,
) : PresenterBase<UiState<Nothing>>() {
    @Composable
    override fun body(): UiState<Nothing> {
        val applicationRepository: ApplicationRepository = rememberInject()
        val accountRepository: AccountRepository = rememberInject()
        if (code == null) {
            return UiState.Error(Exception("No code"))
        }
        var error by remember { mutableStateOf<Exception?>(null) }
        LaunchedEffect(code) {
            val pendingOAuth = applicationRepository.getPendingOAuth()
            if (pendingOAuth == null) {
                error = Exception("No pending OAuth")
            }
            if (pendingOAuth is UiApplication.Mastodon) {
                tryPendingOAuth(pendingOAuth, code, accountRepository)
                applicationRepository.setPendingOAuth(pendingOAuth.host, false)
                toHome.invoke()
            } else {
                error = Exception("Invalid pending OAuth: $pendingOAuth")
            }
        }
        if (error != null) {
            Logger.e(throwable = error) {
                error.toString()
            }
            return UiState.Error(error!!)
        }
        return UiState.Loading()
    }

    private suspend fun tryPendingOAuth(
        application: UiApplication.Mastodon,
        code: String,
        accountRepository: AccountRepository,
    ) {
        val host = application.host
        val service = MastodonOAuthService(
            baseUrl = "https://$host/",
            client_name = "Flare",
            website = "https://github.com/DimensionDev/Flare",
            redirect_uri = AppDeepLink.Callback.Mastodon,
        )
        val accessTokenResponse = service.getAccessToken(code, application.application)
        requireNotNull(accessTokenResponse.accessToken) { "Invalid access token" }
        val user = service.verifyCredentials(accessToken = accessTokenResponse.accessToken)
        val id = user.id
        requireNotNull(id) { "Invalid user id" }
        accountRepository.addAccount(
            UiAccount.Mastodon(
                credential = UiAccount.Mastodon.Credential(
                    instance = host,
                    accessToken = accessTokenResponse.accessToken,
                ),
                accountKey = MicroBlogKey(
                    id = id,
                    host = host,
                ),
            )
        )
    }
}


suspend fun mastodonLoginUseCase(
    domain: String,
    launchOAuth: (String) -> Unit,
): Result<Unit> {
    return runCatching {
        val applicationRepository: ApplicationRepository = inject()
        val baseUrl = if (domain.startsWith("http://", ignoreCase = true) || domain.startsWith(
                "https://",
                ignoreCase = true,
            )
        ) {
            Url(domain)
        } else {
            Url("https://$domain/")
        }
        val host = baseUrl.host
        val service = MastodonOAuthService(
            baseUrl = baseUrl.toString(),
            client_name = "Flare",
            website = "https://github.com/DimensionDev/Flare",
            redirect_uri = AppDeepLink.Callback.Mastodon,
        )

        val application = applicationRepository.findByHost(host)?.let {
            if (it is UiApplication.Mastodon) {
                it.application
            } else {
                null
            }
        } ?: service.createApplication().also {
            applicationRepository.addApplication(
                host,
                it.encodeJson(),
                platformType = PlatformType.Mastodon,
            )
        }
        applicationRepository.clearPendingOAuth()
        applicationRepository.setPendingOAuth(host, true)
        val target = service.getWebOAuthUrl(application)
        launchOAuth(target)
    }
}