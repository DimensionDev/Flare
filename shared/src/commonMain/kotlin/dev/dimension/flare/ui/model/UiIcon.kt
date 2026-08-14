package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.data.model.IconType
import kotlinx.serialization.Serializable
import kotlin.native.HiddenFromObjC

// icon should add after the last one, otherwise it will break the serialization compatibility.
// If you want to remove an icon, please deprecate it and hide it from the tab/group icon picker instead of removing it directly.
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
    Pixiv,
    Fanbox,
    Eye,
}

/**
 * Icons exposed by the tab/group icon picker.
 *
 * Keep this list in sync with UiIcon.toImageVector(): entries that render to the
 * same Font Awesome icon should appear only once here.
 */
@HiddenFromObjC
public val TabPickerUiIcons: List<UiIcon> =
    listOf(
        UiIcon.Twitter,
        UiIcon.Mastodon,
        UiIcon.Misskey,
        UiIcon.Bluesky,
        UiIcon.Pixiv,
        UiIcon.Weibo,
        UiIcon.Nostr,
        UiIcon.X,
        UiIcon.Home,
        UiIcon.Notification,
        UiIcon.Search,
        UiIcon.Profile,
        UiIcon.Settings,
        UiIcon.Local,
        UiIcon.World,
        UiIcon.Featured,
        UiIcon.Bookmark,
        UiIcon.Heart,
        UiIcon.List,
        UiIcon.Messages,
        UiIcon.Rss,
        UiIcon.Channel,
        UiIcon.Translate,
        UiIcon.Like,
        UiIcon.Retweet,
        UiIcon.Reply,
        UiIcon.Comment,
        UiIcon.Unbookmark,
        UiIcon.More,
        UiIcon.MoreVerticel,
        UiIcon.Delete,
        UiIcon.React,
        UiIcon.UnReact,
        UiIcon.Share,
        UiIcon.Mute,
        UiIcon.Block,
        UiIcon.Follow,
        UiIcon.Favourite,
        UiIcon.Mention,
        UiIcon.Poll,
        UiIcon.Edit,
        UiIcon.Info,
        UiIcon.Eye,
        UiIcon.Pin,
        UiIcon.Check,
        UiIcon.UnFavourite,
    )

@HiddenFromObjC
public fun UiIcon.asType(): IconType = IconType.Material(this)
