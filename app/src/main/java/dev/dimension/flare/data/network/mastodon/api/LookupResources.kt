package dev.dimension.flare.data.network.mastodon.api

import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Path
import de.jensklingenberg.ktorfit.http.Query
import dev.dimension.flare.data.network.mastodon.api.model.Account
import dev.dimension.flare.data.network.mastodon.api.model.Status

interface LookupResources {
    @GET("/api/v1/accounts/{id}")
    suspend fun lookupUser(
        @Path(value = "id") id: String
    ): Account

    @GET("/api/v1/statuses/{id}")
    suspend fun lookupStatus(
        @Path("id") id: String
    ): Status

    @GET("/api/v1/accounts/lookup")
    suspend fun lookupUserByAcct(
        @Query("acct") acct: String
    ): Account?
}
