package dev.dimension.flare.data.network.mastodon

import dev.dimension.flare.data.network.ktorfit
import dev.dimension.flare.data.network.mastodon.api.MastodonOAuthResources
import dev.dimension.flare.data.network.mastodon.api.model.CreateApplicationResponse
import dev.dimension.flare.data.network.mastodon.api.model.MastodonAuthScope
import io.ktor.http.encodeURLParameter

class MastodonOAuthService(
    private val baseUrl: String,
    private val client_name: String,
    private val website: String? = null,
    private val redirect_uri: String = "urn:ietf:wg:oauth:2.0:oob",
    private val scopes: List<MastodonAuthScope> = listOf(
        MastodonAuthScope.read,
        MastodonAuthScope.write,
        MastodonAuthScope.follow,
        MastodonAuthScope.push,
    ),
) {
    private val resources: MastodonOAuthResources by lazy {
        ktorfit(baseUrl).create()
    }

    suspend fun createApplication() = resources.createApplication(
        client_name = client_name,
        redirect_uris = redirect_uri,
        scopes = scopes.joinToString(" ") { it.name },
        website = website,
    )

    fun getWebOAuthUrl(response: CreateApplicationResponse) =
        "$baseUrl/oauth/authorize?client_id=${response.clientID}&response_type=code&redirect_uri=${response.redirectURI.encodeURLParameter()}&scope=${
            scopes.joinToString(
            " "
        ) { it.name }.encodeURLParameter()
        }"

    suspend fun getAccessToken(code: String, response: CreateApplicationResponse) =
        resources.requestToken(
            client_id = response.clientID,
            client_secret = response.clientSecret,
            redirect_uri = response.redirectURI,
            scope = scopes.joinToString(" ") { it.name },
            code = code,
            grant_type = "authorization_code",
        )

    suspend fun verifyCredentials(accessToken: String) =
        resources.verifyCredentials(accessToken = "Bearer $accessToken")
}
