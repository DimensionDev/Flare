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
 * @param richtextTypes
 * @param toIndex
 */
@Serializable
internal data class NoteTweetResultRichTextTag(
    @SerialName(value = "from_index")
    val fromIndex: kotlin.Int,
    @SerialName(value = "richtext_types")
    val richtextTypes: kotlin.collections.List<NoteTweetResultRichTextTag.RichtextTypes>,
    @SerialName(value = "to_index")
    val toIndex: kotlin.Int,
) {
    /**
     *
     *
     * Values: bold,italic
     */
    @Serializable
    enum class RichtextTypes(
        val value: kotlin.String,
    ) {
        @SerialName(value = "Bold")
        bold("Bold"),

        @SerialName(value = "Italic")
        italic("Italic"),
    }
}
