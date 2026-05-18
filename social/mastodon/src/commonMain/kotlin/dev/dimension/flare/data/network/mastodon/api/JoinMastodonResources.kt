package dev.dimension.flare.data.network.mastodon.api

import de.jensklingenberg.ktorfit.http.GET
import dev.dimension.flare.data.network.mastodon.api.model.MastodonInstanceElement

public interface JoinMastodonResources {
    @GET("servers")
    public suspend fun servers(): List<MastodonInstanceElement>
}
