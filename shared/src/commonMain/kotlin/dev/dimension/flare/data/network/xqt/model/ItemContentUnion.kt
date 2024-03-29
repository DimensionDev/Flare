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

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/**
 *
 *
 * @param typename
 * @param itemType
 * @param tweetDisplayType
 * @param tweetResults
 * @param cursorType
 * @param `value`
 * @param userDisplayType
 * @param userResults
 * @param socialContext
 * @param promotedMetadata
 * @param entryType
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("__typename")
internal sealed interface ItemContentUnion {
//    @Contextual
//    @SerialName(value = "__typename")
//    val typename: TypeName
//
//    @Contextual
//    @SerialName(value = "itemType")
//    val itemType: ContentItemType
//
//    @SerialName(value = "tweetDisplayType")
//    val tweetDisplayType: kotlin.String
//
//    @SerialName(value = "tweet_results")
//    val tweetResults: ItemResult
//
//    @Contextual
//    @SerialName(value = "cursorType")
//    val cursorType: CursorType
//
//    @SerialName(value = "value")
//    val `value`: kotlin.String
//
//    @SerialName(value = "userDisplayType")
//    val userDisplayType: ItemContentUnion.UserDisplayType
//
//    @SerialName(value = "user_results")
//    val userResults: UserResults
//
//    @SerialName(value = "SocialContext")
//    val socialContext: SocialContext?
//
//    @Contextual
//    @SerialName(value = "promotedMetadata")
//    val promotedMetadata: kotlin.collections.Map<kotlin.String, kotlin.Any>?
//
//    @Contextual
//    @SerialName(value = "entryType")
//    val entryType: ContentEntryType?
//
//    /**
//     *
//     *
//     * Values: user,userDetailed,subscribableUser
//     */
//    @Serializable
//    enum class UserDisplayType(val value: kotlin.String) {
//        @SerialName(value = "User")
//        user("User"),
//
//        @SerialName(value = "UserDetailed")
//        userDetailed("UserDetailed"),
//
//        @SerialName(value = "SubscribableUser")
//        subscribableUser("SubscribableUser"),
//    }
}
