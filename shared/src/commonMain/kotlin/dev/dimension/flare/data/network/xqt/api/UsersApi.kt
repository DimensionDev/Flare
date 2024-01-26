package dev.dimension.flare.data.network.xqt.api

import de.jensklingenberg.ktorfit.Response
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Path
import de.jensklingenberg.ktorfit.http.Query
import dev.dimension.flare.data.network.xqt.model.GetUsersByRestIds200Response

internal interface UsersApi {
    /**
     *
     * get users by rest ids
     * Responses:
     *  - 200: Successful operation
     *
     * @param pathQueryId  (default to "GD4q8bBE2i6cqWw2iT74Gg")
     * @param variables  (default to "{\"userIds\": [\"44196397\"]}")
     * @param features  (default to "{\"responsive_web_graphql_exclude_directive_enabled\": true, \"verified_phone_label_enabled\": false, \"responsive_web_graphql_skip_user_profile_image_extensions_enabled\": false, \"responsive_web_graphql_timeline_navigation_enabled\": true}")
     * @return [GetUsersByRestIds200Response]
     */
    @GET("graphql/{pathQueryId}/UsersByRestIds")
    suspend fun getUsersByRestIds(
        @Path("pathQueryId") pathQueryId: kotlin.String = "GD4q8bBE2i6cqWw2iT74Gg",
        @Query("variables") variables: kotlin.String = "{\"userIds\": [\"44196397\"]}",
        @Query(
            "features",
        ) features: kotlin.String = "{\"responsive_web_graphql_exclude_directive_enabled\": true, \"verified_phone_label_enabled\": false, \"responsive_web_graphql_skip_user_profile_image_extensions_enabled\": false, \"responsive_web_graphql_timeline_navigation_enabled\": true}",
    ): Response<GetUsersByRestIds200Response>
}
