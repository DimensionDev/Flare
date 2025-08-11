package dev.dimension.flare.data.network.bluesky

import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.util.AttributeKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import sh.christian.ozone.BlueskyApi
import sh.christian.ozone.XrpcBlueskyApi

internal data class BlueskyService private constructor(
    private val baseUrlFlow: Flow<String>,
    private val accountKey: MicroBlogKey? = null,
    private val authTokenFlow: Flow<UiAccount.Bluesky.Credential>? = null,
    private val onCredentialRefreshed: (UiAccount.Bluesky.Credential) -> Unit = {},
) : BlueskyApi by XrpcBlueskyApi(
        ktorClient {
            install(AtprotoProxyPlugin)
            install(BlueskyAuthPlugin) {
                this.baseUrlFlow = baseUrlFlow
                this.accountKey = accountKey
                this.authTokenFlow = authTokenFlow
                this.onAuthTokensChanged = onCredentialRefreshed
            }

            expectSuccess = false
        },
    ) {
    constructor(
        accountKey: MicroBlogKey,
        credentialFlow: Flow<UiAccount.Bluesky.Credential>,
        onCredentialRefreshed: (UiAccount.Bluesky.Credential) -> Unit,
    ) : this(
        baseUrlFlow = credentialFlow.map { it.baseUrl },
        accountKey = accountKey,
        authTokenFlow = credentialFlow,
        onCredentialRefreshed = onCredentialRefreshed,
    )

    constructor(
        baseUrl: String,
    ) : this(
        baseUrlFlow = flowOf(baseUrl),
    )

    fun newBaseUrlService(baseUrl: String): BlueskyService = copy(baseUrlFlow = flowOf(baseUrl))
}

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
