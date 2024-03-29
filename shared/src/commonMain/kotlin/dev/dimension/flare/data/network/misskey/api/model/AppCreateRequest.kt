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
 * @param name * @param description * @param permission * @param callbackUrl */
@Serializable
internal data class AppCreateRequest(
    @SerialName(value = "name") val name: kotlin.String,
    @SerialName(value = "description") val description: kotlin.String,
    @SerialName(value = "permission") val permission: kotlin.collections.Set<kotlin.String>,
    @SerialName(value = "callbackUrl") val callbackUrl: kotlin.String? = null,
)
