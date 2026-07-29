@file:OptIn(dev.dimension.flare.ui.LowLevelFlareApi::class)

package dev.dimension.flare.ui.benchmark

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import dev.dimension.flare.ui.FlareUiComposable
import dev.dimension.flare.ui.foundation.Column
import dev.dimension.flare.ui.foundation.Text
import dev.dimension.flare.ui.swiftui.FlareSwiftUIChildren
import dev.dimension.flare.ui.swiftui.FlareSwiftUIHost
import dev.dimension.flare.ui.swiftui.FlareSwiftUINodePlugin
import dev.dimension.flare.ui.swiftui.FlareSwiftUITree
import dev.dimension.flare.ui.swiftui.FlareSwiftUITreeObserver
import dev.dimension.flare.ui.swiftui.createSwiftUIWidgetSystem

/** Swift-visible owner of the live Flare tree used by the SwiftUI XCTest benchmark. */
public class FlareSwiftUIBenchmarkHost(
    plugins: List<FlareSwiftUINodePlugin>,
    private val runtime: FlareAppleBenchmarkRuntime,
) {
    private val tree = FlareSwiftUITree()
    private val updatedText: MutableState<String> = mutableStateOf(INITIAL_TEXT)
    private val host =
        FlareSwiftUIHost(
            tree = tree,
            widgetSystem = createSwiftUIWidgetSystem(plugins),
            parent = runtime.recomposer,
        )
    private var disposed = false

    public val content: FlareSwiftUIChildren
        get() = host.content

    init {
        host.setContent {
            Column {
                initialItemTexts.forEachIndexed { index, text ->
                    if (index == UPDATED_INDEX) {
                        StatefulSwiftUIBenchmarkText(updatedText)
                    } else {
                        Text(text)
                    }
                }
            }
        }
        runtime.awaitIdle()
    }

    public fun setObserver(observer: FlareSwiftUITreeObserver?) {
        check(!disposed) { "FlareSwiftUIBenchmarkHost is already disposed." }
        host.setObserver(observer)
    }

    public fun updateText(value: String) {
        check(!disposed) { "FlareSwiftUIBenchmarkHost is already disposed." }
        updatedText.value = value
        runtime.awaitIdle()
    }

    public fun currentText(): String = updatedText.value

    /** Used by the benchmark harness to wait until the Flare composition has emitted its root. */
    public fun renderedRootCount(): Int = content.nodes.size

    public fun dispose() {
        if (disposed) return
        disposed = true
        host.dispose()
    }

    private companion object {
        const val ITEM_COUNT: Int = 100
        const val UPDATED_INDEX: Int = ITEM_COUNT / 2
        const val INITIAL_TEXT: String = "Item 50 A"
        val initialItemTexts =
            List(ITEM_COUNT) { index ->
                if (index == UPDATED_INDEX) INITIAL_TEXT else "Item $index"
            }
    }
}

@Composable
@FlareUiComposable
private fun StatefulSwiftUIBenchmarkText(text: MutableState<String>) {
    Text(text.value)
}
