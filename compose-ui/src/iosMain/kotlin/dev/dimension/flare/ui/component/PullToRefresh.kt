package dev.dimension.flare.ui.component

import androidx.annotation.FloatRange
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.slapps.cupertino.CupertinoActivityIndicator
import com.slapps.cupertino.ExperimentalCupertinoApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/**
 * Creates a [PullToRefreshState] that is remembered across compositions.
 *
 * Changes to [isRefreshing] will result in the [PullToRefreshState] being updated.
 *
 * @param isRefreshing the value for [PullToRefreshState.isRefreshing]
 */
@Composable
internal fun rememberPullToRefreshState(isRefreshing: Boolean): PullToRefreshState =
    remember {
        PullToRefreshState(isRefreshing)
    }.apply { this.isRefreshing = isRefreshing }

/**
 * A state object that can be hoisted to control and observe changes for [PullToRefresh].
 *
 * In most cases, this will be created via [rememberPullToRefreshState].
 *
 * @param isRefreshing the initial value for [PullToRefreshState.isRefreshing]
 */
@Stable
internal class PullToRefreshState(
    isRefreshing: Boolean,
) {
    private val _contentOffset = Animatable(0f)

    /**
     * Whether this [PullToRefreshState] is currently refreshing or not.
     */
    var isRefreshing by mutableStateOf(isRefreshing)

    /**
     * Whether a drag is currently in progress.
     */
    var isPullInProgress: Boolean by mutableStateOf(false)
        internal set

    /**
     * Whether this [PullToRefreshState] is currently animating to its resting position or not.
     */
    val isResting: Boolean get() = _contentOffset.isRunning && !isRefreshing

    /**
     * The current offset for the content, in pixels.
     */
    val contentOffset: Float get() = _contentOffset.value

    internal suspend fun animateOffsetTo(offset: Float) {
        _contentOffset.animateTo(offset)
    }

    /**
     * Dispatch scroll delta in pixels from touch events.
     */
    internal suspend fun dispatchScrollDelta(delta: Float) {
        _contentOffset.snapTo(_contentOffset.value + delta)
    }
}

private class PullToRefreshNestedScrollConnection(
    private val state: PullToRefreshState,
    private val coroutineScope: CoroutineScope,
    private val onRefresh: () -> Unit,
) : NestedScrollConnection {
    var enabled: Boolean = false
    var refreshTrigger: Float = 0f
    var dragMultiplier: Float = 0f

    override fun onPreScroll(
        available: Offset,
        source: NestedScrollSource,
    ): Offset =
        when {
            // If pulling isn't enabled, return zero
            !enabled -> Offset.Zero
            // If we're refreshing, consume y
            state.isRefreshing -> Offset(0f, available.y)
            // If the user is pulling up, handle it
            source == NestedScrollSource.UserInput && available.y < 0 -> dragUp(available)
            else -> Offset.Zero
        }

    private fun dragUp(available: Offset): Offset {
        state.isPullInProgress = true

        val newOffset = (available.y * dragMultiplier + state.contentOffset).coerceAtLeast(0f)
        val dragConsumed = newOffset - state.contentOffset

        return if (dragConsumed.absoluteValue >= 0.5f) {
            coroutineScope.launch {
                state.dispatchScrollDelta(dragConsumed)
            }
            Offset(x = 0f, y = available.y)
        } else {
            Offset.Zero
        }
    }

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset =
        when {
            // If pulling isn't enabled, return zero
            !enabled -> Offset.Zero
            // If we're refreshing, return zero
            state.isRefreshing -> Offset.Zero
            // If the user is pulling down and there's y remaining, handle it
            source == NestedScrollSource.UserInput && available.y > 0 -> dragDown(available)
            else -> Offset.Zero
        }

    private fun dragDown(available: Offset): Offset {
        state.isPullInProgress = true
        coroutineScope.launch {
            state.dispatchScrollDelta(available.y * dragMultiplier)
        }
        return Offset(x = 0f, y = available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        // If we're dragging, not currently refreshing and scrolled
        // past the trigger point, refresh!
        if (!state.isRefreshing && state.contentOffset >= refreshTrigger) {
            onRefresh()
        }

        // Reset the drag in progress state
        state.isPullInProgress = false

        return when {
            // If we're pulling/refreshing/resting, consume velocity
            state.contentOffset != 0f -> available
            // Allow the scrolling layout to fling
            else -> Velocity.Zero
        }
    }
}

/**
 * A layout which implements the pull-to-refresh pattern, allowing the user to refresh content via
 * a vertical pull gesture.
 *
 * This layout requires its content to be scrollable so that it receives vertical swipe events.
 * The scrollable content does not need to be a direct descendant though. Layouts such as
 * [androidx.compose.foundation.lazy.LazyColumn] are automatically scrollable, but others such as
 * [androidx.compose.foundation.layout.Column] require you to provide the
 * [androidx.compose.foundation.verticalScroll] modifier to that content.
 *
 * Apps should provide a [onRefresh] block to be notified each time a swipe to refresh gesture
 * is completed. That block is responsible for updating the [state] as appropriately,
 * typically by setting [PullToRefreshState.isRefreshing] to `true` once a 'refresh' has been
 * started. Once a refresh has completed, the app should then set
 * [PullToRefreshState.isRefreshing] to `false`.
 *
 * If an app wishes to show the progress animation outside of a swipe gesture, it can
 * set [PullToRefreshState.isRefreshing] as required.
 *
 * @param state the state object to be used to control or observe the [PullToRefresh] state.
 * @param onRefresh Lambda which is invoked when a pull to refresh gesture is completed.
 * @param modifier The modifier to apply to this layout.
 * @param enabled Whether the the layout should react to pull gestures or not.
 * @param dragMultiplier Multiplier that will be applied to pull gestures.
 * @param refreshTriggerDistance The minimum pull distance which would trigger a refresh.
 * @param refreshingOffset The content's offset when refreshing. By default this will equal to [refreshTriggerDistance].
 * @param indicatorPadding Content padding for the indicator, to inset the indicator in if required.
 * @param indicator the indicator that represents the current state. By default this will use a [PullToRefreshIndicator].
 * @param clipIndicatorToPadding Whether to clip the indicator to [indicatorPadding]. If false is provided the indicator will be clipped to the [content] bounds. Defaults to true.
 * @param content The content containing a scroll composable.
 */
@OptIn(ExperimentalCupertinoApi::class)
@Composable
internal fun PullToRefresh(
    state: PullToRefreshState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @FloatRange(from = 0.0, to = 1.0) dragMultiplier: Float = 0.5f,
    refreshTriggerDistance: Dp = 60.dp,
    refreshingOffset: Dp = refreshTriggerDistance,
    indicatorPadding: PaddingValues = PaddingValues(0.dp),
    indicator: @Composable (state: PullToRefreshState, refreshTrigger: Dp, refreshingOffset: Dp) -> Unit = { s, trigger, offset ->
        val indicatorSize = 36.dp
        if (state.isRefreshing) {
            CupertinoActivityIndicator(
                modifier =
                    Modifier
                        .size(indicatorSize),
            )
        } else {
            val refreshTriggerPx = with(LocalDensity.current) { refreshTriggerDistance.toPx() }
            val progress =
                ((state.contentOffset - refreshTriggerPx / 2) / refreshTriggerPx * 2)
                    .coerceIn(0f, 1f)
            val indicatorHeightPx = with(LocalDensity.current) { indicatorSize.toPx() }
            CupertinoActivityIndicator(
                progress = progress,
                modifier =
                    Modifier
                        .size(indicatorSize)
                        .graphicsLayer {
                            translationY = (state.contentOffset - indicatorHeightPx) / 2f
                        },
            )
        }
    },
    clipIndicatorToPadding: Boolean = true,
    content: @Composable () -> Unit,
) {
    require(dragMultiplier in 0f..1f) { "dragMultiplier must be >= 0 and <= 1" }
    require(refreshingOffset <= refreshTriggerDistance) { "refreshingOffset must be <= refreshTriggerDistance" }

    val coroutineScope = rememberCoroutineScope()
    val updatedOnRefresh by rememberUpdatedState(onRefresh)

    val refreshingOffsetPx = with(LocalDensity.current) { refreshingOffset.toPx() }

    LaunchedEffect(Unit) {
        snapshotFlow { !state.isPullInProgress to state.isRefreshing }
            .distinctUntilChanged()
            .filter { it.first }
            .map { it.second }
            .collectLatest { isRefreshing ->
                state.animateOffsetTo(if (isRefreshing) refreshingOffsetPx else 0f)
            }
    }

    // Our nested scroll connection, which updates our state.
    val nestedScrollConnection =
        remember(state, coroutineScope) {
            PullToRefreshNestedScrollConnection(state, coroutineScope) {
                // On refresh, re-dispatch to the update onRefresh block
                updatedOnRefresh()
            }
        }.apply {
            this.enabled = enabled
            this.dragMultiplier = dragMultiplier
            this.refreshTrigger = with(LocalDensity.current) { refreshTriggerDistance.toPx() }
        }

    Box(modifier.nestedScroll(connection = nestedScrollConnection)) {
        Box(
            modifier =
                Modifier
                    .offset {
                        IntOffset(0, state.contentOffset.toInt())
                    },
        ) {
            content()
        }
        Box(
            Modifier
                // If we're not clipping to the padding, we use clipToBounds() before the padding()
                // modifier.
                .let { if (!clipIndicatorToPadding) it.clipToBounds() else it }
                .padding(indicatorPadding)
                .matchParentSize()
                // Else, if we're are clipping to the padding, we use clipToBounds() after
                // the padding() modifier.
                .let { if (clipIndicatorToPadding) it.clipToBounds() else it },
        ) {
            Box(Modifier.align(Alignment.TopCenter)) {
                indicator(state, refreshTriggerDistance, refreshingOffset)
            }
        }
    }
}
