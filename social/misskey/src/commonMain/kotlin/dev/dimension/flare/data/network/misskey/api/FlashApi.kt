package dev.dimension.flare.data.network.misskey.api

import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.POST
import dev.dimension.flare.data.network.misskey.api.model.Flash
import dev.dimension.flare.data.network.misskey.api.model.FlashCreateRequest
import dev.dimension.flare.data.network.misskey.api.model.FlashDeleteRequest
import dev.dimension.flare.data.network.misskey.api.model.FlashUpdateRequest

public interface FlashApi {
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
    public suspend fun flashCreate(
        @Body flashCreateRequest: FlashCreateRequest,
    ): Unit

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
    public suspend fun flashFeatured(
        @Body body: kotlin.Any,
    ): kotlin.collections.List<Flash>

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
    public suspend fun flashLike(
        @Body flashDeleteRequest: FlashDeleteRequest,
    ): Unit

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
    public suspend fun flashUnlike(
        @Body flashDeleteRequest: FlashDeleteRequest,
    ): Unit

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
    public suspend fun flashUpdate(
        @Body flashUpdateRequest: FlashUpdateRequest,
    ): Unit
}
