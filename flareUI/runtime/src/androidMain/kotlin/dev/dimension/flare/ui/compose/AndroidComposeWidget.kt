package dev.dimension.flare.ui.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.UiComposable
import androidx.compose.ui.platform.testTag
import dev.dimension.flare.ui.AbstractFlareWidget
import dev.dimension.flare.ui.FlareBackend
import dev.dimension.flare.ui.FlareBackendWidget
import dev.dimension.flare.ui.FlareChildren
import dev.dimension.flare.ui.FlareModifier
import dev.dimension.flare.ui.FlareWidget
import dev.dimension.flare.ui.testTagOrNull

/** Strong type token for Jetpack Compose renderer plugins and widget systems. */
public data object AndroidComposeBackend : FlareBackend

/**
 * Stateful renderer node consumed by [FlareComposeHost].
 *
 * Unlike an Android View widget, this object is not itself visible. It owns the snapshot state
 * from which [Render] emits Compose UI.
 */
public interface AndroidComposeWidget : FlareBackendWidget<AndroidComposeBackend> {
    @Composable
    @UiComposable
    public fun Render()
}

/**
 * Base for custom Compose primitive renderers.
 *
 * Flare modifiers are converted into Compose modifiers and kept as observable state so modifier
 * changes invalidate only the affected renderer node.
 */
public abstract class AbstractAndroidComposeWidget :
    AbstractFlareWidget(),
    AndroidComposeWidget {
    protected var composeModifier: Modifier by mutableStateOf(Modifier)
        private set

    final override fun onModifierChanged(
        previous: FlareModifier,
        current: FlareModifier,
    ) {
        composeModifier = current.toComposeModifier()
    }
}

/** Observable child slot used by Compose-backed container primitives. */
public class AndroidComposeChildren : FlareChildren {
    private val widgets = mutableStateListOf<AndroidComposeWidget>()

    override fun insert(
        index: Int,
        widget: FlareWidget,
    ) {
        widgets.add(index, widget.requireAndroidComposeWidget())
    }

    override fun move(
        fromIndex: Int,
        toIndex: Int,
        count: Int,
    ) {
        if (fromIndex == toIndex || count == 0) return
        val moved = widgets.subList(fromIndex, fromIndex + count).toList()
        widgets.removeRange(fromIndex, fromIndex + count)
        val destination = if (fromIndex > toIndex) toIndex else toIndex - count
        widgets.addAll(destination, moved)
    }

    override fun remove(
        index: Int,
        count: Int,
    ) {
        widgets.removeRange(index, index + count)
    }

    @Composable
    @UiComposable
    public fun Render() {
        widgets.forEach { widget ->
            key(widget) {
                widget.Render()
            }
        }
    }

    private fun FlareWidget.requireAndroidComposeWidget(): AndroidComposeWidget =
        this as? AndroidComposeWidget
            ?: error("Android Compose backend received non-Compose widget $this.")
}

private fun FlareModifier.toComposeModifier(): Modifier =
    testTagOrNull()
        ?.let(Modifier::testTag)
        ?: Modifier
