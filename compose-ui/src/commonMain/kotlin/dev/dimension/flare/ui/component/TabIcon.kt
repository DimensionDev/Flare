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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import dev.dimension.flare.compose.ui.Res
import dev.dimension.flare.compose.ui.all_rss_feeds_title
import dev.dimension.flare.compose.ui.antenna_title
import dev.dimension.flare.compose.ui.channel_title
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
import dev.dimension.flare.compose.ui.posts_title
import dev.dimension.flare.compose.ui.rss_title
import dev.dimension.flare.compose.ui.settings_title
import dev.dimension.flare.compose.ui.social_title
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.TabItem
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.ui.component.platform.PlatformText
import dev.dimension.flare.ui.component.status.toImageVector
import dev.dimension.flare.ui.component.platform.PlatformTextStyle
import dev.dimension.flare.ui.model.onLoading
import dev.dimension.flare.ui.model.onSuccess
import dev.dimension.flare.ui.presenter.home.FavIconPresenter
import dev.dimension.flare.ui.presenter.home.UserPresenter
import dev.dimension.flare.ui.presenter.invoke
import dev.dimension.flare.ui.theme.PlatformContentColor
import dev.dimension.flare.ui.theme.PlatformTheme
import moe.tlaster.precompose.molecule.producePresenter
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
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

@HiddenFromObjC
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

@HiddenFromObjC
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
                }.invoke()
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
                imageVector = icon.icon.toImageVector(),
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
                    imageVector = icon.icon.toImageVector(),
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
                    }.invoke()
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
                        imageVector = icon.icon.toImageVector(),
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

        is IconType.FavIcon -> {
            val iconState by producePresenter(key = "fav-$accountType:${icon.host}") {
                remember(accountType, icon) {
                    FavIconPresenter(
                        icon.host,
                    )
                }.invoke()
            }
            iconState
                .onSuccess {
                    NetworkImage(
                        it,
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
                }.onLoading {
                    AvatarComponent(
                        null,
                        size = size,
                        modifier = Modifier.placeholder(true),
                    )
                }
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
            TitleType.Localized.LocalizedKey.AllRssFeeds -> Res.string.all_rss_feeds_title
            TitleType.Localized.LocalizedKey.Posts -> Res.string.posts_title
            TitleType.Localized.LocalizedKey.Channel -> Res.string.channel_title
}
