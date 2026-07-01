package dev.dimension.flare.data.network.bluesky

import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.platform.BlueskyCredential
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import sh.christian.ozone.BlueskyJson
import sh.christian.ozone.oauth.OAuthScope
import sh.christian.ozone.oauth.OAuthToken
import kotlin.io.encoding.Base64

internal const val BLUESKY_SCOPE_ATPROTO = "atproto"
internal const val BLUESKY_SCOPE_TRANSITION_CHAT = "transition:chat.bsky"
internal const val BLUESKY_SCOPE_TRANSITION_GENERIC = "transition:generic"

internal val FLARE_BLUESKY_OAUTH_SCOPES =
    listOf(
        OAuthScope(BLUESKY_SCOPE_ATPROTO),
        OAuthScope(BLUESKY_SCOPE_TRANSITION_CHAT),
        OAuthScope(BLUESKY_SCOPE_TRANSITION_GENERIC),
    )

internal val FLARE_BLUESKY_DELEGATION_SCOPES =
    listOf(
        BLUESKY_SCOPE_TRANSITION_GENERIC,
        BLUESKY_SCOPE_TRANSITION_CHAT,
    )

internal fun OAuthToken.missingFlareOAuthScopes(): List<String> {
    val grantedScopes = scopes.mapTo(mutableSetOf()) { it.value }
    return FLARE_BLUESKY_DELEGATION_SCOPES.filterNot(grantedScopes::contains)
}

internal fun OAuthToken.requireFlareOAuthScopes() {
    val missingScopes = missingFlareOAuthScopes()
    require(missingScopes.isEmpty()) {
        "OAuth token is missing required scope(s): ${missingScopes.joinToString(" ")}"
    }
}

internal fun OAuthToken.delegationControllerDidOrNull(): String? =
    runCatching {
        val payload =
            accessToken
                .split('.')
                .getOrNull(1)
                ?: return null
        BlueskyJson
            .parseToJsonElement(payload.decodeJwtPayload())
            .jsonObject["act"]
            ?.jsonObject
            ?.get("sub")
            ?.jsonPrimitive
            ?.contentOrNull
    }.getOrNull()

internal suspend fun repairTranquilDelegationScopes(
    credential: BlueskyCredential.OAuthCredential,
    controllerDid: String,
) {
    val credentialFlow = MutableStateFlow<BlueskyCredential>(credential)
    val oauthApi =
        sh.christian.ozone.oauth.OAuthApi(
            httpClient =
                ktorClient {
                    install(DefaultRequest) {
                        url.takeFrom(credential.baseUrl)
                    }
                },
            challengeSelector = { OAuthCodeChallengeMethodS256 },
        )
    val client =
        ktorClient {
            install(DefaultRequest) {
                url.takeFrom(credential.baseUrl)
            }
            install(BlueskyAuthPlugin) {
                baseUrlFlow = credentialFlow.map { it.baseUrl }
                authTokenFlow = credentialFlow
                onAuthTokensChanged = { credentialFlow.value = it }
                this.oauthApi = oauthApi
            }
            expectSuccess = false
        }

    try {
        val controllersBody =
            client
                .get("/xrpc/_delegation.listControllers")
                .requireTranquilSuccess("list Tranquil delegation controllers")
        val grantedScopes = controllersBody.findDelegationControllerScopes(controllerDid)
        val updatedScopes =
            mergeDelegationScopes(
                grantedScopes = grantedScopes,
                scopesToAdd = FLARE_BLUESKY_DELEGATION_SCOPES,
            ).joinToString(" ")
        client
            .post("/xrpc/_delegation.updateControllerScopes") {
                contentType(ContentType.Application.Json)
                setBody(
                    buildJsonObject {
                        put("controller_did", JsonPrimitive(controllerDid))
                        put("granted_scopes", JsonPrimitive(updatedScopes))
                    }.toString(),
                )
            }.requireTranquilSuccess("update Tranquil delegation scopes")
    } finally {
        client.close()
    }
}

internal fun mergeDelegationScopes(
    grantedScopes: String,
    scopesToAdd: List<String>,
): List<String> {
    val merged =
        grantedScopes
            .split(Regex("\\s+"))
            .filterTo(mutableListOf()) { it.isNotBlank() }
    val seen = merged.toMutableSet()
    scopesToAdd.forEach { scope ->
        if (seen.add(scope)) {
            merged += scope
        }
    }
    return merged
}

private suspend fun HttpResponse.requireTranquilSuccess(action: String): String {
    val body = bodyAsText()
    if (status.isSuccess()) {
        return body
    }

    val message =
        runCatching {
            val json = BlueskyJson.parseToJsonElement(body).jsonObject
            json.stringValue("message")
                ?: json.stringValue("error_description")
                ?: json.stringValue("error")
        }.getOrNull()
    throw IllegalStateException(
        buildString {
            append("Failed to ")
            append(action)
            append(": HTTP ")
            append(status.value)
            if (!message.isNullOrBlank()) {
                append(" ")
                append(message)
            }
        },
    )
}

private fun String.findDelegationControllerScopes(controllerDid: String): String {
    val controllers =
        BlueskyJson
            .parseToJsonElement(this)
            .jsonObject["controllers"]
            ?.jsonArray
            ?: JsonArray(emptyList())
    val controller =
        controllers
            .firstOrNull { element ->
                element.jsonObject.stringValue("did") == controllerDid
            }?.jsonObject
            ?: error("Delegation controller $controllerDid was not found on this PDS.")

    return controller.stringValue("grantedScopes")
        ?: controller.stringValue("granted_scopes")
        ?: ""
}

private fun JsonObject.stringValue(key: String): String? =
    this[key]
        ?.jsonPrimitive
        ?.contentOrNull

private fun String.decodeJwtPayload(): String {
    val padded =
        replace('-', '+')
            .replace('_', '/')
            .let { value ->
                value + "=".repeat((4 - value.length % 4) % 4)
            }
    return Base64.decode(padded).decodeToString()
}
