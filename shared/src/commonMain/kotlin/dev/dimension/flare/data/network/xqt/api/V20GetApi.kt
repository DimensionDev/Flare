package dev.dimension.flare.data.network.xqt.api

import de.jensklingenberg.ktorfit.Response
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Query
import dev.dimension.flare.data.network.xqt.model.legacy.TopLevel

internal interface V20GetApi {
    /**
     *
     * get search adaptive
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
     * @param cardsPlatform  (default to "Web-12")
     * @param includeCards  (default to 1)
     * @param includeExtAltText  (default to true)
     * @param includeExtLimitedActionResults  (default to false)
     * @param includeQuoteCount  (default to true)
     * @param includeReplyCount  (default to 1)
     * @param tweetMode  (default to "extended")
     * @param includeExtViews  (default to true)
     * @param includeEntities  (default to true)
     * @param includeUserEntities  (default to true)
     * @param includeExtMediaColor  (default to true)
     * @param includeExtMediaAvailability  (default to true)
     * @param includeExtSensitiveMediaWarning  (default to true)
     * @param includeExtTrustedFriendsMetadata  (default to true)
     * @param sendErrorCodes  (default to true)
     * @param simpleQuotedTweet  (default to true)
     * @param q  (default to "elon musk")
     * @param querySource  (default to "trend_click")
     * @param count  (default to 20)
     * @param requestContext  (default to "launch")
     * @param pc  (default to 1)
     * @param spellingCorrections  (default to 1)
     * @param includeExtEditControl  (default to true)
     * @param ext  (default to "mediaStats,highlightedLabel,hasNftAvatar,voiceInfo,birdwatchPivot,enrichments,superFollowMetadata,unmentionInfo,editControl,vibe")
     * @return [Unit]
     */
    @GET("2/search/adaptive.json")
    suspend fun getSearchAdaptive(
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
        @Query("cards_platform") cardsPlatform: kotlin.String = "Web-12",
        @Query("include_cards") includeCards: kotlin.Int = 1,
        @Query("include_ext_alt_text") includeExtAltText: kotlin.Boolean = true,
        @Query("include_ext_limited_action_results") includeExtLimitedActionResults: kotlin.Boolean = false,
        @Query("include_quote_count") includeQuoteCount: kotlin.Boolean = true,
        @Query("include_reply_count") includeReplyCount: kotlin.Int = 1,
        @Query("tweet_mode") tweetMode: kotlin.String = "extended",
        @Query("include_ext_views") includeExtViews: kotlin.Boolean = true,
        @Query("include_entities") includeEntities: kotlin.Boolean = true,
        @Query("include_user_entities") includeUserEntities: kotlin.Boolean = true,
        @Query("include_ext_media_color") includeExtMediaColor: kotlin.Boolean = true,
        @Query("include_ext_media_availability") includeExtMediaAvailability: kotlin.Boolean = true,
        @Query("include_ext_sensitive_media_warning") includeExtSensitiveMediaWarning: kotlin.Boolean = true,
        @Query("include_ext_trusted_friends_metadata") includeExtTrustedFriendsMetadata: kotlin.Boolean = true,
        @Query("send_error_codes") sendErrorCodes: kotlin.Boolean = true,
        @Query("simple_quoted_tweet") simpleQuotedTweet: kotlin.Boolean = true,
        @Query("q") q: kotlin.String = "elon musk",
        @Query("query_source") querySource: kotlin.String = "trend_click",
        @Query("count") count: kotlin.Int = 20,
        @Query("requestContext") requestContext: kotlin.String = "launch",
        @Query("pc") pc: kotlin.Int = 1,
        @Query("spelling_corrections") spellingCorrections: kotlin.Int = 1,
        @Query("include_ext_edit_control") includeExtEditControl: kotlin.Boolean = true,
        @Query(
            "ext",
        ) ext: kotlin.String =
            "mediaStats,highlightedLabel,hasNftAvatar,voiceInfo," +
                "birdwatchPivot,enrichments,superFollowMetadata,unmentionInfo,editControl,vibe",
    ): Response<Unit>

    @GET("2/notifications/mentions.json")
    suspend fun getNotificationsMentions(
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
        @Query("cards_platform") cardsPlatform: String = "Web-12",
        @Query("include_cards") includeCards: Int = 1,
        @Query("include_ext_alt_text") includeExtAltText: Boolean = true,
        @Query("include_ext_limited_action_results") includeExtLimitedActionResults: Boolean = true,
        @Query("include_quote_count") includeQuoteCount: Boolean = true,
        @Query("include_reply_count") includeReplyCount: Int = 1,
        @Query("tweet_mode") tweetMode: String = "extended",
        @Query("include_ext_views") includeExtViews: Boolean = true,
        @Query("include_entities") includeEntities: Boolean = true,
        @Query("include_user_entities") includeUserEntities: Boolean = true,
        @Query("include_ext_media_color") includeExtMediaColor: Boolean = true,
        @Query("include_ext_media_availability") includeExtMediaAvailability: Boolean = true,
        @Query("include_ext_sensitive_media_warning") includeExtSensitiveMediaWarning: Boolean = true,
        @Query("include_ext_trusted_friends_metadata") includeExtTrustedFriendsMetadata: Boolean = true,
        @Query("send_error_codes") sendErrorCodes: Boolean = true,
        @Query("simple_quoted_tweet") simpleQuotedTweet: Boolean = true,
        @Query("count") count: Int = 20,
        @Query("cursor") cursor: String? = null,
        @Query("requestContext") requestContext: String = "launch",
        @Query(
            "ext",
        ) ext: String =
            "mediaStats,highlightedLabel,hasNftAvatar,voiceInfo," +
                "birdwatchPivot,superFollowMetadata,unmentionInfo,editControl",
    ): TopLevel

    @GET("2/guide.json")
    suspend fun getGuide(
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
        @Query("cards_platform") cardsPlatform: String = "Web-12",
        @Query("include_cards") includeCards: Int = 1,
        @Query("include_ext_alt_text") includeExtAltText: Boolean = true,
        @Query("include_ext_limited_action_results") includeExtLimitedActionResults: Boolean = true,
        @Query("include_quote_count") includeQuoteCount: Boolean = true,
        @Query("include_reply_count") includeReplyCount: Int = 1,
        @Query("tweet_mode") tweetMode: String = "extended",
        @Query("include_ext_views") includeExtViews: Boolean = true,
        @Query("include_entities") includeEntities: Boolean = true,
        @Query("include_user_entities") includeUserEntities: Boolean = true,
        @Query("include_ext_media_color") includeExtMediaColor: Boolean = true,
        @Query("include_ext_media_availability") includeExtMediaAvailability: Boolean = true,
        @Query("include_ext_sensitive_media_warning") includeExtSensitiveMediaWarning: Boolean = true,
        @Query("include_ext_trusted_friends_metadata") includeExtTrustedFriendsMetadata: Boolean = true,
        @Query("send_error_codes") sendErrorCodes: Boolean = true,
        @Query("simple_quoted_tweet") simpleQuotedTweet: Boolean = true,
        @Query("tab_category") tabCategory: String = "objective_trends",
        @Query("count") count: Int = 20,
        @Query(
            "ext",
        ) ext: String =
            "mediaStats,highlightedLabel,hasNftAvatar,voiceInfo," +
                "birdwatchPivot,superFollowMetadata,unmentionInfo,editControl",
    ): TopLevel
}
