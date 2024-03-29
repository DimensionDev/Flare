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
 * @param ids * @param license Use `null` to reset the license.
 */
@Serializable
internal data class AdminEmojiSetLicenseBulkRequest(
    @SerialName(value = "ids") val ids: kotlin.collections.List<kotlin.String>,
    // Use `null` to reset the license.
    @SerialName(value = "license") val license: kotlin.String? = null,
)
