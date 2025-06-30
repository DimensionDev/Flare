package dev.dimension.flare.data.database.cache.mapper

import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbEmoji
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.DbStatus
import dev.dimension.flare.data.database.cache.model.DbStatusWithUser
import dev.dimension.flare.data.database.cache.model.DbUser
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.data.network.mastodon.api.model.Account
import dev.dimension.flare.data.network.mastodon.api.model.Emoji
import dev.dimension.flare.data.network.mastodon.api.model.Notification
import dev.dimension.flare.data.network.mastodon.api.model.Status
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.ui.model.mapper.parseMastodonContent
import dev.dimension.flare.ui.render.toUi

internal object Mastodon {
    suspend fun save(
        accountKey: MicroBlogKey,
        pagingKey: String,
        database: CacheDatabase,
        data: List<Status>,
        sortIdProvider: (Status) -> Long = {
            if (it.pinned == true) {
                Long.MAX_VALUE
            } else {
                it.createdAt?.toEpochMilliseconds() ?: 0
            }
        },
    ) {
        val items = data.toDbPagingTimeline(accountKey, pagingKey, sortIdProvider)
        saveToDatabase(database, items)
    }

    suspend fun save(
        accountKey: MicroBlogKey,
        pagingKey: String,
        database: CacheDatabase,
        data: List<Notification>,
    ) {
        val items = data.toDb(accountKey, pagingKey)
        saveToDatabase(database, items)
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
            sortId = it.createdAt?.toEpochMilliseconds() ?: 0,
            status = it.toDbStatusWithUser(accountKey),
            references =
                listOfNotNull(
                    if (it.status != null) {
                        ReferenceType.Notification to it.status.toDbStatusWithUser(accountKey)
                    } else {
                        null
                    },
                ).toMap(),
        )
    }

private fun Notification.toDbStatusWithUser(accountKey: MicroBlogKey): DbStatusWithUser {
    val user =
        this.account?.toDbUser(accountKey.host) ?: throw IllegalStateException("account is null")
    val status = this.toDbStatus(accountKey)
    return DbStatusWithUser(
        data = status,
        user = user,
    )
}

private fun Notification.toDbStatus(accountKey: MicroBlogKey): DbStatus {
    val user =
        this.account?.toDbUser(accountKey.host) ?: throw IllegalStateException("account is null")
    return DbStatus(
        statusKey =
            MicroBlogKey(
                this.id ?: throw IllegalStateException("id is null"),
                user.userKey.host,
            ),
        userKey = user.userKey,
        content = StatusContent.MastodonNotification(this),
        accountType = AccountType.Specific(accountKey),
        text = null,
    )
}

internal fun List<Status>.toDbPagingTimeline(
    accountKey: MicroBlogKey,
    pagingKey: String,
    sortIdProvider: (Status) -> Long = {
        if (it.pinned == true) {
            Long.MAX_VALUE
        } else {
            it.createdAt?.toEpochMilliseconds() ?: 0
        }
    },
): List<DbPagingTimelineWithStatus> =
    this.map {
        createDbPagingTimelineWithStatus(
            accountKey = accountKey,
            pagingKey = pagingKey,
            sortId = sortIdProvider(it),
            status = it.toDbStatusWithUser(accountKey),
            references =
                listOfNotNull(
                    if (it.reblog != null) {
                        ReferenceType.Retweet to it.reblog.toDbStatusWithUser(accountKey)
                    } else {
                        null
                    },
                ).toMap(),
        )
    }

private fun Status.toDbStatusWithUser(accountKey: MicroBlogKey): DbStatusWithUser {
    val user =
        account?.toDbUser(accountKey.host)
            ?: throw IllegalArgumentException("mastodon Status.user should not be null")
    val status =
        DbStatus(
            statusKey =
                MicroBlogKey(
                    id
                        ?: throw IllegalArgumentException("mastodon Status.idStr should not be null"),
                    host = user.userKey.host,
                ),
            content =
                dev.dimension.flare.data.database.cache.model.StatusContent
                    .Mastodon(this),
            userKey = user.userKey,
            accountType = AccountType.Specific(accountKey),
            text =
                buildString {
                    if (spoilerText != null) {
                        append(spoilerText)
                        append("\n\n")
                    }
                    append(
                        parseMastodonContent(
                            this@toDbStatusWithUser,
                            accountKey,
                            accountKey.host,
                        ).toUi().raw,
                    )
                },
        )
    return DbStatusWithUser(
        data = status,
        user = user,
    )
}

internal fun Account.toDbUser(host: String): DbUser {
    val remoteHost =
        if (acct != null && acct.contains('@')) {
            acct.substring(acct.indexOf('@') + 1)
        } else {
            host
        }
    return DbUser(
        userKey =
            MicroBlogKey(
                id = id ?: throw IllegalArgumentException("mastodon Account.id should not be null"),
                host = host,
            ),
        platformType = PlatformType.Mastodon,
        name =
            displayName
                ?: throw IllegalArgumentException("mastodon Account.displayName should not be null"),
        handle =
            username
                ?: throw IllegalArgumentException("mastodon Account.username should not be null"),
        content =
            dev.dimension.flare.data.database.cache.model.UserContent
                .Mastodon(this),
        host = remoteHost,
    )
}

internal fun List<Emoji>.toDb(host: String): DbEmoji =
    DbEmoji(
        host = host,
        content =
            dev.dimension.flare.data.database.cache.model.EmojiContent
                .Mastodon(this),
    )
