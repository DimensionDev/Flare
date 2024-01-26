package dev.dimension.flare.data.network.misskey.api

import de.jensklingenberg.ktorfit.Response
import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.POST
import dev.dimension.flare.data.network.misskey.api.model.IWebhooksCreateRequest
import dev.dimension.flare.data.network.misskey.api.model.IWebhooksShowRequest
import dev.dimension.flare.data.network.misskey.api.model.IWebhooksUpdateRequest

internal interface WebhooksApi {
    /**
     * i/webhooks/create
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:account*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param iwebhooksCreateRequest * @return [Unit]
     */
    @POST("i/webhooks/create")
    suspend fun iWebhooksCreate(
        @Body iwebhooksCreateRequest: IWebhooksCreateRequest,
    ): Response<Unit>

    /**
     * i/webhooks/delete
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:account*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param iwebhooksShowRequest * @return [Unit]
     */
    @POST("i/webhooks/delete")
    suspend fun iWebhooksDelete(
        @Body iwebhooksShowRequest: IWebhooksShowRequest,
    ): Response<Unit>

    /**
     * i/webhooks/list
     * No description provided.  **Credential required**: *Yes* / **Permission**: *read:account*
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
    @POST("i/webhooks/list")
    suspend fun iWebhooksList(
        @Body body: kotlin.Any,
    ): Response<Unit>

    /**
     * i/webhooks/show
     * No description provided.  **Credential required**: *Yes* / **Permission**: *read:account*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param iwebhooksShowRequest * @return [Unit]
     */
    @POST("i/webhooks/show")
    suspend fun iWebhooksShow(
        @Body iwebhooksShowRequest: IWebhooksShowRequest,
    ): Response<Unit>

    /**
     * i/webhooks/update
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:account*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param iwebhooksUpdateRequest * @return [Unit]
     */
    @POST("i/webhooks/update")
    suspend fun iWebhooksUpdate(
        @Body iwebhooksUpdateRequest: IWebhooksUpdateRequest,
    ): Response<Unit>
}
