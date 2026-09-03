package dev.dimension.flare.data.datasource.microblog.paging

import androidx.room3.withReadTransaction
import dev.dimension.flare.common.PlatformDispatchers
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.dao.DbTimelinePageIdentity
import dev.dimension.flare.data.database.cache.dao.PagingTimelineDao
import dev.dimension.flare.data.database.cache.dao.getTimelinePageInCurrentTransaction
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class TimelineDbPageCache {
    private val mutex = Mutex()
    private var snapshot = Snapshot()

    suspend fun hasCurrentWindowChanged(
        dao: PagingTimelineDao,
        pagingKey: String,
    ): Boolean =
        mutex.withLock {
            if (!snapshot.loaded) {
                return@withLock true
            }
            val identities =
                dao.getTimelinePageIdentities(
                    pagingKey = pagingKey,
                    offset = 0,
                    limit = snapshot.identities.size + 1,
                )
            val currentWindow = identities.take(snapshot.identities.size)
            val nextIdentity = identities.getOrNull(snapshot.identities.size)
            currentWindow != snapshot.identities || nextIdentity != snapshot.nextIdentity
        }

    suspend fun load(
        database: CacheDatabase,
        pagingKey: String,
        offset: Int,
        limit: Int,
    ): List<DbPagingTimelineWithStatus> =
        mutex.withLock {
            database.withReadTransaction {
                loadPage(
                    dao = database.pagingTimelineDao(),
                    pagingKey = pagingKey,
                    offset = offset,
                    limit = limit,
                )
            }
        }

    private suspend fun loadPage(
        dao: PagingTimelineDao,
        pagingKey: String,
        offset: Int,
        limit: Int,
    ): List<DbPagingTimelineWithStatus> {
        val identityRows =
            dao.getTimelinePageIdentities(
                pagingKey = pagingKey,
                offset = offset,
                limit = limit + 1,
            )
        val pageIdentities = identityRows.take(limit)
        if (pageIdentities.isEmpty()) {
            if (offset == 0) {
                snapshot =
                    Snapshot(
                        loaded = true,
                        nextIdentity = identityRows.firstOrNull(),
                    )
            }
            return emptyList()
        }

        if (offset == 0) {
            val cachedByIdentity = snapshot.identities.zip(snapshot.data).toMap()
            val reused = pageIdentities.map { cachedByIdentity[it] }
            val firstDirty = reused.indexOfFirst { it == null }
            val data =
                if (firstDirty < 0) {
                    reused.filterNotNull()
                } else {
                    // ponytail: Split disjoint ranges only if a trace shows this span is too broad.
                    val lastDirty = reused.indexOfLast { it == null }
                    val fetchedData =
                        dao.getTimelinePageInCurrentTransaction(
                            pagingKey = pagingKey,
                            offset = firstDirty,
                            limit = lastDirty - firstDirty + 1,
                        )
                    reused.mapIndexed { index, cached ->
                        if (index in firstDirty..lastDirty) {
                            fetchedData[index - firstDirty]
                        } else {
                            checkNotNull(cached)
                        }
                    }
                }
            snapshot =
                Snapshot(
                    loaded = true,
                    identities = pageIdentities.take(data.size),
                    data = data,
                    nextIdentity = identityRows.getOrNull(data.size),
                )
            return data
        }

        val data =
            dao.getTimelinePageInCurrentTransaction(
                pagingKey = pagingKey,
                offset = offset,
                limit = limit,
            )
        if (snapshot.data.size == offset && data.size == pageIdentities.size) {
            snapshot =
                Snapshot(
                    loaded = true,
                    identities = snapshot.identities + pageIdentities,
                    data = snapshot.data + data,
                    nextIdentity = identityRows.getOrNull(pageIdentities.size),
                )
        }
        return data
    }

    private data class Snapshot(
        val loaded: Boolean = false,
        val identities: List<DbTimelinePageIdentity> = emptyList(),
        val data: List<DbPagingTimelineWithStatus> = emptyList(),
        val nextIdentity: DbTimelinePageIdentity? = null,
    )
}

internal class TimelineDbPageLoader(
    private val database: CacheDatabase,
    private val pagingKey: String,
    private val pageCache: TimelineDbPageCache,
) : OffsetFromStartPageLoader<DbPagingTimelineWithStatus> {
    override suspend fun load(
        offset: Int,
        limit: Int,
    ): List<DbPagingTimelineWithStatus> =
        pageCache.load(
            database = database,
            pagingKey = pagingKey,
            offset = offset,
            limit = limit,
        )

    override fun observeInvalidations(invalidate: () -> Unit): PageInvalidationSubscription {
        val ready = CompletableDeferred<Unit>()
        val job =
            CoroutineScope(PlatformDispatchers.IO).launch(start = CoroutineStart.UNDISPATCHED) {
                val dao = database.pagingTimelineDao()
                database
                    .invalidationTracker
                    .createFlow(
                        "DbPagingTimeline",
                        "DbStatus",
                        "status_reference",
                        "timeline_item_presentation_reference",
                        "DbTranslation",
                        emitInitialState = true,
                    ).collect {
                        if (ready.complete(Unit)) {
                            return@collect
                        }
                        val currentWindowChanged =
                            pageCache.hasCurrentWindowChanged(
                                dao = dao,
                                pagingKey = pagingKey,
                            )
                        if (!currentWindowChanged) {
                            return@collect
                        }
                        invalidate()
                    }
            }
        return PageInvalidationSubscription(
            job = job,
            ready = ready,
        )
    }
}
