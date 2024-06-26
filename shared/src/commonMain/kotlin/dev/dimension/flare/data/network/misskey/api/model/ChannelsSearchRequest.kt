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
 * @param query * @param type * @param sinceId * @param untilId * @param limit */
@Serializable
internal data class ChannelsSearchRequest(
    @SerialName(value = "query") val query: kotlin.String,
    @SerialName(value = "type") val type: ChannelsSearchRequest.Type? = Type.NameAndDescription,
    @SerialName(value = "sinceId") val sinceId: kotlin.String? = null,
    @SerialName(value = "untilId") val untilId: kotlin.String? = null,
    @SerialName(value = "limit") val limit: kotlin.Int? = 5,
) {
    /**
     * *
     * Values: NameAndDescription,NameOnly
     */
    @Serializable
    enum class Type(
        val value: kotlin.String,
    ) {
        @SerialName(value = "nameAndDescription")
        NameAndDescription("nameAndDescription"),

        @SerialName(value = "nameOnly")
        NameOnly("nameOnly"),
    }
}
