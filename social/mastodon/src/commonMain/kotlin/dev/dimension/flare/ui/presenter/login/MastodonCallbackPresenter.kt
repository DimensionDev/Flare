package dev.dimension.flare.ui.presenter.login

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.dimension.flare.data.datastore.PlatformOAuthPending
import dev.dimension.flare.data.datastore.PlatformOAuthPendingRepository
import dev.dimension.flare.data.network.mastodon.MastodonOAuthService
import dev.dimension.flare.data.network.mastodon.api.model.CreateApplicationResponse
import dev.dimension.flare.data.network.nodeinfo.NodeInfoService
import dev.dimension.flare.data.platform.MastodonCredential
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.data.repository.addAccount
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.route.DeeplinkRoute
import io.ktor.http.Url
import dev.dimension.flare.di.koinInject
import kotlin.native.HiddenFromObjC
import kotlin.time.Clock

public class MastodonCallbackPresenter(
    private val code: String?,
    private val toHome: () -> Unit,
) : PresenterBase<UiState<Nothing>>() {
    private val accountService: AccountService by koinInject()
    private val pendingRepository: PlatformOAuthPendingRepository by koinInject()

    @Composable
    override fun body(): UiState<Nothing> {
        if (code == null) {
            return UiState.Error(Exception("No code"))
        }
        var error by remember { mutableStateOf<Throwable?>(null) }
        LaunchedEffect(code) {
            val pendingOAuth =
                pendingRepository.latest(PlatformType.Mastodon)?.toMastodonPending() ?: run {
                    error = Exception("No pending OAuth")
                    return@LaunchedEffect
                }
            try {
                tryPendingOAuth(pendingOAuth, code, accountService)
                pendingRepository.clear(
                    platformType = PlatformType.Mastodon,
                    host = pendingOAuth.host,
                )
                toHome.invoke()
            } catch (e: Exception) {
                error = e
            }
        }
        if (error != null) {
            return UiState.Error(error!!)
        }
        return UiState.Loading()
    }

    private suspend fun tryPendingOAuth(
        application: MastodonLoginSessionStore.Pending,
        code: String,
        accountService: AccountService,
    ) {
        val host = application.host
        val service =
            MastodonOAuthService(
                baseUrl = "https://$host/",
                client_name = "Flare",
                website = "https://github.com/DimensionDev/Flare",
                redirect_uri = application.application.redirectURI,
            )
        val accessTokenResponse = service.getAccessToken(code, application.application)
        requireNotNull(accessTokenResponse.accessToken) { "Invalid access token" }
        val user = service.verify(accessToken = accessTokenResponse.accessToken)
        val id = user.id
        requireNotNull(id) { "Invalid user id" }
        val nodeInfo = NodeInfoService.fetchNodeInfo(host)
        val forkType =
            if (nodeInfo in NodeInfoService.pleromaNodeInfoName) {
                MastodonCredential.ForkType.Pleroma
            } else {
                MastodonCredential.ForkType.Mastodon
            }
        accountService.addAccount(
            UiAccount(
                accountKey =
                    MicroBlogKey(
                        id = id,
                        host = host,
                    ),
                platformType = PlatformType.Mastodon,
            ),
            credential =
                MastodonCredential(
                    instance = host,
                    accessToken = accessTokenResponse.accessToken,
                    forkType = forkType,
                    nodeType = nodeInfo,
                ),
        )
    }
}

@HiddenFromObjC
public suspend fun mastodonLoginUseCase(
    domain: String,
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
                redirect_uri = DeeplinkRoute.Companion.Callback.MASTODON,
            )

        val application =
            MastodonLoginSessionStore.application(host)
                ?: service.createApplication().also {
                    MastodonLoginSessionStore.saveApplication(host, it)
                }
        MastodonOAuthPendingBridge.pendingRepository.save(
            MastodonLoginSessionStore
                .Pending(
                    host = host,
                    application = application,
                ).toPlatformOAuthPending(),
        )
        val target = service.getWebOAuthUrl(application)
        launchOAuth(target)
    }

private object MastodonOAuthPendingBridge  {
    val pendingRepository: PlatformOAuthPendingRepository by koinInject()
}

private object MastodonLoginSessionStore {
    private val applications = mutableMapOf<String, CreateApplicationResponse>()

    fun application(host: String): CreateApplicationResponse? = applications[host]

    fun saveApplication(
        host: String,
        application: CreateApplicationResponse,
    ) {
        applications[host] = application
    }

    data class Pending(
        val host: String,
        val application: CreateApplicationResponse,
    )
}

private fun MastodonLoginSessionStore.Pending.toPlatformOAuthPending(): PlatformOAuthPending =
    PlatformOAuthPending(
        platformType = PlatformType.Mastodon,
        host = host,
        createdAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
        attributes =
            mapOf(
                "application_id" to application.id.orEmpty(),
                "application_name" to application.name.orEmpty(),
                "application_website" to application.website.orEmpty(),
                "redirect_uri" to application.redirectURI,
                "client_id" to application.clientID,
                "client_secret" to application.clientSecret,
                "vapid_key" to application.vapidKey.orEmpty(),
            ),
    )

private fun PlatformOAuthPending.toMastodonPending(): MastodonLoginSessionStore.Pending {
    val redirectUri = attributes.getValue("redirect_uri")
    val clientId = attributes.getValue("client_id")
    val clientSecret = attributes.getValue("client_secret")
    return MastodonLoginSessionStore.Pending(
        host = host,
        application =
            CreateApplicationResponse(
                id = attributes["application_id"]?.takeIf { it.isNotBlank() },
                name = attributes["application_name"]?.takeIf { it.isNotBlank() },
                website = attributes["application_website"]?.takeIf { it.isNotBlank() },
                redirectURI = redirectUri,
                clientID = clientId,
                clientSecret = clientSecret,
                vapidKey = attributes["vapid_key"]?.takeIf { it.isNotBlank() },
            ),
    )
}
