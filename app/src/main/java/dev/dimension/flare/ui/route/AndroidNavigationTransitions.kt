package dev.dimension.flare.ui.route

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.PathEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.unveilIn
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEvent

internal const val ANDROID_NAVIGATION_DURATION_MILLIS = 450
private const val ANDROID_NAVIGATION_ENTER_FADE_START_MILLIS = 50
private const val ANDROID_NAVIGATION_ENTER_FADE_END_MILLIS = 133
private const val ANDROID_NAVIGATION_EXIT_FADE_START_MILLIS = 35
private const val ANDROID_NAVIGATION_EXIT_FADE_END_MILLIS = 118
internal const val ANDROID_PREDICTIVE_BACK_TARGET_SCALE = 0.85f

// shortcut: Fixed entering start scale; calibrate the corner-height cap with real-device window bounds.
internal const val ANDROID_PREDICTIVE_BACK_ENTERING_START_SCALE = 0.95f
internal const val ANDROID_PREDICTIVE_BACK_SCRIM_ALPHA_LIGHT = 0.2f
internal const val ANDROID_PREDICTIVE_BACK_SCRIM_ALPHA_DARK = 0.8f
internal val androidNavigationSlideDistance = 96.dp
internal val androidPredictiveBackDisplayMargin = 8.dp
internal val androidPredictiveBackEnteringStartOffset = 96.dp

internal val androidNavigationSpatialEasing =
    PathEasing(
        Path().apply {
            moveTo(0f, 0f)
            cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
            cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
        },
    )

internal val androidPredictiveBackEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

// Navigation3 samples this easing during preview; the background moves only after commit.
private val stationaryPreview = Easing { 0f }

internal fun androidOpenTransition(slideDistance: Int): ContentTransform =
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

internal fun androidCloseTransition(slideDistance: Int): ContentTransform =
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
internal fun androidPredictivePopTransition(
    swipeEdge: Int,
    displayMargin: Int,
    enteringStartOffset: Int,
    scrimColor: Color,
): ContentTransform =
    (
        unveilIn(
            initialColor = scrimColor,
            matchParentSize = true,
            animationSpec = tween(easing = stationaryPreview),
        ) +
            slideInHorizontally(
                animationSpec =
                    tween(
                        durationMillis = ANDROID_NAVIGATION_DURATION_MILLIS,
                        easing = stationaryPreview,
                    ),
                initialOffsetX = { fullWidth ->
                    -maxOf(
                        enteringStartOffset,
                        (
                            fullWidth * ANDROID_PREDICTIVE_BACK_ENTERING_START_SCALE *
                                (1f - ANDROID_PREDICTIVE_BACK_TARGET_SCALE)
                        ).toInt(),
                    )
                },
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
                )
        )

@OptIn(ExperimentalAnimationApi::class)
internal fun androidPredictiveCommitTransition(
    progress: Float,
    swipeEdge: Int,
    displayMargin: Int,
    enteringStartOffset: Int,
    scrimColor: Color,
): ContentTransform =
    (
        slideInHorizontally(
            animationSpec = tween(ANDROID_NAVIGATION_DURATION_MILLIS, easing = androidNavigationSpatialEasing),
            initialOffsetX = { -enteringStartOffset },
        ) +
            unveilIn(
                initialColor = scrimColor,
                matchParentSize = true,
                animationSpec = tween(ANDROID_NAVIGATION_DURATION_MILLIS, easing = LinearEasing),
            )
    ) togetherWith
        (
            scaleOut(
                targetScale = 1f,
                animationSpec = tween(ANDROID_NAVIGATION_DURATION_MILLIS, easing = androidNavigationSpatialEasing),
            ) +
                slideOutHorizontally(
                    animationSpec = tween(ANDROID_NAVIGATION_DURATION_MILLIS, easing = androidNavigationSpatialEasing),
                    targetOffsetX = { width ->
                        val inset = width * (1f - ANDROID_PREDICTIVE_BACK_TARGET_SCALE) / 2f
                        val left = (inset + if (swipeEdge == NavigationEvent.EDGE_LEFT) inset - displayMargin else 0f) * progress
                        left.toInt() + enteringStartOffset
                    },
                ) + fadeOut(animationSpec = tween(90, easing = LinearEasing))
        )
