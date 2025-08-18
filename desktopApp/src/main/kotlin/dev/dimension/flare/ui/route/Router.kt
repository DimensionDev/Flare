package dev.dimension.flare.ui.route

import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import dev.dimension.flare.data.model.Bluesky
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.ListTimelineTabItem
import dev.dimension.flare.data.model.TabMetaData
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.ui.screen.feeds.FeedListScreen
import dev.dimension.flare.ui.screen.home.DiscoverScreen
import dev.dimension.flare.ui.screen.home.NotificationScreen
import dev.dimension.flare.ui.screen.home.ProfileScreen
import dev.dimension.flare.ui.screen.home.TimelineScreen
import dev.dimension.flare.ui.screen.list.AllListScreen
import dev.dimension.flare.ui.screen.serviceselect.ServiceSelectScreen
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
        manager.currentEntry,
        modifier = modifier,
    ) { entry ->
        entry.Content { route ->
            when (route) {
                is Route.AllLists -> {
                    AllListScreen(
                        accountType = route.accountType,
                        onAddList = {
                        },
                        toList = {
                            navigate(
                                Route.Timeline(
                                    ListTimelineTabItem(
                                        account = route.accountType,
                                        listId = it.id,
                                        metaData =
                                            TabMetaData(
                                                title = TitleType.Text(it.title),
                                                icon = IconType.Material(IconType.Material.MaterialIcon.List),
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
                                Route.Timeline(
                                    Bluesky.FeedTabItem(
                                        account = route.accountType,
                                        uri = it.id,
                                        metaData =
                                            TabMetaData(
                                                title = TitleType.Text(it.title),
                                                icon = IconType.Material(IconType.Material.MaterialIcon.Feeds),
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
            }
        }
    }
}
