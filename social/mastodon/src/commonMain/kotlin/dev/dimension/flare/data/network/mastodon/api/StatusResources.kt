package dev.dimension.flare.data.network.mastodon.api

import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.DELETE
import de.jensklingenberg.ktorfit.http.Header
import de.jensklingenberg.ktorfit.http.Multipart
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Path
import dev.dimension.flare.data.network.mastodon.api.model.Poll
import dev.dimension.flare.data.network.mastodon.api.model.PostStatus
import dev.dimension.flare.data.network.mastodon.api.model.PostVote
import dev.dimension.flare.data.network.mastodon.api.model.Status
import dev.dimension.flare.data.network.mastodon.api.model.UploadResponse
import io.ktor.client.request.forms.MultiPartFormDataContent

public interface StatusResources {
    @POST("api/v1/statuses/{id}/favourite")
    public suspend fun favourite(
        @Path("id") id: String,
    ): Status

    @POST("api/v1/statuses/{id}/unfavourite")
    public suspend fun unfavourite(
        @Path("id") id: String,
    ): Status

    @POST("api/v1/statuses/{id}/reblog")
    public suspend fun reblog(
        @Path("id") id: String,
    ): Status

    @POST("api/v1/statuses/{id}/unreblog")
    public suspend fun unreblog(
        @Path("id") id: String,
    ): Status

    @POST("api/v1/statuses/{id}/bookmark")
    public suspend fun bookmark(
        @Path("id") id: String,
    ): Status

    @POST("api/v1/statuses/{id}/unbookmark")
    public suspend fun unbookmark(
        @Path("id") id: String,
    ): Status

    @POST("api/v1/statuses/{id}/mute")
    public suspend fun mute(
        @Path("id") id: String,
    ): Status

    @POST("api/v1/statuses/{id}/unmute")
    public suspend fun unmute(
        @Path("id") id: String,
    ): Status

    @POST("api/v1/statuses/{id}/pin")
    public suspend fun pin(
        @Path("id") id: String,
    ): Status

    @POST("api/v1/statuses/{id}/unpin")
    public suspend fun unpin(
        @Path("id") id: String,
    ): Status

    @DELETE("api/v1/statuses/{id}")
    public suspend fun delete(
        @Path("id") id: String,
    ): Status

    @POST("api/v1/statuses")
    public suspend fun post(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body data: PostStatus,
        @Header("Content-Type") contentType: String = "application/json",
    ): Status

    @Multipart
    @POST("api/v1/media")
    public suspend fun upload(
        @Body map: MultiPartFormDataContent,
    ): UploadResponse

    @POST("api/v1/polls/{id}/votes")
    public suspend fun vote(
        @Path("id") id: String,
        @Body data: PostVote,
        @Header("Content-Type") contentType: String = "application/json",
    ): Poll
}
