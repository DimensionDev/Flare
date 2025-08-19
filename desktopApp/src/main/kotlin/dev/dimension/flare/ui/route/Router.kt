package dev.dimension.flare.ui.route

import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import dev.dimension.flare.data.model.Bluesky.FeedTabItem
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.IconType.Material
import dev.dimension.flare.data.model.ListTimelineTabItem
import dev.dimension.flare.data.model.TabMetaData
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.ui.route.Route.Timeline
import dev.dimension.flare.ui.screen.feeds.FeedListScreen
import dev.dimension.flare.ui.screen.home.DiscoverScreen
import dev.dimension.flare.ui.screen.home.NotificationScreen
import dev.dimension.flare.ui.screen.home.ProfileScreen
import dev.dimension.flare.ui.screen.home.TimelineScreen
import dev.dimension.flare.ui.screen.list.AllListScreen
import dev.dimension.flare.ui.screen.serviceselect.ServiceSelectScreen
import dev.dimension.flare.ui.screen.status.StatusScreen
import dev.dimension.flare.ui.screen.status.VVOCommentScreen
import dev.dimension.flare.ui.screen.status.VVOStatusScreen
import dev.dimension.flare.ui.screen.status.action.AddReactionSheet
import dev.dimension.flare.ui.screen.status.action.BlueskyReportStatusDialog
import dev.dimension.flare.ui.screen.status.action.DeleteStatusConfirmDialog
import dev.dimension.flare.ui.screen.status.action.MastodonReportDialog
import dev.dimension.flare.ui.screen.status.action.MisskeyReportDialog
import io.github.composefluent.component.Text

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun Router(
    manager: StackManager,
    modifier: Modifier = Modifier,
) {
    fun navigate(route: Route) {
        manager.push(route)
    }

    fun onBack() {
        manager.pop()
    }
    AnimatedContent(
        manager.currentScreenEntry,
        modifier = modifier,
    ) { entry ->
        entry.Content { route ->
            if (route is Route.ScreenRoute) {
                when (route) {
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
                                                    icon = Material(IconType.Material.MaterialIcon.List),
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
                                                    icon = Material(IconType.Material.MaterialIcon.Feeds),
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
                        )
                    }

                    Route.Rss -> {
                        Text("route")
                    }

                    Route.ServiceSelect -> {
                        ServiceSelectScreen(
                            onBack = ::onBack,
                            onVVO = {
                            },
                            onXQT = {
                            },
                        )
                    }

                    Route.Settings -> {
                        Text("route")
                    }

                    is Route.Timeline -> {
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
                }
            }
        }
    }
    manager.currentFloatingEntry?.let { entry ->
        if (entry.route is Route.FloatingRoute) {
            when (entry.route) {
                is Route.AddReaction -> {
                    AddReactionSheet(
                        accountType = entry.route.accountType,
                        statusKey = entry.route.statusKey,
                        onBack = ::onBack,
                    )
                }
                is Route.BlueskyReport -> {
                    BlueskyReportStatusDialog(
                        accountType = entry.route.accountType,
                        statusKey = entry.route.statusKey,
                        onBack = ::onBack,
                    )
                }
                is Route.DeleteStatus -> {
                    DeleteStatusConfirmDialog(
                        accountType = entry.route.accountType,
                        statusKey = entry.route.statusKey,
                        onBack = ::onBack,
                    )
                }
                is Route.MastodonReport -> {
                    MastodonReportDialog(
                        accountType = entry.route.accountType,
                        statusKey = entry.route.statusKey,
                        onBack = ::onBack,
                        userKey = entry.route.userKey,
                    )
                }
                is Route.MisskeyReport -> {
                    MisskeyReportDialog(
                        accountType = entry.route.accountType,
                        statusKey = entry.route.statusKey,
                        onBack = ::onBack,
                        userKey = entry.route.userKey,
                    )
                }
            }
        }
    }
}
