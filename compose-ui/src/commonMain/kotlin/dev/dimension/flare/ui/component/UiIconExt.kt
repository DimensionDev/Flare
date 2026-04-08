package dev.dimension.flare.ui.component

import androidx.compose.ui.graphics.vector.ImageVector
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.Regular
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.brands.Bluesky
import compose.icons.fontawesomeicons.brands.Mastodon
import compose.icons.fontawesomeicons.brands.Tumblr
import compose.icons.fontawesomeicons.brands.Weibo
import compose.icons.fontawesomeicons.brands.XTwitter
import compose.icons.fontawesomeicons.regular.Bookmark
import compose.icons.fontawesomeicons.regular.CommentDots
import compose.icons.fontawesomeicons.regular.Heart
import compose.icons.fontawesomeicons.solid.At
import compose.icons.fontawesomeicons.solid.Bell
import compose.icons.fontawesomeicons.solid.Bookmark
import compose.icons.fontawesomeicons.solid.Check
import compose.icons.fontawesomeicons.solid.CircleInfo
import compose.icons.fontawesomeicons.solid.CircleUser
import compose.icons.fontawesomeicons.solid.Ellipsis
import compose.icons.fontawesomeicons.solid.EllipsisVertical
import compose.icons.fontawesomeicons.solid.Gear
import compose.icons.fontawesomeicons.solid.Globe
import compose.icons.fontawesomeicons.solid.Heart
import compose.icons.fontawesomeicons.solid.House
import compose.icons.fontawesomeicons.solid.List
import compose.icons.fontawesomeicons.solid.MagnifyingGlass
import compose.icons.fontawesomeicons.solid.Message
import compose.icons.fontawesomeicons.solid.Minus
import compose.icons.fontawesomeicons.solid.Pen
import compose.icons.fontawesomeicons.solid.Plus
import compose.icons.fontawesomeicons.solid.RectangleList
import compose.icons.fontawesomeicons.solid.Reply
import compose.icons.fontawesomeicons.solid.Retweet
import compose.icons.fontawesomeicons.solid.ShareNodes
import compose.icons.fontawesomeicons.solid.SquarePollHorizontal
import compose.icons.fontawesomeicons.solid.SquareRss
import compose.icons.fontawesomeicons.solid.Thumbtack
import compose.icons.fontawesomeicons.solid.Trash
import compose.icons.fontawesomeicons.solid.Tv
import compose.icons.fontawesomeicons.solid.UserPlus
import compose.icons.fontawesomeicons.solid.UserSlash
import compose.icons.fontawesomeicons.solid.Users
import compose.icons.fontawesomeicons.solid.VolumeXmark
import dev.dimension.flare.ui.icons.Misskey
import dev.dimension.flare.ui.icons.Nostr
import dev.dimension.flare.ui.model.UiIcon

public fun UiIcon.toImageVector(): ImageVector =
    when (this) {
        UiIcon.Like -> FontAwesomeIcons.Regular.Heart
        UiIcon.Unlike -> FontAwesomeIcons.Solid.Heart
        UiIcon.Retweet -> FontAwesomeIcons.Solid.Retweet
        UiIcon.Unretweet -> FontAwesomeIcons.Solid.Retweet
        UiIcon.Reply -> FontAwesomeIcons.Solid.Reply
        UiIcon.Comment -> FontAwesomeIcons.Regular.CommentDots
        UiIcon.Quote -> FontAwesomeIcons.Solid.Reply
        UiIcon.Bookmark -> FontAwesomeIcons.Regular.Bookmark
        UiIcon.Unbookmark -> FontAwesomeIcons.Solid.Bookmark
        UiIcon.More -> FontAwesomeIcons.Solid.Ellipsis
        UiIcon.Delete -> FontAwesomeIcons.Solid.Trash
        UiIcon.Report -> FontAwesomeIcons.Solid.CircleInfo
        UiIcon.React -> FontAwesomeIcons.Solid.Plus
        UiIcon.UnReact -> FontAwesomeIcons.Solid.Minus
        UiIcon.Share -> FontAwesomeIcons.Solid.ShareNodes
        UiIcon.MoreVerticel -> FontAwesomeIcons.Solid.EllipsisVertical
        UiIcon.List -> FontAwesomeIcons.Solid.List
        UiIcon.ChatMessage -> FontAwesomeIcons.Solid.Message
        UiIcon.Mute -> FontAwesomeIcons.Solid.VolumeXmark
        UiIcon.UnMute -> FontAwesomeIcons.Solid.VolumeXmark
        UiIcon.Block -> FontAwesomeIcons.Solid.UserSlash
        UiIcon.UnBlock -> FontAwesomeIcons.Solid.UserSlash
        UiIcon.Follow -> FontAwesomeIcons.Solid.UserPlus
        UiIcon.Favourite -> FontAwesomeIcons.Solid.Heart
        UiIcon.Mention -> FontAwesomeIcons.Solid.At
        UiIcon.Poll -> FontAwesomeIcons.Solid.SquarePollHorizontal
        UiIcon.Edit -> FontAwesomeIcons.Solid.Pen
        UiIcon.Info -> FontAwesomeIcons.Solid.CircleInfo
        UiIcon.Pin -> FontAwesomeIcons.Solid.Thumbtack
        UiIcon.Check -> FontAwesomeIcons.Solid.Check
        UiIcon.Home -> FontAwesomeIcons.Solid.House
        UiIcon.Notification -> FontAwesomeIcons.Solid.Bell
        UiIcon.Search -> FontAwesomeIcons.Solid.MagnifyingGlass
        UiIcon.Profile -> FontAwesomeIcons.Solid.CircleUser
        UiIcon.Settings -> FontAwesomeIcons.Solid.Gear
        UiIcon.Local -> FontAwesomeIcons.Solid.Users
        UiIcon.World -> FontAwesomeIcons.Solid.Globe
        UiIcon.Featured -> FontAwesomeIcons.Solid.RectangleList
        UiIcon.Feeds -> FontAwesomeIcons.Solid.SquareRss
        UiIcon.Messages -> FontAwesomeIcons.Solid.Message
        UiIcon.Rss -> FontAwesomeIcons.Solid.SquareRss
        UiIcon.Channel -> FontAwesomeIcons.Solid.Tv
        UiIcon.Heart -> FontAwesomeIcons.Solid.Heart
        UiIcon.Mastodon -> FontAwesomeIcons.Brands.Mastodon
        UiIcon.Misskey -> FontAwesomeIcons.Brands.Misskey
        UiIcon.Bluesky -> FontAwesomeIcons.Brands.Bluesky
        UiIcon.Tumblr -> FontAwesomeIcons.Brands.Tumblr
        UiIcon.Nostr -> FontAwesomeIcons.Brands.Nostr
        UiIcon.Twitter -> FontAwesomeIcons.Brands.XTwitter
        UiIcon.X -> FontAwesomeIcons.Brands.XTwitter
        UiIcon.Weibo -> FontAwesomeIcons.Brands.Weibo
    }
