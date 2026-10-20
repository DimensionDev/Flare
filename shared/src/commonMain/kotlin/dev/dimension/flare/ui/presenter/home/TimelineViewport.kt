@file:OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)

package dev.dimension.flare.ui.presenter.home

import androidx.datastore.core.DataStore
import dev.dimension.flare.data.datastore.model.HomeTimelinePosition
import dev.dimension.flare.data.datastore.model.HomeTimelinePreferences
import dev.dimension.flare.ui.model.TimelineReadingPosition
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Clock

/** UI coordinates only. Cache paging and native stable-key anchoring own the list. */
internal class TimelineViewport(
    private val preferences: DataStore<HomeTimelinePreferences>,
    private val sourceKey: String,
    private val tabId: String,
    private val appScope: CoroutineScope,
    private val canSave: suspend () -> Boolean = { true },
) {
    private val mutableTarget = MutableStateFlow<TimelineReadingPosition?>(null)
    private val mutableHasNewContent = MutableStateFlow(false)
    private val mutableIsAtLatest = MutableStateFlow(true)
    val target = mutableTarget.asStateFlow()
    val hasNewContent = mutableHasNewContent.asStateFlow()
    val isAtLatest = mutableIsAtLatest.asStateFlow()
    private val restored = CompletableDeferred<Unit>()
    private var current: TimelineReadingPosition? = null
    private var headKey: String? = null
    private var dirtyAt = 0L
    private var savedAt = 0L
    private var active = true
    private var initialized = false

    suspend fun restore() {
        if (initialized) return
        try {
            val saved =
                preferences.data.first().positions[tabId]?.takeIf {
                    it.sourceKey == sourceKey && it.position.index >= 0 && it.position.offset.isFinite()
                }
            if (initialized) return
            current = saved?.position ?: TimelineReadingPosition(0, 0.0)
            mutableTarget.value = current
            dirtyAt = saved?.updatedAt ?: 0
            savedAt = dirtyAt
            mutableIsAtLatest.value = current?.index?.let { it == 0 } ?: true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            error.printStackTrace()
            if (!initialized) {
                current = TimelineReadingPosition(0, 0.0)
                mutableTarget.value = current
            }
        } finally {
            initialized = true
        }
    }

    suspend fun awaitRestoration() = restored.await()

    /** Returns the ordinary paging index to access while filling a local prefix. */
    fun cacheChanged(
        keys: List<String>,
        endOfCache: Boolean,
        firstPostKey: String? = keys.firstOrNull(),
    ): Int? {
        val first = firstPostKey
        val previous = headKey
        if (previous != null && first != previous && previous in keys) mutableHasNewContent.value = true
        headKey = first
        val pending = target.value ?: return null
        if (pending.index < keys.size) return null
        if (!endOfCache) return keys.lastIndex.takeIf { it >= 0 }
        // A missing local index falls back to the top; never fetch a remote page to restore it.
        mutableTarget.value = TimelineReadingPosition(0, 0.0)
        current = target.value
        dirtyAt = nextPositionTime()
        mutableIsAtLatest.value = true
        if (keys.isEmpty()) restorationFinished(target.value)
        return null
    }

    fun viewportChanged(
        index: Int,
        offset: Double,
    ) {
        if (index < 0 || !offset.isFinite() || !initialized || target.value != null) return
        val position = TimelineReadingPosition(index, offset)
        if (current != null && current != position) dirtyAt = nextPositionTime()
        current = position
        mutableIsAtLatest.value = index == 0
        if (index == 0) mutableHasNewContent.value = false
    }

    fun positionChanged(
        index: Int,
        offset: Double,
    ) {
        if (index < 0 || !offset.isFinite()) return
        initialized = true
        mutableTarget.value = null
        restored.complete(Unit)
        viewportChanged(index, offset)
        dirtyAt = nextPositionTime()
    }

    fun deactivate() {
        active = false
    }

    fun restorationFinished(expected: TimelineReadingPosition?) {
        if (target.value != expected) return
        current = expected ?: current
        mutableTarget.value = null
        restored.complete(Unit)
    }

    fun restoreCurrentPosition() {
        if (target.value == null) mutableTarget.value = current
    }

    fun savePosition() {
        if (!active || target.value != null) return
        val position = current ?: return
        if (dirtyAt <= savedAt) return
        val timestamp = dirtyAt
        appScope.launch {
            try {
                preferences.updateData { previous ->
                    // Recheck inside the serialized write so an old window cannot undo a newer bookmark or tab removal.
                    if (!active || !canSave() || (previous.positions[tabId]?.updatedAt ?: 0) > timestamp) {
                        previous
                    } else {
                        previous.copy(positions = previous.positions + (tabId to HomeTimelinePosition(sourceKey, position, timestamp)))
                    }
                }
                savedAt = maxOf(savedAt, timestamp)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                error.printStackTrace()
            }
        }
    }

    fun showLatest() {
        initialized = true
        mutableTarget.value = TimelineReadingPosition(0, 0.0)
        current = target.value
        dirtyAt = nextPositionTime()
        mutableHasNewContent.value = false
        mutableIsAtLatest.value = true
    }
}

private val positionTime = kotlin.concurrent.atomics.AtomicLong(0)

private fun nextPositionTime(): Long {
    while (true) {
        val previous = positionTime.load()
        val next = maxOf(previous + 1, Clock.System.now().toEpochMilliseconds() * 1000)
        if (positionTime.compareAndSet(previous, next)) return next
    }
}
