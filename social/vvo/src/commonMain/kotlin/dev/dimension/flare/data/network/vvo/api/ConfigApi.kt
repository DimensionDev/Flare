package dev.dimension.flare.data.network.vvo.api

import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Header
import de.jensklingenberg.ktorfit.http.Query
import dev.dimension.flare.data.network.vvo.model.Config
import dev.dimension.flare.data.network.vvo.model.UnreadData
import dev.dimension.flare.data.network.vvo.model.VVOResponse

internal interface ConfigApi {
    @GET("api/config")
    suspend fun config(): VVOResponse<Config>

    @GET("api/remind/unread")
    suspend fun remindUnread(
        @Query("t") time: Long,
        @Header("X-Xsrf-Token") st: String,
    ): VVOResponse<UnreadData>
}
