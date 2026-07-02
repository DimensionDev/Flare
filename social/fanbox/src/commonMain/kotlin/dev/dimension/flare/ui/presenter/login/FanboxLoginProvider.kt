package dev.dimension.flare.ui.presenter.login

import dev.dimension.flare.data.network.fanbox.FanboxPlatformDetector
import dev.dimension.flare.data.network.fanbox.FanboxService
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.data.platform.FANBOX_HOST
import dev.dimension.flare.data.platform.FanboxCredential
import dev.dimension.flare.data.platform.FanboxPlatformSpec
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
import dev.dimension.flare.model.RecommendedInstance
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiInstance
import dev.dimension.flare.ui.model.UiInstanceMetadata
import dev.dimension.flare.ui.model.UiStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private const val LOGIN_ACTION = "login"
private const val FANBOX_SESSION_COOKIE = "FANBOXSESSID"
private const val FANBOX_LOGIN_URL = "https://www.fanbox.cc/login"

public data object FanboxLoginProvider : LoginPlatformProvider {
    override val platformType: PlatformType = PlatformType.Fanbox
    override val metadata: PlatformTypeMetadata
        get() = FanboxPlatformSpec.metadata
    override val detector: PlatformDetector = FanboxPlatformDetector
    override val methods: List<LoginMethodSpec> =
        listOf(
            LoginMethodSpec(
                type = LoginMethodType.WebCookie,
                title = UiStrings.WebCookieLogin,
            ),
        )

    override fun agreementUrl(host: String): String? = "https://www.fanbox.cc/terms"

    override suspend fun recommendInstances(): List<RecommendedInstance> =
        listOf(
            RecommendedInstance(
                instance =
                    UiInstance(
                        name = "FANBOX",
                        description = "Creator posts and supporter-only content.",
                        iconUrl = null,
                        domain = FANBOX_HOST,
                        type = platformType,
                        bannerUrl = null,
                        usersCount = 0,
                    ),
                priority = 70,
            ),
        )

    override suspend fun instanceMetadata(host: String): UiInstanceMetadata =
        throw UnsupportedOperationException("${platformType.name} metadata is not supported yet")

    override fun createHandler(context: LoginContext): LoginMethodHandler {
        require(context.methodType == LoginMethodType.WebCookie) {
            "Unsupported FANBOX login method: ${context.methodType}"
        }
        return FanboxWebCookieLoginHandler(context)
    }
}

private class FanboxWebCookieLoginHandler(
    private val context: LoginContext,
) : LoginMethodHandler {
    private val accountService: AccountService by koinInject()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow(state())
    private val _effects = MutableSharedFlow<LoginEffect>(extraBufferCapacity = 1)
    private val validatedSessionId = MutableStateFlow<String?>(null)
    private val validatingSessionId = MutableStateFlow<String?>(null)

    override val state: StateFlow<LoginFlowState> = _state
    override val effects: Flow<LoginEffect> = _effects

    override fun updateField(
        id: String,
        value: String,
    ) = Unit

    override suspend fun perform(actionId: String) {
        if (actionId != LOGIN_ACTION) return
        _effects.emit(
            LoginEffect.OpenWebCookieLogin(
                url = FANBOX_LOGIN_URL,
            ),
        )
    }

    override suspend fun resume(value: String) {
        _state.value = state(loading = true)
        runCatching {
            val sessionId = value.extractFanboxSession()
            require(!sessionId.isNullOrBlank()) { "FANBOX session cookie is missing" }
            val service = FanboxService(flowOf(FanboxCredential(sessionId = sessionId, userId = "")))
            val metadata = service.metadata(sessionId)
            val user = metadata.context?.user
            val userId = user?.userId
            require(!userId.isNullOrBlank()) { "FANBOX user id is missing" }
            val credential =
                FanboxCredential(
                    sessionId = sessionId,
                    csrfToken = metadata.csrfToken,
                    userId = userId,
                    creatorId = user.creatorId,
                    name = user.name,
                    iconUrl = user.iconUrl,
                    showAdultContent = user.showAdultContent,
                    isSupporter = user.isSupporter,
                    isCreator = user.isCreator,
                )
            val accountKey = MicroBlogKey(id = userId, host = FANBOX_HOST)
            context.requireReloginAccount(accountKey)
            accountService.addAccount(
                account =
                    UiAccount(
                        accountKey = accountKey,
                        platformType = PlatformType.Fanbox,
                    ),
                credential = credential,
                serializer = FanboxCredential.serializer(),
            )
            context.onSuccess()
        }.onFailure {
            _state.value = state(error = it.message)
        }
    }

    override fun canResume(value: String): Boolean {
        val sessionId = value.extractFanboxSession() ?: return false
        if (validatedSessionId.value == sessionId) {
            return true
        }
        if (validatingSessionId.value != sessionId) {
            validatingSessionId.value = sessionId
            scope.launch {
                val isLoggedIn =
                    runCatching {
                        val service = FanboxService(flowOf(FanboxCredential(sessionId = sessionId, userId = "")))
                        val user = service.metadata(sessionId).context?.user
                        !user?.userId.isNullOrBlank()
                    }.getOrDefault(false)
                if (isLoggedIn) {
                    validatedSessionId.value = sessionId
                }
                if (validatingSessionId.value == sessionId) {
                    validatingSessionId.value = null
                }
            }
        }
        return false
    }

    override fun clear() {
        _state.value = state()
    }

    override fun close() {
        scope.cancel()
    }

    private fun state(
        loading: Boolean = false,
        error: String? = null,
    ): LoginFlowState =
        LoginFlowState(
            actions =
                listOf(
                    LoginAction(
                        id = LOGIN_ACTION,
                        label = UiStrings.Login,
                        enabled = !loading,
                    ),
                ),
            loading = loading,
            error = error,
        )
}

private fun String.extractFanboxSession(): String? =
    split(";")
        .map { it.trim() }
        .firstOrNull { it.startsWith("$FANBOX_SESSION_COOKIE=") }
        ?.substringAfter("=")
        ?.takeIf { it.isNotBlank() }
