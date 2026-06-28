package dev.dimension.flare.ui.presenter.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.dimension.flare.data.datastore.PlatformOAuthPending
import dev.dimension.flare.data.datastore.PlatformOAuthPendingRepository
import dev.dimension.flare.data.network.misskey.MisskeyOauthService
import dev.dimension.flare.data.network.nodeinfo.NodeInfoService
import dev.dimension.flare.data.platform.MisskeyCredential
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.data.repository.addAccount
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlinx.coroutines.delay
import kotlin.native.HiddenFromObjC
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

public class MisskeyCallbackPresenter(
    private val session: String?,
    private val toHome: () -> Unit,
) : PresenterBase<UiState<Nothing>>() {
    private val accountService: AccountService by koinInject()
    private val pendingRepository: PlatformOAuthPendingRepository by koinInject()

    @Composable
    override fun body(): UiState<Nothing> {
        if (session == null) {
            return UiState.Error(Exception("No code"))
        }
        var error by remember { mutableStateOf<Throwable?>(null) }
        LaunchedEffect(session) {
            val pendingOAuth =
                pendingRepository
                    .all(PlatformType.Misskey)
                    .firstOrNull { it.attributes["session"] == session }
                    ?.toMisskeyPending() ?: run {
                    error = Exception("No pending OAuth")
                    return@LaunchedEffect
                }
            runCatching {
                misskeyAuthCheckUseCase(pendingOAuth.host, session, accountService)
                pendingRepository.clear(
                    platformType = PlatformType.Misskey,
                    host = pendingOAuth.host,
                )
                // TODO: delay to workaround iOS NavigationPath.append not working
                delay(2.seconds)
                toHome.invoke()
            }.onFailure {
                error = it
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
        accountService: AccountService,
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
        val nodeInfo = NodeInfoService.fetchNodeInfo(host)
        accountService.addAccount(
            UiAccount(
                accountKey =
                    MicroBlogKey(
                        id = id,
                        host = host,
                    ),
                platformType = PlatformType.Misskey,
            ),
            credential =
                MisskeyCredential(
                    host = host,
                    accessToken = response.token,
                    nodeType = nodeInfo,
                ),
        )
    }
}

@HiddenFromObjC
public suspend fun misskeyLoginUseCase(
    host: String,
    launchOAuth: (String) -> Unit,
): Result<Unit> =
    runCatching {
        val session = Uuid.random().toString()
        val service =
            MisskeyOauthService(
                host = host,
                name = "Flare",
                callback = DeeplinkRoute.Companion.Callback.MISSKEY,
                session = session,
            )
        MisskeyOAuthPendingBridge.pendingRepository.save(
            MisskeyLoginSessionStore
                .Pending(
                    host = host,
                    session = session,
                ).toPlatformOAuthPending(),
        )
        val target = service.getAuthorizeUrl()
        launchOAuth(target)
    }

private object MisskeyOAuthPendingBridge {
    val pendingRepository: PlatformOAuthPendingRepository by koinInject()
}

private object MisskeyLoginSessionStore {
    data class Pending(
        val host: String,
        val session: String,
    )
}

private fun MisskeyLoginSessionStore.Pending.toPlatformOAuthPending(): PlatformOAuthPending =
    PlatformOAuthPending(
        platformType = PlatformType.Misskey,
        host = host,
        createdAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
        attributes = mapOf("session" to session),
    )

private fun PlatformOAuthPending.toMisskeyPending(): MisskeyLoginSessionStore.Pending =
    MisskeyLoginSessionStore.Pending(
        host = host,
        session = attributes.getValue("session"),
    )
