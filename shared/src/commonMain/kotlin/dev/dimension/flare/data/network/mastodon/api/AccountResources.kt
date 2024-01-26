package dev.dimension.flare.data.network.mastodon.api

import de.jensklingenberg.ktorfit.Response
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Path
import de.jensklingenberg.ktorfit.http.Query
import dev.dimension.flare.data.network.mastodon.api.model.Account

internal interface AccountResources {
    @GET("api/v1/accounts/{id}/followers")
    suspend fun followers(
        @Path(value = "id") id: String,
        @Query("max_id") max_id: String? = null,
        @Query("since_id") since_id: String? = null,
        @Query("limit") limit: Int? = null,
    ): Response<List<Account>>

    @GET("api/v1/accounts/{id}/following")
    suspend fun following(
        @Path(value = "id") id: String,
        @Query("max_id") max_id: String? = null,
        @Query("since_id") since_id: String? = null,
        @Query("limit") limit: Int? = null,
    ): Response<List<Account>>
}
