package dev.dimension.flare.data.network.pixiv.api

import de.jensklingenberg.ktorfit.http.Field
import de.jensklingenberg.ktorfit.http.FormUrlEncoded
import de.jensklingenberg.ktorfit.http.POST
import dev.dimension.flare.data.network.pixiv.model.PixivTokenResponse

internal interface PixivAuthResources {
    @POST("auth/token")
    @FormUrlEncoded
    suspend fun login(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("grant_type") grantType: String = "authorization_code",
        @Field("code") code: String,
        @Field("code_verifier") codeVerifier: String,
        @Field("redirect_uri") redirectUri: String,
        @Field("include_policy") includePolicy: Boolean = true,
    ): PixivTokenResponse

    @POST("auth/token")
    @FormUrlEncoded
    suspend fun refreshToken(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("grant_type") grantType: String = "refresh_token",
        @Field("refresh_token") refreshToken: String,
        @Field("include_policy") includePolicy: Boolean = true,
    ): PixivTokenResponse
}
