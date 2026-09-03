package dev.dimension.flare.data.datasource.microblog.paging

import androidx.room3.withReadTransaction
import dev.dimension.flare.common.PlatformDispatchers
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.dao.DbTimelinePageIdentity
import dev.dimension.flare.data.database.cache.dao.PagingTimelineDao
import dev.dimension.flare.data.database.cache.dao.getTimelinePageInCurrentTransaction
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
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
    private val canonicalProfiles = HashMap<MicroBlogKey, UiProfile>()

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
                    limit = snapshot.size + 1,
                )
            !snapshot.matches(identities)
        }

    suspend fun load(
        database: CacheDatabase,
        pagingKey: String,
        offset: Int,
        limit: Int,
    ): List<TimelinePageItem> =
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
    ): List<TimelinePageItem> {
        val identityRows =
            dao.getTimelinePageIdentities(
                pagingKey = pagingKey,
                offset = offset,
                limit = limit + 1,
            )
        val pageSize = minOf(limit, identityRows.size)
        val pageIdentities = ArrayList<DbTimelinePageIdentity>(pageSize)
        repeat(pageSize) { pageIdentities += identityRows[it] }
        if (pageIdentities.isEmpty()) {
            if (offset == 0) {
                snapshot.replace(emptyList(), emptyList(), identityRows.firstOrNull())
                canonicalProfiles.clear()
            }
            return emptyList()
        }

        if (offset == 0) {
            var firstDirty = -1
            var lastDirty = -1
            pageIdentities.forEachIndexed { index, identity ->
                if (snapshot[identity] == null) {
                    if (firstDirty < 0) firstDirty = index
                    lastDirty = index
                }
            }
            val data =
                if (firstDirty < 0) {
                    ArrayList<TimelinePageItem>(pageIdentities.size).also { result ->
                        pageIdentities.forEach { identity -> result += checkNotNull(snapshot[identity]) }
                    }
                } else {
                    // ponytail: Split disjoint ranges only if a trace shows this span is too broad.
                    val fetchedData =
                        loadProjectedPage(
                            dao = dao,
                            pagingKey = pagingKey,
                            offset = firstDirty,
                            limit = lastDirty - firstDirty + 1,
                            identities = pageIdentities.subList(firstDirty, lastDirty + 1),
                        )
                    ArrayList<TimelinePageItem>(pageIdentities.size).also { result ->
                        pageIdentities.forEachIndexed { index, identity ->
                            result +=
                                if (index in firstDirty..lastDirty) {
                                    fetchedData[index - firstDirty]
                                } else {
                                    checkNotNull(snapshot[identity])
                                }
                        }
                    }
                }
            val retainedIdentities =
                if (data.size == pageIdentities.size) pageIdentities else pageIdentities.take(data.size)
            snapshot.replace(
                identities = retainedIdentities,
                data = data,
                nextIdentity = identityRows.getOrNull(data.size),
            )
            TimelinePagingMapper.rebuildCanonicalProfiles(data, canonicalProfiles)
            return data
        }

        val data =
            loadProjectedPage(
                dao = dao,
                pagingKey = pagingKey,
                offset = offset,
                limit = limit,
                identities = pageIdentities,
            )
        if (snapshot.size == offset && data.size == pageIdentities.size) {
            snapshot.append(
                identities = pageIdentities,
                data = data,
                nextIdentity = identityRows.getOrNull(pageIdentities.size),
            )
        }
        return data
    }

    private suspend fun loadProjectedPage(
        dao: PagingTimelineDao,
        pagingKey: String,
        offset: Int,
        limit: Int,
        identities: List<DbTimelinePageIdentity>,
    ): List<TimelinePageItem> =
        TimelinePagingMapper.toPageItems(
            items = dao.getTimelinePageInCurrentTransaction(pagingKey, offset, limit),
            identities = identities,
            pagingKey = pagingKey,
            canonicalProfiles = canonicalProfiles,
        )

    private class Snapshot {
        var loaded: Boolean = false
            private set
        var size: Int = 0
            private set
        private var nextIdentity: DbTimelinePageIdentity? = null
        private var segments = ArrayList<Segment>()
        private var itemByIdentity = HashMap<DbTimelinePageIdentity, TimelinePageItem>()

        operator fun get(identity: DbTimelinePageIdentity): TimelinePageItem? = itemByIdentity[identity]

        fun replace(
            identities: List<DbTimelinePageIdentity>,
            data: List<TimelinePageItem>,
            nextIdentity: DbTimelinePageIdentity?,
        ) {
            loaded = true
            segments = ArrayList(1)
            itemByIdentity = HashMap(data.size * 4 / 3 + 1)
            size = 0
            if (data.isNotEmpty()) {
                append(identities, data, nextIdentity)
            } else {
                this.nextIdentity = nextIdentity
            }
        }

        fun append(
            identities: List<DbTimelinePageIdentity>,
            data: List<TimelinePageItem>,
            nextIdentity: DbTimelinePageIdentity?,
        ) {
            require(identities.size == data.size)
            if (data.isNotEmpty()) {
                segments += Segment(identities)
                identities.forEachIndexed { index, identity -> itemByIdentity[identity] = data[index] }
                size += data.size
            }
            this.nextIdentity = nextIdentity
        }

        fun matches(identityRows: List<DbTimelinePageIdentity>): Boolean {
            if (identityRows.getOrNull(size) != nextIdentity) {
                return false
            }
            if (identityRows.size < size) {
                return false
            }
            var index = 0
            segments.forEach { segment ->
                segment.identities.forEach { identity ->
                    if (identityRows[index++] != identity) {
                        return false
                    }
                }
            }
            return true
        }

        private data class Segment(
            val identities: List<DbTimelinePageIdentity>,
        )
    }
}

internal class TimelineDbPageLoader(
    private val database: CacheDatabase,
    private val pagingKey: String,
    private val pageCache: TimelineDbPageCache,
) : OffsetFromStartPageLoader<TimelinePageItem> {
    override suspend fun load(
        offset: Int,
        limit: Int,
    ): List<TimelinePageItem> =
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
