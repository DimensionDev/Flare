package dev.dimension.flare.data.network.xqt.api

import de.jensklingenberg.ktorfit.Response
import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.Header
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Path
import de.jensklingenberg.ktorfit.http.Query
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

    /**
     * POST graphql/{pathQueryId}/AddParticipantsMutation
     *
     * post DM Un Block User
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "oBwyQ0_xVbAQ8FAyG0pCRA")
     * @param queryId  (default to "oBwyQ0_xVbAQ8FAyG0pCRA")
     * @param addedParticipants
     * @param conversationId  (default to "426425493-1714936029558476800")
     * @return [kotlin.Any]
     */
    @POST("graphql/{pathQueryId}/AddParticipantsMutation")
    public suspend fun postDMAddParticipantsMutation(
        @Path("pathQueryId") pathQueryId: kotlin.String = "oBwyQ0_xVbAQ8FAyG0pCRA",
        @Query("queryId") queryId: kotlin.String = "oBwyQ0_xVbAQ8FAyG0pCRA",
        @Query("addedParticipants") addedParticipants: kotlin.collections.List<kotlin.String>,
        @Query("conversationId") conversationId: kotlin.String = "426425493-1714936029558476800",
    ): Response<kotlin.Any>

    /**
     * POST graphql/{pathQueryId}/dmBlockUser
     *
     * post DM Block User
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "IYw9u1KEhrS-t-BXsau4Uw")
     * @param queryId  (default to "IYw9u1KEhrS-t-BXsau4Uw")
     * @param target_user_id  (default to "1")
     * @return [kotlin.Any]
     */
    @POST("graphql/{pathQueryId}/dmBlockUser")
    public suspend fun postDMBlockUser(
        @Path("pathQueryId") pathQueryId: kotlin.String = "IYw9u1KEhrS-t-BXsau4Uw",
        @Query("queryId") queryId: kotlin.String = "IYw9u1KEhrS-t-BXsau4Uw",
        @Query("target_user_id") target_user_id: kotlin.String = "1",
    ): Response<kotlin.Any>

    /**
     * POST graphql/{pathQueryId}/DMMessageDeleteMutation
     *
     * post DM Message Delete Mutation
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "BJ6DtxA2llfjnRoRjaiIiw")
     * @param queryId  (default to "BJ6DtxA2llfjnRoRjaiIiw")
     * @param messageId  (default to "1844651953697296738")
     * @param requestId  (default to "c71a2690-87a8-11ef-9564-b7a4d8a5f00c")
     * @return [kotlin.Any]
     */
    @POST("graphql/{pathQueryId}/DMMessageDeleteMutation")
    public suspend fun postDMMessageDeleteMutation(
        @Path("pathQueryId") pathQueryId: kotlin.String = "BJ6DtxA2llfjnRoRjaiIiw",
        @Query("queryId") queryId: kotlin.String = "BJ6DtxA2llfjnRoRjaiIiw",
        @Query("messageId") messageId: kotlin.String = "1844651953697296738",
        @Query("requestId") requestId: kotlin.String = "c71a2690-87a8-11ef-9564-b7a4d8a5f00c",
    ): Response<kotlin.Any>

    /**
     * POST graphql/{pathQueryId}/DMPinnedInboxAppend_Mutation
     *
     * post DM Pinned Inbox
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "o0aymgGiJY-53Y52YSUGVA")
     * @param queryId  (default to "o0aymgGiJY-53Y52YSUGVA")
     * @param conversation_id  (default to "426425493-1714936029558476800")
     * @param label  (default to "Pinned")
     * @return [kotlin.Any]
     */
    @POST("graphql/{pathQueryId}/DMPinnedInboxAppend_Mutation")
    public suspend fun postDMPinnedInboxAppendMutation(
        @Path("pathQueryId") pathQueryId: kotlin.String = "o0aymgGiJY-53Y52YSUGVA",
        @Query("queryId") queryId: kotlin.String = "o0aymgGiJY-53Y52YSUGVA",
        @Query("conversation_id") conversation_id: kotlin.String = "426425493-1714936029558476800",
        @Query("label") label: kotlin.String = "Pinned",
    ): Response<kotlin.Any>

    /**
     * POST graphql/{pathQueryId}/DMPinnedInboxDelete_Mutation
     *
     * post Delete DM Pinned Inbox
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "_TQxP2Rb0expwVP9ktGrTQ")
     * @param queryId  (default to "_TQxP2Rb0expwVP9ktGrTQ")
     * @param conversation_id  (default to "426425493-1714936029558476800")
     * @param label_type  (default to "Pinned")
     * @return [kotlin.Any]
     */
    @POST("graphql/{pathQueryId}/DMPinnedInboxDelete_Mutation")
    public suspend fun postDMPinnedInboxDeleteMutation(
        @Path("pathQueryId") pathQueryId: kotlin.String = "_TQxP2Rb0expwVP9ktGrTQ",
        @Query("queryId") queryId: kotlin.String = "_TQxP2Rb0expwVP9ktGrTQ",
        @Query("conversation_id") conversation_id: kotlin.String = "426425493-1714936029558476800",
        @Query("label_type") label_type: kotlin.String = "Pinned",
    ): Response<kotlin.Any>

    /**
     * POST graphql/{pathQueryId}/dmUnblockUser
     *
     * post DM Un Block User
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "Krbs6Nak_o7liWQwfV1jOQ")
     * @param queryId  (default to "Krbs6Nak_o7liWQwfV1jOQ")
     * @param target_user_id  (default to "1")
     * @return [kotlin.Any]
     */
    @POST("graphql/{pathQueryId}/dmUnblockUser")
    public suspend fun postDMUnBlockUser(
        @Path("pathQueryId") pathQueryId: kotlin.String = "Krbs6Nak_o7liWQwfV1jOQ",
        @Query("queryId") queryId: kotlin.String = "Krbs6Nak_o7liWQwfV1jOQ",
        @Query("target_user_id") target_user_id: kotlin.String = "1",
    ): Response<kotlin.Any>

    /**
     * POST graphql/{pathQueryId}/useDMReactionMutationAddMutation
     *
     * post Use DM Reaction Mutation Add Mutation
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "VyDyV9pC2oZEj6g52hgnhA")
     * @param queryId  (default to "VyDyV9pC2oZEj6g52hgnhA")
     * @param conversationId  (default to "426425493-1714936029558476800")
     * @param emojiReactions
     * @param messageId  (default to "1844628043379425446")
     * @param reactionTypes
     * @return [kotlin.Any]
     */
    @POST("graphql/{pathQueryId}/useDMReactionMutationAddMutation")
    public suspend fun postDMUseReactionMutationAddMutation(
        @Path("pathQueryId") pathQueryId: kotlin.String = "VyDyV9pC2oZEj6g52hgnhA",
        @Query("queryId") queryId: kotlin.String = "VyDyV9pC2oZEj6g52hgnhA",
        @Query("conversationId") conversationId: kotlin.String = "426425493-1714936029558476800",
        @Query("emojiReactions") emojiReactions: kotlin.collections.List<kotlin.String>,
        @Query("messageId") messageId: kotlin.String = "1844628043379425446",
        @Query("reactionTypes") reactionTypes: kotlin.collections.List<kotlin.String>,
    ): Response<kotlin.Any>

    /**
     * POST graphql/{pathQueryId}/useDMReactionMutationRemoveMutation
     *
     * post Use DM Reaction Mutation remove Mutation
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "bV_Nim3RYHsaJwMkTXJ6ew")
     * @param queryId  (default to "bV_Nim3RYHsaJwMkTXJ6ew")
     * @param conversationId  (default to "426425493-1714936029558476800")
     * @param emojiReactions
     * @param messageId  (default to "1844628043379425446")
     * @param reactionTypes
     * @return [kotlin.Any]
     */
    @POST("graphql/{pathQueryId}/useDMReactionMutationRemoveMutation")
    public suspend fun postDMUseReactionMutationRemoveMutation(
        @Path("pathQueryId") pathQueryId: kotlin.String = "bV_Nim3RYHsaJwMkTXJ6ew",
        @Query("queryId") queryId: kotlin.String = "bV_Nim3RYHsaJwMkTXJ6ew",
        @Query("conversationId") conversationId: kotlin.String = "426425493-1714936029558476800",
        @Query("emojiReactions") emojiReactions: kotlin.collections.List<kotlin.String>,
        @Query("messageId") messageId: kotlin.String = "1844628043379425446",
        @Query("reactionTypes") reactionTypes: kotlin.collections.List<kotlin.String>,
    ): Response<kotlin.Any>
}
