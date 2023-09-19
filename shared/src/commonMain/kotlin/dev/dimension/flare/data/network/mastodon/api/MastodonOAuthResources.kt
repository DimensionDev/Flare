package dev.dimension.flare.data.network.mastodon.api

import de.jensklingenberg.ktorfit.http.Field
import de.jensklingenberg.ktorfit.http.FormUrlEncoded
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Header
import de.jensklingenberg.ktorfit.http.POST
import dev.dimension.flare.data.network.mastodon.api.model.Account
import dev.dimension.flare.data.network.mastodon.api.model.CreateApplicationResponse
import dev.dimension.flare.data.network.mastodon.api.model.RequestTokenResponse

interface MastodonOAuthResources {
    @POST("api/v1/apps")
    @FormUrlEncoded
    suspend fun createApplication(
        @Field("client_name") client_name: String,
        @Field("redirect_uris") redirect_uris: String,
        @Field("scopes") scopes: String,
        @Field("website") website: String?,
    ): CreateApplicationResponse

    @GET("api/v1/accounts/verify_credentials")
    suspend fun verifyCredentials(
        @Header("Authorization") accessToken: String,
    ): Account

    @POST("oauth/token")
    @FormUrlEncoded
    suspend fun requestToken(
        @Field("client_id") client_id: String,
        @Field("client_secret") client_secret: String,
        @Field("redirect_uri") redirect_uri: String,
        @Field("scope") scope: String,
        @Field("code") code: String,
        @Field("grant_type") grant_type: String,
    ): RequestTokenResponse
}
