package dev.dimension.flare.data.database.cache.mapper

import androidx.room.withTransaction
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbEmoji
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.DbStatus
import dev.dimension.flare.data.database.cache.model.DbStatusWithReference
import dev.dimension.flare.data.database.cache.model.DbStatusWithUser
import dev.dimension.flare.data.database.cache.model.DbUser
import dev.dimension.flare.data.database.cache.model.EmojiContent
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.data.database.cache.model.UserContent
import dev.dimension.flare.data.network.misskey.api.model.EmojiSimple
import dev.dimension.flare.data.network.misskey.api.model.Note
import dev.dimension.flare.data.network.misskey.api.model.Notification
import dev.dimension.flare.data.network.misskey.api.model.User
import dev.dimension.flare.data.network.misskey.api.model.UserLite
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.ReferenceType
import kotlinx.datetime.toInstant
import java.util.UUID

context(CacheDatabase, List<Note>)
suspend fun save(
    accountKey: MicroBlogKey,
    pagingKey: String,
    sortIdProvider: (Note) -> Long = { it.createdAt.toInstant().toEpochMilliseconds() }
) {
    val data = toDbPagingTimeline(accountKey, pagingKey, sortIdProvider)
    with(data) {
        saveDbPagingTimelineWithStatus()
    }
}

context(CacheDatabase, List<Notification>)
suspend fun saveNotification(
    accountKey: MicroBlogKey,
    pagingKey: String
) {
    val data = toDb(accountKey, pagingKey)
    with(data) {
        saveDbPagingTimelineWithStatus()
    }
}

context(CacheDatabase, List<DbPagingTimelineWithStatus>)
suspend fun saveDbPagingTimelineWithStatus() {
    withTransaction {
        (
            mapNotNull { it.status.status.user } + flatMap { it.status.references }
                .mapNotNull { it.status.user }
            ).let { allUsers ->
            val exsitingUsers = userDao()
                .findByKeys(allUsers.map { it.userKey })
                .filter {
                    it.content is UserContent.Misskey
                }.map {
                    val content = it.content as UserContent.Misskey
                    val user = allUsers.find { user ->
                        user.userKey == it.userKey
                    }

                    if (user != null && user.content is UserContent.MisskeyLite) {
                        it.copy(
                            content = content.copy(
                                data = content.data.copy(
                                    name = user.content.data.name,
                                    username = user.content.data.username,
                                    avatarUrl = user.content.data.avatarUrl
                                )
                            )
                        )
                    } else {
                        it
                    }
                }

            val result = (exsitingUsers + allUsers).distinctBy { it.userKey }
            userDao().insertAll(result)
        }
        (
            map { it.status.status.data } + flatMap { it.status.references }
                .map { it.status.data }
            ).let {
            statusDao().insertAll(it)
        }
        flatMap { it.status.references }.map { it.reference }.let {
            statusReferenceDao().insertAll(it)
        }
        pagingTimelineDao().insertAll(map { it.timeline })
    }
}

fun List<Notification>.toDb(
    accountKey: MicroBlogKey,
    pagingKey: String
): List<DbPagingTimelineWithStatus> {
    return this.map {
        it.toDbPagingTimeline(accountKey, pagingKey)
    }
}

private fun Notification.toDbPagingTimeline(
    accountKey: MicroBlogKey,
    pagingKey: String
): DbPagingTimelineWithStatus {
    val status = this.toDbStatusWithReference(accountKey)
    val sortId = this.createdAt.toInstant().toEpochMilliseconds()
    return DbPagingTimelineWithStatus(
        timeline = DbPagingTimeline(
            _id = UUID.randomUUID().toString(),
            accountKey = accountKey,
            statusKey = MicroBlogKey(
                this.id,
                accountKey.host
            ),
            pagingKey = pagingKey,
            sortId = sortId
        ),
        status = status
    )
}

private fun Notification.toDbStatusWithReference(
    accountKey: MicroBlogKey
): DbStatusWithReference {
    val status = this.toDbStatusWithUser(accountKey)
    val retweet = this.note?.toDbStatusWithUser(accountKey)
    return DbStatusWithReference(
        status = status,
        references = listOfNotNull(
            retweet?.toDbStatusReference(status.data.statusKey, ReferenceType.MisskeyNotification)
        )
    )
}

private fun Notification.toDbStatusWithUser(
    accountKey: MicroBlogKey
): DbStatusWithUser {
    val user = this.user?.toDbUser(accountKey.host)
    val status = this.toDbStatus(accountKey)
    return DbStatusWithUser(
        data = status,
        user = user
    )
}

private fun Notification.toDbStatus(
    accountKey: MicroBlogKey
): DbStatus {
    val user = this.user?.toDbUser(accountKey.host)
    return DbStatus(
        statusKey = MicroBlogKey(
            this.id,
            accountKey.host
        ),
        platformType = PlatformType.Misskey,
        userKey = user?.userKey,
        content = StatusContent.MisskeyNotification(this),
        accountKey = accountKey
    )
}

private fun List<Note>.toDbPagingTimeline(
    accountKey: MicroBlogKey,
    pagingKey: String,
    sortIdProvider: (Note) -> Long = { it.createdAt.toInstant().toEpochMilliseconds() }
): List<DbPagingTimelineWithStatus> {
    return this.map {
        it.toDbPagingTimeline(accountKey, pagingKey, sortIdProvider)
    }
}

private fun Note.toDbPagingTimeline(
    accountKey: MicroBlogKey,
    pagingKey: String,
    sortIdProvider: (Note) -> Long = { it.createdAt.toInstant().toEpochMilliseconds() }
): DbPagingTimelineWithStatus {
    val status = this.toDbStatusWithReference(accountKey)
    val sortId = sortIdProvider(this)
    return DbPagingTimelineWithStatus(
        timeline = DbPagingTimeline(
            _id = UUID.randomUUID().toString(),
            accountKey = accountKey,
            statusKey = status.status.data.statusKey,
            pagingKey = pagingKey,
            sortId = sortId
        ),
        status = status
    )
}

private fun Note.toDbStatusWithReference(
    accountKey: MicroBlogKey
): DbStatusWithReference {
    val status = this.toDbStatusWithUser(accountKey)
    val retweet = this.renote?.toDbStatusWithUser(accountKey)
    val reply = this.reply?.toDbStatusWithUser(accountKey)
    return DbStatusWithReference(
        status = status,
        references = listOfNotNull(
            retweet?.toDbStatusReference(status.data.statusKey, ReferenceType.Retweet),
            reply?.toDbStatusReference(status.data.statusKey, ReferenceType.Reply)
        )
    )
}

private fun Note.toDbStatusWithUser(
    accountKey: MicroBlogKey
): DbStatusWithUser {
    val user = user.toDbUser(accountKey.host)
    val status = DbStatus(
        statusKey = MicroBlogKey(
            id = id,
            host = user.userKey.host
        ),
        platformType = PlatformType.Misskey,
        content = StatusContent.Misskey(this),
        userKey = user.userKey,
        accountKey = accountKey
    )
    return DbStatusWithUser(
        data = status,
        user = user
    )
}

private fun UserLite.toDbUser(
    accountHost: String
) = DbUser(
    userKey = MicroBlogKey(
        id = id,
        host = accountHost
    ),
    platformType = dev.dimension.flare.model.PlatformType.Misskey,
    name = name ?: "",
    handle = username,
    content = UserContent.MisskeyLite(this),
    host = if (host.isNullOrEmpty()) {
        accountHost
    } else {
        host
    }
)

fun User.toDbUser(
    accountHost: String
) = DbUser(
    userKey = MicroBlogKey(
        id = id,
        host = accountHost
    ),
    platformType = dev.dimension.flare.model.PlatformType.Misskey,
    name = name ?: "",
    handle = username,
    content = UserContent.Misskey(this),
    host = if (host.isNullOrEmpty()) {
        accountHost
    } else {
        host
    }
)

fun List<EmojiSimple>.toDb(
    host: String
): DbEmoji {
    return DbEmoji(
        host = host,
        content = EmojiContent.Misskey(this)
    )
}
