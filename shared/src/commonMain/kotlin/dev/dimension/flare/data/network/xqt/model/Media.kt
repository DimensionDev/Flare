/**
 *
 * Please note:
 * This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * Do not edit this file manually.
 *
 */

@file:Suppress(
    "ArrayInDataClass",
    "EnumEntryName",
    "RemoveRedundantQualifierName",
    "UnusedImport",
)

package dev.dimension.flare.data.network.xqt.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 *
 *
 * @param displayUrl
 * @param expandedUrl
 * @param idStr
 * @param indices
 * @param mediaUrlHttps
 * @param originalInfo
 * @param sizes
 * @param type
 * @param url
 * @param features
 */
@Serializable
internal data class Media(
    @SerialName(value = "display_url")
    val displayUrl: String,
    @SerialName(value = "expanded_url")
    val expandedUrl: String,
    @SerialName(value = "id_str")
    val idStr: kotlin.String,
    @SerialName(value = "indices")
    val indices: kotlin.collections.List<kotlin.Int>,
    @SerialName(value = "media_url_https")
    val mediaUrlHttps: String,
    @SerialName(value = "original_info")
    val originalInfo: MediaOriginalInfo,
    @SerialName(value = "sizes")
    val sizes: MediaSizes,
    @SerialName(value = "type")
    val type: Media.Type,
    @SerialName(value = "url")
    val url: String,
    @SerialName(value = "video_info")
    val videoInfo: MediaVideoInfo? = null,
    @SerialName(value = "ext_alt_text")
    val ext_alt_text: String? = null,
//    @SerialName(value = "features")
//    val features: kotlin.Any? = null,
) {
    /**
     *
     *
     * Values: photo,video,animatedGif
     */
    @Serializable
    enum class Type(val value: kotlin.String) {
        @SerialName(value = "photo")
        photo("photo"),

        @SerialName(value = "video")
        video("video"),

        @SerialName(value = "animated_gif")
        animatedGif("animated_gif"),
    }
}