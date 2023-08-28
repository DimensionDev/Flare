package dev.dimension.flare.data.network.mastodon.api

import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Path
import de.jensklingenberg.ktorfit.http.Query
import dev.dimension.flare.data.network.mastodon.api.model.Account
import dev.dimension.flare.data.network.mastodon.api.model.PostReport
import dev.dimension.flare.data.network.mastodon.api.model.RelationshipResponse

interface FriendshipResources {
    @POST("api/v1/accounts/{id}/follow")
    suspend fun follow(
        @Path(value = "id") id: String,
    ): Account

    @POST("api/v1/accounts/{id}/unfollow")
    suspend fun unfollow(
        @Path(value = "id") id: String,
    ): Account

    @GET("api/v1/accounts/relationships")
    suspend fun showFriendships(@Query("id[]") id: List<String>): List<RelationshipResponse>

    @POST("api/v1/accounts/{id}/block")
    suspend fun block(
        @Path(value = "id") id: String,
    ): RelationshipResponse

    @POST("api/v1/accounts/{id}/unblock")
    suspend fun unblock(
        @Path(value = "id") id: String,
    ): RelationshipResponse

    @POST("api/v1/reports")
    suspend fun report(@Body data: PostReport)
}
