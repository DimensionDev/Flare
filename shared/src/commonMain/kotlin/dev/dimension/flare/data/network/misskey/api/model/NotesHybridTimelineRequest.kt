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
 * @param limit * @param sinceId * @param untilId * @param sinceDate * @param untilDate * @param includeMyRenotes * @param includeRenotedMyNotes * @param includeLocalRenotes * @param withFiles * @param withReplies */
@Serializable
internal data class NotesHybridTimelineRequest(
    @SerialName(value = "limit") val limit: kotlin.Int? = 10,
    @SerialName(value = "sinceId") val sinceId: kotlin.String? = null,
    @SerialName(value = "untilId") val untilId: kotlin.String? = null,
    @SerialName(value = "sinceDate") val sinceDate: kotlin.Int? = null,
    @SerialName(value = "untilDate") val untilDate: kotlin.Int? = null,
    @SerialName(value = "includeMyRenotes") val includeMyRenotes: kotlin.Boolean? = true,
    @SerialName(value = "includeRenotedMyNotes") val includeRenotedMyNotes: kotlin.Boolean? = true,
    @SerialName(value = "includeLocalRenotes") val includeLocalRenotes: kotlin.Boolean? = true,
    @SerialName(value = "withFiles") val withFiles: kotlin.Boolean? = false,
    @SerialName(value = "withReplies") val withReplies: kotlin.Boolean? = false,
)
