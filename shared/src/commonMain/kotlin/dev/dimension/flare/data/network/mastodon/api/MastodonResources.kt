package dev.dimension.flare.data.network.mastodon.api

import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Header
import de.jensklingenberg.ktorfit.http.POST
import dev.dimension.flare.data.network.mastodon.api.model.Emoji
import dev.dimension.flare.data.network.mastodon.api.model.Marker
import dev.dimension.flare.data.network.mastodon.api.model.MarkerUpdate

internal interface MastodonResources {
    @GET("api/v1/custom_emojis")
    suspend fun emojis(): List<Emoji>

    @GET("api/v1/markers?timeline[]=notifications")
    suspend fun notificationMarkers(): Marker

    @POST("api/v1/markers")
    suspend fun updateMarker(
        @Body data: MarkerUpdate,
        @Header("Content-Type") contentType: String = "application/json",
    )
}
