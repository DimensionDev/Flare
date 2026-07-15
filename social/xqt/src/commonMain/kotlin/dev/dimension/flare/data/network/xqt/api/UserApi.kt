package dev.dimension.flare.data.network.xqt.api

import de.jensklingenberg.ktorfit.Response
import de.jensklingenberg.ktorfit.http.*
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Path
import de.jensklingenberg.ktorfit.http.Query
import dev.dimension.flare.data.network.xqt.model.GetUserByRestId200Response
import dev.dimension.flare.data.network.xqt.model.UserResponse

internal interface UserApi {
    /**
     * GET graphql/{pathQueryId}/UserByRestId
     *
     * get user by rest id
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "XIpMDIi_YoVzXeoON-cfAQ")
     * @param variables  (default to "{\"userId\": \"44196397\", \"withSafetyModeUserFields\": true}")
     * @param features  (default to "{\"hidden_profile_subscriptions_enabled\": true, \"payments_enabled\": false, \"profile_label_improvements_pcf_label_in_post_enabled\": true, \"responsive_web_profile_redirect_enabled\": false, \"rweb_tipjar_consumption_enabled\": true, \"verified_phone_label_enabled\": false, \"highlights_tweets_tab_ui_enabled\": true, \"responsive_web_twitter_article_notes_tab_enabled\": true, \"subscriptions_feature_can_gift_premium\": true, \"creator_subscriptions_tweet_preview_api_enabled\": true, \"responsive_web_graphql_skip_user_profile_image_extensions_enabled\": false, \"responsive_web_graphql_timeline_navigation_enabled\": true}")
     * @return [UserResponse]
     */
    @GET("graphql/{pathQueryId}/UserByRestId")
    suspend fun getUserByRestId(@Path("pathQueryId") pathQueryId: kotlin.String = "XIpMDIi_YoVzXeoON-cfAQ", @Query("variables") variables: kotlin.String = "{\"userId\": \"44196397\", \"withSafetyModeUserFields\": true}", @Query("features") features: kotlin.String = "{\"hidden_profile_subscriptions_enabled\": true, \"payments_enabled\": false, \"profile_label_improvements_pcf_label_in_post_enabled\": true, \"responsive_web_profile_redirect_enabled\": false, \"rweb_tipjar_consumption_enabled\": true, \"verified_phone_label_enabled\": false, \"highlights_tweets_tab_ui_enabled\": true, \"responsive_web_twitter_article_notes_tab_enabled\": true, \"subscriptions_feature_can_gift_premium\": true, \"creator_subscriptions_tweet_preview_api_enabled\": true, \"responsive_web_graphql_skip_user_profile_image_extensions_enabled\": false, \"responsive_web_graphql_timeline_navigation_enabled\": true}"): Response<GetUserByRestId200Response>

    /**
     * GET graphql/{pathQueryId}/UserByScreenName
     *
     * get user by screen name
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "IGgvgiOx4QZndDHuD3x9TQ")
     * @param variables  (default to "{\"screen_name\": \"elonmusk\"}")
     * @param features  (default to "{\"hidden_profile_subscriptions_enabled\": true, \"profile_label_improvements_pcf_label_in_post_enabled\": true, \"responsive_web_profile_redirect_enabled\": false, \"rweb_tipjar_consumption_enabled\": false, \"verified_phone_label_enabled\": false, \"subscriptions_verification_info_is_identity_verified_enabled\": true, \"subscriptions_verification_info_verified_since_enabled\": true, \"highlights_tweets_tab_ui_enabled\": true, \"responsive_web_twitter_article_notes_tab_enabled\": true, \"subscriptions_feature_can_gift_premium\": true, \"creator_subscriptions_tweet_preview_api_enabled\": true, \"responsive_web_graphql_skip_user_profile_image_extensions_enabled\": false, \"responsive_web_graphql_timeline_navigation_enabled\": true}")
     * @param fieldToggles  (default to "{\"withPayments\": false, \"withAuxiliaryUserLabels\": true}")
     * @return [UserResponse]
     */
    @GET("graphql/{pathQueryId}/UserByScreenName")
    suspend fun getUserByScreenName(@Path("pathQueryId") pathQueryId: kotlin.String = "IGgvgiOx4QZndDHuD3x9TQ", @Query("variables") variables: kotlin.String = "{\"screen_name\": \"elonmusk\"}", @Query("features") features: kotlin.String = "{\"hidden_profile_subscriptions_enabled\": true, \"profile_label_improvements_pcf_label_in_post_enabled\": true, \"responsive_web_profile_redirect_enabled\": false, \"rweb_tipjar_consumption_enabled\": false, \"verified_phone_label_enabled\": false, \"subscriptions_verification_info_is_identity_verified_enabled\": true, \"subscriptions_verification_info_verified_since_enabled\": true, \"highlights_tweets_tab_ui_enabled\": true, \"responsive_web_twitter_article_notes_tab_enabled\": true, \"subscriptions_feature_can_gift_premium\": true, \"creator_subscriptions_tweet_preview_api_enabled\": true, \"responsive_web_graphql_skip_user_profile_image_extensions_enabled\": false, \"responsive_web_graphql_timeline_navigation_enabled\": true}", @Query("fieldToggles") fieldToggles: kotlin.String = "{\"withPayments\": false, \"withAuxiliaryUserLabels\": true}"): Response<GetUserByRestId200Response>

}
