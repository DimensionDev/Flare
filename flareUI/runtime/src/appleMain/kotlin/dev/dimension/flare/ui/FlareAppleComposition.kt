@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.dimension.flare.ui

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.ObserverHandle
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import platform.Foundation.NSThread
import kotlin.coroutines.CoroutineContext
import kotlin.native.HiddenFromObjC

/**
 * Shared Compose Runtime driver for Apple renderer hosts.
 *
 * UIKit and AppKit own different widget trees, but they must use the same snapshot notification,
 * frame-clock, and disposal rules. Keeping that machinery here prevents each backend from
 * installing a subtly different Apple recomposer.
 */
@HiddenFromObjC
@LowLevelFlareApi
public class FlareAppleComposition<B : FlareBackend>(
    root: FlareChildren,
    widgetSystem: FlareWidgetSystem<B>,
    backend: B,
    private val hostName: String,
) {
    init {
        checkMainThread()
    }

    private val runtime: AppleRecomposerRuntime = AppleRecomposerRuntimePool.acquire()
    private val composition =
        try {
            FlareComposition(
                root = root,
                widgetSystem = widgetSystem,
                backend = backend,
                parent = runtime.recomposer,
            )
        } catch (throwable: Throwable) {
            AppleRecomposerRuntimePool.release(runtime)
            throw throwable
        }
    private var disposed = false

    public fun setContent(content: FlareContent) {
        checkMainThread()
        check(!disposed) { "$hostName is already disposed." }
        composition.setContent(content)
    }

    public fun dispose() {
        checkMainThread()
        if (disposed) return
        disposed = true
        try {
            composition.dispose()
        } finally {
            AppleRecomposerRuntimePool.release(runtime)
        }
    }

    private fun checkMainThread() {
        check(NSThread.isMainThread) {
            "$hostName must be used from the Apple main thread."
        }
    }
}

private class AppleRecomposerRuntime {
    private val frameClock: AppleFrameClock = createAppleFrameClock()
    private val coroutineContext = appleRecomposerContext(frameClock)
    private val scope = CoroutineScope(coroutineContext)
    private val notifications = Channel<Unit>(capacity = Channel.CONFLATED)
    private val observerHandle: ObserverHandle =
        Snapshot.registerGlobalWriteObserver {
            notifications.trySend(Unit)
        }
    val recomposer: Recomposer = Recomposer(coroutineContext)

    init {
        // Keep delivery out of the global write observer to avoid snapshot re-entry.
        scope.launch {
            for (notification in notifications) {
                Snapshot.sendApplyNotifications()
            }
        }
        scope.launch {
            recomposer.runRecomposeAndApplyChanges()
        }
    }

    fun dispose() {
        observerHandle.dispose()
        notifications.close()
        recomposer.cancel()
        scope.cancel()
    }
}

private object AppleRecomposerRuntimePool {
    private var runtime: AppleRecomposerRuntime? = null
    private var users: Int = 0

    fun acquire(): AppleRecomposerRuntime {
        check(NSThread.isMainThread) {
            "The Apple Flare runtime must be acquired on the main thread."
        }
        val current = runtime ?: AppleRecomposerRuntime().also { runtime = it }
        users += 1
        return current
    }

    fun release(value: AppleRecomposerRuntime) {
        check(NSThread.isMainThread) {
            "The Apple Flare runtime must be released on the main thread."
        }
        check(runtime === value && users > 0) {
            "The Apple Flare runtime was released without a matching acquisition."
        }
        users -= 1
        if (users == 0) {
            runtime = null
            value.dispose()
        }
    }
}

internal interface AppleFrameClock : MonotonicFrameClock

internal expect fun createAppleFrameClock(): AppleFrameClock

private fun appleRecomposerContext(frameClock: MonotonicFrameClock): CoroutineContext =
    Dispatchers.Main.immediate + frameClock + SupervisorJob()
