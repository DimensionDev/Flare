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
 * @param state
 * @param count
 */
@Serializable
data class TweetView(
    @SerialName(value = "state")
    val state: TweetView.State,
    @SerialName(value = "count")
    val count: kotlin.String? = null,
) {
    /**
     *
     *
     * Values: enabledWithCount
     */
    @Serializable
    enum class State(val value: kotlin.String) {
        @SerialName(value = "EnabledWithCount")
        enabledWithCount("EnabledWithCount"),
    }
}