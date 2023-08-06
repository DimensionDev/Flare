package dev.dimension.flare.data.network.misskey.api

import de.jensklingenberg.ktorfit.Response
import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.POST
import dev.dimension.flare.data.network.misskey.api.model.UserList
import dev.dimension.flare.data.network.misskey.api.model.UsersListsCreateRequest
import dev.dimension.flare.data.network.misskey.api.model.UsersListsDeleteRequest
import dev.dimension.flare.data.network.misskey.api.model.UsersListsListRequest
import dev.dimension.flare.data.network.misskey.api.model.UsersListsPullRequest
import dev.dimension.flare.data.network.misskey.api.model.UsersListsShowRequest
import dev.dimension.flare.data.network.misskey.api.model.UsersListsUpdateRequest

interface ListsApi {
    /**
     * users/lists/create
     * Create a new list of users.  **Credential required**: *Yes* / **Permission**: *write:account*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param usersListsCreateRequest * @return [UserList]
     */
    @POST("users/lists/create")
    suspend fun usersListsCreate(@Body usersListsCreateRequest: UsersListsCreateRequest): Response<UserList>

    /**
     * users/lists/delete
     * Delete an existing list of users.  **Credential required**: *Yes* / **Permission**: *write:account*
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
    @POST("users/lists/delete")
    suspend fun usersListsDelete(@Body usersListsDeleteRequest: UsersListsDeleteRequest): Response<Unit>

    /**
     * users/lists/list
     * Show all lists that the authenticated user has created.  **Credential required**: *No* / **Permission**: *read:account*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param usersListsListRequest * @return [kotlin.collections.List<UserList>]
     */
    @POST("users/lists/list")
    suspend fun usersListsList(@Body usersListsListRequest: UsersListsListRequest): Response<kotlin.collections.List<UserList>>

    /**
     * users/lists/pull
     * Remove a user from a list.  **Credential required**: *Yes* / **Permission**: *write:account*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param usersListsPullRequest * @return [Unit]
     */
    @POST("users/lists/pull")
    suspend fun usersListsPull(@Body usersListsPullRequest: UsersListsPullRequest): Response<Unit>

    /**
     * users/lists/push
     * Add a user to an existing list.  **Credential required**: *Yes* / **Permission**: *write:account*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 429: To many requests
     *  - 500: Internal server error
     *
     * @param usersListsPullRequest * @return [Unit]
     */
    @POST("users/lists/push")
    suspend fun usersListsPush(@Body usersListsPullRequest: UsersListsPullRequest): Response<Unit>

    /**
     * users/lists/show
     * Show the properties of a list.  **Credential required**: *No* / **Permission**: *read:account*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param usersListsShowRequest * @return [UserList]
     */
    @POST("users/lists/show")
    suspend fun usersListsShow(@Body usersListsShowRequest: UsersListsShowRequest): Response<UserList>

    /**
     * users/lists/update
     * Update the properties of a list.  **Credential required**: *Yes* / **Permission**: *write:account*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param usersListsUpdateRequest * @return [UserList]
     */
    @POST("users/lists/update")
    suspend fun usersListsUpdate(@Body usersListsUpdateRequest: UsersListsUpdateRequest): Response<UserList>
}
