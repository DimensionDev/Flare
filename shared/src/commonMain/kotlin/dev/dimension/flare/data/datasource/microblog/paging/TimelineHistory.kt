package dev.dimension.flare.data.datasource.microblog.paging

import dev.dimension.flare.common.InAppNotification
import dev.dimension.flare.common.Message
import dev.dimension.flare.common.PlatformDispatchers
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.dao.HISTORY_GAP_PREFIX
import dev.dimension.flare.data.database.cache.dao.TimelineHistoryRow
import dev.dimension.flare.data.database.cache.mapper.saveToDatabase
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.DbTimelineBatch
import dev.dimension.flare.data.database.cache.model.DbTimelineBinding
import dev.dimension.flare.data.database.cache.model.DbTimelineBookmark
import dev.dimension.flare.data.database.cache.model.DbTimelineEntry
import dev.dimension.flare.data.database.cache.model.DbTimelineHistory
import dev.dimension.flare.data.datasource.microblog.MixedRemoteMediator
import dev.dimension.flare.data.datasource.microblog.MixedTimelinePageLoader
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.data.translation.NoopPreTranslationService
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
import kotlinx.serialization.json.Json
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
            mapper.reportFailure(error)
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
        val dao = database.timelineHistoryDao()
        val mixed = loader as? MixedTimelinePageLoader
        val response =
            if (mixed != null) {
                // A post shared by two feeds only proves continuity for the feed that
                // previously returned it. Keep a bounded first-page boundary per leaf.
                val sourceHeads =
                    if (batch.manual) {
                        dao
                            .olderSourceHeads(key, batch.ordinal)
                            .flatMap { Json.decodeFromString<Map<String, List<String>>>(it).entries }
                            .groupBy({ it.key }, { it.value })
                            .mapValues { (_, pages) -> pages.flatten().toSet() }
                    } else {
                        emptyMap()
                    }
                mapper.mapResult(
                    mixed.loadHistory(20, request) { sourceKey, posts ->
                        val candidates = posts.filter { it.stableItemKey in sourceHeads[sourceKey].orEmpty() }
                        val keys = TimelinePagingMapper.toDb(candidates, key).map { it.historyItemKey() }
                        keys.any { dao.row(key, it)?.timelineId != null }
                    },
                )
            } else {
                mapper.load(20, request)
            }
        database.connect {
            if (dao.history(key)?.generation != generation) return@connect
            val items = response.data.distinctBy { it.timeline._id }
            val keys = items.map { it.historyItemKey() }
            val existing = keys.chunked(500).flatMap { dao.entries(key, it) }.associateBy { it.itemKey }
            val fresh = items.zip(keys).filter { (_, itemKey) -> itemKey !in existing }
            val joined =
                loader.canJoinHistory && batch.manual &&
                    existing.values.any {
                        (dao.batch(it.batchId)?.ordinal ?: Long.MIN_VALUE) > batch.ordinal
                    }
            check(
                request !is PagingRequest.Append || response.nextKey == null || response.nextKey != request.nextKey || fresh.isNotEmpty(),
            ) { "Timeline continuation did not advance" }
            saveToDatabase(database, items)
            val nextKey = response.nextKey.takeUnless { joined }
            val pageKeys =
                Json.encodeToString(
                    listOf(
                        Json.encodeToString(keys.sorted()),
                        nextKey?.let { mixed?.continuationIdentity(it) ?: it },
                    ),
                )
            val equivalent = nextKey?.let { dao.equivalentBatch(key, batch.id, pageKeys) }
            if (equivalent != null) dao.save(equivalent.copy(nextKey = nextKey))
            dao.save(
                batch.copy(
                    nextKey = nextKey.takeIf { equivalent == null },
                    pageKeys = pageKeys,
                    sourceHeads = Json.encodeToString(response.sourceHeads),
                ),
            )
            if (mixed?.ordersByTime == true) {
                backfillTimes()
                val oldHead = dao.head(key)?.let { dao.entries(key, listOf(it.itemKey)).firstOrNull() }
                for (item in fresh) {
                    val time =
                        item.first.statusData.content.createdAt.value
                            .toEpochMilliseconds()
                    val following = dao.entryAtOrBeforeTime(key, time)
                    if (following != null && following.itemKey != oldHead?.itemKey) {
                        insertBefore(following, listOf(item))
                    } else if (following == null && oldHead != null) {
                        appendEntries(checkNotNull(dao.lastEntry(key)).batchId, listOf(item))
                    } else {
                        appendEntries(batch.id, listOf(item))
                    }
                }
                dao.batch(batch.id)?.let { updated ->
                    dao.save(updated.copy(gapAfterItemKey = keys.lastOrNull() ?: batch.gapAfterItemKey))
                }
            } else if (loader.canJoinHistory && existing.isNotEmpty()) {
                val pending = mutableListOf<Pair<DbPagingTimelineWithStatus, String>>()
                var neighbour: DbTimelineEntry? = null
                for ((item, itemKey) in items.zip(keys)) {
                    val known = dao.entries(key, listOf(itemKey)).firstOrNull()
                    if (known == null) {
                        pending += item to itemKey
                    } else {
                        insertBefore(known, pending)
                        pending.clear()
                        neighbour = known
                    }
                }
                if (pending.isNotEmpty()) {
                    val previous = checkNotNull(neighbour)
                    val updated = dao.entries(key, listOf(previous.itemKey)).single()
                    val following = dao.entryAfter(updated.batchId, updated.position)
                    if (following != null) insertBefore(following, pending) else appendEntries(updated.batchId, pending)
                }
            } else {
                appendEntries(batch.id, fresh)
            }
            dao.deleteEmptyBatch(batch.id)
        }
        mapper.enqueuePreTranslation(response.data)
        response.continuationFailure?.let { error ->
            if (batch.manual) mutableActivity.update { it.copy(failures = it.failures + (batch.id to error)) }
            throw error
        }
    }

    private suspend fun appendEntries(
        batchId: String,
        items: List<Pair<DbPagingTimelineWithStatus, String>>,
    ) {
        val dao = database.timelineHistoryDao()
        val start = dao.lastPosition(batchId) ?: 0L
        dao.save(
            items.mapIndexed { index, (item, itemKey) ->
                DbTimelineEntry(
                    key,
                    itemKey,
                    item.timeline._id,
                    batchId,
                    start + (index + 1) * POSITION_STEP,
                    item.statusData.content.createdAt.value
                        .toEpochMilliseconds(),
                )
            },
        )
    }

    private suspend fun insertBefore(
        neighbour: DbTimelineEntry,
        items: List<Pair<DbPagingTimelineWithStatus, String>>,
    ) {
        if (items.isEmpty()) return
        val dao = database.timelineHistoryDao()
        var right = dao.entries(key, listOf(neighbour.itemKey)).single()
        var left =
            dao.entryBefore(right.batchId, right.position)?.position
                ?: (right.position - (items.size + 1) * POSITION_STEP)
        if (right.position - left <= items.size) {
            // Only legacy/dense batches need new spacing; stable item identities and order stay unchanged.
            dao.save(dao.batchEntries(right.batchId).mapIndexed { index, entry -> entry.copy(position = (index + 1) * POSITION_STEP) })
            right = dao.entries(key, listOf(neighbour.itemKey)).single()
            left = dao.entryBefore(right.batchId, right.position)?.position
                ?: (right.position - (items.size + 1) * POSITION_STEP)
        }
        val step = (right.position - left) / (items.size + 1)
        dao.save(
            items.mapIndexed { index, (item, itemKey) ->
                DbTimelineEntry(
                    key,
                    itemKey,
                    item.timeline._id,
                    right.batchId,
                    left + (index + 1) * step,
                    item.statusData.content.createdAt.value
                        .toEpochMilliseconds(),
                )
            },
        )
    }

    private fun DbPagingTimelineWithStatus.historyItemKey(): String =
        if (timeline.notificationKey != null) timeline._id else "${key}_${timeline.statusId}"

    private suspend fun backfillTimes() {
        val dao = database.timelineHistoryDao()
        while (true) {
            val entries = dao.undatedEntries(key)
            if (entries.isEmpty()) return
            val dates =
                database
                    .pagingTimelineDao()
                    .getTimelineRootRowsByIds(key, entries.map { it.timelineId })
                    .associate {
                        it.timeline._id to
                            it.status.content.createdAt.value
                                .toEpochMilliseconds()
                    }
            dao.save(entries.map { it.copy(createdAt = dates[it.timelineId] ?: Long.MIN_VALUE) })
        }
    }

    companion object {
        private const val POSITION_STEP = 1_000_000L
        const val REFRESH = "refresh"
        const val APPEND = "append"
        const val PREFIX = "home-history/"

        fun isGap(itemKey: String): Boolean = itemKey.startsWith(HISTORY_GAP_PREFIX)
    }
}

@Single
internal class TimelineHistoryStore(
    private val database: CacheDatabase,
    private val notification: InAppNotification? = null,
) {
    private var enabled = true
    private val bindingVersions = MutableStateFlow<Map<String, String>>(emptyMap())
    private val tabEpochs = MutableStateFlow<Map<String, Long>>(emptyMap())
    private val mutex = Mutex()
    private val histories = mutableMapOf<String, TimelineHistory>()
    val changes = MutableStateFlow(0)
    private val bookmarkScope = CoroutineScope(SupervisorJob() + PlatformDispatchers.IO)
    private val bookmarkWrites = Mutex()

    private data class BookmarkWrite(
        val position: TimelineReadingPosition?,
        val seen: TimelineHistoryRow?,
    )

    private val pendingBookmarks = MutableStateFlow<Map<String, BookmarkWrite>>(emptyMap())

    fun savePosition(
        tabId: String,
        history: TimelineHistory,
        position: TimelineReadingPosition?,
        seen: TimelineHistoryRow?,
        binding: String?,
    ) {
        if (binding == null || bindingVersions.value[tabId] != binding) return
        pendingBookmarks.update { pending ->
            pending + (tabId to BookmarkWrite(position ?: pending[tabId]?.position, seen ?: pending[tabId]?.seen))
        }
        val write = pendingBookmarks.value[tabId]
        bookmarkScope.launch {
            bookmarkWrites.withLock {
                if (write == null || pendingBookmarks.value[tabId] !== write) return@withLock
                database.connect {
                    val dao = database.timelineHistoryDao()
                    if (dao.history(history.key)?.generation != history.generation ||
                        dao.binding(tabId)?.generation != binding
                    ) {
                        return@connect
                    }
                    val previous = dao.bookmark(tabId, history.key)
                    val position = write.position ?: previous?.let { TimelineReadingPosition(it.itemKey, it.offset) } ?: return@connect
                    val row = dao.row(history.key, position.itemKey) ?: return@connect
                    dao.save(
                        DbTimelineBookmark(
                            tabId,
                            history.key,
                            position.itemKey,
                            row.ordinal,
                            row.position,
                            position.offset,
                            write.seen?.itemKey ?: previous?.seenItemKey,
                            write.seen?.ordinal ?: previous?.seenOrdinal,
                            write.seen?.position ?: previous?.seenPosition,
                        ),
                    )
                }
            }
        }
    }

    suspend fun open(
        source: CacheableRemoteLoader<UiTimelineV2>,
        allowLongText: Boolean,
        sourceKey: String? = null,
        preTranslationService: PreTranslationService = NoopPreTranslationService,
    ): TimelineHistory? =
        mutex.withLock {
            if (!enabled) return@withLock null
            val loader = if (source is MixedRemoteMediator) source.historyLoader() else source
            val key = TimelineHistory.PREFIX + (sourceKey?.let { Json.encodeToString(listOf(it, loader.pagingKey)) } ?: loader.pagingKey)
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
                TimelineRemoteMediator(
                    mappedLoader,
                    database,
                    allowLongText,
                    preTranslationService = preTranslationService,
                    notifyError = { if (it is LoginExpiredException) notification?.onError(Message.LoginExpired, it) },
                ),
            ).also { histories[key] = it }
        }

    fun tabEpoch(tabId: String): Long = tabEpochs.value[tabId] ?: 0L

    suspend fun bind(
        tabId: String,
        history: TimelineHistory,
        epoch: Long,
    ): String? =
        mutex.withLock {
            if (!enabled || epoch != tabEpoch(tabId)) return@withLock null
            database.connect {
                val dao = database.timelineHistoryDao()
                if (dao.history(history.key)?.generation != history.generation) return@connect null
                val previous = dao.binding(tabId)
                val binding =
                    previous?.takeIf { it.historyKey == history.key }
                        ?: DbTimelineBinding(tabId, history.key, Uuid.random().toString()).also {
                            if (previous != null || dao.bookmark(tabId, history.key) == null) dao.deleteBookmarks(setOf(tabId))
                            pendingBookmarks.update { it - tabId }
                            dao.save(it)
                            previous?.let { removeUnusedHistory(it.historyKey) }
                        }
                bindingVersions.update { it + (tabId to binding.generation) }
                binding.generation
            }
        }

    suspend fun invalidateTabs(tabIds: Set<String>) =
        mutex.withLock {
            if (tabIds.isEmpty()) return@withLock
            tabEpochs.update { old -> old + tabIds.associateWith { (old[it] ?: 0L) + 1L } }
            bindingVersions.update { it - tabIds }
            pendingBookmarks.update { it - tabIds }
            database.connect {
                val dao = database.timelineHistoryDao()
                val keys = tabIds.mapNotNull { dao.binding(it)?.historyKey }.distinct()
                dao.deleteBindings(tabIds)
                dao.deleteBookmarks(tabIds)
                keys.forEach { removeUnusedHistory(it) }
            }
        }

    private suspend fun removeUnusedHistory(key: String) {
        val dao = database.timelineHistoryDao()
        if (dao.bindingCount(key) != 0) return
        dao.deleteHistory(key)
        dao.deleteEntries(key)
        dao.deleteBatches(key)
        database.pagingTimelineDao().deletePresentationReferences(key)
        database.pagingTimelineDao().delete(key)
        histories.remove(key)
    }

    suspend fun setEnabled(value: Boolean) =
        mutex.withLock {
            enabled = value
            if (!value) clearLocked()
        }

    suspend fun clear() = mutex.withLock { clearLocked() }

    private suspend fun clearLocked() {
        bindingVersions.value = emptyMap()
        pendingBookmarks.value = emptyMap()
        database.connect {
            val dao = database.timelineHistoryDao()
            dao.clearHistories()
            dao.clearBookmarks()
            dao.clearBindings()
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
            bindingVersions.value = emptyMap()
            pendingBookmarks.value = emptyMap()
            changes.value += 1
        }
}
