package dev.dimension.flare.data.network.xqt.api

import de.jensklingenberg.ktorfit.Response
import de.jensklingenberg.ktorfit.http.*
import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.Header
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Path
import de.jensklingenberg.ktorfit.http.Query
import dev.dimension.flare.data.network.xqt.model.CreateBookmark200Response
import dev.dimension.flare.data.network.xqt.model.CreateBookmarkRequest
import dev.dimension.flare.data.network.xqt.model.CreateBookmarkResponse
import dev.dimension.flare.data.network.xqt.model.CreateRetweetResponse
import dev.dimension.flare.data.network.xqt.model.CreateTweetResponse
import dev.dimension.flare.data.network.xqt.model.DeleteBookmark200Response
import dev.dimension.flare.data.network.xqt.model.DeleteBookmarkRequest
import dev.dimension.flare.data.network.xqt.model.DeleteBookmarkResponse
import dev.dimension.flare.data.network.xqt.model.DeleteRetweetResponse
import dev.dimension.flare.data.network.xqt.model.DeleteTweetResponse
import dev.dimension.flare.data.network.xqt.model.FavoriteTweetResponse
import dev.dimension.flare.data.network.xqt.model.PostCreateBookmarkRequest
import dev.dimension.flare.data.network.xqt.model.PostCreateRetweet200Response
import dev.dimension.flare.data.network.xqt.model.PostCreateRetweetRequest
import dev.dimension.flare.data.network.xqt.model.PostCreateTweet200Response
import dev.dimension.flare.data.network.xqt.model.PostCreateTweetRequest
import dev.dimension.flare.data.network.xqt.model.PostDeleteBookmarkRequest
import dev.dimension.flare.data.network.xqt.model.PostDeleteRetweet200Response
import dev.dimension.flare.data.network.xqt.model.PostDeleteRetweetRequest
import dev.dimension.flare.data.network.xqt.model.PostDeleteTweet200Response
import dev.dimension.flare.data.network.xqt.model.PostDeleteTweetRequest
import dev.dimension.flare.data.network.xqt.model.PostFavoriteTweet200Response
import dev.dimension.flare.data.network.xqt.model.PostFavoriteTweetRequest
import dev.dimension.flare.data.network.xqt.model.PostUnfavoriteTweet200Response
import dev.dimension.flare.data.network.xqt.model.PostUnfavoriteTweetRequest
import dev.dimension.flare.data.network.xqt.model.UnfavoriteTweetResponse
import kotlinx.serialization.json.JsonObject

internal interface PostApi {
    /**
     * POST graphql/{pathQueryId}/CreateBookmark
     *
     * create Bookmark
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "aoDbu3RHznuiSkQ9aNM67Q")
     * @param postCreateBookmarkRequest body
     * @return [CreateBookmarkResponse]
     */
    @POST("graphql/{pathQueryId}/CreateBookmark")
    suspend fun postCreateBookmark(@Header("Content-Type") contentType: kotlin.String = "application/json", @Path("pathQueryId") pathQueryId: kotlin.String = "aoDbu3RHznuiSkQ9aNM67Q", @Body postCreateBookmarkRequest: CreateBookmarkRequest): Response<CreateBookmark200Response>

    /**
     * POST graphql/{pathQueryId}/CreateRetweet
     *
     * create Retweet
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "mbRO74GrOvSfRcJnlMapnQ")
     * @param postCreateRetweetRequest body
     * @return [CreateRetweetResponse]
     */
    @POST("graphql/{pathQueryId}/CreateRetweet")
    suspend fun postCreateRetweet(@Header("Content-Type") contentType: kotlin.String = "application/json", @Path("pathQueryId") pathQueryId: kotlin.String = "mbRO74GrOvSfRcJnlMapnQ", @Body postCreateRetweetRequest: PostCreateRetweetRequest): Response<PostCreateRetweet200Response>

    /**
     * POST graphql/{pathQueryId}/CreateTweet
     *
     * create Tweet
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "5CdvsV_zjv4L64XFifAglw")
     * @param postCreateTweetRequest body
     * @return [CreateTweetResponse]
     */
    @POST("graphql/{pathQueryId}/CreateTweet")
    suspend fun postCreateTweet(@Header("Content-Type") contentType: kotlin.String = "application/json", @Path("pathQueryId") pathQueryId: kotlin.String = "5CdvsV_zjv4L64XFifAglw", @Body postCreateTweetRequest: PostCreateTweetRequest): Response<PostCreateTweet200Response>

    /**
     * POST graphql/{pathQueryId}/DeleteBookmark
     *
     * delete Bookmark
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "Wlmlj2-xzyS1GN3a6cj-mQ")
     * @param postDeleteBookmarkRequest body
     * @return [DeleteBookmarkResponse]
     */
    @POST("graphql/{pathQueryId}/DeleteBookmark")
    suspend fun postDeleteBookmark(@Header("Content-Type") contentType: kotlin.String = "application/json", @Path("pathQueryId") pathQueryId: kotlin.String = "Wlmlj2-xzyS1GN3a6cj-mQ", @Body postDeleteBookmarkRequest: DeleteBookmarkRequest): Response<DeleteBookmark200Response>

    /**
     * POST graphql/{pathQueryId}/DeleteRetweet
     *
     * delete Retweet
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "ZyZigVsNiFO6v1dEks1eWg")
     * @param postDeleteRetweetRequest body
     * @return [DeleteRetweetResponse]
     */
    @POST("graphql/{pathQueryId}/DeleteRetweet")
    suspend fun postDeleteRetweet(@Header("Content-Type") contentType: kotlin.String = "application/json", @Path("pathQueryId") pathQueryId: kotlin.String = "ZyZigVsNiFO6v1dEks1eWg", @Body postDeleteRetweetRequest: PostDeleteRetweetRequest): Response<PostDeleteRetweet200Response>

    /**
     * POST graphql/{pathQueryId}/DeleteTweet
     *
     * delete Retweet
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "VaenaVgh5q5ih7kvyVjgtg")
     * @param postDeleteTweetRequest body
     * @return [DeleteTweetResponse]
     */
    @POST("graphql/{pathQueryId}/DeleteTweet")
    suspend fun postDeleteTweet(@Header("Content-Type") contentType: kotlin.String = "application/json", @Path("pathQueryId") pathQueryId: kotlin.String = "VaenaVgh5q5ih7kvyVjgtg", @Body postDeleteTweetRequest: PostDeleteTweetRequest): Response<PostDeleteTweet200Response>

    /**
     * POST graphql/{pathQueryId}/FavoriteTweet
     *
     * favorite Tweet
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "lI07N6Otwv1PhnEgXILM7A")
     * @param postFavoriteTweetRequest body
     * @return [FavoriteTweetResponse]
     */
    @POST("graphql/{pathQueryId}/FavoriteTweet")
    suspend fun postFavoriteTweet(@Header("Content-Type") contentType: kotlin.String = "application/json", @Path("pathQueryId") pathQueryId: kotlin.String = "lI07N6Otwv1PhnEgXILM7A", @Body postFavoriteTweetRequest: PostFavoriteTweetRequest): Response<PostFavoriteTweet200Response>

    /**
     * POST graphql/{pathQueryId}/UnfavoriteTweet
     *
     * unfavorite Tweet
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "ZYKSe-w7KEslx3JhSIk5LA")
     * @param postUnfavoriteTweetRequest body
     * @return [UnfavoriteTweetResponse]
     */
    @POST("graphql/{pathQueryId}/UnfavoriteTweet")
    suspend fun postUnfavoriteTweet(@Header("Content-Type") contentType: kotlin.String = "application/json", @Path("pathQueryId") pathQueryId: kotlin.String = "ZYKSe-w7KEslx3JhSIk5LA", @Body postUnfavoriteTweetRequest: PostUnfavoriteTweetRequest): Response<PostUnfavoriteTweet200Response>

    @POST("graphql/{pathQueryId}/AddParticipantsMutation")
    public suspend fun postDMAddParticipantsMutation(
        @Path("pathQueryId") pathQueryId: kotlin.String = "oBwyQ0_xVbAQ8FAyG0pCRA",
        @Query("queryId") queryId: kotlin.String = "oBwyQ0_xVbAQ8FAyG0pCRA",
        @Query("addedParticipants") addedParticipants: kotlin.collections.List<kotlin.String>,
        @Query("conversationId") conversationId: kotlin.String = "426425493-1714936029558476800",
    ): Response<kotlin.Any>

    @POST("graphql/{pathQueryId}/DMMessageDeleteMutation")
    public suspend fun postDMMessageDeleteMutation(
        @Path("pathQueryId") pathQueryId: kotlin.String = "BJ6DtxA2llfjnRoRjaiIiw",
        @Query("queryId") queryId: kotlin.String = "BJ6DtxA2llfjnRoRjaiIiw",
        @Query("messageId") messageId: kotlin.String,
        @Query("requestId") requestId: kotlin.String,
    ): Response<JsonObject>

    @POST("graphql/{pathQueryId}/useDMReactionMutationRemoveMutation")
    public suspend fun postDMUseReactionMutationRemoveMutation(
        @Path("pathQueryId") pathQueryId: kotlin.String = "bV_Nim3RYHsaJwMkTXJ6ew",
        @Query("queryId") queryId: kotlin.String = "bV_Nim3RYHsaJwMkTXJ6ew",
        @Query("conversationId") conversationId: kotlin.String = "426425493-1714936029558476800",
        @Query("emojiReactions") emojiReactions: kotlin.collections.List<kotlin.String>,
        @Query("messageId") messageId: kotlin.String = "1844628043379425446",
        @Query("reactionTypes") reactionTypes: kotlin.collections.List<kotlin.String>,
    ): Response<kotlin.Any>

    @POST("graphql/{pathQueryId}/DMPinnedInboxAppend_Mutation")
    public suspend fun postDMPinnedInboxAppendMutation(
        @Path("pathQueryId") pathQueryId: kotlin.String = "o0aymgGiJY-53Y52YSUGVA",
        @Query("queryId") queryId: kotlin.String = "o0aymgGiJY-53Y52YSUGVA",
        @Query("conversation_id") conversation_id: kotlin.String = "426425493-1714936029558476800",
        @Query("label") label: kotlin.String = "Pinned",
    ): Response<kotlin.Any>

    @POST("graphql/{pathQueryId}/DMPinnedInboxDelete_Mutation")
    public suspend fun postDMPinnedInboxDeleteMutation(
        @Path("pathQueryId") pathQueryId: kotlin.String = "_TQxP2Rb0expwVP9ktGrTQ",
        @Query("queryId") queryId: kotlin.String = "_TQxP2Rb0expwVP9ktGrTQ",
        @Query("conversation_id") conversation_id: kotlin.String = "426425493-1714936029558476800",
        @Query("label_type") label_type: kotlin.String = "Pinned",
    ): Response<kotlin.Any>

    @POST("graphql/{pathQueryId}/dmBlockUser")
    public suspend fun postDMBlockUser(
        @Path("pathQueryId") pathQueryId: kotlin.String = "IYw9u1KEhrS-t-BXsau4Uw",
        @Query("queryId") queryId: kotlin.String = "IYw9u1KEhrS-t-BXsau4Uw",
        @Query("target_user_id") target_user_id: kotlin.String = "1",
    ): Response<kotlin.Any>

    @POST("graphql/{pathQueryId}/useDMReactionMutationAddMutation")
    public suspend fun postDMUseReactionMutationAddMutation(
        @Path("pathQueryId") pathQueryId: kotlin.String = "VyDyV9pC2oZEj6g52hgnhA",
        @Query("queryId") queryId: kotlin.String = "VyDyV9pC2oZEj6g52hgnhA",
        @Query("conversationId") conversationId: kotlin.String = "426425493-1714936029558476800",
        @Query("emojiReactions") emojiReactions: kotlin.collections.List<kotlin.String>,
        @Query("messageId") messageId: kotlin.String = "1844628043379425446",
        @Query("reactionTypes") reactionTypes: kotlin.collections.List<kotlin.String>,
    ): Response<kotlin.Any>

    @POST("graphql/{pathQueryId}/dmUnblockUser")
    public suspend fun postDMUnBlockUser(
        @Path("pathQueryId") pathQueryId: kotlin.String = "Krbs6Nak_o7liWQwfV1jOQ",
        @Query("queryId") queryId: kotlin.String = "Krbs6Nak_o7liWQwfV1jOQ",
        @Query("target_user_id") target_user_id: kotlin.String = "1",
    ): Response<kotlin.Any>
}
