package dev.dimension.flare.data.database.cache.mapper

import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.DbStatus
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2

internal suspend fun saveToDatabase(
    database: CacheDatabase,
    items: List<DbPagingTimelineWithStatus>,
) {
    val rootStatusIds = items.map { it.status.status.data.id }.distinct()
    val statuses =
        items.map { it.status.status.data } +
            items
                .flatMap { it.status.references }
                .mapNotNull { it.status?.data } +
            items
                .flatMap { it.presentationReferences }
                .mapNotNull { it.status?.data }
    val users = statuses.flatMap { it.content.usersInContent() }.distinctBy { it.key }
    database.upsertUsers(users.map { it.toDbUser() })
    val changedStatuses = loadChangedStatuses(database, statuses)
    if (changedStatuses.isNotEmpty()) {
        database.statusDao().insertAll(changedStatuses)
    }
    if (rootStatusIds.isNotEmpty()) {
        database.statusReferenceDao().delete(rootStatusIds)
    }
    items.flatMap { it.status.references }.map { it.reference }.let {
        database.statusReferenceDao().insertAll(it)
    }
    items
        .groupBy { it.timeline.pagingKey }
        .forEach { (pagingKey, rows) ->
            val statusIds = rows.map { it.timeline.statusId }.distinct()
            if (statusIds.isNotEmpty()) {
                database.pagingTimelineDao().deletePresentationReferences(
                    pagingKey = pagingKey,
                    statusIds = statusIds,
                )
            }
        }
    items.flatMap { it.presentationReferences }.map { it.reference }.let {
        database.pagingTimelineDao().insertPresentationReferences(it)
    }
    val changedTimeline = loadChangedTimeline(database, items.map { it.timeline })
    if (changedTimeline.isNotEmpty()) {
        database.pagingTimelineDao().insertAll(changedTimeline)
    }
}

private const val SQL_IN_BATCH_SIZE = 500

private suspend fun loadChangedStatuses(
    database: CacheDatabase,
    incoming: List<DbStatus>,
): List<DbStatus> {
    val existingByKey =
        incoming
            .groupBy { it.accountType }
            .flatMap { (accountType, accountStatuses) ->
                accountStatuses
                    .map { it.statusKey }
                    .distinct()
                    .chunked(SQL_IN_BATCH_SIZE)
                    .flatMap { chunk ->
                        database.statusDao().getByKeys(statusKeys = chunk, accountType = accountType)
                    }
            }.associateBy { it.id }
    return incoming.filter { status ->
        existingByKey[status.id] != status
    }
}

private suspend fun loadChangedTimeline(
    database: CacheDatabase,
    incoming: List<DbPagingTimeline>,
): List<DbPagingTimeline> {
    val existingByPair =
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
            }.associateBy { it.pagingKey to it.statusId }
    return incoming.filter { timeline ->
        existingByPair[timeline.pagingKey to timeline.statusId] != timeline
    }
}

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
