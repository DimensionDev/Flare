package dev.dimension.flare.ui.benchmark

import android.view.View
import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

internal const val ITEM_COUNT: Int = 100
internal const val UPDATED_INDEX: Int = ITEM_COUNT / 2
internal const val INITIAL_TEXT: String = "Item 50 A"
internal const val UPDATED_TEXT: String = "Item 50 B"
private const val WIDTH_PX: Int = 1080
private const val MAX_HEIGHT_PX: Int = 10_000
private const val FRAME_DURATION_NANOS: Long = 16_666_667L
private const val MAX_IDLE_PASSES: Int = 8

internal val INITIAL_ITEM_TEXTS: List<String> =
    List(ITEM_COUNT) { index ->
        if (index == UPDATED_INDEX) INITIAL_TEXT else "Item $index"
    }

/**
 * A reusable synchronous Compose runtime for CPU microbenchmarks.
 *
 * Runtime construction is intentionally outside warm mount/update measurements. Cold-host
 * benchmarks create this class inside their measured block instead.
 */
internal class BenchmarkRecomposerRuntime : AutoCloseable {
    private var frameTimeNanos: Long = 0L
    private val frameClock: BroadcastFrameClock = createFrameClock()
    private val scope =
        CoroutineScope(
            Dispatchers.Unconfined +
                SupervisorJob() +
                frameClock,
        )

    val recomposer: Recomposer = Recomposer(scope.coroutineContext)

    init {
        scope.launch {
            recomposer.runRecomposeAndApplyChanges()
        }
    }

    fun awaitIdle() {
        repeat(MAX_IDLE_PASSES) {
            Snapshot.sendApplyNotifications()
            if (!recomposer.hasPendingWork) return
        }
        check(!recomposer.hasPendingWork) {
            "Benchmark recomposer did not become idle after $MAX_IDLE_PASSES synchronous passes."
        }
    }

    override fun close() {
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
}

internal fun measureAndLayout(view: View) {
    view.measure(
        View.MeasureSpec.makeMeasureSpec(WIDTH_PX, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(MAX_HEIGHT_PX, View.MeasureSpec.AT_MOST),
    )
    view.layout(0, 0, view.measuredWidth, view.measuredHeight)
}
