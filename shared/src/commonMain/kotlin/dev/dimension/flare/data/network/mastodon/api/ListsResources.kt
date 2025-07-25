package dev.dimension.flare.data.network.mastodon.api

import de.jensklingenberg.ktorfit.Response
import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.DELETE
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.HTTP
import de.jensklingenberg.ktorfit.http.Header
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.PUT
import de.jensklingenberg.ktorfit.http.Path
import de.jensklingenberg.ktorfit.http.Query
import dev.dimension.flare.data.network.mastodon.api.model.Account
import dev.dimension.flare.data.network.mastodon.api.model.MastodonList
import dev.dimension.flare.data.network.mastodon.api.model.MastodonPaging
import dev.dimension.flare.data.network.mastodon.api.model.PostAccounts
import dev.dimension.flare.data.network.mastodon.api.model.PostList

internal interface ListsResources {
    @GET("api/v1/lists")
    suspend fun lists(): List<MastodonList>

    @POST("api/v1/lists")
    suspend fun createList(
        @Body postList: PostList,
        @Header("Content-Type") contentType: String = "application/json",
    ): MastodonList

    @GET("api/v1/lists/{id}")
    suspend fun getList(
        @Path("id") id: String,
    ): MastodonList

    @PUT("api/v1/lists/{id}")
    suspend fun updateList(
        @Path("id") id: String,
        @Body postList: PostList,
        @Header("Content-Type") contentType: String = "application/json",
    ): MastodonList

    @DELETE("api/v1/lists/{id}")
    suspend fun deleteList(
        @Path("id") id: String,
    ): Response<String>

    @GET("api/v1/lists/{id}/accounts")
    suspend fun listMembers(
        @Path("id") listId: String,
        @Query("max_id") max_id: String? = null,
        @Query("since_id") since_id: String? = null,
        @Query("limit") limit: Int = 20,
    ): MastodonPaging<Account>

    @POST("api/v1/lists/{id}/accounts")
    suspend fun addMember(
        @Path("id") listId: String,
        @Body accounts: PostAccounts,
        @Header("Content-Type") contentType: String = "application/json",
    ): Response<String>

    @HTTP(method = "DELETE", path = "/api/v1/lists/{id}/accounts", hasBody = true)
    suspend fun removeMember(
        @Path("id") listId: String,
        @Body accounts: PostAccounts,
        @Header("Content-Type") contentType: String = "application/json",
    ): Response<String>
}
