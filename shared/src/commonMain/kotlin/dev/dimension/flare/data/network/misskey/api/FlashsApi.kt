package dev.dimension.flare.data.network.misskey.api

import de.jensklingenberg.ktorfit.Response
import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.POST
import dev.dimension.flare.data.network.misskey.api.model.Flash
import dev.dimension.flare.data.network.misskey.api.model.FlashDeleteRequest

internal interface FlashsApi {
    /**
     * flash/delete
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:flash*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param flashDeleteRequest * @return [Unit]
     */
    @POST("flash/delete")
    suspend fun flashDelete(
        @Body flashDeleteRequest: FlashDeleteRequest,
    ): Response<Unit>

    /**
     * flash/show
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param flashDeleteRequest * @return [Flash]
     */
    @POST("flash/show")
    suspend fun flashShow(
        @Body flashDeleteRequest: FlashDeleteRequest,
    ): Response<Flash>
}
