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
import io.ktor.http.isSuccess
import io.ktor.util.AttributeKey
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
    private val authTokens: UiAccount.Bluesky.Credential?,
    private val onAuthTokensChanged: (UiAccount.Bluesky.Credential) -> Unit,
) {
    class Config(
        var json: Json = BlueskyJson,
        var oauthApi: OAuthApi = OAuthApi(),
        var accountKey: MicroBlogKey? = null,
        var authTokens: UiAccount.Bluesky.Credential? = null,
        var onAuthTokensChanged: (UiAccount.Bluesky.Credential) -> Unit = {},
    )

    companion object : HttpClientPlugin<Config, BlueskyAuthPlugin> {
        override val key = AttributeKey<BlueskyAuthPlugin>("BlueskyAuthPlugin")

        override fun prepare(block: Config.() -> Unit): BlueskyAuthPlugin {
            val config = Config().apply(block)
            return BlueskyAuthPlugin(
                config.json,
                config.oauthApi,
                config.accountKey,
                config.authTokens,
                config.onAuthTokensChanged,
            )
        }

        override fun install(
            plugin: BlueskyAuthPlugin,
            scope: HttpClient,
        ) {
            scope.plugin(HttpSend.Plugin).intercept { context ->
                if (!context.headers.contains(HttpHeaders.Authorization)) {
                    context.auth(plugin)
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

                val newTokens =
                    when (response.getOrNull()?.error) {
                        "ExpiredToken" -> refreshExpiredToken(plugin, scope)
                        "use_dpop_nonce" -> refreshDpopNonce(plugin, result.response)
                        "invalid_token" -> throw LoginExpiredException(
                            plugin.accountKey!!,
                            PlatformType.Bluesky,
                        )

                        else -> null
                    }

                if (newTokens != null) {
                    plugin.onAuthTokensChanged(newTokens)

                    context.headers.remove(HttpHeaders.Authorization)
                    context.headers.remove("DPoP")
                    context.auth(plugin)
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
                        else -> throw IllegalStateException(
                            "Unexpected error after refreshing token: ${newResponse.getOrNull()?.error}",
                        )
                    }
                }

                onResponse(plugin, result.response)
                result
            }
        }

        private suspend fun HttpRequestBuilder.auth(plugin: BlueskyAuthPlugin) {
            when (val tokens = plugin.authTokens) {
                is UiAccount.Bluesky.Credential.BlueskyCredential ->
                    header(
                        HttpHeaders.Authorization,
                        "Bearer ${tokens.accessToken}",
                    )

                is UiAccount.Bluesky.Credential.OAuthCredential ->
                    applyDpop(
                        plugin,
                        tokens,
                        tokens.accessToken,
                    )

                null -> {
                    // No tokens available, do not add Authorization header
                }
            }
        }

        private suspend fun HttpRequestBuilder.refresh(plugin: BlueskyAuthPlugin) {
            when (val tokens = plugin.authTokens) {
                is UiAccount.Bluesky.Credential.BlueskyCredential ->
                    header(
                        HttpHeaders.Authorization,
                        "Bearer ${tokens.refreshToken}",
                    )

                is UiAccount.Bluesky.Credential.OAuthCredential ->
                    applyDpop(
                        plugin,
                        tokens,
                        tokens.refreshToken,
                    )

                null -> {
                    // No tokens available, do not add Authorization header
                }
            }
        }

        private fun onResponse(
            plugin: BlueskyAuthPlugin,
            response: HttpResponse,
        ) {
            refreshDpopNonce(plugin, response)?.let { newTokens ->
                plugin.onAuthTokensChanged(newTokens)
            }
        }

        private suspend fun refreshExpiredToken(
            plugin: BlueskyAuthPlugin,
            scope: HttpClient,
        ): UiAccount.Bluesky.Credential? =
            when (val tokens = plugin.authTokens) {
                is UiAccount.Bluesky.Credential.BlueskyCredential -> {
                    val refreshResponse =
                        scope.post("/xrpc/com.atproto.server.refreshSession") {
                            refresh(plugin)
                        }
                    refreshResponse
                        .toAtpResponse<RefreshSessionResponse>()
                        .maybeResponse()
                        ?.let { refreshed ->
                            UiAccount.Bluesky.Credential.BlueskyCredential(
                                accessToken = refreshed.accessJwt,
                                refreshToken = refreshed.refreshJwt,
                                baseUrl = plugin.authTokens.baseUrl,
                            )
                        }
                }

                is UiAccount.Bluesky.Credential.OAuthCredential -> {
                    plugin.oauthApi
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

        private fun refreshDpopNonce(
            plugin: BlueskyAuthPlugin,
            callResponse: HttpResponse,
        ): UiAccount.Bluesky.Credential? =
            when (val tokens = plugin.authTokens) {
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

        private suspend fun HttpRequestBuilder.applyDpop(
            plugin: BlueskyAuthPlugin,
            tokens: UiAccount.Bluesky.Credential.OAuthCredential,
            auth: String,
        ) {
            url.protocol = tokens.oAuthToken.pds.protocol
            url.host = tokens.oAuthToken.pds.host

            val dpopHeader =
                plugin.oauthApi.createDpopHeaderValue(
                    keyPair = tokens.oAuthToken.keyPair,
                    method = method.value,
                    endpoint = url.toString(),
                    nonce = tokens.oAuthToken.nonce,
                    accessToken = auth,
                )

            header(HttpHeaders.Authorization, "DPoP $auth")
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
