package dev.dimension.flare.data.network.vvo.api

import de.jensklingenberg.ktorfit.http.GET
import dev.dimension.flare.data.network.vvo.model.Config
import dev.dimension.flare.data.network.vvo.model.VVOResponse

interface ConfigApi {
    @GET("api/config")
    suspend fun config(): VVOResponse<Config>
}
