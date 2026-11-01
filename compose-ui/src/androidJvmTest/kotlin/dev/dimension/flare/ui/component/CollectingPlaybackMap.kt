package dev.dimension.flare.ui.component

import java.lang.ref.Reference
import java.util.WeakHashMap

internal class CollectingPlaybackMap : WeakHashMap<TimelinePlaybackCoordinator, Boolean>() {
    private var entryToCollect: Reference<*>? = null
    var collections = 0
        private set

    fun collectOnNextSizeRead(playback: TimelinePlaybackCoordinator) {
        entryToCollect = entries.single { it.key === playback } as Reference<*>
    }

    override val size: Int
        get() {
            val observed = super.size
            entryToCollect?.let {
                // Clear the actual weak entry at the GC race boundary without relying on GC timing.
                it.clear()
                it.enqueue()
                entryToCollect = null
                collections++
            }
            return observed
        }
}
