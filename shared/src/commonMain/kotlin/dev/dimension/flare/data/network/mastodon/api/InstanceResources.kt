package dev.dimension.flare.data.network.mastodon.api

import de.jensklingenberg.ktorfit.http.GET
import dev.dimension.flare.data.network.mastodon.api.model.InstanceData

internal interface InstanceResources {
    @GET("api/v2/instance")
    suspend fun instance(): InstanceData
}
