package dev.dimension.flare.data.network.mastodon.api

import de.jensklingenberg.ktorfit.http.GET
import dev.dimension.flare.data.network.mastodon.api.model.InstanceData
import dev.dimension.flare.data.network.mastodon.api.model.InstanceInfoV1

internal interface InstanceResources {
    @GET("api/v2/instance")
    suspend fun instance(): InstanceData

    @GET("api/v1/instance")
    suspend fun instanceV1(): InstanceInfoV1
}
