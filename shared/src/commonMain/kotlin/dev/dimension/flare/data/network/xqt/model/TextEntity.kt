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
 * @param fromIndex
 * @param ref
 * @param toIndex
 */
@Serializable
data class TextEntity(
    @SerialName(value = "fromIndex")
    val fromIndex: kotlin.Int,
    @SerialName(value = "ref")
    val ref: TextEntityRef,
    @SerialName(value = "toIndex")
    val toIndex: kotlin.Int,
)