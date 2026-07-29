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
 * UIKit and SwiftUI own different widget trees, but they must use the same snapshot notification,
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

private object AppleFlareSnapshotManager {
    private var users = 0
    private var scope: CoroutineScope? = null
    private var notifications: Channel<Unit>? = null
    private var observerHandle: ObserverHandle? = null

    fun acquire() {
        users += 1
        if (users != 1) return

        val newNotifications = Channel<Unit>(capacity = Channel.CONFLATED)
        // Keep notification delivery out of the global write observer to avoid snapshot re-entry.
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        notifications = newNotifications
        scope = newScope
        newScope.launch {
            for (notification in newNotifications) {
                Snapshot.sendApplyNotifications()
            }
        }
        observerHandle =
            Snapshot.registerGlobalWriteObserver {
                newNotifications.trySend(Unit)
            }
    }

    fun release() {
        check(users > 0) { "AppleFlareSnapshotManager was released without an active user." }
        users -= 1
        if (users != 0) return

        observerHandle?.dispose()
        observerHandle = null
        notifications?.close()
        notifications = null
        scope?.cancel()
        scope = null
    }
}

private class AppleRecomposerRuntime {
    private val frameClock: AppleFrameClock = createAppleFrameClock()
    private val coroutineContext = appleRecomposerContext(frameClock)
    private val scope = CoroutineScope(coroutineContext)
    val recomposer: Recomposer = Recomposer(coroutineContext)

    init {
        AppleFlareSnapshotManager.acquire()
        scope.launch {
            recomposer.runRecomposeAndApplyChanges()
        }
    }

    fun dispose() {
        recomposer.cancel()
        scope.cancel()
        frameClock.dispose()
        AppleFlareSnapshotManager.release()
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

internal interface AppleFrameClock : MonotonicFrameClock {
    fun dispose()
}

internal expect fun createAppleFrameClock(): AppleFrameClock

private fun appleRecomposerContext(frameClock: MonotonicFrameClock): CoroutineContext =
    Dispatchers.Main.immediate + frameClock + SupervisorJob()
