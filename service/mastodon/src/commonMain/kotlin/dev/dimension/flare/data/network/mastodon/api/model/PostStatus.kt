package dev.dimension.flare.data.network.mastodon.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class PostStatus(
    val status: String? = null,
    @SerialName("in_reply_to_id")
    val inReplyToID: String? = null,
    @SerialName("media_ids")
    val mediaIDS: List<String>? = null,
    val sensitive: Boolean? = null,
    @SerialName("spoiler_text")
    val spoilerText: String? = null,
    val visibility: Visibility? = null,
    val poll: PostPoll? = null,
    @SerialName("scheduled_at")
    val scheduledAt: String? = null,
    @SerialName("language")
    val language: String? = null,
    // Pleroma-specific parameter: used only by Pleroma servers
    @SerialName("quote_id")
    val quoteID: String? = null,
    // Mastodon-specific parameter: used only by Mastodon servers
    @SerialName("quoted_status_id")
    val quotedStatusID: String? = null,
    @SerialName("quote_approval_policy")
    val quoteApprovalPolicy: String? = null,
)
