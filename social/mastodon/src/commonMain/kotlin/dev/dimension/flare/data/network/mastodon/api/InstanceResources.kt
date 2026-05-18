package dev.dimension.flare.data.network.mastodon.api

import de.jensklingenberg.ktorfit.http.GET
import dev.dimension.flare.data.network.mastodon.api.model.InstanceData
import dev.dimension.flare.data.network.mastodon.api.model.InstanceInfoV1

public interface InstanceResources {
    @GET("api/v2/instance")
    public suspend fun instance(): InstanceData

    @GET("api/v1/instance")
    public suspend fun instanceV1(): InstanceInfoV1
}
