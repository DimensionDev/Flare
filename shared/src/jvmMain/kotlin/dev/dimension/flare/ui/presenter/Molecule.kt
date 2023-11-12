package dev.dimension.flare.ui.presenter

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.ObserverHandle
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import java.lang.System.nanoTime
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.cancellation.CancellationException

/** The different recomposition modes of Molecule. */
public enum class RecompositionMode {
    /**
     * When a recomposition is needed, use a [MonotonicFrameClock] pulled from the calling [CoroutineContext]
     * to determine when to run. If no clock is found in the context, an exception is thrown.
     *
     * Use this option to drive Molecule with a built-in frame clock or a custom one.
     */
    ContextClock,

    /**
     * Run recomposition eagerly whenever one is needed.
     * Molecule will emit a new item every time the snapshot state is invalidated.
     */
    Immediate,
}

/**
 * Launch a coroutine into this [CoroutineScope] which will continually recompose `body`
 * to produce a [StateFlow] stream of [T] values.
 */
public fun <T> CoroutineScope.launchMolecule2(
    mode: RecompositionMode,
    body: @Composable () -> T,
): StateFlow<T> {
    var flow: MutableStateFlow<T>? = null

    launchMolecule2(
        mode = mode,
        emitter = { value ->
            val outputFlow = flow
            if (outputFlow != null) {
                outputFlow.value = value
            } else {
                flow = MutableStateFlow(value)
            }
        },
        body = body,
    )

    return flow!!
}

/**
 * Launch a coroutine into this [CoroutineScope] which will continually recompose `body`
 * to invoke [emitter] with each returned [T] value.
 *
 * [launchMolecule]'s [emitter] is always free-running and will not respect backpressure.
 * Use [moleculeFlow] to create a backpressure-capable flow.
 */
public fun <T> CoroutineScope.launchMolecule2(
    mode: RecompositionMode,
    emitter: (value: T) -> Unit,
    body: @Composable () -> T,
) {
    val clockContext =
        when (mode) {
            RecompositionMode.ContextClock -> EmptyCoroutineContext
            RecompositionMode.Immediate -> GatedFrameClock(this)
        }

    with(this + clockContext) {
        val recomposer = Recomposer(coroutineContext)
        val composition = Composition(UnitApplier, recomposer)
        var snapshotHandle: ObserverHandle? = null
        launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                recomposer.runRecomposeAndApplyChanges()
            } catch (e: CancellationException) {
                composition.dispose()
                snapshotHandle?.dispose()
            }
        }

        var applyScheduled = false
        snapshotHandle =
            Snapshot.registerGlobalWriteObserver {
                if (!applyScheduled) {
                    applyScheduled = true
                    launch {
                        applyScheduled = false
                        Snapshot.sendApplyNotifications()
                    }
                }
            }

        composition.setContent {
            emitter(body())
        }
    }
}

private object UnitApplier : AbstractApplier<Unit>(Unit) {
    override fun insertBottomUp(
        index: Int,
        instance: Unit,
    ) {}

    override fun insertTopDown(
        index: Int,
        instance: Unit,
    ) {}

    override fun move(
        from: Int,
        to: Int,
        count: Int,
    ) {}

    override fun remove(
        index: Int,
        count: Int,
    ) {}

    override fun onClear() {}
}

/**
 * A [MonotonicFrameClock] that is either running, or not.
 *
 * While running, any request for a frame immediately succeeds. If stopped, requests for a frame wait until
 * the clock is set to run again.
 */
internal class GatedFrameClock(scope: CoroutineScope) : MonotonicFrameClock {
    private val frameSends = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            for (send in frameSends) sendFrame()
        }
    }

    var isRunning: Boolean = true
        set(value) {
            val started = value && !field
            field = value
            if (started) {
                sendFrame()
            }
        }

    private var lastNanos = 0L
    private var lastOffset = 0

    private fun sendFrame() {
        val timeNanos = nanoTime()

        // Since we only have millisecond resolution, ensure the nanos form always increases by
        // incrementing a nano offset if we collide with the previous timestamp.
        val offset =
            if (timeNanos == lastNanos) {
                lastOffset + 1
            } else {
                lastNanos = timeNanos
                0
            }
        lastOffset = offset

        clock.sendFrame(timeNanos + offset)
    }

    private val clock =
        BroadcastFrameClock {
            if (isRunning) frameSends.trySend(Unit).getOrThrow()
        }

    override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
        return clock.withFrameNanos(onFrame)
    }
}
