package dev.dimension.flare.data.network.fanbox

import com.fleeksoft.ksoup.Ksoup
import dev.dimension.flare.common.JSON
import dev.dimension.flare.data.network.fanbox.api.FanboxResources
import dev.dimension.flare.data.network.fanbox.api.FanboxWebResources
import dev.dimension.flare.data.network.fanbox.api.createFanboxResources
import dev.dimension.flare.data.network.fanbox.api.createFanboxWebResources
import dev.dimension.flare.data.network.ktorfit
import dev.dimension.flare.data.platform.FANBOX_WEB_HOST
import dev.dimension.flare.data.platform.FanboxCredential
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOf

private const val FANBOX_API_URL = "https://api.fanbox.cc/"
internal const val FANBOX_WEB_URL = "https://www.fanbox.cc/"
internal const val FANBOX_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 13; Flare) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36"
private const val FANBOX_JSON_ACCEPT = "application/json, text/plain, */*"
private const val FANBOX_HTML_ACCEPT = "text/html,*/*"

private class FanboxHeaderConfig {
    var accept: String = FANBOX_JSON_ACCEPT
    var credentialFlow: Flow<FanboxCredential>? = null
}

private val FanboxHeaderPlugin =
    createClientPlugin("FanboxHeaderPlugin", ::FanboxHeaderConfig) {
        val accept = pluginConfig.accept
        val credentialFlow = pluginConfig.credentialFlow
        onRequest { request, _ ->
            credentialFlow
                ?.firstOrNull()
                ?.sessionId
                ?.takeIf { it.isNotBlank() }
                ?.let { sessionId ->
                    request.headers.append(HttpHeaders.Cookie, fanboxSessionCookie(sessionId))
                }
            request.headers.append(HttpHeaders.UserAgent, FANBOX_USER_AGENT)
            request.headers.append("Origin", "https://$FANBOX_WEB_HOST")
            request.headers.append("Referer", "https://$FANBOX_WEB_HOST/")
            request.headers.append("Accept", accept)
        }
    }

private fun fanboxKtorfit(
    baseUrl: String,
    accept: String,
    credentialFlow: Flow<FanboxCredential>,
) = ktorfit(baseUrl) {
    expectSuccess = true
    install(FanboxHeaderPlugin) {
        this.accept = accept
        this.credentialFlow = credentialFlow
    }
}

private fun fanboxResources(credentialFlow: Flow<FanboxCredential>): FanboxResources =
    fanboxKtorfit(FANBOX_API_URL, FANBOX_JSON_ACCEPT, credentialFlow)
        .createFanboxResources()

private fun fanboxWebResources(credentialFlow: Flow<FanboxCredential>): FanboxWebResources =
    fanboxKtorfit(FANBOX_WEB_URL, FANBOX_HTML_ACCEPT, credentialFlow)
        .createFanboxWebResources()

internal class FanboxService(
    private val credentialFlow: Flow<FanboxCredential>,
    private val onCredentialRefreshed: suspend (FanboxCredential) -> Unit = {},
) : FanboxResources by fanboxResources(credentialFlow) {
    private val webResources: FanboxWebResources =
        fanboxWebResources(credentialFlow)

    suspend fun metadata(): FanboxMetaDataEntity = webResources.metadata().toFanboxMetadata()

    suspend fun metadata(sessionId: String): FanboxMetaDataEntity {
        val html =
            fanboxWebResources(
                flowOf(FanboxCredential(sessionId = sessionId, userId = "")),
            ).metadata()
        return html.toFanboxMetadata()
    }

    suspend fun credentialWithCsrf(): FanboxCredential = credentialWithCsrf(forceRefresh = false)

    suspend fun refreshCredential(): FanboxCredential = credentialWithCsrf(forceRefresh = true)

    suspend fun currentCredential(): FanboxCredential = credential()

    private suspend fun credential(): FanboxCredential = credentialFlow.first()

    private suspend fun credentialWithCsrf(forceRefresh: Boolean = false): FanboxCredential {
        val current = credential()
        if (!forceRefresh && !current.csrfToken.isNullOrBlank()) {
            return current
        }
        val metadata = metadata()
        val user = metadata.context?.user
        val updated =
            current.copy(
                csrfToken = metadata.csrfToken,
                userId = user?.userId ?: current.userId,
                creatorId = user?.creatorId ?: current.creatorId,
                name = user?.name ?: current.name,
                iconUrl = user?.iconUrl ?: current.iconUrl,
                showAdultContent = user?.showAdultContent ?: current.showAdultContent,
                isSupporter = user?.isSupporter ?: current.isSupporter,
                isCreator = user?.isCreator ?: current.isCreator,
            )
        if (updated != current) {
            onCredentialRefreshed(updated)
        }
        return updated
    }
}

private fun fanboxSessionCookie(sessionId: String): String = "FANBOXSESSID=$sessionId"

internal fun FanboxCredential.requireCsrfToken(): String =
    csrfToken?.takeIf { it.isNotBlank() }
        ?: error("FANBOX CSRF token is missing")

private fun String.toFanboxMetadata(): FanboxMetaDataEntity {
    val content =
        Ksoup
            .parse(this)
            .selectFirst("meta[name=metadata]")
            ?.attr("content")
            ?.takeIf { it.isNotBlank() }
            ?: error("FANBOX metadata is missing")
    return JSON.decodeFromString(FanboxMetaDataEntity.serializer(), content)
}
