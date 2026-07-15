package dev.dimension.flare.data.network.xqt.api

import de.jensklingenberg.ktorfit.Response
import de.jensklingenberg.ktorfit.http.*
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Path
import de.jensklingenberg.ktorfit.http.Query
import dev.dimension.flare.data.network.xqt.model.GetUsersByRestIds200Response
import dev.dimension.flare.data.network.xqt.model.UsersResponse

internal interface UsersApi {
    /**
     * GET graphql/{pathQueryId}/UsersByRestIds
     *
     * get users by rest ids
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "_8egOzcbgeLIhP0TbTStGw")
     * @param variables  (default to "{\"userIds\": [\"44196397\"]}")
     * @param features  (default to "{\"payments_enabled\": false, \"profile_label_improvements_pcf_label_in_post_enabled\": true, \"responsive_web_profile_redirect_enabled\": false, \"rweb_tipjar_consumption_enabled\": true, \"verified_phone_label_enabled\": false, \"responsive_web_graphql_skip_user_profile_image_extensions_enabled\": false, \"responsive_web_graphql_timeline_navigation_enabled\": true}")
     * @return [UsersResponse]
     */
    @GET("graphql/{pathQueryId}/UsersByRestIds")
    suspend fun getUsersByRestIds(@Path("pathQueryId") pathQueryId: kotlin.String = "_8egOzcbgeLIhP0TbTStGw", @Query("variables") variables: kotlin.String = "{\"userIds\": [\"44196397\"]}", @Query("features") features: kotlin.String = "{\"payments_enabled\": false, \"profile_label_improvements_pcf_label_in_post_enabled\": true, \"responsive_web_profile_redirect_enabled\": false, \"rweb_tipjar_consumption_enabled\": true, \"verified_phone_label_enabled\": false, \"responsive_web_graphql_skip_user_profile_image_extensions_enabled\": false, \"responsive_web_graphql_timeline_navigation_enabled\": true}"): Response<GetUsersByRestIds200Response>

}
