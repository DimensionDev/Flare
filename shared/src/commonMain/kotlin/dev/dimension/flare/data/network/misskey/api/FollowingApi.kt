package dev.dimension.flare.data.network.misskey.api

import de.jensklingenberg.ktorfit.Response
import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.POST
import dev.dimension.flare.data.network.misskey.api.model.AdminAccountsDeleteRequest
import dev.dimension.flare.data.network.misskey.api.model.FollowingRequestsList200ResponseInner
import dev.dimension.flare.data.network.misskey.api.model.FollowingRequestsListRequest
import dev.dimension.flare.data.network.misskey.api.model.UserLite

internal interface FollowingApi {
    /**
     * following/create
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:following*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 429: To many requests
     *  - 500: Internal server error
     *
     * @param adminAccountsDeleteRequest * @return [UserLite]
     */
    @POST("following/create")
    suspend fun followingCreate(
        @Body adminAccountsDeleteRequest: AdminAccountsDeleteRequest,
    ): Response<UserLite>

    /**
     * following/delete
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:following*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 429: To many requests
     *  - 500: Internal server error
     *
     * @param adminAccountsDeleteRequest * @return [UserLite]
     */
    @POST("following/delete")
    suspend fun followingDelete(
        @Body adminAccountsDeleteRequest: AdminAccountsDeleteRequest,
    ): Response<UserLite>

    /**
     * following/invalidate
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:following*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 429: To many requests
     *  - 500: Internal server error
     *
     * @param adminAccountsDeleteRequest * @return [UserLite]
     */
    @POST("following/invalidate")
    suspend fun followingInvalidate(
        @Body adminAccountsDeleteRequest: AdminAccountsDeleteRequest,
    ): Response<UserLite>

    /**
     * following/requests/accept
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:following*
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
    @POST("following/requests/accept")
    suspend fun followingRequestsAccept(
        @Body adminAccountsDeleteRequest: AdminAccountsDeleteRequest,
    ): Response<Unit>

    /**
     * following/requests/cancel
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:following*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminAccountsDeleteRequest * @return [UserLite]
     */
    @POST("following/requests/cancel")
    suspend fun followingRequestsCancel(
        @Body adminAccountsDeleteRequest: AdminAccountsDeleteRequest,
    ): Response<UserLite>

    /**
     * following/requests/list
     * No description provided.  **Credential required**: *Yes* / **Permission**: *read:following*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param followingRequestsListRequest * @return [kotlin.collections.List<FollowingRequestsList200ResponseInner>]
     */
    @POST("following/requests/list")
    suspend fun followingRequestsList(
        @Body followingRequestsListRequest: FollowingRequestsListRequest,
    ): Response<kotlin.collections.List<FollowingRequestsList200ResponseInner>>

    /**
     * following/requests/reject
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:following*
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
    @POST("following/requests/reject")
    suspend fun followingRequestsReject(
        @Body adminAccountsDeleteRequest: AdminAccountsDeleteRequest,
    ): Response<Unit>
}
