package dev.dimension.flare.data.network.xqt.api

import de.jensklingenberg.ktorfit.Response
import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Header
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Path
import de.jensklingenberg.ktorfit.http.Query
import dev.dimension.flare.data.network.xqt.XQTTimelineQueryIds
import dev.dimension.flare.data.network.xqt.model.GetHomeLatestTimeline200Response
import dev.dimension.flare.data.network.xqt.model.PinnedTimelinesManagementSheetResponse
import dev.dimension.flare.data.network.xqt.model.TagTimelineRequest

internal interface PinnableTimelineApi {
    @GET("graphql/{pathQueryId}/PinnedTimelinesManagementSheetQuery")
    suspend fun getPinnedTimelinesManagementSheet(
        @Path("pathQueryId")
        pathQueryId: String = XQTTimelineQueryIds.PINNED_TIMELINES_MANAGEMENT_SHEET,
        @Query("variables")
        variables: String = "{}",
    ): Response<PinnedTimelinesManagementSheetResponse>

    @POST("graphql/{pathQueryId}/HomeTimeline")
    suspend fun postTagTimeline(
        @Header("Content-Type")
        contentType: String = "application/json",
        @Path("pathQueryId")
        pathQueryId: String = XQTTimelineQueryIds.TAG_TIMELINE,
        @Body
        request: TagTimelineRequest,
    ): Response<GetHomeLatestTimeline200Response>
}
