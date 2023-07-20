package dev.dimension.flare.data.database.cache.mapper

import androidx.room.withTransaction
import dev.dimension.flare.data.database.cache.CacheDatabase
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
import dev.dimension.flare.data.network.mastodon.api.model.Notification
import dev.dimension.flare.data.network.mastodon.api.model.Status
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.ReferenceType
import io.ktor.http.Url
import java.util.UUID

context(CacheDatabase, List<Status>)
suspend fun save(
    accountKey: MicroBlogKey,
    pagingKey: String,
) {
    val data = toDbPagingTimeline(accountKey, pagingKey)
    withTransaction {
        (data.map { it.status.status.user } + data.flatMap { it.status.references }
            .map { it.status.user }).let {
            userDao().insertAll(it)
        }
        (data.map { it.status.status.data } + data.flatMap { it.status.references }
            .map { it.status.data }).let {
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
        (data.map { it.status.status.user } + data.flatMap { it.status.references }
            .map { it.status.user }).let {
            userDao().insertAll(it)
        }
        (data.map { it.status.status.data } + data.flatMap { it.status.references }
            .map { it.status.data }).let {
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
    pagingKey: String
): DbPagingTimelineWithStatus {
    val status = this.toDbStatusWithReference()
    val user = this.account?.toDbUser() ?: throw IllegalStateException("account is null")
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
            sortId = sortId
        ),
        status = status,
    )
}

private fun Notification.toDbStatusWithReference(): DbStatusWithReference {
    val status = this.toDbStatusWithUser()
    val retweet = this.status?.toDbStatusWithUser()
    return DbStatusWithReference(
        status = status,
        references = listOfNotNull(
            retweet?.toDbStatusReference(status.data.statusKey, ReferenceType.MastodonNotification),
        ),
    )
}

private fun Notification.toDbStatusWithUser(): DbStatusWithUser {
    val user = this.account?.toDbUser() ?: throw IllegalStateException("account is null")
    val status = this.toDbStatus()
    return DbStatusWithUser(
        data = status,
        user = user,
    )
}

private fun Notification.toDbStatus(): DbStatus {
    val user = this.account?.toDbUser() ?: throw IllegalStateException("account is null")
    return DbStatus(
        statusKey = MicroBlogKey(
            this.id ?: throw IllegalStateException("id is null"),
            user.userKey.host,
        ),
        platformType = PlatformType.Mastodon,
        userKey = user.userKey,
        content = StatusContent.MastodonNotification(this),
    )
}

fun List<Status>.toDbPagingTimeline(
    accountKey: MicroBlogKey,
    pagingKey: String,
): List<DbPagingTimelineWithStatus> {
    return this.map {
        it.toDbPagingTimeline(accountKey, pagingKey)
    }
}

fun Status.toDbPagingTimeline(
    accountKey: MicroBlogKey,
    pagingKey: String,
): DbPagingTimelineWithStatus {
    val status = this.toDbStatusWithReference()
    val sortId = this.createdAt?.toEpochMilliseconds() ?: 0
    return DbPagingTimelineWithStatus(
        timeline = DbPagingTimeline(
            _id = UUID.randomUUID().toString(),
            accountKey = accountKey,
            statusKey = status.status.data.statusKey,
            pagingKey = pagingKey,
            sortId = sortId
        ),
        status = status,
    )
}

fun Status.toDbStatusWithReference(): DbStatusWithReference {
    val status = this.toDbStatusWithUser()
    val retweet = this.reblog?.toDbStatusWithUser()
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
            referenceStatusKey = data.statusKey
        ),
        status = this,
    )
}

private fun Status.toDbStatusWithUser(): DbStatusWithUser {
    val user = account?.toDbUser()
        ?: throw IllegalArgumentException("mastodon Status.user should not be null")
    val status = DbStatus(
        statusKey = MicroBlogKey(
            id ?: throw IllegalArgumentException("mastodon Status.idStr should not be null"),
            host = user.userKey.host,
        ),
        platformType = PlatformType.Mastodon,
        content = dev.dimension.flare.data.database.cache.model.StatusContent.Mastodon(this),
        userKey = user.userKey,
    )
    return DbStatusWithUser(
        data = status,
        user = user,
    )
}

fun Account.toDbUser(): DbUser {
    requireNotNull(acct) { "mastodon Account.acct should not be null" }
    val host = if (acct.contains("@")) {
        acct.substring(acct.indexOf("@") + 1)
    } else {
        requireNotNull(url) { "mastodon Account.url should not be null" }
        Url(url).host
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
    )
}