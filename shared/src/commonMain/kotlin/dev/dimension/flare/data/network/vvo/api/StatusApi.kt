package dev.dimension.flare.data.network.vvo.api

import de.jensklingenberg.ktorfit.http.Field
import de.jensklingenberg.ktorfit.http.FormUrlEncoded
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Header
import de.jensklingenberg.ktorfit.http.Multipart
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Part
import de.jensklingenberg.ktorfit.http.Query
import dev.dimension.flare.data.network.vvo.model.Comment
import dev.dimension.flare.data.network.vvo.model.Status
import dev.dimension.flare.data.network.vvo.model.StatusExtend
import dev.dimension.flare.data.network.vvo.model.UploadResponse
import dev.dimension.flare.data.network.vvo.model.VVOResponse
import io.ktor.http.content.PartData

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

    @POST("api/statuses/update")
    @FormUrlEncoded
    suspend fun updateStatus(
        @Field("content") content: String,
        @Field("st") st: String,
        @Header("X-Xsrf-Token") xsrfToken: String = st,
        // 6 for friends only, 1 for private
        @Field("visible") visible: String? = null,
        @Field("picId") picId: String? = null,
    ): VVOResponse<Status>

    @POST("api/statuses/uploadPic")
    @Multipart
    suspend fun uploadPic(
        @Part("st") st: String,
        @Part("") file: List<PartData>,
        @Header("X-Xsrf-Token") xsrfToken: String = st,
        @Part("type") type: String = "json",
    ): UploadResponse

    @POST("api/statuses/repost")
    suspend fun repostStatus(
        @Field("id") id: String,
        @Field("content") content: String,
        @Field("st") st: String,
        @Header("X-Xsrf-Token") xsrfToken: String = st,
        @Field("picId") picId: String? = null,
        @Field("mid") mid: String = id,
    ): VVOResponse<Status>

    @POST("api/comments/create")
    suspend fun commentStatus(
        @Field("id") id: String,
        @Field("content") content: String,
        @Field("st") st: String,
        @Header("X-Xsrf-Token") xsrfToken: String = st,
        @Field("picId") picId: String? = null,
        @Field("mid") mid: String = id,
    ): VVOResponse<Comment>

    @POST("api/comments/reply")
    suspend fun replyComment(
        @Field("id") id: String,
        @Field("cid") cid: String,
        @Field("content") content: String,
        @Field("st") st: String,
        @Header("X-Xsrf-Token") xsrfToken: String = st,
        @Field("picId") picId: String? = null,
        @Field("mid") mid: String = id,
        @Field("reply") reply: String = cid,
    ): VVOResponse<Comment>
}
