package dev.dimension.flare.data.database.cache.mapper

import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.DbStatus
import dev.dimension.flare.data.database.cache.model.DbStatusReference
import dev.dimension.flare.data.database.cache.model.DbTimelineItemPresentationReference
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2

internal suspend fun saveToDatabase(
    database: CacheDatabase,
    items: List<DbPagingTimelineWithStatus>,
) {
    val timelines = ArrayList<DbPagingTimeline>(items.size)
    val statusesById = LinkedHashMap<String, DbStatus>(items.size * 2)
    items.forEach { item ->
        timelines += item.timeline
        statusesById.getOrPut(item.statusData.id) { item.statusData }
    }
    val existingTimelineById = loadExistingTimeline(database = database, incoming = timelines)
    items.forEach { item ->
        item.references.forEach { reference ->
            reference.status?.data?.let { status -> statusesById.getOrPut(status.id) { status } }
        }
        item.presentationReferences.forEach { reference ->
            reference.status?.data?.let { status -> statusesById.getOrPut(status.id) { status } }
        }
    }
    val statuses = statusesById.values.toList()
    val statusChanges = loadChangedStatuses(database, statuses)
    val usersByKey = LinkedHashMap<MicroBlogKey, UiProfile>()
    statusChanges.statuses.forEach { status -> status.content.collectUsersInto(usersByKey) }
    database.upsertUsers(usersByKey.values.map { it.toDbUser() })
    if (statusChanges.statuses.isNotEmpty()) {
        database.statusDao().insertAll(statusChanges.statuses)
        statusChanges.dependencyBumpIds.chunked(DEPENDENCY_REVISION_BATCH_SIZE).forEach { statusIds ->
            database.statusDao().bumpTimelineDependencies(statusIds)
        }
    }
    val statusReferenceRootIds = HashSet<String>()
    val presentationReferenceItems = ArrayList<DbPagingTimelineWithStatus>()
    timelines.forEachIndexed { index, timeline ->
        val existing = existingTimelineById[timeline._id]
        if (existing?.statusReferenceHash != timeline.statusReferenceHash) {
            statusReferenceRootIds += timeline.statusId
        }
        if (existing?.presentationReferenceHash != timeline.presentationReferenceHash) {
            presentationReferenceItems += items[index]
        }
    }
    syncStatusReferences(
        database = database,
        rootStatusIds = statusReferenceRootIds,
        items = items,
    )
    syncPresentationReferences(
        database = database,
        items = presentationReferenceItems,
    )
    val changedTimeline = loadChangedTimeline(existingById = existingTimelineById, incoming = timelines)
    if (changedTimeline.isNotEmpty()) {
        database.pagingTimelineDao().insertAll(changedTimeline)
    }
}

private const val SQL_IN_BATCH_SIZE = 500
private const val DEPENDENCY_REVISION_BATCH_SIZE = 300

private suspend fun loadChangedStatuses(
    database: CacheDatabase,
    incoming: List<DbStatus>,
): StatusChanges {
    if (incoming.isEmpty()) {
        return StatusChanges()
    }
    val existingById =
        incoming
            .map { it.id }
            .distinct()
            .chunked(SQL_IN_BATCH_SIZE)
            .flatMap { database.statusDao().getFingerprints(it) }
            .associateBy { it.id }
    val changed =
        incoming.filter { status ->
            val saved = existingById[status.id]
            saved == null || saved.contentHash != status.contentHash || saved.renderHash != status.renderHash
        }
    val newIds = changed.mapNotNullTo(HashSet()) { status -> status.id.takeIf { it !in existingById } }
    val restoredDependencyIds =
        if (newIds.isEmpty()) {
            emptySet()
        } else {
            database
                .statusDao()
                .getMissingTimelineDependencyStatusIds()
                .mapNotNullTo(HashSet()) { row -> row.id.takeIf { it in newIds } }
        }
    return StatusChanges(
        statuses = changed,
        dependencyBumpIds = changed.mapNotNull { status -> status.id.takeIf { it in existingById } } + restoredDependencyIds,
    )
}

private data class StatusChanges(
    val statuses: List<DbStatus> = emptyList(),
    val dependencyBumpIds: List<String> = emptyList(),
)

private suspend fun loadExistingTimeline(
    database: CacheDatabase,
    incoming: List<DbPagingTimeline>,
): Map<String, DbPagingTimeline> =
    incoming
        .groupBy { it.pagingKey }
        .flatMap { (pagingKey, rows) ->
            rows
                .map { it.statusId }
                .distinct()
                .chunked(SQL_IN_BATCH_SIZE)
                .flatMap { chunk ->
                    database.pagingTimelineDao().getByPagingKeyAndStatusIds(
                        pagingKey = pagingKey,
                        statusIds = chunk,
                    )
                }
        }.associateBy { DbPagingTimeline.createId(pagingKey = it.pagingKey, statusId = it.statusId) }

private fun loadChangedTimeline(
    existingById: Map<String, DbPagingTimeline>,
    incoming: List<DbPagingTimeline>,
): List<DbPagingTimeline> =
    incoming.mapNotNull { timeline ->
        val existing = existingById[timeline._id]
        when {
            existing == null -> timeline
            existing.hasSameStoredContentAs(timeline) -> null
            else -> timeline.copy(_id = existing._id)
        }
    }

private suspend fun syncStatusReferences(
    database: CacheDatabase,
    rootStatusIds: Set<String>,
    items: List<DbPagingTimelineWithStatus>,
) {
    if (rootStatusIds.isEmpty()) {
        return
    }
    val dao = database.statusReferenceDao()
    val existing =
        rootStatusIds
            .chunked(SQL_IN_BATCH_SIZE)
            .flatMap { dao.getByStatusIds(it) }
    val incomingById = LinkedHashMap<String, DbStatusReference>()
    items.forEach { item ->
        item.references.forEach { referenceWithStatus ->
            val reference = referenceWithStatus.reference
            if (reference.statusId in rootStatusIds) {
                incomingById[reference._id] = reference
            }
        }
    }
    val existingById = existing.associateBy { it._id }
    val stale = existing.filter { it._id !in incomingById }
    val changed =
        incomingById.mapNotNull { (id, reference) ->
            val saved = existingById[id]
            when {
                saved == null -> reference
                saved.hasSameStoredContentAs(reference) -> null
                else -> reference.copy(_id = saved._id)
            }
        }
    if (stale.isNotEmpty()) {
        dao.deleteItems(stale)
    }
    if (changed.isNotEmpty()) {
        dao.insertAll(changed)
    }
}

private suspend fun syncPresentationReferences(
    database: CacheDatabase,
    items: List<DbPagingTimelineWithStatus>,
) {
    val dao = database.pagingTimelineDao()
    items.groupBy { it.timeline.pagingKey }.forEach { (pagingKey, rows) ->
        val statusIds = rows.map { it.timeline.statusId }.distinct()
        if (statusIds.isEmpty()) {
            return@forEach
        }
        val existing =
            statusIds
                .chunked(SQL_IN_BATCH_SIZE)
                .flatMap { dao.getPresentationReferences(pagingKey = pagingKey, statusIds = it) }
        val incomingById = LinkedHashMap<String, DbTimelineItemPresentationReference>()
        rows.forEach { item ->
            item.presentationReferences.forEach { reference ->
                incomingById[reference.reference._id] = reference.reference
            }
        }
        val existingById = existing.associateBy { it._id }
        val stale = existing.filter { it._id !in incomingById }
        val changed =
            incomingById.mapNotNull { (id, reference) ->
                val saved = existingById[id]
                when {
                    saved == null -> reference
                    saved.hasSameStoredContentAs(reference) -> null
                    else -> reference.copy(_id = saved._id)
                }
            }
        if (stale.isNotEmpty()) {
            dao.deletePresentationReferenceItems(stale)
        }
        if (changed.isNotEmpty()) {
            dao.insertPresentationReferences(changed)
        }
    }
}

private fun DbStatusReference.hasSameStoredContentAs(other: DbStatusReference): Boolean =
    referenceType == other.referenceType &&
        statusId == other.statusId &&
        referenceStatusId == other.referenceStatusId &&
        referenceOrder == other.referenceOrder

private fun DbTimelineItemPresentationReference.hasSameStoredContentAs(other: DbTimelineItemPresentationReference): Boolean =
    pagingKey == other.pagingKey &&
        statusId == other.statusId &&
        referenceStatusId == other.referenceStatusId &&
        presentationType == other.presentationType &&
        referenceOrder == other.referenceOrder

private fun DbPagingTimeline.hasSameStoredContentAs(other: DbPagingTimeline): Boolean =
    pagingKey == other.pagingKey &&
        statusId == other.statusId &&
        sortId == other.sortId &&
        message == other.message &&
        messageRenderHash == other.messageRenderHash &&
        statusReferenceHash == other.statusReferenceHash &&
        presentationReferenceHash == other.presentationReferenceHash

private fun UiTimelineV2.collectUsersInto(destination: MutableMap<MicroBlogKey, UiProfile>) {
    when (this) {
        is UiTimelineV2.Post -> {
            user?.addTo(destination)
        }

        is UiTimelineV2.TimelinePostItem -> {
            post.collectUsersInto(destination)
            presentation.message?.user?.addTo(destination)
            presentation.inlineParents.forEach { it.collectUsersInto(destination) }
            presentation.quotes.forEach { it.collectUsersInto(destination) }
            presentation.repost?.collectUsersInto(destination)
        }

        is UiTimelineV2.User -> {
            value.addTo(destination)
            message?.user?.addTo(destination)
        }

        is UiTimelineV2.UserList -> {
            users.forEach { it.addTo(destination) }
            message?.user?.addTo(destination)
            post?.collectUsersInto(destination)
        }

        else -> {
        }
    }
}

private fun UiProfile.addTo(destination: MutableMap<MicroBlogKey, UiProfile>) {
    destination.getOrPut(key) { this }
}
