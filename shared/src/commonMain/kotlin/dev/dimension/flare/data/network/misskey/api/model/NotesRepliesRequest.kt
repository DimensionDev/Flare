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
 * @param noteId * @param sinceId * @param untilId * @param limit */
@Serializable
internal data class NotesRepliesRequest(
    @SerialName(value = "noteId") val noteId: kotlin.String,
    @SerialName(value = "sinceId") val sinceId: kotlin.String? = null,
    @SerialName(value = "untilId") val untilId: kotlin.String? = null,
    @SerialName(value = "limit") val limit: kotlin.Int? = 10,
)
