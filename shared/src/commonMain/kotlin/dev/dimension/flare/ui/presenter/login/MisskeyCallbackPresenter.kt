package dev.dimension.flare.ui.presenter.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.dimension.flare.common.AppDeepLink
import dev.dimension.flare.data.network.misskey.MisskeyOauthService
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.ApplicationRepository
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiApplication
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

class MisskeyCallbackPresenter(
    private val session: String?,
    private val toHome: () -> Unit,
) : PresenterBase<UiState<Nothing>>() {
    @Composable
    override fun body(): UiState<Nothing> {
        if (session == null) {
            return UiState.Error(Exception("No code"))
        }
        val applicationRepository: ApplicationRepository = koinInject()
        val accountRepository: AccountRepository = koinInject()
        var error by remember { mutableStateOf<Throwable?>(null) }
        LaunchedEffect(session) {
            val pendingOAuth = applicationRepository.getPendingOAuth()
            if (pendingOAuth == null) {
                error = Exception("No pending OAuth")
            }
            if (pendingOAuth is UiApplication.Misskey) {
                runCatching {
                    misskeyAuthCheckUseCase(pendingOAuth.host, session, accountRepository)
                    applicationRepository.setPendingOAuth(pendingOAuth.host, false)
                    // TODO: delay to workaround iOS NavigationPath.append not working
                    delay(2.seconds)
                    toHome.invoke()
                }.onFailure {
                    error = it
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

    private suspend fun misskeyAuthCheckUseCase(
        host: String,
        session: String,
        accountRepository: AccountRepository,
    ) {
        val response =
            MisskeyOauthService(
                host = host,
                session = session,
            ).check()
        requireNotNull(response.ok) { "No response" }
        require(response.ok) { "Response is not ok" }
        requireNotNull(response.token) { "No token" }
        val id = response.user?.id
        requireNotNull(id) { "No user id" }
        accountRepository.addAccount(
            UiAccount.Misskey(
                credential =
                    UiAccount.Misskey.Credential(
                        host = host,
                        accessToken = response.token,
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

@OptIn(ExperimentalUuidApi::class)
suspend fun misskeyLoginUseCase(
    host: String,
    applicationRepository: ApplicationRepository,
    launchOAuth: (String) -> Unit,
): Result<Unit> =
    runCatching {
        val session = Uuid.random().toString()
        val service =
            MisskeyOauthService(
                host = host,
                name = "Flare",
                callback = AppDeepLink.Callback.MISSKEY,
                session = session,
            )
        applicationRepository.addApplication(
            host = host,
            credentialJson = session,
            platformType = PlatformType.Misskey,
        )
        applicationRepository.clearPendingOAuth()
        applicationRepository.setPendingOAuth(host, true)
        val target = service.getAuthorizeUrl()
        launchOAuth(target)
    }
