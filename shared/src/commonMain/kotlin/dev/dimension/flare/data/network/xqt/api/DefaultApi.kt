package dev.dimension.flare.data.network.xqt.api

import de.jensklingenberg.ktorfit.Response
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Path
import de.jensklingenberg.ktorfit.http.Query
import dev.dimension.flare.data.network.xqt.model.GetProfileSpotlightsQuery200Response
import dev.dimension.flare.data.network.xqt.model.GetTweetResultByRestId200Response

internal interface DefaultApi {
    /**
     *
     * get user by screen name
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "_pnlqeTOtnpbIL9o-fS_pg")
     * @param variables  (default to "{\"screen_name\": \"elonmusk\"}")
     * @param features  (default to "{}")
     * @return [GetProfileSpotlightsQuery200Response]
     */
    @GET("graphql/{pathQueryId}/ProfileSpotlightsQuery")
    suspend fun getProfileSpotlightsQuery(
        @Path("pathQueryId") pathQueryId: kotlin.String = "_pnlqeTOtnpbIL9o-fS_pg",
        @Query("variables") variables: kotlin.String = "{\"screen_name\": \"elonmusk\"}",
        @Query("features") features: kotlin.String = "{}",
    ): Response<GetProfileSpotlightsQuery200Response>

    /**
     *
     * get TweetResultByRestId
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "DJS3BdhUhcaEpZ7B7irJDg")
     * @param variables  (default to "{\"tweetId\": \"1691730070669517096\", \"withCommunity\": false, \"includePromotedContent\": false, \"withVoice\": false}")
     * @param features  (default to "{\"creator_subscriptions_tweet_preview_api_enabled\": true, \"tweetypie_unmention_optimization_enabled\": true, \"responsive_web_edit_tweet_api_enabled\": true, \"graphql_is_translatable_rweb_tweet_is_translatable_enabled\": true, \"view_counts_everywhere_api_enabled\": true, \"longform_notetweets_consumption_enabled\": true, \"responsive_web_twitter_article_tweet_consumption_enabled\": false, \"tweet_awards_web_tipping_enabled\": false, \"freedom_of_speech_not_reach_fetch_enabled\": true, \"standardized_nudges_misinfo\": true, \"tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled\": true, \"longform_notetweets_rich_text_read_enabled\": true, \"longform_notetweets_inline_media_enabled\": true, \"responsive_web_graphql_exclude_directive_enabled\": true, \"verified_phone_label_enabled\": false, \"responsive_web_media_download_video_enabled\": false, \"responsive_web_graphql_skip_user_profile_image_extensions_enabled\": false, \"responsive_web_graphql_timeline_navigation_enabled\": true, \"responsive_web_enhance_cards_enabled\": false}")
     * @return [GetTweetResultByRestId200Response]
     */
    @GET("graphql/{pathQueryId}/TweetResultByRestId")
    suspend fun getTweetResultByRestId(
        @Path("pathQueryId") pathQueryId: kotlin.String = "tmhPpO5sDermwYmq3h034A",
        @Query(
            "variables",
        ) variables: kotlin.String = "{\"tweetId\": \"1691730070669517096\", \"withCommunity\": false, \"includePromotedContent\": false, \"withVoice\": false}",
        @Query(
            "features",
        ) features: kotlin.String = "\t{\"creator_subscriptions_tweet_preview_api_enabled\":true,\"premium_content_api_read_enabled\":false,\"communities_web_enable_tweet_community_results_fetch\":true,\"c9s_tweet_anatomy_moderator_badge_enabled\":true,\"responsive_web_grok_analyze_button_fetch_trends_enabled\":false,\"responsive_web_grok_analyze_post_followups_enabled\":true,\"responsive_web_jetfuel_frame\":true,\"responsive_web_grok_share_attachment_enabled\":true,\"responsive_web_grok_annotations_enabled\":true,\"articles_preview_enabled\":true,\"responsive_web_edit_tweet_api_enabled\":true,\"graphql_is_translatable_rweb_tweet_is_translatable_enabled\":true,\"view_counts_everywhere_api_enabled\":true,\"longform_notetweets_consumption_enabled\":true,\"responsive_web_twitter_article_tweet_consumption_enabled\":true,\"content_disclosure_indicator_enabled\":true,\"content_disclosure_ai_generated_indicator_enabled\":true,\"responsive_web_grok_show_grok_translated_post\":false,\"responsive_web_grok_analysis_button_from_backend\":true,\"post_ctas_fetch_enabled\":false,\"freedom_of_speech_not_reach_fetch_enabled\":true,\"standardized_nudges_misinfo\":true,\"tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled\":true,\"longform_notetweets_rich_text_read_enabled\":true,\"longform_notetweets_inline_media_enabled\":false,\"profile_label_improvements_pcf_label_in_post_enabled\":true,\"responsive_web_profile_redirect_enabled\":false,\"rweb_tipjar_consumption_enabled\":false,\"verified_phone_label_enabled\":false,\"responsive_web_grok_image_annotation_enabled\":true,\"responsive_web_grok_imagine_annotation_enabled\":true,\"responsive_web_grok_community_note_auto_translation_is_enabled\":false,\"responsive_web_graphql_skip_user_profile_image_extensions_enabled\":false,\"responsive_web_graphql_timeline_navigation_enabled\":true,\"responsive_web_enhance_cards_enabled\":false}",
        @Query("fieldToggles")
            fieldToggles: kotlin.String = "{\"withArticleRichContentState\":true,\"withArticlePlainText\":false,\"withArticleSummaryText\":true,\"withArticleVoiceOver\":true}",
    ): Response<GetTweetResultByRestId200Response>
}
