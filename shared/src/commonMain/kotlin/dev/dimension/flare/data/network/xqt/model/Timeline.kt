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
 * @param instructions
 * @param metadata
 * @param responseObjects
 */
@Serializable
internal data class Timeline(
    @SerialName(value = "instructions")
    val instructions: kotlin.collections.List<InstructionUnion>,
//    @Contextual @SerialName(value = "metadata")
//    val metadata: kotlin.collections.Map<kotlin.String, kotlin.Any>? = null,
//    @Contextual @SerialName(value = "responseObjects")
//    val responseObjects: kotlin.collections.Map<kotlin.String, kotlin.Any>? = null,
)
