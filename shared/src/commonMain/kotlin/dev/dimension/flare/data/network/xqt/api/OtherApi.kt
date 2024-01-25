package dev.dimension.flare.data.network.xqt.api

import de.jensklingenberg.ktorfit.Response
import de.jensklingenberg.ktorfit.http.GET
import dev.dimension.flare.data.network.xqt.model.Other200Response

interface OtherApi {
    /**
     *
     * This is not an actual endpoint
     * Responses:
     *  - 200: Successful operation
     *
     * @return [Other200Response]
     */
    @GET("other")
    suspend fun other(): Response<Other200Response>
}
