package dev.dimension.flare.data.network.misskey.api

import de.jensklingenberg.ktorfit.http.GET
import dev.dimension.flare.data.network.misskey.api.model.MisskeyInstance

interface MisskeyInstanceAppApi {
    @GET("instances.json")
    suspend fun instances(): MisskeyInstance
}