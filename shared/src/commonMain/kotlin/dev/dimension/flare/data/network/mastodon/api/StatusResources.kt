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

interface StatusResources {
    @POST("api/v1/statuses/{id}/favourite")
    suspend fun favourite(
        @Path("id") id: String,
    ): Status

    @POST("api/v1/statuses/{id}/unfavourite")
    suspend fun unfavourite(
        @Path("id") id: String,
    ): Status

    @POST("api/v1/statuses/{id}/reblog")
    suspend fun reblog(
        @Path("id") id: String,
    ): Status

    @POST("api/v1/statuses/{id}/unreblog")
    suspend fun unreblog(
        @Path("id") id: String,
    ): Status

    @POST("api/v1/statuses/{id}/bookmark")
    suspend fun bookmark(
        @Path("id") id: String,
    ): Status

    @POST("api/v1/statuses/{id}/unbookmark")
    suspend fun unbookmark(
        @Path("id") id: String,
    ): Status

    @POST("api/v1/statuses/{id}/mute")
    suspend fun mute(
        @Path("id") id: String,
    ): Status

    @POST("api/v1/statuses/{id}/unmute")
    suspend fun unmute(
        @Path("id") id: String,
    ): Status

    @POST("api/v1/statuses/{id}/pin")
    suspend fun pin(
        @Path("id") id: String,
    ): Status

    @POST("api/v1/statuses/{id}/unpin")
    suspend fun unpin(
        @Path("id") id: String,
    ): Status

    @DELETE("api/v1/statuses/{id}")
    suspend fun delete(
        @Path("id") id: String,
    ): Status

    @POST("api/v1/statuses")
    suspend fun post(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body data: PostStatus,
        @Header("Content-Type") contentType: String = "application/json",
    ): Status

    @Multipart
    @POST("api/v1/media")
    suspend fun upload(
        @Body map: MultiPartFormDataContent,
    ): UploadResponse

    @POST("api/v1/polls/{id}/votes")
    suspend fun vote(
        @Path("id") id: String,
        @Body data: PostVote,
    ): Poll
}
