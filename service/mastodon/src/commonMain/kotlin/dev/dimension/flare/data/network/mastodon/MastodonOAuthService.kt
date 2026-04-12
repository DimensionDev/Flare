package dev.dimension.flare.data.network.mastodon

import dev.dimension.flare.data.network.ktorfit
import dev.dimension.flare.data.network.mastodon.api.MastodonOAuthResources
import dev.dimension.flare.data.network.mastodon.api.createMastodonOAuthResources
import dev.dimension.flare.data.network.mastodon.api.model.CreateApplicationResponse
import dev.dimension.flare.data.network.mastodon.api.model.MastodonAuthScope
import io.ktor.http.encodeURLParameter

public class MastodonOAuthService(
    private val baseUrl: String,
    private val client_name: String,
    private val website: String? = null,
    private val redirect_uri: String = "urn:ietf:wg:oauth:2.0:oob",
    private val scopes: List<MastodonAuthScope> =
        listOf(
            MastodonAuthScope.Read,
            MastodonAuthScope.Write,
            MastodonAuthScope.Follow,
            MastodonAuthScope.Push,
        ),
) {
    private val resources by lazy { ktorfit(baseUrl).createMastodonOAuthResources() }

    public suspend fun createApplication(): CreateApplicationResponse =
        resources.createApplication(
            client_name = client_name,
            redirect_uris = redirect_uri,
            scopes = scopes.joinToString(" ") { it.value },
            website = website,
        )

    public fun getWebOAuthUrl(response: CreateApplicationResponse): String =
        "${baseUrl}oauth/authorize?" +
            "client_id=${response.clientID}" +
            "&response_type=code" +
            "&redirect_uri=${response.redirectURI.encodeURLParameter()}" +
            "&scope=${
                scopes.joinToString(
                    " ",
                ) { it.value }.encodeURLParameter()
            }"

    public suspend fun getAccessToken(
        code: String,
        response: CreateApplicationResponse,
    ): dev.dimension.flare.data.network.mastodon.api.model.RequestTokenResponse = resources.requestToken(
        client_id = response.clientID,
        client_secret = response.clientSecret,
        redirect_uri = response.redirectURI,
        scope = scopes.joinToString(" ") { it.value },
        code = code,
        grant_type = "authorization_code",
    )

    public suspend fun verify(accessToken: String): dev.dimension.flare.data.network.mastodon.api.model.Account =
        resources.verifyCredentials(accessToken = "Bearer $accessToken")
}
