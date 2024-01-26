package dev.dimension.flare.data.network.xqt.api

import de.jensklingenberg.ktorfit.Response
import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.Field
import de.jensklingenberg.ktorfit.http.FormUrlEncoded
import de.jensklingenberg.ktorfit.http.POST
import dev.dimension.flare.data.network.xqt.model.PostMediaMetadataCreateRequest

internal interface V11PostApi {
    /**
     *
     * post create friendships
     * Responses:
     *  - 200: Successful operation
     *
     * @param includeBlockedBy  (default to 1)
     * @param includeBlocking  (default to 1)
     * @param includeCanDm  (default to 1)
     * @param includeCanMediaTag  (default to 1)
     * @param includeExtHasNftAvatar  (default to 1)
     * @param includeExtIsBlueVerified  (default to 1)
     * @param includeExtProfileImageShape  (default to 1)
     * @param includeExtVerifiedType  (default to 1)
     * @param includeFollowedBy  (default to 1)
     * @param includeMuteEdge  (default to 1)
     * @param includeProfileInterstitialType  (default to 1)
     * @param includeWantRetweets  (default to 1)
     * @param skipStatus  (default to 1)
     * @param userId  (default to "44196397")
     * @return [Unit]
     */
    @FormUrlEncoded
    @POST("1.1/friendships/create.json")
    suspend fun postCreateFriendships(
        @Field("include_blocked_by") includeBlockedBy: kotlin.Int = 1,
        @Field("include_blocking") includeBlocking: kotlin.Int = 1,
        @Field("include_can_dm") includeCanDm: kotlin.Int = 1,
        @Field("include_can_media_tag") includeCanMediaTag: kotlin.Int = 1,
        @Field("include_ext_has_nft_avatar") includeExtHasNftAvatar: kotlin.Int = 1,
        @Field("include_ext_is_blue_verified") includeExtIsBlueVerified: kotlin.Int = 1,
        @Field("include_ext_profile_image_shape") includeExtProfileImageShape: kotlin.Int = 1,
        @Field("include_ext_verified_type") includeExtVerifiedType: kotlin.Int = 1,
        @Field("include_followed_by") includeFollowedBy: kotlin.Int = 1,
        @Field("include_mute_edge") includeMuteEdge: kotlin.Int = 1,
        @Field("include_profile_interstitial_type") includeProfileInterstitialType: kotlin.Int = 1,
        @Field("include_want_retweets") includeWantRetweets: kotlin.Int = 1,
        @Field("skip_status") skipStatus: kotlin.Int = 1,
        @Field("user_id") userId: kotlin.String = "44196397",
    ): Response<Unit>

    /**
     *
     * post destroy friendships
     * Responses:
     *  - 200: Successful operation
     *
     * @param includeBlockedBy  (default to 1)
     * @param includeBlocking  (default to 1)
     * @param includeCanDm  (default to 1)
     * @param includeCanMediaTag  (default to 1)
     * @param includeExtHasNftAvatar  (default to 1)
     * @param includeExtIsBlueVerified  (default to 1)
     * @param includeExtProfileImageShape  (default to 1)
     * @param includeExtVerifiedType  (default to 1)
     * @param includeFollowedBy  (default to 1)
     * @param includeMuteEdge  (default to 1)
     * @param includeProfileInterstitialType  (default to 1)
     * @param includeWantRetweets  (default to 1)
     * @param skipStatus  (default to 1)
     * @param userId  (default to "44196397")
     * @return [Unit]
     */
    @FormUrlEncoded
    @POST("1.1/friendships/destroy.json")
    suspend fun postDestroyFriendships(
        @Field("include_blocked_by") includeBlockedBy: kotlin.Int = 1,
        @Field("include_blocking") includeBlocking: kotlin.Int = 1,
        @Field("include_can_dm") includeCanDm: kotlin.Int = 1,
        @Field("include_can_media_tag") includeCanMediaTag: kotlin.Int = 1,
        @Field("include_ext_has_nft_avatar") includeExtHasNftAvatar: kotlin.Int = 1,
        @Field("include_ext_is_blue_verified") includeExtIsBlueVerified: kotlin.Int = 1,
        @Field("include_ext_profile_image_shape") includeExtProfileImageShape: kotlin.Int = 1,
        @Field("include_ext_verified_type") includeExtVerifiedType: kotlin.Int = 1,
        @Field("include_followed_by") includeFollowedBy: kotlin.Int = 1,
        @Field("include_mute_edge") includeMuteEdge: kotlin.Int = 1,
        @Field("include_profile_interstitial_type") includeProfileInterstitialType: kotlin.Int = 1,
        @Field("include_want_retweets") includeWantRetweets: kotlin.Int = 1,
        @Field("skip_status") skipStatus: kotlin.Int = 1,
        @Field("user_id") userId: kotlin.String = "44196397",
    ): Response<Unit>

    @FormUrlEncoded
    @POST("1.1/mutes/users/create.json")
    suspend fun postMutesUsersCreate(
        @Field("user_id") userId: kotlin.String = "44196397",
    ): Response<Unit>

    @FormUrlEncoded
    @POST("1.1/mutes/users/destroy.json")
    suspend fun postMutesUsersDestroy(
        @Field("user_id") userId: kotlin.String = "44196397",
    ): Response<Unit>

    @FormUrlEncoded
    @POST("1.1/blocks/create.json")
    suspend fun postBlocksCreate(
        @Field("user_id") userId: kotlin.String = "44196397",
    ): Response<Unit>

    @FormUrlEncoded
    @POST("1.1/blocks/destroy.json")
    suspend fun postBlocksDestroy(
        @Field("user_id") userId: kotlin.String = "44196397",
    ): Response<Unit>

    @POST("1.1/media/metadata/create.json")
    suspend fun postMediaMetadataCreate(
        @Body body: PostMediaMetadataCreateRequest,
    ): Response<Unit>
}
