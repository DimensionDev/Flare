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
 * @param component
 * @param details
 * @param element
 */
@Serializable
internal data class ClientEventInfo(
    @SerialName(value = "component")
    val component: kotlin.String? = null,
//    @Contextual @SerialName(value = "details")
//    val details: kotlin.collections.Map<kotlin.String, kotlin.Any>? = null,
    @SerialName(value = "element")
    val element: kotlin.String? = null,
)
