package dev.dimension.flare.data.network.mastodon.api

import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Path
import de.jensklingenberg.ktorfit.http.Query
import dev.dimension.flare.data.network.mastodon.api.model.Account
import dev.dimension.flare.data.network.mastodon.api.model.PostReport
import dev.dimension.flare.data.network.mastodon.api.model.RelationshipResponse

public interface FriendshipResources {
    @POST("api/v1/accounts/{id}/follow")
    public suspend fun follow(
        @Path(value = "id") id: String,
    ): Account

    @POST("api/v1/accounts/{id}/unfollow")
    public suspend fun unfollow(
        @Path(value = "id") id: String,
    ): Account

    @GET("api/v1/accounts/relationships")
    public suspend fun showFriendships(
        @Query("id[]") id: List<String>,
    ): List<RelationshipResponse>

    @POST("api/v1/accounts/{id}/block")
    public suspend fun block(
        @Path(value = "id") id: String,
    ): RelationshipResponse

    @POST("api/v1/accounts/{id}/unblock")
    public suspend fun unblock(
        @Path(value = "id") id: String,
    ): RelationshipResponse

    @POST("api/v1/accounts/{id}/mute")
    public suspend fun muteUser(
        @Path(value = "id") id: String,
    ): RelationshipResponse

    @POST("api/v1/accounts/{id}/unmute")
    public suspend fun unmuteUser(
        @Path(value = "id") id: String,
    ): RelationshipResponse

    @POST("api/v1/reports")
    public suspend fun report(
        @Body data: PostReport,
    )

    @POST("api/v1/follow_requests/{id}/authorize")
    public suspend fun authorizeFollowRequest(
        @Path(value = "id") id: String,
    ): RelationshipResponse

    @POST("api/v1/follow_requests/{id}/reject")
    public suspend fun rejectFollowRequest(
        @Path(value = "id") id: String,
    ): RelationshipResponse
}
