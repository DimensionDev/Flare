package dev.dimension.flare.data.database.cache.mapper

import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.DbStatus
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

internal suspend fun saveToDatabase(
    database: CacheDatabase,
    items: List<DbPagingTimelineWithStatus>,
) {
    val rootStatusKeys = items.map { it.status.status.data.statusKey }.distinct()
    val statuses =
        items.map { it.status.status.data } +
            items
                .flatMap { it.status.references }
                .mapNotNull { it.status?.data }
    val users = statuses.flatMap { it.content.usersInContent() }.distinctBy { it.key }
    database.upsertUsers(users.map { it.toDbUser() })
    val mergedStatuses = mergeWithExistingPostParents(database, statuses)
    val changedStatuses = loadChangedStatuses(database, mergedStatuses)
    if (changedStatuses.isNotEmpty()) {
        database.statusDao().insertAll(changedStatuses)
    }
    if (rootStatusKeys.isNotEmpty()) {
        database.statusReferenceDao().delete(rootStatusKeys)
    }
    items.flatMap { it.status.references }.map { it.reference }.let {
        database.statusReferenceDao().insertAll(it)
    }
    val changedTimeline = loadChangedTimeline(database, items.map { it.timeline })
    if (changedTimeline.isNotEmpty()) {
        database.pagingTimelineDao().insertAll(changedTimeline)
    }
}

private suspend fun mergeWithExistingPostParents(
    database: CacheDatabase,
    incoming: List<DbStatus>,
): List<DbStatus> {
    if (incoming.isEmpty()) {
        return incoming
    }

    val candidatesByAccount =
        incoming
            .asSequence()
            .mapNotNull { item ->
                val post = item.content as? UiTimelineV2.Post ?: return@mapNotNull null
                if (post.parents.isNotEmpty() || post.references.any { it.type == ReferenceType.Reply }) {
                    return@mapNotNull null
                }
                item.accountType to item.statusKey
            }.groupBy(
                keySelector = { it.first },
                valueTransform = { it.second },
            )
    if (candidatesByAccount.isEmpty()) {
        return incoming
    }

    val existingReplyReferencesByStatus = loadExistingPostReplyReferences(database, candidatesByAccount)
    return incoming.map { item ->
        val post = item.content as? UiTimelineV2.Post ?: return@map item
        if (post.parents.isNotEmpty() || post.references.any { it.type == ReferenceType.Reply }) {
            return@map item
        }
        val existingReplyReferences = existingReplyReferencesByStatus[item.accountType to item.statusKey] ?: return@map item
        item.copy(
            content =
                post.copy(
                    references =
                        (
                            post.references +
                                existingReplyReferences
                        ).distinctBy { it.type to it.statusKey }
                            .toImmutableList(),
                ),
        )
    }
}

private suspend fun loadExistingPostReplyReferences(
    database: CacheDatabase,
    candidatesByAccount: Map<DbAccountType, List<MicroBlogKey>>,
): Map<Pair<DbAccountType, MicroBlogKey>, ImmutableList<UiTimelineV2.Post.Reference>> {
    val result =
        mutableMapOf<
            Pair<DbAccountType, MicroBlogKey>,
            ImmutableList<UiTimelineV2.Post.Reference>,
        >()
    candidatesByAccount.forEach { (accountType, keys) ->
        keys.distinct().chunked(SQL_IN_BATCH_SIZE).forEach { chunk ->
            database.statusDao().getByKeys(statusKeys = chunk, accountType = accountType).forEach { existing ->
                val existingPost = existing.content as? UiTimelineV2.Post ?: return@forEach
                val replyReferences =
                    (
                        existingPost.references.filter { it.type == ReferenceType.Reply } +
                            existingPost.parents.map {
                                UiTimelineV2.Post.Reference(
                                    statusKey = it.statusKey,
                                    type = ReferenceType.Reply,
                                )
                            }
                    ).distinctBy { it.type to it.statusKey }
                        .toImmutableList()
                if (replyReferences.isEmpty()) {
                    return@forEach
                }
                result[accountType to existing.statusKey] = replyReferences
            }
        }
    }
    return result
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
                    .map { it.statusKey }
                    .distinct()
                    .chunked(SQL_IN_BATCH_SIZE)
                    .flatMap { chunk ->
                        database.pagingTimelineDao().getByPagingKeyAndStatusKeys(
                            pagingKey = pagingKey,
                            statusKeys = chunk,
                        )
                    }
            }.associateBy { it.pagingKey to it.statusKey }
    return incoming.filter { timeline ->
        existingByPair[timeline.pagingKey to timeline.statusKey] != timeline
    }
}

private fun UiTimelineV2.usersInContent(): List<UiProfile> =
    when (this) {
        is UiTimelineV2.Post -> listOfNotNull(user)
        is UiTimelineV2.User -> listOf(value)
        is UiTimelineV2.UserList -> users
        else -> emptyList()
    }
