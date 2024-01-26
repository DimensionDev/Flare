package dev.dimension.flare.data.network.xqt.api

import de.jensklingenberg.ktorfit.http.Field
import de.jensklingenberg.ktorfit.http.FormUrlEncoded
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.Header
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Query
import dev.dimension.flare.model.xqtHost
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal interface MediaApi {
    @POST("media/upload.json?command=INIT")
    suspend fun initUpload(
        @Query("media_type") mediaType: String,
        @Query("total_bytes") totalBytes: String,
        @Query("media_category") category: String?,
        @Header("Referer") referer: String = "https://$xqtHost/",
    ): TwitterUploadResponse

    @POST("media/upload.json?command=APPEND")
    @FormUrlEncoded
    suspend fun appendUpload(
        @Field("media_id") mediaId: String,
        @Field("segment_index") segmentIndex: String,
        @Field("media_data") mediaData: String,
        @Header("Referer") referer: String = "https://$xqtHost/",
    ): Unit

    @POST("media/upload.json?command=FINALIZE")
    @FormUrlEncoded
    suspend fun finalizeUpload(
        @Field("media_id") mediaId: String,
        @Header("Referer") referer: String = "https://$xqtHost/",
    ): TwitterUploadResponse

    @GET("media/upload.json?command=STATUS")
    suspend fun uploadStatus(
        @Query("media_id") mediaId: String,
        @Header("Referer") referer: String = "https://$xqtHost/",
    ): TwitterUploadResponse
}

@Serializable
internal data class TwitterUploadResponse(
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
internal data class TwitterUploadProcessInfo(
    @SerialName("check_after_secs")
    val checkAfterSecs: Int? = null,
    @SerialName("progress_percent")
    val progressPercent: Int? = null,
    @SerialName("state")
    val state: String? = null,
)
