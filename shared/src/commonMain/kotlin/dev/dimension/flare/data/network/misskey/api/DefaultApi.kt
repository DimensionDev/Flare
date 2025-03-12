package dev.dimension.flare.data.network.misskey.api

import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.POST
import dev.dimension.flare.data.network.misskey.api.model.AdminAbuseReportResolverCreate200Response
import dev.dimension.flare.data.network.misskey.api.model.AdminAbuseReportResolverDeleteRequest
import dev.dimension.flare.data.network.misskey.api.model.AdminAbuseReportResolverListRequest
import dev.dimension.flare.data.network.misskey.api.model.AdminAbuseReportResolverUpdateRequest
import dev.dimension.flare.data.network.misskey.api.model.AdminAccountsDeleteRequest
import dev.dimension.flare.data.network.misskey.api.model.IClaimAchievementRequest
import dev.dimension.flare.data.network.misskey.api.model.UserList
import dev.dimension.flare.data.network.misskey.api.model.UsersListsCreateFromPublicRequest
import dev.dimension.flare.data.network.misskey.api.model.UsersListsDeleteRequest

internal interface DefaultApi {
    /**
     * admin/abuse-report-resolver/delete
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminAbuseReportResolverDeleteRequest * @return [Unit]
     */
    @POST("admin/abuse-report-resolver/delete")
    suspend fun adminAbuseReportResolverDelete(
        @Body adminAbuseReportResolverDeleteRequest: AdminAbuseReportResolverDeleteRequest,
    ): Unit

    /**
     * admin/abuse-report-resolver/list
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminAbuseReportResolverListRequest * @return [kotlin.collections.List<AdminAbuseReportResolverCreate200Response>]
     */
    @POST("admin/abuse-report-resolver/list")
    suspend fun adminAbuseReportResolverList(
        @Body adminAbuseReportResolverListRequest: AdminAbuseReportResolverListRequest,
    ): kotlin.collections.List<AdminAbuseReportResolverCreate200Response>

    /**
     * admin/abuse-report-resolver/update
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminAbuseReportResolverUpdateRequest * @return [Unit]
     */
    @POST("admin/abuse-report-resolver/update")
    suspend fun adminAbuseReportResolverUpdate(
        @Body adminAbuseReportResolverUpdateRequest: AdminAbuseReportResolverUpdateRequest,
    ): Unit

    /**
     * i/claim-achievement
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param iclaimAchievementRequest * @return [Unit]
     */
    @POST("i/claim-achievement")
    suspend fun iClaimAchievement(
        @Body iclaimAchievementRequest: IClaimAchievementRequest,
    ): Unit

    /**
     * users/achievements
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminAccountsDeleteRequest * @return [Unit]
     */
    @POST("users/achievements")
    suspend fun usersAchievements(
        @Body adminAccountsDeleteRequest: AdminAccountsDeleteRequest,
    ): Unit

    /**
     * users/lists/create-from-public
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param usersListsCreateFromPublicRequest * @return [UserList]
     */
    @POST("users/lists/create-from-public")
    suspend fun usersListsCreateFromPublic(
        @Body usersListsCreateFromPublicRequest: UsersListsCreateFromPublicRequest,
    ): UserList

    /**
     * users/lists/favorite
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param usersListsDeleteRequest * @return [Unit]
     */
    @POST("users/lists/favorite")
    suspend fun usersListsFavorite(
        @Body usersListsDeleteRequest: UsersListsDeleteRequest,
    ): Unit

    /**
     * users/lists/unfavorite
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param usersListsDeleteRequest * @return [Unit]
     */
    @POST("users/lists/unfavorite")
    suspend fun usersListsUnfavorite(
        @Body usersListsDeleteRequest: UsersListsDeleteRequest,
    ): Unit
}
