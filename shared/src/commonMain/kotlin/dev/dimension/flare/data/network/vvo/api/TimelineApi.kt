package dev.dimension.flare.data.network.vvo.api

import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Query
import dev.dimension.flare.data.network.vvo.model.Attitude
import dev.dimension.flare.data.network.vvo.model.Comment
import dev.dimension.flare.data.network.vvo.model.HotflowData
import dev.dimension.flare.data.network.vvo.model.RepostTimeline
import dev.dimension.flare.data.network.vvo.model.Status
import dev.dimension.flare.data.network.vvo.model.TimelineData
import dev.dimension.flare.data.network.vvo.model.VVOResponse

internal interface TimelineApi {
    @GET("feed/friends")
    suspend fun getFriendsTimeline(
        @Query("max_id") maxId: String? = null,
    ): VVOResponse<TimelineData>

    @GET("message/mentionsAt")
    suspend fun getMentionsAt(
        @Query("page") page: Int,
    ): VVOResponse<List<Status>>

    @GET("message/cmt")
    suspend fun getComments(
        @Query("page") page: Int,
    ): VVOResponse<List<Comment>>

    @GET("message/attitude")
    suspend fun getAttitudes(
        @Query("page") page: Int,
    ): VVOResponse<List<Attitude>>

    @GET("comments/hotflow")
    suspend fun getHotComments(
        @Query("id") id: String,
        @Query("mid") mid: String,
        @Query("max_id") maxId: Long? = null,
        @Query("max_id_type") maxIdType: Int = 0,
    ): VVOResponse<HotflowData>

    @GET("statuses/repostTimeline")
    suspend fun getRepostTimeline(
        @Query("id") id: String,
        @Query("page") page: Int,
    ): VVOResponse<RepostTimeline>
}
