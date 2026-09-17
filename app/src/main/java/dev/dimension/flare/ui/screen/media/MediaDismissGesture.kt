package dev.dimension.flare.ui.screen.media

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

internal class MediaDismissState {
    var offset by mutableFloatStateOf(0f)
    var height by mutableFloatStateOf(1f)
    var recovery: Job? = null
    val progress: Float get() = (abs(offset) / height).coerceIn(0f, 1f)
}

@Composable
internal fun MediaDismissGesture(
    state: MediaDismissState,
    enabled: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    Box(
        Modifier
            .fillMaxSize()
            .onSizeChanged { state.height = it.height.toFloat().coerceAtLeast(1f) }
            .offset { IntOffset(0, state.offset.roundToInt()) }
            .draggable(
                state = rememberDraggableState { state.offset += it },
                orientation = Orientation.Vertical,
                enabled = enabled,
                onDragStarted = { state.recovery?.cancel() },
                onDragStopped = {
                    if (state.progress >= 0.25f) {
                        onDismiss()
                    } else {
                        state.recovery =
                            scope.launch {
                                animate(state.offset, 0f) { value, _ -> state.offset = value }
                            }
                    }
                },
            ),
    ) {
        content()
    }
}
