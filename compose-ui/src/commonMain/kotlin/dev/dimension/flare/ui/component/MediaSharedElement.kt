package dev.dimension.flare.ui.component

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import kotlin.random.Random

public data class MediaSharedElementKey(
    val group: Long,
    val preview: String,
)

public class MediaSharedTransitionState(
    public val scope: SharedTransitionScope,
    public val windowInfo: WindowInfo,
) {
    public var pressedKey: MediaSharedElementKey? = null
    public var hiddenKey: MediaSharedElementKey? by mutableStateOf(null)
    public var dragging: Boolean by mutableStateOf(false)
}

public class MediaSharedElementDestination(
    public val group: Long?,
    public val visibilityScope: AnimatedVisibilityScope,
)

public val LocalMediaSharedTransition: ProvidableCompositionLocal<MediaSharedTransitionState?> = compositionLocalOf { null }
public val LocalMediaSharedElementGroup: ProvidableCompositionLocal<Long?> = compositionLocalOf { null }
public val LocalMediaSharedSourcesEnabled: ProvidableCompositionLocal<Boolean> = compositionLocalOf { true }
public val LocalMediaSharedDestination: ProvidableCompositionLocal<MediaSharedElementDestination?> = compositionLocalOf { null }

@Composable
public fun Modifier.mediaSharedElementSource(preview: String?): Modifier {
    val state = LocalMediaSharedTransition.current ?: return this
    // Dialogs have their own window and cannot share this overlay.
    if (state.windowInfo !== LocalWindowInfo.current) return this
    if (preview == null || !LocalMediaSharedSourcesEnabled.current) return this
    val group = LocalMediaSharedElementGroup.current ?: rememberSaveable { Random.nextLong() }
    val key = MediaSharedElementKey(group, preview)
    return with(state.scope) {
        sharedElementWithCallerManagedVisibility(
            sharedContentState = rememberSharedContentState(key),
            visible = state.hiddenKey != key,
        ).pointerInput(key, state) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                state.pressedKey = key
            }
        }
    }
}

@Composable
public fun Modifier.mediaSharedElementDestination(preview: String?): Modifier {
    val state = LocalMediaSharedTransition.current ?: return this
    if (state.windowInfo !== LocalWindowInfo.current) return this
    val destination = LocalMediaSharedDestination.current ?: return this
    val group = destination.group ?: return this
    if (preview == null) return this
    val key = MediaSharedElementKey(group, preview)
    SideEffect {
        if (destination.visibilityScope.transition.targetState == androidx.compose.animation.EnterExitState.Visible) {
            state.hiddenKey = key
        }
    }
    return with(state.scope) {
        sharedElement(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = destination.visibilityScope,
        )
    }
}
