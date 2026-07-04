package dev.dimension.flare.data.datasource.microblog.paging

import dev.dimension.flare.common.PlatformDispatchers
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.dao.DbTimelinePageIdentity
import dev.dimension.flare.data.database.cache.dao.PagingTimelineDao
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
        dao: PagingTimelineDao,
        pagingKey: String,
        offset: Int,
        limit: Int,
    ): List<DbPagingTimelineWithStatus> =
        mutex.withLock {
            val identityRows =
                dao.getTimelinePageIdentities(
                    pagingKey = pagingKey,
                    offset = offset,
                    limit = limit + 1,
                )
            val pageIdentities = identityRows.take(limit)
            if (pageIdentities.isEmpty()) {
                if (offset == 0) {
                    val nextIdentity = identityRows.firstOrNull()
                    snapshot =
                        Snapshot(
                            loaded = true,
                            nextIdentity = nextIdentity,
                        )
                }
                return@withLock emptyList()
            }

            if (offset == 0) {
                val reusablePrefix = snapshot.reusablePrefix(pageIdentities)
                val fetchedLimit = pageIdentities.size - reusablePrefix
                val fetchedData =
                    if (fetchedLimit > 0) {
                        dao.getTimelinePage(
                            pagingKey = pagingKey,
                            offset = reusablePrefix,
                            limit = fetchedLimit,
                        )
                    } else {
                        emptyList()
                    }
                val data = snapshot.data.take(reusablePrefix) + fetchedData
                snapshot =
                    Snapshot(
                        loaded = true,
                        identities = pageIdentities.take(data.size),
                        data = data,
                        nextIdentity = identityRows.getOrNull(data.size),
                    )
                return@withLock data
            }

            val data =
                dao.getTimelinePage(
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
            data
        }

    private data class Snapshot(
        val loaded: Boolean = false,
        val identities: List<DbTimelinePageIdentity> = emptyList(),
        val data: List<DbPagingTimelineWithStatus> = emptyList(),
        val nextIdentity: DbTimelinePageIdentity? = null,
    ) {
        fun reusablePrefix(newIdentities: List<DbTimelinePageIdentity>): Int {
            val max = minOf(identities.size, data.size, newIdentities.size)
            for (index in 0 until max) {
                if (identities[index] != newIdentities[index]) {
                    return index
                }
            }
            return max
        }
    }
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
            dao = database.pagingTimelineDao(),
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
