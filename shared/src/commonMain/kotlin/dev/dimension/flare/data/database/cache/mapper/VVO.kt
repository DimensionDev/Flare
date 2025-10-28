package dev.dimension.flare.data.database.cache.mapper

import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.DbStatus
import dev.dimension.flare.data.database.cache.model.DbStatusWithUser
import dev.dimension.flare.data.database.cache.model.DbUser
import dev.dimension.flare.data.database.cache.model.UserContent
import dev.dimension.flare.data.network.vvo.model.Comment
import dev.dimension.flare.data.network.vvo.model.Status
import dev.dimension.flare.data.network.vvo.model.User
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.model.vvoHost
import kotlin.time.Clock

internal object VVO {
    suspend fun saveStatus(
        accountKey: MicroBlogKey,
        pagingKey: String,
        database: dev.dimension.flare.data.database.cache.CacheDatabase,
        statuses: List<Status>,
        sortIdProvider: (Status) -> Long = { it.createdAt?.toEpochMilliseconds() ?: 0L },
    ) {
        val items =
            statuses.map {
                it.toDbPagingTimeline(accountKey, pagingKey, sortIdProvider)
            }
        saveToDatabase(database, items)
    }

    suspend fun saveComment(
        accountKey: MicroBlogKey,
        pagingKey: String,
        database: dev.dimension.flare.data.database.cache.CacheDatabase,
        statuses: List<Comment>,
        sortIdProvider: (Comment) -> Long = { it.createdAt?.toEpochMilliseconds() ?: 0L },
    ) {
        val items =
            statuses.map {
                it.toDbPagingTimeline(accountKey, pagingKey, sortIdProvider)
            }
        saveToDatabase(database, items)
    }
}

internal suspend fun Status.toDbPagingTimeline(
    accountKey: MicroBlogKey,
    pagingKey: String,
    sortIdProvider: suspend (Status) -> Long = { it.createdAt?.toEpochMilliseconds() ?: 0L },
): DbPagingTimelineWithStatus =
    createDbPagingTimelineWithStatus(
        accountKey = accountKey,
        pagingKey = pagingKey,
        sortId = sortIdProvider(this),
        status = this.toDbStatusWithUser(accountKey),
        references =
            listOfNotNull(
                if (this.retweetedStatus != null) {
                    ReferenceType.Retweet to listOfNotNull(this.retweetedStatus.toDbStatusWithUser(accountKey))
                } else {
                    null
                },
            ).toMap(),
    )

private fun Status.toDbStatusWithUser(accountKey: MicroBlogKey): DbStatusWithUser {
    val user = this.user?.toDbUser()
    val status = this.toDbStatus(accountKey)
    return DbStatusWithUser(
        data = status,
        user = user,
    )
}

private fun Status.toDbStatus(accountKey: MicroBlogKey): DbStatus =
    DbStatus(
        statusKey = MicroBlogKey(id = id, host = vvoHost),
        accountType = AccountType.Specific(accountKey),
        userKey = user?.id?.let { MicroBlogKey(id = it.toString(), host = vvoHost) },
        content =
            dev.dimension.flare.data.database.cache.model.StatusContent
                .VVO(data = this),
        text = rawText,
        createdAt = createdAt ?: Clock.System.now(),
    )

internal suspend fun Comment.toDbPagingTimeline(
    accountKey: MicroBlogKey,
    pagingKey: String,
    sortIdProvider: suspend (Comment) -> Long = { it.createdAt?.toEpochMilliseconds() ?: 0L },
): DbPagingTimelineWithStatus =
    createDbPagingTimelineWithStatus(
        accountKey = accountKey,
        pagingKey = pagingKey,
        sortId = sortIdProvider(this),
        status = this.toDbStatusWithUser(accountKey),
        references =
            commentList.orEmpty().associate {
                ReferenceType.Reply to listOfNotNull(it.toDbStatusWithUser(accountKey))
            },
    )

private fun Comment.toDbStatusWithUser(accountKey: MicroBlogKey): DbStatusWithUser {
    val user = this.user?.toDbUser()
    val status = this.toDbStatus(accountKey)
    return DbStatusWithUser(
        data = status,
        user = user,
    )
}

private fun Comment.toDbStatus(accountKey: MicroBlogKey): DbStatus =
    DbStatus(
        statusKey = MicroBlogKey(id = id, host = vvoHost),
        accountType = AccountType.Specific(accountKey),
        userKey = user?.id?.let { MicroBlogKey(id = it.toString(), host = vvoHost) },
        content =
            dev.dimension.flare.data.database.cache.model.StatusContent
                .VVOComment(data = this),
        text = null,
        createdAt = createdAt ?: Clock.System.now(),
    )

internal fun User.toDbUser(): DbUser? =
    screenName?.let {
        DbUser(
            handle = it,
            host = vvoHost,
            name = screenName,
            userKey = MicroBlogKey(id = id.toString(), host = vvoHost),
            platformType = PlatformType.VVo,
            content =
                UserContent
                    .VVO(data = this),
        )
    }
