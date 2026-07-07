package dev.dimension.flare.data.network.tumblr

import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.Field
import de.jensklingenberg.ktorfit.http.FormUrlEncoded
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Header
import de.jensklingenberg.ktorfit.http.Multipart
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Path
import de.jensklingenberg.ktorfit.http.Query
import io.ktor.client.request.forms.MultiPartFormDataContent

internal interface TumblrAuthResources {
    @POST("oauth2/token")
    @FormUrlEncoded
    suspend fun requestToken(
        @Field("grant_type") grantType: String,
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("redirect_uri") redirectUri: String,
        @Field("code") code: String,
    ): TumblrTokenResponse

    @POST("oauth2/token")
    @FormUrlEncoded
    suspend fun refreshToken(
        @Field("grant_type") grantType: String,
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("refresh_token") refreshToken: String,
    ): TumblrTokenResponse
}

internal interface TumblrResources {
    @GET("user/info")
    suspend fun userInfo(
        @Header("Authorization") authorization: String,
    ): TumblrEnvelope<TumblrUserInfoResponse>

    @GET("user/dashboard")
    suspend fun dashboard(
        @Header("Authorization") authorization: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int? = null,
        @Query("type") type: String? = null,
        @Query("since_id") sinceId: String? = null,
        @Query("npf") npf: Boolean = true,
        @Query("reblog_info") reblogInfo: Boolean = true,
        @Query("notes_info") notesInfo: Boolean = true,
    ): TumblrEnvelope<TumblrPostsPage>

    @GET("blog/{blogIdentifier}/posts")
    suspend fun blogPosts(
        @Header("Authorization") authorization: String,
        @Path("blogIdentifier") blogIdentifier: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int? = null,
        @Query("id") postId: String? = null,
        @Query("tag") tag: String? = null,
        @Query("before") beforeTimestampSeconds: Long? = null,
        @Query("npf") npf: Boolean = true,
        @Query("reblog_info") reblogInfo: Boolean = true,
        @Query("notes_info") notesInfo: Boolean = true,
        @Query("filter") filter: String? = null,
    ): TumblrEnvelope<TumblrPostsPage>

    @GET("tagged")
    suspend fun tagged(
        @Header("Authorization") authorization: String,
        @Query("tag") tag: String,
        @Query("limit") limit: Int,
        @Query("before") beforeTimestampSeconds: Long? = null,
        @Query("npf") npf: Boolean = true,
    ): TumblrEnvelope<List<TumblrPost>>

    @GET("blog/{blogIdentifier}/info")
    suspend fun blogInfo(
        @Header("Authorization") authorization: String,
        @Path("blogIdentifier") blogIdentifier: String,
    ): TumblrEnvelope<TumblrBlogInfoResponse>

    @GET("user/following")
    suspend fun following(
        @Header("Authorization") authorization: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int? = null,
    ): TumblrEnvelope<TumblrBlogPage>

    @GET("blog/{blogIdentifier}/followers")
    suspend fun followers(
        @Header("Authorization") authorization: String,
        @Path("blogIdentifier") blogIdentifier: String,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int? = null,
    ): TumblrEnvelope<TumblrFollowerPage>

    @POST("user/like")
    @FormUrlEncoded
    suspend fun like(
        @Header("Authorization") authorization: String,
        @Field("id") postId: String,
        @Field("reblog_key") reblogKey: String,
    ): TumblrEnvelope<TumblrActionResponse>

    @POST("user/unlike")
    @FormUrlEncoded
    suspend fun unlike(
        @Header("Authorization") authorization: String,
        @Field("id") postId: String,
        @Field("reblog_key") reblogKey: String,
    ): TumblrEnvelope<TumblrActionResponse>

    @POST("blog/{blogIdentifier}/post/reblog")
    @FormUrlEncoded
    suspend fun reblog(
        @Header("Authorization") authorization: String,
        @Path("blogIdentifier") blogIdentifier: String,
        @Field("id") postId: String,
        @Field("reblog_key") reblogKey: String,
        @Field("comment") comment: String? = null,
        @Field("state") state: String? = null,
    ): TumblrEnvelope<TumblrPostMutationResponse>

    @POST("user/follow")
    @FormUrlEncoded
    suspend fun follow(
        @Header("Authorization") authorization: String,
        @Field("url") blogUrl: String,
    ): TumblrEnvelope<TumblrFollowResponse>

    @POST("user/unfollow")
    @FormUrlEncoded
    suspend fun unfollow(
        @Header("Authorization") authorization: String,
        @Field("url") blogUrl: String,
    ): TumblrEnvelope<TumblrActionResponse>

    @POST("blog/{blogIdentifier}/post/delete")
    @FormUrlEncoded
    suspend fun deletePost(
        @Header("Authorization") authorization: String,
        @Path("blogIdentifier") blogIdentifier: String,
        @Field("id") postId: String,
    ): TumblrEnvelope<TumblrActionResponse>

    @POST("blog/{blogIdentifier}/posts")
    suspend fun createPost(
        @Header("Authorization") authorization: String,
        @Path("blogIdentifier") blogIdentifier: String,
        @Body request: TumblrCreatePostRequest,
        @Header("Content-Type") contentType: String = "application/json",
    ): TumblrEnvelope<TumblrPostMutationResponse>

    @Multipart
    @POST("blog/{blogIdentifier}/posts")
    suspend fun createPost(
        @Header("Authorization") authorization: String,
        @Path("blogIdentifier") blogIdentifier: String,
        @Body body: MultiPartFormDataContent,
    ): TumblrEnvelope<TumblrPostMutationResponse>
}
