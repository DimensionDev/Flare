package dev.dimension.flare.data.network.misskey.api

import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Path
import dev.dimension.flare.data.network.misskey.api.model.response.MiAuthCheckResponse

interface AuthResources {
    @POST("api/miauth/{id}/check")
    suspend fun check(
        @Path("id") id: String,
    ): MiAuthCheckResponse
}
