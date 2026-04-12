package dev.dimension.flare.data.network.mastodon.api

import de.jensklingenberg.ktorfit.http.GET
import dev.dimension.flare.data.network.mastodon.api.model.MastodonInstanceElement

internal interface JoinMastodonResources {
    @GET("servers")
    suspend fun servers(): List<MastodonInstanceElement>
}
