package dev.dimension.flare.data.network.misskey.api

import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.POST
import dev.dimension.flare.data.network.misskey.api.model.AdminRolesDeleteRequest
import dev.dimension.flare.data.network.misskey.api.model.AdminRolesUsersRequest
import dev.dimension.flare.data.network.misskey.api.model.Note
import dev.dimension.flare.data.network.misskey.api.model.RolesNotesRequest

internal interface RoleApi {
    /**
     * roles/list
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param body * @return [Unit]
     */
    @POST("roles/list")
    suspend fun rolesList(
        @Body body: kotlin.Any,
    ): Unit

    /**
     * roles/notes
     * No description provided.  **Credential required**: *Yes*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param rolesNotesRequest * @return [kotlin.collections.List<Note>]
     */
    @POST("roles/notes")
    suspend fun rolesNotes(
        @Body rolesNotesRequest: RolesNotesRequest,
    ): kotlin.collections.List<Note>

    /**
     * roles/show
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminRolesDeleteRequest * @return [Unit]
     */
    @POST("roles/show")
    suspend fun rolesShow(
        @Body adminRolesDeleteRequest: AdminRolesDeleteRequest,
    ): Unit

    /**
     * roles/users
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param adminRolesUsersRequest * @return [Unit]
     */
    @POST("roles/users")
    suspend fun rolesUsers(
        @Body adminRolesUsersRequest: AdminRolesUsersRequest,
    ): Unit
}
