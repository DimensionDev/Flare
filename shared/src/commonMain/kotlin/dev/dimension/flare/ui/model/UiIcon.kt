package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.data.model.IconType
import kotlinx.serialization.Serializable

@Serializable
@Immutable
public enum class UiIcon {
    Home,
    Notification,
    Search,
    Profile,
    Settings,
    Local,
    World,
    Featured,
    Bookmark,
    Heart,
    Twitter,
    Mastodon,
    Misskey,
    Bluesky,
    List,
    Feeds,
    Messages,
    Rss,
    Weibo,
    Channel,

    Like,
    Unlike,
    Retweet,
    Unretweet,
    Reply,
    Comment,
    Quote,
    Unbookmark,
    More,
    MoreVerticel,
    Delete,
    Report,
    React,
    UnReact,
    Share,
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
    Nostr,
    X,
    Translate,
    UnFavourite,
}

public fun UiIcon.asType(): IconType = IconType.Material(this)