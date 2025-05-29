package dev.dimension.flare.ui.route

import androidx.compose.animation.togetherWith
import androidx.compose.material3.DrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSavedStateNavEntryDecorator
import androidx.navigation3.ui.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.rememberSceneSetupNavEntryDecorator
import dev.dimension.flare.ui.common.ProxyUriHandler
import dev.dimension.flare.ui.screen.bluesky.blueskyEntryBuilder
import dev.dimension.flare.ui.screen.compose.composeEntryBuilder
import dev.dimension.flare.ui.screen.dm.dmEntryBuilder
import dev.dimension.flare.ui.screen.home.NavigationState
import dev.dimension.flare.ui.screen.home.homeEntryBuilder
import dev.dimension.flare.ui.screen.list.listEntryBuilder
import dev.dimension.flare.ui.screen.media.mediaEntryBuilder
import dev.dimension.flare.ui.screen.profile.profileEntryBuilder
import dev.dimension.flare.ui.screen.rss.rssEntryBuilder
import dev.dimension.flare.ui.screen.serviceselect.serviceSelectEntryBuilder
import dev.dimension.flare.ui.screen.settings.settingsSelectEntryBuilder
import dev.dimension.flare.ui.screen.status.statusEntryBuilder
import soup.compose.material.motion.animation.materialElevationScaleIn
import soup.compose.material.motion.animation.materialElevationScaleOut
import soup.compose.material.motion.animation.materialFadeIn
import soup.compose.material.motion.animation.materialFadeOut
import soup.compose.material.motion.animation.materialSharedAxisXIn
import soup.compose.material.motion.animation.materialSharedAxisXOut
import soup.compose.material.motion.animation.rememberSlideDistance

@Composable
internal fun Router(
    initialRoute: Route,
    navigationState: NavigationState,
    drawerState: DrawerState,
) {
    val scope = rememberCoroutineScope()
    val backStack = rememberNavBackStack(initialRoute)
    val slideDistance = rememberSlideDistance()

    fun navigate(route: Route) {
        backStack.add(route)
    }

    fun onBack() {
        backStack.removeAt(backStack.lastIndex)
    }

    val uriHandler = LocalUriHandler.current
    CompositionLocalProvider(
        LocalUriHandler provides
            remember {
                ProxyUriHandler(uriHandler) {
                    Route.parse(it)?.let {
                        navigate(it)
                    }
                }
            },
    ) {
        NavDisplay(
            sceneStrategy = remember { DialogSceneStrategy() },
            entryDecorators =
                listOf(
                    rememberSceneSetupNavEntryDecorator(),
                    rememberSavedStateNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
            backStack = backStack,
            transitionSpec = {
                materialSharedAxisXIn(true, slideDistance) togetherWith
                    materialSharedAxisXOut(true, slideDistance)
            },
            popTransitionSpec = {
                materialSharedAxisXIn(false, slideDistance) togetherWith
                    materialSharedAxisXOut(false, slideDistance)
            },
            predictivePopTransitionSpec = {
                materialSharedAxisXIn(false, slideDistance) + materialElevationScaleIn() + materialFadeIn() togetherWith
                    materialSharedAxisXOut(false, slideDistance) + materialElevationScaleOut() + materialFadeOut()
            },
            entryProvider =
                entryProvider {
                    with(scope) {
                        homeEntryBuilder(::navigate, ::onBack, scope, drawerState)
                        blueskyEntryBuilder(::navigate, ::onBack)
                        composeEntryBuilder(::navigate, ::onBack)
                        dmEntryBuilder(::navigate, ::onBack, navigationState)
                        listEntryBuilder(::navigate, ::onBack)
                        mediaEntryBuilder(::navigate, ::onBack)
                        profileEntryBuilder(::navigate, ::onBack)
                        rssEntryBuilder(::navigate, ::onBack)
                        serviceSelectEntryBuilder(::navigate, ::onBack)
                        settingsSelectEntryBuilder(::navigate, ::onBack)
                        statusEntryBuilder(::navigate, ::onBack)
                    }
                },
        )
    }
}
