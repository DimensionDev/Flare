package dev.dimension.flare.data.model

import android.content.Context
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
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
import dev.dimension.flare.R
import dev.dimension.flare.ui.icons.Misskey

internal val Context.appearanceSettings: DataStore<AppearanceSettings> by dataStore(
    fileName = "appearance_settings.pb",
    serializer = AccountPreferencesSerializer,
)
internal val Context.appSettings: DataStore<AppSettings> by dataStore(
    fileName = "app_settings.pb",
    serializer = AppSettingsSerializer,
)

internal val TitleType.Localized.resId: Int
    get() =
        when (key) {
            TitleType.Localized.LocalizedKey.Home -> R.string.home_tab_home_title
            TitleType.Localized.LocalizedKey.Notifications -> R.string.home_tab_notifications_title
            TitleType.Localized.LocalizedKey.Discover -> R.string.home_tab_discover_title
            TitleType.Localized.LocalizedKey.Me -> R.string.home_tab_me_title
            TitleType.Localized.LocalizedKey.Settings -> R.string.settings_title
            TitleType.Localized.LocalizedKey.MastodonLocal -> R.string.mastodon_tab_local_title
            TitleType.Localized.LocalizedKey.MastodonPublic -> R.string.mastodon_tab_public_title
            TitleType.Localized.LocalizedKey.Featured -> R.string.home_tab_featured_title
            TitleType.Localized.LocalizedKey.Bookmark -> R.string.home_tab_bookmarks_title
            TitleType.Localized.LocalizedKey.Favourite -> R.string.home_tab_favorite_title
            TitleType.Localized.LocalizedKey.List -> R.string.home_tab_list_title
            TitleType.Localized.LocalizedKey.Feeds -> R.string.home_tab_feeds_title
            TitleType.Localized.LocalizedKey.DirectMessage -> R.string.dm_list_title
            TitleType.Localized.LocalizedKey.Rss -> R.string.rss_title
            TitleType.Localized.LocalizedKey.Antenna -> R.string.home_tab_antennas_title
            TitleType.Localized.LocalizedKey.MixedTimeline -> R.string.home_tab_mixed_timeline_title
            TitleType.Localized.LocalizedKey.Social -> R.string.home_tab_social_timeline_title
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

internal val Context.tabSettings: DataStore<TabSettings> by dataStore(
    fileName = "tab_settings.pb",
    serializer = TabSettingsSerializer,
    corruptionHandler =
        ReplaceFileCorruptionHandler {
            TabSettingsSerializer.defaultValue
        },
)
