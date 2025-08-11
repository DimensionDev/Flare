package dev.dimension.flare.data.network.bluesky

import com.atproto.server.RefreshSessionResponse
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiAccount
import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.call.body
import io.ktor.client.call.save
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.util.AttributeKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import sh.christian.ozone.BlueskyJson
import sh.christian.ozone.api.response.AtpErrorDescription
import sh.christian.ozone.api.response.AtpResponse
import sh.christian.ozone.api.response.StatusCode
import sh.christian.ozone.oauth.OAuthApi

/**
 * Appends the `Authorization` header to XRPC requests, as well as automatically refreshing and
 * replaying a network request if it fails due to an expired access token.
 */
internal class BlueskyAuthPlugin(
    private val json: Json,
    private val oauthApi: OAuthApi,
    private val accountKey: MicroBlogKey?,
    private val baseUrlFlow: Flow<String>? = null,
    private val authTokenFlow: Flow<UiAccount.Bluesky.Credential>?,
    private val onAuthTokensChanged: (UiAccount.Bluesky.Credential) -> Unit,
) {
    class Config(
        var json: Json = BlueskyJson,
        var oauthApi: OAuthApi = OAuthApi(),
        var accountKey: MicroBlogKey? = null,
        var baseUrlFlow: Flow<String>? = null,
        var authTokenFlow: Flow<UiAccount.Bluesky.Credential>? = null,
        var onAuthTokensChanged: (UiAccount.Bluesky.Credential) -> Unit = {},
    )

    companion object : HttpClientPlugin<Config, BlueskyAuthPlugin> {
        override val key = AttributeKey<BlueskyAuthPlugin>("BlueskyAuthPlugin")
        val refreshMutex = Mutex()

        override fun prepare(block: Config.() -> Unit): BlueskyAuthPlugin {
            val config = Config().apply(block)
            return BlueskyAuthPlugin(
                json = config.json,
                oauthApi = config.oauthApi,
                accountKey = config.accountKey,
                baseUrlFlow = config.baseUrlFlow,
                authTokenFlow = config.authTokenFlow,
                onAuthTokensChanged = config.onAuthTokensChanged,
            )
        }

        override fun install(
            plugin: BlueskyAuthPlugin,
            scope: HttpClient,
        ) {
            scope.plugin(HttpSend.Plugin).intercept { context ->

                // If the base URL is provided, set it in the request.
                plugin.baseUrlFlow?.firstOrNull()?.let { baseUrl ->
                    context.url.protocol = Url(baseUrl).protocol
                    context.url.host = Url(baseUrl).host
                    context.url.port = Url(baseUrl).port
                }

                val credentialFlow = plugin.authTokenFlow
                val oAuthApi = plugin.oauthApi
                if (!context.headers.contains(HttpHeaders.Authorization) && plugin.authTokenFlow != null) {
                    context.auth(credentialFlow, oAuthApi)
                }

                var result: HttpClientCall = execute(context)

                if (result.response.status.isSuccess()) {
                    return@intercept result
                }

                // Cache the response in memory since we will need to decode it potentially more than once.
                result = result.save()

                val response =
                    runCatching<AtpErrorDescription> {
                        plugin.json.decodeFromString(result.response.bodyAsText())
                    }

                if (credentialFlow != null) {
                    val newTokens =
                        when (response.getOrNull()?.error) {
                            "invalid_token", "ExpiredToken" ->
                                refreshExpiredToken(credentialFlow, oAuthApi, scope)

                            "use_dpop_nonce" -> refreshDpopNonce(credentialFlow, result.response)
                            else -> null
                        }

                    if (newTokens != null) {
                        plugin.onAuthTokensChanged(newTokens)

                        context.headers.remove(HttpHeaders.Authorization)
                        context.headers.remove("DPoP")
                        context.auth(flowOf(newTokens), oAuthApi)
                        result = execute(context)

                        val newResponse =
                            runCatching<AtpErrorDescription> {
                                plugin.json.decodeFromString(result.response.bodyAsText())
                            }
                        when (newResponse.getOrNull()?.error) {
                            "ExpiredToken" -> throw LoginExpiredException(
                                plugin.accountKey!!,
                                PlatformType.Bluesky,
                            )

                            null -> Unit
                            else -> throw LoginExpiredException(
                                plugin.accountKey!!,
                                PlatformType.Bluesky,
                            )
                        }
                    }

                    onResponse(
                        credentialFlow,
                        result.response,
                        plugin.onAuthTokensChanged,
                    )
                }

                result
            }
        }

        private suspend fun HttpRequestBuilder.auth(
            credentialFlow: Flow<UiAccount.Bluesky.Credential>,
            oAuthApi: OAuthApi,
        ) {
            val credential = credentialFlow.firstOrNull()
            when (val tokens = credential) {
                is UiAccount.Bluesky.Credential.BlueskyCredential ->
                    header(
                        HttpHeaders.Authorization,
                        "Bearer ${tokens.accessToken}",
                    )

                is UiAccount.Bluesky.Credential.OAuthCredential ->
                    applyDpop(
                        credential,
                        oAuthApi,
                    )

                null -> Unit
            }
        }

        private suspend fun HttpRequestBuilder.refresh(
            credentialFlow: Flow<UiAccount.Bluesky.Credential>,
            oAuthApi: OAuthApi,
        ) {
            val credential = credentialFlow.firstOrNull()
            when (val tokens = credential) {
                is UiAccount.Bluesky.Credential.BlueskyCredential ->
                    header(
                        HttpHeaders.Authorization,
                        "Bearer ${tokens.refreshToken}",
                    )

                is UiAccount.Bluesky.Credential.OAuthCredential ->
                    applyDpop(
                        tokens,
                        oAuthApi,
                    )

                null -> {
                    // No tokens available, do not add Authorization header
                }
            }
        }

        private suspend fun onResponse(
            credentialFlow: Flow<UiAccount.Bluesky.Credential>,
            response: HttpResponse,
            onAuthTokensChanged: (UiAccount.Bluesky.Credential) -> Unit,
        ) {
            refreshDpopNonce(credentialFlow, response)?.let { newTokens ->
                onAuthTokensChanged(newTokens)
            }
        }

        private suspend fun refreshExpiredToken(
            credentialFlow: Flow<UiAccount.Bluesky.Credential>,
            oAuthApi: OAuthApi,
            scope: HttpClient,
        ): UiAccount.Bluesky.Credential? {
            val old = credentialFlow.firstOrNull()
            return refreshMutex.withLock {
                val current = credentialFlow.firstOrNull()
                if (current != old) {
                    current
                } else {
                    when (val tokens = old) {
                        is UiAccount.Bluesky.Credential.BlueskyCredential -> {
                            val refreshResponse =
                                scope.post("/xrpc/com.atproto.server.refreshSession") {
                                    refresh(credentialFlow, oAuthApi)
                                }
                            refreshResponse
                                .toAtpResponse<RefreshSessionResponse>()
                                .maybeResponse()
                                ?.let { refreshed ->
                                    UiAccount.Bluesky.Credential.BlueskyCredential(
                                        accessToken = refreshed.accessJwt,
                                        refreshToken = refreshed.refreshJwt,
                                        baseUrl = tokens.baseUrl,
                                    )
                                }
                        }

                        is UiAccount.Bluesky.Credential.OAuthCredential -> {
                            oAuthApi
                                .refreshToken(
                                    clientId = tokens.oAuthToken.clientId,
                                    nonce = tokens.oAuthToken.nonce,
                                    refreshToken = tokens.oAuthToken.refreshToken,
                                    keyPair = tokens.oAuthToken.keyPair,
                                ).let { refreshed ->
                                    UiAccount.Bluesky.Credential.OAuthCredential(
                                        baseUrl = tokens.baseUrl,
                                        oAuthToken = refreshed,
                                    )
                                }
                        }

                        null -> {
                            // No tokens available, unable to refresh
                            null
                        }
                    }
                }
            }
        }

        private suspend fun refreshDpopNonce(
            credentialFlow: Flow<UiAccount.Bluesky.Credential>,
            callResponse: HttpResponse,
        ): UiAccount.Bluesky.Credential? {
            val credential = credentialFlow.firstOrNull()
            return when (val tokens = credential) {
                is UiAccount.Bluesky.Credential.BlueskyCredential -> {
                    // Bearer tokens do not use DPoP, unable to refresh
                    null
                }

                is UiAccount.Bluesky.Credential.OAuthCredential -> {
                    callResponse.headers["DPoP-Nonce"]?.let {
                        tokens.copy(
                            oAuthToken =
                                tokens.oAuthToken.copy(
                                    nonce = it,
                                ),
                        )
                    }
                }

                null -> {
                    // No tokens available, unable to refresh
                    null
                }
            }
        }

        private suspend fun HttpRequestBuilder.applyDpop(
            tokens: UiAccount.Bluesky.Credential.OAuthCredential,
            oAuthApi: OAuthApi,
        ) {
            url.protocol = tokens.oAuthToken.pds.protocol
            url.host = tokens.oAuthToken.pds.host

            val dpopHeader =
                oAuthApi.createDpopHeaderValue(
                    keyPair = tokens.oAuthToken.keyPair,
                    method = method.value,
                    endpoint = url.toString(),
                    nonce = tokens.oAuthToken.nonce,
                    accessToken = tokens.oAuthToken.accessToken,
                )

            header(HttpHeaders.Authorization, "DPoP ${tokens.oAuthToken.accessToken}")
            header("DPoP", dpopHeader)
        }
    }
}

private suspend inline fun <reified T : Any> HttpResponse.toAtpResponse(): AtpResponse<T> {
    val headers = headers.entries().associateByTo(mutableMapOf(), { it.key }, { it.value.last() })

    return when (val code = StatusCode.fromCode(status.value)) {
        is StatusCode.Okay -> {
            AtpResponse.Success(
                headers = headers,
                response = body(),
            )
        }

        is StatusCode.Failure -> {
            val maybeError = errorDescriptionOrNull()
            val maybeBody = runCatching<T> { body() }.getOrNull()

            return AtpResponse.Failure(
                headers = headers,
                statusCode = code,
                response = maybeBody,
                error = maybeError,
            )
        }
    }
}

private suspend inline fun HttpResponse.errorDescriptionOrNull(): AtpErrorDescription? =
    when (StatusCode.fromCode(status.value)) {
        is StatusCode.Failure -> {
            call.save()
            runCatching { body<AtpErrorDescription>() }.getOrNull()
        }

        else -> null
    }
