package dev.dimension.flare.data.model

import androidx.compose.ui.graphics.vector.ImageVector
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.Solid
import compose.icons.fontawesomeicons.brands.Bluesky
import compose.icons.fontawesomeicons.brands.Mastodon
import compose.icons.fontawesomeicons.brands.Twitter
import compose.icons.fontawesomeicons.solid.Bell
import compose.icons.fontawesomeicons.solid.BookBookmark
import compose.icons.fontawesomeicons.solid.CircleUser
import compose.icons.fontawesomeicons.solid.Gear
import compose.icons.fontawesomeicons.solid.Globe
import compose.icons.fontawesomeicons.solid.House
import compose.icons.fontawesomeicons.solid.List
import compose.icons.fontawesomeicons.solid.MagnifyingGlass
import compose.icons.fontawesomeicons.solid.Message
import compose.icons.fontawesomeicons.solid.RectangleList
import compose.icons.fontawesomeicons.solid.SquareRss
import compose.icons.fontawesomeicons.solid.Star
import compose.icons.fontawesomeicons.solid.Users
import dev.dimension.flare.Res
import dev.dimension.flare.antenna_title
import dev.dimension.flare.dm_list_title
import dev.dimension.flare.home_tab_bookmarks_title
import dev.dimension.flare.home_tab_discover_title
import dev.dimension.flare.home_tab_favorite_title
import dev.dimension.flare.home_tab_featured_title
import dev.dimension.flare.home_tab_feeds_title
import dev.dimension.flare.home_tab_home_title
import dev.dimension.flare.home_tab_list_title
import dev.dimension.flare.home_tab_me_title
import dev.dimension.flare.home_tab_notifications_title
import dev.dimension.flare.mastodon_tab_local_title
import dev.dimension.flare.mastodon_tab_public_title
import dev.dimension.flare.mixed_timeline_title
import dev.dimension.flare.rss_title
import dev.dimension.flare.settings_title
import dev.dimension.flare.social_title
import dev.dimension.flare.ui.icons.Misskey
import org.jetbrains.compose.resources.StringResource

internal val TitleType.Localized.res: StringResource
    get() =
        when (key) {
            TitleType.Localized.LocalizedKey.Home -> Res.string.home_tab_home_title
            TitleType.Localized.LocalizedKey.Notifications -> Res.string.home_tab_notifications_title
            TitleType.Localized.LocalizedKey.Discover -> Res.string.home_tab_discover_title
            TitleType.Localized.LocalizedKey.Me -> Res.string.home_tab_me_title
            TitleType.Localized.LocalizedKey.Settings -> Res.string.settings_title
            TitleType.Localized.LocalizedKey.MastodonLocal -> Res.string.mastodon_tab_local_title
            TitleType.Localized.LocalizedKey.MastodonPublic -> Res.string.mastodon_tab_public_title
            TitleType.Localized.LocalizedKey.Featured -> Res.string.home_tab_featured_title
            TitleType.Localized.LocalizedKey.Bookmark -> Res.string.home_tab_bookmarks_title
            TitleType.Localized.LocalizedKey.Favourite -> Res.string.home_tab_favorite_title
            TitleType.Localized.LocalizedKey.List -> Res.string.home_tab_list_title
            TitleType.Localized.LocalizedKey.Feeds -> Res.string.home_tab_feeds_title
            TitleType.Localized.LocalizedKey.DirectMessage -> Res.string.dm_list_title
            TitleType.Localized.LocalizedKey.Rss -> Res.string.rss_title
            TitleType.Localized.LocalizedKey.Social -> Res.string.social_title
            TitleType.Localized.LocalizedKey.Antenna -> Res.string.antenna_title
            TitleType.Localized.LocalizedKey.MixedTimeline -> Res.string.mixed_timeline_title
        }

internal fun IconType.Material.MaterialIcon.toIcon(): ImageVector =
    when (this) {
        IconType.Material.MaterialIcon.Home -> FontAwesomeIcons.Solid.House
        IconType.Material.MaterialIcon.Notification -> FontAwesomeIcons.Solid.Bell
        IconType.Material.MaterialIcon.Search -> FontAwesomeIcons.Solid.MagnifyingGlass
        IconType.Material.MaterialIcon.Profile -> FontAwesomeIcons.Solid.CircleUser
        IconType.Material.MaterialIcon.Settings -> FontAwesomeIcons.Solid.Gear
        IconType.Material.MaterialIcon.Local -> FontAwesomeIcons.Solid.Users
        IconType.Material.MaterialIcon.World -> FontAwesomeIcons.Solid.Globe
        IconType.Material.MaterialIcon.Featured -> FontAwesomeIcons.Solid.RectangleList
        IconType.Material.MaterialIcon.Bookmark -> FontAwesomeIcons.Solid.BookBookmark
        IconType.Material.MaterialIcon.Heart -> FontAwesomeIcons.Solid.Star
        IconType.Material.MaterialIcon.Twitter -> FontAwesomeIcons.Brands.Twitter
        IconType.Material.MaterialIcon.Mastodon -> FontAwesomeIcons.Brands.Mastodon
        IconType.Material.MaterialIcon.Misskey -> FontAwesomeIcons.Brands.Misskey
        IconType.Material.MaterialIcon.Bluesky -> FontAwesomeIcons.Brands.Bluesky
        IconType.Material.MaterialIcon.List -> FontAwesomeIcons.Solid.List
        IconType.Material.MaterialIcon.Feeds -> FontAwesomeIcons.Solid.SquareRss
        IconType.Material.MaterialIcon.Messages -> FontAwesomeIcons.Solid.Message
        IconType.Material.MaterialIcon.Rss -> FontAwesomeIcons.Solid.SquareRss
    }
