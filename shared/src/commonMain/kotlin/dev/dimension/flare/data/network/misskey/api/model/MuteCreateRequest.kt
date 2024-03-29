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
 * @param userId * @param expiresAt A Unix Epoch timestamp that must lie in the future. `null` means an indefinite mute.
 */
@Serializable
internal data class MuteCreateRequest(
    @SerialName(value = "userId") val userId: kotlin.String,
    // A Unix Epoch timestamp that must lie in the future. `null` means an indefinite mute.
    @SerialName(value = "expiresAt") val expiresAt: kotlin.Int? = null,
)
