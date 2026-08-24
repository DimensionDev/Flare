package dev.dimension.flare.data.network.bluesky

import dev.dimension.flare.data.network.ktorClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import io.ktor.http.buildUrl
import io.ktor.http.decodeURLPart
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import sh.christian.ozone.BlueskyJson
import sh.christian.ozone.oauth.OAuthToken

private const val AT_PROTO_PDS_SERVICE_ID = "#atproto_pds"
private const val AT_PROTO_PDS_SERVICE_TYPE = "AtprotoPersonalDataServer"

internal suspend fun OAuthToken.withResolvedPds(issuer: String): OAuthToken {
    val httpClient =
        ktorClient {
            followRedirects = false
        }
    return try {
        withResolvedPds(issuer, httpClient)
    } finally {
        httpClient.close()
    }
}

internal suspend fun OAuthToken.withResolvedPds(
    issuer: String,
    httpClient: HttpClient,
): OAuthToken {
    val issuerUrl = Url(issuer).requireHttpsOrigin("OAuth issuer")
    val did = subject.did
    val didDocument =
        httpClient.getJson<OAuthDidDocument>(
            url = didDocumentUrl(did),
            description = "DID document for $did",
        )
    check(didDocument.id == did) {
        "Resolved DID document id ${didDocument.id} does not match OAuth subject $did"
    }

    val matchingServices =
        didDocument.services.filter { service ->
            service.type == AT_PROTO_PDS_SERVICE_TYPE &&
                (service.id == AT_PROTO_PDS_SERVICE_ID || service.id == "$did$AT_PROTO_PDS_SERVICE_ID")
        }
    check(matchingServices.size == 1) {
        "DID document for $did must declare exactly one $AT_PROTO_PDS_SERVICE_TYPE service"
    }
    val pdsUrl =
        Url(
            matchingServices
                .single()
                .serviceEndpoint
                .orEmpty(),
        ).requireHttpsOrigin("PDS service endpoint")

    val protectedResource =
        httpClient.getJson<OAuthProtectedResource>(
            url = pdsUrl.protectedResourceMetadataUrl(),
            description = "OAuth protected-resource metadata for $pdsUrl",
        )
    val declaredResource =
        Url(protectedResource.resource).requireHttpsOrigin("OAuth protected resource")
    check(declaredResource.sameOriginAs(pdsUrl)) {
        "OAuth protected resource $declaredResource does not match PDS $pdsUrl"
    }
    check(protectedResource.authorizationServers.size == 1) {
        "PDS $pdsUrl must declare exactly one OAuth authorization server"
    }
    val declaredIssuer =
        Url(protectedResource.authorizationServers.single())
            .requireHttpsOrigin("PDS authorization server")
    check(declaredIssuer.sameOriginAs(issuerUrl)) {
        "PDS $pdsUrl is bound to OAuth issuer $declaredIssuer, not $issuerUrl"
    }

    return copy(pdsUrl = pdsUrl.toString())
}

private suspend inline fun <reified T> HttpClient.getJson(
    url: Url,
    description: String,
): T {
    val response = get(url)
    check(response.status == HttpStatusCode.OK) {
        "Failed to fetch $description: HTTP ${response.status.value}"
    }
    val body = response.bodyAsText()
    return try {
        BlueskyJson.decodeFromString(body)
    } catch (error: Exception) {
        throw IllegalStateException("Failed to parse $description", error)
    }
}

private fun didDocumentUrl(did: String): Url =
    when {
        did.startsWith("did:plc:") -> Url("https://plc.directory/$did")
        did.startsWith("did:web:") -> didWebDocumentUrl(did)
        else -> error("Unsupported AT Protocol DID method: $did")
    }

private fun didWebDocumentUrl(did: String): Url {
    val components = did.removePrefix("did:web:").split(':')
    require(components.isNotEmpty() && components.none(String::isEmpty)) {
        "Invalid did:web identifier: $did"
    }
    val authority = components.first().decodeURLPart()
    require(authority.none { it == '/' || it == '?' || it == '#' || it == '@' }) {
        "Invalid did:web authority: $authority"
    }
    val authorityUrl = Url("https://$authority").requireHttpsOrigin("did:web authority")
    val pathSegments =
        if (components.size == 1) {
            listOf(".well-known", "did.json")
        } else {
            components.drop(1).map(String::decodeURLPart) + "did.json"
        }
    return buildUrl {
        protocol = authorityUrl.protocol
        host = authorityUrl.host
        port = authorityUrl.port
        appendPathSegments(*pathSegments.toTypedArray(), encodeSlash = true)
    }
}

private fun Url.protectedResourceMetadataUrl(): Url =
    buildUrl {
        protocol = this@protectedResourceMetadataUrl.protocol
        host = this@protectedResourceMetadataUrl.host
        port = this@protectedResourceMetadataUrl.port
        appendPathSegments(".well-known", "oauth-protected-resource")
    }

private fun Url.requireHttpsOrigin(description: String): Url {
    require(protocol == URLProtocol.HTTPS) { "$description must use HTTPS: $this" }
    require(user.isNullOrEmpty() && password.isNullOrEmpty()) { "$description must not contain credentials: $this" }
    require(encodedPath.isEmpty() || encodedPath == "/") { "$description must not contain a path: $this" }
    require(parameters.isEmpty()) { "$description must not contain query parameters: $this" }
    require(fragment.isEmpty()) { "$description must not contain a fragment: $this" }
    return this
}

private fun Url.sameOriginAs(other: Url): Boolean =
    protocol == other.protocol &&
        host.equals(other.host, ignoreCase = true) &&
        port == other.port

@Serializable
private data class OAuthDidDocument(
    val id: String? = null,
    @SerialName("service")
    val services: List<OAuthDidService> = emptyList(),
)

@Serializable
private data class OAuthDidService(
    val id: String? = null,
    val type: String? = null,
    val serviceEndpoint: String? = null,
)

@Serializable
private data class OAuthProtectedResource(
    val resource: String,
    @SerialName("authorization_servers")
    val authorizationServers: List<String> = emptyList(),
)
