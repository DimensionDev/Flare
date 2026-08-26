@file:OptIn(dev.dimension.flare.ui.LowLevelFlareApi::class)
@file:Suppress("ktlint:standard:annotation")

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
import dev.dimension.flare.ui.EmitFlareWidget
import dev.dimension.flare.ui.FlareBackend
import dev.dimension.flare.ui.FlareChildren
import dev.dimension.flare.ui.FlareContent
import dev.dimension.flare.ui.FlareModifier
import dev.dimension.flare.ui.FlareRendererPlugin
import dev.dimension.flare.ui.FlareUiComposable
import dev.dimension.flare.ui.FlareWidget
import dev.dimension.flare.ui.FlareWidgetRegistrar

/** Strong type token for the Jetpack Compose renderer. */
public data object AndroidComposeBackend : FlareBackend

/** Snapshot-backed renderer node consumed by [FlareComposeHost]. */
public interface AndroidComposeWidget : FlareWidget {
    @Composable
    @UiComposable
    public fun Render()
}

/** Base for typed Compose primitive renderers. */
public abstract class AbstractAndroidComposeWidget :
    AbstractFlareWidget(),
    AndroidComposeWidget {
    protected var composeModifier: Modifier by mutableStateOf(Modifier)
        private set

    final override fun onModifierChanged(
        previous: FlareModifier,
        current: FlareModifier,
    ) {
        composeModifier =
            current.testTag
                ?.let(Modifier::testTag)
                ?: Modifier
    }
}

/** Observable child container used by Compose-backed layout primitives. */
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

/** Compose UI content rendered directly inside a Flare Compose tree. */
public typealias AndroidComposeContent = @Composable @UiComposable () -> Unit

/** Escape hatch for Android-only components which already expose a Compose API. */
@Composable
@FlareUiComposable
public fun AndroidCompose(content: AndroidComposeContent) {
    EmitFlareWidget(
        componentType = AndroidComposeContentWidget::class,
        update = {
            set(content, AndroidComposeContentWidget::setContent)
        },
    )
}

/** Registration required by [AndroidCompose]. */
public object AndroidComposeRuntimeRendererPlugin : FlareRendererPlugin<AndroidComposeBackend> {
    override fun register(registrar: FlareWidgetRegistrar<AndroidComposeBackend>) {
        registrar.register(AndroidComposeContentWidget::class) { _ ->
            AndroidComposeContentWidget()
        }
    }
}

private class AndroidComposeContentWidget :
    AbstractFlareWidget(),
    AndroidComposeWidget {
    private var renderedContent: AndroidComposeContent by mutableStateOf({})

    fun setContent(value: AndroidComposeContent) {
        renderedContent = value
    }

    @Composable
    @UiComposable
    override fun Render() {
        renderedContent()
    }

    override fun dispose() {
        renderedContent = {}
    }
}
