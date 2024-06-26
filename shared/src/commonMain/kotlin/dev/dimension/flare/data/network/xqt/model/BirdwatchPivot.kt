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

import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 *
 *
 * @param destinationUrl
 * @param footer
 * @param iconType
 * @param note
 * @param shorttitle
 * @param subtitle
 * @param title
 * @param visualStyle
 */
@Serializable
internal data class BirdwatchPivot(
    @Contextual @SerialName(value = "destinationUrl")
    val destinationUrl: String,
    @SerialName(value = "footer")
    val footer: BirdwatchPivotFooter,
    @SerialName(value = "iconType")
    val iconType: BirdwatchPivot.IconType,
    @SerialName(value = "note")
    val note: BirdwatchPivotNote,
    @SerialName(value = "shorttitle")
    val shorttitle: kotlin.String,
    @SerialName(value = "subtitle")
    val subtitle: BirdwatchPivotSubtitle,
    @SerialName(value = "title")
    val title: kotlin.String,
    @SerialName(value = "visualStyle")
    val visualStyle: BirdwatchPivot.VisualStyle? = null,
) {
    /**
     *
     *
     * Values: birdwatchV1Icon
     */
    @Serializable
    enum class IconType(
        val value: kotlin.String,
    ) {
        @SerialName(value = "BirdwatchV1Icon")
        birdwatchV1Icon("BirdwatchV1Icon"),
    }

    /**
     *
     *
     * Values: default
     */
    @Serializable
    enum class VisualStyle(
        val value: kotlin.String,
    ) {
        @SerialName(value = "Default")
        default("Default"),
    }
}
