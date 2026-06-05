package dev.dimension.flare.data.network.pixiv

import dev.dimension.flare.data.platform.PixivCredential
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.call.save
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.util.AttributeKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

internal class PixivAuthPlugin(
    private val accountKey: MicroBlogKey?,
    private val credentialFlow: Flow<PixivCredential>?,
    private val onCredentialRefreshed: suspend (PixivCredential) -> Unit,
    private val refreshToken: suspend (
        clientId: String,
        clientSecret: String,
        refreshToken: String,
    ) -> PixivCredential?,
) {
    private val refreshMutex = Mutex()

    class Config {
        var accountKey: MicroBlogKey? = null
        var credentialFlow: Flow<PixivCredential>? = null
        var onCredentialRefreshed: suspend (PixivCredential) -> Unit = {}
        var refreshToken: suspend (
            clientId: String,
            clientSecret: String,
            refreshToken: String,
        ) -> PixivCredential? = { _, _, _ -> null }
    }

    companion object : HttpClientPlugin<Config, PixivAuthPlugin> {
        override val key = AttributeKey<PixivAuthPlugin>("PixivAuthPlugin")

        override fun prepare(block: Config.() -> Unit): PixivAuthPlugin {
            val config = Config().apply(block)
            return PixivAuthPlugin(
                accountKey = config.accountKey,
                credentialFlow = config.credentialFlow,
                onCredentialRefreshed = config.onCredentialRefreshed,
                refreshToken = config.refreshToken,
            )
        }

        override fun install(
            plugin: PixivAuthPlugin,
            scope: HttpClient,
        ) {
            scope.plugin(HttpSend.Plugin).intercept { context ->
                val credential = plugin.currentCredential()
                val authCredential =
                    if (credential != null && credential.shouldRefresh()) {
                        plugin.refreshExpiredTokenLocked(credential) ?: credential
                    } else {
                        credential
                    }
                if (!context.headers.contains(HttpHeaders.Authorization) && authCredential != null) {
                    context.auth(authCredential)
                }

                var result: HttpClientCall = execute(context)
                if (result.response.status.isSuccess() || authCredential == null || result.response.status != HttpStatusCode.Unauthorized) {
                    return@intercept result
                }

                result = result.save()
                val refreshed =
                    plugin.refreshExpiredTokenLocked(authCredential)
                        ?: throw LoginExpiredException(
                            plugin.accountKey ?: MicroBlogKey("unknown", "unknown"),
                            PlatformType.Pixiv,
                        )
                context.headers.remove(HttpHeaders.Authorization)
                context.auth(refreshed)
                execute(context)
            }
        }

        private fun HttpRequestBuilder.auth(credential: PixivCredential) {
            header(HttpHeaders.Authorization, "Bearer ${credential.accessToken}")
        }
    }

    private suspend fun currentCredential(): PixivCredential? = credentialFlow?.firstOrNull()

    private suspend fun refreshExpiredTokenLocked(credential: PixivCredential): PixivCredential? =
        refreshMutex.withLock {
            val latestCredential = currentCredential()
            if (latestCredential != null && latestCredential != credential && !latestCredential.shouldRefresh()) {
                latestCredential
            } else {
                refreshExpiredToken(credential)?.also {
                    onCredentialRefreshed(it)
                }
            }
        }

    private suspend fun refreshExpiredToken(credential: PixivCredential): PixivCredential? {
        val clientId = credential.clientId
        val clientSecret = credential.clientSecret
        if (clientId.isNullOrBlank() || clientSecret.isNullOrBlank()) {
            return null
        }
        return refreshToken(clientId, clientSecret, credential.refreshToken)
    }

}

private fun PixivCredential.shouldRefresh(): Boolean =
    expiresAtEpochSeconds <= Clock.System.now().epochSeconds + TOKEN_REFRESH_LEEWAY_SECONDS

private const val TOKEN_REFRESH_LEEWAY_SECONDS = 60L
