package dev.dimension.flare.data.database.cache.mapper

import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.dao.DbPagingTimelineVersion
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.DbStatus
import dev.dimension.flare.data.database.cache.model.DbStatusReference
import dev.dimension.flare.data.database.cache.model.DbTimelineItemPresentationReference
import dev.dimension.flare.data.database.cache.model.DbTimelineItemPresentationType
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2

internal suspend fun saveToDatabase(
    database: CacheDatabase,
    items: List<DbPagingTimelineWithStatus>,
) {
    val statuses = collectStatuses(items)
    val users = statuses.flatMap { it.content.usersInContent() }.distinctBy { it.key }
    database.upsertUsers(users.map { it.toDbUser() })
    val statusChanges = loadChangedStatuses(database, statuses)
    if (statusChanges.inserted.isNotEmpty()) {
        database.statusDao().insertNew(statusChanges.inserted)
        statusChanges.inserted
            .map { it.id }
            .chunked(DEPENDENCY_REVISION_BATCH_SIZE)
            .forEach { database.statusDao().bumpTimelineDependencies(it) }
    }
    if (statusChanges.updated.isNotEmpty()) {
        database.statusDao().updateExisting(statusChanges.updated)
    }

    val timelines =
        items
            .map { it.timeline }
            .associateBy { it.pagingKey to it.statusId }
            .values
            .toList()
    val existingTimelineByPair = loadTimelineVersions(database, timelines)
    val semanticRootsToSync =
        items
            .filter { item ->
                existingTimelineByPair[item.timeline.key]
                    ?.semanticReferenceSignature != item.timeline.semanticReferenceSignature
            }.map { it.timeline.statusId }
            .toSet()
    syncStatusReferences(
        database = database,
        rootStatusIds = semanticRootsToSync.toList(),
        incoming =
            items
                .filter { it.timeline.statusId in semanticRootsToSync }
                .flatMap { it.status.references }
                .map { it.reference },
    )
    val presentationItemsToSync =
        items.filter { item ->
            existingTimelineByPair[item.timeline.key]
                ?.presentationReferenceSignature != item.timeline.presentationReferenceSignature
        }
    syncPresentationReferences(database, presentationItemsToSync)
    val timelineChanges =
        loadChangedTimeline(
            database = database,
            incoming = timelines,
            existingByPair = existingTimelineByPair,
        )
    if (timelineChanges.inserted.isNotEmpty()) {
        database.pagingTimelineDao().insertNew(timelineChanges.inserted)
    }
    if (timelineChanges.updated.isNotEmpty()) {
        database.pagingTimelineDao().updateExisting(timelineChanges.updated)
    }
}

private const val SQL_IN_BATCH_SIZE = 500
private const val DEPENDENCY_REVISION_BATCH_SIZE = 300

private data class DbChanges<T>(
    val inserted: List<T>,
    val updated: List<T>,
)

private val DbPagingTimeline.key: Pair<String, String>
    get() = pagingKey to statusId

private data class SemanticReferenceKey(
    val statusId: String,
    val referenceType: ReferenceType,
    val referenceStatusId: String,
)

private val DbStatusReference.key: SemanticReferenceKey
    get() = SemanticReferenceKey(statusId, referenceType, referenceStatusId)

private data class PresentationReferenceKey(
    val pagingKey: String,
    val statusId: String,
    val presentationType: DbTimelineItemPresentationType,
    val referenceStatusId: String,
)

private val DbTimelineItemPresentationReference.key: PresentationReferenceKey
    get() =
        PresentationReferenceKey(
            pagingKey = pagingKey,
            statusId = statusId,
            presentationType = presentationType,
            referenceStatusId = referenceStatusId,
        )

private fun collectStatuses(items: List<DbPagingTimelineWithStatus>): List<DbStatus> =
    buildList {
        items.forEach { item ->
            add(item.status.status.data)
            item.status.references.mapNotNullTo(this) { it.status?.data }
            item.presentationReferences.mapNotNullTo(this) { it.status?.data }
        }
    }.associateBy { it.id }
        .values
        .toList()

private suspend fun loadChangedStatuses(
    database: CacheDatabase,
    incoming: List<DbStatus>,
): DbChanges<DbStatus> {
    val existingById =
        incoming
            .map { it.id }
            .chunked(SQL_IN_BATCH_SIZE)
            .flatMap { database.statusDao().getVersions(it) }
            .associateBy { it.id }
    return DbChanges(
        inserted = incoming.filter { it.id !in existingById },
        updated =
            incoming.filter { status ->
                val existing = existingById[status.id] ?: return@filter false
                existing.contentFingerprint != status.contentFingerprint ||
                    existing.renderHash != status.renderHash
            },
    )
}

private suspend fun syncStatusReferences(
    database: CacheDatabase,
    rootStatusIds: List<String>,
    incoming: List<DbStatusReference>,
) {
    if (rootStatusIds.isEmpty()) {
        return
    }
    val incomingByKey = incoming.associateBy { it.key }
    val existingByKey =
        rootStatusIds
            .chunked(SQL_IN_BATCH_SIZE)
            .flatMap { database.statusReferenceDao().getByStatusIds(it) }
            .associateBy { it.key }
    val removed = existingByKey.filterKeys { it !in incomingByKey }.values.toList()
    val inserted = incomingByKey.filterKeys { it !in existingByKey }.values.toList()
    val updated =
        incomingByKey
            .filter { (key, item) -> existingByKey[key]?.let { it != item } == true }
            .values
            .toList()
    if (removed.isNotEmpty()) {
        database.statusReferenceDao().deleteItems(removed)
    }
    if (inserted.isNotEmpty()) {
        database.statusReferenceDao().insertNew(inserted)
    }
    if (updated.isNotEmpty()) {
        database.statusReferenceDao().updateExisting(updated)
    }
}

private suspend fun syncPresentationReferences(
    database: CacheDatabase,
    items: List<DbPagingTimelineWithStatus>,
) {
    val incomingByPagingKey =
        items
            .flatMap { it.presentationReferences }
            .map { it.reference }
            .groupBy { it.pagingKey }
    items.groupBy { it.timeline.pagingKey }.forEach { (pagingKey, rows) ->
        val rootStatusIds = rows.map { it.timeline.statusId }.distinct()
        val incomingByKey = incomingByPagingKey[pagingKey].orEmpty().associateBy { it.key }
        val existingByKey =
            rootStatusIds
                .chunked(SQL_IN_BATCH_SIZE)
                .flatMap { database.pagingTimelineDao().getPresentationReferences(pagingKey, it) }
                .associateBy { it.key }
        val removed = existingByKey.filterKeys { it !in incomingByKey }.values.toList()
        val inserted = incomingByKey.filterKeys { it !in existingByKey }.values.toList()
        val updated =
            incomingByKey
                .filter { (key, item) -> existingByKey[key]?.let { it != item } == true }
                .values
                .toList()
        if (removed.isNotEmpty()) {
            database.pagingTimelineDao().deletePresentationReferences(removed)
        }
        if (inserted.isNotEmpty()) {
            database.pagingTimelineDao().insertNewPresentationReferences(inserted)
        }
        if (updated.isNotEmpty()) {
            database.pagingTimelineDao().updateExistingPresentationReferences(updated)
        }
    }
}

private suspend fun loadChangedTimeline(
    database: CacheDatabase,
    incoming: List<DbPagingTimeline>,
    existingByPair: Map<Pair<String, String>, DbPagingTimelineVersion>,
): DbChanges<DbPagingTimeline> {
    val changed = incoming.filter { existingByPair[it.key]?.matches(it) != true }
    val changedExisting = changed.filter { it.key in existingByPair }
    val latestByPair = loadTimelineVersions(database, changedExisting)
    val revisionSafeChanges =
        changed.map { timeline ->
            val existing = existingByPair[timeline.key]
            val latestRevision =
                latestByPair[timeline.key]?.contentRevision
                    ?: existing?.contentRevision
                    ?: timeline.contentRevision
            timeline.copy(
                contentRevision =
                    if (existing == null) {
                        latestRevision
                    } else {
                        latestRevision + 1
                    },
            )
        }
    return DbChanges(
        inserted = revisionSafeChanges.filter { it.key !in existingByPair },
        updated = revisionSafeChanges.filter { it.key in existingByPair },
    )
}

private suspend fun loadTimelineVersions(
    database: CacheDatabase,
    timelines: Collection<DbPagingTimeline>,
): Map<Pair<String, String>, DbPagingTimelineVersion> =
    timelines
        .groupBy { it.pagingKey }
        .flatMap { (pagingKey, rows) ->
            rows
                .map { it.statusId }
                .distinct()
                .chunked(SQL_IN_BATCH_SIZE)
                .flatMap { chunk ->
                    database.pagingTimelineDao().getVersionsByPagingKeyAndStatusIds(
                        pagingKey = pagingKey,
                        statusIds = chunk,
                    )
                }
        }.associateBy { it.pagingKey to it.statusId }

private fun DbPagingTimelineVersion.matches(timeline: DbPagingTimeline): Boolean =
    pagingKey == timeline.pagingKey &&
        statusId == timeline.statusId &&
        sortId == timeline.sortId &&
        messageRenderHash == timeline.messageRenderHash &&
        semanticReferenceSignature == timeline.semanticReferenceSignature &&
        presentationReferenceSignature == timeline.presentationReferenceSignature &&
        _id == timeline._id

private fun UiTimelineV2.usersInContent(): List<UiProfile> =
    when (this) {
        is UiTimelineV2.Post -> {
            listOfNotNull(user)
        }

        is UiTimelineV2.TimelinePostItem -> {
            post.usersInContent() +
                listOfNotNull(presentation.message?.user) +
                presentation.inlineParents.flatMap { it.usersInContent() } +
                presentation.quotes.flatMap { it.usersInContent() } +
                listOfNotNull(presentation.repost).flatMap { it.usersInContent() }
        }

        is UiTimelineV2.User -> {
            listOfNotNull(value, message?.user)
        }

        is UiTimelineV2.UserList -> {
            users +
                listOfNotNull(message?.user) +
                listOfNotNull(post).flatMap { it.usersInContent() }
        }

        else -> {
            emptyList()
        }
    }
