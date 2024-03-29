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
 * @param tag * @param mentionedUsersCount * @param mentionedLocalUsersCount * @param mentionedRemoteUsersCount * @param attachedUsersCount * @param attachedLocalUsersCount * @param attachedRemoteUsersCount */
@Serializable
internal data class Hashtag(
    @SerialName(value = "tag") val tag: kotlin.String,
    @SerialName(value = "mentionedUsersCount") val mentionedUsersCount: kotlin.Double,
    @SerialName(value = "mentionedLocalUsersCount") val mentionedLocalUsersCount: kotlin.Double,
    @SerialName(value = "mentionedRemoteUsersCount") val mentionedRemoteUsersCount: kotlin.Double,
    @SerialName(value = "attachedUsersCount") val attachedUsersCount: kotlin.Double,
    @SerialName(value = "attachedLocalUsersCount") val attachedLocalUsersCount: kotlin.Double,
    @SerialName(value = "attachedRemoteUsersCount") val attachedRemoteUsersCount: kotlin.Double,
)
