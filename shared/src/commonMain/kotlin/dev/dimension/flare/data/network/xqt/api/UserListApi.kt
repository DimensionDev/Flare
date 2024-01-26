package dev.dimension.flare.data.network.xqt.api

import de.jensklingenberg.ktorfit.Response
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Path
import de.jensklingenberg.ktorfit.http.Query
import dev.dimension.flare.data.network.xqt.model.GetFavoriters200Response
import dev.dimension.flare.data.network.xqt.model.GetFollowers200Response
import dev.dimension.flare.data.network.xqt.model.GetRetweeters200Response

internal interface UserListApi {
    /**
     *
     * get tweet favoriters
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "VIA2_af01oqZqBB6NvWi-Q")
     * @param variables  (default to "{\"tweetId\": \"1349129669258448897\", \"count\": 20, \"includePromotedContent\": true}")
     * @param features  (default to "{\"responsive_web_graphql_exclude_directive_enabled\": true, \"verified_phone_label_enabled\": false, \"creator_subscriptions_tweet_preview_api_enabled\": true, \"responsive_web_graphql_timeline_navigation_enabled\": true, \"responsive_web_graphql_skip_user_profile_image_extensions_enabled\": false, \"tweetypie_unmention_optimization_enabled\": true, \"responsive_web_edit_tweet_api_enabled\": true, \"graphql_is_translatable_rweb_tweet_is_translatable_enabled\": true, \"view_counts_everywhere_api_enabled\": true, \"longform_notetweets_consumption_enabled\": true, \"responsive_web_twitter_article_tweet_consumption_enabled\": false, \"tweet_awards_web_tipping_enabled\": false, \"freedom_of_speech_not_reach_fetch_enabled\": true, \"standardized_nudges_misinfo\": true, \"tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled\": true, \"longform_notetweets_rich_text_read_enabled\": true, \"longform_notetweets_inline_media_enabled\": true, \"responsive_web_media_download_video_enabled\": false, \"responsive_web_enhance_cards_enabled\": false}")
     * @return [GetFavoriters200Response]
     */
    @GET("graphql/{pathQueryId}/Favoriters")
    suspend fun getFavoriters(
        @Path("pathQueryId") pathQueryId: kotlin.String = "VIA2_af01oqZqBB6NvWi-Q",
        @Query(
            "variables",
        ) variables: kotlin.String = "{\"tweetId\": \"1349129669258448897\", \"count\": 20, \"includePromotedContent\": true}",
        @Query(
            "features",
        ) features: kotlin.String = "{\"responsive_web_graphql_exclude_directive_enabled\": true, \"verified_phone_label_enabled\": false, \"creator_subscriptions_tweet_preview_api_enabled\": true, \"responsive_web_graphql_timeline_navigation_enabled\": true, \"responsive_web_graphql_skip_user_profile_image_extensions_enabled\": false, \"tweetypie_unmention_optimization_enabled\": true, \"responsive_web_edit_tweet_api_enabled\": true, \"graphql_is_translatable_rweb_tweet_is_translatable_enabled\": true, \"view_counts_everywhere_api_enabled\": true, \"longform_notetweets_consumption_enabled\": true, \"responsive_web_twitter_article_tweet_consumption_enabled\": false, \"tweet_awards_web_tipping_enabled\": false, \"freedom_of_speech_not_reach_fetch_enabled\": true, \"standardized_nudges_misinfo\": true, \"tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled\": true, \"longform_notetweets_rich_text_read_enabled\": true, \"longform_notetweets_inline_media_enabled\": true, \"responsive_web_media_download_video_enabled\": false, \"responsive_web_enhance_cards_enabled\": false}",
    ): Response<GetFavoriters200Response>

    /**
     *
     * get user list of followers
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "rRXFSG5vR6drKr5M37YOTw")
     * @param variables  (default to "{\"userId\": \"44196397\", \"count\": 20, \"includePromotedContent\": false}")
     * @param features  (default to "{\"responsive_web_graphql_exclude_directive_enabled\": true, \"verified_phone_label_enabled\": false, \"creator_subscriptions_tweet_preview_api_enabled\": true, \"responsive_web_graphql_timeline_navigation_enabled\": true, \"responsive_web_graphql_skip_user_profile_image_extensions_enabled\": false, \"tweetypie_unmention_optimization_enabled\": true, \"responsive_web_edit_tweet_api_enabled\": true, \"graphql_is_translatable_rweb_tweet_is_translatable_enabled\": true, \"view_counts_everywhere_api_enabled\": true, \"longform_notetweets_consumption_enabled\": true, \"responsive_web_twitter_article_tweet_consumption_enabled\": false, \"tweet_awards_web_tipping_enabled\": false, \"freedom_of_speech_not_reach_fetch_enabled\": true, \"standardized_nudges_misinfo\": true, \"tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled\": true, \"longform_notetweets_rich_text_read_enabled\": true, \"longform_notetweets_inline_media_enabled\": true, \"responsive_web_media_download_video_enabled\": false, \"responsive_web_enhance_cards_enabled\": false}")
     * @return [GetFollowers200Response]
     */
    @GET("graphql/{pathQueryId}/Followers")
    suspend fun getFollowers(
        @Path("pathQueryId") pathQueryId: kotlin.String = "rRXFSG5vR6drKr5M37YOTw",
        @Query("variables") variables: kotlin.String = "{\"userId\": \"44196397\", \"count\": 20, \"includePromotedContent\": false}",
        @Query(
            "features",
        ) features: kotlin.String = "{\"responsive_web_graphql_exclude_directive_enabled\": true, \"verified_phone_label_enabled\": false, \"creator_subscriptions_tweet_preview_api_enabled\": true, \"responsive_web_graphql_timeline_navigation_enabled\": true, \"responsive_web_graphql_skip_user_profile_image_extensions_enabled\": false, \"tweetypie_unmention_optimization_enabled\": true, \"responsive_web_edit_tweet_api_enabled\": true, \"graphql_is_translatable_rweb_tweet_is_translatable_enabled\": true, \"view_counts_everywhere_api_enabled\": true, \"longform_notetweets_consumption_enabled\": true, \"responsive_web_twitter_article_tweet_consumption_enabled\": false, \"tweet_awards_web_tipping_enabled\": false, \"freedom_of_speech_not_reach_fetch_enabled\": true, \"standardized_nudges_misinfo\": true, \"tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled\": true, \"longform_notetweets_rich_text_read_enabled\": true, \"longform_notetweets_inline_media_enabled\": true, \"responsive_web_media_download_video_enabled\": false, \"responsive_web_enhance_cards_enabled\": false}",
    ): Response<GetFollowers200Response>

    /**
     *
     * get followers you know
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "yqrUptVSP9DAkVLqpc0lsg")
     * @param variables  (default to "{\"userId\": \"44196397\", \"count\": 20, \"includePromotedContent\": false}")
     * @param features  (default to "{\"responsive_web_graphql_exclude_directive_enabled\": true, \"verified_phone_label_enabled\": false, \"creator_subscriptions_tweet_preview_api_enabled\": true, \"responsive_web_graphql_timeline_navigation_enabled\": true, \"responsive_web_graphql_skip_user_profile_image_extensions_enabled\": false, \"tweetypie_unmention_optimization_enabled\": true, \"responsive_web_edit_tweet_api_enabled\": true, \"graphql_is_translatable_rweb_tweet_is_translatable_enabled\": true, \"view_counts_everywhere_api_enabled\": true, \"longform_notetweets_consumption_enabled\": true, \"responsive_web_twitter_article_tweet_consumption_enabled\": false, \"tweet_awards_web_tipping_enabled\": false, \"freedom_of_speech_not_reach_fetch_enabled\": true, \"standardized_nudges_misinfo\": true, \"tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled\": true, \"longform_notetweets_rich_text_read_enabled\": true, \"longform_notetweets_inline_media_enabled\": true, \"responsive_web_media_download_video_enabled\": false, \"responsive_web_enhance_cards_enabled\": false}")
     * @return [GetFollowers200Response]
     */
    @GET("graphql/{pathQueryId}/FollowersYouKnow")
    suspend fun getFollowersYouKnow(
        @Path("pathQueryId") pathQueryId: kotlin.String = "yqrUptVSP9DAkVLqpc0lsg",
        @Query("variables") variables: kotlin.String = "{\"userId\": \"44196397\", \"count\": 20, \"includePromotedContent\": false}",
        @Query(
            "features",
        ) features: kotlin.String = "{\"responsive_web_graphql_exclude_directive_enabled\": true, \"verified_phone_label_enabled\": false, \"creator_subscriptions_tweet_preview_api_enabled\": true, \"responsive_web_graphql_timeline_navigation_enabled\": true, \"responsive_web_graphql_skip_user_profile_image_extensions_enabled\": false, \"tweetypie_unmention_optimization_enabled\": true, \"responsive_web_edit_tweet_api_enabled\": true, \"graphql_is_translatable_rweb_tweet_is_translatable_enabled\": true, \"view_counts_everywhere_api_enabled\": true, \"longform_notetweets_consumption_enabled\": true, \"responsive_web_twitter_article_tweet_consumption_enabled\": false, \"tweet_awards_web_tipping_enabled\": false, \"freedom_of_speech_not_reach_fetch_enabled\": true, \"standardized_nudges_misinfo\": true, \"tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled\": true, \"longform_notetweets_rich_text_read_enabled\": true, \"longform_notetweets_inline_media_enabled\": true, \"responsive_web_media_download_video_enabled\": false, \"responsive_web_enhance_cards_enabled\": false}",
    ): Response<GetFollowers200Response>

    /**
     *
     * get user list of following
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "iSicc7LrzWGBgDPL0tM_TQ")
     * @param variables  (default to "{\"userId\": \"44196397\", \"count\": 20, \"includePromotedContent\": false}")
     * @param features  (default to "{\"responsive_web_graphql_exclude_directive_enabled\": true, \"verified_phone_label_enabled\": false, \"creator_subscriptions_tweet_preview_api_enabled\": true, \"responsive_web_graphql_timeline_navigation_enabled\": true, \"responsive_web_graphql_skip_user_profile_image_extensions_enabled\": false, \"tweetypie_unmention_optimization_enabled\": true, \"responsive_web_edit_tweet_api_enabled\": true, \"graphql_is_translatable_rweb_tweet_is_translatable_enabled\": true, \"view_counts_everywhere_api_enabled\": true, \"longform_notetweets_consumption_enabled\": true, \"responsive_web_twitter_article_tweet_consumption_enabled\": false, \"tweet_awards_web_tipping_enabled\": false, \"freedom_of_speech_not_reach_fetch_enabled\": true, \"standardized_nudges_misinfo\": true, \"tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled\": true, \"longform_notetweets_rich_text_read_enabled\": true, \"longform_notetweets_inline_media_enabled\": true, \"responsive_web_media_download_video_enabled\": false, \"responsive_web_enhance_cards_enabled\": false}")
     * @return [GetFollowers200Response]
     */
    @GET("graphql/{pathQueryId}/Following")
    suspend fun getFollowing(
        @Path("pathQueryId") pathQueryId: kotlin.String = "iSicc7LrzWGBgDPL0tM_TQ",
        @Query("variables") variables: kotlin.String = "{\"userId\": \"44196397\", \"count\": 20, \"includePromotedContent\": false}",
        @Query(
            "features",
        ) features: kotlin.String = "{\"responsive_web_graphql_exclude_directive_enabled\": true, \"verified_phone_label_enabled\": false, \"creator_subscriptions_tweet_preview_api_enabled\": true, \"responsive_web_graphql_timeline_navigation_enabled\": true, \"responsive_web_graphql_skip_user_profile_image_extensions_enabled\": false, \"tweetypie_unmention_optimization_enabled\": true, \"responsive_web_edit_tweet_api_enabled\": true, \"graphql_is_translatable_rweb_tweet_is_translatable_enabled\": true, \"view_counts_everywhere_api_enabled\": true, \"longform_notetweets_consumption_enabled\": true, \"responsive_web_twitter_article_tweet_consumption_enabled\": false, \"tweet_awards_web_tipping_enabled\": false, \"freedom_of_speech_not_reach_fetch_enabled\": true, \"standardized_nudges_misinfo\": true, \"tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled\": true, \"longform_notetweets_rich_text_read_enabled\": true, \"longform_notetweets_inline_media_enabled\": true, \"responsive_web_media_download_video_enabled\": false, \"responsive_web_enhance_cards_enabled\": false}",
    ): Response<GetFollowers200Response>

    /**
     *
     * get tweet retweeters
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "9jBdme5U626ATWp01dvgrA")
     * @param variables  (default to "{\"tweetId\": \"1349129669258448897\", \"count\": 20, \"includePromotedContent\": true}")
     * @param features  (default to "{\"responsive_web_graphql_exclude_directive_enabled\": true, \"verified_phone_label_enabled\": false, \"creator_subscriptions_tweet_preview_api_enabled\": true, \"responsive_web_graphql_timeline_navigation_enabled\": true, \"responsive_web_graphql_skip_user_profile_image_extensions_enabled\": false, \"tweetypie_unmention_optimization_enabled\": true, \"responsive_web_edit_tweet_api_enabled\": true, \"graphql_is_translatable_rweb_tweet_is_translatable_enabled\": true, \"view_counts_everywhere_api_enabled\": true, \"longform_notetweets_consumption_enabled\": true, \"responsive_web_twitter_article_tweet_consumption_enabled\": false, \"tweet_awards_web_tipping_enabled\": false, \"freedom_of_speech_not_reach_fetch_enabled\": true, \"standardized_nudges_misinfo\": true, \"tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled\": true, \"longform_notetweets_rich_text_read_enabled\": true, \"longform_notetweets_inline_media_enabled\": true, \"responsive_web_media_download_video_enabled\": false, \"responsive_web_enhance_cards_enabled\": false}")
     * @return [GetRetweeters200Response]
     */
    @GET("graphql/{pathQueryId}/Retweeters")
    suspend fun getRetweeters(
        @Path("pathQueryId") pathQueryId: kotlin.String = "9jBdme5U626ATWp01dvgrA",
        @Query(
            "variables",
        ) variables: kotlin.String = "{\"tweetId\": \"1349129669258448897\", \"count\": 20, \"includePromotedContent\": true}",
        @Query(
            "features",
        ) features: kotlin.String = "{\"responsive_web_graphql_exclude_directive_enabled\": true, \"verified_phone_label_enabled\": false, \"creator_subscriptions_tweet_preview_api_enabled\": true, \"responsive_web_graphql_timeline_navigation_enabled\": true, \"responsive_web_graphql_skip_user_profile_image_extensions_enabled\": false, \"tweetypie_unmention_optimization_enabled\": true, \"responsive_web_edit_tweet_api_enabled\": true, \"graphql_is_translatable_rweb_tweet_is_translatable_enabled\": true, \"view_counts_everywhere_api_enabled\": true, \"longform_notetweets_consumption_enabled\": true, \"responsive_web_twitter_article_tweet_consumption_enabled\": false, \"tweet_awards_web_tipping_enabled\": false, \"freedom_of_speech_not_reach_fetch_enabled\": true, \"standardized_nudges_misinfo\": true, \"tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled\": true, \"longform_notetweets_rich_text_read_enabled\": true, \"longform_notetweets_inline_media_enabled\": true, \"responsive_web_media_download_video_enabled\": false, \"responsive_web_enhance_cards_enabled\": false}",
    ): Response<GetRetweeters200Response>
}
