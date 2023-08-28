package dev.dimension.flare.data.network.mastodon.api

import de.jensklingenberg.ktorfit.http.GET
import dev.dimension.flare.data.network.mastodon.api.model.Emoji

interface MastodonResources {

    @GET("api/v1/custom_emojis")
    suspend fun emojis(): List<Emoji>
}
