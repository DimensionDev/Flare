package dev.dimension.flare.ui.presenter.login

import dev.dimension.flare.data.network.mastodon.JoinMastodonService
import dev.dimension.flare.data.network.mastodon.MastodonInstanceService
import dev.dimension.flare.data.network.mastodon.MastodonOAuthService
import dev.dimension.flare.data.network.mastodon.MastodonPlatformDetector
import dev.dimension.flare.data.network.mastodon.api.model.CreateApplicationResponse
import dev.dimension.flare.data.network.nodeinfo.NodeInfoService
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.data.platform.MastodonCredential
import dev.dimension.flare.data.platform.MastodonPlatformSpec
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.data.repository.addAccount
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
import dev.dimension.flare.model.RecommendedInstance
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiInstance
import dev.dimension.flare.ui.model.UiInstanceMetadata
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.mapper.render
import dev.dimension.flare.ui.route.DeeplinkRoute
import io.ktor.http.Url
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

public data object MastodonLoginProvider : LoginPlatformProvider {
    override val platformType: PlatformType = PlatformType.Mastodon
    override val metadata: PlatformTypeMetadata = MastodonPlatformSpec.metadata
    override val detector: PlatformDetector = MastodonPlatformDetector
    override val methods: List<LoginMethodSpec> =
        listOf(
            LoginMethodSpec(
                type = LoginMethodType.OAuth,
                title = UiStrings.OAuthLogin,
            ),
        )

    override fun agreementUrl(host: String): String? = "https://$host/about"

    override suspend fun recommendInstances(): List<RecommendedInstance> {
        val instances =
            coroutineScope {
                listOf(
                    async { joinMastodonInstances() },
                    async { pawooInstance() },
                ).awaitAll().flatten()
            }
        return listOf(
            RecommendedInstance(
                instance =
                    instances.firstOrNull { it.domain == "mstdn.jp" }
                        ?: fallbackInstance("mstdn.jp"),
                priority = 100,
            ),
            RecommendedInstance(
                instance =
                    instances.firstOrNull { it.domain == "pawoo.net" }
                        ?: fallbackInstance("pawoo.net"),
                priority = 90,
            ),
        ) + instances.map { RecommendedInstance(it) }
    }

    override suspend fun instanceMetadata(host: String): UiInstanceMetadata = MastodonInstanceService("https://$host/").instance().render()

    override fun createHandler(context: LoginContext): LoginMethodHandler {
        require(context.methodType == LoginMethodType.OAuth) {
            "Unsupported Mastodon login method: ${context.methodType}"
        }
        return MastodonOAuthLoginHandler(context)
    }

    private suspend fun joinMastodonInstances(): List<UiInstance> =
        tryRun {
            JoinMastodonService.servers().map {
                UiInstance(
                    name = it.domain,
                    description = it.description,
                    iconUrl = null,
                    domain = it.domain,
                    type = platformType,
                    bannerUrl = it.proxiedThumbnail,
                    usersCount = it.totalUsers,
                )
            }
        }.getOrDefault(emptyList())

    private suspend fun pawooInstance(): List<UiInstance> =
        tryRun {
            MastodonInstanceService("https://pawoo.net/").instance().let {
                val domain = it.domain ?: "pawoo.net"
                listOf(
                    UiInstance(
                        name = domain,
                        description = it.title,
                        iconUrl = it.thumbnail?.url,
                        domain = domain,
                        type = platformType,
                        bannerUrl = it.thumbnail?.url,
                        usersCount = it.usage?.users?.activeMonth ?: 0,
                    ),
                )
            }
        }.getOrDefault(emptyList())

    private fun fallbackInstance(domain: String): UiInstance =
        UiInstance(
            name = domain,
            description = domain,
            iconUrl = null,
            domain = domain,
            type = platformType,
            bannerUrl = null,
            usersCount = 0,
        )
}

private class MastodonOAuthLoginHandler(
    private val context: LoginContext,
) : LoginMethodHandler,
    KoinComponent {
    private val accountService: AccountService by inject()
    private val _state = MutableStateFlow(oauthState())
    private val _effects = MutableSharedFlow<LoginEffect>(extraBufferCapacity = 1)

    override val state: StateFlow<LoginFlowState> = _state
    override val effects: Flow<LoginEffect> = _effects

    override fun updateField(
        id: String,
        value: String,
    ) = Unit

    override suspend fun perform(actionId: String) {
        if (actionId != LOGIN_ACTION) return
        _state.value = oauthState(loading = true)
        runCatching {
            val baseUrl =
                if (context.host.startsWith("http://", ignoreCase = true) ||
                    context.host.startsWith("https://", ignoreCase = true)
                ) {
                    Url(context.host)
                } else {
                    Url("https://${context.host}/")
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
                MastodonLoginStore.application(host)
                    ?: service.createApplication().also {
                        MastodonLoginStore.saveApplication(host, it)
                    }
            MastodonLoginStore.pending =
                MastodonLoginStore.Pending(
                    host = host,
                    application = application,
                )
            _effects.emit(LoginEffect.OpenUrl(service.getWebOAuthUrl(application)))
        }.onFailure {
            _state.value = oauthState(error = it.message)
        }
    }

    override suspend fun resume(value: String) {
        _state.value = oauthState(loading = true)
        runCatching {
            val code = value.substringAfter("code=").substringBeforeLast('&')
            require(code.isNotBlank() && code != value) { "No code" }
            val pendingOAuth = MastodonLoginStore.pending ?: error("No pending OAuth")
            tryPendingOAuth(pendingOAuth, code)
            MastodonLoginStore.clearPending()
            context.onSuccess()
        }.onFailure {
            _state.value = oauthState(error = it.message)
        }
    }

    override fun clear() {
        _state.value = oauthState()
    }

    private suspend fun tryPendingOAuth(
        application: MastodonLoginStore.Pending,
        code: String,
    ) {
        val host = application.host
        val service =
            MastodonOAuthService(
                baseUrl = "https://$host/",
                client_name = "Flare",
                website = "https://github.com/DimensionDev/Flare",
                redirect_uri = DeeplinkRoute.Companion.Callback.MASTODON,
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

private fun oauthState(
    loading: Boolean = false,
    error: String? = null,
): LoginFlowState =
    LoginFlowState(
        actions =
            listOf(
                LoginAction(
                    id = LOGIN_ACTION,
                    label = UiStrings.Next,
                    enabled = !loading,
                ),
            ),
        loading = loading,
        error = error,
    )

private object MastodonLoginStore {
    private val applications = mutableMapOf<String, CreateApplicationResponse>()
    var pending: Pending? = null

    fun application(host: String): CreateApplicationResponse? = applications[host]

    fun saveApplication(
        host: String,
        application: CreateApplicationResponse,
    ) {
        applications[host] = application
    }

    fun clearPending() {
        pending = null
    }

    data class Pending(
        val host: String,
        val application: CreateApplicationResponse,
    )
}

private const val LOGIN_ACTION = "login"
