package dev.dimension.flare.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.UiComposable
import dev.dimension.flare.ui.FlareComposition
import dev.dimension.flare.ui.FlareContent
import dev.dimension.flare.ui.FlareWidgetSystem

/** Hosts a Flare composition which renders real Jetpack Compose UI nodes. */
@Composable
@UiComposable
public fun FlareComposeHost(
    widgetSystem: FlareWidgetSystem<AndroidComposeBackend>,
    content: FlareContent,
) {
    val parent = rememberCompositionContext()
    val root = remember(parent, widgetSystem) { AndroidComposeChildren() }
    val currentContent = rememberUpdatedState(content)

    DisposableEffect(parent, root, widgetSystem) {
        val composition =
            FlareComposition(
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
