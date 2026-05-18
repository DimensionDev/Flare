package dev.dimension.flare.data.model.tab

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
public data class TimelineFilterConfig(
    val excludedKinds: List<TimelinePostKind> = emptyList(),
    val excludedContents: List<TimelinePostContent> = emptyList(),
)

@Immutable
@Serializable
public enum class TimelinePostKind {
    Original,
    Reply,
    Repost,
    Quote,
}

@Immutable
@Serializable
public enum class TimelinePostContent {
    Text,
    Image,
    Video,
    Other,
}
