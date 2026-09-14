@file:OptIn(dev.dimension.compose.nativekit.LowLevelNativeKitApi::class)
@file:Suppress("ktlint:standard:annotation")

package dev.dimension.compose.nativekit.compose

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.UiComposable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.dimension.compose.nativekit.AbstractNativeKitWidget
import dev.dimension.compose.nativekit.EmitNativeKitWidget
import dev.dimension.compose.nativekit.NativeKitBackend
import dev.dimension.compose.nativekit.NativeKitChildren
import dev.dimension.compose.nativekit.NativeKitComposable
import dev.dimension.compose.nativekit.NativeKitContent
import dev.dimension.compose.nativekit.NativeKitModifier
import dev.dimension.compose.nativekit.NativeKitRendererPlugin
import dev.dimension.compose.nativekit.NativeKitSize
import dev.dimension.compose.nativekit.NativeKitWidget
import dev.dimension.compose.nativekit.NativeKitWidgetRegistrar

/** Strong type token for the Jetpack Compose renderer. */
public data object AndroidComposeBackend : NativeKitBackend

/** Snapshot-backed renderer node consumed by [NativeKitComposeHost]. */
public interface AndroidComposeWidget : NativeKitWidget {
    @Composable
    @UiComposable
    public fun Render()
}

/** Base for typed Compose primitive renderers. */
public abstract class AbstractAndroidComposeWidget :
    AbstractNativeKitWidget(),
    AndroidComposeWidget {
    protected var composeModifier: Modifier by mutableStateOf(Modifier)
        private set

    final override fun onModifierChanged(
        previous: NativeKitModifier,
        current: NativeKitModifier,
    ) {
        var result: Modifier = Modifier
        current.testTag?.let { result = result.testTag(it) }
        result =
            when (val width = current.width) {
                NativeKitSize.Wrap -> result
                NativeKitSize.Fill -> result.fillMaxWidth()
                is NativeKitSize.Fixed -> result.width(width.value.dp)
            }
        result =
            when (val height = current.height) {
                NativeKitSize.Wrap -> result
                NativeKitSize.Fill -> result.fillMaxHeight()
                is NativeKitSize.Fixed -> result.height(height.value.dp)
            }
        composeModifier = result
    }
}

/** Observable child container used by Compose-backed layout primitives. */
public class AndroidComposeChildren : NativeKitChildren {
    private val widgets = mutableStateListOf<AndroidComposeWidget>()

    override fun insert(
        index: Int,
        widget: NativeKitWidget,
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

    private fun NativeKitWidget.requireAndroidComposeWidget(): AndroidComposeWidget =
        this as? AndroidComposeWidget
            ?: error("Android Compose backend received non-Compose widget $this.")
}

/** Compose UI content rendered directly inside a NativeKit Compose tree. */
public typealias AndroidComposeContent = @Composable @UiComposable () -> Unit

/** Escape hatch for Android-only components which already expose a Compose API. */
@Composable
@NativeKitComposable
public fun AndroidCompose(content: AndroidComposeContent) {
    EmitNativeKitWidget(
        componentType = AndroidComposeContentWidget::class,
        update = {
            set(content, AndroidComposeContentWidget::setContent)
        },
    )
}

/** Registration required by [AndroidCompose]. */
public object AndroidComposeRuntimeRendererPlugin : NativeKitRendererPlugin<AndroidComposeBackend> {
    override fun register(registrar: NativeKitWidgetRegistrar<AndroidComposeBackend>) {
        registrar.register(AndroidComposeContentWidget::class) { _ ->
            AndroidComposeContentWidget()
        }
    }
}

private class AndroidComposeContentWidget :
    AbstractNativeKitWidget(),
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
