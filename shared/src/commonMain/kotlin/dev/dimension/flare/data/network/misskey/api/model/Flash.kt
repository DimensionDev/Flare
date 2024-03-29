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

package dev.dimension.flare.data.network.misskey.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * *
 * @param id * @param createdAt * @param updatedAt * @param title * @param summary * @param script * @param userId * @param user * @param likedCount * @param isLiked */
@Serializable
internal data class Flash(
    @SerialName(value = "id") val id: kotlin.String,
    @SerialName(value = "createdAt") val createdAt: kotlin.String,
    @SerialName(value = "updatedAt") val updatedAt: kotlin.String,
    @SerialName(value = "title") val title: kotlin.String,
    @SerialName(value = "summary") val summary: kotlin.String,
    @SerialName(value = "script") val script: kotlin.String,
    @SerialName(value = "userId") val userId: kotlin.String,
    @SerialName(value = "user") val user: UserLite,
    @SerialName(value = "likedCount") val likedCount: kotlin.Double? = null,
    @SerialName(value = "isLiked") val isLiked: kotlin.Boolean? = null,
)
