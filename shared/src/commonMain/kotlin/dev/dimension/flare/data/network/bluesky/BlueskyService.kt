package dev.dimension.flare.data.network.bluesky

import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.SHA256
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.http.Url
import io.ktor.util.AttributeKey
import io.ktor.util.encodeBase64
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import sh.christian.ozone.BlueskyApi
import sh.christian.ozone.XrpcBlueskyApi
import sh.christian.ozone.oauth.OAuthApi
import sh.christian.ozone.oauth.OAuthCodeChallengeMethod

// compatibility support for darwin (iOS, macOS) since Ktor's SHA-256 implementation is not available there
// see: https://github.com/ktorio/ktor/blob/477d76409fec6c2d71683817c6060f1b2afdcbb2/ktor-utils/posix/src/io/ktor/util/CryptoNative.kt#L25C57-L25C92
internal data object OAuthCodeChallengeMethodS256 : OAuthCodeChallengeMethod("S256") {
    override suspend fun provideCodeChallenge(codeVerifier: String): String {
        val hasher =
            CryptographyProvider
                .Default
                .get(SHA256)
                .hasher()
        val sha256 = hasher.hash(codeVerifier.encodeToByteArray())
        val base64UrlSafe = sha256.encodeBase64Url()
        return base64UrlSafe
    }

    private fun ByteArray.encodeBase64Url(): String = encodeBase64().trimEnd('=').replace('+', '-').replace('/', '_')
}

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
                this.oauthApi =
                    OAuthApi(
                        httpClient =
                            ktorClient {
                                install(BaseUrlPlugin) {
                                    this.baseUrlFlow = baseUrlFlow
                                }
                            },
                        challengeSelector = { OAuthCodeChallengeMethodS256 },
                    )
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

private class BaseUrlConfig {
    var baseUrlFlow: Flow<String>? = null
}

private val BaseUrlPlugin =
    createClientPlugin("BaseUrlPlugin", ::BaseUrlConfig) {
        val baseUrlFlow = pluginConfig.baseUrlFlow ?: error("BaseUrlPlugin: baseUrlFlow is not set")
        onRequest { request, _ ->
            baseUrlFlow.firstOrNull()?.let { baseUrl ->
                request.url.protocol = Url(baseUrl).protocol
                request.url.host = Url(baseUrl).host
                request.url.port = Url(baseUrl).port
            }
        }
    }
