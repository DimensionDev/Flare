package dev.dimension.flare.data.network.mastodon.api

import de.jensklingenberg.ktorfit.Response
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Path
import de.jensklingenberg.ktorfit.http.Query
import dev.dimension.flare.data.network.mastodon.api.model.Context
import dev.dimension.flare.data.network.mastodon.api.model.Notification
import dev.dimension.flare.data.network.mastodon.api.model.NotificationTypes
import dev.dimension.flare.data.network.mastodon.api.model.Status

interface TimelineResources {
    @GET("/api/v1/timelines/home")
    suspend fun homeTimeline(
        @Query("max_id") max_id: String? = null,
        @Query("since_id") since_id: String? = null,
        @Query("min_id") min_id: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("local") local: Boolean? = null,
    ): List<Status>

    @GET("/api/v1/timelines/public")
    suspend fun publicTimeline(
        @Query("max_id") max_id: String? = null,
        @Query("since_id") since_id: String? = null,
        @Query("min_id") min_id: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("local") local: Boolean? = null,
        @Query("remote") remote: Boolean? = null,
        @Query("only_media") only_media: Boolean? = null,
    ): List<Status>

    @GET("/api/v1/accounts/{id}/statuses")
    suspend fun userTimeline(
        @Path("id") user_id: String,
        @Query("max_id") max_id: String? = null,
        @Query("since_id") since_id: String? = null,
        @Query("min_id") min_id: String? = null,
        @Query("exclude_replies") exclude_replies: Boolean? = null,
        @Query("limit") limit: Int? = null,
        @Query("pinned") pinned: Boolean? = null,
    ): List<Status>

    @GET("/api/v1/favourites")
    suspend fun favoritesList(
        @Query("max_id") max_id: String? = null,
        @Query("since_id") since_id: String? = null,
        @Query("min_id") min_id: String? = null,
        @Query("exclude_replies") exclude_replies: Boolean? = null,
        @Query("limit") limit: Int? = null,
    ): Response<List<Status>>

    @GET("/api/v1/notifications")
    suspend fun notification(
        @Query("max_id") max_id: String? = null,
        @Query("since_id") since_id: String? = null,
        @Query("min_id") min_id: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("exclude_types[]") exclude_types: List<NotificationTypes>? = null,
        @Query("account_id") account_id: String? = null,
    ): List<Notification>

    @GET("/api/v1/statuses/{id}/context")
    suspend fun context(@Path("id") id: String): Context

    @GET("/api/v1/timelines/tag/{hashtag}")
    suspend fun hashtagTimeline(
        @Path("hashtag") hashtag: String,
        @Query("max_id") max_id: String? = null,
        @Query("since_id") since_id: String? = null,
        @Query("min_id") min_id: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("local") local: Boolean? = null,
        @Query("only_media") only_media: Boolean? = null,
    ): List<Status>

    @GET("/api/v1//timelines/list/{id}")
    suspend fun listTimeline(
        @Path("id") listId: String,
        @Query("max_id") max_id: String? = null,
        @Query("since_id") since_id: String? = null,
        @Query("min_id") min_id: String? = null,
        @Query("limit") limit: Int? = null,
    ): List<Status>
}
