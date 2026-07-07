package dev.dimension.flare.ui.presenter.login

import dev.dimension.flare.data.datastore.PlatformOAuthPending
import dev.dimension.flare.data.datastore.PlatformOAuthPendingRepository
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.data.network.tumblr.TumblrPlatformDetector
import dev.dimension.flare.data.network.tumblr.TumblrService
import dev.dimension.flare.data.network.tumblr.TumblrTokenResponse
import dev.dimension.flare.data.platform.TUMBLR_HOST
import dev.dimension.flare.data.platform.TumblrCredential
import dev.dimension.flare.data.platform.TumblrPlatformSpec
import dev.dimension.flare.data.repository.AccountService
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
import dev.dimension.flare.model.RecommendedInstance
import dev.dimension.flare.social.tumblr.TumblrBuildConfig
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiInstance
import dev.dimension.flare.ui.model.UiInstanceMetadata
import dev.dimension.flare.ui.model.UiStrings
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

private const val LOGIN_ACTION = "login"
private const val OAUTH_HOST = "www.tumblr.com"
private const val OAUTH_AUTHORIZE_URL = "https://www.tumblr.com/oauth2/authorize"
private val TUMBLR_SCOPES = listOf("basic", "write", "offline_access")

public data object TumblrLoginProvider : LoginPlatformProvider {
    override val platformType: PlatformType = PlatformType.Tumblr
    override val metadata: PlatformTypeMetadata
        get() = TumblrPlatformSpec.metadata
    override val detector: PlatformDetector = TumblrPlatformDetector
    override val methods: List<LoginMethodSpec> =
        listOf(
            LoginMethodSpec(
                type = LoginMethodType.OAuth,
                title = UiStrings.OAuthLogin,
            ),
        )

    override fun agreementUrl(host: String): String? = "https://www.tumblr.com/policy/en/terms-of-service"

    override suspend fun recommendInstances(): List<RecommendedInstance> =
        listOf(
            RecommendedInstance(
                instance =
                    UiInstance(
                        name = "Tumblr",
                        description = "Blogs, posts, art, and fandoms.",
                        iconUrl = null,
                        domain = TUMBLR_HOST,
                        type = platformType,
                        bannerUrl = null,
                        usersCount = 0,
                    ),
                priority = 70,
            ),
        )

    override suspend fun instanceMetadata(host: String): UiInstanceMetadata =
        throw UnsupportedOperationException("${platformType.name} metadata is not supported")

    override fun createHandler(context: LoginContext): LoginMethodHandler {
        require(context.methodType == LoginMethodType.OAuth) {
            "Unsupported Tumblr login method: ${context.methodType}"
        }
        return TumblrOAuthLoginHandler(context)
    }
}

private class TumblrOAuthLoginHandler(
    private val context: LoginContext,
) : LoginMethodHandler {
    private val accountService: AccountService by koinInject()
    private val pendingRepository: PlatformOAuthPendingRepository by koinInject()
    private val service = TumblrService()
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
            require(TumblrBuildConfig.configured) {
                "Tumblr OAuth is not configured. Set TUMBLR_CLIENT_ID and TUMBLR_CLIENT_SECRET."
            }
            val redirectUri = context.redirectUri ?: TumblrBuildConfig.redirectUri
            val state = newState()
            val authorizeUrl = buildAuthorizeUrl(redirectUri = redirectUri, state = state)
            pendingRepository.save(
                PlatformOAuthPending(
                    platformType = PlatformType.Tumblr,
                    host = OAUTH_HOST,
                    createdAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
                    attributes =
                        mapOf(
                            "state" to state,
                            "redirect_uri" to redirectUri,
                            "expires_in_millis" to 10.minutes.inWholeMilliseconds.toString(),
                        ),
                ),
            )
            _effects.emit(LoginEffect.OpenUrl(authorizeUrl))
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
                val parsedUrl = Url(value)
                val code = parsedUrl.parameters["code"] ?: error("Missing Tumblr OAuth code")
                val state = parsedUrl.parameters["state"] ?: error("Missing Tumblr OAuth state")
                val pending =
                    pendingRepository
                        .all(PlatformType.Tumblr)
                        .firstOrNull { it.attributes["state"] == state }
                        ?: error("No pending Tumblr OAuth")
                val expectedState = pending.attributes.getValue("state")
                require(state == expectedState) {
                    "State mismatch: expected $expectedState, got $state"
                }
                val redirectUri = pending.attributes["redirect_uri"] ?: TumblrBuildConfig.redirectUri
                val token =
                    service.requestToken(
                        clientId = TumblrBuildConfig.clientId,
                        clientSecret = TumblrBuildConfig.clientSecret,
                        redirectUri = redirectUri,
                        code = code,
                    )
                val userInfoService =
                    TumblrService(
                        credentialFlow =
                            kotlinx.coroutines.flow.flowOf(
                                token.toCredential(
                                    blogIdentifier = "pending",
                                    blogName = "pending",
                                    blogUrl = "https://$TUMBLR_HOST/",
                                    blogUuid = null,
                                    isPrimary = false,
                                ),
                            ),
                    )
                val blogs = userInfoService.userInfo().user.blogs
                require(blogs.isNotEmpty()) { "Tumblr account has no blogs" }
                val target = context.reloginTarget
                blogs.forEach { blog ->
                    val accountKey = MicroBlogKey(id = blog.name, host = TUMBLR_HOST)
                    if (target != null && target.accountKey != accountKey) {
                        return@forEach
                    }
                    context.requireReloginAccount(accountKey)
                    accountService.addAccount(
                        account =
                            UiAccount(
                                accountKey = accountKey,
                                platformType = PlatformType.Tumblr,
                            ),
                        credential =
                            token.toCredential(
                                blogIdentifier = blog.name,
                                blogName = blog.name,
                                blogUrl = blog.url ?: "https://${blog.name}.tumblr.com/",
                                blogUuid = blog.uuid,
                                isPrimary = blog.primary == true,
                            ),
                        serializer = TumblrCredential.serializer(),
                    )
                }
                if (target != null && blogs.none { MicroBlogKey(id = it.name, host = TUMBLR_HOST) == target.accountKey }) {
                    error("Relogin account not found in Tumblr blogs: ${target.accountKey}")
                }
                pendingRepository.clear(pending)
                resumeCompleted = true
                context.onSuccess()
            }.onFailure {
                _state.value = oauthState(error = it.message)
            }
        }
    }

    override fun canResume(value: String): Boolean =
        runCatching {
            val parsed = Url(value)
            parsed.parameters["code"] != null && parsed.parameters["state"] != null
        }.getOrDefault(false)

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

private fun buildAuthorizeUrl(
    redirectUri: String,
    state: String,
): String =
    URLBuilder(OAUTH_AUTHORIZE_URL)
        .apply {
            parameters.append("client_id", TumblrBuildConfig.clientId)
            parameters.append("response_type", "code")
            parameters.append("scope", TUMBLR_SCOPES.joinToString(" "))
            parameters.append("state", state)
            parameters.append("redirect_uri", redirectUri)
        }.buildString()

private fun TumblrTokenResponse.toCredential(
    blogIdentifier: String,
    blogName: String,
    blogUrl: String,
    blogUuid: String?,
    isPrimary: Boolean,
): TumblrCredential =
    TumblrCredential(
        accessToken = accessToken,
        refreshToken = refreshToken,
        tokenType = tokenType,
        scope = scope,
        expiresAtEpochSeconds = expiresIn?.let { Clock.System.now().toEpochMilliseconds() / 1000 + it },
        blogIdentifier = blogIdentifier,
        blogName = blogName,
        blogUrl = blogUrl,
        blogUuid = blogUuid,
        isPrimary = isPrimary,
    )

private fun newState(): String =
    buildString {
        append(Clock.System.now().toEpochMilliseconds())
        append('-')
        append(Random.nextLong().toString().encodeURLParameter())
    }
