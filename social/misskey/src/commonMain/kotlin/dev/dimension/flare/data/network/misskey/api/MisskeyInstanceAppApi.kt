package dev.dimension.flare.data.network.misskey.api

import de.jensklingenberg.ktorfit.http.GET
import dev.dimension.flare.data.network.misskey.api.model.MisskeyInstance

public interface MisskeyInstanceAppApi {
    @GET("instances.json")
    public suspend fun instances(): MisskeyInstance
}
