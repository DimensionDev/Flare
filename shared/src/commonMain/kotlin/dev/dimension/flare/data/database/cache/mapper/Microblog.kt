package dev.dimension.flare.data.database.cache.mapper

import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.DbStatusReference
import dev.dimension.flare.data.database.cache.model.DbStatusReferenceWithStatus
import dev.dimension.flare.data.database.cache.model.DbStatusWithReference
import dev.dimension.flare.data.database.cache.model.DbStatusWithUser
import dev.dimension.flare.data.database.cache.model.UserContent
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.ReferenceType
import kotlinx.coroutines.flow.firstOrNull
import kotlin.uuid.Uuid

internal suspend fun saveToDatabase(
    database: CacheDatabase,
    items: List<DbPagingTimelineWithStatus>,
) {
    (
        items.mapNotNull { it.status.status.user } +
            items
                .flatMap { it.status.references }
                .mapNotNull { it.status.user }
    ).let { allUsers ->
        val exsitingUsers =
            database
                .userDao()
                .findByKeys(allUsers.map { it.userKey })
                .firstOrNull()
                .orEmpty()
                .map {
                    when (val content = it.content) {
                        is UserContent.Bluesky -> {
                            val user =
                                allUsers.find { user ->
                                    user.userKey == it.userKey
                                }
                            if (user != null && user.content is UserContent.BlueskyLite) {
                                it.copy(
                                    content =
                                        content.copy(
                                            data =
                                                content.data.copy(
                                                    handle = user.content.data.handle,
                                                    displayName = user.content.data.displayName,
                                                    avatar = user.content.data.avatar,
                                                ),
                                        ),
                                )
                            } else {
                                it
                            }
                        }
                        is UserContent.Misskey -> {
                            val user =
                                allUsers.find { user ->
                                    user.userKey == it.userKey
                                }
                            if (user != null && user.content is UserContent.MisskeyLite) {
                                it.copy(
                                    content =
                                        content.copy(
                                            data =
                                                content.data.copy(
                                                    name = user.content.data.name,
                                                    username = user.content.data.username,
                                                    avatarUrl = user.content.data.avatarUrl,
                                                ),
                                        ),
                                )
                            } else {
                                it
                            }
                        }
                        else -> it
                    }
                }

        val result = (exsitingUsers + allUsers).distinctBy { it.userKey }
        database.userDao().insertAll(result)
    }
    (
        items.map { it.status.status.data } +
            items
                .flatMap { it.status.references }
                .map { it.status.data }
    ).let {
        database.statusDao().insertAll(it)
    }
    items.flatMap { it.status.references }.map { it.reference }.let {
        database.statusReferenceDao().delete(it.map { it.statusKey })
        database.statusReferenceDao().insertAll(it)
    }
    database.pagingTimelineDao().insertAll(items.map { it.timeline })
}

internal fun createDbPagingTimelineWithStatus(
    accountType: AccountType,
    pagingKey: String,
    sortId: Long,
    status: DbStatusWithUser,
    references: Map<ReferenceType, DbStatusWithUser>,
): DbPagingTimelineWithStatus {
    val timeline =
        DbPagingTimeline(
            _id = Uuid.random().toString(),
            accountType = accountType,
            statusKey = status.data.statusKey,
            pagingKey = pagingKey,
            sortId = sortId,
        )
    return DbPagingTimelineWithStatus(
        timeline = timeline,
        status =
            DbStatusWithReference(
                status = status,
                references =
                    references.map { (type, reference) ->
                        reference.toDbStatusReference(status.data.statusKey, type)
                    },
            ),
    )
}

internal fun createDbPagingTimelineWithStatus(
    accountKey: MicroBlogKey,
    pagingKey: String,
    sortId: Long,
    status: DbStatusWithUser,
    references: Map<ReferenceType, DbStatusWithUser>,
): DbPagingTimelineWithStatus =
    createDbPagingTimelineWithStatus(
        accountType = AccountType.Specific(accountKey),
        pagingKey = pagingKey,
        sortId = sortId,
        status = status,
        references = references,
    )

private fun DbStatusWithUser.toDbStatusReference(
    statusKey: MicroBlogKey,
    referenceType: ReferenceType,
): DbStatusReferenceWithStatus =
    DbStatusReferenceWithStatus(
        reference =
            DbStatusReference(
                _id = Uuid.random().toString(),
                referenceType = referenceType,
                statusKey = statusKey,
                referenceStatusKey = data.statusKey,
            ),
        status = this,
    )
