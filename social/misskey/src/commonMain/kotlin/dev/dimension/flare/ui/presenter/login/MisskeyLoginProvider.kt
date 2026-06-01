package dev.dimension.flare.ui.presenter.login

import dev.dimension.flare.data.network.misskey.JoinMisskeyService
import dev.dimension.flare.data.network.misskey.MisskeyOauthService
import dev.dimension.flare.data.network.misskey.MisskeyPlatformDetector
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.MetaRequest
import dev.dimension.flare.data.network.nodeinfo.NodeInfoService
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.data.platform.MisskeyCredential
import dev.dimension.flare.data.platform.MisskeyPlatformSpec
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

public data object MisskeyLoginProvider : LoginPlatformProvider {
    override val platformType: PlatformType = PlatformType.Misskey
    override val metadata: PlatformTypeMetadata
        get() = MisskeyPlatformSpec.metadata
    override val detector: PlatformDetector = MisskeyPlatformDetector
    override val methods: List<LoginMethodSpec> =
        listOf(
            LoginMethodSpec(
                type = LoginMethodType.OAuth,
                title = UiStrings.OAuthLogin,
            ),
        )

    override fun agreementUrl(host: String): String? = "https://$host/about"

    override suspend fun recommendInstances(): List<RecommendedInstance> =
        tryRun {
            JoinMisskeyService
                .instances()
                .instancesInfos
                .map {
                    RecommendedInstance(
                        UiInstance(
                            name = it.name,
                            description = it.description,
                            iconUrl = it.meta?.iconURL,
                            domain = it.url,
                            type = platformType,
                            bannerUrl = it.meta?.bannerURL,
                            usersCount =
                                it.stats?.usersCount ?: it.nodeinfo
                                    ?.usage
                                    ?.users
                                    ?.total ?: 0,
                        ),
                    )
                }
        }.getOrDefault(emptyList())

    override suspend fun instanceMetadata(host: String): UiInstanceMetadata =
        MisskeyService("https://$host/api/").meta(MetaRequest()).render()

    override fun createHandler(context: LoginContext): LoginMethodHandler {
        require(context.methodType == LoginMethodType.OAuth) {
            "Unsupported Misskey login method: ${context.methodType}"
        }
        return MisskeyOAuthLoginHandler(context)
    }
}

private class MisskeyOAuthLoginHandler(
    private val context: LoginContext,
) : LoginMethodHandler,
    KoinComponent {
    private val accountService: AccountService by inject()
    private val _state = MutableStateFlow(misskeyOAuthState())
    private val _effects = MutableSharedFlow<LoginEffect>(extraBufferCapacity = 1)

    override val state: StateFlow<LoginFlowState> = _state
    override val effects: Flow<LoginEffect> = _effects

    override fun updateField(
        id: String,
        value: String,
    ) = Unit

    override suspend fun perform(actionId: String) {
        if (actionId != LOGIN_ACTION) return
        _state.value = misskeyOAuthState(loading = true)
        runCatching {
            val session = Uuid.random().toString()
            val service =
                MisskeyOauthService(
                    host = context.host,
                    name = "Flare",
                    callback = DeeplinkRoute.Companion.Callback.MISSKEY,
                    session = session,
                )
            MisskeyLoginStore.pending =
                MisskeyLoginStore.Pending(
                    host = context.host,
                    session = session,
                )
            _effects.emit(LoginEffect.OpenUrl(service.getAuthorizeUrl()))
        }.onFailure {
            _state.value = misskeyOAuthState(error = it.message)
        }
    }

    override suspend fun resume(value: String) {
        _state.value = misskeyOAuthState(loading = true)
        runCatching {
            val session = value.substringAfter("session=")
            require(session.isNotBlank() && session != value) { "No session" }
            val pendingOAuth = MisskeyLoginStore.pending ?: error("No pending OAuth")
            require(pendingOAuth.session == session) { "Invalid pending OAuth" }
            misskeyAuthCheckUseCase(pendingOAuth.host, session)
            MisskeyLoginStore.clearPending()
            // Preserve the existing iOS NavigationPath workaround.
            delay(2.seconds)
            context.onSuccess()
        }.onFailure {
            _state.value = misskeyOAuthState(error = it.message)
        }
    }

    override fun clear() {
        _state.value = misskeyOAuthState()
    }

    private suspend fun misskeyAuthCheckUseCase(
        host: String,
        session: String,
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

private fun misskeyOAuthState(
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

private object MisskeyLoginStore {
    var pending: Pending? = null

    fun clearPending() {
        pending = null
    }

    data class Pending(
        val host: String,
        val session: String,
    )
}

private const val LOGIN_ACTION = "login"
