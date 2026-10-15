package dev.dimension.flare.data.repository

import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.model.DbHomeTimelineSelection
import dev.dimension.flare.data.database.cache.model.DbTimelineReadingSession
import dev.dimension.flare.data.database.cache.model.DbTimelineReadingSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
    private val liveSnapshots = mutableMapOf<String, DbTimelineReadingSession>()

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
        record: DbTimelineReadingSession,
        pagingKey: String,
        itemKey: String,
        offset: Double,
    ) {
        scope.launch {
            checkpoint(record, pagingKey, itemKey, offset)
        }
    }

    suspend fun checkpoint(
        record: DbTimelineReadingSession,
        pagingKey: String,
        itemKey: String,
        offset: Double,
    ) = database.connect {
        val latest = current(record) ?: return@connect
        val row = database.pagingTimelineDao().readingItem(pagingKey, itemKey) ?: return@connect
        saveSnapshot(record, latest.copy(anchorId = itemKey, anchorSortId = row.sortId, offset = offset), remember = true)
    }

    suspend fun acquire(record: DbTimelineReadingSession): DbTimelineReadingSession? {
        var acquired: DbTimelineReadingSession? = null
        try {
            return database.connect {
                val latest =
                    database
                        .timelineReadingSessionDao()
                        .get(record.tabId)
                        ?.takeIf { enabled.value == true && it.key == record.key } ?: return@connect null
                val previousKey = latest.snapshotKey
                val key = previousKey?.takeIf { it !in liveSnapshots } ?: "${record.key}${Uuid.random()}/"
                val previousPrefix = previousKey ?: record.key
                if (key != previousPrefix) copyData(previousPrefix, key)
                val snapshot =
                    latest.copy(
                        snapshotKey = key,
                        anchorId = latest.anchorId?.let { key + it.removePrefix(previousPrefix) },
                    )
                database.timelineReadingSessionDao().saveSnapshot(DbTimelineReadingSnapshot(key, snapshot))
                liveSnapshots[key] = snapshot
                acquired = snapshot
                removeUnusedSnapshots(record.key)
                snapshot
            }
        } catch (error: Exception) {
            withContext(NonCancellable) { acquired?.let { release(it) } }
            throw error
        }
    }

    suspend fun current(record: DbTimelineReadingSession): DbTimelineReadingSession? =
        database.connect {
            if (enabled.value != true || liveSnapshots[record.snapshotKey] !== record ||
                database.timelineReadingSessionDao().get(record.tabId)?.key != record.key
            ) {
                return@connect null
            }
            record.snapshotKey?.let { database.timelineReadingSessionDao().snapshot(it)?.session }
        }

    suspend fun saveSnapshot(
        lease: DbTimelineReadingSession,
        record: DbTimelineReadingSession,
        remember: Boolean = false,
    ) = database.connect {
        if (current(lease) == null) return@connect
        val dao = database.timelineReadingSessionDao()
        dao.saveSnapshot(DbTimelineReadingSnapshot(requireNotNull(record.snapshotKey), record))
        val previous = dao.get(record.tabId) ?: return@connect
        if (remember || previous.snapshotKey == null || previous.snapshotKey == record.snapshotKey) {
            dao.save(record)
        }
        removeUnusedSnapshots(record.key)
    }

    suspend fun release(record: DbTimelineReadingSession) =
        database.connect {
            if (liveSnapshots[record.snapshotKey] === record) record.snapshotKey?.let { liveSnapshots.remove(it) }
            removeUnusedSnapshots(record.key)
        }

    private suspend fun removeUnusedSnapshots(sessionKey: String) {
        val dao = database.timelineReadingSessionDao()
        dao.snapshots(sessionKey).forEach { snapshot ->
            if (snapshot.key !in liveSnapshots && dao.get(snapshot.session.tabId)?.snapshotKey != snapshot.key) {
                deleteData(snapshot.key)
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
        database.timelineReadingSessionDao().deleteSnapshots(session.key)
        database.timelineReadingSessionDao().delete(session.tabId)
        liveSnapshots.keys.removeAll { it.startsWith(session.key) }
    }

    suspend fun deleteData(prefix: String) {
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
