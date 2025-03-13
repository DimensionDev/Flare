package dev.dimension.flare.data.network.misskey.api

import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.POST
import dev.dimension.flare.data.network.misskey.api.model.Clip
import dev.dimension.flare.data.network.misskey.api.model.ClipsCreateRequest
import dev.dimension.flare.data.network.misskey.api.model.ClipsDeleteRequest
import dev.dimension.flare.data.network.misskey.api.model.ClipsUpdateRequest
import dev.dimension.flare.data.network.misskey.api.model.IPinRequest

internal interface ClipsApi {
    /**
     * clips/create
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:account*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param clipsCreateRequest * @return [Clip]
     */
    @POST("clips/create")
    suspend fun clipsCreate(
        @Body clipsCreateRequest: ClipsCreateRequest,
    ): Clip

    /**
     * clips/delete
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:account*
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
    @POST("clips/delete")
    suspend fun clipsDelete(
        @Body clipsDeleteRequest: ClipsDeleteRequest,
    ): Unit

    /**
     * clips/list
     * No description provided.  **Credential required**: *Yes* / **Permission**: *read:account*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param body * @return [kotlin.collections.List<Clip>]
     */
    @POST("clips/list")
    suspend fun clipsList(
        @Body body: kotlin.Any,
    ): kotlin.collections.List<Clip>

    /**
     * clips/show
     * No description provided.  **Credential required**: *No* / **Permission**: *read:account*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param clipsDeleteRequest * @return [Clip]
     */
    @POST("clips/show")
    suspend fun clipsShow(
        @Body clipsDeleteRequest: ClipsDeleteRequest,
    ): Clip

    /**
     * clips/update
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:account*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param clipsUpdateRequest * @return [Clip]
     */
    @POST("clips/update")
    suspend fun clipsUpdate(
        @Body clipsUpdateRequest: ClipsUpdateRequest,
    ): Clip

    /**
     * notes/clips
     * No description provided.  **Credential required**: *No*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param ipinRequest * @return [kotlin.collections.List<Clip>]
     */
    @POST("notes/clips")
    suspend fun notesClips(
        @Body ipinRequest: IPinRequest,
    ): kotlin.collections.List<Clip>
}
