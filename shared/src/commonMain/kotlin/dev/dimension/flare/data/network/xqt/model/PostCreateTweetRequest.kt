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
 * @param features
 * @param queryId
 * @param variables
 */
@Serializable
internal data class PostCreateTweetRequest(
    @SerialName(value = "features")
    val features: PostCreateTweetRequestFeatures,
    @SerialName(value = "queryId")
    val queryId: kotlin.String = "PIZtQLRIYtSa9AtW_fI2Mw",
    @SerialName(value = "variables")
    val variables: PostCreateTweetRequestVariables,
)

@Serializable
internal data class CreateBookmarkRequest(
    @SerialName("variables")
    val variables: CreateBookmarkRequestVariables,
    @SerialName("queryId")
    val queryId: String = "aoDbu3RHznuiSkQ9aNM67Q",
)

@Serializable
internal data class CreateBookmarkRequestVariables(
    @SerialName("tweet_id")
    val tweetId: String,
    @SerialName("darkRequest")
    val darkRequest: Boolean = false,
)

@Serializable
internal data class CreateBookmark200Response(
    @SerialName("data")
    val data: CreateBookmark200ResponseData,
)

@Serializable
internal data class CreateBookmark200ResponseData(
    @SerialName("tweet_bookmark_put")
    val tweet_bookmark_put: String,
)

@Serializable
internal data class DeleteBookmarkRequest(
    @SerialName("variables")
    val variables: DeleteBookmarkRequestVariables,
    @SerialName("queryId")
    val queryId: String = "Wlmlj2-xzyS1GN3a6cj-mQ",
)

@Serializable
internal data class DeleteBookmarkRequestVariables(
    @SerialName("tweet_id")
    val tweetId: String,
    @SerialName("darkRequest")
    val darkRequest: Boolean = false,
)

@Serializable
internal data class DeleteBookmark200Response(
    @SerialName("data")
    val data: DeleteBookmark200ResponseData,
)

@Serializable
internal data class DeleteBookmark200ResponseData(
    @SerialName("tweet_bookmark_delete")
    val tweet_bookmark_delete: String,
)