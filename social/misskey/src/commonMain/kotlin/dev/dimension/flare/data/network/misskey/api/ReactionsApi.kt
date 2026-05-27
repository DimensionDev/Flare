package dev.dimension.flare.data.network.misskey.api

import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.POST
import dev.dimension.flare.data.network.misskey.api.model.IPinRequest
import dev.dimension.flare.data.network.misskey.api.model.NotesReactionsCreateRequest

internal interface ReactionsApi {
    /**
     * notes/reactions/create
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:reactions*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param notesReactionsCreateRequest * @return [Unit]
     */
    @POST("notes/reactions/create")
    suspend fun notesReactionsCreate(
        @Body notesReactionsCreateRequest: NotesReactionsCreateRequest,
    ): Unit

    /**
     * notes/reactions/delete
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:reactions*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 429: To many requests
     *  - 500: Internal server error
     *
     * @param ipinRequest * @return [Unit]
     */
    @POST("notes/reactions/delete")
    suspend fun notesReactionsDelete(
        @Body ipinRequest: IPinRequest,
    ): Unit
}
