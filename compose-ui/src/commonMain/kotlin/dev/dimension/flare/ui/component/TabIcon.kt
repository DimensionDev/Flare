package dev.dimension.flare.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
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
import compose.icons.fontawesomeicons.solid.Heart
import compose.icons.fontawesomeicons.solid.House
import compose.icons.fontawesomeicons.solid.List
import compose.icons.fontawesomeicons.solid.MagnifyingGlass
import compose.icons.fontawesomeicons.solid.Message
import compose.icons.fontawesomeicons.solid.RectangleList
import compose.icons.fontawesomeicons.solid.SquareRss
import compose.icons.fontawesomeicons.solid.Users
import dev.dimension.flare.compose.ui.Res
import dev.dimension.flare.compose.ui.antenna_title
import dev.dimension.flare.compose.ui.dm_list_title
import dev.dimension.flare.compose.ui.home_tab_bookmarks_title
import dev.dimension.flare.compose.ui.home_tab_discover_title
import dev.dimension.flare.compose.ui.home_tab_favorite_title
import dev.dimension.flare.compose.ui.home_tab_featured_title
import dev.dimension.flare.compose.ui.home_tab_feeds_title
import dev.dimension.flare.compose.ui.home_tab_home_title
import dev.dimension.flare.compose.ui.home_tab_list_title
import dev.dimension.flare.compose.ui.home_tab_me_title
import dev.dimension.flare.compose.ui.home_tab_notifications_title
import dev.dimension.flare.compose.ui.liked_title
import dev.dimension.flare.compose.ui.mastodon_tab_local_title
import dev.dimension.flare.compose.ui.mastodon_tab_public_title
import dev.dimension.flare.compose.ui.mixed_timeline_title
import dev.dimension.flare.compose.ui.rss_title
import dev.dimension.flare.compose.ui.settings_title
import dev.dimension.flare.compose.ui.social_title
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.TabItem
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.component.platform.PlatformTextStyle
import dev.dimension.flare.ui.component.platform.placeholder
import dev.dimension.flare.ui.icons.Misskey
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.UserPresenter
import dev.dimension.flare.ui.theme.PlatformContentColor
import dev.dimension.flare.ui.theme.PlatformTheme
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
public fun TabTitle(
    title: TitleType,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    style: TextStyle = PlatformTextStyle.current,
) {
    PlatformText(
        text =
            when (title) {
                is TitleType.Localized -> stringResource(title.res)
                is TitleType.Text -> title.content
            },
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        style = style,
        maxLines = 1,
    )
}

@Composable
public fun TabIcon(
    tabItem: TabItem,
    modifier: Modifier = Modifier,
    iconOnly: Boolean = false,
    color: Color = PlatformContentColor.current,
) {
    TabIcon(
        accountType = tabItem.account,
        icon = tabItem.metaData.icon,
        title = tabItem.metaData.title,
        modifier = modifier,
        iconOnly = iconOnly,
        color = color,
    )
}

@Composable
public fun TabIcon(
    accountType: AccountType,
    icon: IconType,
    title: TitleType,
    modifier: Modifier = Modifier,
    iconOnly: Boolean = false,
    color: Color = PlatformContentColor.current,
    size: Dp = 24.dp,
) {
    when (icon) {
        is IconType.Avatar -> {
            val userState by producePresenter(key = "$accountType:${icon.userKey}") {
                remember(accountType, icon.userKey) {
                    UserPresenter(
                        accountType,
                        icon.userKey,
                    )
                }.body()
            }
            userState.user
                .onSuccess {
                    AvatarComponent(it.avatar, size = size, modifier = modifier)
                }.onLoading {
                    AvatarComponent(null, size = size, modifier = modifier.placeholder(true))
                }
        }

        is IconType.Material -> {
            FAIcon(
                imageVector = icon.icon.toIcon(),
                contentDescription =
                    when (title) {
                        is TitleType.Localized -> stringResource(title.res)
                        is TitleType.Text -> title.content
                    },
                modifier =
                    modifier
                        .size(size),
                tint = color,
            )
        }

        is IconType.Mixed -> {
            if (iconOnly) {
                FAIcon(
                    imageVector = icon.icon.toIcon(),
                    contentDescription =
                        when (title) {
                            is TitleType.Localized -> stringResource(title.res)
                            is TitleType.Text -> title.content
                        },
                    modifier =
                        modifier
                            .size(size),
                    tint = color,
                )
            } else {
                val userState by producePresenter(key = "$accountType:${icon.userKey}") {
                    remember(accountType, icon.userKey) {
                        UserPresenter(
                            accountType,
                            icon.userKey,
                        )
                    }.body()
                }
                Box(
                    modifier = modifier,
                ) {
                    userState.user
                        .onSuccess {
                            AvatarComponent(it.avatar, size = size)
                        }.onLoading {
                            AvatarComponent(
                                null,
                                size = size,
                                modifier = Modifier.placeholder(true),
                            )
                        }
                    FAIcon(
                        imageVector = icon.icon.toIcon(),
                        contentDescription =
                            when (title) {
                                is TitleType.Localized -> stringResource(title.res)
                                is TitleType.Text -> title.content
                            },
                        modifier =
                            Modifier
                                .size(size / 2)
                                .align(Alignment.BottomEnd)
                                .background(PlatformTheme.colorScheme.card, shape = CircleShape)
                                .padding(2.dp),
                    )
                }
            }
        }

        is IconType.Url -> {
            NetworkImage(
                icon.url,
                contentDescription =
                    when (title) {
                        is TitleType.Localized -> stringResource(title.res)
                        is TitleType.Text -> title.content
                    },
                modifier =
                    modifier
                        .size(size),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

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
            TitleType.Localized.LocalizedKey.Liked -> Res.string.liked_title
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
        IconType.Material.MaterialIcon.Heart -> FontAwesomeIcons.Solid.Heart
        IconType.Material.MaterialIcon.Twitter -> FontAwesomeIcons.Brands.Twitter
        IconType.Material.MaterialIcon.Mastodon -> FontAwesomeIcons.Brands.Mastodon
        IconType.Material.MaterialIcon.Misskey -> FontAwesomeIcons.Brands.Misskey
        IconType.Material.MaterialIcon.Bluesky -> FontAwesomeIcons.Brands.Bluesky
        IconType.Material.MaterialIcon.List -> FontAwesomeIcons.Solid.List
        IconType.Material.MaterialIcon.Feeds -> FontAwesomeIcons.Solid.SquareRss
        IconType.Material.MaterialIcon.Messages -> FontAwesomeIcons.Solid.Message
        IconType.Material.MaterialIcon.Rss -> FontAwesomeIcons.Solid.SquareRss
    }
