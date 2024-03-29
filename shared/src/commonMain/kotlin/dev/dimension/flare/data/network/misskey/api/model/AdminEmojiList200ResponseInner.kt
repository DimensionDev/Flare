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
 * @param id * @param aliases * @param name * @param category * @param host The local host is represented with `null`. The field exists for compatibility with other API endpoints that return files.
 * @param url */
@Serializable
internal data class AdminEmojiList200ResponseInner(
    @SerialName(value = "id") val id: kotlin.String,
    @SerialName(value = "aliases") val aliases: kotlin.collections.List<kotlin.String>,
    @SerialName(value = "name") val name: kotlin.String,
    @SerialName(value = "category") val category: kotlin.String? = null,
    // The local host is represented with `null`. The field exists for compatibility with other API endpoints that return files.
    @SerialName(value = "host") val host: kotlin.String? = null,
    @SerialName(value = "url") val url: kotlin.String,
)
