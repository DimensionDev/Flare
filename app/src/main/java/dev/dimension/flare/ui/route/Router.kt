package dev.dimension.flare.ui.route

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.RoundedCornerCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import dev.dimension.flare.ui.component.BottomSheetSceneStrategy
import dev.dimension.flare.ui.component.platform.isBigScreen
import dev.dimension.flare.ui.screen.article.articleEntryBuilder
import dev.dimension.flare.ui.screen.bluesky.blueskyEntryBuilder
import dev.dimension.flare.ui.screen.compose.composeEntryBuilder
import dev.dimension.flare.ui.screen.dm.dmEntryBuilder
import dev.dimension.flare.ui.screen.gallery.galleryEntryBuilder
import dev.dimension.flare.ui.screen.home.homeEntryBuilder
import dev.dimension.flare.ui.screen.list.listEntryBuilder
import dev.dimension.flare.ui.screen.media.mediaEntryBuilder
import dev.dimension.flare.ui.screen.misskey.misskeyEntryBuilder
import dev.dimension.flare.ui.screen.profile.profileEntryBuilder
import dev.dimension.flare.ui.screen.rss.rssEntryBuilder
import dev.dimension.flare.ui.screen.serviceselect.serviceSelectEntryBuilder
import dev.dimension.flare.ui.screen.settings.settingsSelectEntryBuilder
import dev.dimension.flare.ui.screen.status.statusEntryBuilder
import soup.compose.material.motion.animation.materialSharedAxisZ

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
internal fun Router(
    backStack: SnapshotStateList<Route>,
    navigate: (Route) -> Unit,
    onBack: () -> Unit,
    openDrawer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>()
    val uriHandler = LocalUriHandler.current
    val latestNavigate = rememberUpdatedState(navigate)
    val latestOnBack = rememberUpdatedState(onBack)
    val latestOpenDrawer = rememberUpdatedState(openDrawer)
    val latestUriHandler = rememberUpdatedState(uriHandler)
    val stableNavigate: (Route) -> Unit =
        remember {
            { route -> latestNavigate.value(route) }
        }
    val stableOnBack: () -> Unit =
        remember {
            { latestOnBack.value() }
        }
    val stableOpenDrawer: () -> Unit =
        remember {
            { latestOpenDrawer.value() }
        }
    val stableUriHandler: UriHandler =
        remember {
            object : UriHandler {
                override fun openUri(uri: String) {
                    latestUriHandler.value.openUri(uri)
                }
            }
        }
    val navEntryProvider =
        remember {
            entryProvider<NavKey> {
                homeEntryBuilder(stableNavigate, stableOnBack, stableOpenDrawer, uriHandler = stableUriHandler)
                articleEntryBuilder(stableNavigate, stableOnBack)
                blueskyEntryBuilder(stableNavigate, stableOnBack)
                composeEntryBuilder(stableNavigate, stableOnBack)
                dmEntryBuilder(stableNavigate, stableOnBack)
                galleryEntryBuilder(stableNavigate, stableOnBack)
                listEntryBuilder(stableNavigate, stableOnBack)
                mediaEntryBuilder(stableNavigate, stableOnBack, uriHandler = stableUriHandler)
                profileEntryBuilder(stableNavigate, stableOnBack)
                rssEntryBuilder(stableNavigate, stableOnBack)
                serviceSelectEntryBuilder(stableNavigate, stableOnBack)
                settingsSelectEntryBuilder(stableNavigate, stableOnBack)
                statusEntryBuilder(stableNavigate, stableOnBack)
                misskeyEntryBuilder(stableNavigate, stableOnBack)
            }
        }
    val isBigScreen = isBigScreen()
    val cornerShape = rememberDeviceCornerShape().takeUnless { isBigScreen }
    val layoutDirection = LocalLayoutDirection.current
    val slideDistance =
        with(LocalDensity.current) { androidNavigationSlideDistance.roundToPx() } *
            if (layoutDirection == LayoutDirection.Ltr) 1 else -1
    AndroidNavDisplay(
        modifier = modifier,
        cornerShape = cornerShape,
        sceneStrategies =
            remember {
                listOf(
                    DialogSceneStrategy(),
                    BottomSheetSceneStrategy(),
                    listDetailStrategy,
                )
            },
        entries =
            rememberDecoratedNavEntries(
                backStack = backStack,
                entryDecorators =
                    listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                entryProvider = navEntryProvider,
            ),
        onBack = stableOnBack,
        transitionSpec = {
            if (isBigScreen) {
                materialSharedAxisZ(true)
            } else {
                androidOpenTransition(slideDistance)
            }
        },
        popTransitionSpec = {
            if (isBigScreen) {
                materialSharedAxisZ(false)
            } else {
                androidCloseTransition(slideDistance)
            }
        },
    )
}

@Composable
private fun rememberDeviceCornerShape(): Shape {
    val view = LocalView.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    var rootWindowInsets by
        remember(view) {
            mutableStateOf(ViewCompat.getRootWindowInsets(view))
        }

    LaunchedEffect(view, configuration) {
        withFrameNanos { _ -> }
        rootWindowInsets = ViewCompat.getRootWindowInsets(view)
    }

    fun cornerRadius(position: Int) =
        with(density) {
            (rootWindowInsets?.getRoundedCorner(position)?.radius ?: 0).toDp()
        }

    return AbsoluteRoundedCornerShape(
        topLeft = cornerRadius(RoundedCornerCompat.POSITION_TOP_LEFT),
        topRight = cornerRadius(RoundedCornerCompat.POSITION_TOP_RIGHT),
        bottomRight = cornerRadius(RoundedCornerCompat.POSITION_BOTTOM_RIGHT),
        bottomLeft = cornerRadius(RoundedCornerCompat.POSITION_BOTTOM_LEFT),
    )
}
