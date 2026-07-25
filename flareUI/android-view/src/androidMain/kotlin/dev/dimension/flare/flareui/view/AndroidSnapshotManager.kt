package dev.dimension.flare.flareui.view

import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Delivers global snapshot writes to standalone Runtime compositions.
 *
 * Compose UI normally owns this process-level service. A pure Android View host has to start it
 * itself so state writes wake the Recomposer.
 */
internal object AndroidSnapshotManager {
    private val started = AtomicBoolean(false)
    private val notificationPending = AtomicBoolean(false)

    fun ensureStarted() {
        if (!started.compareAndSet(false, true)) return

        val notifications = Channel<Unit>(capacity = Channel.CONFLATED)
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            for (notification in notifications) {
                notificationPending.set(false)
                Snapshot.sendApplyNotifications()
            }
        }
        Snapshot.registerGlobalWriteObserver {
            if (notificationPending.compareAndSet(false, true)) {
                notifications.trySend(Unit)
            }
        }
    }
}
