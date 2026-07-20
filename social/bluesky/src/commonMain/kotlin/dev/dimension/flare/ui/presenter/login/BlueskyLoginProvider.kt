package dev.dimension.flare.ui.presenter.login

import com.atproto.server.CreateSessionRequest
import com.atproto.server.CreateSessionResponse
import dev.dimension.flare.data.datastore.PlatformOAuthPending
import dev.dimension.flare.data.datastore.PlatformOAuthPendingRepository
import dev.dimension.flare.data.network.bluesky.BlueskyPlatformDetector
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.data.network.bluesky.FLARE_BLUESKY_OAUTH_SCOPES
import dev.dimension.flare.data.network.bluesky.OAuthCodeChallengeMethodS256
import dev.dimension.flare.data.network.bluesky.delegationControllerDidOrNull
import dev.dimension.flare.data.network.bluesky.missingFlareOAuthScopes
import dev.dimension.flare.data.network.bluesky.repairTranquilDelegationScopes
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.data.platform.BlueskyCredential
import dev.dimension.flare.data.platform.BlueskyPlatformSpec
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
import io.ktor.client.plugins.DefaultRequest
import io.ktor.http.Url
import io.ktor.http.takeFrom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import sh.christian.ozone.api.response.AtpException
import sh.christian.ozone.api.response.AtpResponse
import sh.christian.ozone.oauth.OAuthApi
import sh.christian.ozone.oauth.OAuthAuthorizationRequest
import sh.christian.ozone.oauth.OAuthClient
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds

private const val CLIENT_METADATA = "https://flareapp.moe/client-metadata.json"
private const val REDIRECT_URI = "https://flareapp.moe/callback"
private const val LOGIN_ACTION = "login"
private const val FIX_TRANQUIL_DELEGATION_ACTION = "fix_tranquil_delegation"
private const val USERNAME_FIELD = "username"
private const val PASSWORD_FIELD = "password"
private const val OTP_FIELD = "otp"

public data object BlueskyLoginProvider : LoginPlatformProvider {
    override val platformType: PlatformType = PlatformType.Bluesky
    override val metadata: PlatformTypeMetadata
        get() = BlueskyPlatformSpec.metadata
    override val detector: PlatformDetector = BlueskyPlatformDetector
    override val methods: List<LoginMethodSpec> =
        listOf(
            LoginMethodSpec(
                type = LoginMethodType.Password,
                title = UiStrings.PasswordLogin,
                priority = 10,
            ),
            LoginMethodSpec(
                type = LoginMethodType.OAuth,
                title = UiStrings.OAuthLogin,
            ),
        )

    override fun agreementUrl(host: String): String? = "https://bsky.social/about/support/tos"

    override suspend fun recommendInstances(): List<RecommendedInstance> =
        listOf(
            RecommendedInstance(
                instance =
                    UiInstance(
                        name = metadata.displayName,
                        description =
                            "The web. Email. RSS feeds. XMPP chats. " +
                                "What all these technologies had in common is they allowed people to freely interact " +
                                "and create content, without a single intermediary.",
                        iconUrl = null,
                        domain = "bsky.social",
                        type = platformType,
                        bannerUrl = null,
                        usersCount = 0,
                    ),
                priority = 70,
            ),
        )

    override suspend fun instanceMetadata(host: String): UiInstanceMetadata =
        throw UnsupportedOperationException("${platformType.name} is not supported yet")

    override fun createHandler(context: LoginContext): LoginMethodHandler =
        when (context.methodType) {
            LoginMethodType.Password -> BlueskyPasswordLoginHandler(context)
            LoginMethodType.OAuth -> BlueskyOAuthLoginHandler(context)
            else -> error("Unsupported Bluesky login method: ${context.methodType}")
        }
}

private class BlueskyPasswordLoginHandler(
    private val context: LoginContext,
) : LoginMethodHandler {
    private val accountService: AccountService by koinInject()
    private val values = mutableMapOf<String, String>()
    private var requireOtp = false
    private val _state = MutableStateFlow(passwordState())
    private val _effects = MutableSharedFlow<LoginEffect>(extraBufferCapacity = 1)

    override val state: StateFlow<LoginFlowState> = _state
    override val effects: Flow<LoginEffect> = _effects

    override fun updateField(
        id: String,
        value: String,
    ) {
        values[id] = value
        _state.value = passwordState(error = null)
    }

    override suspend fun perform(actionId: String) {
        if (actionId != LOGIN_ACTION) return
        _state.value = passwordState(loading = true)
        runCatching {
            blueskyLoginUseCase(
                baseUrl = "https://${context.host}",
                username = values[USERNAME_FIELD].orEmpty(),
                password = values[PASSWORD_FIELD].orEmpty(),
                authFactorToken = values[OTP_FIELD]?.takeIf { it.isNotEmpty() && requireOtp },
            )
            context.onSuccess()
        }.onFailure {
            if (it is AtpException && it.error?.error == "AuthFactorTokenRequired") {
                requireOtp = true
                _state.value = passwordState()
            } else {
                _state.value = passwordState(error = errorMessage(it))
            }
        }
    }

    override suspend fun resume(value: String) = Unit

    override fun clear() {
        values.clear()
        requireOtp = false
        _state.value = passwordState()
    }

    private fun passwordState(
        loading: Boolean = false,
        error: String? = null,
    ): LoginFlowState {
        val username = values[USERNAME_FIELD].orEmpty()
        val password = values[PASSWORD_FIELD].orEmpty()
        val otp = values[OTP_FIELD].orEmpty()
        val fields =
            buildList {
                add(
                    LoginField(
                        id = USERNAME_FIELD,
                        type = LoginFieldType.TextInput,
                        label = UiStrings.Username,
                        value = username,
                        readOnly = requireOtp,
                    ),
                )
                add(
                    LoginField(
                        id = PASSWORD_FIELD,
                        type = LoginFieldType.PasswordInput,
                        label = UiStrings.Password,
                        value = password,
                        readOnly = requireOtp,
                    ),
                )
                if (requireOtp) {
                    add(
                        LoginField(
                            id = OTP_FIELD,
                            type = LoginFieldType.OtpInput,
                            label = UiStrings.Otp,
                            value = otp,
                        ),
                    )
                }
            }
        val canLogin =
            username.isNotBlank() &&
                password.isNotBlank() &&
                (!requireOtp || otp.isNotBlank())
        return LoginFlowState(
            fields = fields,
            actions =
                listOf(
                    LoginAction(
                        id = LOGIN_ACTION,
                        label = if (requireOtp) UiStrings.Verify else UiStrings.Login,
                        enabled = !loading && canLogin,
                    ),
                ),
            loading = loading,
            error = error,
        )
    }

    private suspend fun blueskyLoginUseCase(
        baseUrl: String,
        username: String,
        password: String,
        authFactorToken: String?,
    ) {
        val service = BlueskyService(baseUrl)
        val response =
            if (username.contains("@") || username.contains(".")) {
                service.createSession(
                    CreateSessionRequest(
                        identifier = username,
                        password = password,
                        authFactorToken = authFactorToken,
                    ),
                )
            } else {
                val server = service.describeServer()
                val actualUserName =
                    server
                        .maybeResponse()
                        ?.availableUserDomains
                        ?.firstOrNull()
                        ?.let { username.withBlueskyUserDomain(it) }
                        ?: username
                service.createSession(
                    CreateSessionRequest(
                        identifier = actualUserName,
                        password = password,
                        authFactorToken = authFactorToken,
                    ),
                )
            }.let {
                when (it) {
                    is AtpResponse.Failure<CreateSessionResponse> -> {
                        throw AtpException(
                            statusCode = it.statusCode,
                            error = it.error,
                        )
                    }

                    is AtpResponse.Success<CreateSessionResponse> -> {
                        it.response
                    }
                }
            }

        val credential: BlueskyCredential =
            BlueskyCredential.Password(
                baseUrl = baseUrl,
                accessToken = response.accessJwt,
                refreshToken = response.refreshJwt,
            )
        val accountKey =
            MicroBlogKey(
                id = response.did.did,
                host = Url(baseUrl).host,
            )
        context.requireReloginAccount(accountKey)
        accountService.addAccount(
            account =
                UiAccount(
                    accountKey = accountKey,
                    platformType = PlatformType.Bluesky,
                ),
            credential = credential,
            serializer = BlueskyCredential.serializer(),
        )
    }
}

private class BlueskyOAuthLoginHandler(
    private val context: LoginContext,
) : LoginMethodHandler {
    private val accountService: AccountService by koinInject()
    private val pendingRepository: PlatformOAuthPendingRepository by koinInject()
    private val values = mutableMapOf<String, String>()
    private var request: OAuthAuthorizationRequest? = null
    private var pendingRepair: PendingTranquilDelegationRepair? = null

    private fun oauthClient(redirectUri: String) =
        OAuthClient(
            clientId = CLIENT_METADATA,
            redirectUri = redirectUri,
        )

    private val _state = MutableStateFlow(oauthState())
    private val _effects = MutableSharedFlow<LoginEffect>(extraBufferCapacity = 1)

    override val state: StateFlow<LoginFlowState> = _state
    override val effects: Flow<LoginEffect> = _effects

    override fun updateField(
        id: String,
        value: String,
    ) {
        values[id] = value
        _state.value = oauthState(error = null)
    }

    override suspend fun perform(actionId: String) {
        when (actionId) {
            LOGIN_ACTION -> startOAuthLogin()
            FIX_TRANQUIL_DELEGATION_ACTION -> repairDelegationAndRestartOAuth()
        }
    }

    private suspend fun startOAuthLogin() {
        _state.value = oauthState(loading = true)
        request = null
        pendingRepair = null
        runCatching {
            val redirectUri = context.redirectUri ?: REDIRECT_URI
            request =
                login(
                    host = context.host,
                    userName = values[USERNAME_FIELD].orEmpty(),
                    redirectUri = redirectUri,
                )
            val request = request ?: error("No authorization request")
            pendingRepository.save(
                request.toPlatformOAuthPending(
                    host = context.host,
                    redirectUri = redirectUri,
                ),
            )
            val authorizeUrl = request.authorizeRequestUrl
            require(authorizeUrl.isNotEmpty()) { "Invalid authorization request URL" }
            _effects.emit(LoginEffect.OpenUrl(authorizeUrl))
        }.onFailure {
            _state.value = oauthState(error = errorMessage(it))
        }
    }

    private suspend fun repairDelegationAndRestartOAuth() {
        val repair = pendingRepair ?: return
        _state.value = oauthState(loading = true)
        runCatching {
            repairTranquilDelegationScopes(
                credential = repair.credential,
                controllerDid = repair.controllerDid,
            )
            pendingRepair = null
            request =
                login(
                    host = repair.host,
                    userName = repair.userName,
                    redirectUri = repair.redirectUri,
                )
            val request = request ?: error("No authorization request")
            pendingRepository.save(
                request.toPlatformOAuthPending(
                    host = repair.host,
                    redirectUri = repair.redirectUri,
                ),
            )
            val authorizeUrl = request.authorizeRequestUrl
            require(authorizeUrl.isNotEmpty()) { "Invalid authorization request URL" }
            _effects.emit(LoginEffect.OpenUrl(authorizeUrl))
        }.onSuccess {
            _state.value = oauthState()
        }.onFailure {
            _state.value = oauthState(error = errorMessage(it))
        }
    }

    override suspend fun resume(value: String) {
        _state.value = oauthState(loading = true)
        runCatching {
            val state = Url(value).parameters["state"]
            val pending =
                pendingRepository
                    .all(PlatformType.Bluesky)
                    .firstOrNull { it.attributes["state"] == state }
                    ?: error("No pending authorization request")
            resumeOAuth(
                url = value,
                request = pending.toBlueskyAuthorizationRequest(),
                redirectUri = pending.attributes["redirect_uri"] ?: REDIRECT_URI,
            ).let { result ->
                pendingRepository.clear(pending)
                when (result) {
                    OAuthResumeResult.Success -> {
                        context.onSuccess()
                    }

                    is OAuthResumeResult.NeedsTranquilDelegationRepair -> {
                        pendingRepair = result.repair
                        _state.value = oauthState(error = result.message)
                    }
                }
            }
        }.onFailure {
            _state.value = oauthState(error = errorMessage(it))
        }
    }

    override fun onExternalAuthenticationDismissed(error: String?) {
        _state.value = oauthState(error = error)
    }

    override fun clear() {
        values.clear()
        request = null
        pendingRepair = null
        _state.value = oauthState()
    }

    private fun oauthState(
        loading: Boolean = false,
        error: String? = null,
    ): LoginFlowState {
        val username = values[USERNAME_FIELD].orEmpty()
        return LoginFlowState(
            fields =
                listOf(
                    LoginField(
                        id = USERNAME_FIELD,
                        type = LoginFieldType.TextInput,
                        label = UiStrings.Username,
                        value = username,
                    ),
                ),
            actions =
                buildList {
                    if (pendingRepair != null) {
                        add(
                            LoginAction(
                                id = FIX_TRANQUIL_DELEGATION_ACTION,
                                label = UiStrings.BlueskyFixDelegationScopes,
                                enabled = !loading,
                            ),
                        )
                    }
                    add(
                        LoginAction(
                            id = LOGIN_ACTION,
                            label = UiStrings.Login,
                            enabled = !loading && username.isNotBlank(),
                        ),
                    )
                },
            loading = loading,
            error = error,
        )
    }

    private suspend fun login(
        host: String,
        userName: String,
        redirectUri: String,
    ): OAuthAuthorizationRequest =
        createOAuthApi(host).buildAuthorizationRequest(
            oauthClient = oauthClient(redirectUri),
            scopes = FLARE_BLUESKY_OAUTH_SCOPES,
            loginHandleHint = userName.takeIf { !it.contains('@') && it.contains('.') },
        )

    private suspend fun resumeOAuth(
        url: String,
        request: OAuthAuthorizationRequest,
        redirectUri: String,
    ): OAuthResumeResult {
        val parsedUrl = Url(url)
        val code = parsedUrl.parameters["code"]
        val state = parsedUrl.parameters["state"]
        val iss = parsedUrl.parameters["iss"]
        if (code == null || state == null || iss == null) {
            throw IllegalArgumentException("Invalid URL: $url")
        }
        if (state != request.state) {
            throw IllegalArgumentException("State mismatch: expected ${request.state}, got $state")
        }
        val host = Url(iss).host
        val token =
            createOAuthApi(host).requestToken(
                oauthClient = oauthClient(redirectUri),
                code = code,
                nonce = request.nonce,
                codeVerifier = request.codeVerifier,
            )
        val credential =
            BlueskyCredential.OAuthCredential(
                baseUrl = iss,
                oAuthToken = token,
            )
        val missingScopes = token.missingFlareOAuthScopes()
        if (missingScopes.isNotEmpty()) {
            val controllerDid = token.delegationControllerDidOrNull()
            if (controllerDid != null) {
                return OAuthResumeResult.NeedsTranquilDelegationRepair(
                    repair =
                        PendingTranquilDelegationRepair(
                            credential = credential,
                            controllerDid = controllerDid,
                            host = context.host,
                            userName = values[USERNAME_FIELD].orEmpty(),
                            redirectUri = redirectUri,
                        ),
                    message = tranquilDelegationRepairMessage(missingScopes),
                )
            }
            error("OAuth token is missing required scope(s): ${missingScopes.joinToString(" ")}")
        }
        val accountKey =
            MicroBlogKey(
                id = token.subject.did,
                host = host,
            )
        context.requireReloginAccount(accountKey)
        accountService.addAccount(
            account =
                UiAccount(
                    accountKey = accountKey,
                    platformType = PlatformType.Bluesky,
                ),
            credential = credential,
            serializer = BlueskyCredential.serializer(),
        )
        return OAuthResumeResult.Success
    }
}

private sealed interface OAuthResumeResult {
    data object Success : OAuthResumeResult

    data class NeedsTranquilDelegationRepair(
        val repair: PendingTranquilDelegationRepair,
        val message: String,
    ) : OAuthResumeResult
}

private data class PendingTranquilDelegationRepair(
    val credential: BlueskyCredential.OAuthCredential,
    val controllerDid: String,
    val host: String,
    val userName: String,
    val redirectUri: String,
)

private fun tranquilDelegationRepairMessage(missingScopes: List<String>): String =
    "This Tranquil delegated account did not grant Flare the required Bluesky API scope(s): " +
        missingScopes.joinToString(" ") +
        ". Tap Fix Tranquil permissions, then authorize Flare again."

private fun createOAuthApi(host: String): OAuthApi =
    OAuthApi(
        ktorClient {
            install(DefaultRequest) {
                url.takeFrom("https://$host")
            }
        },
        { OAuthCodeChallengeMethodS256 },
    )

internal fun String.withBlueskyUserDomain(domain: String): String =
    when {
        domain.isBlank() -> this
        domain.startsWith(".") -> "$this$domain"
        else -> "$this.$domain"
    }

private fun OAuthAuthorizationRequest.toPlatformOAuthPending(
    host: String,
    redirectUri: String,
): PlatformOAuthPending =
    PlatformOAuthPending(
        platformType = PlatformType.Bluesky,
        host = host,
        createdAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
        attributes =
            mapOf(
                "authorize_request_url" to authorizeRequestUrl,
                "expires_in_millis" to expiresIn.inWholeMilliseconds.toString(),
                "code_verifier" to codeVerifier,
                "state" to state,
                "nonce" to nonce,
                "redirect_uri" to redirectUri,
            ),
    )

private fun PlatformOAuthPending.toBlueskyAuthorizationRequest(): OAuthAuthorizationRequest =
    OAuthAuthorizationRequest(
        authorizeRequestUrl = attributes.getValue("authorize_request_url"),
        expiresIn = attributes.getValue("expires_in_millis").toLong().milliseconds,
        codeVerifier = attributes.getValue("code_verifier"),
        state = attributes.getValue("state"),
        nonce = attributes.getValue("nonce"),
    )

private fun errorMessage(error: Throwable): String? =
    if (error is AtpException) {
        error.error?.message ?: error.message
    } else {
        error.message
    }
