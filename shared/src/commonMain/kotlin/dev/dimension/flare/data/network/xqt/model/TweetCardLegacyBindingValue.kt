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
 * @param key
 * @param `value`
 */
@Serializable
internal data class TweetCardLegacyBindingValue(
    @SerialName(value = "key")
    val key: kotlin.String,
    @SerialName(value = "value")
    val `value`: TweetCardLegacyBindingValueData,
)
