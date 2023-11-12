package dev.dimension.flare.data.network.misskey.api

import de.jensklingenberg.ktorfit.Response
import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.POST
import dev.dimension.flare.data.network.misskey.api.model.Flash
import dev.dimension.flare.data.network.misskey.api.model.FlashCreateRequest
import dev.dimension.flare.data.network.misskey.api.model.FlashDeleteRequest
import dev.dimension.flare.data.network.misskey.api.model.FlashUpdateRequest

interface FlashApi {
    /**
     * flash/create
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:flash*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 429: To many requests
     *  - 500: Internal server error
     *
     * @param flashCreateRequest * @return [Unit]
     */
    @POST("flash/create")
    suspend fun flashCreate(
        @Body flashCreateRequest: FlashCreateRequest,
    ): Response<Unit>

    /**
     * flash/featured
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param body * @return [kotlin.collections.List<Flash>]
     */
    @POST("flash/featured")
    suspend fun flashFeatured(
        @Body body: kotlin.Any,
    ): Response<kotlin.collections.List<Flash>>

    /**
     * flash/like
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:flash-likes*
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
    @POST("flash/like")
    suspend fun flashLike(
        @Body flashDeleteRequest: FlashDeleteRequest,
    ): Response<Unit>

    /**
     * flash/unlike
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:flash-likes*
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
    @POST("flash/unlike")
    suspend fun flashUnlike(
        @Body flashDeleteRequest: FlashDeleteRequest,
    ): Response<Unit>

    /**
     * flash/update
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:flash*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 429: To many requests
     *  - 500: Internal server error
     *
     * @param flashUpdateRequest * @return [Unit]
     */
    @POST("flash/update")
    suspend fun flashUpdate(
        @Body flashUpdateRequest: FlashUpdateRequest,
    ): Response<Unit>
}
