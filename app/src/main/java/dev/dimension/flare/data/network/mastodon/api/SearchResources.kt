package dev.dimension.flare.data.network.mastodon.api

import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Query
import dev.dimension.flare.data.network.mastodon.api.model.SearchResult

interface SearchResources {
    @GET("api/v2/search")
    suspend fun searchV2(
        @Query("q") query: String,
        @Query("account_id") account_id: String? = null,
        @Query("max_id") max_id: String? = null,
        @Query("min_id") min_id: String? = null,
        @Query("type") type: String? = null,
        @Query("exclude_unreviewed") exclude_unreviewed: Boolean? = null,
        @Query("resolve") resolve: Boolean? = null,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
        @Query("following") following: Boolean? = null,
    ): SearchResult
}
