package dev.dimension.flare.data.network.xqt.api

import de.jensklingenberg.ktorfit.Response
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Path
import de.jensklingenberg.ktorfit.http.Query
import dev.dimension.flare.data.network.xqt.model.DMPermissionsCreateGroupConversationResponse
import dev.dimension.flare.data.network.xqt.model.InboxConversationTimelineResponse
import dev.dimension.flare.data.network.xqt.model.InboxDMTrustedTimelineResponse
import dev.dimension.flare.data.network.xqt.model.InboxDMUserUpdateResponse
import dev.dimension.flare.data.network.xqt.model.ListsMembershipsResponse
import dev.dimension.flare.data.network.xqt.model.UserRecommendationsItem

internal interface V11GetApi {
    /**
     *
     * get friends following list
     * Responses:
     *  - 200: Successful operation
     *
     * @param includeProfileInterstitialType  (default to 1)
     * @param includeBlocking  (default to 1)
     * @param includeBlockedBy  (default to 1)
     * @param includeFollowedBy  (default to 1)
     * @param includeWantRetweets  (default to 1)
     * @param includeMuteEdge  (default to 1)
     * @param includeCanDm  (default to 1)
     * @param includeCanMediaTag  (default to 1)
     * @param includeExtHasNftAvatar  (default to 1)
     * @param includeExtIsBlueVerified  (default to 1)
     * @param includeExtVerifiedType  (default to 1)
     * @param includeExtProfileImageShape  (default to 1)
     * @param skipStatus  (default to 1)
     * @param cursor  (default to -1)
     * @param userId  (default to "44196397")
     * @param count  (default to 3)
     * @param withTotalCount  (default to true)
     * @return [Unit]
     */
    @GET("1.1/friends/following/list.json")
    suspend fun getFriendsFollowingList(
        @Query("include_profile_interstitial_type") includeProfileInterstitialType: kotlin.Int = 1,
        @Query("include_blocking") includeBlocking: kotlin.Int = 1,
        @Query("include_blocked_by") includeBlockedBy: kotlin.Int = 1,
        @Query("include_followed_by") includeFollowedBy: kotlin.Int = 1,
        @Query("include_want_retweets") includeWantRetweets: kotlin.Int = 1,
        @Query("include_mute_edge") includeMuteEdge: kotlin.Int = 1,
        @Query("include_can_dm") includeCanDm: kotlin.Int = 1,
        @Query("include_can_media_tag") includeCanMediaTag: kotlin.Int = 1,
        @Query("include_ext_has_nft_avatar") includeExtHasNftAvatar: kotlin.Int = 1,
        @Query("include_ext_is_blue_verified") includeExtIsBlueVerified: kotlin.Int = 1,
        @Query("include_ext_verified_type") includeExtVerifiedType: kotlin.Int = 1,
        @Query("include_ext_profile_image_shape") includeExtProfileImageShape: kotlin.Int = 1,
        @Query("skip_status") skipStatus: kotlin.Int = 1,
        @Query("cursor") cursor: kotlin.Int = -1,
        @Query("user_id") userId: kotlin.String = "44196397",
        @Query("count") count: kotlin.Int = 3,
        @Query("with_total_count") withTotalCount: kotlin.Boolean = true,
    ): Response<Unit>

    /**
     *
     * get search typeahead
     * Responses:
     *  - 200: Successful operation
     *
     * @param includeExtIsBlueVerified  (default to 1)
     * @param includeExtVerifiedType  (default to 1)
     * @param includeExtProfileImageShape  (default to 1)
     * @param q  (default to "test")
     * @param src  (default to "search_box")
     * @param resultType  (default to "events,users,topics")
     * @return [Unit]
     */
    @GET("1.1/search/typeahead.json")
    suspend fun getSearchTypeahead(
        @Query("include_ext_is_blue_verified") includeExtIsBlueVerified: kotlin.Int = 1,
        @Query("include_ext_verified_type") includeExtVerifiedType: kotlin.Int = 1,
        @Query("include_ext_profile_image_shape") includeExtProfileImageShape: kotlin.Int = 1,
        @Query("q") q: kotlin.String = "test",
        @Query("src") src: kotlin.String = "search_box",
        @Query("result_type") resultType: kotlin.String = "events,users,topics",
    ): Response<Unit>

    @GET("1.1/users/recommendations.json")
    suspend fun getUserRecommendations(
        @Query("include_profile_interstitial_type") includeProfileInterstitialType: Int = 1,
        @Query("include_blocking") includeBlocking: Int = 1,
        @Query("include_blocked_by") includeBlockedBy: Int = 1,
        @Query("include_followed_by") includeFollowedBy: Int = 1,
        @Query("include_want_retweets") includeWantRetweets: Int = 1,
        @Query("include_mute_edge") includeMuteEdge: Int = 1,
        @Query("include_can_dm") includeCanDm: Int = 1,
        @Query("include_can_media_tag") includeCanMediaTag: Int = 1,
        @Query("include_ext_has_nft_avatar") includeExtHasNftAvatar: Int = 1,
        @Query("include_ext_is_blue_verified") includeExtIsBlueVerified: Int = 1,
        @Query("include_ext_verified_type") includeExtVerifiedType: Int = 1,
        @Query("include_ext_profile_image_shape") includeExtProfileImageShape: Int = 1,
        @Query("skip_status") skipStatus: Int = 1,
        @Query("pc") pc: Boolean = true,
        @Query("display_location") displayLocation: String = "profile_accounts_sidebar",
        @Query("limit") limit: Int = 3,
        @Query("user_id") userId: String,
        @Query(
            "ext",
        ) ext: String = "mediaStats,highlightedLabel,hasNftAvatar,voiceInfo,birdwatchPivot,superFollowMetadata,unmentionInfo,editControl",
    ): List<UserRecommendationsItem>

    @GET("1.1/lists/memberships.json")
    suspend fun getListsMemberships(
        @Query("include_profile_interstitial_type") includeProfileInterstitialType: Int = 1,
        @Query("include_blocking") includeBlocking: Int = 1,
        @Query("include_blocked_by") includeBlockedBy: Int = 1,
        @Query("include_followed_by") includeFollowedBy: Int = 1,
        @Query("include_want_retweets") includeWantRetweets: Int = 1,
        @Query("include_mute_edge") includeMuteEdge: Int = 1,
        @Query("include_can_dm") includeCanDm: Int = 1,
        @Query("include_can_media_tag") includeCanMediaTag: Int = 1,
        @Query("include_ext_is_blue_verified") includeExtIsBlueVerified: Int = 1,
        @Query("include_ext_verified_type") includeExtVerifiedType: Int = 1,
        @Query("include_ext_profile_image_shape") includeExtProfileImageShape: Int = 1,
        @Query("skip_status") skipStatus: Int = 1,
        @Query("cards_platform") cardsPlatform: String = "Web-12",
        @Query("include_cards") includeCards: Int = 1,
        @Query("include_ext_alt_text") includeExtAltText: Boolean = true,
        @Query("include_ext_limited_action_results") includeExtLimitedActionResults: Boolean = true,
        @Query("include_quote_count") includeQuoteCount: Boolean = true,
        @Query("include_reply_count") includeReplyCount: Int = 1,
        @Query("tweet_mode") tweetMode: String = "extended",
        @Query("include_ext_views") includeExtViews: Boolean = true,
        @Query("cursor") cursor: Int = -1,
        @Query("user_id") userId: String,
        @Query("count") count: Int = 1000,
        @Query("filter_to_owned_lists") filterToOwnedLists: Boolean = true,
    ): Response<ListsMembershipsResponse>

//
//    /**
//     * GET 1.1/dm/conversation/{conversation_id}.json
//     *
//     * get dm Conversation timeline
//     * Responses:
//     *  - 200: Successful response
//     *
//     * @param conversationId dm conversation_id
//     * @param maxId  (optional)
//     * @param context  (optional, default to "FETCH_DM_CONVERSATION_HISTORY")
//     * @param includeProfileInterstitialType  (optional, default to 1)
//     * @param includeBlocking  (optional, default to 1)
//     * @param includeBlockedBy  (optional, default to 1)
//     * @param includeFollowedBy  (optional, default to 1)
//     * @param includeWantRetweets  (optional, default to 1)
//     * @param includeMuteEdge  (optional, default to 1)
//     * @param includeCanDm  (optional, default to 1)
//     * @param includeCanMediaTag  (optional, default to 1)
//     * @param includeExtIsBlueVerified  (optional, default to 1)
//     * @param includeExtVerifiedType  (optional, default to 1)
//     * @param includeExtProfileImageShape  (optional, default to 1)
//     * @param skipStatus  (optional, default to 1)
//     * @param cardsPlatform  (optional, default to "Web-12")
//     * @param includeCards  (optional, default to 1)
//     * @param includeExtAltText  (optional, default to true)
//     * @param includeExtLimitedActionResults  (optional, default to true)
//     * @param includeQuoteCount  (optional, default to true)
//     * @param includeReplyCount  (optional, default to 1)
//     * @param tweetMode  (optional, default to "extended")
//     * @param includeExtViews  (optional, default to true)
//     * @param dmUsers  (optional, default to false)
//     * @param includeGroups  (optional, default to true)
//     * @param includeInboxTimelines  (optional, default to true)
//     * @param includeExtMediaColor  (optional, default to true)
//     * @param supportsReactions  (optional, default to true)
//     * @param dmSecretConversationsEnabled  (optional, default to false)
//     * @param krsRegistrationEnabled  (optional, default to true)
//     * @param includeConversationInfo  (optional, default to true)
//     * @param ext  (optional, default to "mediaColor,altText,mediaStats,highlightedLabel,voiceInfo,birdwatchPivot,superFollowMetadata,unmentionInfo,editControl,article")
//     * @return [InboxConversationTimelineResponse]
//     */
//    @GET("1.1/dm/conversation/{conversation_id}.json")
//    suspend fun getDMConversationTimeline(
//        @Path("conversation_id") conversationId: kotlin.String,
//        @Query("max_id") maxId: kotlin.String? = null,
//        @Query("context") context: kotlin.String = "FETCH_DM_CONVERSATION_HISTORY",
//        @Query("include_profile_interstitial_type") includeProfileInterstitialType: kotlin.Int = 1,
//        @Query("include_blocking") includeBlocking: kotlin.Int = 1,
//        @Query("include_blocked_by") includeBlockedBy: kotlin.Int = 1,
//        @Query("include_followed_by") includeFollowedBy: kotlin.Int = 1,
//        @Query("include_want_retweets") includeWantRetweets: kotlin.Int = 1,
//        @Query("include_mute_edge") includeMuteEdge: kotlin.Int = 1,
//        @Query("include_can_dm") includeCanDm: kotlin.Int = 1,
//        @Query("include_can_media_tag") includeCanMediaTag: kotlin.Int = 1,
//        @Query("include_ext_is_blue_verified") includeExtIsBlueVerified: kotlin.Int = 1,
//        @Query("include_ext_verified_type") includeExtVerifiedType: kotlin.Int = 1,
//        @Query("include_ext_profile_image_shape") includeExtProfileImageShape: kotlin.Int = 1,
//        @Query("skip_status") skipStatus: kotlin.Int = 1,
//        @Query("cards_platform") cardsPlatform: kotlin.String = "Web-12",
//        @Query("include_cards") includeCards: kotlin.Int = 1,
//        @Query("include_ext_alt_text") includeExtAltText: kotlin.Boolean = true,
//        @Query("include_ext_limited_action_results") includeExtLimitedActionResults: kotlin.Boolean = true,
//        @Query("include_quote_count") includeQuoteCount: kotlin.Boolean = true,
//        @Query("include_reply_count") includeReplyCount: kotlin.Int = 1,
//        @Query("tweet_mode") tweetMode: kotlin.String = "extended",
//        @Query("include_ext_views") includeExtViews: kotlin.Boolean = true,
//        @Query("dm_users") dmUsers: kotlin.Boolean = false,
//        @Query("include_groups") includeGroups: kotlin.Boolean = true,
//        @Query("include_inbox_timelines") includeInboxTimelines: kotlin.Boolean = true,
//        @Query("include_ext_media_color") includeExtMediaColor: kotlin.Boolean = true,
//        @Query("supports_reactions") supportsReactions: kotlin.Boolean = true,
//        @Query("dm_secret_conversations_enabled") dmSecretConversationsEnabled: kotlin.Boolean = false,
//        @Query("krs_registration_enabled") krsRegistrationEnabled: kotlin.Boolean = true,
//        @Query("include_conversation_info") includeConversationInfo: kotlin.Boolean = true,
//        @Query("ext") ext: kotlin.String = "mediaColor,altText,mediaStats,highlightedLabel,voiceInfo,birdwatchPivot,superFollowMetadata,unmentionInfo,editControl,article"
//    ): Response<InboxConversationTimelineResponse>
//
//    /**
//     * GET 1.1/dm/inbox_timeline/trusted.json
//     *
//     * get dm inbox timeline trusted
//     * Responses:
//     *  - 200: Successful response
//     *
//     * @return [InboxDMTrustedTimelineResponse]
//     */
//    @GET("1.1/dm/inbox_timeline/trusted.json")
//    suspend fun getDMInboxTimelineTrusted(): Response<InboxDMTrustedTimelineResponse>
//
//    /**
//     * GET 1.1/dm/inbox_timeline/untrusted.json
//     *
//     * get dm inbox timeline un trusted
//     * Responses:
//     *  - 200: Successful response
//     *
//     * @return [InboxDMTrustedTimelineResponse]
//     */
//    @GET("1.1/dm/inbox_timeline/untrusted.json")
//    suspend fun getDMInboxTimelineUNTrusted(): Response<InboxDMTrustedTimelineResponse>
//
//    /**
//     * GET 1.1/dm/permissions.json
//     *
//     * get DM Permissions
//     * Responses:
//     *  - 200: Successful response
//     *
//     * @return [DMPermissionsCreateGroupConversationResponse]
//     */
//    @GET("1.1/dm/permissions.json")
//    suspend fun getDMPermissions(): Response<DMPermissionsCreateGroupConversationResponse>
//
//    /**
//     * GET 1.1/dm/user_updates.json
//     *
//     * get dm user updates
//     * Responses:
//     *  - 200: Successful response
//     *
//     * @param cursor cursor (optional)
//     * @return [InboxDMUserUpdateResponse]
//     */
//    @GET("1.1/dm/user_updates.json")
//    suspend fun getDMUserUpdates(@Query("cursor") cursor: kotlin.String? = null): Response<InboxDMUserUpdateResponse>

}
