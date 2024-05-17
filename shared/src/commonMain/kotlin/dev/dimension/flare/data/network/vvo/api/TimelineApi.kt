package dev.dimension.flare.data.network.vvo.api

import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Query
import dev.dimension.flare.data.network.vvo.model.TimelineData
import dev.dimension.flare.data.network.vvo.model.VVOResponse

interface TimelineApi {
    @GET("feed/friends")
    suspend fun getFriendsTimeline(
        @Query("max_id") maxId: String? = null,
    ): VVOResponse<TimelineData>
}
