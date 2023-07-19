package dev.dimension.flare.data.network.mastodon.api

import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Query
import dev.dimension.flare.data.network.mastodon.api.model.Trend

interface TrendsResources {
  @GET("/api/v1/trends")
  suspend fun trends(
    @Query("limit") limit: Int? = null,
  ): List<Trend>
}
