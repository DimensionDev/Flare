package dev.dimension.flare.data.network.tumblr

import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.network.tumblr.model.TumblrOAuth2TokenResponse
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.http.Parameters

internal class TumblrOAuth2Service {
    suspend fun requestToken(
        code: String,
        clientId: String,
        clientSecret: String,
        redirectUri: String? = null,
    ): TumblrOAuth2TokenResponse =
        ktorClient()
            .submitForm(
                url = "https://api.tumblr.com/v2/oauth2/token",
                formParameters =
                    Parameters.build {
                        append("grant_type", "authorization_code")
                        append("code", code)
                        append("client_id", clientId)
                        append("client_secret", clientSecret)
                        redirectUri?.let { append("redirect_uri", it) }
                    },
            ).body()

    suspend fun refreshToken(
        refreshToken: String,
        clientId: String,
        clientSecret: String,
    ): TumblrOAuth2TokenResponse =
        ktorClient()
            .submitForm(
                url = "https://api.tumblr.com/v2/oauth2/token",
                formParameters =
                    Parameters.build {
                        append("grant_type", "refresh_token")
                        append("refresh_token", refreshToken)
                        append("client_id", clientId)
                        append("client_secret", clientSecret)
                    },
            ).body()
}
