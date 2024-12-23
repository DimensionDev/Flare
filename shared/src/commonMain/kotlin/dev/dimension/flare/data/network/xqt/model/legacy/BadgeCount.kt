package dev.dimension.flare.data.network.xqt.model.legacy

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class BadgeCount(
    @SerialName("ntab_unread_count")
    val ntabUnreadCount: Long? = null,
    @SerialName("dm_unread_count")
    val dmUnreadCount: Long? = null,
    @SerialName("total_unread_count")
    val totalUnreadCount: Long? = null,
    @SerialName("is_from_urt")
    val isFromUrt: Boolean? = null,
)
