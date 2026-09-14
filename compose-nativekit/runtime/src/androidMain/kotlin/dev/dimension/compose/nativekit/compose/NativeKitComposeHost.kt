package dev.dimension.compose.nativekit.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.UiComposable
import dev.dimension.compose.nativekit.NativeKitComposition
import dev.dimension.compose.nativekit.NativeKitContent
import dev.dimension.compose.nativekit.NativeKitWidgetSystem

/** Hosts a NativeKit composition which renders real Jetpack Compose UI nodes. */
@Composable
@UiComposable
public fun NativeKitComposeHost(
    widgetSystem: NativeKitWidgetSystem<AndroidComposeBackend>,
    content: NativeKitContent,
) {
    val parent = rememberCompositionContext()
    val root = remember(parent, widgetSystem) { AndroidComposeChildren() }
    val currentContent = rememberUpdatedState(content)

    DisposableEffect(parent, root, widgetSystem) {
        val composition =
            NativeKitComposition(
                root = root,
                widgetSystem = widgetSystem,
                backend = AndroidComposeBackend,
                parent = parent,
            )
        composition.setContent {
            currentContent.value()
        }
        onDispose(composition::dispose)
    }

    // ponytail: This adds one state-tree hop. Rework applier polymorphism only if profiling shows
    // that the extra invalidation misses real frame budgets.
    root.Render()
}
