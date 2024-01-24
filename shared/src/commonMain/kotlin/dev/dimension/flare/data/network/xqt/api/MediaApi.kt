package dev.dimension.flare.data.network.xqt.api

import de.jensklingenberg.ktorfit.http.Field
import de.jensklingenberg.ktorfit.http.FormUrlEncoded
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Query
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

interface MediaApi {
    @FormUrlEncoded
    @POST("media/upload.json?command=INIT")
    suspend fun initUpload(
        @Field("media_type") mediaType: String,
        @Field("total_bytes") totalBytes: String,
        @Field("media_category") category: String?,
    ): TwitterUploadResponse

    @POST("media/upload.json?command=APPEND")
    @FormUrlEncoded
    suspend fun appendUpload(
        @Field("media_id") mediaId: String,
        @Field("segment_index") segmentIndex: String,
        @Field("media_data") mediaData: String,
    ): Unit

    @POST("media/upload.json?command=FINALIZE")
    @FormUrlEncoded
    suspend fun finalizeUpload(
        @Field("media_id") mediaId: String,
    ): TwitterUploadResponse

    @GET("media/upload.json?command=STATUS")
    suspend fun uploadStatus(
        @Query("media_id") mediaId: String,
    ): TwitterUploadResponse
}

@Serializable
data class TwitterUploadResponse(
    @SerialName("media_id")
    val mediaID: Long? = null,

    @SerialName("media_id_string")
    val mediaIDString: String? = null,

    @SerialName("expires_after_secs")
    val expiresAfterSecs: Long? = null,

    @SerialName("processing_info")
    val processingInfo: TwitterUploadProcessInfo? = null,
)

@Serializable
data class TwitterUploadProcessInfo(
    @SerialName("check_after_secs")
    val checkAfterSecs: Int? = null,
    @SerialName("progress_percent")
    val progressPercent: Int? = null,
    @SerialName("state")
    val state: String? = null,
)
