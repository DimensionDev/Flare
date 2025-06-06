package dev.dimension.flare.ui.route

import androidx.compose.animation.togetherWith
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalUriHandler
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.rememberSceneSetupNavEntryDecorator
import dev.dimension.flare.ui.common.ProxyUriHandler
import dev.dimension.flare.ui.component.BottomSheetSceneStrategy
import dev.dimension.flare.ui.component.TopLevelBackStack
import dev.dimension.flare.ui.component.platform.isBigScreen
import dev.dimension.flare.ui.component.rememberSavedStateNavEntryDecorator2
import dev.dimension.flare.ui.component.rememberViewModelStoreNavEntryDecorator2
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
import soup.compose.material.motion.animation.holdIn
import soup.compose.material.motion.animation.holdOut
import soup.compose.material.motion.animation.materialElevationScaleIn
import soup.compose.material.motion.animation.materialElevationScaleOut
import soup.compose.material.motion.animation.materialSharedAxisZ
import soup.compose.material.motion.animation.translateXIn
import soup.compose.material.motion.animation.translateXOut

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun Router(
    topLevelBackStack: TopLevelBackStack<Route>,
    navigationState: NavigationState,
    openDrawer: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>()

    fun navigate(route: Route) {
        topLevelBackStack.add(route)
    }

    fun onBack() {
        topLevelBackStack.removeLast()
    }

    val isBigScreen = isBigScreen()

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
            sceneStrategy =
                remember {
                    DialogSceneStrategy<NavKey>()
                        .then(BottomSheetSceneStrategy())
                        .then(listDetailStrategy)
                },
            entryDecorators =
                listOf(
                    rememberSceneSetupNavEntryDecorator(),
                    rememberSavedStateNavEntryDecorator2<Route>(
                        shouldRemoveState = {
                            !topLevelBackStack.isInBackStack(it)
                        },
                    ),
                    rememberViewModelStoreNavEntryDecorator2<Route>(
                        shouldRemoveState = {
                            !topLevelBackStack.isInBackStack(it)
                        },
                    ),
                ),
            backStack = topLevelBackStack.backStack,
            onBack = { onBack() },
            transitionSpec = {
                if (isBigScreen) {
                    materialSharedAxisZ(true)
                } else {
                    holdIn() + translateXIn { it } togetherWith
                        materialElevationScaleOut()
                }
            },
            popTransitionSpec = {
                if (isBigScreen) {
                    materialSharedAxisZ(false)
                } else {
                    materialElevationScaleIn() togetherWith
                        holdOut() + translateXOut { it }
                }
            },
            predictivePopTransitionSpec = {
                if (isBigScreen) {
                    materialSharedAxisZ(false)
                } else {
                    materialElevationScaleIn() togetherWith
                        holdOut() + translateXOut { it }
                }
            },
            entryProvider =
                entryProvider {
                    with(scope) {
                        homeEntryBuilder(::navigate, ::onBack, openDrawer)
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
