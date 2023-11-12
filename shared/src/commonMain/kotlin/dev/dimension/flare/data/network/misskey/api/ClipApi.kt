package dev.dimension.flare.data.network.misskey.api

import de.jensklingenberg.ktorfit.Response
import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.POST
import dev.dimension.flare.data.network.misskey.api.model.ClipsDeleteRequest

interface ClipApi {
    /**
     * clips/favorite
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:clip-favorite*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param clipsDeleteRequest * @return [Unit]
     */
    @POST("clips/favorite")
    suspend fun clipsFavorite(
        @Body clipsDeleteRequest: ClipsDeleteRequest,
    ): Response<Unit>

    /**
     * clips/unfavorite
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:clip-favorite*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param clipsDeleteRequest * @return [Unit]
     */
    @POST("clips/unfavorite")
    suspend fun clipsUnfavorite(
        @Body clipsDeleteRequest: ClipsDeleteRequest,
    ): Response<Unit>
}
