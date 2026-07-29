package dev.dimension.flare.ui.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.UiComposable
import dev.dimension.flare.ui.FlareComposition
import dev.dimension.flare.ui.FlareContent
import dev.dimension.flare.ui.FlareWidgetSystem

/**
 * Hosts a Flare composition inside a Jetpack Compose UI composition.
 *
 * Flare continues to own component reconciliation. Its widget nodes expose snapshot state which
 * this host renders as Compose UI.
 */
@Composable
@UiComposable
public fun FlareComposeHost(
    widgetSystem: FlareWidgetSystem<AndroidComposeBackend>,
    modifier: Modifier = Modifier,
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

    Box(modifier = modifier) {
        root.Render()
    }
}
