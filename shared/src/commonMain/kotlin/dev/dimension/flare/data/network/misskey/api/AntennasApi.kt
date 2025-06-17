package dev.dimension.flare.data.network.misskey.api

import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.POST
import dev.dimension.flare.data.network.misskey.api.model.Antenna
import dev.dimension.flare.data.network.misskey.api.model.AntennasCreateRequest
import dev.dimension.flare.data.network.misskey.api.model.AntennasDeleteRequest
import dev.dimension.flare.data.network.misskey.api.model.AntennasNotesRequest
import dev.dimension.flare.data.network.misskey.api.model.AntennasUpdateRequest
import dev.dimension.flare.data.network.misskey.api.model.Note
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject

internal interface AntennasApi {
    /**
     * antennas/create
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:account*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param antennasCreateRequest * @return [Antenna]
     */
    @POST("antennas/create")
    suspend fun antennasCreate(
        @Body antennasCreateRequest: AntennasCreateRequest,
    ): Antenna

    /**
     * antennas/delete
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:account*
     * Responses:
     *  - 204: OK (without any results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param antennasDeleteRequest * @return [Unit]
     */
    @POST("antennas/delete")
    suspend fun antennasDelete(
        @Body antennasDeleteRequest: AntennasDeleteRequest,
    ): Unit

    /**
     * antennas/list
     * No description provided.  **Credential required**: *Yes* / **Permission**: *read:account*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param body * @return [kotlin.collections.List<Antenna>]
     */
    @POST("antennas/list")
    suspend fun antennasList(
        @Body body: JsonObject = buildJsonObject { },
    ): kotlin.collections.List<Antenna>

    /**
     * antennas/notes
     * No description provided.  **Credential required**: *Yes* / **Permission**: *read:account*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param antennasNotesRequest * @return [kotlin.collections.List<Note>]
     */
    @POST("antennas/notes")
    suspend fun antennasNotes(
        @Body antennasNotesRequest: AntennasNotesRequest,
    ): kotlin.collections.List<Note>

    /**
     * antennas/show
     * No description provided.  **Credential required**: *Yes* / **Permission**: *read:account*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param antennasDeleteRequest * @return [Antenna]
     */
    @POST("antennas/show")
    suspend fun antennasShow(
        @Body antennasDeleteRequest: AntennasDeleteRequest,
    ): Antenna

    /**
     * antennas/update
     * No description provided.  **Credential required**: *Yes* / **Permission**: *write:account*
     * Responses:
     *  - 200: OK (with results)
     *  - 400: Client error
     *  - 401: Authentication error
     *  - 403: Forbidden error
     *  - 418: I'm Ai
     *  - 500: Internal server error
     *
     * @param antennasUpdateRequest * @return [Antenna]
     */
    @POST("antennas/update")
    suspend fun antennasUpdate(
        @Body antennasUpdateRequest: AntennasUpdateRequest,
    ): Antenna
}
