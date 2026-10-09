package dev.dimension.flare.data.repository

import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.model.DbHomeTimelineSelection
import dev.dimension.flare.data.database.cache.model.DbTimelineReadingSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

@Single
internal class TimelineReadingRepository(
    private val database: CacheDatabase,
    private val scope: CoroutineScope,
) {
    val selectedTab get() = database.timelineReadingSessionDao().selectedTab()

    fun savePosition(
        record: DbTimelineReadingSession,
        pagingKey: String,
        itemKey: String,
        offset: Double,
    ) {
        scope.launch {
            val row = database.pagingTimelineDao().readingItem(pagingKey, itemKey) ?: return@launch
            database.timelineReadingSessionDao().savePosition(record.tabId, record.key, itemKey, row.sortId, offset)
        }
    }

    suspend fun selectTab(id: String) {
        database.timelineReadingSessionDao().select(DbHomeTimelineSelection(tabId = id))
    }

    suspend fun open(
        tabId: String,
        sourceKey: String,
    ): DbTimelineReadingSession =
        database.connect {
            val previous = database.timelineReadingSessionDao().get(tabId)
            if (previous?.sourceKey == sourceKey) {
                previous
            } else {
                previous?.let { deleteData(it.key) }
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
                    deleteData(session.key)
                    database.timelineReadingSessionDao().delete(session.tabId)
                }
            }
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
