package dev.dimension.flare.data.database.cache.mapper

import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.DbStatus
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.flow.firstOrNull

internal suspend fun saveToDatabase(
    database: CacheDatabase,
    items: List<DbPagingTimelineWithStatus>,
) {
    // TODO: check existing users and update them if needed
    val users =
        items
            .flatMap {
                listOfNotNull(it.status.status) + it.status.references.mapNotNull { it.status }
            }.flatMap { it.references }
            .mapNotNull { it.user }
            .distinctBy { it.userKey }
    val userReferences =
        items
            .flatMap {
                listOfNotNull(it.status.status) + it.status.references.mapNotNull { it.status }
            }.flatMap { post ->
                post.references.map { it.reference }
            }
    database.upsertUsers(users)
    database.userDao().insertAllReferences(userReferences)

//    (
//            items.flatMap { it.status.status.references.mapNotNull { it.user } } +
//                    items
//                        .flatMap { it.status.references }
//                        .flatMap { it.status?.references.orEmpty() }
//                        .mapNotNull { it.user }
//            ).let { allUsers ->
//            val exsitingUsers =
//                database
//                    .userDao()
//                    .findByKeys(allUsers.map { it.userKey })
//                    .firstOrNull()
//                    .orEmpty()
// //                .map {
// //                    when (val content = it.content) {
// //                        is UserContent.Bluesky -> {
// //                            val user =
// //                                allUsers.find { user ->
// //                                    user.userKey == it.userKey
// //                                }
// //                            if (user != null && user.content is UserContent.BlueskyLite) {
// //                                it.copy(
// //                                    content =
// //                                        content.copy(
// //                                            data =
// //                                                content.data.copy(
// //                                                    handle = user.content.data.handle,
// //                                                    displayName = user.content.data.displayName,
// //                                                    avatar = user.content.data.avatar,
// //                                                ),
// //                                        ),
// //                                )
// //                            } else {
// //                                it
// //                            }
// //                        }
// //                        is UserContent.Misskey -> {
// //                            val user =
// //                                allUsers.find { user ->
// //                                    user.userKey == it.userKey
// //                                }
// //                            if (user != null && user.content is UserContent.MisskeyLite) {
// //                                it.copy(
// //                                    content =
// //                                        content.copy(
// //                                            data =
// //                                                content.data.copy(
// //                                                    name = user.content.data.name,
// //                                                    username = user.content.data.username,
// //                                                    avatarUrl = user.content.data.avatarUrl,
// //                                                ),
// //                                        ),
// //                                )
// //                            } else {
// //                                it
// //                            }
// //                        }
// //                        else -> it
// //                    }
// //                }
//
//            val result = (exsitingUsers + allUsers).distinctBy { it.userKey }
//            database.userDao().insertAll(result)
//            database.userDao().insertAll(result.map {
//                DbStatusUserReference(
//                    _id = "${it.userKey}_${it.name}",
//                    statusKey = it.userKey,
//                    referenceUserKey = it.userKey,
//                )
//            })
//        }
    val statuses =
        items.map { it.status.status.data } +
            items
                .flatMap { it.status.references }
                .mapNotNull { it.status?.data }
    val mergedStatuses = statuses.map { mergeWithExistingPostParents(database, it) }
    database.statusDao().insertAll(mergedStatuses)
    items.flatMap { it.status.references }.map { it.reference }.let {
        // TODO: delete old references
        database.statusReferenceDao().insertAll(it)
    }
    database.pagingTimelineDao().insertAll(items.map { it.timeline })
}

private suspend fun mergeWithExistingPostParents(
    database: CacheDatabase,
    incoming: DbStatus,
): DbStatus {
    val incomingPost = incoming.content as? UiTimelineV2.Post ?: return incoming
    if (incomingPost.parents.isNotEmpty()) {
        return incoming
    }
    val existingPost =
        database
            .statusDao()
            .get(incoming.statusKey, incoming.accountType)
            .firstOrNull()
            ?.content as? UiTimelineV2.Post
    return if (existingPost?.parents?.isNotEmpty() == true) {
        incoming.copy(content = incomingPost.copy(parents = existingPost.parents))
    } else {
        incoming
    }
}

// internal fun createDbPagingTimelineWithStatus(
//    accountType: DbAccountType,
//    pagingKey: String,
//    sortId: Long,
//    status: DbStatusWithUser,
//    references: Map<ReferenceType, List<DbStatusWithUser>>,
// ): DbPagingTimelineWithStatus {
//    val timeline =
//        DbPagingTimeline(
//            accountType = accountType,
//            statusKey = status.data.statusKey,
//            pagingKey = pagingKey,
//            sortId = sortId,
//        )
//    return DbPagingTimelineWithStatus(
//        timeline = timeline,
//        status =
//            DbStatusWithReference(
//                status = status,
//                references =
//                    references.flatMap { (type, reference) ->
//                        reference.map {
//                            it.toDbStatusReference(status.data.statusKey, type)
//                        }
//                    },
//            ),
//    )
// }
//
// internal fun createDbPagingTimelineWithStatus(
//    accountKey: MicroBlogKey,
//    pagingKey: String,
//    sortId: Long,
//    status: DbStatusWithUser,
//    references: Map<ReferenceType, List<DbStatusWithUser>>,
// ): DbPagingTimelineWithStatus =
//    createDbPagingTimelineWithStatus(
//        accountType = AccountType.Specific(accountKey),
//        pagingKey = pagingKey,
//        sortId = sortId,
//        status = status,
//        references = references,
//    )
//
// private fun DbStatusWithUser.toDbStatusReference(
//    statusKey: MicroBlogKey,
//    referenceType: ReferenceType,
// ): DbStatusReferenceWithStatus =
//    DbStatusReferenceWithStatus(
//        reference =
//            DbStatusReference(
//                _id = Uuid.random().toString(),
//                referenceType = referenceType,
//                statusKey = statusKey,
//                referenceStatusKey = data.statusKey,
//            ),
//        status = this,
//    )
