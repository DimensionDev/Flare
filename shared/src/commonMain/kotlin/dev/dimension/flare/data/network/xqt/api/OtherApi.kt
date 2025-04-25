package dev.dimension.flare.data.network.xqt.api

import de.jensklingenberg.ktorfit.Response
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Path
import de.jensklingenberg.ktorfit.http.Query
import dev.dimension.flare.data.network.xqt.model.AudioSpaceByIDResponse
import dev.dimension.flare.data.network.xqt.model.Other200Response

internal interface OtherApi {
    /**
     *
     * This is not an actual endpoint
     * Responses:
     *  - 200: Successful operation
     *
     * @return [Other200Response]
     */
    @GET("other")
    suspend fun other(): Response<Other200Response>

    @GET("graphql/{pathQueryId}/AudioSpaceById")
    suspend fun getAudioSpaceById(
        @Path("pathQueryId") pathQueryId: String = "6EKIIUVBY_o9xdZqboKTZg",
        @Query("variables")
        variables: String =
            """
            {
                "id": "1gqxvjdzVpkxB",
                "isMetatagsQuery": false,
                "withReplays": true,
                "withListeners": true
            }
            """.trimIndent(),
        @Query("features")
        features: String =
            """
            {
                "spaces_2022_h2_spaces_communities": true,
                "spaces_2022_h2_clipping": true,
                "creator_subscriptions_tweet_preview_api_enabled": true,
                "profile_label_improvements_pcf_label_in_post_enabled": true,
                "rweb_tipjar_consumption_enabled": true,
                "responsive_web_graphql_exclude_directive_enabled": true,
                "verified_phone_label_enabled": false,
                "premium_content_api_read_enabled": false,
                "communities_web_enable_tweet_community_results_fetch": true,
                "c9s_tweet_anatomy_moderator_badge_enabled": true,
                "responsive_web_grok_analyze_button_fetch_trends_enabled": false,
                "responsive_web_grok_analyze_post_followups_enabled": true,
                "responsive_web_jetfuel_frame": false,
                "responsive_web_grok_share_attachment_enabled": true,
                "articles_preview_enabled": true,
                "responsive_web_graphql_skip_user_profile_image_extensions_enabled": false,
                "responsive_web_edit_tweet_api_enabled": true,
                "graphql_is_translatable_rweb_tweet_is_translatable_enabled": true,
                "view_counts_everywhere_api_enabled": true,
                "longform_notetweets_consumption_enabled": true,
                "responsive_web_twitter_article_tweet_consumption_enabled": true,
                "tweet_awards_web_tipping_enabled": false,
                "responsive_web_grok_show_grok_translated_post": false,
                "responsive_web_grok_analysis_button_from_backend": false,
                "creator_subscriptions_quote_tweet_preview_enabled": false,
                "freedom_of_speech_not_reach_fetch_enabled": true,
                "standardized_nudges_misinfo": true,
                "tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled": true,
                "longform_notetweets_rich_text_read_enabled": true,
                "longform_notetweets_inline_media_enabled": true,
                "responsive_web_grok_image_annotation_enabled": true,
                "responsive_web_graphql_timeline_navigation_enabled": true,
                "responsive_web_enhance_cards_enabled": false
            }
            """.trimIndent(),
    ): AudioSpaceByIDResponse
}
