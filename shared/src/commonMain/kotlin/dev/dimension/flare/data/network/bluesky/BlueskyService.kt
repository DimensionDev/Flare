package dev.dimension.flare.data.network.bluesky

import com.atproto.server.RefreshSessionResponse
import dev.dimension.flare.common.JSON
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.data.database.app.dao.AccountDao
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiAccount.Companion.toUi
import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.call.body
import io.ktor.client.call.save
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders.Authorization
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import io.ktor.http.Url
import io.ktor.util.AttributeKey
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.Json
import sh.christian.ozone.BlueskyApi
import sh.christian.ozone.XrpcBlueskyApi
import sh.christian.ozone.api.response.AtpErrorDescription

internal class BlueskyService(
    private val baseUrl: String,
    private val accountKey: MicroBlogKey? = null,
    private val accountQueries: AccountDao? = null,
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
                this.accountKey = accountKey
                this.accountQueries = accountQueries
            }

            expectSuccess = false
        },
    )

/**
 * Appends the `Authorization` header to XRPC requests, as well as automatically refreshing and
 * replaying a network request if it fails due to an expired access token.
 */
internal class XrpcAuthPlugin(
    private val json: Json,
    private val accountKey: MicroBlogKey?,
    private val accountQueries: AccountDao?,
) {
    class Config(
        var json: Json = Json { ignoreUnknownKeys = true },
        var accountKey: MicroBlogKey? = null,
        var accountQueries: AccountDao? = null,
    )

    companion object : HttpClientPlugin<Config, XrpcAuthPlugin> {
        override val key = AttributeKey<XrpcAuthPlugin>("XrpcAuthPlugin")

        override fun prepare(block: Config.() -> Unit): XrpcAuthPlugin {
            val config = Config().apply(block)
            return XrpcAuthPlugin(config.json, config.accountKey, config.accountQueries)
        }

        override fun install(
            plugin: XrpcAuthPlugin,
            scope: HttpClient,
        ) {
            scope.plugin(HttpSend).intercept { context ->
                if (!context.headers.contains(Authorization) && plugin.accountKey != null && plugin.accountQueries != null) {
                    val account =
                        plugin.accountQueries
                            .get(plugin.accountKey)
                            .firstOrNull()
                            ?.toUi() as? UiAccount.Bluesky
                    if (account != null) {
                        context.bearerAuth(account.credential.accessToken)
                    }
                }

                var result: HttpClientCall = execute(context)
                if (result.response.status != BadRequest) {
                    return@intercept result
                }

                // Cache the response in memory since we will need to decode it potentially more than once.
                result = result.save()

                val response =
                    runCatching<AtpErrorDescription> {
                        plugin.json.decodeFromString(result.response.bodyAsText())
                    }

                if (response.getOrNull()?.error == "ExpiredToken" && plugin.accountKey != null && plugin.accountQueries != null) {
                    val account =
                        plugin.accountQueries
                            .get(plugin.accountKey)
                            .firstOrNull()
                            ?.toUi() as? UiAccount.Bluesky
                    if (account != null) {
                        val refreshResponse =
                            scope.post("/xrpc/com.atproto.server.refreshSession") {
                                bearerAuth(account.credential.refreshToken)
                            }
                        runCatching { refreshResponse.body<RefreshSessionResponse>() }
                            .getOrNull()
                            ?.let { refreshed ->
                                val newAccessToken = refreshed.accessJwt
                                val newRefreshToken = refreshed.refreshJwt
                                plugin.accountQueries.setCredential(
                                    credentialJson =
                                        UiAccount.Bluesky
                                            .Credential(
                                                baseUrl = account.credential.baseUrl,
                                                accessToken = newAccessToken,
                                                refreshToken = newRefreshToken,
                                            ).encodeJson(),
                                    accountKey = plugin.accountKey,
                                )
                                context.headers.remove(Authorization)
                                context.bearerAuth(newAccessToken)
                                result = execute(context)
                            }
                    }
                }

                result
            }
        }
    }
}

// internal fun UiAccount.Bluesky.getService(appDatabase: AppDatabase) =
//    BlueskyService(
//        baseUrl = credential.baseUrl,
//        accountKey = accountKey,
//        accountQueries = appDatabase.accountDao(),
//    )
