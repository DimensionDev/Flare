package dev.dimension.flare.ui.route

import androidx.compose.animation.AnimatedContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import dev.dimension.flare.ui.screen.home.ProfileScreen
import dev.dimension.flare.ui.screen.home.TimelineScreen
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
        manager.current,
    ) { entry ->
        entry.Content { route ->
            when (route) {
                is Route.AllLists -> {
                    Text("route")
                }
                is Route.BlueskyFeeds -> {
                    Text("route")
                }
                is Route.DirectMessage -> {
                    Text("route")
                }
                is Route.Discover -> {
                    Text("route")
                }
                is Route.MeRoute -> {
                    ProfileScreen(
                        accountType = route.accountType,
                        userKey = null,
                    )
                }
                is Route.Notification -> {
                    Text("route")
                }
                is Route.Profile -> {
                    ProfileScreen(
                        accountType = route.accountType,
                        userKey = route.userKey,
                    )
                }
                Route.Rss -> {
                    Text("rss")
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
