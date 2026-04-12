package dev.dimension.flare.data.network.mastodon.api

import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Query
import dev.dimension.flare.data.network.mastodon.api.model.Status
import dev.dimension.flare.data.network.mastodon.api.model.Suggestions
import dev.dimension.flare.data.network.mastodon.api.model.Trend

internal interface TrendsResources {
    @GET("api/v1/trends/tags")
    suspend fun trendsTags(): List<Trend>

    @GET("api/v1/trends/statuses")
    suspend fun trendsStatuses(
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
    ): List<Status>

    @GET("api/v2/suggestions")
    suspend fun suggestionsUsers(
        @Query("limit") limit: Int? = null,
    ): List<Suggestions>
}
