package dev.dimension.flare.data.network.misskey.api

import de.jensklingenberg.ktorfit.Response
import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.POST
import dev.dimension.flare.data.network.misskey.api.model.NotificationsCreateRequest

internal interface NotificationsApi {
    /**
     * notifications/create
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:notifications*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param notificationsCreateRequest * @return [Unit]
     */
    @POST("notifications/create")
    suspend fun notificationsCreate(
        @Body notificationsCreateRequest: NotificationsCreateRequest,
    ): Response<Unit>

    /**
     * notifications/mark-all-as-read
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:notifications*
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
    @POST("notifications/mark-all-as-read")
    suspend fun notificationsMarkAllAsRead(
        @Body body: kotlin.Any,
    ): Response<Unit>
}
