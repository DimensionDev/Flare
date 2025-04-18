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

import dev.dimension.flare.data.network.xqt.model.DMUserResults
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 *
 *
 * @param userResults
 * @param lastReadEventId
 * @param joinTimeMillis
 * @param joinConversationEventId
 */
@Serializable
internal data class DMParticipantMetadata(
    @SerialName(value = "user_results")
    val userResults: DMUserResults? = null,
    @SerialName(value = "last_read_event_id")
    val lastReadEventId: kotlin.String? = null,
    @SerialName(value = "join_time_millis")
    val joinTimeMillis: kotlin.String? = null,
    @SerialName(value = "join_conversation_event_id")
    val joinConversationEventId: kotlin.String? = null,
)
