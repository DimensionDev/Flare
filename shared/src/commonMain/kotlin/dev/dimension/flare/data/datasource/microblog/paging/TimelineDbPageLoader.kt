package dev.dimension.flare.data.datasource.microblog.paging

import dev.dimension.flare.common.PlatformDispatchers
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.dao.DbTimelinePageIdentity
import dev.dimension.flare.data.database.cache.dao.DbTimelinePageIdentityRoot
import dev.dimension.flare.data.database.cache.loadTimelineItems
import dev.dimension.flare.data.database.cache.loadTimelinePageIdentities
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.DbStatus
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps the logical prefix already exposed to Paging loaded across invalidations.
 *
 * A smaller refresh request cannot evict its tail, and rows inserted above the prefix extend it
 * through the previously loaded last row. Database deletions can still make the prefix shorter.
 */
internal class TimelineDbPageCache {
    private val mutex = Mutex()
    private var snapshot = Snapshot()
    private var generation = 0L

    suspend fun hasCurrentWindowChanged(
        database: CacheDatabase,
        pagingKey: String,
        changedTables: Set<String>? = null,
    ): Boolean {
        val state =
            mutex.withLock {
                if (!snapshot.loaded) {
                    return true
                }
                CacheState(
                    generation = generation,
                    snapshot = snapshot,
                )
            }
        val dependencyTablesChanged =
            changedTables == null ||
                changedTables.any { table -> !table.equals(TIMELINE_TABLE, ignoreCase = true) }
        val identities =
            if (dependencyTablesChanged) {
                database.loadTimelinePageIdentities(
                    pagingKey = pagingKey,
                    offset = 0,
                    limit = state.snapshot.identities.size + 1,
                )
            } else {
                null
            }
        val roots =
            if (identities == null) {
                database
                    .pagingTimelineDao()
                    .getTimelinePageIdentityRoots(
                        pagingKey = pagingKey,
                        offset = 0,
                        limit = state.snapshot.identities.size + 1,
                    )
            } else {
                null
            }
        return mutex.withLock {
            if (generation != state.generation) {
                return@withLock true
            }
            if (identities != null) {
                val currentWindow = identities.take(state.snapshot.identities.size)
                val nextIdentity = identities.getOrNull(state.snapshot.identities.size)
                currentWindow != state.snapshot.identities || nextIdentity != state.snapshot.nextIdentity
            } else {
                checkNotNull(roots)
                val currentWindow = roots.take(state.snapshot.identities.size)
                val nextRoot = roots.getOrNull(state.snapshot.identities.size)
                currentWindow.size != state.snapshot.identities.size ||
                    currentWindow.indices.any { index ->
                        !currentWindow[index].hasSameStructureAs(state.snapshot.identities[index])
                    } ||
                    !nextRoot.hasSameStructureAs(state.snapshot.nextIdentity)
            }
        }
    }

    suspend fun load(
        database: CacheDatabase,
        pagingKey: String,
        offset: Int,
        limit: Int,
    ): List<DbPagingTimelineWithStatus> {
        while (true) {
            val state =
                mutex.withLock {
                    CacheState(
                        generation = generation,
                        snapshot = snapshot,
                    )
                }
            val retainedPrefixLimit =
                if (offset == 0) {
                    state.snapshot.identities.lastOrNull()?.let { lastIdentity ->
                        database
                            .pagingTimelineDao()
                            .getTimelinePrefixLengthThroughStatus(
                                pagingKey = pagingKey,
                                statusId = lastIdentity.statusId,
                            )
                    } ?: 0
                } else {
                    0
                }
            val requestedLimit =
                if (offset == 0) {
                    maxOf(limit, state.snapshot.data.size, retainedPrefixLimit)
                } else {
                    limit
                }
            val identityRows =
                database.loadTimelinePageIdentities(
                    pagingKey = pagingKey,
                    offset = offset,
                    limit = requestedLimit + 1,
                )
            val pageIdentities = identityRows.take(requestedLimit)
            val unchangedData =
                if (offset == 0 && pageIdentities == state.snapshot.identities &&
                    identityRows.getOrNull(pageIdentities.size) == state.snapshot.nextIdentity
                ) {
                    mutex.withLock {
                        state.snapshot.data.takeIf { generation == state.generation }
                    }
                } else {
                    null
                }
            if (unchangedData != null) {
                return unchangedData
            }
            val resolved =
                resolve(
                    database = database,
                    pagingKey = pagingKey,
                    previous = state.snapshot.entries,
                    reusableStatuses = state.snapshot.statuses,
                    identities = pageIdentities,
                )
            val committed =
                mutex.withLock {
                    if (generation != state.generation) {
                        return@withLock null
                    }
                    val data = resolved.map { it.data }.toPersistentList()
                    if (offset == 0) {
                        snapshot =
                            Snapshot(
                                loaded = true,
                                identities = resolved.map { it.identity }.toPersistentList(),
                                data = data,
                                entries = resolved.associateBy { it.identity.statusId }.toPersistentMap(),
                                statuses = resolved.collectStatuses().toPersistentMap(),
                                nextIdentity = identityRows.getOrNull(pageIdentities.size),
                            )
                    } else if (
                        snapshot.data.size == offset &&
                        resolved.size == pageIdentities.size
                    ) {
                        snapshot =
                            snapshot.copy(
                                loaded = true,
                                identities = snapshot.identities.addingAll(resolved.map { it.identity }),
                                data = snapshot.data.addingAll(data),
                                entries = snapshot.entries.puttingAll(resolved.associateBy { it.identity.statusId }),
                                statuses = snapshot.statuses.puttingAll(resolved.collectStatuses()),
                                nextIdentity = identityRows.getOrNull(pageIdentities.size),
                            )
                    }
                    generation++
                    data
                }
            if (committed != null) {
                return committed
            }
        }
    }

    private suspend fun resolve(
        database: CacheDatabase,
        pagingKey: String,
        previous: PersistentMap<String, CachedEntry>,
        reusableStatuses: PersistentMap<String, DbStatus>,
        identities: List<DbTimelinePageIdentity>,
    ): List<CachedEntry> {
        if (identities.isEmpty()) {
            return emptyList()
        }
        val missingStatusIds =
            identities.mapNotNull { identity ->
                if (previous[identity.statusId]?.identity?.hasSamePayloadAs(identity) == true) {
                    null
                } else {
                    identity.statusId
                }
            }
        val fetchedById =
            database
                .loadTimelineItems(
                    pagingKey = pagingKey,
                    statusIds = missingStatusIds,
                    reusableStatuses = reusableStatuses,
                ).associateBy { it.timeline.statusId }
        return identities.mapNotNull { identity ->
            val cached = previous[identity.statusId]
            val data =
                if (cached != null && cached.identity.hasSamePayloadAs(identity)) {
                    cached.data.withSortId(identity.sortId)
                } else {
                    fetchedById[identity.statusId]
                }
            data?.let { CachedEntry(identity = identity, data = it) }
        }
    }

    private fun DbPagingTimelineWithStatus.withSortId(sortId: Long): DbPagingTimelineWithStatus =
        if (timeline.sortId == sortId) {
            this
        } else {
            copy(timeline = timeline.copy(sortId = sortId))
        }

    private fun List<CachedEntry>.collectStatuses(): Map<String, DbStatus> {
        val result = LinkedHashMap<String, DbStatus>(size * 2)
        forEach { entry ->
            val item = entry.data
            result[item.statusData.id] = item.statusData
            item.references.forEach { reference ->
                reference.status?.data?.let { result[it.id] = it }
            }
            item.presentationReferences.forEach { reference ->
                reference.status?.data?.let { result[it.id] = it }
            }
        }
        return result
    }

    private fun DbTimelinePageIdentity.hasSamePayloadAs(other: DbTimelinePageIdentity): Boolean =
        statusId == other.statusId &&
            rootContentHash == other.rootContentHash &&
            messageRenderHash == other.messageRenderHash &&
            statusReferenceHash == other.statusReferenceHash &&
            presentationReferenceHash == other.presentationReferenceHash &&
            dependencyCount == other.dependencyCount &&
            dependencyRevision == other.dependencyRevision

    private fun DbTimelinePageIdentityRoot?.hasSameStructureAs(other: DbTimelinePageIdentity?): Boolean =
        when {
            this == null || other == null -> {
                this == null && other == null
            }

            else -> {
                statusId == other.statusId &&
                    sortId == other.sortId &&
                    rootContentHash == other.rootContentHash &&
                    messageRenderHash == other.messageRenderHash &&
                    statusReferenceHash == other.statusReferenceHash &&
                    presentationReferenceHash == other.presentationReferenceHash
            }
        }

    private data class Snapshot(
        val loaded: Boolean = false,
        val identities: PersistentList<DbTimelinePageIdentity> = persistentListOf(),
        val data: PersistentList<DbPagingTimelineWithStatus> = persistentListOf(),
        val entries: PersistentMap<String, CachedEntry> = persistentHashMapOf(),
        val statuses: PersistentMap<String, DbStatus> = persistentHashMapOf(),
        val nextIdentity: DbTimelinePageIdentity? = null,
    )

    private data class CachedEntry(
        val identity: DbTimelinePageIdentity,
        val data: DbPagingTimelineWithStatus,
    )

    private data class CacheState(
        val generation: Long,
        val snapshot: Snapshot,
    )

    private companion object {
        const val TIMELINE_TABLE = "DbPagingTimeline"
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
            database = database,
            pagingKey = pagingKey,
            offset = offset,
            limit = limit,
        )

    override fun observeInvalidations(invalidate: () -> Unit): PageInvalidationSubscription {
        val ready = CompletableDeferred<Unit>()
        val job =
            CoroutineScope(PlatformDispatchers.IO).launch(start = CoroutineStart.UNDISPATCHED) {
                database
                    .invalidationTracker
                    .createFlow(
                        "DbPagingTimeline",
                        "DbStatus",
                        "status_reference",
                        "timeline_item_presentation_reference",
                        "DbTranslation",
                        emitInitialState = true,
                    ).collect { changedTables ->
                        if (ready.complete(Unit)) {
                            return@collect
                        }
                        val currentWindowChanged =
                            pageCache.hasCurrentWindowChanged(
                                database = database,
                                pagingKey = pagingKey,
                                changedTables = changedTables,
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
