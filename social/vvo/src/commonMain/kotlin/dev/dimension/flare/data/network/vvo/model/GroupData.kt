package dev.dimension.flare.data.network.vvo.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class GroupListData(
    val groups: List<VVOGroup>,
)

@Serializable
internal data class VVOGroup(
    val gid: String,
    val name: String,
)

@Serializable
internal data class GroupTimelineData(
    val statuses: List<Status>? = null,
    val cards: List<Card>? = null,
    @SerialName("max_id")
    val maxId: String? = null,
)
