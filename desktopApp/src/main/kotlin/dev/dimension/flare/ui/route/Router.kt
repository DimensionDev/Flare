package dev.dimension.flare.ui.route

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.dimension.flare.common.OnDeepLink
import dev.dimension.flare.data.model.Bluesky.FeedTabItem
import dev.dimension.flare.data.model.IconType.Material
import dev.dimension.flare.data.model.ListTimelineTabItem
import dev.dimension.flare.data.model.RssTimelineTabItem
import dev.dimension.flare.data.model.TabMetaData
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.AccountType.Specific
import dev.dimension.flare.ui.presenter.compose.ComposeStatus
import dev.dimension.flare.ui.route.Route.Profile
import dev.dimension.flare.ui.route.Route.Search
import dev.dimension.flare.ui.route.Route.Timeline
import dev.dimension.flare.ui.screen.compose.ComposeDialog
import dev.dimension.flare.ui.screen.feeds.FeedListScreen
import dev.dimension.flare.ui.screen.home.DiscoverScreen
import dev.dimension.flare.ui.screen.home.NotificationScreen
import dev.dimension.flare.ui.screen.home.ProfileScreen
import dev.dimension.flare.ui.screen.home.ProfileWithUserNameAndHostDeeplinkRoute
import dev.dimension.flare.ui.screen.home.SearchScreen
import dev.dimension.flare.ui.screen.home.TimelineScreen
import dev.dimension.flare.ui.screen.list.AllListScreen
import dev.dimension.flare.ui.screen.media.RawMediaScreen
import dev.dimension.flare.ui.screen.media.StatusMediaScreen
import dev.dimension.flare.ui.screen.rss.EditRssSourceScreen
import dev.dimension.flare.ui.screen.rss.RssListScreen
import dev.dimension.flare.ui.screen.serviceselect.ServiceSelectScreen
import dev.dimension.flare.ui.screen.settings.SettingsScreen
import dev.dimension.flare.ui.screen.status.StatusScreen
import dev.dimension.flare.ui.screen.status.VVOCommentScreen
import dev.dimension.flare.ui.screen.status.VVOStatusScreen
import dev.dimension.flare.ui.screen.status.action.AddReactionSheet
import dev.dimension.flare.ui.screen.status.action.BlueskyReportStatusDialog
import dev.dimension.flare.ui.screen.status.action.DeleteStatusConfirmDialog
import dev.dimension.flare.ui.screen.status.action.MastodonReportDialog
import dev.dimension.flare.ui.screen.status.action.MisskeyReportDialog
import io.github.composefluent.component.FluentDialog
import io.github.composefluent.component.Flyout
import io.github.composefluent.component.Text

@Composable
internal fun Router(
    manager: StackManager,
    onWindowRoute: (Route.WindowRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    fun navigate(route: Route) {
        if (route is Route.WindowRoute) {
            onWindowRoute(route)
        } else {
            manager.push(route)
        }
    }

    fun onBack() {
        manager.pop()
    }
    OnDeepLink {
        val route = Route.parse(it)
        if (route != null) {
            navigate(route)
        }
        route != null
    }
    AnimatedContent(
        manager.currentScreenEntry,
        modifier = modifier,
    ) { entry ->
        entry.Content { route ->
            RouteContent(
                route = route,
                onBack = ::onBack,
                navigate = ::navigate,
            )
        }
    }
    manager.currentFloatingEntry?.let { entry ->
        entry.Content { route ->
            RouteContent(
                route = route,
                onBack = ::onBack,
                navigate = ::navigate,
            )
        }
    }
}

@Composable
internal fun RouteContent(
    route: Route,
    onBack: () -> Unit,
    navigate: (Route) -> Unit,
) {
    when (route) {
        is Route.RssDetail -> Unit
        is Route.AddReaction -> {
            AddReactionSheet(
                accountType = route.accountType,
                statusKey = route.statusKey,
                onBack = onBack,
            )
        }

        is Route.BlueskyReport -> {
            BlueskyReportStatusDialog(
                accountType = route.accountType,
                statusKey = route.statusKey,
                onBack = onBack,
            )
        }

        is Route.DeleteStatus -> {
            DeleteStatusConfirmDialog(
                accountType = route.accountType,
                statusKey = route.statusKey,
                onBack = onBack,
            )
        }

        is Route.MastodonReport -> {
            MastodonReportDialog(
                accountType = route.accountType,
                statusKey = route.statusKey,
                onBack = onBack,
                userKey = route.userKey,
            )
        }

        is Route.MisskeyReport -> {
            MisskeyReportDialog(
                accountType = route.accountType,
                statusKey = route.statusKey,
                onBack = onBack,
                userKey = route.userKey,
            )
        }

        is Route.AltText -> {
            Flyout(
                visible = true,
                onDismissRequest = {
                    onBack()
                },
            ) {
                Text(
                    text = route.text,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        Route.CreateRssSource -> {
            EditRssSourceScreen(
                onDismissRequest = onBack,
                id = null,
            )
        }

        is Route.EditRssSource -> {
            EditRssSourceScreen(
                onDismissRequest = onBack,
                id = route.id,
            )
        }
        is Route.Compose.New ->
            FluentDialog(
                visible = true,
            ) {
                ComposeDialog(
                    onBack = onBack,
                    accountType = route.accountType,
                )
            }

        is Route.Compose.Quote ->
            FluentDialog(visible = true) {
                ComposeDialog(
                    onBack = onBack,
                    status = ComposeStatus.Quote(route.statusKey),
                    accountType = AccountType.Specific(accountKey = route.accountKey),
                )
            }

        is Route.Compose.Reply ->
            FluentDialog(visible = true) {
                ComposeDialog(
                    onBack = onBack,
                    status = ComposeStatus.Reply(route.statusKey),
                    accountType = AccountType.Specific(accountKey = route.accountKey),
                )
            }

        is Route.Compose.VVOReplyComment ->
            FluentDialog(visible = true) {
                ComposeDialog(
                    onBack = onBack,
                    accountType = AccountType.Specific(accountKey = route.accountKey),
                    status = ComposeStatus.VVOComment(route.replyTo, route.rootId),
                )
            }

        is Route.AllLists -> {
            AllListScreen(
                accountType = route.accountType,
                onAddList = {
                },
                toList = {
                    navigate(
                        Timeline(
                            ListTimelineTabItem(
                                account = route.accountType,
                                listId = it.id,
                                metaData =
                                    TabMetaData(
                                        title = TitleType.Text(it.title),
                                        icon = Material(Material.MaterialIcon.List),
                                    ),
                            ),
                        ),
                    )
                },
            )
        }

        is Route.BlueskyFeeds -> {
            FeedListScreen(
                accountType = route.accountType,
                toFeed = {
                    navigate(
                        Timeline(
                            FeedTabItem(
                                account = route.accountType,
                                uri = it.id,
                                metaData =
                                    TabMetaData(
                                        title = TitleType.Text(it.title),
                                        icon = Material(Material.MaterialIcon.Feeds),
                                    ),
                            ),
                        ),
                    )
                },
            )
        }

        is Route.DirectMessage -> {
            Text("route")
        }

        is Route.Discover -> {
            DiscoverScreen(
                accountType = route.accountType,
                toUser = {
                    navigate(
                        Profile(
                            accountType = route.accountType,
                            userKey = it,
                        ),
                    )
                },
                toSearch = {
                    navigate(
                        Search(
                            accountType = route.accountType,
                            keyword = it,
                        ),
                    )
                },
            )
        }

        is Route.Search -> {
            SearchScreen(
                initialQuery = route.keyword,
                accountType = route.accountType,
                toUser = {
                    navigate(
                        Profile(
                            accountType = route.accountType,
                            userKey = it,
                        ),
                    )
                },
            )
        }

        is Route.MeRoute -> {
            ProfileScreen(
                accountType = route.accountType,
                userKey = null,
            )
        }

        is Route.Notification -> {
            NotificationScreen(
                accountType = route.accountType,
            )
        }

        is Route.Profile -> {
            ProfileScreen(
                accountType = route.accountType,
                userKey = route.userKey,
                toEditAccountList = {},
                toSearchUserUsingAccount = { keyword, accountKey ->
                    navigate(
                        Search(
                            accountType = Specific(accountKey),
                            keyword = keyword,
                        ),
                    )
                },
                toStartMessage = {},
                onFollowListClick = {},
                onFansListClick = {},
            )
        }

        Route.ServiceSelect -> {
            ServiceSelectScreen(
                onBack = onBack,
                onVVO = {
                },
                onXQT = {
                },
            )
        }

        Route.Settings -> {
            SettingsScreen(
                toLogin = {
                    navigate(Route.ServiceSelect)
                },
            )
        }

        is Timeline -> {
            TimelineScreen(
                route.tabItem,
            )
        }

        is Route.StatusDetail -> {
            StatusScreen(
                statusKey = route.statusKey,
                accountType = route.accountType,
            )
        }

        is Route.VVO.CommentDetail -> {
            VVOCommentScreen(
                commentKey = route.statusKey,
                accountType = route.accountType,
            )
        }

        is Route.VVO.StatusDetail -> {
            VVOStatusScreen(
                statusKey = route.statusKey,
                accountType = route.accountType,
            )
        }

        is Route.ProfileWithNameAndHost -> {
            ProfileWithUserNameAndHostDeeplinkRoute(
                userName = route.userName,
                host = route.host,
                accountType = route.accountType,
                onBack = onBack,
                toEditAccountList = {},
                toSearchUserUsingAccount = { keyword, accountKey ->
                    navigate(
                        Search(
                            accountType = Specific(accountKey),
                            keyword = keyword,
                        ),
                    )
                },
                toStartMessage = {},
                onFollowListClick = {},
                onFansListClick = {},
            )
        }

        Route.RssList ->
            RssListScreen(
                toItem = {
                    navigate(
                        Route.RssTimeline(
                            url = it.url,
                            title = it.title,
                            id = it.id,
                        ),
                    )
                },
                onEdit = {
                    navigate(
                        Route.EditRssSource(
                            id = it.id,
                        ),
                    )
                },
                onAdd = {
                    navigate(Route.CreateRssSource)
                },
            )

        is Route.RssTimeline -> {
            TimelineScreen(
                RssTimelineTabItem(
                    feedUrl = route.url,
                    title = route.title.orEmpty(),
                ),
            )
        }

        is Route.RawImage ->
            RawMediaScreen(url = route.rawImage)
        is Route.StatusMedia ->
            StatusMediaScreen(
                accountType = route.accountType,
                statusKey = route.statusKey,
                index = route.index,
            )
    }
}
