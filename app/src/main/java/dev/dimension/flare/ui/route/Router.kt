package dev.dimension.flare.ui.route

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.PathEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.RoundedCornerCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.scene.rememberNavigationEventState
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventTransitionState.Companion.TRANSITIONING_BACK
import androidx.navigationevent.NavigationEventTransitionState.InProgress
import androidx.navigationevent.compose.NavigationBackHandler
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
    val isBigScreen = isBigScreen()
    val layoutDirection = LocalLayoutDirection.current
    val density = LocalDensity.current
    val slideDistance =
        with(density) { androidNavigationSlideDistance.roundToPx() } *
            if (layoutDirection == LayoutDirection.Ltr) 1 else -1
    val predictiveBackMotionState =
        rememberAndroidPredictiveBackMotionState(
            deviceCornerShape = rememberDeviceCornerShape(),
            displayMargin = with(density) { androidPredictiveBackDisplayMargin.toPx() },
            enteringStartOffset =
                with(density) { androidPredictiveBackEnteringStartOffset.toPx() },
            scrimAlpha =
                if (isSystemInDarkTheme()) {
                    ANDROID_PREDICTIVE_BACK_SCRIM_ALPHA_DARK
                } else {
                    ANDROID_PREDICTIVE_BACK_SCRIM_ALPHA_LIGHT
                },
        )
    val uriHandler = LocalUriHandler.current
    val latestNavigate = rememberUpdatedState(navigate)
    val latestOnBack = rememberUpdatedState(onBack)
    val latestOpenDrawer = rememberUpdatedState(openDrawer)
    val latestUriHandler = rememberUpdatedState(uriHandler)
    val performBack: () -> Unit =
        remember {
            { latestOnBack.value() }
        }
    val stableNavigate: (Route) -> Unit =
        remember {
            { route -> latestNavigate.value(route) }
        }
    val stableOnBack: () -> Unit =
        remember(predictiveBackMotionState) {
            {
                if (!predictiveBackMotionState.isPostCommit) {
                    performBack()
                }
            }
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
    val entries =
        rememberDecoratedNavEntries<NavKey>(
            backStack = backStack,
            entryDecorators =
                listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
            entryProvider = navEntryProvider,
        )
    val sceneStrategies =
        remember(listDetailStrategy) {
            listOf(
                DialogSceneStrategy(),
                BottomSheetSceneStrategy(),
                listDetailStrategy,
            )
        }
    val predictiveBackSceneDecorator =
        rememberAndroidPredictiveBackSceneDecorator<NavKey>(predictiveBackMotionState)
    val sceneState =
        rememberSceneState(
            entries = entries,
            sceneStrategies = sceneStrategies,
            sceneDecoratorStrategies = listOf(predictiveBackSceneDecorator),
            onBack = stableOnBack,
        )
    val navigationEventState = rememberNavigationEventState(sceneState)

    LaunchedEffect(navigationEventState, sceneState) {
        snapshotFlow { navigationEventState.transitionState }.collect { transitionState ->
            val inProgress = transitionState as? InProgress ?: return@collect
            val event = inProgress.latestEvent
            val previousScene = sceneState.previousScenes.lastOrNull() ?: return@collect
            if (
                inProgress.direction == TRANSITIONING_BACK &&
                event.swipeEdge != NavigationEvent.EDGE_NONE
            ) {
                predictiveBackMotionState.onProgress(
                    outgoingSceneKey = sceneState.currentScene.key,
                    incomingSceneKey = previousScene.key,
                    event = event,
                )
            }
        }
    }

    NavigationBackHandler(
        state = navigationEventState,
        isBackEnabled =
            sceneState.currentScene.previousEntries.isNotEmpty() &&
                !predictiveBackMotionState.isPostCommit,
        onBackCancelled = predictiveBackMotionState::cancel,
        onBackCompleted = {
            // The handler's enabled flag may be stale for one frame after completion.
            if (!predictiveBackMotionState.isPostCommit) {
                val popCount =
                    (sceneState.entries.size - sceneState.currentScene.previousEntries.size)
                        .coerceIn(0, backStack.size)
                if (popCount == 0) {
                    predictiveBackMotionState.cancel()
                } else {
                    val routesToPop = backStack.takeLast(popCount)
                    val completeBack: () -> Boolean = {
                        val canPop =
                            backStack.size >= popCount &&
                                backStack.takeLast(popCount) == routesToPop
                        if (canPop) {
                            val previousSize = backStack.size
                            repeat(popCount) { performBack() }
                            backStack.size == previousSize - popCount
                        } else {
                            false
                        }
                    }
                    if (!predictiveBackMotionState.commit(completeBack)) {
                        completeBack()
                    }
                }
            }
        },
    )

    NavDisplay(
        sceneState = sceneState,
        navigationEventState = navigationEventState,
        // NavDisplay applies this modifier to AnimatedContent, while OverlayScenes stay outside it.
        // Keep page transitions inside their pane without constraining fullscreen media dialogs.
        modifier = modifier.clipToBounds(),
        transitionSpec = {
            if (
                predictiveBackMotionState.ownsPostCommitTransition(
                    initialSceneKey = initialState.key,
                    targetSceneKey = targetState.key,
                )
            ) {
                androidPredictiveBackHoldTransition(
                    predictiveBackMotionState.transitionHoldDurationMillis,
                )
            } else if (isBigScreen) {
                materialSharedAxisZ(true)
            } else {
                androidOpenTransition(slideDistance)
            }
        },
        popTransitionSpec = {
            if (
                predictiveBackMotionState.ownsPostCommitTransition(
                    initialSceneKey = initialState.key,
                    targetSceneKey = targetState.key,
                )
            ) {
                androidPredictiveBackHoldTransition(
                    predictiveBackMotionState.transitionHoldDurationMillis,
                )
            } else if (isBigScreen) {
                materialSharedAxisZ(false)
            } else {
                androidCloseTransition(slideDistance)
            }
        },
        predictivePopTransitionSpec = { swipeEdge ->
            if (swipeEdge == NavigationEvent.EDGE_NONE) {
                if (isBigScreen) {
                    materialSharedAxisZ(false)
                } else {
                    androidCloseTransition(slideDistance)
                }
            } else {
                androidPredictiveBackHoldTransition(
                    ANDROID_NAVIGATION_DURATION_MILLIS,
                )
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

    return remember(rootWindowInsets, density, configuration) {
        fun cornerRadius(position: Int) =
            with(density) {
                (rootWindowInsets?.getRoundedCorner(position)?.radius ?: 0).toDp()
            }

        AbsoluteRoundedCornerShape(
            topLeft = cornerRadius(RoundedCornerCompat.POSITION_TOP_LEFT),
            topRight = cornerRadius(RoundedCornerCompat.POSITION_TOP_RIGHT),
            bottomRight = cornerRadius(RoundedCornerCompat.POSITION_BOTTOM_RIGHT),
            bottomLeft = cornerRadius(RoundedCornerCompat.POSITION_BOTTOM_LEFT),
        )
    }
}

private const val ANDROID_NAVIGATION_DURATION_MILLIS = 450
private const val ANDROID_NAVIGATION_ENTER_FADE_START_MILLIS = 50
private const val ANDROID_NAVIGATION_ENTER_FADE_END_MILLIS = 133
private const val ANDROID_NAVIGATION_EXIT_FADE_START_MILLIS = 35
private const val ANDROID_NAVIGATION_EXIT_FADE_END_MILLIS = 118
private const val ANDROID_PREDICTIVE_BACK_SCRIM_ALPHA_LIGHT = 0.2f
private const val ANDROID_PREDICTIVE_BACK_SCRIM_ALPHA_DARK = 0.8f
private val androidNavigationSlideDistance = 96.dp
private val androidPredictiveBackDisplayMargin = 8.dp
private val androidPredictiveBackEnteringStartOffset = 96.dp

private val androidNavigationSpatialEasing =
    PathEasing(
        Path().apply {
            moveTo(0f, 0f)
            cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
            cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
        },
    )

private fun androidOpenTransition(slideDistance: Int): ContentTransform =
    (
        slideInHorizontally(
            animationSpec =
                tween(
                    durationMillis = ANDROID_NAVIGATION_DURATION_MILLIS,
                    easing = androidNavigationSpatialEasing,
                ),
            initialOffsetX = { slideDistance },
        ) +
            fadeIn(
                animationSpec =
                    keyframes {
                        durationMillis = ANDROID_NAVIGATION_DURATION_MILLIS
                        0f at 0
                        0f at ANDROID_NAVIGATION_ENTER_FADE_START_MILLIS using LinearEasing
                        1f at ANDROID_NAVIGATION_ENTER_FADE_END_MILLIS
                    },
            )
    ) togetherWith
        slideOutHorizontally(
            animationSpec =
                tween(
                    durationMillis = ANDROID_NAVIGATION_DURATION_MILLIS,
                    easing = androidNavigationSpatialEasing,
                ),
            targetOffsetX = { -slideDistance },
        )

private fun androidCloseTransition(slideDistance: Int): ContentTransform =
    slideInHorizontally(
        animationSpec =
            tween(
                durationMillis = ANDROID_NAVIGATION_DURATION_MILLIS,
                easing = androidNavigationSpatialEasing,
            ),
        initialOffsetX = { -slideDistance },
    ) togetherWith
        (
            slideOutHorizontally(
                animationSpec =
                    tween(
                        durationMillis = ANDROID_NAVIGATION_DURATION_MILLIS,
                        easing = androidNavigationSpatialEasing,
                    ),
                targetOffsetX = { slideDistance },
            ) +
                fadeOut(
                    animationSpec =
                        keyframes {
                            durationMillis = ANDROID_NAVIGATION_DURATION_MILLIS
                            1f at 0
                            1f at ANDROID_NAVIGATION_EXIT_FADE_START_MILLIS using LinearEasing
                            0f at ANDROID_NAVIGATION_EXIT_FADE_END_MILLIS
                        },
                )
        )
