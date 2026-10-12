package dev.dimension.flare.ui.route

import android.os.SystemClock
import android.view.MotionEvent
import android.view.VelocityTracker
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.util.lerp
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.NavigationBackHandler
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneDecoratorStrategy
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.rememberNavigationEventState
import androidx.navigation3.scene.rememberSceneState
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventTransitionState.InProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun <T : Any> AndroidNavDisplay(
    entries: List<NavEntry<T>>,
    sceneStrategies: List<SceneStrategy<T>>,
    cornerShape: Shape?,
    transitionSpec: () -> ContentTransform,
    popTransitionSpec: () -> ContentTransform,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val margin = with(LocalDensity.current) { androidPredictiveBackDisplayMargin.roundToPx() }
    val enteringOffset = with(LocalDensity.current) { androidPredictiveBackEnteringStartOffset.roundToPx() }
    val scrim =
        Color.Black.copy(
            alpha = if (isSystemInDarkTheme()) ANDROID_PREDICTIVE_BACK_SCRIM_ALPHA_DARK else ANDROID_PREDICTIVE_BACK_SCRIM_ALPHA_LIGHT,
        )
    val scope = rememberCoroutineScope()
    val motion = remember(scope) { AndroidBackMotion(scope) }
    val currentCornerShape = rememberUpdatedState(cornerShape)
    val currentMargin = rememberUpdatedState(margin.toFloat())
    val decorators =
        remember(motion) {
            listOf(SceneDecoratorStrategy<T> { scene -> AndroidBackScene(scene, motion, currentCornerShape, currentMargin) })
        }
    val scenes = rememberSceneState(entries, sceneStrategies, decorators, onBack = onBack)
    val events = rememberNavigationEventState(scenes)
    LaunchedEffect(events) {
        snapshotFlow { events.transitionState }.collect { state ->
            if (state is InProgress) {
                val previous = events.backInfo.lastOrNull()?.scene
                if (previous != null && events.currentInfo.scene.key != previous.key) {
                    motion.progress(events.currentInfo.scene.key, previous.key, state.latestEvent)
                }
            } else {
                motion.finish(completed = false)
            }
        }
    }
    DisposableEffect(motion) { onDispose { motion.gesture?.recycleTracker() } }
    NavigationBackHandler(
        sceneState = scenes,
        state = events,
        onBackCancelled = { motion.finish(completed = false) },
        onBackCompleted = {
            motion.finish(completed = true)
            onBack()
        },
    )
    NavDisplay(
        sceneState = scenes,
        navigationEventState = events,
        modifier = modifier.clipToBounds(),
        transitionSpec = { transitionSpec() },
        popTransitionSpec = {
            val committed = motion.commits[initialState.key]?.takeIf { it.entering == targetState.key }
            if (committed == null) {
                popTransitionSpec()
            } else {
                androidPredictiveCommitTransition(committed.progress, committed.edge, margin, enteringOffset, scrim)
            }
        },
        predictivePopTransitionSpec = { edge -> androidPredictivePopTransition(edge, margin, enteringOffset, scrim) },
    )
}

private class AndroidBackMotion(
    private val scope: CoroutineScope,
) {
    var gesture by mutableStateOf<AndroidBackGesture?>(null)
        private set
    val commits = mutableStateMapOf<Any, AndroidBackGesture>()

    fun progress(
        exiting: Any,
        entering: Any,
        event: NavigationEvent,
    ) {
        var current = gesture
        if (current == null || !current.dragging || current.exiting != exiting) {
            finish(completed = false)
            current = AndroidBackGesture(exiting, entering, event)
            gesture = current
        }
        current.update(event)
    }

    fun finish(completed: Boolean) {
        val current = gesture?.takeIf { it.dragging } ?: return
        current.dragging = false
        current.completed = completed
        val velocity = current.releaseVelocity()
        current.recycleTracker()
        if (completed) {
            commits[current.exiting] = current
            scope.launch {
                current.fling.animateTo(
                    1f,
                    spring(dampingRatio = 0.75f, stiffness = 200f, visibilityThreshold = 0.0001f),
                    initialVelocity = -velocity,
                )
            }
        }
        scope.launch {
            current.settle.animateTo(
                1f,
                if (completed) {
                    tween(
                        ANDROID_NAVIGATION_DURATION_MILLIS,
                        easing = LinearEasing,
                    )
                } else {
                    spring(dampingRatio = 1f, stiffness = 500f)
                },
            )
            withFrameNanos { }
            if (commits[current.exiting] === current) commits.remove(current.exiting)
        }
    }
}

private class AndroidBackGesture(
    val exiting: Any,
    val entering: Any,
    event: NavigationEvent,
) {
    val edge = event.swipeEdge
    val initialTouchY = event.touchY
    var progress by mutableStateOf(0f)
        private set
    var deltaY by mutableStateOf(0f)
        private set
    var dragging by mutableStateOf(true)
    var completed = false
    val settle = Animatable(0f)
    val fling = Animatable(1f)
    private var tracker: VelocityTracker? = VelocityTracker.obtain()
    private val startTime = event.frameTimeMillis.takeIf { it > 0 } ?: SystemClock.uptimeMillis()

    fun update(event: NavigationEvent) {
        progress = androidPredictiveBackEasing.transform(event.progress.coerceIn(0f, 1f))
        deltaY = event.touchY - initialTouchY
        val time = event.frameTimeMillis.takeIf { it > 0 } ?: SystemClock.uptimeMillis()
        val sample = MotionEvent.obtain(startTime, time, MotionEvent.ACTION_MOVE, progress, 0f, 0)
        tracker?.addMovement(sample)
        sample.recycle()
    }

    fun releaseVelocity(): Float {
        tracker?.computeCurrentVelocity(1000)
        // AOSP scales progress velocity by 2 * (1 - 0.85), with a small early-release impulse.
        return ((tracker?.xVelocity ?: 0f) * 0.3f).coerceIn(if (progress < 0.1f) 0.12f else 0f, 1f)
    }

    fun recycleTracker() {
        tracker?.recycle()
        tracker = null
    }
}

private data class AndroidBackScene<T : Any>(
    val scene: Scene<T>,
    val motion: AndroidBackMotion,
    val cornerShape: State<Shape?>,
    val margin: State<Float>,
) : Scene<T> by scene {
    override val key: Any = scene::class to scene.key

    override val content: @Composable () -> Unit = {
        val layer = remember { AndroidBackLayer() }
        val latest = motion.gesture
        if (latest != null && (latest.exiting == key || latest.entering == key) && layer.gesture !== latest) {
            val continuing = layer.gesture?.let { it.dragging || it.settle.value < 1f || it.fling.isRunning } == true
            layer.startScale = if (latest.entering == key && !continuing) ANDROID_PREDICTIVE_BACK_ENTERING_START_SCALE else layer.scale
            layer.startY = layer.y
            layer.gesture = latest
        }
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val gesture = layer.gesture
                if (gesture != null) {
                    val entering = gesture.entering == key
                    val p = gesture.progress
                    val closingScale = lerp(1f, ANDROID_PREDICTIVE_BACK_TARGET_SCALE, p)
                    val previewScale =
                        lerp(layer.startScale, if (entering) layer.startScale * ANDROID_PREDICTIVE_BACK_TARGET_SCALE else 1f, p)
                    val yRatio = (abs(gesture.deltaY) * 2f / size.height.coerceAtLeast(1f)).coerceIn(0f, 1f)
                    val yOffset =
                        (size.height * (1f - closingScale) / 2f - margin.value).coerceAtLeast(0f) * (1f - (1f - yRatio) * (1f - yRatio)) *
                            sign(gesture.deltaY)
                    val previewY = layer.startY * (1f - p) + yOffset
                    val settled =
                        if (gesture.dragging) {
                            0f
                        } else if (gesture.completed) {
                            androidNavigationSpatialEasing.transform(gesture.settle.value)
                        } else {
                            gesture.settle.value
                        }
                    layer.scale = lerp(previewScale, 1f, settled) * gesture.fling.value.coerceAtMost(1f)
                    layer.y = lerp(previewY, 0f, settled)
                    scaleX = layer.scale
                    scaleY = layer.scale
                    // NavDisplay scales the outgoing scene; compensate so both pages follow the same Y.
                    translationY = layer.y / if (entering) 1f else lerp(closingScale, 1f, settled)
                }
                shape = cornerShape.value ?: RectangleShape
                clip = cornerShape.value != null
            },
        ) { scene.content() }
    }
}

private class AndroidBackLayer {
    var gesture by mutableStateOf<AndroidBackGesture?>(null)
    var startScale = 1f
    var startY = 0f
    var scale = 1f
    var y = 0f
}
