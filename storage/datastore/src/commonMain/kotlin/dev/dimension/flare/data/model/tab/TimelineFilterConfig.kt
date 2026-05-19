package dev.dimension.flare.data.model.tab

import kotlinx.serialization.Serializable

@Serializable
public data class TimelineFilterConfig(
    val excludedKinds: List<TimelinePostKind> = emptyList(),
    val excludedContents: List<TimelinePostContent> = emptyList(),
)

@Serializable
public enum class TimelinePostKind {
    Original,
    Reply,
    Repost,
    Quote,
}

@Serializable
public enum class TimelinePostContent {
    Text,
    Image,
    Video,
    Other,
}
