package dev.dimension.flare.data.network.xqt.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class InboxConversationTimelineMessageDataAttachmentTweet(
    @SerialName(value = "id")
    val id: String? = null,
    @SerialName(value = "url")
    val url: String? = null,
    @SerialName(value = "display_url")
    val displayUrl: String? = null,
    @SerialName(value = "expanded_url")
    val expandedUrl: String? = null,
    @SerialName(value = "indices")
    val indices: List<Int>? = null,
    @SerialName(value = "status")
    val status: TweetLegacy? = null,
)
