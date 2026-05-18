package dev.dimension.flare.data.network.misskey.api

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

public interface ChartsApi {
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
    public suspend fun chartsActiveUsers(
        @Body chartsActiveUsersRequest: ChartsActiveUsersRequest,
    ): ChartsActiveUsers200Response

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
    public suspend fun chartsApRequest(
        @Body chartsActiveUsersRequest: ChartsActiveUsersRequest,
    ): ChartsApRequest200Response

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
    public suspend fun chartsDrive(
        @Body chartsActiveUsersRequest: ChartsActiveUsersRequest,
    ): ChartsDrive200Response

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
    public suspend fun chartsFederation(
        @Body chartsActiveUsersRequest: ChartsActiveUsersRequest,
    ): ChartsFederation200Response

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
    public suspend fun chartsInstance(
        @Body chartsInstanceRequest: ChartsInstanceRequest,
    ): ChartsInstance200Response

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
    public suspend fun chartsNotes(
        @Body chartsActiveUsersRequest: ChartsActiveUsersRequest,
    ): ChartsNotes200Response

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
    public suspend fun chartsUserDrive(
        @Body chartsUserDriveRequest: ChartsUserDriveRequest,
    ): ChartsUserDrive200Response

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
    public suspend fun chartsUserFollowing(
        @Body chartsUserDriveRequest: ChartsUserDriveRequest,
    ): ChartsUserFollowing200Response

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
    public suspend fun chartsUserNotes(
        @Body chartsUserDriveRequest: ChartsUserDriveRequest,
    ): ChartsUserNotes200Response

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
    public suspend fun chartsUserPv(
        @Body chartsUserDriveRequest: ChartsUserDriveRequest,
    ): ChartsUserPv200Response

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
    public suspend fun chartsUserReactions(
        @Body chartsUserDriveRequest: ChartsUserDriveRequest,
    ): ChartsUserReactions200Response

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
    public suspend fun chartsUsers(
        @Body chartsActiveUsersRequest: ChartsActiveUsersRequest,
    ): ChartsUsers200Response
}
