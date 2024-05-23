package dev.dimension.flare.data.network.vvo.api

import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Header
import de.jensklingenberg.ktorfit.http.Query
import dev.dimension.flare.data.network.vvo.model.StatusExtend
import dev.dimension.flare.data.network.vvo.model.VVOResponse

internal interface StatusApi {
    @GET("statuses/extend")
    suspend fun getStatusExtend(
        @Query("id") id: String,
        @Header("X-Xsrf-Token") xsrfToken: String,
    ): VVOResponse<StatusExtend>

    @GET("detail/{id}")
    suspend fun getStatusDetail(
        @Query("id") id: String,
    ): String
}
