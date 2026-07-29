package dev.dimension.flare.ui.android

import androidx.compose.runtime.snapshots.ObserverHandle
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.AndroidUiDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** Delivers Compose snapshot notifications to the Android View host. */
internal object AndroidFlareSnapshotManager {
    private var users: Int = 0
    private var scope: CoroutineScope? = null
    private var notifications: Channel<Unit>? = null
    private var observerHandle: ObserverHandle? = null

    fun acquire() {
        users += 1
        if (users != 1) return

        val newNotifications = Channel<Unit>(capacity = Channel.CONFLATED)
        // Dispatch asynchronously. Sending notifications inline from the global write observer can
        // re-enter snapshot application before the write has finished.
        val newScope = CoroutineScope(SupervisorJob() + AndroidUiDispatcher.Main)
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
        check(users > 0) { "AndroidFlareSnapshotManager was released without an active user." }
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
