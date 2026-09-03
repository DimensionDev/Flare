package dev.dimension.flare.data.database.cache

import androidx.room3.withReadTransaction
import dev.dimension.flare.data.database.cache.dao.DbStatusFingerprint
import dev.dimension.flare.data.database.cache.dao.TimelinePageDao
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.DbStatus
import dev.dimension.flare.data.database.cache.model.DbStatusReferenceWithStatus
import dev.dimension.flare.data.database.cache.model.DbStatusWithReference
import dev.dimension.flare.data.database.cache.model.DbStatusWithUser
import dev.dimension.flare.data.database.cache.model.DbTimelineItemPresentationReferenceWithStatus
import dev.dimension.flare.data.database.cache.model.TranslationEntityType

private const val SQL_IN_BATCH_SIZE = 500

internal suspend fun CacheDatabase.loadTimelinePage(
    pagingKey: String,
    offset: Int,
    limit: Int,
): List<DbPagingTimelineWithStatus> =
    withReadTransaction {
        val dao = timelinePageDao()
        dao
            .getTimelineRows(
                pagingKey = pagingKey,
                offset = offset,
                limit = limit,
            ).hydrate(dao = dao, pagingKey = pagingKey)
    }

internal suspend fun CacheDatabase.loadTimelineItems(
    pagingKey: String,
    statusIds: List<String>,
    reusableStatuses: Map<String, DbStatus> = emptyMap(),
): List<DbPagingTimelineWithStatus> {
    if (statusIds.isEmpty()) {
        return emptyList()
    }
    val distinctStatusIds = statusIds.distinct()
    return withReadTransaction {
        val dao = timelinePageDao()
        val rows =
            distinctStatusIds
                .chunked(SQL_IN_BATCH_SIZE)
                .flatMap { dao.getTimelineRowsByStatusIds(pagingKey = pagingKey, statusIds = it) }
        val hydratedById =
            rows
                .hydrate(
                    dao = dao,
                    pagingKey = pagingKey,
                    reusableStatuses = reusableStatuses,
                ).associateBy { it.timeline.statusId }
        distinctStatusIds.mapNotNull(hydratedById::get)
    }
}

private suspend fun List<DbPagingTimeline>.hydrate(
    dao: TimelinePageDao,
    pagingKey: String,
    reusableStatuses: Map<String, DbStatus> = emptyMap(),
): List<DbPagingTimelineWithStatus> {
    if (isEmpty()) {
        return emptyList()
    }
    val rootStatusIds = map { it.statusId }.distinct()
    val statusReferences =
        rootStatusIds
            .chunked(SQL_IN_BATCH_SIZE)
            .flatMap { dao.getStatusReferences(it) }
    val presentationReferences =
        rootStatusIds
            .chunked(SQL_IN_BATCH_SIZE)
            .flatMap { dao.getPresentationReferences(pagingKey = pagingKey, statusIds = it) }
    val allStatusIds =
        buildSet {
            addAll(rootStatusIds)
            statusReferences.forEach { add(it.referenceStatusId) }
            presentationReferences.forEach { add(it.referenceStatusId) }
        }.toList()
    val reusableIds = allStatusIds.filter { it in reusableStatuses }
    val reusableFingerprints =
        reusableIds
            .chunked(SQL_IN_BATCH_SIZE)
            .flatMap { dao.getStatusFingerprints(it) }
            .associateBy { it.id }
    val reusedStatuses =
        reusableIds
            .mapNotNull { statusId ->
                val status = reusableStatuses[statusId] ?: return@mapNotNull null
                val fingerprint = reusableFingerprints[statusId] ?: return@mapNotNull null
                status.takeIf { it.hasSameStoredContentAs(fingerprint) }
            }.associateBy { it.id }
    val fetchedStatuses =
        allStatusIds
            .filterNot { it in reusedStatuses }
            .chunked(SQL_IN_BATCH_SIZE)
            .flatMap { dao.getStatuses(it) }
            .associateBy { it.id }
    val translations =
        allStatusIds
            .chunked(SQL_IN_BATCH_SIZE)
            .flatMap {
                dao.getTranslations(
                    entityType = TranslationEntityType.Status,
                    entityKeys = it,
                )
            }.groupBy { it.entityKey }
    val statusesById =
        allStatusIds
            .mapNotNull { statusId ->
                val status = reusedStatuses[statusId] ?: fetchedStatuses[statusId] ?: return@mapNotNull null
                statusId to
                    DbStatusWithUser(
                        data = status,
                        translations = translations[status.id].orEmpty(),
                    )
            }.toMap()
    val referencesByStatusId =
        statusReferences
            .map { reference ->
                DbStatusReferenceWithStatus(
                    reference = reference,
                    status = statusesById[reference.referenceStatusId],
                )
            }.groupBy { it.reference.statusId }
    val presentationReferencesByStatusId =
        presentationReferences
            .map { reference ->
                DbTimelineItemPresentationReferenceWithStatus(
                    reference = reference,
                    status = statusesById[reference.referenceStatusId],
                )
            }.groupBy { it.reference.statusId }

    return mapNotNull { timeline ->
        val status = statusesById[timeline.statusId] ?: return@mapNotNull null
        DbPagingTimelineWithStatus(
            timeline = timeline,
            status =
                DbStatusWithReference(
                    status = status,
                    references = referencesByStatusId[timeline.statusId].orEmpty(),
                ),
            presentationReferences = presentationReferencesByStatusId[timeline.statusId].orEmpty(),
        )
    }
}

private fun DbStatus.hasSameStoredContentAs(fingerprint: DbStatusFingerprint): Boolean =
    id == fingerprint.id &&
        contentHash == fingerprint.contentHash &&
        renderHash == fingerprint.renderHash
