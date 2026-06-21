package dev.dimension.flare.data.network.fanbox.api

import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Header
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Query
import de.jensklingenberg.ktorfit.http.Url
import dev.dimension.flare.data.network.fanbox.FanboxCommentListResponse
import dev.dimension.flare.data.network.fanbox.FanboxCreatorDetailResponse
import dev.dimension.flare.data.network.fanbox.FanboxCreatorListResponse
import dev.dimension.flare.data.network.fanbox.FanboxCreatorPostListResponse
import dev.dimension.flare.data.network.fanbox.FanboxCreatorPostPagesResponse
import dev.dimension.flare.data.network.fanbox.FanboxCreatorSearchResponse
import dev.dimension.flare.data.network.fanbox.FanboxFollowRequest
import dev.dimension.flare.data.network.fanbox.FanboxPostDetailResponse
import dev.dimension.flare.data.network.fanbox.FanboxPostIdRequest
import dev.dimension.flare.data.network.fanbox.FanboxPostListResponse
import dev.dimension.flare.data.network.fanbox.FanboxPostSearchResponse

internal interface FanboxWebResources {
    @GET
    suspend fun metadata(
        @Url url: String,
    ): String
}

internal interface FanboxResources {
    @GET("post.listHome")
    suspend fun listHomePosts(
        @Query("limit") limit: Int,
        @Query("firstPublishedDatetime") firstPublishedDatetime: String? = null,
        @Query("maxPublishedDatetime") maxPublishedDatetime: String? = null,
        @Query("firstId") firstId: String? = null,
        @Query("maxId") maxId: String? = null,
    ): FanboxPostListResponse

    @GET("post.listSupporting")
    suspend fun listSupportingPosts(
        @Query("limit") limit: Int,
        @Query("firstPublishedDatetime") firstPublishedDatetime: String? = null,
        @Query("maxPublishedDatetime") maxPublishedDatetime: String? = null,
        @Query("firstId") firstId: String? = null,
        @Query("maxId") maxId: String? = null,
    ): FanboxPostListResponse

    @GET("post.paginateCreator")
    suspend fun paginateCreatorPosts(
        @Query("creatorId") creatorId: String,
    ): FanboxCreatorPostPagesResponse

    @GET("post.listCreator")
    suspend fun listCreatorPosts(
        @Query("creatorId") creatorId: String,
        @Query("limit") limit: Int,
        @Query("firstPublishedDatetime") firstPublishedDatetime: String? = null,
        @Query("maxPublishedDatetime") maxPublishedDatetime: String? = null,
        @Query("firstId") firstId: String? = null,
        @Query("maxId") maxId: String? = null,
    ): FanboxCreatorPostListResponse

    @GET("post.listTagged")
    suspend fun listTaggedPosts(
        @Query("tag") tag: String,
        @Query("page") page: Int,
    ): FanboxPostSearchResponse

    @GET("post.info")
    suspend fun postInfo(
        @Query("postId") postId: String,
    ): FanboxPostDetailResponse

    @GET("post.getComments")
    suspend fun getComments(
        @Query("postId") postId: String,
        @Query("offset") offset: Int,
        @Query("limit") limit: Int,
    ): FanboxCommentListResponse

    @GET("creator.get")
    suspend fun getCreator(
        @Query("creatorId") creatorId: String,
    ): FanboxCreatorDetailResponse

    @GET("creator.listFollowing")
    suspend fun listFollowingCreators(): FanboxCreatorListResponse

    @GET("creator.listRecommended")
    suspend fun listRecommendedCreators(
        @Query("limit") limit: Int,
    ): FanboxCreatorListResponse

    @GET("creator.search")
    suspend fun searchCreatorsRaw(
        @Query("q") query: String,
        @Query("page") page: Int,
    ): FanboxCreatorSearchResponse

    @POST("post.likePost")
    suspend fun likePost(
        @Header("x-csrf-token") csrfToken: String,
        @Body request: FanboxPostIdRequest,
    ): Unit

    @POST("follow.create")
    suspend fun followCreator(
        @Header("x-csrf-token") csrfToken: String,
        @Body request: FanboxFollowRequest,
    ): Unit

    @POST("follow.delete")
    suspend fun unfollowCreator(
        @Header("x-csrf-token") csrfToken: String,
        @Body request: FanboxFollowRequest,
    ): Unit
}
