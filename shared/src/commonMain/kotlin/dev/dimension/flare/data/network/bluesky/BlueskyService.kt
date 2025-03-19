package dev.dimension.flare.data.network.bluesky

import com.atproto.server.RefreshSessionResponse
import dev.dimension.flare.common.JSON
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.model.MicroBlogKey
import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.call.body
import io.ktor.client.call.save
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.Url
import io.ktor.util.AttributeKey
import kotlinx.serialization.json.Json
import sh.christian.ozone.BlueskyApi
import sh.christian.ozone.XrpcBlueskyApi
import sh.christian.ozone.api.response.AtpErrorDescription
import sh.christian.ozone.api.response.StatusCode

internal data class BlueskyService(
    private val baseUrl: String,
    private val accountKey: MicroBlogKey? = null,
    private val accessToken: String? = null,
    private val refreshToken: String? = null,
    private val onTokenRefreshed: ((accessToken: String, refreshToken: String) -> Unit)? = null,
) : BlueskyApi by XrpcBlueskyApi(
        ktorClient {
            install(DefaultRequest) {
                val hostUrl = Url(baseUrl)
                url.protocol = hostUrl.protocol
                url.host = hostUrl.host
                url.port = hostUrl.port
            }
            install(XrpcAuthPlugin) {
                json = JSON
                access = accessToken
                refresh = refreshToken
                tokenRefreshed = onTokenRefreshed
            }
            install(AtprotoProxyPlugin)

            expectSuccess = false
        },
    )

// internal data object UnspeccedBlueskyService : UnspeccedBlueskyApi by XrpcUnspeccedBlueskyApi()

private class AtprotoProxyPlugin {
    companion object : HttpClientPlugin<Unit, AtprotoProxyPlugin> {
        override val key = AttributeKey<AtprotoProxyPlugin>("AtprotoProxyPlugin")

        override fun prepare(block: Unit.() -> Unit): AtprotoProxyPlugin = AtprotoProxyPlugin()

        override fun install(
            plugin: AtprotoProxyPlugin,
            scope: HttpClient,
        ) {
            scope.requestPipeline.intercept(HttpRequestPipeline.State) {
                if (context.url.pathSegments
                        .lastOrNull()
                        ?.startsWith("chat.bsky.convo.") == true
                ) {
                    context.headers["Atproto-Proxy"] = "did:web:api.bsky.chat#bsky_chat"
                }
            }
        }
    }
}

/**
 * Appends the `Authorization` header to XRPC requests, as well as automatically refreshing and
 * replaying a network request if it fails due to an expired access token.
 */
internal class XrpcAuthPlugin(
    private val json: Json,
    private val accessToken: String? = null,
    private val refreshToken: String? = null,
    private val onTokenRefreshed: ((accessToken: String, refreshToken: String) -> Unit)? = null,
) {
    class Config(
        var json: Json = Json { ignoreUnknownKeys = true },
        var access: String? = null,
        var refresh: String? = null,
        var tokenRefreshed: ((accessToken: String, refreshToken: String) -> Unit)? = null,
    )

    companion object : HttpClientPlugin<Config, XrpcAuthPlugin> {
        override val key = AttributeKey<XrpcAuthPlugin>("XrpcAuthPlugin")

        override fun prepare(block: Config.() -> Unit): XrpcAuthPlugin {
            val config = Config().apply(block)
            return XrpcAuthPlugin(
                config.json,
                config.access,
                config.refresh,
                config.tokenRefreshed,
            )
        }

        override fun install(
            plugin: XrpcAuthPlugin,
            scope: HttpClient,
        ) {
            scope.plugin(HttpSend).intercept { context ->
                if (!context.headers.contains(Authorization)) {
                    plugin.accessToken?.let { context.bearerAuth(it) }
                }
                var result: HttpClientCall = execute(context)
                if (result.response.status != BadRequest) {
                    return@intercept result
                }

                // Cache the response in memory since we will need to decode it potentially more than once.
                result = result.save()

                val response =
                    tryRun<AtpErrorDescription> {
                        plugin.json.decodeFromString(result.response.bodyAsText())
                    }

                if (response.getOrNull()?.error == "ExpiredToken") {
                    val refreshResponse =
                        scope.post("/xrpc/com.atproto.server.refreshSession") {
                            plugin.refreshToken?.let { bearerAuth(it) }
                        }
                    if (StatusCode.fromCode(refreshResponse.status.value) == StatusCode.Okay) {
                        val refreshed = refreshResponse.body<RefreshSessionResponse>()
                        val newAccessToken = refreshed.accessJwt
                        val newRefreshToken = refreshed.refreshJwt

                        plugin.onTokenRefreshed?.invoke(newAccessToken, newRefreshToken)

                        context.headers.remove(Authorization)
                        context.bearerAuth(newAccessToken)
                        result = execute(context)
                    }
                }

                result
            }
        }
    }
}
