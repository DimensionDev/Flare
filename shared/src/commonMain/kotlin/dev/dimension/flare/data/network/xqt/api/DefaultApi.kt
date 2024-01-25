package dev.dimension.flare.data.network.xqt.api

import de.jensklingenberg.ktorfit.Response
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Path
import de.jensklingenberg.ktorfit.http.Query
import dev.dimension.flare.data.network.xqt.model.GetProfileSpotlightsQuery200Response
import dev.dimension.flare.data.network.xqt.model.GetTweetResultByRestId200Response

interface DefaultApi {
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
        @Path("pathQueryId") pathQueryId: kotlin.String = "DJS3BdhUhcaEpZ7B7irJDg",
        @Query(
            "variables",
        ) variables: kotlin.String = "{\"tweetId\": \"1691730070669517096\", \"withCommunity\": false, \"includePromotedContent\": false, \"withVoice\": false}",
        @Query(
            "features",
        ) features: kotlin.String = "{\"creator_subscriptions_tweet_preview_api_enabled\": true, \"tweetypie_unmention_optimization_enabled\": true, \"responsive_web_edit_tweet_api_enabled\": true, \"graphql_is_translatable_rweb_tweet_is_translatable_enabled\": true, \"view_counts_everywhere_api_enabled\": true, \"longform_notetweets_consumption_enabled\": true, \"responsive_web_twitter_article_tweet_consumption_enabled\": false, \"tweet_awards_web_tipping_enabled\": false, \"freedom_of_speech_not_reach_fetch_enabled\": true, \"standardized_nudges_misinfo\": true, \"tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled\": true, \"longform_notetweets_rich_text_read_enabled\": true, \"longform_notetweets_inline_media_enabled\": true, \"responsive_web_graphql_exclude_directive_enabled\": true, \"verified_phone_label_enabled\": false, \"responsive_web_media_download_video_enabled\": false, \"responsive_web_graphql_skip_user_profile_image_extensions_enabled\": false, \"responsive_web_graphql_timeline_navigation_enabled\": true, \"responsive_web_enhance_cards_enabled\": false}",
    ): Response<GetTweetResultByRestId200Response>
}
