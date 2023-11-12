package dev.dimension.flare.data.network.misskey.api

import de.jensklingenberg.ktorfit.Response
import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.POST
import dev.dimension.flare.data.network.misskey.api.model.ChartsActiveUsers200Response
import dev.dimension.flare.data.network.misskey.api.model.ChartsActiveUsersRequest
import dev.dimension.flare.data.network.misskey.api.model.ChartsApRequest200Response
import dev.dimension.flare.data.network.misskey.api.model.ChartsDrive200Response
import dev.dimension.flare.data.network.misskey.api.model.ChartsFederation200Response
import dev.dimension.flare.data.network.misskey.api.model.ChartsInstance200Response
import dev.dimension.flare.data.network.misskey.api.model.ChartsInstanceRequest
import dev.dimension.flare.data.network.misskey.api.model.ChartsNotes200Response
import dev.dimension.flare.data.network.misskey.api.model.ChartsUserDrive200Response
import dev.dimension.flare.data.network.misskey.api.model.ChartsUserDriveRequest
import dev.dimension.flare.data.network.misskey.api.model.ChartsUserFollowing200Response
import dev.dimension.flare.data.network.misskey.api.model.ChartsUserNotes200Response
import dev.dimension.flare.data.network.misskey.api.model.ChartsUserPv200Response
import dev.dimension.flare.data.network.misskey.api.model.ChartsUserReactions200Response
import dev.dimension.flare.data.network.misskey.api.model.ChartsUsers200Response

interface ChartsApi {
    /**
     * charts/active-users
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param chartsActiveUsersRequest * @return [ChartsActiveUsers200Response]
     */
    @POST("charts/active-users")
    suspend fun chartsActiveUsers(
        @Body chartsActiveUsersRequest: ChartsActiveUsersRequest,
    ): Response<ChartsActiveUsers200Response>

    /**
     * charts/ap-request
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param chartsActiveUsersRequest * @return [ChartsApRequest200Response]
     */
    @POST("charts/ap-request")
    suspend fun chartsApRequest(
        @Body chartsActiveUsersRequest: ChartsActiveUsersRequest,
    ): Response<ChartsApRequest200Response>

    /**
     * charts/drive
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param chartsActiveUsersRequest * @return [ChartsDrive200Response]
     */
    @POST("charts/drive")
    suspend fun chartsDrive(
        @Body chartsActiveUsersRequest: ChartsActiveUsersRequest,
    ): Response<ChartsDrive200Response>

    /**
     * charts/federation
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param chartsActiveUsersRequest * @return [ChartsFederation200Response]
     */
    @POST("charts/federation")
    suspend fun chartsFederation(
        @Body chartsActiveUsersRequest: ChartsActiveUsersRequest,
    ): Response<ChartsFederation200Response>

    /**
     * charts/instance
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param chartsInstanceRequest * @return [ChartsInstance200Response]
     */
    @POST("charts/instance")
    suspend fun chartsInstance(
        @Body chartsInstanceRequest: ChartsInstanceRequest,
    ): Response<ChartsInstance200Response>

    /**
     * charts/notes
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param chartsActiveUsersRequest * @return [ChartsNotes200Response]
     */
    @POST("charts/notes")
    suspend fun chartsNotes(
        @Body chartsActiveUsersRequest: ChartsActiveUsersRequest,
    ): Response<ChartsNotes200Response>

    /**
     * charts/user/drive
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param chartsUserDriveRequest * @return [ChartsUserDrive200Response]
     */
    @POST("charts/user/drive")
    suspend fun chartsUserDrive(
        @Body chartsUserDriveRequest: ChartsUserDriveRequest,
    ): Response<ChartsUserDrive200Response>

    /**
     * charts/user/following
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param chartsUserDriveRequest * @return [ChartsUserFollowing200Response]
     */
    @POST("charts/user/following")
    suspend fun chartsUserFollowing(
        @Body chartsUserDriveRequest: ChartsUserDriveRequest,
    ): Response<ChartsUserFollowing200Response>

    /**
     * charts/user/notes
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param chartsUserDriveRequest * @return [ChartsUserNotes200Response]
     */
    @POST("charts/user/notes")
    suspend fun chartsUserNotes(
        @Body chartsUserDriveRequest: ChartsUserDriveRequest,
    ): Response<ChartsUserNotes200Response>

    /**
     * charts/user/pv
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param chartsUserDriveRequest * @return [ChartsUserPv200Response]
     */
    @POST("charts/user/pv")
    suspend fun chartsUserPv(
        @Body chartsUserDriveRequest: ChartsUserDriveRequest,
    ): Response<ChartsUserPv200Response>

    /**
     * charts/user/reactions
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param chartsUserDriveRequest * @return [ChartsUserReactions200Response]
     */
    @POST("charts/user/reactions")
    suspend fun chartsUserReactions(
        @Body chartsUserDriveRequest: ChartsUserDriveRequest,
    ): Response<ChartsUserReactions200Response>

    /**
     * charts/users
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param chartsActiveUsersRequest * @return [ChartsUsers200Response]
     */
    @POST("charts/users")
    suspend fun chartsUsers(
        @Body chartsActiveUsersRequest: ChartsActiveUsersRequest,
    ): Response<ChartsUsers200Response>
}
