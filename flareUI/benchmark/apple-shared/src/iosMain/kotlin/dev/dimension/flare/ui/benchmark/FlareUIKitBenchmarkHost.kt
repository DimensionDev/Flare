@file:OptIn(
    dev.dimension.flare.ui.LowLevelFlareApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package dev.dimension.flare.ui.benchmark

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import dev.dimension.flare.ui.FlareComposition
import dev.dimension.flare.ui.FlareUiComposable
import dev.dimension.flare.ui.foundation.Column
import dev.dimension.flare.ui.foundation.Text
import dev.dimension.flare.ui.uikit.UIKitBackend
import dev.dimension.flare.ui.uikit.UIKitChildren
import dev.dimension.flare.ui.uikit.createUIKitWidgetSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import platform.UIKit.UIStackView
import platform.UIKit.UIView

/**
 * Reusable synchronous Recomposer for Apple CPU microbenchmarks.
 *
 * Keep one instance alive around a warm-mount benchmark. Create it inside the measured block only
 * when measuring the first Flare host in a process. Production Apple hosts render on
 * CADisplayLink frames; this runtime avoids adding an arbitrary frame wait to every CPU sample.
 */
public class FlareAppleBenchmarkRuntime {
    private var frameTimeNanos: Long = 0L
    private val frameClock: BroadcastFrameClock = createFrameClock()
    private val scope =
        CoroutineScope(
            Dispatchers.Unconfined +
                SupervisorJob() +
                frameClock,
        )
    internal val recomposer: Recomposer = Recomposer(scope.coroutineContext)
    private var disposed: Boolean = false

    init {
        scope.launch {
            recomposer.runRecomposeAndApplyChanges()
        }
    }

    internal fun awaitIdle() {
        check(!disposed) { "FlareAppleBenchmarkRuntime is already disposed." }
        repeat(MAX_IDLE_PASSES) {
            Snapshot.sendApplyNotifications()
            if (!recomposer.hasPendingWork) return
        }
        check(!recomposer.hasPendingWork) {
            "Benchmark recomposer did not become idle after $MAX_IDLE_PASSES synchronous passes."
        }
    }

    public fun dispose() {
        if (disposed) return
        disposed = true
        recomposer.cancel()
        scope.cancel()
    }

    private fun createFrameClock(): BroadcastFrameClock {
        lateinit var clock: BroadcastFrameClock
        clock =
            BroadcastFrameClock {
                frameTimeNanos += FRAME_DURATION_NANOS
                clock.sendFrame(frameTimeNanos)
            }
        return clock
    }

    private companion object {
        const val FRAME_DURATION_NANOS: Long = 16_666_667L
        const val MAX_IDLE_PASSES: Int = 8
    }
}

public class FlareUIKitBenchmarkHost(
    private val runtime: FlareAppleBenchmarkRuntime,
) {
    private val root = UIStackView()
    private val backend = UIKitBackend()
    private val updatedText: MutableState<String> = mutableStateOf(INITIAL_TEXT)
    private val composition =
        FlareComposition(
            root = UIKitChildren(root, backend),
            widgetSystem = widgetSystem,
            backend = backend,
            parent = runtime.recomposer,
        )
    private var disposed: Boolean = false

    public val view: UIView
        get() = root

    init {
        composition.setContent {
            Column {
                initialItemTexts.forEachIndexed { index, text ->
                    if (index == UPDATED_INDEX) {
                        StatefulBenchmarkText(updatedText)
                    } else {
                        Text(text)
                    }
                }
            }
        }
        runtime.awaitIdle()
    }

    public fun updateText(value: String) {
        check(!disposed) { "FlareUIKitBenchmarkHost is already disposed." }
        updatedText.value = value
        runtime.awaitIdle()
    }

    public fun renderedText(): String? {
        val column = root.arrangedSubviews.singleOrNull() as? UIStackView ?: return null
        return (column.arrangedSubviews.getOrNull(UPDATED_INDEX) as? platform.UIKit.UILabel)?.text
    }

    public fun dispose() {
        if (disposed) return
        disposed = true
        composition.dispose()
    }

    private companion object {
        const val ITEM_COUNT: Int = 100
        const val UPDATED_INDEX: Int = ITEM_COUNT / 2
        const val INITIAL_TEXT: String = "Item 50 A"
        val initialItemTexts =
            List(ITEM_COUNT) { index ->
                if (index == UPDATED_INDEX) INITIAL_TEXT else "Item $index"
            }
        val widgetSystem = createUIKitWidgetSystem()
    }
}

@Composable
@FlareUiComposable
private fun StatefulBenchmarkText(text: MutableState<String>) {
    Text(text.value)
}
