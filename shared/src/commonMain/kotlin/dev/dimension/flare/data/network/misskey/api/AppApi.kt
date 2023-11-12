package dev.dimension.flare.data.network.misskey.api

import de.jensklingenberg.ktorfit.Response
import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.POST
import dev.dimension.flare.data.network.misskey.api.model.App
import dev.dimension.flare.data.network.misskey.api.model.AppCreateRequest
import dev.dimension.flare.data.network.misskey.api.model.AppShowRequest

interface AppApi {
    /**
     * app/create
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param appCreateRequest * @return [App]
     */
    @POST("app/create")
    suspend fun appCreate(
        @Body appCreateRequest: AppCreateRequest,
    ): Response<App>

    /**
     * app/show
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param appShowRequest * @return [App]
     */
    @POST("app/show")
    suspend fun appShow(
        @Body appShowRequest: AppShowRequest,
    ): Response<App>
}
