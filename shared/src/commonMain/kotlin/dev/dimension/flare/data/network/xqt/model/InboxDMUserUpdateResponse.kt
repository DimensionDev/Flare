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

import dev.dimension.flare.data.network.xqt.model.InboxDMUserUpdateResponseInboxInitialState
import dev.dimension.flare.data.network.xqt.model.InboxDMUserUpdateResponseUserEvents
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * get dm user updates
 *
 * @param userEvents
 * @param inboxInitialState
 */
@Serializable
internal data class InboxDMUserUpdateResponse(
    @SerialName(value = "user_events")
    val userEvents: InboxDMUserUpdateResponseUserEvents? = null,
    @SerialName(value = "inbox_initial_state")
    val inboxInitialState: InboxDMUserUpdateResponseInboxInitialState? = null,
)
