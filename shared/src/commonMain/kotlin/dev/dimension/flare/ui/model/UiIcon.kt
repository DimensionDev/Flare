package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Serializable
@Immutable
public enum class UiIcon {
    Like,
    Unlike,
    Retweet,
    Unretweet,
    Reply,
    Comment,
    Quote,
    Bookmark,
    Unbookmark,
    More,
    MoreVerticel,
    Delete,
    Report,
    React,
    UnReact,
    Share,
    List,
    ChatMessage,
    Mute,
    UnMute,
    Block,
    UnBlock,

    Follow,
    Favourite,
    Mention,
    Poll,
    Edit,
    Info,
    Pin,
    Check,
}
