package dev.dimension.flare.data.network.misskey.api

import de.jensklingenberg.ktorfit.Response
import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.POST
import dev.dimension.flare.data.network.misskey.api.model.AdminAccountsDeleteRequest
import dev.dimension.flare.data.network.misskey.api.model.AdminFederationDeleteAllFilesRequest
import dev.dimension.flare.data.network.misskey.api.model.ApGetRequest
import dev.dimension.flare.data.network.misskey.api.model.ApShow200Response
import dev.dimension.flare.data.network.misskey.api.model.FederationFollowersRequest
import dev.dimension.flare.data.network.misskey.api.model.FederationInstance
import dev.dimension.flare.data.network.misskey.api.model.FederationInstancesRequest
import dev.dimension.flare.data.network.misskey.api.model.FederationShowInstance200Response
import dev.dimension.flare.data.network.misskey.api.model.FederationStatsRequest
import dev.dimension.flare.data.network.misskey.api.model.Following
import dev.dimension.flare.data.network.misskey.api.model.UserDetailedNotMe

interface FederationApi {
    /**
     * ap/get
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 429: To many requests
     *  - 500: Internal server error
     *
     * @param apGetRequest * @return [kotlin.Any]
     */
    @POST("ap/get")
    suspend fun apGet(@Body apGetRequest: ApGetRequest): Response<kotlin.Any>

    /**
     * ap/show
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 429: To many requests
     *  - 500: Internal server error
     *
     * @param apGetRequest * @return [ApShow200Response]
     */
    @POST("ap/show")
    suspend fun apShow(@Body apGetRequest: ApGetRequest): Response<ApShow200Response>

    /**
     * federation/followers
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param federationFollowersRequest * @return [kotlin.collections.List<Following>]
     */
    @POST("federation/followers")
    suspend fun federationFollowers(@Body federationFollowersRequest: FederationFollowersRequest): Response<kotlin.collections.List<Following>>

    /**
     * federation/following
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param federationFollowersRequest * @return [kotlin.collections.List<Following>]
     */
    @POST("federation/following")
    suspend fun federationFollowing(@Body federationFollowersRequest: FederationFollowersRequest): Response<kotlin.collections.List<Following>>

    /**
     * federation/instances
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param federationInstancesRequest * @return [kotlin.collections.List<FederationInstance>]
     */
    @POST("federation/instances")
    suspend fun federationInstances(@Body federationInstancesRequest: FederationInstancesRequest): Response<kotlin.collections.List<FederationInstance>>

    /**
     * federation/show-instance
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminFederationDeleteAllFilesRequest * @return [FederationShowInstance200Response]
     */
    @POST("federation/show-instance")
    suspend fun federationShowInstance(@Body adminFederationDeleteAllFilesRequest: AdminFederationDeleteAllFilesRequest): Response<FederationShowInstance200Response>

    /**
     * federation/stats
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param federationStatsRequest * @return [Unit]
     */
    @POST("federation/stats")
    suspend fun federationStats(@Body federationStatsRequest: FederationStatsRequest): Response<Unit>

    /**
     * federation/update-remote-user
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
    @POST("federation/update-remote-user")
    suspend fun federationUpdateRemoteUser(@Body adminAccountsDeleteRequest: AdminAccountsDeleteRequest): Response<Unit>

    /**
     * federation/users
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param federationFollowersRequest * @return [kotlin.collections.List<UserDetailedNotMe>]
     */
    @POST("federation/users")
    suspend fun federationUsers(@Body federationFollowersRequest: FederationFollowersRequest): Response<kotlin.collections.List<UserDetailedNotMe>>
}
