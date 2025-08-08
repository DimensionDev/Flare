package dev.dimension.flare.data.network.bluesky

import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import io.ktor.client.HttpClient
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.http.Url
import io.ktor.util.AttributeKey
import sh.christian.ozone.BlueskyApi
import sh.christian.ozone.XrpcBlueskyApi

internal data class BlueskyService(
    private val baseUrl: String,
    private val accountKey: MicroBlogKey? = null,
    private val credential: UiAccount.Bluesky.Credential? = null,
    private val onCredentialRefreshed: (UiAccount.Bluesky.Credential) -> Unit = {},
) : BlueskyApi by XrpcBlueskyApi(
        ktorClient {
            install(DefaultRequest) {
                val hostUrl = Url(baseUrl)
                url.protocol = hostUrl.protocol
                url.host = hostUrl.host
                url.port = hostUrl.port
            }
            install(AtprotoProxyPlugin)
            install(BlueskyAuthPlugin) {
                this.authTokens = credential
                this.onAuthTokensChanged = onCredentialRefreshed
                this.accountKey = accountKey
            }

            expectSuccess = false
        },
    )

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
