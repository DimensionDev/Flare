package dev.dimension.flare.data.datasource.microblog.paging

import dev.dimension.flare.common.PlatformDispatchers
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.dao.HISTORY_GAP_PREFIX
import dev.dimension.flare.data.database.cache.mapper.saveToDatabase
import dev.dimension.flare.data.database.cache.model.DbTimelineBatch
import dev.dimension.flare.data.database.cache.model.DbTimelineBookmark
import dev.dimension.flare.data.database.cache.model.DbTimelineEntry
import dev.dimension.flare.data.database.cache.model.DbTimelineHistory
import dev.dimension.flare.data.datasource.microblog.MixedRemoteMediator
import dev.dimension.flare.data.translation.PreTranslationService
import dev.dimension.flare.ui.model.TimelineReadingPosition
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

internal data class TimelineHistoryActivity(
    val loading: Set<String> = emptySet(),
    val failures: Map<String, Throwable> = emptyMap(),
    val revision: Long = 0,
)

/** Owns list data, never a reader's viewport. Shared by every reader of this source. */
internal class TimelineHistory(
    val key: String,
    val generation: String,
    private val loader: CacheableRemoteLoader<UiTimelineV2>,
    private val database: CacheDatabase,
    private val mapper: TimelineRemoteMediator,
) {
    private val writes = Mutex()
    private val initialization = Mutex()
    private var initialized = false
    private val mutableActivity = MutableStateFlow(TimelineHistoryActivity())
    val activity = mutableActivity.asStateFlow()

    suspend fun initialize(refreshOnLaunch: Boolean): Boolean =
        initialization.withLock {
            if (initialized) return@withLock true
            val success = if (refreshOnLaunch || database.timelineHistoryDao().newestBatch(key) == null) refresh() else true
            initialized = success
            success
        }

    suspend fun refresh(): Boolean =
        execute(REFRESH) {
            val dao = database.timelineHistoryDao()
            val head = dao.head(key)
            val batch =
                DbTimelineBatch(
                    id = Uuid.random().toString(),
                    historyKey = key,
                    ordinal = (dao.newestBatch(key)?.ordinal ?: 1L) - 1L,
                    nextKey = null,
                    manual = dao.tail(key) != null,
                    lowerItemKey = head?.itemKey,
                )
            fetch(batch, PagingRequest.Refresh)
        }

    suspend fun append(): Boolean =
        execute(APPEND) {
            val batch = database.timelineHistoryDao().tail(key) ?: return@execute
            batch.nextKey?.let { fetch(batch, PagingRequest.Append(it)) }
        }

    suspend fun loadGap(id: String): Boolean =
        execute(id) {
            val batch = database.timelineHistoryDao().batch(id)?.takeIf { it.historyKey == key && it.manual } ?: return@execute
            batch.nextKey?.let { fetch(batch, PagingRequest.Append(it)) }
        }

    suspend fun hasMore(): Boolean = database.timelineHistoryDao().tail(key)?.nextKey != null

    private suspend fun execute(
        operation: String,
        block: suspend () -> Unit,
    ): Boolean {
        while (true) {
            val before = mutableActivity.value
            if (operation in before.loading) {
                return mutableActivity.first { operation !in it.loading }.failures[operation] == null
            }
            if (mutableActivity.compareAndSet(
                    before,
                    before.copy(
                        loading = before.loading + operation,
                        failures =
                            before.failures - operation,
                    ),
                )
            ) {
                break
            }
        }
        return try {
            writes.withLock {
                if (database.timelineHistoryDao().history(key)?.generation != generation) return@withLock
                block()
            }
            true
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            mutableActivity.update { it.copy(failures = it.failures + (operation to error)) }
            false
        } finally {
            mutableActivity.update { it.copy(loading = it.loading - operation, revision = it.revision + 1) }
        }
    }

    private suspend fun fetch(
        batch: DbTimelineBatch,
        request: PagingRequest,
    ) {
        val response = mapper.load(20, request)
        val dao = database.timelineHistoryDao()
        database.connect {
            if (dao.history(key)?.generation != generation) return@connect
            val items = response.data.distinctBy { it.timeline._id }
            val keys = items.map { it.timeline.notificationKey?.let { _ -> it.timeline._id } ?: "${key}_${it.timeline.statusId}" }
            val existing = keys.chunked(500).flatMap { dao.entries(key, it) }.associateBy { it.itemKey }
            val fresh = items.zip(keys).filter { (_, itemKey) -> itemKey !in existing }
            val joined = loader.canJoinHistory && batch.lowerItemKey != null && batch.lowerItemKey in keys
            check(
                request !is PagingRequest.Append || response.nextKey == null || response.nextKey != request.nextKey || fresh.isNotEmpty(),
            ) {
                "Timeline continuation did not advance"
            }
            // Retain every fetched post; only membership determines presentation order.
            saveToDatabase(database, items)
            var position = (dao.lastPosition(batch.id) ?: -1L) + 1L
            dao.save(
                fresh.map { (item, itemKey) ->
                    DbTimelineEntry(key, itemKey, item.timeline._id, batch.id, position++)
                },
            )
            dao.save(batch.copy(nextKey = response.nextKey.takeUnless { joined }))
        }
        mapper.enqueuePreTranslation(response.data)
        response.continuationFailure?.let { error ->
            if (batch.manual) mutableActivity.update { it.copy(failures = it.failures + (batch.id to error)) }
            throw error
        }
    }

    companion object {
        const val REFRESH = "refresh"
        const val APPEND = "append"
        const val PREFIX = "home-history/"

        fun isGap(itemKey: String): Boolean = itemKey.startsWith(HISTORY_GAP_PREFIX)
    }
}

@Single
internal class TimelineHistoryStore(
    private val database: CacheDatabase,
    private val preTranslationService: PreTranslationService,
) {
    private val mutex = Mutex()
    private val histories = mutableMapOf<String, TimelineHistory>()
    val changes = MutableStateFlow(0)
    private val bookmarkScope = CoroutineScope(SupervisorJob() + PlatformDispatchers.IO)
    private val bookmarkWrites = Mutex()
    private val pendingBookmarks = MutableStateFlow<Map<String, TimelineReadingPosition>>(emptyMap())

    fun savePosition(
        tabId: String,
        history: TimelineHistory,
        position: TimelineReadingPosition,
    ) {
        pendingBookmarks.update { it + (tabId to position) }
        bookmarkScope.launch {
            bookmarkWrites.withLock {
                if (pendingBookmarks.value[tabId] !== position) return@withLock
                database.connect {
                    val dao = database.timelineHistoryDao()
                    if (dao.history(history.key)?.generation != history.generation) return@connect
                    val row = dao.row(history.key, position.itemKey) ?: return@connect
                    dao.save(DbTimelineBookmark(tabId, history.key, position.itemKey, row.ordinal, row.position, position.offset))
                }
            }
        }
    }

    suspend fun open(
        source: CacheableRemoteLoader<UiTimelineV2>,
        allowLongText: Boolean,
    ): TimelineHistory =
        mutex.withLock {
            val loader = if (source is MixedRemoteMediator) source.historyLoader() else source
            val key = TimelineHistory.PREFIX + loader.pagingKey
            val dao = database.timelineHistoryDao()
            val record = dao.history(key)
            if (record != null) histories[key]?.let { return@withLock it }
            val history = record ?: DbTimelineHistory(key, Uuid.random().toString()).also { dao.save(it) }
            val mappedLoader =
                object : CacheableRemoteLoader<UiTimelineV2> by loader {
                    override val pagingKey = key
                }
            TimelineHistory(
                key,
                history.generation,
                loader,
                database,
                TimelineRemoteMediator(mappedLoader, database, allowLongText, preTranslationService = preTranslationService),
            ).also { histories[key] = it }
        }

    suspend fun clear() =
        mutex.withLock {
            database.connect {
                val dao = database.timelineHistoryDao()
                dao.clearHistories()
                dao.clearBookmarks()
                dao.clearEntries()
                dao.clearBatches()
                database.pagingTimelineDao().deleteNamespaceReferences(TimelineHistory.PREFIX)
                database.pagingTimelineDao().deleteNamespaceRows(TimelineHistory.PREFIX)
            }
            histories.clear()
            changes.value += 1
        }

    suspend fun cacheCleared() =
        mutex.withLock {
            histories.clear()
            pendingBookmarks.value = emptyMap()
            changes.value += 1
        }
}
