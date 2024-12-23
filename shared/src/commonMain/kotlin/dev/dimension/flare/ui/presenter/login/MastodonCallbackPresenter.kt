package dev.dimension.flare.ui.presenter.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.dimension.flare.common.AppDeepLink
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.network.mastodon.MastodonOAuthService
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

public class MastodonCallbackPresenter(
    private val code: String?,
    private val toHome: () -> Unit,
) : PresenterBase<UiState<Nothing>>(),
    KoinComponent {
    private val applicationRepository: ApplicationRepository by inject()
    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun body(): UiState<Nothing> {
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
                try {
                    tryPendingOAuth(pendingOAuth, code, accountRepository)
                    applicationRepository.setPendingOAuth(pendingOAuth.host, false)
                    toHome.invoke()
                } catch (e: Exception) {
                    error = e
                }
            } else {
                error = Exception("Invalid pending OAuth: $pendingOAuth")
            }
        }
        if (error != null) {
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
        val service =
            MastodonOAuthService(
                baseUrl = "https://$host/",
                client_name = "Flare",
                website = "https://github.com/DimensionDev/Flare",
                redirect_uri = AppDeepLink.Callback.MASTODON,
            )
        val accessTokenResponse = service.getAccessToken(code, application.application)
        requireNotNull(accessTokenResponse.accessToken) { "Invalid access token" }
        val user = service.verify(accessToken = accessTokenResponse.accessToken)
        val id = user.id
        requireNotNull(id) { "Invalid user id" }
        accountRepository.addAccount(
            UiAccount.Mastodon(
                credential =
                    UiAccount.Mastodon.Credential(
                        instance = host,
                        accessToken = accessTokenResponse.accessToken,
                    ),
                accountKey =
                    MicroBlogKey(
                        id = id,
                        host = host,
                    ),
            ),
        )
    }
}

internal suspend fun mastodonLoginUseCase(
    domain: String,
    applicationRepository: ApplicationRepository,
    launchOAuth: (String) -> Unit,
): Result<Unit> =
    runCatching {
        val baseUrl =
            if (domain.startsWith("http://", ignoreCase = true) ||
                domain.startsWith(
                    "https://",
                    ignoreCase = true,
                )
            ) {
                Url(domain)
            } else {
                Url("https://$domain/")
            }
        val host = baseUrl.host
        val service =
            MastodonOAuthService(
                baseUrl = baseUrl.toString(),
                client_name = "Flare",
                website = "https://github.com/DimensionDev/Flare",
                redirect_uri = AppDeepLink.Callback.MASTODON,
            )

        val application =
            applicationRepository.findByHost(host)?.let {
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
