package dev.dimension.flare.data.network.xqt.api

import de.jensklingenberg.ktorfit.Response
import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.Field
import de.jensklingenberg.ktorfit.http.FormUrlEncoded
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Path
import dev.dimension.flare.data.network.xqt.model.PostMediaMetadataCreateRequest
import dev.dimension.flare.data.network.xqt.model.UpdateAccountSettingsResponse

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

//
//    /**
//     * POST 1.1/dm/conversation/{conversation_id}/accept.json
//     *
//     * post DM Conversation Accept
//     * Responses:
//     *  - 204: Successful response
//     *
//     * @param conversationId dm conversation_id
//     * @return [Unit]
//     */
//    @POST("1.1/dm/conversation/{conversation_id}/accept.json")
//    suspend fun postDMConversationAccept(@Path("conversation_id") conversationId: kotlin.String): Response<Unit>
//
//    /**
//     * POST 1.1/dm/conversation/{conversation_id}/delete.json
//     *
//     * post dm Conversation delete
//     * Responses:
//     *  - 204: Successful response
//     *
//     * @param conversationId dm conversation_id
//     * @return [Unit]
//     */
//    @POST("1.1/dm/conversation/{conversation_id}/delete.json")
//    suspend fun postDMConversationDelete(@Path("conversation_id") conversationId: kotlin.String): Response<Unit>
//
//    /**
//     * POST 1.1/dm/conversation/{conversation_id}/disable_notifications.json
//     *
//     * duration:[1:1h,2:8h,3:1w,0:forever]
//     * Responses:
//     *  - 204: Successful response
//     *
//     * @param conversationId dm conversation_id
//     * @return [Unit]
//     */
//    @POST("1.1/dm/conversation/{conversation_id}/disable_notifications.json")
//    suspend fun postDMConversationDisableNotifications(@Path("conversation_id") conversationId: kotlin.String): Response<Unit>
//
//    /**
//     * POST 1.1/dm/conversation/{conversation_id}/enable_notifications.json
//     *
//     * post dm Conversation enable notifications
//     * Responses:
//     *  - 204: Successful response
//     *
//     * @param conversationId dm conversation_id
//     * @return [Unit]
//     */
//    @POST("1.1/dm/conversation/{conversation_id}/enable_notifications.json")
//    suspend fun postDMConversationEnableNotifications(@Path("conversation_id") conversationId: kotlin.String): Response<Unit>
//
//    /**
//     * POST 1.1/dm/conversation/{conversation_id}/mark_read.json
//     *
//     * post dm Conversation mark read
//     * Responses:
//     *  - 204: Successful response
//     *
//     * @param conversationId dm conversation_id
//     * @return [Unit]
//     */
//    @POST("1.1/dm/conversation/{conversation_id}/mark_read.json")
//    suspend fun postDMConversationMarkRead(@Path("conversation_id") conversationId: kotlin.String): Response<Unit>
//
//    /**
//     * POST 1.1/dm/conversation/{conversation_id}/update_avatar.json
//     *
//     * post DM Conversation update avatar
//     * Responses:
//     *  - 204: Successful response
//     *
//     * @param conversationId dm conversation_id
//     * @return [Unit]
//     */
//    @POST("1.1/dm/conversation/{conversation_id}/update_avatar.json")
//    suspend fun postDMConversationUpdateAvatar(@Path("conversation_id") conversationId: kotlin.String): Response<Unit>
//
//    /**
//     * POST 1.1/dm/conversation/{conversation_id}/update_mention_notifications_setting.json
//     *
//     * post DM Conversation update Mention Notifications Setting
//     * Responses:
//     *  - 204: Successful response
//     *
//     * @param conversationId dm conversation_id
//     * @return [Unit]
//     */
//    @POST("1.1/dm/conversation/{conversation_id}/update_mention_notifications_setting.json")
//    suspend fun postDMConversationUpdateMentionNotificationsSetting(@Path("conversation_id") conversationId: kotlin.String): Response<Unit>
//
//    /**
//     * POST 1.1/dm/conversation/{conversation_id}/update_name.json
//     *
//     * post DM Conversation update name
//     * Responses:
//     *  - 204: Successful response
//     *
//     * @param conversationId dm conversation_id
//     * @return [Unit]
//     */
//    @POST("1.1/dm/conversation/{conversation_id}/update_name.json")
//    suspend fun postDMConversationUpdateName(@Path("conversation_id") conversationId: kotlin.String): Response<Unit>
//
//    /**
//     * POST 1.1/dm/update_last_seen_event_id.json
//     *
//     * post DM Update Last Seen Event Id
//     * Responses:
//     *  - 204: Successful operation
//     *
//     * @return [Unit]
//     */
//    @POST("1.1/dm/update_last_seen_event_id.json")
//    suspend fun postDMUpdateLastSeenEventId(): Response<Unit>
//
//    /**
//     * POST 1.1/account/settings.json
//     *
//     * post Update Account Settings
//     * Responses:
//     *  - 200: Successful response
//     *
//     * @param includeMentionFilter  (default to true)
//     * @param includeNsfwUserFlag  (default to true)
//     * @param includeNsfwAdminFlag  (default to true)
//     * @param includeRankedTimeline  (default to true)
//     * @param includeAltTextCompose  (default to true)
//     * @param dmQualityFilter enabled, disabled (optional)
//     * @param allowDmsFrom verified ,following ,all  (optional)
//     * @param dmReceiptSetting all_disabled, all_disabled (optional)
//     * @return [UpdateAccountSettingsResponse]
//     */
//    @FormUrlEncoded
//    @POST("1.1/account/settings.json")
//    suspend fun postUpdateAccountSettings(
//        @Field("include_mention_filter") includeMentionFilter: kotlin.Boolean = true,
//        @Field("include_nsfw_user_flag") includeNsfwUserFlag: kotlin.Boolean = true,
//        @Field("include_nsfw_admin_flag") includeNsfwAdminFlag: kotlin.Boolean = true,
//        @Field("include_ranked_timeline") includeRankedTimeline: kotlin.Boolean = true,
//        @Field("include_alt_text_compose") includeAltTextCompose: kotlin.Boolean = true,
//        @Field("dm_quality_filter") dmQualityFilter: kotlin.String? = null,
//        @Field("allow_dms_from") allowDmsFrom: kotlin.String? = null,
//        @Field("dm_receipt_setting") dmReceiptSetting: kotlin.String? = null
//    ): Response<UpdateAccountSettingsResponse>

}
