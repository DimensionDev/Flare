package dev.dimension.flare.data.network.xqt.api

import de.jensklingenberg.ktorfit.Response
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Header
import de.jensklingenberg.ktorfit.http.Path
import de.jensklingenberg.ktorfit.http.Query
import dev.dimension.flare.data.network.xqt.model.GetBookmarks200Response
import dev.dimension.flare.data.network.xqt.model.GetHomeLatestTimeline200Response
import dev.dimension.flare.data.network.xqt.model.GetLikes200Response
import dev.dimension.flare.data.network.xqt.model.GetListLatestTweetsTimeline200Response
import dev.dimension.flare.data.network.xqt.model.GetSearchTimeline200Response
import dev.dimension.flare.data.network.xqt.model.GetTweetDetail200Response
import dev.dimension.flare.data.network.xqt.model.GetUserHighlightsTweets200Response

internal interface TweetApi {
    /**
     *
     * get bookmarks
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "j5KExFXtSWj8HjRui17ydA")
     * @param variables  (default to "{\"count\": 20, \"includePromotedContent\": true}")
     * @param features  (default to "{\"graphql_timeline_v2_bookmark_timeline\": true, \"responsive_web_graphql_exclude_directive_enabled\": true, \"verified_phone_label_enabled\": false, \"creator_subscriptions_tweet_preview_api_enabled\": true, \"responsive_web_graphql_timeline_navigation_enabled\": true, \"responsive_web_graphql_skip_user_profile_image_extensions_enabled\": false, \"tweetypie_unmention_optimization_enabled\": true, \"responsive_web_edit_tweet_api_enabled\": true, \"graphql_is_translatable_rweb_tweet_is_translatable_enabled\": true, \"view_counts_everywhere_api_enabled\": true, \"longform_notetweets_consumption_enabled\": true, \"responsive_web_twitter_article_tweet_consumption_enabled\": false, \"tweet_awards_web_tipping_enabled\": false, \"freedom_of_speech_not_reach_fetch_enabled\": true, \"standardized_nudges_misinfo\": true, \"tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled\": true, \"longform_notetweets_rich_text_read_enabled\": true, \"longform_notetweets_inline_media_enabled\": true, \"responsive_web_media_download_video_enabled\": false, \"responsive_web_enhance_cards_enabled\": false}")
     * @return [GetBookmarks200Response]
     */
    @GET("graphql/{pathQueryId}/Bookmarks")
    suspend fun getBookmarks(
        @Path("pathQueryId") pathQueryId: kotlin.String = "j5KExFXtSWj8HjRui17ydA",
        @Query("variables") variables: kotlin.String = "{\"count\": 20, \"includePromotedContent\": true}",
        @Query(
            "features",
        ) features: kotlin.String = "{\"graphql_timeline_v2_bookmark_timeline\": true, \"responsive_web_graphql_exclude_directive_enabled\": true, \"verified_phone_label_enabled\": false, \"creator_subscriptions_tweet_preview_api_enabled\": true, \"responsive_web_graphql_timeline_navigation_enabled\": true, \"responsive_web_graphql_skip_user_profile_image_extensions_enabled\": false, \"tweetypie_unmention_optimization_enabled\": true, \"responsive_web_edit_tweet_api_enabled\": true, \"graphql_is_translatable_rweb_tweet_is_translatable_enabled\": true, \"view_counts_everywhere_api_enabled\": true, \"longform_notetweets_consumption_enabled\": true, \"responsive_web_twitter_article_tweet_consumption_enabled\": false, \"tweet_awards_web_tipping_enabled\": false, \"freedom_of_speech_not_reach_fetch_enabled\": true, \"standardized_nudges_misinfo\": true, \"tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled\": true, \"longform_notetweets_rich_text_read_enabled\": true, \"longform_notetweets_inline_media_enabled\": true, \"responsive_web_media_download_video_enabled\": false, \"responsive_web_enhance_cards_enabled\": false}",
    ): Response<GetBookmarks200Response>

    /**
     *
     * get tweet list of timeline
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "fKbuCe1XHAqSM99T6q-MOg")
     * @param variables  (default to "{\"count\": 20, \"includePromotedContent\": true, \"latestControlAvailable\": true, \"requestContext\": \"launch\"}")
     * @param features  (default to "{\"responsive_web_graphql_exclude_directive_enabled\": true, \"verified_phone_label_enabled\": false, \"creator_subscriptions_tweet_preview_api_enabled\": true, \"responsive_web_graphql_timeline_navigation_enabled\": true, \"responsive_web_graphql_skip_user_profile_image_extensions_enabled\": false, \"tweetypie_unmention_optimization_enabled\": true, \"responsive_web_edit_tweet_api_enabled\": true, \"graphql_is_translatable_rweb_tweet_is_translatable_enabled\": true, \"view_counts_everywhere_api_enabled\": true, \"longform_notetweets_consumption_enabled\": true, \"responsive_web_twitter_article_tweet_consumption_enabled\": false, \"tweet_awards_web_tipping_enabled\": false, \"freedom_of_speech_not_reach_fetch_enabled\": true, \"standardized_nudges_misinfo\": true, \"tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled\": true, \"longform_notetweets_rich_text_read_enabled\": true, \"longform_notetweets_inline_media_enabled\": true, \"responsive_web_media_download_video_enabled\": false, \"responsive_web_enhance_cards_enabled\": false}")
     * @return [GetHomeLatestTimeline200Response]
     */
    @GET("graphql/{pathQueryId}/HomeLatestTimeline")
    suspend fun getHomeLatestTimeline(
        @Path("pathQueryId") pathQueryId: kotlin.String = "fKbuCe1XHAqSM99T6q-MOg",
        @Query(
            "variables",
        ) variables: kotlin.String = "{\"count\": 20, \"includePromotedContent\": true, \"latestControlAvailable\": true, \"requestContext\": \"launch\"}",
        @Query(
            "features",
        ) features: kotlin.String = "{\"responsive_web_graphql_exclude_directive_enabled\": true, \"verified_phone_label_enabled\": false, \"creator_subscriptions_tweet_preview_api_enabled\": true, \"responsive_web_graphql_timeline_navigation_enabled\": true, \"responsive_web_graphql_skip_user_profile_image_extensions_enabled\": false, \"tweetypie_unmention_optimization_enabled\": true, \"responsive_web_edit_tweet_api_enabled\": true, \"graphql_is_translatable_rweb_tweet_is_translatable_enabled\": true, \"view_counts_everywhere_api_enabled\": true, \"longform_notetweets_consumption_enabled\": true, \"responsive_web_twitter_article_tweet_consumption_enabled\": false, \"tweet_awards_web_tipping_enabled\": false, \"freedom_of_speech_not_reach_fetch_enabled\": true, \"standardized_nudges_misinfo\": true, \"tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled\": true, \"longform_notetweets_rich_text_read_enabled\": true, \"longform_notetweets_inline_media_enabled\": true, \"responsive_web_media_download_video_enabled\": false, \"responsive_web_enhance_cards_enabled\": false}",
    ): Response<GetHomeLatestTimeline200Response>

    /**
     *
     * get tweet list of timeline
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "vd1SSLv05a4lAc9-ml4kpA")
     * @param variables  (default to "{\"count\": 20, \"includePromotedContent\": true, \"latestControlAvailable\": true, \"requestContext\": \"launch\", \"seenTweetIds\": [\"1349129669258448897\"], \"withCommunity\": true}")
     * @param features  (default to "{\"responsive_web_graphql_exclude_directive_enabled\": true, \"verified_phone_label_enabled\": false, \"creator_subscriptions_tweet_preview_api_enabled\": true, \"responsive_web_graphql_timeline_navigation_enabled\": true, \"responsive_web_graphql_skip_user_profile_image_extensions_enabled\": false, \"tweetypie_unmention_optimization_enabled\": true, \"responsive_web_edit_tweet_api_enabled\": true, \"graphql_is_translatable_rweb_tweet_is_translatable_enabled\": true, \"view_counts_everywhere_api_enabled\": true, \"longform_notetweets_consumption_enabled\": true, \"responsive_web_twitter_article_tweet_consumption_enabled\": false, \"tweet_awards_web_tipping_enabled\": false, \"freedom_of_speech_not_reach_fetch_enabled\": true, \"standardized_nudges_misinfo\": true, \"tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled\": true, \"longform_notetweets_rich_text_read_enabled\": true, \"longform_notetweets_inline_media_enabled\": true, \"responsive_web_media_download_video_enabled\": false, \"responsive_web_enhance_cards_enabled\": false}")
     * @return [GetHomeLatestTimeline200Response]
     */
    @GET("graphql/{pathQueryId}/HomeTimeline")
    suspend fun getHomeTimeline(
        @Path("pathQueryId") pathQueryId: kotlin.String = "vd1SSLv05a4lAc9-ml4kpA",
        @Query(
            "variables",
        ) variables: kotlin.String = "{\"count\": 20, \"includePromotedContent\": true, \"latestControlAvailable\": true, \"requestContext\": \"launch\", \"seenTweetIds\": [\"1349129669258448897\"], \"withCommunity\": true}",
        @Query(
            "features",
        ) features: kotlin.String = "{\"responsive_web_graphql_exclude_directive_enabled\": true, \"verified_phone_label_enabled\": false, \"creator_subscriptions_tweet_preview_api_enabled\": true, \"responsive_web_graphql_timeline_navigation_enabled\": true, \"responsive_web_graphql_skip_user_profile_image_extensions_enabled\": false, \"tweetypie_unmention_optimization_enabled\": true, \"responsive_web_edit_tweet_api_enabled\": true, \"graphql_is_translatable_rweb_tweet_is_translatable_enabled\": true, \"view_counts_everywhere_api_enabled\": true, \"longform_notetweets_consumption_enabled\": true, \"responsive_web_twitter_article_tweet_consumption_enabled\": false, \"tweet_awards_web_tipping_enabled\": false, \"freedom_of_speech_not_reach_fetch_enabled\": true, \"standardized_nudges_misinfo\": true, \"tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled\": true, \"longform_notetweets_rich_text_read_enabled\": true, \"longform_notetweets_inline_media_enabled\": true, \"responsive_web_media_download_video_enabled\": false, \"responsive_web_enhance_cards_enabled\": false}",
    ): Response<GetHomeLatestTimeline200Response>

    /**
     *
     * get user likes tweets
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "lVf2NuhLoYVrpN4nO7uw0Q")
     * @param variables  (default to "{\"userId\": \"44196397\", \"count\": 20, \"includePromotedContent\": false, \"withClientEventToken\": false, \"withBirdwatchNotes\": false, \"withVoice\": true, \"withV2Timeline\": true}")
     * @param features  (default to "{\"responsive_web_graphql_exclude_directive_enabled\": true, \"verified_phone_label_enabled\": false, \"creator_subscriptions_tweet_preview_api_enabled\": true, \"responsive_web_graphql_timeline_navigation_enabled\": true, \"responsive_web_graphql_skip_user_profile_image_extensions_enabled\": false, \"tweetypie_unmention_optimization_enabled\": true, \"responsive_web_edit_tweet_api_enabled\": true, \"graphql_is_translatable_rweb_tweet_is_translatable_enabled\": true, \"view_counts_everywhere_api_enabled\": true, \"longform_notetweets_consumption_enabled\": true, \"responsive_web_twitter_article_tweet_consumption_enabled\": false, \"tweet_awards_web_tipping_enabled\": false, \"freedom_of_speech_not_reach_fetch_enabled\": true, \"standardized_nudges_misinfo\": true, \"tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled\": true, \"longform_notetweets_rich_text_read_enabled\": true, \"longform_notetweets_inline_media_enabled\": true, \"responsive_web_media_download_video_enabled\": false, \"responsive_web_enhance_cards_enabled\": false}")
     * @return [GetLikes200Response]
     */
    @GET("graphql/{pathQueryId}/Likes")
    suspend fun getLikes(
        @Path("pathQueryId") pathQueryId: kotlin.String = "lVf2NuhLoYVrpN4nO7uw0Q",
        @Query(
            "variables",
        ) variables: kotlin.String = "{\"userId\": \"44196397\", \"count\": 20, \"includePromotedContent\": false, \"withClientEventToken\": false, \"withBirdwatchNotes\": false, \"withVoice\": true, \"withV2Timeline\": true}",
        @Query(
            "features",
        ) features: kotlin.String = "{\"responsive_web_graphql_exclude_directive_enabled\": true, \"verified_phone_label_enabled\": false, \"creator_subscriptions_tweet_preview_api_enabled\": true, \"responsive_web_graphql_timeline_navigation_enabled\": true, \"responsive_web_graphql_skip_user_profile_image_extensions_enabled\": false, \"tweetypie_unmention_optimization_enabled\": true, \"responsive_web_edit_tweet_api_enabled\": true, \"graphql_is_translatable_rweb_tweet_is_translatable_enabled\": true, \"view_counts_everywhere_api_enabled\": true, \"longform_notetweets_consumption_enabled\": true, \"responsive_web_twitter_article_tweet_consumption_enabled\": false, \"tweet_awards_web_tipping_enabled\": false, \"freedom_of_speech_not_reach_fetch_enabled\": true, \"standardized_nudges_misinfo\": true, \"tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled\": true, \"longform_notetweets_rich_text_read_enabled\": true, \"longform_notetweets_inline_media_enabled\": true, \"responsive_web_media_download_video_enabled\": false, \"responsive_web_enhance_cards_enabled\": false}",
    ): Response<GetLikes200Response>

    /**
     *
     * get tweet list of timeline
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "qHgwF5h2HLowIJ6dHmAP_A")
     * @param variables  (default to "{\"listId\": \"1539453138322673664\", \"count\": 20}")
     * @param features  (default to "{\"responsive_web_graphql_exclude_directive_enabled\": true, \"verified_phone_label_enabled\": false, \"creator_subscriptions_tweet_preview_api_enabled\": true, \"responsive_web_graphql_timeline_navigation_enabled\": true, \"responsive_web_graphql_skip_user_profile_image_extensions_enabled\": false, \"tweetypie_unmention_optimization_enabled\": true, \"responsive_web_edit_tweet_api_enabled\": true, \"graphql_is_translatable_rweb_tweet_is_translatable_enabled\": true, \"view_counts_everywhere_api_enabled\": true, \"longform_notetweets_consumption_enabled\": true, \"responsive_web_twitter_article_tweet_consumption_enabled\": false, \"tweet_awards_web_tipping_enabled\": false, \"freedom_of_speech_not_reach_fetch_enabled\": true, \"standardized_nudges_misinfo\": true, \"tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled\": true, \"longform_notetweets_rich_text_read_enabled\": true, \"longform_notetweets_inline_media_enabled\": true, \"responsive_web_media_download_video_enabled\": false, \"responsive_web_enhance_cards_enabled\": false}")
     * @return [GetListLatestTweetsTimeline200Response]
     */
    @GET("graphql/{pathQueryId}/ListLatestTweetsTimeline")
    suspend fun getListLatestTweetsTimeline(
        @Path("pathQueryId") pathQueryId: kotlin.String = "qHgwF5h2HLowIJ6dHmAP_A",
        @Query("variables") variables: kotlin.String = "{\"listId\": \"1539453138322673664\", \"count\": 20}",
        @Query(
            "features",
        ) features: kotlin.String = "{\"responsive_web_graphql_exclude_directive_enabled\": true, \"verified_phone_label_enabled\": false, \"creator_subscriptions_tweet_preview_api_enabled\": true, \"responsive_web_graphql_timeline_navigation_enabled\": true, \"responsive_web_graphql_skip_user_profile_image_extensions_enabled\": false, \"tweetypie_unmention_optimization_enabled\": true, \"responsive_web_edit_tweet_api_enabled\": true, \"graphql_is_translatable_rweb_tweet_is_translatable_enabled\": true, \"view_counts_everywhere_api_enabled\": true, \"longform_notetweets_consumption_enabled\": true, \"responsive_web_twitter_article_tweet_consumption_enabled\": false, \"tweet_awards_web_tipping_enabled\": false, \"freedom_of_speech_not_reach_fetch_enabled\": true, \"standardized_nudges_misinfo\": true, \"tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled\": true, \"longform_notetweets_rich_text_read_enabled\": true, \"longform_notetweets_inline_media_enabled\": true, \"responsive_web_media_download_video_enabled\": false, \"responsive_web_enhance_cards_enabled\": false}",
    ): Response<GetListLatestTweetsTimeline200Response>

    /**
     *
     * search tweet list. product:[Top, Latest, People, Photos, Videos]
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "3Ej-6N7xXONuEp5eJa1TdQ")
     * @param variables  (default to "{\"rawQuery\": \"elonmusk\", \"count\": 20, \"querySource\": \"typed_query\", \"product\": \"Top\"}")
     * @param features  (default to "{\"responsive_web_graphql_exclude_directive_enabled\": true, \"verified_phone_label_enabled\": false, \"creator_subscriptions_tweet_preview_api_enabled\": true, \"responsive_web_graphql_timeline_navigation_enabled\": true, \"responsive_web_graphql_skip_user_profile_image_extensions_enabled\": false, \"tweetypie_unmention_optimization_enabled\": true, \"responsive_web_edit_tweet_api_enabled\": true, \"graphql_is_translatable_rweb_tweet_is_translatable_enabled\": true, \"view_counts_everywhere_api_enabled\": true, \"longform_notetweets_consumption_enabled\": true, \"responsive_web_twitter_article_tweet_consumption_enabled\": false, \"tweet_awards_web_tipping_enabled\": false, \"freedom_of_speech_not_reach_fetch_enabled\": true, \"standardized_nudges_misinfo\": true, \"tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled\": true, \"longform_notetweets_rich_text_read_enabled\": true, \"longform_notetweets_inline_media_enabled\": true, \"responsive_web_media_download_video_enabled\": false, \"responsive_web_enhance_cards_enabled\": false}")
     * @return [GetSearchTimeline200Response]
     */
    @GET("graphql/{pathQueryId}/SearchTimeline")
    suspend fun getSearchTimeline(
        @Path("pathQueryId") pathQueryId: kotlin.String = "AIdc203rPpK_k_2KWSdm7g",
        @Query(
            "variables",
        ) variables: kotlin.String = "{\"rawQuery\": \"elonmusk\", \"count\": 20, \"querySource\": \"typed_query\", \"product\": \"Top\"}",
        @Query(
            "features",
        ) features: kotlin.String = "{\"rweb_video_screen_enabled\":false,\"profile_label_improvements_pcf_label_in_post_enabled\":true,\"rweb_tipjar_consumption_enabled\":true,\"verified_phone_label_enabled\":false,\"creator_subscriptions_tweet_preview_api_enabled\":true,\"responsive_web_graphql_timeline_navigation_enabled\":true,\"responsive_web_graphql_skip_user_profile_image_extensions_enabled\":false,\"premium_content_api_read_enabled\":false,\"communities_web_enable_tweet_community_results_fetch\":true,\"c9s_tweet_anatomy_moderator_badge_enabled\":true,\"responsive_web_grok_analyze_button_fetch_trends_enabled\":false,\"responsive_web_grok_analyze_post_followups_enabled\":true,\"responsive_web_jetfuel_frame\":false,\"responsive_web_grok_share_attachment_enabled\":true,\"articles_preview_enabled\":true,\"responsive_web_edit_tweet_api_enabled\":true,\"graphql_is_translatable_rweb_tweet_is_translatable_enabled\":true,\"view_counts_everywhere_api_enabled\":true,\"longform_notetweets_consumption_enabled\":true,\"responsive_web_twitter_article_tweet_consumption_enabled\":true,\"tweet_awards_web_tipping_enabled\":false,\"responsive_web_grok_show_grok_translated_post\":false,\"responsive_web_grok_analysis_button_from_backend\":false,\"creator_subscriptions_quote_tweet_preview_enabled\":false,\"freedom_of_speech_not_reach_fetch_enabled\":true,\"standardized_nudges_misinfo\":true,\"tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled\":true,\"longform_notetweets_rich_text_read_enabled\":true,\"longform_notetweets_inline_media_enabled\":true,\"responsive_web_grok_image_annotation_enabled\":true,\"responsive_web_enhance_cards_enabled\":false}",
        @Header("Referer")
        referer: String,
    ): Response<GetSearchTimeline200Response>

    /**
     *
     * get TweetDetail
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "xOhkmRac04YFZmOzU9PJHg")
     * @param variables  (default to "{\"focalTweetId\": \"1349129669258448897\", \"with_rux_injections\": false, \"includePromotedContent\": true, \"withCommunity\": true, \"withQuickPromoteEligibilityTweetFields\": true, \"withBirdwatchNotes\": true, \"withVoice\": true, \"withV2Timeline\": true}")
     * @param features  (default to "{\"responsive_web_graphql_exclude_directive_enabled\": true, \"verified_phone_label_enabled\": false, \"creator_subscriptions_tweet_preview_api_enabled\": true, \"responsive_web_graphql_timeline_navigation_enabled\": true, \"responsive_web_graphql_skip_user_profile_image_extensions_enabled\": false, \"tweetypie_unmention_optimization_enabled\": true, \"responsive_web_edit_tweet_api_enabled\": true, \"graphql_is_translatable_rweb_tweet_is_translatable_enabled\": true, \"view_counts_everywhere_api_enabled\": true, \"longform_notetweets_consumption_enabled\": true, \"responsive_web_twitter_article_tweet_consumption_enabled\": false, \"tweet_awards_web_tipping_enabled\": false, \"freedom_of_speech_not_reach_fetch_enabled\": true, \"standardized_nudges_misinfo\": true, \"tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled\": true, \"longform_notetweets_rich_text_read_enabled\": true, \"longform_notetweets_inline_media_enabled\": true, \"responsive_web_media_download_video_enabled\": false, \"responsive_web_enhance_cards_enabled\": false}")
     * @param fieldToggles  (default to "{\"withArticleRichContentState\": false}")
     * @return [GetTweetDetail200Response]
     */
    @GET("graphql/{pathQueryId}/TweetDetail")
    suspend fun getTweetDetail(
        @Path("pathQueryId") pathQueryId: kotlin.String = "_8aYOgEDz35BrBcBal1-_w",
        @Query(
            "variables",
        ) variables: kotlin.String = "{\"focalTweetId\": \"1349129669258448897\", \"with_rux_injections\": false, \"includePromotedContent\": true, \"withCommunity\": true, \"withQuickPromoteEligibilityTweetFields\": true, \"withBirdwatchNotes\": true, \"withVoice\": true, \"withV2Timeline\": true}",
        @Query(
            "features",
        ) features: kotlin.String = "{\"rweb_video_screen_enabled\":false,\"profile_label_improvements_pcf_label_in_post_enabled\":true,\"rweb_tipjar_consumption_enabled\":true,\"verified_phone_label_enabled\":false,\"creator_subscriptions_tweet_preview_api_enabled\":true,\"responsive_web_graphql_timeline_navigation_enabled\":true,\"responsive_web_graphql_skip_user_profile_image_extensions_enabled\":false,\"premium_content_api_read_enabled\":false,\"communities_web_enable_tweet_community_results_fetch\":true,\"c9s_tweet_anatomy_moderator_badge_enabled\":true,\"responsive_web_grok_analyze_button_fetch_trends_enabled\":false,\"responsive_web_grok_analyze_post_followups_enabled\":true,\"responsive_web_jetfuel_frame\":false,\"responsive_web_grok_share_attachment_enabled\":true,\"articles_preview_enabled\":true,\"responsive_web_edit_tweet_api_enabled\":true,\"graphql_is_translatable_rweb_tweet_is_translatable_enabled\":true,\"view_counts_everywhere_api_enabled\":true,\"longform_notetweets_consumption_enabled\":true,\"responsive_web_twitter_article_tweet_consumption_enabled\":true,\"tweet_awards_web_tipping_enabled\":false,\"responsive_web_grok_show_grok_translated_post\":false,\"responsive_web_grok_analysis_button_from_backend\":false,\"creator_subscriptions_quote_tweet_preview_enabled\":false,\"freedom_of_speech_not_reach_fetch_enabled\":true,\"standardized_nudges_misinfo\":true,\"tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled\":true,\"longform_notetweets_rich_text_read_enabled\":true,\"longform_notetweets_inline_media_enabled\":true,\"responsive_web_grok_image_annotation_enabled\":true,\"responsive_web_enhance_cards_enabled\":false}",
        @Query(
            "fieldToggles",
        ) fieldToggles: kotlin.String = "{\"withArticleRichContentState\":true,\"withArticlePlainText\":false,\"withGrokAnalyze\":false,\"withDisallowedReplyControls\":false}",
    ): Response<GetTweetDetail200Response>

    /**
     *
     * get user highlights tweets
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "KTtT5_kU8yor3I3UI4G5Vw")
     * @param variables  (default to "{\"userId\": \"44196397\", \"count\": 40, \"includePromotedContent\": true, \"withVoice\": true}")
     * @param features  (default to "{\"responsive_web_graphql_exclude_directive_enabled\": true, \"verified_phone_label_enabled\": false, \"creator_subscriptions_tweet_preview_api_enabled\": true, \"responsive_web_graphql_timeline_navigation_enabled\": true, \"responsive_web_graphql_skip_user_profile_image_extensions_enabled\": false, \"tweetypie_unmention_optimization_enabled\": true, \"responsive_web_edit_tweet_api_enabled\": true, \"graphql_is_translatable_rweb_tweet_is_translatable_enabled\": true, \"view_counts_everywhere_api_enabled\": true, \"longform_notetweets_consumption_enabled\": true, \"responsive_web_twitter_article_tweet_consumption_enabled\": false, \"tweet_awards_web_tipping_enabled\": false, \"freedom_of_speech_not_reach_fetch_enabled\": true, \"standardized_nudges_misinfo\": true, \"tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled\": true, \"longform_notetweets_rich_text_read_enabled\": true, \"longform_notetweets_inline_media_enabled\": true, \"responsive_web_media_download_video_enabled\": false, \"responsive_web_enhance_cards_enabled\": false}")
     * @return [GetUserHighlightsTweets200Response]
     */
    @GET("graphql/{pathQueryId}/UserHighlightsTweets")
    suspend fun getUserHighlightsTweets(
        @Path("pathQueryId") pathQueryId: kotlin.String = "KTtT5_kU8yor3I3UI4G5Vw",
        @Query(
            "variables",
        ) variables: kotlin.String = "{\"userId\": \"44196397\", \"count\": 40, \"includePromotedContent\": true, \"withVoice\": true}",
        @Query(
            "features",
        ) features: kotlin.String = "{\"responsive_web_graphql_exclude_directive_enabled\": true, \"verified_phone_label_enabled\": false, \"creator_subscriptions_tweet_preview_api_enabled\": true, \"responsive_web_graphql_timeline_navigation_enabled\": true, \"responsive_web_graphql_skip_user_profile_image_extensions_enabled\": false, \"tweetypie_unmention_optimization_enabled\": true, \"responsive_web_edit_tweet_api_enabled\": true, \"graphql_is_translatable_rweb_tweet_is_translatable_enabled\": true, \"view_counts_everywhere_api_enabled\": true, \"longform_notetweets_consumption_enabled\": true, \"responsive_web_twitter_article_tweet_consumption_enabled\": false, \"tweet_awards_web_tipping_enabled\": false, \"freedom_of_speech_not_reach_fetch_enabled\": true, \"standardized_nudges_misinfo\": true, \"tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled\": true, \"longform_notetweets_rich_text_read_enabled\": true, \"longform_notetweets_inline_media_enabled\": true, \"responsive_web_media_download_video_enabled\": false, \"responsive_web_enhance_cards_enabled\": false}",
    ): Response<GetUserHighlightsTweets200Response>

    /**
     *
     * get user media tweets
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "Le6KlbilFmSu-5VltFND-Q")
     * @param variables  (default to "{\"userId\": \"44196397\", \"count\": 40, \"includePromotedContent\": false, \"withClientEventToken\": false, \"withBirdwatchNotes\": false, \"withVoice\": true, \"withV2Timeline\": true}")
     * @param features  (default to "{\"responsive_web_graphql_exclude_directive_enabled\": true, \"verified_phone_label_enabled\": false, \"creator_subscriptions_tweet_preview_api_enabled\": true, \"responsive_web_graphql_timeline_navigation_enabled\": true, \"responsive_web_graphql_skip_user_profile_image_extensions_enabled\": false, \"tweetypie_unmention_optimization_enabled\": true, \"responsive_web_edit_tweet_api_enabled\": true, \"graphql_is_translatable_rweb_tweet_is_translatable_enabled\": true, \"view_counts_everywhere_api_enabled\": true, \"longform_notetweets_consumption_enabled\": true, \"responsive_web_twitter_article_tweet_consumption_enabled\": false, \"tweet_awards_web_tipping_enabled\": false, \"freedom_of_speech_not_reach_fetch_enabled\": true, \"standardized_nudges_misinfo\": true, \"tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled\": true, \"longform_notetweets_rich_text_read_enabled\": true, \"longform_notetweets_inline_media_enabled\": true, \"responsive_web_media_download_video_enabled\": false, \"responsive_web_enhance_cards_enabled\": false}")
     * @return [GetLikes200Response]
     */
    @GET("graphql/{pathQueryId}/UserMedia")
    suspend fun getUserMedia(
        @Path("pathQueryId") pathQueryId: kotlin.String = "Le6KlbilFmSu-5VltFND-Q",
        @Query(
            "variables",
        ) variables: kotlin.String = "{\"userId\": \"44196397\", \"count\": 40, \"includePromotedContent\": false, \"withClientEventToken\": false, \"withBirdwatchNotes\": false, \"withVoice\": true, \"withV2Timeline\": true}",
        @Query(
            "features",
        ) features: kotlin.String = "{\"responsive_web_graphql_exclude_directive_enabled\": true, \"verified_phone_label_enabled\": false, \"creator_subscriptions_tweet_preview_api_enabled\": true, \"responsive_web_graphql_timeline_navigation_enabled\": true, \"responsive_web_graphql_skip_user_profile_image_extensions_enabled\": false, \"tweetypie_unmention_optimization_enabled\": true, \"responsive_web_edit_tweet_api_enabled\": true, \"graphql_is_translatable_rweb_tweet_is_translatable_enabled\": true, \"view_counts_everywhere_api_enabled\": true, \"longform_notetweets_consumption_enabled\": true, \"responsive_web_twitter_article_tweet_consumption_enabled\": false, \"tweet_awards_web_tipping_enabled\": false, \"freedom_of_speech_not_reach_fetch_enabled\": true, \"standardized_nudges_misinfo\": true, \"tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled\": true, \"longform_notetweets_rich_text_read_enabled\": true, \"longform_notetweets_inline_media_enabled\": true, \"responsive_web_media_download_video_enabled\": false, \"responsive_web_enhance_cards_enabled\": false}",
    ): Response<GetLikes200Response>

    /**
     *
     * get user tweets
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "H8OOoI-5ZE4NxgRr8lfyWg")
     * @param variables  (default to "{\"userId\": \"44196397\", \"count\": 40, \"includePromotedContent\": true, \"withQuickPromoteEligibilityTweetFields\": true, \"withVoice\": true, \"withV2Timeline\": true}")
     * @param features  (default to "{\"responsive_web_graphql_exclude_directive_enabled\": true, \"verified_phone_label_enabled\": false, \"creator_subscriptions_tweet_preview_api_enabled\": true, \"responsive_web_graphql_timeline_navigation_enabled\": true, \"responsive_web_graphql_skip_user_profile_image_extensions_enabled\": false, \"tweetypie_unmention_optimization_enabled\": true, \"responsive_web_edit_tweet_api_enabled\": true, \"graphql_is_translatable_rweb_tweet_is_translatable_enabled\": true, \"view_counts_everywhere_api_enabled\": true, \"longform_notetweets_consumption_enabled\": true, \"responsive_web_twitter_article_tweet_consumption_enabled\": false, \"tweet_awards_web_tipping_enabled\": false, \"freedom_of_speech_not_reach_fetch_enabled\": true, \"standardized_nudges_misinfo\": true, \"tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled\": true, \"longform_notetweets_rich_text_read_enabled\": true, \"longform_notetweets_inline_media_enabled\": true, \"responsive_web_media_download_video_enabled\": false, \"responsive_web_enhance_cards_enabled\": false}")
     * @return [GetLikes200Response]
     */
    @GET("graphql/{pathQueryId}/UserTweets")
    suspend fun getUserTweets(
        @Path("pathQueryId") pathQueryId: kotlin.String = "H8OOoI-5ZE4NxgRr8lfyWg",
        @Query(
            "variables",
        ) variables: kotlin.String = "{\"userId\": \"44196397\", \"count\": 40, \"includePromotedContent\": true, \"withQuickPromoteEligibilityTweetFields\": true, \"withVoice\": true, \"withV2Timeline\": true}",
        @Query(
            "features",
        ) features: kotlin.String = "{\"responsive_web_graphql_exclude_directive_enabled\": true, \"verified_phone_label_enabled\": false, \"creator_subscriptions_tweet_preview_api_enabled\": true, \"responsive_web_graphql_timeline_navigation_enabled\": true, \"responsive_web_graphql_skip_user_profile_image_extensions_enabled\": false, \"tweetypie_unmention_optimization_enabled\": true, \"responsive_web_edit_tweet_api_enabled\": true, \"graphql_is_translatable_rweb_tweet_is_translatable_enabled\": true, \"view_counts_everywhere_api_enabled\": true, \"longform_notetweets_consumption_enabled\": true, \"responsive_web_twitter_article_tweet_consumption_enabled\": false, \"tweet_awards_web_tipping_enabled\": false, \"freedom_of_speech_not_reach_fetch_enabled\": true, \"standardized_nudges_misinfo\": true, \"tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled\": true, \"longform_notetweets_rich_text_read_enabled\": true, \"longform_notetweets_inline_media_enabled\": true, \"responsive_web_media_download_video_enabled\": false, \"responsive_web_enhance_cards_enabled\": false}",
    ): Response<GetLikes200Response>

    /**
     *
     * get user replies tweets
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "pz0IHaV_t7T4HJavqqqcIA")
     * @param variables  (default to "{\"userId\": \"44196397\", \"count\": 40, \"includePromotedContent\": true, \"withCommunity\": true, \"withVoice\": true, \"withV2Timeline\": true}")
     * @param features  (default to "{\"responsive_web_graphql_exclude_directive_enabled\": true, \"verified_phone_label_enabled\": false, \"creator_subscriptions_tweet_preview_api_enabled\": true, \"responsive_web_graphql_timeline_navigation_enabled\": true, \"responsive_web_graphql_skip_user_profile_image_extensions_enabled\": false, \"tweetypie_unmention_optimization_enabled\": true, \"responsive_web_edit_tweet_api_enabled\": true, \"graphql_is_translatable_rweb_tweet_is_translatable_enabled\": true, \"view_counts_everywhere_api_enabled\": true, \"longform_notetweets_consumption_enabled\": true, \"responsive_web_twitter_article_tweet_consumption_enabled\": false, \"tweet_awards_web_tipping_enabled\": false, \"freedom_of_speech_not_reach_fetch_enabled\": true, \"standardized_nudges_misinfo\": true, \"tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled\": true, \"longform_notetweets_rich_text_read_enabled\": true, \"longform_notetweets_inline_media_enabled\": true, \"responsive_web_media_download_video_enabled\": false, \"responsive_web_enhance_cards_enabled\": false}")
     * @return [GetLikes200Response]
     */
    @GET("graphql/{pathQueryId}/UserTweetsAndReplies")
    suspend fun getUserTweetsAndReplies(
        @Path("pathQueryId") pathQueryId: kotlin.String = "pz0IHaV_t7T4HJavqqqcIA",
        @Query(
            "variables",
        ) variables: kotlin.String = "{\"userId\": \"44196397\", \"count\": 40, \"includePromotedContent\": true, \"withCommunity\": true, \"withVoice\": true, \"withV2Timeline\": true}",
        @Query(
            "features",
        ) features: kotlin.String = "{\"rweb_video_screen_enabled\":false,\"profile_label_improvements_pcf_label_in_post_enabled\":true,\"rweb_tipjar_consumption_enabled\":true,\"responsive_web_graphql_exclude_directive_enabled\":true,\"verified_phone_label_enabled\":false,\"creator_subscriptions_tweet_preview_api_enabled\":true,\"responsive_web_graphql_timeline_navigation_enabled\":true,\"responsive_web_graphql_skip_user_profile_image_extensions_enabled\":false,\"premium_content_api_read_enabled\":false,\"communities_web_enable_tweet_community_results_fetch\":true,\"c9s_tweet_anatomy_moderator_badge_enabled\":true,\"responsive_web_grok_analyze_button_fetch_trends_enabled\":false,\"responsive_web_grok_analyze_post_followups_enabled\":true,\"responsive_web_jetfuel_frame\":false,\"responsive_web_grok_share_attachment_enabled\":true,\"articles_preview_enabled\":true,\"responsive_web_edit_tweet_api_enabled\":true,\"graphql_is_translatable_rweb_tweet_is_translatable_enabled\":true,\"view_counts_everywhere_api_enabled\":true,\"longform_notetweets_consumption_enabled\":true,\"responsive_web_twitter_article_tweet_consumption_enabled\":true,\"tweet_awards_web_tipping_enabled\":false,\"responsive_web_grok_show_grok_translated_post\":false,\"responsive_web_grok_analysis_button_from_backend\":false,\"creator_subscriptions_quote_tweet_preview_enabled\":false,\"freedom_of_speech_not_reach_fetch_enabled\":true,\"standardized_nudges_misinfo\":true,\"tweet_with_visibility_results_prefer_gql_limited_actions_policy_enabled\":true,\"longform_notetweets_rich_text_read_enabled\":true,\"longform_notetweets_inline_media_enabled\":true,\"responsive_web_grok_image_annotation_enabled\":true,\"responsive_web_enhance_cards_enabled\":false}",
        @Query("fieldToggles")
        fieldToggles: kotlin.String = "{\"withArticlePlainText\":false}",
    ): Response<GetLikes200Response>
}
