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
 * @param darkRequest
 * @param tweetId
 */
@Serializable
internal data class PostCreateRetweetRequestVariables(
    @SerialName(value = "dark_request")
    val darkRequest: kotlin.Boolean = false,
    @SerialName(value = "tweet_id")
    val tweetId: kotlin.String = "1349129669258448897",
)
