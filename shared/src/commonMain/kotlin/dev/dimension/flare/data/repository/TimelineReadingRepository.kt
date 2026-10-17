package dev.dimension.flare.data.repository

import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.model.DbHomeTimelineSelection
import dev.dimension.flare.data.database.cache.model.DbTimelineReadingSession
import dev.dimension.flare.data.database.cache.model.DbTimelineReadingSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

@Single
internal class TimelineReadingRepository(
    private val database: CacheDatabase,
    private val scope: CoroutineScope,
) {
    private val appliedEnabled = MutableStateFlow<Boolean?>(null)
    val enabled = appliedEnabled.asStateFlow()
    private val entryMutex = Mutex()

    // Identity distinguishes successive readers that reuse the same retained snapshot.
    private val liveSnapshots = MutableStateFlow<Map<String, ReadingLease>>(emptyMap())

    private class ReadingLease(
        val snapshot: DbTimelineReadingSnapshot,
    ) {
        val positionVersion = MutableStateFlow(0L)
    }

    fun nextPositionVersion(record: DbTimelineReadingSnapshot): Long? =
        liveSnapshots.value[record.key]
            ?.takeIf { it.snapshot === record }
            ?.positionVersion
            ?.updateAndGet { it + 1 }

    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedTab =
        enabled.filterNotNull().flatMapLatest {
            if (it == true) database.timelineReadingSessionDao().selectedTab() else flowOf(null)
        }

    suspend fun enterHome(readEnabled: suspend () -> Boolean) =
        entryMutex.withLock {
            val enabled = readEnabled()
            if (!enabled) {
                // Finish clearing even if the page's scope ends during the mode switch.
                withContext(NonCancellable) {
                    appliedEnabled.value = false
                    retainTabs(emptyMap())
                }
            } else {
                appliedEnabled.value = true
            }
        }

    fun savePosition(
        record: DbTimelineReadingSnapshot,
        pagingKey: String,
        itemKey: String,
        offset: Double,
    ) {
        val version = nextPositionVersion(record) ?: return
        scope.launch {
            try {
                checkpoint(record, pagingKey, itemKey, offset, version)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                DebugRepository.error(error)
            }
        }
    }

    suspend fun checkpoint(
        record: DbTimelineReadingSnapshot,
        pagingKey: String,
        itemKey: String,
        offset: Double,
        version: Long? = nextPositionVersion(record),
    ) = database.connect {
        // Capture before dispatch or debounce; transaction arrival order need not match viewport order.
        if (version == null || liveSnapshots.value[record.key]?.positionVersion?.value != version) return@connect
        val latest = current(record) ?: return@connect
        if (pagingKey != latest.activeKey) return@connect
        val row = database.pagingTimelineDao().readingItem(pagingKey, itemKey) ?: return@connect
        saveSnapshot(record, latest.copy(anchorId = itemKey, anchorSortId = row.sortId, offset = offset), remember = true)
    }

    suspend fun acquire(record: DbTimelineReadingSession): DbTimelineReadingSnapshot? {
        var acquired: DbTimelineReadingSnapshot? = null
        try {
            return database.connect {
                val latest =
                    database
                        .timelineReadingSessionDao()
                        .get(record.tabId)
                        ?.takeIf { enabled.value == true && it.key == record.key } ?: return@connect null
                val dao = database.timelineReadingSessionDao()
                val previous = latest.snapshotKey?.let { dao.snapshot(it) }
                val previousKey = previous?.key
                val key = previousKey?.takeIf { it !in liveSnapshots.value } ?: "${record.key}${Uuid.random()}/"
                val snapshot =
                    if (previous != null) {
                        if (key != previous.key) {
                            copyData(previous.key, key)
                            dao.segments(previous.key).forEach { segment ->
                                dao.saveSegment(segment.copy(key = key + segment.key.removePrefix(previous.key), snapshotKey = key))
                            }
                            dao.cursors(previous.key).forEach { cursor ->
                                dao.saveCursor(cursor.copy(key = key + cursor.key.removePrefix(previous.key), snapshotKey = key))
                            }
                        }
                        previous.copy(
                            key = key,
                            activeKey = key + previous.activeKey.removePrefix(previous.key),
                            pendingKey = key + previous.pendingKey.removePrefix(previous.key),
                            anchorId = previous.anchorId?.let { key + it.removePrefix(previous.key) },
                        )
                    } else {
                        DbTimelineReadingSnapshot(key, record.tabId, record.key)
                    }
                dao.saveSnapshot(snapshot)
                if (previous == null) dao.retainSnapshot(record.tabId, key)
                liveSnapshots.update { it + (key to ReadingLease(snapshot)) }
                acquired = snapshot
                removeUnusedSnapshots(record.key)
                snapshot
            }
        } catch (error: Exception) {
            withContext(NonCancellable) { acquired?.let { release(it) } }
            throw error
        }
    }

    suspend fun current(record: DbTimelineReadingSnapshot): DbTimelineReadingSnapshot? =
        database.connect {
            if (enabled.value != true || liveSnapshots.value[record.key]?.snapshot !== record ||
                database.timelineReadingSessionDao().get(record.tabId)?.key != record.sessionKey
            ) {
                return@connect null
            }
            database.timelineReadingSessionDao().snapshot(record.key)
        }

    suspend fun saveSnapshot(
        lease: DbTimelineReadingSnapshot,
        record: DbTimelineReadingSnapshot,
        remember: Boolean = false,
    ) = database.connect {
        if (current(lease) == null) return@connect
        val dao = database.timelineReadingSessionDao()
        dao.saveSnapshot(record)
        if (remember) dao.retainSnapshot(record.tabId, record.key)
        removeUnusedSnapshots(record.sessionKey)
    }

    suspend fun release(record: DbTimelineReadingSnapshot) =
        database.connect {
            liveSnapshots.update { if (it[record.key]?.snapshot === record) it - record.key else it }
            removeUnusedSnapshots(record.sessionKey)
        }

    private suspend fun removeUnusedSnapshots(sessionKey: String) {
        val dao = database.timelineReadingSessionDao()
        dao.snapshots(sessionKey).forEach { snapshot ->
            if (snapshot.key !in liveSnapshots.value && dao.get(snapshot.tabId)?.snapshotKey != snapshot.key) {
                deleteData(snapshot.key)
                dao.deleteSegments(snapshot.key)
                dao.deleteSnapshot(snapshot.key)
            }
        }
    }

    suspend fun selectTab(id: String) {
        database.connect {
            if (enabled.value == true) database.timelineReadingSessionDao().select(DbHomeTimelineSelection(tabId = id))
        }
    }

    suspend fun open(
        tabId: String,
        sourceKey: String,
    ): DbTimelineReadingSession? =
        database.connect {
            if (enabled.value != true) return@connect null
            val previous = database.timelineReadingSessionDao().get(tabId)
            if (previous?.sourceKey == sourceKey) {
                previous
            } else {
                previous?.let { deleteSession(it) }
                DbTimelineReadingSession(tabId, sourceKey, "reading/${Uuid.random()}/").also {
                    database.timelineReadingSessionDao().save(it)
                }
            }
        }

    suspend fun retainTabs(sources: Map<String, String>) =
        database.connect {
            database.timelineReadingSessionDao().retainSelection(sources.keys.toList())
            database.timelineReadingSessionDao().all().forEach { session ->
                if (sources[session.tabId] != session.sourceKey) {
                    deleteSession(session)
                }
            }
        }

    private suspend fun deleteSession(session: DbTimelineReadingSession) {
        deleteData(session.key)
        database.timelineReadingSessionDao().snapshots(session.key).forEach {
            database.timelineReadingSessionDao().deleteSegments(it.key)
        }
        database.timelineReadingSessionDao().deleteSnapshots(session.key)
        database.timelineReadingSessionDao().delete(session.tabId)
        liveSnapshots.update { snapshots -> snapshots.filterKeys { !it.startsWith(session.key) } }
    }

    suspend fun deleteData(prefix: String) {
        database.timelineReadingSessionDao().deleteCursors(prefix)
        val dao = database.pagingTimelineDao()
        dao.keysWithPrefix(prefix).forEach { key ->
            dao.deletePresentationReferences(key)
            dao.delete(key)
            dao.deletePagingKey(key)
        }
    }

    suspend fun copyData(
        from: String,
        to: String,
    ) {
        val dao = database.pagingTimelineDao()
        dao.keysWithPrefix(from).forEach { source ->
            val target = to + source.removePrefix(from)
            val rows = dao.getByPagingKey(source)
            val ids = rows.associate { row -> row._id to "${target.length}:$target${row._id.removePrefix("${source.length}:$source")}" }
            dao.insertAll(rows.map { it.copy(pagingKey = target, _id = ids.getValue(it._id)) })
            rows.chunked(500).forEach { batch ->
                dao.insertPresentationReferences(
                    dao.getPresentationReferences(source, batch.map { it._id }).map {
                        it.copy(pagingKey = target, timelineId = ids.getValue(it.timelineId))
                    },
                )
            }
            dao.getPagingKey(source)?.let { dao.insertPagingKey(it.copy(pagingKey = target)) }
        }
    }
}
