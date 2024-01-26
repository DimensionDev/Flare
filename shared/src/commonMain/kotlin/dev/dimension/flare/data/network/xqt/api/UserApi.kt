package dev.dimension.flare.data.network.xqt.api

import de.jensklingenberg.ktorfit.Response
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Path
import de.jensklingenberg.ktorfit.http.Query
import dev.dimension.flare.data.network.xqt.model.GetUserByRestId200Response

internal interface UserApi {
    /**
     *
     * get user by rest id
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "QdS5LJDl99iL_KUzckdfNQ")
     * @param variables  (default to "{\"userId\": \"44196397\", \"withSafetyModeUserFields\": true}")
     * @param features  (default to "{\"hidden_profile_likes_enabled\": true, \"hidden_profile_subscriptions_enabled\": true, \"responsive_web_graphql_exclude_directive_enabled\": true, \"verified_phone_label_enabled\": false, \"highlights_tweets_tab_ui_enabled\": true, \"creator_subscriptions_tweet_preview_api_enabled\": true, \"responsive_web_graphql_skip_user_profile_image_extensions_enabled\": false, \"responsive_web_graphql_timeline_navigation_enabled\": true}")
     * @return [GetUserByRestId200Response]
     */
    @GET("graphql/{pathQueryId}/UserByRestId")
    suspend fun getUserByRestId(
        @Path("pathQueryId") pathQueryId: kotlin.String = "QdS5LJDl99iL_KUzckdfNQ",
        @Query("variables") variables: kotlin.String = "{\"userId\": \"44196397\", \"withSafetyModeUserFields\": true}",
        @Query(
            "features",
        ) features: kotlin.String = "{\"hidden_profile_likes_enabled\": true, \"hidden_profile_subscriptions_enabled\": true, \"responsive_web_graphql_exclude_directive_enabled\": true, \"verified_phone_label_enabled\": false, \"highlights_tweets_tab_ui_enabled\": true, \"creator_subscriptions_tweet_preview_api_enabled\": true, \"responsive_web_graphql_skip_user_profile_image_extensions_enabled\": false, \"responsive_web_graphql_timeline_navigation_enabled\": true}",
    ): Response<GetUserByRestId200Response>

    /**
     *
     * get user by screen name
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "G3KGOASz96M-Qu0nwmGXNg")
     * @param variables  (default to "{\"screen_name\": \"elonmusk\", \"withSafetyModeUserFields\": true}")
     * @param features  (default to "{\"hidden_profile_likes_enabled\": true, \"hidden_profile_subscriptions_enabled\": true, \"responsive_web_graphql_exclude_directive_enabled\": true, \"verified_phone_label_enabled\": false, \"subscriptions_verification_info_is_identity_verified_enabled\": true, \"subscriptions_verification_info_verified_since_enabled\": true, \"highlights_tweets_tab_ui_enabled\": true, \"creator_subscriptions_tweet_preview_api_enabled\": true, \"responsive_web_graphql_skip_user_profile_image_extensions_enabled\": false, \"responsive_web_graphql_timeline_navigation_enabled\": true}")
     * @param fieldToggles  (default to "{\"withAuxiliaryUserLabels\": false}")
     * @return [GetUserByRestId200Response]
     */
    @GET("graphql/{pathQueryId}/UserByScreenName")
    suspend fun getUserByScreenName(
        @Path("pathQueryId") pathQueryId: kotlin.String = "G3KGOASz96M-Qu0nwmGXNg",
        @Query("variables") variables: kotlin.String = "{\"screen_name\": \"elonmusk\", \"withSafetyModeUserFields\": true}",
        @Query(
            "features",
        ) features: kotlin.String = "{\"hidden_profile_likes_enabled\": true, \"hidden_profile_subscriptions_enabled\": true, \"responsive_web_graphql_exclude_directive_enabled\": true, \"verified_phone_label_enabled\": false, \"subscriptions_verification_info_is_identity_verified_enabled\": true, \"subscriptions_verification_info_verified_since_enabled\": true, \"highlights_tweets_tab_ui_enabled\": true, \"creator_subscriptions_tweet_preview_api_enabled\": true, \"responsive_web_graphql_skip_user_profile_image_extensions_enabled\": false, \"responsive_web_graphql_timeline_navigation_enabled\": true}",
        @Query("fieldToggles") fieldToggles: kotlin.String = "{\"withAuxiliaryUserLabels\": false}",
    ): Response<GetUserByRestId200Response>
}
