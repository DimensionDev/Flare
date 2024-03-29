package dev.dimension.flare.data.network.xqt.api

import de.jensklingenberg.ktorfit.Response
import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.Header
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Path
import dev.dimension.flare.data.network.xqt.model.CreateBookmark200Response
import dev.dimension.flare.data.network.xqt.model.CreateBookmarkRequest
import dev.dimension.flare.data.network.xqt.model.DeleteBookmark200Response
import dev.dimension.flare.data.network.xqt.model.DeleteBookmarkRequest
import dev.dimension.flare.data.network.xqt.model.PostCreateRetweet200Response
import dev.dimension.flare.data.network.xqt.model.PostCreateRetweetRequest
import dev.dimension.flare.data.network.xqt.model.PostCreateTweet200Response
import dev.dimension.flare.data.network.xqt.model.PostCreateTweetRequest
import dev.dimension.flare.data.network.xqt.model.PostDeleteRetweet200Response
import dev.dimension.flare.data.network.xqt.model.PostDeleteRetweetRequest
import dev.dimension.flare.data.network.xqt.model.PostDeleteTweet200Response
import dev.dimension.flare.data.network.xqt.model.PostDeleteTweetRequest
import dev.dimension.flare.data.network.xqt.model.PostFavoriteTweet200Response
import dev.dimension.flare.data.network.xqt.model.PostFavoriteTweetRequest
import dev.dimension.flare.data.network.xqt.model.PostUnfavoriteTweet200Response
import dev.dimension.flare.data.network.xqt.model.PostUnfavoriteTweetRequest

internal interface PostApi {
    /**
     *
     * create Retweet
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "ojPdsZsimiJrUGLR1sjUtA")
     * @param postCreateRetweetRequest body
     * @return [PostCreateRetweet200Response]
     */
    @POST("graphql/{pathQueryId}/CreateRetweet")
    suspend fun postCreateRetweet(
        @Header("Content-Type") contentType: kotlin.String = "application/json",
        @Path("pathQueryId") pathQueryId: kotlin.String = "ojPdsZsimiJrUGLR1sjUtA",
        @Body postCreateRetweetRequest: PostCreateRetweetRequest,
    ): Response<PostCreateRetweet200Response>

    /**
     *
     * create Tweet
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "PIZtQLRIYtSa9AtW_fI2Mw")
     * @param postCreateTweetRequest body
     * @return [PostCreateTweet200Response]
     */
    @POST("graphql/{pathQueryId}/CreateTweet")
    suspend fun postCreateTweet(
        @Header("Content-Type") contentType: kotlin.String = "application/json",
        @Path("pathQueryId") pathQueryId: kotlin.String = "PIZtQLRIYtSa9AtW_fI2Mw",
        @Body postCreateTweetRequest: PostCreateTweetRequest,
    ): Response<PostCreateTweet200Response>

    /**
     *
     * delete Retweet
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "iQtK4dl5hBmXewYZuEOKVw")
     * @param postDeleteRetweetRequest body
     * @return [PostDeleteRetweet200Response]
     */
    @POST("graphql/{pathQueryId}/DeleteRetweet")
    suspend fun postDeleteRetweet(
        @Header("Content-Type") contentType: kotlin.String = "application/json",
        @Path("pathQueryId") pathQueryId: kotlin.String = "iQtK4dl5hBmXewYZuEOKVw",
        @Body postDeleteRetweetRequest: PostDeleteRetweetRequest,
    ): Response<PostDeleteRetweet200Response>

    /**
     *
     * delete Retweet
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "VaenaVgh5q5ih7kvyVjgtg")
     * @param postDeleteTweetRequest body
     * @return [PostDeleteTweet200Response]
     */
    @POST("graphql/{pathQueryId}/DeleteTweet")
    suspend fun postDeleteTweet(
        @Header("Content-Type") contentType: kotlin.String = "application/json",
        @Path("pathQueryId") pathQueryId: kotlin.String = "VaenaVgh5q5ih7kvyVjgtg",
        @Body postDeleteTweetRequest: PostDeleteTweetRequest,
    ): Response<PostDeleteTweet200Response>

    /**
     *
     * favorite Tweet
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "lI07N6Otwv1PhnEgXILM7A")
     * @param postFavoriteTweetRequest body
     * @return [PostFavoriteTweet200Response]
     */
    @POST("graphql/{pathQueryId}/FavoriteTweet")
    suspend fun postFavoriteTweet(
        @Header("Content-Type") contentType: kotlin.String = "application/json",
        @Path("pathQueryId") pathQueryId: kotlin.String = "lI07N6Otwv1PhnEgXILM7A",
        @Body postFavoriteTweetRequest: PostFavoriteTweetRequest,
    ): Response<PostFavoriteTweet200Response>

    /**
     *
     * unfavorite Tweet
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "ZYKSe-w7KEslx3JhSIk5LA")
     * @param postUnfavoriteTweetRequest body
     * @return [PostUnfavoriteTweet200Response]
     */
    @POST("graphql/{pathQueryId}/UnfavoriteTweet")
    suspend fun postUnfavoriteTweet(
        @Header("Content-Type") contentType: kotlin.String = "application/json",
        @Path("pathQueryId") pathQueryId: kotlin.String = "ZYKSe-w7KEslx3JhSIk5LA",
        @Body postUnfavoriteTweetRequest: PostUnfavoriteTweetRequest,
    ): Response<PostUnfavoriteTweet200Response>

    @POST("graphql/{pathQueryId}/CreateBookmark")
    suspend fun postCreateBookmark(
        @Header("Content-Type") contentType: kotlin.String = "application/json",
        @Path("pathQueryId") pathQueryId: kotlin.String = "aoDbu3RHznuiSkQ9aNM67Q",
        @Body postCreateBookmarkRequest: CreateBookmarkRequest,
    ): Response<CreateBookmark200Response>

    @POST("graphql/{pathQueryId}/DeleteBookmark")
    suspend fun postDeleteBookmark(
        @Header("Content-Type") contentType: kotlin.String = "application/json",
        @Path("pathQueryId") pathQueryId: kotlin.String = "Wlmlj2-xzyS1GN3a6cj-mQ",
        @Body postDeleteBookmarkRequest: DeleteBookmarkRequest,
    ): Response<DeleteBookmark200Response>
}
