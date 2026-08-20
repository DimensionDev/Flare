package dev.dimension.flare.ui.route

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.PathEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.util.lerp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneDecoratorStrategy
import androidx.navigationevent.NavigationEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.reflect.KClass
import androidx.compose.ui.geometry.lerp as lerpRect

private const val PREDICTIVE_BACK_POST_COMMIT_DURATION_MILLIS = 450
private const val PREDICTIVE_BACK_TARGET_SCALE = 0.9f
private const val PREDICTIVE_BACK_SPRING_STIFFNESS = 200f
private const val PREDICTIVE_BACK_SPRING_DAMPING_RATIO = 0.75f
private const val PREDICTIVE_BACK_DEFAULT_SCALE_VELOCITY = -1.2f
private const val PREDICTIVE_BACK_MAX_SCALE_VELOCITY = 10f

// A non-identity value makes AnimatedContent retain both scenes for the requested duration.
private const val PREDICTIVE_BACK_HOLD_ALPHA = 0.999999f

private val predictiveBackGestureEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

private val predictiveBackPostCommitEasing =
    PathEasing(
        Path().apply {
            moveTo(0f, 0f)
            cubicTo(0.05f, 0f, 0.133333f, 0.06f, 0.166666f, 0.4f)
            cubicTo(0.208333f, 0.82f, 0.25f, 1f, 1f, 1f)
        },
    )

@Composable
internal fun rememberAndroidPredictiveBackMotionState(
    deviceCornerShape: Shape,
    displayMargin: Float,
    enteringStartOffset: Float,
    scrimAlpha: Float,
): AndroidPredictiveBackMotionState {
    val coroutineScope = rememberCoroutineScope()
    val state =
        remember(coroutineScope) {
            AndroidPredictiveBackMotionState(
                coroutineScope = coroutineScope,
                deviceCornerShape = deviceCornerShape,
                displayMargin = displayMargin,
                enteringStartOffset = enteringStartOffset,
                scrimAlpha = scrimAlpha,
            )
        }

    SideEffect {
        state.updateConfiguration(
            deviceCornerShape = deviceCornerShape,
            displayMargin = displayMargin,
            enteringStartOffset = enteringStartOffset,
            scrimAlpha = scrimAlpha,
        )
    }
    return state
}

@Composable
internal fun <T : Any> rememberAndroidPredictiveBackSceneDecorator(state: AndroidPredictiveBackMotionState): SceneDecoratorStrategy<T> =
    remember(state) {
        SceneDecoratorStrategy { scene ->
            AndroidPredictiveBackScene(
                scene = scene,
                motionState = state,
            )
        }
    }

/**
 * Keeps both scenes composed while [AndroidPredictiveBackMotionState] owns their visual motion.
 *
 * Navigation3 otherwise settles and disposes an interactive scene as soon as the gesture ends.
 * This nearly-identity transition keeps both scenes available until the deferred pop completes.
 */
internal fun androidPredictiveBackHoldTransition(durationMillis: Int): ContentTransform =
    fadeIn(
        animationSpec = tween(durationMillis = durationMillis, easing = LinearEasing),
        initialAlpha = PREDICTIVE_BACK_HOLD_ALPHA,
    ) togetherWith
        fadeOut(
            animationSpec = tween(durationMillis = durationMillis, easing = LinearEasing),
            targetAlpha = PREDICTIVE_BACK_HOLD_ALPHA,
        )

@Stable
internal class AndroidPredictiveBackMotionState(
    private val coroutineScope: CoroutineScope,
    deviceCornerShape: Shape = RectangleShape,
    displayMargin: Float = 0f,
    enteringStartOffset: Float = 0f,
    scrimAlpha: Float = 0f,
) {
    private var phase by mutableStateOf<Phase>(Phase.Idle)
    private var motionJob: Job? = null

    internal var deviceCornerShape by mutableStateOf(deviceCornerShape)
        private set

    private var displayMargin by mutableStateOf(displayMargin)
    private var enteringStartOffset by mutableStateOf(enteringStartOffset)
    private var maximumScrimAlpha by mutableStateOf(scrimAlpha)

    internal val isPostCommit by derivedStateOf { phase is Phase.PostCommit }

    internal val transitionHoldDurationMillis: Int
        get() =
            (phase as? Phase.PostCommit)?.durationMillis
                ?: PREDICTIVE_BACK_POST_COMMIT_DURATION_MILLIS

    internal fun ownsPostCommitTransition(
        initialSceneKey: Any,
        targetSceneKey: Any,
    ): Boolean {
        val release = (phase as? Phase.PostCommit)?.release ?: return false
        return (
            initialSceneKey == release.outgoingSceneKey &&
                targetSceneKey == release.incomingSceneKey
        ) ||
            (
                initialSceneKey == release.incomingSceneKey &&
                    targetSceneKey == release.outgoingSceneKey
            )
    }

    internal fun blocksInputFor(sceneKey: Any): Boolean {
        val release = (phase as? Phase.PostCommit)?.release ?: return false
        return sceneKey == release.outgoingSceneKey || sceneKey == release.incomingSceneKey
    }

    internal fun updateConfiguration(
        deviceCornerShape: Shape,
        displayMargin: Float,
        enteringStartOffset: Float,
        scrimAlpha: Float,
    ) {
        this.deviceCornerShape = deviceCornerShape
        this.displayMargin = displayMargin
        this.enteringStartOffset = enteringStartOffset
        this.maximumScrimAlpha = scrimAlpha
    }

    internal fun onProgress(
        outgoingSceneKey: Any,
        incomingSceneKey: Any,
        event: NavigationEvent,
    ) {
        if (event.swipeEdge == NavigationEvent.EDGE_NONE) return
        val postCommit = phase as? Phase.PostCommit
        if (
            postCommit?.release?.outgoingSceneKey == outgoingSceneKey &&
            postCommit.release.incomingSceneKey == incomingSceneKey
        ) {
            // Ignore a final gesture event that was queued before the completion callback.
            return
        }

        val continuingMotion =
            (phase as? Phase.Gesture)?.motion?.takeIf {
                it.outgoingSceneKey == outgoingSceneKey &&
                    it.incomingSceneKey == incomingSceneKey &&
                    it.swipeEdge == event.swipeEdge
            }

        if (continuingMotion == null) {
            motionJob?.cancel()
        }

        val progress = event.progress.coerceIn(0f, 1f)
        val progressVelocity =
            if (
                continuingMotion != null &&
                event.frameTimeMillis > continuingMotion.frameTimeMillis
            ) {
                val elapsedSeconds =
                    (event.frameTimeMillis - continuingMotion.frameTimeMillis) / 1_000f
                val instantaneousVelocity = (progress - continuingMotion.progress) / elapsedSeconds
                continuingMotion.progressVelocity * 0.7f + instantaneousVelocity * 0.3f
            } else {
                0f
            }

        phase =
            Phase.Gesture(
                Motion(
                    outgoingSceneKey = outgoingSceneKey,
                    incomingSceneKey = incomingSceneKey,
                    progress = progress,
                    initialTouchY = continuingMotion?.initialTouchY ?: event.touchY,
                    touchY = event.touchY,
                    swipeEdge = event.swipeEdge,
                    frameTimeMillis = event.frameTimeMillis,
                    progressVelocity = progressVelocity,
                ),
            )
    }

    /**
     * Pops immediately so Navigation3 settles the predictive transition in the completed
     * direction, while this state continues to own the native-style post-commit motion.
     *
     * Returning false lets the caller perform a regular pop for a non-interactive back event.
     */
    internal fun commit(performPop: () -> Boolean): Boolean {
        val gesture = phase as? Phase.Gesture ?: return false
        val release = gesture.motion

        motionJob?.cancel()
        // AnimatedContent captures its spec for the active segment, so this duration must stay
        // fixed after the pop starts.
        val durationMillis =
            (
                (1f - release.progress.coerceIn(0f, 1f)) *
                    PREDICTIVE_BACK_POST_COMMIT_DURATION_MILLIS
            ).roundToInt().coerceAtLeast(1)
        phase =
            Phase.PostCommit(
                release = release,
                durationMillis = durationMillis,
            )
        if (!performPop()) {
            phase = Phase.Idle
            return true
        }
        motionJob =
            coroutineScope.launch {
                val scaleVelocity =
                    if (predictiveBackGestureEasing.transform(release.progress) < 0.1f) {
                        PREDICTIVE_BACK_DEFAULT_SCALE_VELOCITY
                    } else {
                        -abs(release.progressVelocity)
                            .coerceAtMost(PREDICTIVE_BACK_MAX_SCALE_VELOCITY)
                    }
                val flingJob =
                    launch {
                        animate(
                            initialValue = 1f,
                            targetValue = 1f,
                            initialVelocity = scaleVelocity,
                            animationSpec =
                                spring(
                                    dampingRatio = PREDICTIVE_BACK_SPRING_DAMPING_RATIO,
                                    stiffness = PREDICTIVE_BACK_SPRING_STIFFNESS,
                                ),
                        ) { value, _ ->
                            updatePostCommit(release) {
                                copy(flingScale = min(value, 1f))
                            }
                        }
                    }

                animate(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec =
                        tween(
                            durationMillis = durationMillis,
                            easing = LinearEasing,
                        ),
                ) { value, _ ->
                    updatePostCommit(release) { copy(linearProgress = value) }
                }
                flingJob.cancelAndJoin()
                updatePostCommit(release) {
                    copy(linearProgress = 1f, flingScale = 1f)
                }

                val current = phase as? Phase.PostCommit
                if (current?.release != release) return@launch
                if (current.outgoingDisposed) {
                    phase = Phase.Idle
                } else {
                    phase = current.copy(animationFinished = true)
                }
            }
        return true
    }

    /**
     * Ends post-commit only after both the custom tail and Navigation3's outgoing scene have
     * finished. Until then its internal [androidx.compose.animation.core.SeekableTransitionState]
     * may still be settling, so restoring the identity transform would briefly reveal the outgoing
     * page.
     */
    internal fun onSceneDisposed(sceneKey: Any) {
        val postCommit = phase as? Phase.PostCommit ?: return
        if (
            sceneKey == postCommit.release.outgoingSceneKey
        ) {
            phase =
                if (postCommit.animationFinished) {
                    Phase.Idle
                } else {
                    postCommit.copy(outgoingDisposed = true)
                }
        }
    }

    internal fun cancel() {
        if (phase !is Phase.Gesture) return
        motionJob?.cancel()
        motionJob = null
        phase = Phase.Idle
    }

    internal fun transformFor(
        sceneKey: Any,
        width: Float,
        height: Float,
    ): SceneTransform {
        if (width <= 0f || height <= 0f) return SceneTransform.Identity

        return when (val currentPhase = phase) {
            Phase.Idle -> {
                SceneTransform.Identity
            }

            is Phase.Gesture -> {
                gestureTransform(
                    sceneKey = sceneKey,
                    motion = currentPhase.motion,
                    progress = currentPhase.motion.progress,
                    width = width,
                    height = height,
                )
            }

            is Phase.PostCommit -> {
                postCommitTransform(
                    sceneKey = sceneKey,
                    phase = currentPhase,
                    width = width,
                    height = height,
                )
            }
        }
    }

    internal fun scrimAlphaFor(sceneKey: Any): Float =
        when (val currentPhase = phase) {
            is Phase.Gesture -> {
                if (sceneKey == currentPhase.motion.incomingSceneKey) maximumScrimAlpha else 0f
            }

            is Phase.PostCommit -> {
                if (sceneKey == currentPhase.release.incomingSceneKey) {
                    maximumScrimAlpha * (1f - currentPhase.linearProgress)
                } else {
                    0f
                }
            }

            Phase.Idle -> {
                0f
            }
        }

    private fun gestureTransform(
        sceneKey: Any,
        motion: Motion,
        progress: Float,
        width: Float,
        height: Float,
    ): SceneTransform {
        val isOutgoing = sceneKey == motion.outgoingSceneKey
        val isIncoming = sceneKey == motion.incomingSceneKey
        if (!isOutgoing && !isIncoming) return SceneTransform.Identity

        val easedProgress = predictiveBackGestureEasing.transform(progress.coerceIn(0f, 1f))
        val scale = lerp(1f, PREDICTIVE_BACK_TARGET_SCALE, easedProgress)
        val verticalOffset =
            verticalOffset(
                height = height,
                scaledHeight = height * scale,
                touchDelta = motion.touchY - motion.initialTouchY,
            )
        val horizontalOffset =
            if (isIncoming) {
                -enteringStartOffset
            } else if (motion.swipeEdge == NavigationEvent.EDGE_LEFT) {
                easedProgress * (width * (1f - PREDICTIVE_BACK_TARGET_SCALE) / 2f - displayMargin)
            } else {
                0f
            }

        return SceneTransform(
            scale = scale,
            translationX = horizontalOffset,
            translationY = verticalOffset,
            alpha = 1f,
            clip = true,
        )
    }

    private fun postCommitTransform(
        sceneKey: Any,
        phase: Phase.PostCommit,
        width: Float,
        height: Float,
    ): SceneTransform {
        val isOutgoing = sceneKey == phase.release.outgoingSceneKey
        val isIncoming = sceneKey == phase.release.incomingSceneKey
        if (!isOutgoing && !isIncoming) return SceneTransform.Identity

        val startTransform =
            gestureTransform(
                sceneKey = sceneKey,
                motion = phase.release,
                progress = phase.release.progress,
                width = width,
                height = height,
            )
        val startRect = startTransform.toRect(width, height)
        val targetRect =
            if (isIncoming) {
                Rect(0f, 0f, width, height)
            } else {
                val targetLeft = startRect.left + enteringStartOffset
                Rect(targetLeft, 0f, targetLeft + width, height)
            }
        val easedProgress =
            predictiveBackPostCommitEasing.transform(phase.linearProgress.coerceIn(0f, 1f))
        val animatedRect =
            lerpRect(startRect, targetRect, easedProgress).scaleCentered(phase.flingScale)

        return animatedRect.toTransform(
            containerWidth = width,
            containerHeight = height,
            alpha =
                if (isOutgoing) {
                    max(1f - phase.linearProgress * 5f, 0f)
                } else {
                    1f
                },
        )
    }

    private fun verticalOffset(
        height: Float,
        scaledHeight: Float,
        touchDelta: Float,
    ): Float {
        if (touchDelta == 0f) return 0f
        val halfHeight = height / 2f
        val distanceRatio = min(halfHeight, abs(touchDelta)) / halfHeight
        val deceleratedRatio = 1f - (1f - distanceRatio) * (1f - distanceRatio)
        val availableDistance = max(0f, (height - scaledHeight) / 2f - displayMargin)
        return availableDistance * deceleratedRatio * touchDelta.sign
    }

    private fun updatePostCommit(
        release: Motion,
        update: Phase.PostCommit.() -> Phase.PostCommit,
    ) {
        val current = phase as? Phase.PostCommit
        if (current?.release == release) {
            phase = current.update()
        }
    }

    @Immutable
    private data class Motion(
        val outgoingSceneKey: Any,
        val incomingSceneKey: Any,
        val progress: Float,
        val initialTouchY: Float,
        val touchY: Float,
        @NavigationEvent.SwipeEdge val swipeEdge: Int,
        val frameTimeMillis: Long,
        val progressVelocity: Float,
    )

    @Immutable
    private sealed interface Phase {
        data object Idle : Phase

        data class Gesture(
            val motion: Motion,
        ) : Phase

        data class PostCommit(
            val release: Motion,
            val durationMillis: Int,
            val linearProgress: Float = 0f,
            val flingScale: Float = 1f,
            val animationFinished: Boolean = false,
            val outgoingDisposed: Boolean = false,
        ) : Phase
    }
}

@Immutable
internal data class SceneTransform(
    val scale: Float,
    val translationX: Float,
    val translationY: Float,
    val alpha: Float,
    val clip: Boolean,
) {
    internal fun toRect(
        containerWidth: Float,
        containerHeight: Float,
    ): Rect {
        val scaledWidth = containerWidth * scale
        val scaledHeight = containerHeight * scale
        val left = (containerWidth - scaledWidth) / 2f + translationX
        val top = (containerHeight - scaledHeight) / 2f + translationY
        return Rect(left, top, left + scaledWidth, top + scaledHeight)
    }

    internal companion object {
        val Identity =
            SceneTransform(
                scale = 1f,
                translationX = 0f,
                translationY = 0f,
                alpha = 1f,
                clip = false,
            )
    }
}

private fun Rect.scaleCentered(scale: Float): Rect {
    val halfWidth = width * scale / 2f
    val halfHeight = height * scale / 2f
    return Rect(
        left = center.x - halfWidth,
        top = center.y - halfHeight,
        right = center.x + halfWidth,
        bottom = center.y + halfHeight,
    )
}

private fun Rect.toTransform(
    containerWidth: Float,
    containerHeight: Float,
    alpha: Float,
): SceneTransform =
    SceneTransform(
        scale = width / containerWidth,
        translationX = center.x - containerWidth / 2f,
        translationY = center.y - containerHeight / 2f,
        alpha = alpha,
        clip = true,
    )

private data class AndroidPredictiveBackSceneKey(
    val sceneClass: KClass<*>,
    val sceneKey: Any,
)

private data class AndroidPredictiveBackScene<T : Any>(
    val scene: Scene<T>,
    val motionState: AndroidPredictiveBackMotionState,
) : Scene<T> {
    override val key: Any = AndroidPredictiveBackSceneKey(scene::class, scene.key)
    override val entries: List<NavEntry<T>> = scene.entries
    override val previousEntries: List<NavEntry<T>> = scene.previousEntries
    override val metadata: Map<String, Any> = scene.metadata
    override val content: @Composable () -> Unit = {
        AndroidPredictiveBackSceneContent(
            sceneKey = key,
            sceneContent = scene.content,
            motionState = motionState,
        )
    }
}

@Composable
private fun AndroidPredictiveBackSceneContent(
    sceneKey: Any,
    sceneContent: @Composable () -> Unit,
    motionState: AndroidPredictiveBackMotionState,
) {
    DisposableEffect(motionState, sceneKey) {
        onDispose {
            motionState.onSceneDisposed(sceneKey)
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(motionState, sceneKey) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (motionState.blocksInputFor(sceneKey)) {
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }.drawWithContent {
                    drawContent()
                    val alpha = motionState.scrimAlphaFor(sceneKey)
                    if (alpha > 0f) {
                        drawRect(Color.Black, alpha = alpha)
                    }
                },
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val transform =
                            motionState.transformFor(
                                sceneKey = sceneKey,
                                width = size.width,
                                height = size.height,
                            )
                        scaleX = transform.scale
                        scaleY = transform.scale
                        translationX = transform.translationX
                        translationY = transform.translationY
                        alpha = transform.alpha
                        transformOrigin = TransformOrigin.Center
                        shape = motionState.deviceCornerShape
                        clip = transform.clip
                    },
        ) {
            sceneContent()
        }
    }
}
