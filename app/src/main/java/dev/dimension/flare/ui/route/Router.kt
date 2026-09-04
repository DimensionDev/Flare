package dev.dimension.flare.ui.route

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.PathEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.unveilIn
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.DialogSceneStrategy
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEvent
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
    val deviceCornerDecorator =
        rememberDeviceCornerNavEntryDecorator(
            enabled = !isBigScreen,
            shape = rememberDeviceCornerShape(),
        )
    val layoutDirection = LocalLayoutDirection.current
    val slideDistance =
        with(LocalDensity.current) { androidNavigationSlideDistance.roundToPx() } *
            if (layoutDirection == LayoutDirection.Ltr) 1 else -1
    val predictiveBackMargin =
        with(LocalDensity.current) { androidPredictiveBackDisplayMargin.roundToPx() }
    val predictiveBackEnteringStartOffset =
        with(LocalDensity.current) { androidPredictiveBackEnteringStartOffset.roundToPx() }
    val predictiveBackScrimColor =
        Color.Black.copy(
            alpha =
                if (isSystemInDarkTheme()) {
                    ANDROID_PREDICTIVE_BACK_SCRIM_ALPHA_DARK
                } else {
                    ANDROID_PREDICTIVE_BACK_SCRIM_ALPHA_LIGHT
                },
        )
    NavDisplay(
        modifier = modifier,
        sceneStrategies =
            remember {
                listOf(
                    DialogSceneStrategy(),
                    BottomSheetSceneStrategy(),
                    listDetailStrategy,
                )
            },
        entryDecorators =
            listOf(
                deviceCornerDecorator,
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        backStack = backStack,
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
        predictivePopTransitionSpec = { swipeEdge ->
            androidPredictivePopTransition(
                swipeEdge = swipeEdge,
                displayMargin = predictiveBackMargin,
                enteringStartOffset = predictiveBackEnteringStartOffset,
                scrimColor = predictiveBackScrimColor,
            )
        },
        entryProvider = navEntryProvider,
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

@Composable
private fun rememberDeviceCornerNavEntryDecorator(
    enabled: Boolean,
    shape: Shape,
): NavEntryDecorator<NavKey> =
    remember(enabled, shape) {
        NavEntryDecorator { entry ->
            val shouldClip =
                enabled &&
                    dialogMetadataKey !in entry.metadata &&
                    !BottomSheetSceneStrategy.isBottomSheetEntry(entry)

            if (shouldClip) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                this.shape = shape
                                clip = true
                            },
                ) {
                    entry.Content()
                }
            } else {
                entry.Content()
            }
        }
    }

private val dialogMetadataKey = DialogSceneStrategy.dialog().keys.single()

private const val ANDROID_NAVIGATION_DURATION_MILLIS = 450
private const val ANDROID_NAVIGATION_ENTER_FADE_START_MILLIS = 50
private const val ANDROID_NAVIGATION_ENTER_FADE_END_MILLIS = 133
private const val ANDROID_NAVIGATION_EXIT_FADE_START_MILLIS = 35
private const val ANDROID_NAVIGATION_EXIT_FADE_END_MILLIS = 118
private const val ANDROID_PREDICTIVE_BACK_TARGET_SCALE = 0.85f
private const val ANDROID_PREDICTIVE_BACK_ENTERING_START_SCALE = 0.95f
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

private val androidPredictiveBackEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

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

@OptIn(ExperimentalAnimationApi::class)
private fun androidPredictivePopTransition(
    swipeEdge: Int,
    displayMargin: Int,
    enteringStartOffset: Int,
    scrimColor: Color,
): ContentTransform =
    (
        unveilIn(
            initialColor = scrimColor,
            matchParentSize = true,
            animationSpec =
                keyframes {
                    durationMillis = ANDROID_NAVIGATION_DURATION_MILLIS
                    scrimColor at 0 using LinearEasing
                    scrimColor.copy(alpha = 0f) at ANDROID_NAVIGATION_DURATION_MILLIS
                },
        ) +
            scaleIn(
                animationSpec =
                    tween(
                        durationMillis = ANDROID_NAVIGATION_DURATION_MILLIS,
                        easing = androidPredictiveBackEasing,
                    ),
                initialScale = ANDROID_PREDICTIVE_BACK_ENTERING_START_SCALE,
            ) +
            slideInHorizontally(
                animationSpec =
                    tween(
                        durationMillis = ANDROID_NAVIGATION_DURATION_MILLIS,
                        easing = androidPredictiveBackEasing,
                    ),
                initialOffsetX = { -enteringStartOffset },
            )
    ) togetherWith
        (
            scaleOut(
                animationSpec =
                    tween(
                        durationMillis = ANDROID_NAVIGATION_DURATION_MILLIS,
                        easing = androidPredictiveBackEasing,
                    ),
                targetScale = ANDROID_PREDICTIVE_BACK_TARGET_SCALE,
            ) +
                slideOutHorizontally(
                    animationSpec =
                        tween(
                            durationMillis = ANDROID_NAVIGATION_DURATION_MILLIS,
                            easing = androidPredictiveBackEasing,
                        ),
                    targetOffsetX = { fullWidth ->
                        if (swipeEdge == NavigationEvent.EDGE_LEFT) {
                            (
                                fullWidth *
                                    (1f - ANDROID_PREDICTIVE_BACK_TARGET_SCALE) / 2f
                            ).toInt() - displayMargin
                        } else {
                            0
                        }
                    },
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
