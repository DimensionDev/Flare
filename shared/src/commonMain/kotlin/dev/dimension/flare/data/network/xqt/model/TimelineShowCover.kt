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
 * @param clientEventInfo
 * @param cover
 * @param type
 */
@Serializable
@SerialName("TimelineShowCover")
data class TimelineShowCover(
    @SerialName(value = "clientEventInfo")
    val clientEventInfo: ClientEventInfo,
    @SerialName(value = "cover")
    val cover: TimelineHalfCover,
//    @Contextual @SerialName(value = "type")
//    val type: InstructionType,
) : InstructionUnion