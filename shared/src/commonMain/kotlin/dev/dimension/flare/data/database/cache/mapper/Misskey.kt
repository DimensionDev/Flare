
package dev.dimension.flare.data.database.cache.mapper

import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbEmoji
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.DbStatus
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
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.ReferenceType
import kotlin.time.Instant

internal object Misskey {
    suspend fun save(
        accountKey: MicroBlogKey,
        pagingKey: String,
        database: CacheDatabase,
        data: List<Note>,
        sortIdProvider: (Note) -> Long = { Instant.parse(it.createdAt).toEpochMilliseconds() },
    ) {
        saveToDatabase(database, data.toDbPagingTimeline(accountKey, pagingKey, sortIdProvider))
    }
}

internal fun List<Notification>.toDb(
    accountKey: MicroBlogKey,
    pagingKey: String,
): List<DbPagingTimelineWithStatus> =
    this.map {
        createDbPagingTimelineWithStatus(
            accountKey = accountKey,
            pagingKey = pagingKey,
            sortId = Instant.parse(it.createdAt).toEpochMilliseconds(),
            status = it.toDbStatusWithUser(accountKey),
            references =
                listOfNotNull(
                    if (it.note != null) {
                        ReferenceType.Notification to listOfNotNull(it.note.toDbStatusWithUser(accountKey))
                    } else {
                        null
                    },
                ).toMap(),
        )
    }

private fun Notification.toDbStatusWithUser(accountKey: MicroBlogKey): DbStatusWithUser {
    val user = this.user?.toDbUser(accountKey.host)
    val status = this.toDbStatus(accountKey)
    return DbStatusWithUser(
        data = status,
        user = user,
    )
}

private fun Notification.toDbStatus(accountKey: MicroBlogKey): DbStatus {
    val user = this.user?.toDbUser(accountKey.host)
    return DbStatus(
        statusKey =
            MicroBlogKey(
                this.id,
                accountKey.host,
            ),
        userKey = user?.userKey,
        content = StatusContent.MisskeyNotification(this),
        accountType = AccountType.Specific(accountKey),
        text = null,
        createdAt = Instant.parse(createdAt),
    )
}

internal fun List<Note>.toDbPagingTimeline(
    accountKey: MicroBlogKey,
    pagingKey: String,
    sortIdProvider: (Note) -> Long = { Instant.parse(it.createdAt).toEpochMilliseconds() },
): List<DbPagingTimelineWithStatus> =
    this.map {
        createDbPagingTimelineWithStatus(
            accountKey = accountKey,
            pagingKey = pagingKey,
            sortId = sortIdProvider(it),
            status = it.toDbStatusWithUser(accountKey),
            references =
                listOfNotNull(
                    if (it.renote != null) {
                        if (it.text.isNullOrEmpty() && it.files.isNullOrEmpty() && it.poll == null) {
                            ReferenceType.Retweet to listOfNotNull(it.renote.toDbStatusWithUser(accountKey))
                        } else {
                            ReferenceType.Quote to listOfNotNull(it.renote.toDbStatusWithUser(accountKey))
                        }
                    } else {
                        null
                    },
                    if (it.reply != null) {
                        ReferenceType.Reply to listOfNotNull(it.reply.toDbStatusWithUser(accountKey))
                    } else {
                        null
                    },
                ).toMap(),
        )
    }

private fun Note.toDbStatusWithUser(accountKey: MicroBlogKey): DbStatusWithUser {
    val user = user.toDbUser(accountKey.host)
    val status =
        DbStatus(
            statusKey =
                MicroBlogKey(
                    id = id,
                    host = user.userKey.host,
                ),
            content = StatusContent.Misskey(this),
            userKey = user.userKey,
            accountType = AccountType.Specific(accountKey),
            text = text,
            createdAt = Instant.parse(createdAt),
        )
    return DbStatusWithUser(
        data = status,
        user = user,
    )
}

private fun UserLite.toDbUser(accountHost: String) =
    DbUser(
        userKey =
            MicroBlogKey(
                id = id,
                host = accountHost,
            ),
        platformType = PlatformType.Misskey,
        name = name ?: "",
        handle = username,
        content = UserContent.MisskeyLite(this),
        host =
            if (host.isNullOrEmpty()) {
                accountHost
            } else {
                host
            },
    )

internal fun User.toDbUser(accountHost: String) =
    DbUser(
        userKey =
            MicroBlogKey(
                id = id,
                host = accountHost,
            ),
        platformType = dev.dimension.flare.model.PlatformType.Misskey,
        name = name ?: "",
        handle = username,
        content = UserContent.Misskey(this),
        host =
            if (host.isNullOrEmpty()) {
                accountHost
            } else {
                host
            },
    )

internal fun List<EmojiSimple>.toDb(host: String): DbEmoji =
    DbEmoji(
        host = host,
        content = EmojiContent.Misskey(this),
    )
