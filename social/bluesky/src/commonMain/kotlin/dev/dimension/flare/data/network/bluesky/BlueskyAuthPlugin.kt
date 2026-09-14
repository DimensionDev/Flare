package dev.dimension.flare.data.network.bluesky

import com.atproto.server.RefreshSessionResponse
import dev.dimension.flare.data.platform.BLUESKY_PLATFORM_ID
import dev.dimension.flare.data.platform.BlueskyCredential
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey
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
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.util.AttributeKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import sh.christian.ozone.BlueskyJson
import sh.christian.ozone.api.response.AtpErrorDescription
import sh.christian.ozone.api.response.AtpException
import sh.christian.ozone.api.response.AtpResponse
import sh.christian.ozone.api.response.StatusCode
import sh.christian.ozone.oauth.OAuthApi
import sh.christian.ozone.oauth.OAuthToken
import kotlin.coroutines.cancellation.CancellationException

/**
 * Appends the `Authorization` header to XRPC requests, as well as automatically refreshing and
 * replaying a network request if it fails due to an expired access token.
 */
internal class BlueskyAuthPlugin(
    private val json: Json,
    private val oauthApi: OAuthApi,
    private val resolveOAuthPds: suspend (OAuthToken, String) -> OAuthToken,
    private val accountKey: MicroBlogKey?,
    private val baseUrlFlow: Flow<String>? = null,
    private val authTokenFlow: Flow<BlueskyCredential>?,
    private val onAuthTokensChanged: suspend (BlueskyCredential) -> Unit,
) {
    private val credentialMutex = Mutex()
    private val oauthPdsMigrationMutex = Mutex()
    private var oauthPdsMigration: OAuthPdsMigration? = null

    class Config(
        var json: Json = BlueskyJson,
        var oauthApi: OAuthApi = OAuthApi(),
        var resolveOAuthPds: suspend (OAuthToken, String) -> OAuthToken = { token, issuer ->
            token.withResolvedPds(issuer)
        },
        var accountKey: MicroBlogKey? = null,
        var baseUrlFlow: Flow<String>? = null,
        var authTokenFlow: Flow<BlueskyCredential>? = null,
        var onAuthTokensChanged: suspend (BlueskyCredential) -> Unit = {},
    )

    companion object : HttpClientPlugin<Config, BlueskyAuthPlugin> {
        override val key = AttributeKey<BlueskyAuthPlugin>("BlueskyAuthPlugin")

        override fun prepare(block: Config.() -> Unit): BlueskyAuthPlugin {
            val config = Config().apply(block)
            return BlueskyAuthPlugin(
                json = config.json,
                oauthApi = config.oauthApi,
                resolveOAuthPds = config.resolveOAuthPds,
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
                plugin.baseUrlFlow?.firstOrNull()?.let { baseUrl ->
                    context.url.protocol = Url(baseUrl).protocol
                    context.url.host = Url(baseUrl).host
                    context.url.port = Url(baseUrl).port
                }

                val oAuthApi = plugin.oauthApi
                val credential = plugin.currentCredential()
                if (credential == null || context.headers.contains(HttpHeaders.Authorization)) {
                    return@intercept execute(context)
                }
                context.auth(credential, oAuthApi)

                var result: HttpClientCall = execute(context)
                val isRefresh =
                    context.url.toString().endsWith("/xrpc/com.atproto.server.refreshSession")
                var currentCredential: BlueskyCredential = credential
                var hasRefreshedTokens = false
                var retryCount = 0
                while (true) {
                    val nonceCredential = plugin.refreshDpopNonceLocked(currentCredential, result.response)
                    if (result.response.status.isSuccess()) break

                    result = result.save()
                    if (isRefresh || retryCount >= 3) break
                    val responseBody = result.response.bodyAsText()
                    val error =
                        runCatching {
                            plugin.json.decodeFromString<AtpErrorDescription>(responseBody)
                        }.getOrNull()?.error

                    val newTokens =
                        when {
                            // A nonce challenge does not mean the access token has expired.
                            error == "use_dpop_nonce" -> {
                                nonceCredential
                            }

                            !hasRefreshedTokens &&
                                (
                                    result.response.status == HttpStatusCode.Unauthorized ||
                                        error == "ExpiredToken" || error == "InvalidToken" || error == "invalid_token"
                                ) -> {
                                hasRefreshedTokens = true
                                plugin.refreshExpiredTokenLocked(currentCredential, oAuthApi, scope)
                            }

                            else -> {
                                null
                            }
                        } ?: break

                    retryCount++
                    currentCredential = newTokens
                    context.headers.remove(HttpHeaders.Authorization)
                    context.headers.remove("DPoP")
                    context.auth(newTokens, oAuthApi)
                    result = execute(context)
                }

                result
            }
        }

        private suspend fun HttpRequestBuilder.auth(
            credential: BlueskyCredential,
            oAuthApi: OAuthApi,
        ) {
            when (val tokens = credential) {
                is BlueskyCredential.Password -> {
                    header(
                        HttpHeaders.Authorization,
                        "Bearer ${tokens.accessToken}",
                    )
                }

                is BlueskyCredential.OAuthCredential -> {
                    applyDpop(
                        credential,
                        oAuthApi,
                    )
                }
            }
        }

        private suspend fun HttpRequestBuilder.refresh(
            credential: BlueskyCredential,
            oAuthApi: OAuthApi,
        ) {
            when (val tokens = credential) {
                is BlueskyCredential.Password -> {
                    header(
                        HttpHeaders.Authorization,
                        "Bearer ${tokens.refreshToken}",
                    )
                }

                is BlueskyCredential.OAuthCredential -> {
                    applyDpop(
                        tokens,
                        oAuthApi,
                    )
                }
            }
        }

        private suspend fun refreshExpiredToken(
            credential: BlueskyCredential,
            oAuthApi: OAuthApi,
            scope: HttpClient,
        ): BlueskyCredential =
            when (val tokens = credential) {
                is BlueskyCredential.Password -> {
                    val refreshResponse =
                        scope.post("/xrpc/com.atproto.server.refreshSession") {
                            refresh(tokens, oAuthApi)
                        }
                    when (val response = refreshResponse.toAtpResponse<RefreshSessionResponse>()) {
                        is AtpResponse.Success -> {
                            val refreshed = response.response
                            BlueskyCredential.Password(
                                accessToken = refreshed.accessJwt,
                                refreshToken = refreshed.refreshJwt,
                                baseUrl = tokens.baseUrl,
                            )
                        }

                        is AtpResponse.Failure -> {
                            throw response.asException()
                        }
                    }
                }

                is BlueskyCredential.OAuthCredential -> {
                    oAuthApi
                        .refreshToken(
                            clientId = tokens.oAuthToken.clientId,
                            nonce = tokens.oAuthToken.nonce,
                            refreshToken = tokens.oAuthToken.refreshToken,
                            keyPair = tokens.oAuthToken.keyPair,
                        ).let { refreshed ->
                            tokens.copy(
                                oAuthToken =
                                    refreshed.copy(
                                        pdsUrl = tokens.oAuthToken.pdsUrl,
                                    ),
                            )
                        }
                }
            }

        private fun refreshDpopNonce(
            credential: BlueskyCredential,
            callResponse: HttpResponse,
        ): BlueskyCredential? =
            when (val tokens = credential) {
                is BlueskyCredential.Password -> {
                    null
                }

                is BlueskyCredential.OAuthCredential -> {
                    callResponse.headers["DPoP-Nonce"]?.let {
                        tokens.copy(pdsNonce = it)
                    }
                }
            }

        private suspend fun HttpRequestBuilder.applyDpop(
            tokens: BlueskyCredential.OAuthCredential,
            oAuthApi: OAuthApi,
        ) {
            url.protocol = tokens.oAuthToken.pds.protocol
            url.host = tokens.oAuthToken.pds.host
            url.port = tokens.oAuthToken.pds.port

            val dpopHeader =
                oAuthApi.createDpopHeaderValue(
                    keyPair = tokens.oAuthToken.keyPair,
                    method = method.value,
                    endpoint = url.toString(),
                    nonce = tokens.pdsNonce,
                    accessToken = tokens.oAuthToken.accessToken,
                )

            header(HttpHeaders.Authorization, "DPoP ${tokens.oAuthToken.accessToken}")
            header("DPoP", dpopHeader)
        }
    }

    private suspend fun currentCredential(): BlueskyCredential? {
        val credential = authTokenFlow?.firstOrNull() ?: return null
        if (credential !is BlueskyCredential.OAuthCredential || credential.pdsUrlVerified) {
            return credential
        }

        return oauthPdsMigrationMutex.withLock {
            val migrationKey =
                OAuthPdsMigrationKey(
                    accessToken = credential.oAuthToken.accessToken,
                    baseUrl = credential.baseUrl,
                    pdsUrl = credential.oAuthToken.pdsUrl,
                )
            oauthPdsMigration
                ?.takeIf { it.key == migrationKey }
                ?.let { migration ->
                    credential.copy(
                        oAuthToken = credential.oAuthToken.copy(pdsUrl = migration.pdsUrl),
                        pdsUrlVerified = true,
                    )
                }
                ?: run {
                    val resolvedCredential =
                        credential.copy(
                            oAuthToken = resolveOAuthPds(credential.oAuthToken, credential.baseUrl),
                            pdsUrlVerified = true,
                        )
                    cacheCredential(resolvedCredential)
                    oauthPdsMigration =
                        OAuthPdsMigration(
                            key = migrationKey,
                            pdsUrl = resolvedCredential.oAuthToken.pdsUrl,
                        )
                    resolvedCredential
                }
        }
    }

    private suspend fun cacheCredential(credential: BlueskyCredential) {
        onAuthTokensChanged(credential)
    }

    private suspend fun refreshDpopNonceLocked(
        credential: BlueskyCredential,
        response: HttpResponse,
    ): BlueskyCredential? {
        if (credential !is BlueskyCredential.OAuthCredential || response.headers["DPoP-Nonce"] == null) {
            return null
        }
        return credentialMutex.withLock {
            val latestCredential = currentCredential() ?: return@withLock null
            // A late response must never restore tokens that another request has rotated.
            if (!latestCredential.hasSameTokens(credential)) {
                latestCredential
            } else {
                refreshDpopNonce(latestCredential, response)?.also {
                    if (it != latestCredential) cacheCredential(it)
                }
            }
        }
    }

    private suspend fun refreshExpiredTokenLocked(
        credential: BlueskyCredential,
        oAuthApi: OAuthApi,
        scope: HttpClient,
    ): BlueskyCredential? =
        credentialMutex.withLock {
            val latestCredential = currentCredential()
            if (latestCredential == null || !latestCredential.hasSameTokens(credential)) {
                latestCredential
            } else {
                val refreshed =
                    try {
                        refreshExpiredToken(
                            credential = latestCredential,
                            oAuthApi = oAuthApi,
                            scope = scope,
                        )
                    } catch (error: AtpException) {
                        val invalidRefreshToken =
                            when (latestCredential) {
                                is BlueskyCredential.Password -> {
                                    error.statusCode == StatusCode.AuthenticationRequired ||
                                        error.error?.error == "ExpiredToken" ||
                                        error.error?.error == "InvalidToken"
                                }

                                is BlueskyCredential.OAuthCredential -> {
                                    error.error?.error == "invalid_grant"
                                }
                            }
                        if (invalidRefreshToken) {
                            throw LoginExpiredException(
                                accountKey ?: MicroBlogKey("unknown", "unknown"),
                                BLUESKY_PLATFORM_ID,
                            )
                        }
                        throw error
                    }
                refreshed.also { cacheCredential(it) }
            }
        }

    private fun BlueskyCredential.hasSameTokens(other: BlueskyCredential): Boolean =
        accessToken == other.accessToken && refreshToken == other.refreshToken

    private data class OAuthPdsMigrationKey(
        val accessToken: String,
        val baseUrl: String,
        val pdsUrl: String,
    )

    private data class OAuthPdsMigration(
        val key: OAuthPdsMigrationKey,
        val pdsUrl: String,
    )
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

            AtpResponse.Failure(
                headers = headers,
                statusCode = code,
                response = null,
                error = maybeError,
            )
        }
    }
}

private suspend inline fun HttpResponse.errorDescriptionOrNull(): AtpErrorDescription? =
    when (StatusCode.fromCode(status.value)) {
        is StatusCode.Failure -> {
            call.save()
            try {
                body<AtpErrorDescription>()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                null
            }
        }

        else -> {
            null
        }
    }
