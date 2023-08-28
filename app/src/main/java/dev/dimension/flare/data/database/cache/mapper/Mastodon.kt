package dev.dimension.flare.data.database.cache.mapper

import androidx.room.withTransaction
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbEmoji
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.DbStatus
import dev.dimension.flare.data.database.cache.model.DbStatusReference
import dev.dimension.flare.data.database.cache.model.DbStatusReferenceWithStatus
import dev.dimension.flare.data.database.cache.model.DbStatusWithReference
import dev.dimension.flare.data.database.cache.model.DbStatusWithUser
import dev.dimension.flare.data.database.cache.model.DbUser
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.data.network.mastodon.api.model.Account
import dev.dimension.flare.data.network.mastodon.api.model.Emoji
import dev.dimension.flare.data.network.mastodon.api.model.Notification
import dev.dimension.flare.data.network.mastodon.api.model.Status
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.ReferenceType
import java.util.UUID

context(CacheDatabase, List<Status>)
suspend fun save(
    accountKey: MicroBlogKey,
    pagingKey: String,
    sortIdProvider: (Status) -> Long = { it.createdAt?.toEpochMilliseconds() ?: 0 },
) {
    val data = toDbPagingTimeline(accountKey, pagingKey, sortIdProvider)
    withTransaction {
        (
            data.mapNotNull { it.status.status.user } + data.flatMap { it.status.references }
                .mapNotNull { it.status.user }
            ).let {
            userDao().insertAll(it)
        }
        (
            data.map { it.status.status.data } + data.flatMap { it.status.references }
                .map { it.status.data }
            ).let {
            statusDao().insertAll(it)
        }
        data.flatMap { it.status.references }.map { it.reference }.let {
            statusReferenceDao().insertAll(it)
        }
        pagingTimelineDao().insertAll(data.map { it.timeline })
    }
}

context(CacheDatabase, List<Notification>)
suspend fun saveNotification(
    accountKey: MicroBlogKey,
    pagingKey: String,
) {
    val data = toDb(accountKey, pagingKey)
    withTransaction {
        (
            data.mapNotNull { it.status.status.user } + data.flatMap { it.status.references }
                .mapNotNull { it.status.user }
            ).let {
            userDao().insertAll(it)
        }
        (
            data.map { it.status.status.data } + data.flatMap { it.status.references }
                .map { it.status.data }
            ).let {
            statusDao().insertAll(it)
        }
        data.flatMap { it.status.references }.map { it.reference }.let {
            statusReferenceDao().insertAll(it)
        }
        pagingTimelineDao().insertAll(data.map { it.timeline })
    }
}

fun List<Notification>.toDb(
    accountKey: MicroBlogKey,
    pagingKey: String,
): List<DbPagingTimelineWithStatus> {
    return this.map {
        it.toDbPagingTimeline(accountKey, pagingKey)
    }
}

private fun Notification.toDbPagingTimeline(
    accountKey: MicroBlogKey,
    pagingKey: String,
): DbPagingTimelineWithStatus {
    val status = this.toDbStatusWithReference(accountKey)
    val user = this.account?.toDbUser(accountKey.host) ?: throw IllegalStateException("account is null")
    val sortId = this.createdAt?.toEpochMilliseconds() ?: 0
    return DbPagingTimelineWithStatus(
        timeline = DbPagingTimeline(
            _id = UUID.randomUUID().toString(),
            accountKey = accountKey,
            statusKey = MicroBlogKey(
                this.id ?: throw IllegalStateException("id is null"),
                user.userKey.host,
            ),
            pagingKey = pagingKey,
            sortId = sortId,
        ),
        status = status,
    )
}

private fun Notification.toDbStatusWithReference(
    accountKey: MicroBlogKey,
): DbStatusWithReference {
    val status = this.toDbStatusWithUser(accountKey)
    val retweet = this.status?.toDbStatusWithUser(accountKey)
    return DbStatusWithReference(
        status = status,
        references = listOfNotNull(
            retweet?.toDbStatusReference(status.data.statusKey, ReferenceType.Notification),
        ),
    )
}

private fun Notification.toDbStatusWithUser(
    accountKey: MicroBlogKey,
): DbStatusWithUser {
    val user = this.account?.toDbUser(accountKey.host) ?: throw IllegalStateException("account is null")
    val status = this.toDbStatus(accountKey)
    return DbStatusWithUser(
        data = status,
        user = user,
    )
}

private fun Notification.toDbStatus(
    accountKey: MicroBlogKey,
): DbStatus {
    val user = this.account?.toDbUser(accountKey.host) ?: throw IllegalStateException("account is null")
    return DbStatus(
        statusKey = MicroBlogKey(
            this.id ?: throw IllegalStateException("id is null"),
            user.userKey.host,
        ),
        platformType = PlatformType.Mastodon,
        userKey = user.userKey,
        content = StatusContent.MastodonNotification(this),
        accountKey = accountKey,
    )
}

fun List<Status>.toDbPagingTimeline(
    accountKey: MicroBlogKey,
    pagingKey: String,
    sortIdProvider: (Status) -> Long = { it.createdAt?.toEpochMilliseconds() ?: 0 },
): List<DbPagingTimelineWithStatus> {
    return this.map {
        it.toDbPagingTimeline(accountKey, pagingKey, sortIdProvider)
    }
}

fun Status.toDbPagingTimeline(
    accountKey: MicroBlogKey,
    pagingKey: String,
    sortIdProvider: (Status) -> Long = { it.createdAt?.toEpochMilliseconds() ?: 0 },
): DbPagingTimelineWithStatus {
    val status = this.toDbStatusWithReference(accountKey)
    val sortId = sortIdProvider(this)
    return DbPagingTimelineWithStatus(
        timeline = DbPagingTimeline(
            _id = UUID.randomUUID().toString(),
            accountKey = accountKey,
            statusKey = status.status.data.statusKey,
            pagingKey = pagingKey,
            sortId = sortId,
        ),
        status = status,
    )
}

fun Status.toDbStatusWithReference(
    accountKey: MicroBlogKey,
): DbStatusWithReference {
    val status = this.toDbStatusWithUser(accountKey)
    val retweet = this.reblog?.toDbStatusWithUser(accountKey)
    return DbStatusWithReference(
        status = status,
        references = listOfNotNull(
            retweet?.toDbStatusReference(status.data.statusKey, ReferenceType.Retweet),
        ),
    )
}

fun DbStatusWithUser.toDbStatusReference(
    statusKey: MicroBlogKey,
    referenceType: ReferenceType,
): DbStatusReferenceWithStatus {
    return DbStatusReferenceWithStatus(
        reference = DbStatusReference(
            _id = UUID.randomUUID().toString(),
            referenceType = referenceType,
            statusKey = statusKey,
            referenceStatusKey = data.statusKey,
        ),
        status = this,
    )
}

private fun Status.toDbStatusWithUser(
    accountKey: MicroBlogKey,
): DbStatusWithUser {
    val user = account?.toDbUser(accountKey.host)
        ?: throw IllegalArgumentException("mastodon Status.user should not be null")
    val status = DbStatus(
        statusKey = MicroBlogKey(
            id ?: throw IllegalArgumentException("mastodon Status.idStr should not be null"),
            host = user.userKey.host,
        ),
        platformType = PlatformType.Mastodon,
        content = dev.dimension.flare.data.database.cache.model.StatusContent.Mastodon(this),
        userKey = user.userKey,
        accountKey = accountKey,
    )
    return DbStatusWithUser(
        data = status,
        user = user,
    )
}

fun Account.toDbUser(
    host: String,
): DbUser {
    val remoteHost = if (acct != null && acct.contains('@')) {
        acct.substring(acct.indexOf('@') + 1)
    } else {
        host
    }
    return DbUser(
        userKey = MicroBlogKey(
            id = id ?: throw IllegalArgumentException("mastodon Account.id should not be null"),
            host = host,
        ),
        platformType = PlatformType.Mastodon,
        name = displayName
            ?: throw IllegalArgumentException("mastodon Account.displayName should not be null"),
        handle = username
            ?: throw IllegalArgumentException("mastodon Account.username should not be null"),
        content = dev.dimension.flare.data.database.cache.model.UserContent.Mastodon(this),
        host = remoteHost,
    )
}

fun List<Emoji>.toDb(
    host: String,
): DbEmoji {
    return DbEmoji(
        host = host,
        content = dev.dimension.flare.data.database.cache.model.EmojiContent.Mastodon(this),
    )
}
