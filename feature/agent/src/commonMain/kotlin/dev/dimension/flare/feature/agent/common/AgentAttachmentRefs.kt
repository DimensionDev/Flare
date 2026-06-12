package dev.dimension.flare.feature.agent.common

import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2

internal fun UiTimelineV2.Post.agentAttachmentRef(): String = "${platformType.name}:${statusKey.host}:${statusKey.id}"

internal fun UiProfile.agentAttachmentRef(): String = "${platformType.name}:${key.host}:${key.id}"

internal fun UiTimelineV2.Post.agentAttachmentMarker(): String = "[[post:${agentAttachmentRef()}]]"

internal fun UiProfile.agentAttachmentMarker(): String = "[[user:${agentAttachmentRef()}]]"

internal tailrec fun UiTimelineV2.Post.agentDisplayPost(): UiTimelineV2.Post {
    val repost = internalRepost ?: return this
    return repost.agentDisplayPost()
}
