package dev.dimension.flare.data.network.bluesky

import dev.dimension.flare.data.platform.BlueskyCredential
import sh.christian.ozone.oauth.OAuthApi
import sh.christian.ozone.oauth.OAuthClient

internal suspend fun OAuthApi.requestBlueskyOAuthCredential(
    issuer: String,
    oauthClient: OAuthClient,
    code: String,
    nonce: String,
    codeVerifier: String,
): BlueskyCredential.OAuthCredential {
    val token =
        requestToken(
            oauthClient = oauthClient,
            code = code,
            nonce = nonce,
            codeVerifier = codeVerifier,
        ).withResolvedPds(issuer)
    return BlueskyCredential.OAuthCredential(
        baseUrl = issuer,
        oAuthToken = token,
        pdsUrlVerified = true,
    )
}
