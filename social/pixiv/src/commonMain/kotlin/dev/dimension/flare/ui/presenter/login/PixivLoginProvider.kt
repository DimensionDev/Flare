package dev.dimension.flare.ui.presenter.login

import dev.dimension.flare.data.datastore.PlatformOAuthPending
import dev.dimension.flare.data.datastore.PlatformOAuthPendingRepository
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.data.network.pixiv.PIXIV_ANDROID_CLIENT_ID
import dev.dimension.flare.data.network.pixiv.PIXIV_ANDROID_CLIENT_SECRET
import dev.dimension.flare.data.network.pixiv.PIXIV_ANDROID_REDIRECT_URI
import dev.dimension.flare.data.network.pixiv.PixivAuthorizationRequest
import dev.dimension.flare.data.network.pixiv.PixivPlatformDetector
import dev.dimension.flare.data.network.pixiv.PixivService
import dev.dimension.flare.data.network.pixiv.accountKey
import dev.dimension.flare.data.network.pixiv.buildPixivAuthorizationRequest
import dev.dimension.flare.data.network.pixiv.parsePixivCallbackCode
import dev.dimension.flare.data.network.pixiv.toCredential
import dev.dimension.flare.data.platform.PIXIV_HOST
import dev.dimension.flare.data.platform.PixivCredential
import dev.dimension.flare.data.platform.PixivPlatformSpec
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
import dev.dimension.flare.model.RecommendedInstance
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiInstance
import dev.dimension.flare.ui.model.UiInstanceMetadata
import dev.dimension.flare.ui.model.UiStrings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

private const val LOGIN_ACTION = "login"

public data object PixivLoginProvider : LoginPlatformProvider {
    override val platformType: PlatformType = PlatformType.Pixiv
    override val metadata: PlatformTypeMetadata
        get() = PixivPlatformSpec.metadata
    override val detector: PlatformDetector = PixivPlatformDetector
    override val methods: List<LoginMethodSpec> =
        listOf(
            LoginMethodSpec(
                type = LoginMethodType.OAuth,
                title = UiStrings.OAuthLogin,
            ),
        )

    override fun agreementUrl(host: String): String? = "https://www.pixiv.net/terms/"

    override suspend fun recommendInstances(): List<RecommendedInstance> =
        listOf(
            RecommendedInstance(
                instance =
                    UiInstance(
                        name = "pixiv",
                        description = "Illustrations, manga, and novels.",
                        iconUrl = null,
                        domain = PIXIV_HOST,
                        type = platformType,
                        bannerUrl = null,
                        usersCount = 0,
                    ),
                priority = 80,
            ),
        )

    override suspend fun instanceMetadata(host: String): UiInstanceMetadata =
        throw UnsupportedOperationException("${platformType.name} is not supported yet")

    override fun createHandler(context: LoginContext): LoginMethodHandler {
        require(context.methodType == LoginMethodType.OAuth) {
            "Unsupported Pixiv login method: ${context.methodType}"
        }
        return PixivOAuthLoginHandler(context)
    }
}

private class PixivOAuthLoginHandler(
    private val context: LoginContext,
) : LoginMethodHandler {
    private val accountService: AccountService by koinInject()
    private val pendingRepository: PlatformOAuthPendingRepository by koinInject()
    private val _state = MutableStateFlow(oauthState())
    private val _effects = MutableSharedFlow<LoginEffect>(extraBufferCapacity = 1)
    private val resumeMutex = Mutex()
    private var resumeCompleted = false

    override val state: StateFlow<LoginFlowState> = _state
    override val effects: Flow<LoginEffect> = _effects

    override fun updateField(
        id: String,
        value: String,
    ) = Unit

    override suspend fun perform(actionId: String) {
        if (actionId != LOGIN_ACTION) return
        _state.value = oauthState(loading = true)
        resumeMutex.withLock {
            resumeCompleted = false
        }
        runCatching {
            val redirectUri = context.redirectUri ?: PIXIV_ANDROID_REDIRECT_URI
            val request = buildPixivAuthorizationRequest(redirectUri)
            pendingRepository.save(request.toPlatformOAuthPending())
            _effects.emit(LoginEffect.OpenUrl(request.authorizeRequestUrl))
        }.onFailure {
            _state.value = oauthState(error = it.message)
        }
    }

    override suspend fun resume(value: String) {
        resumeMutex.withLock {
            if (resumeCompleted) {
                return@withLock
            }
            _state.value = oauthState(loading = true)
            runCatching {
                val pending =
                    pendingRepository.latest(PlatformType.Pixiv)
                        ?: error("No pending Pixiv OAuth")
                val request = pending.toPixivAuthorizationRequest()
                val code =
                    parsePixivCallbackCode(
                        callbackUrl = value,
                        expectedState = request.state,
                    )
                val response =
                    PixivService().login(
                        clientId = PIXIV_ANDROID_CLIENT_ID,
                        clientSecret = PIXIV_ANDROID_CLIENT_SECRET,
                        code = code,
                        codeVerifier = request.codeVerifier,
                        redirectUri = request.redirectUri,
                    )
                val credential = response.toCredential()
                val accountKey = credential.accountKey()
                context.requireReloginAccount(accountKey)
                accountService.addAccount(
                    account =
                        UiAccount(
                            accountKey = accountKey,
                            platformType = PlatformType.Pixiv,
                        ),
                    credential = credential,
                    serializer = PixivCredential.serializer(),
                )
                pendingRepository.clear(pending)
                resumeCompleted = true
                context.onSuccess()
            }.onFailure {
                _state.value = oauthState(error = it.message)
            }
        }
    }

    override fun canResume(value: String): Boolean = value.isPixivOAuthCallback() && value.contains("code=")

    override fun onExternalAuthenticationDismissed(error: String?) {
        _state.value = oauthState(error = error)
    }

    override fun clear() {
        _state.value = oauthState()
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
                        label = UiStrings.Login,
                        enabled = !loading,
                    ),
                ),
            loading = loading,
            error = error,
        )
}

private fun PixivAuthorizationRequest.toPlatformOAuthPending(): PlatformOAuthPending =
    PlatformOAuthPending(
        platformType = PlatformType.Pixiv,
        host = PIXIV_HOST,
        createdAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
        attributes =
            mapOf(
                "authorize_request_url" to authorizeRequestUrl,
                "expires_in_millis" to 10.minutes.inWholeMilliseconds.toString(),
                "code_verifier" to codeVerifier,
                "state" to state,
                "redirect_uri" to redirectUri,
            ),
    )

private fun PlatformOAuthPending.toPixivAuthorizationRequest(): PixivAuthorizationRequest =
    PixivAuthorizationRequest(
        authorizeRequestUrl = attributes.getValue("authorize_request_url"),
        codeVerifier = attributes.getValue("code_verifier"),
        state = attributes.getValue("state"),
        redirectUri = attributes.getValue("redirect_uri"),
    )

private fun String.isPixivOAuthCallback(): Boolean =
    startsWith(
        prefix = "https://app-api.pixiv.net/web/v1/users/auth/pixiv/callback",
        ignoreCase = true,
    ) ||
        startsWith(
            prefix = "pixiv://account/login",
            ignoreCase = true,
        )
