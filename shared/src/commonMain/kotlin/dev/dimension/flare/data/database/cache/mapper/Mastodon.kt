package dev.dimension.flare.data.database.cache.mapper

import dev.dimension.flare.data.cache.DbEmoji
import dev.dimension.flare.data.cache.DbPagingTimeline
import dev.dimension.flare.data.cache.DbStatus
import dev.dimension.flare.data.cache.DbUser
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.EmojiContent
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.data.network.mastodon.api.model.Account
import dev.dimension.flare.data.network.mastodon.api.model.Emoji
import dev.dimension.flare.data.network.mastodon.api.model.Notification
import dev.dimension.flare.data.network.mastodon.api.model.Status
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType

internal object Mastodon {
    fun save(
        accountKey: MicroBlogKey,
        pagingKey: String,
        database: CacheDatabase,
        data: List<Status>,
        sortIdProvider: (Status) -> Long = { it.createdAt?.toEpochMilliseconds() ?: 0 },
    ) {
        val timeline = data.map { it.toDbPagingTimeline(accountKey, pagingKey, sortIdProvider) }
        val status = data.map { it.toDbStatus(accountKey) }
        val user = data.mapNotNull { it.account?.toDbUser(accountKey.host) }
        database.transaction {
            timeline.forEach {
                database.dbPagingTimelineQueries.insert(
                    account_key = it.account_key,
                    status_key = it.status_key,
                    paging_key = it.paging_key,
                    sort_id = it.sort_id,
                )
            }
            status.forEach {
                database.dbStatusQueries.insert(
                    status_key = it.status_key,
                    platform_type = it.platform_type,
                    user_key = it.user_key,
                    content = it.content,
                    account_key = it.account_key,
                )
            }
            user.forEach {
                database.dbUserQueries.insert(
                    user_key = it.user_key,
                    platform_type = it.platform_type,
                    name = it.name,
                    handle = it.handle,
                    content = it.content,
                    host = it.host,
                )
            }
        }
    }

    fun save(
        accountKey: MicroBlogKey,
        pagingKey: String,
        database: CacheDatabase,
        data: List<Notification>,
    ) {
        val timeline = data.map { it.toDbPagingTimeline(accountKey, pagingKey) }
        val status = data.map { it.toDbStatus(accountKey) }
        val user = data.mapNotNull { it.account?.toDbUser(accountKey.host) }
        database.transaction {
            timeline.forEach {
                database.dbPagingTimelineQueries.insert(
                    account_key = it.account_key,
                    status_key = it.status_key,
                    paging_key = it.paging_key,
                    sort_id = it.sort_id,
                )
            }
            status.forEach {
                database.dbStatusQueries.insert(
                    status_key = it.status_key,
                    platform_type = it.platform_type,
                    user_key = it.user_key,
                    content = it.content,
                    account_key = it.account_key,
                )
            }
            user.forEach {
                database.dbUserQueries.insert(
                    user_key = it.user_key,
                    platform_type = it.platform_type,
                    name = it.name,
                    handle = it.handle,
                    content = it.content,
                    host = it.host,
                )
            }
        }
    }
}

private fun Notification.toDbPagingTimeline(
    accountKey: MicroBlogKey,
    pagingKey: String,
): DbPagingTimeline {
    val user =
        this.account?.toDbUser(accountKey.host) ?: throw IllegalStateException("account is null")
    val sortId = this.createdAt?.toEpochMilliseconds() ?: 0
    return DbPagingTimeline(
        id = 0,
        account_key = accountKey,
        status_key =
            MicroBlogKey(
                this.id ?: throw IllegalStateException("id is null"),
                user.user_key.host,
            ),
        paging_key = pagingKey,
        sort_id = sortId,
    )
}

private fun Notification.toDbStatus(accountKey: MicroBlogKey): DbStatus {
    val user =
        this.account?.toDbUser(accountKey.host) ?: throw IllegalStateException("account is null")
    return DbStatus(
        id = 0,
        status_key =
            MicroBlogKey(
                this.id ?: throw IllegalStateException("id is null"),
                user.user_key.host,
            ),
        platform_type = PlatformType.Mastodon,
        user_key = user.user_key,
        content = StatusContent.MastodonNotification(this),
        account_key = accountKey,
    )
}

fun Status.toDbPagingTimeline(
    accountKey: MicroBlogKey,
    pagingKey: String,
    sortIdProvider: (Status) -> Long = { it.createdAt?.toEpochMilliseconds() ?: 0 },
): DbPagingTimeline {
    val status = this.toDbStatus(accountKey)
    val sortId = sortIdProvider(this)
    return DbPagingTimeline(
        id = 0,
        account_key = accountKey,
        status_key = status.status_key,
        paging_key = pagingKey,
        sort_id = sortId,
    )
}

private fun Status.toDbStatus(accountKey: MicroBlogKey): DbStatus {
    val user =
        account?.toDbUser(accountKey.host)
            ?: throw IllegalArgumentException("mastodon Status.user should not be null")
    return DbStatus(
        id = 0,
        status_key =
            MicroBlogKey(
                id ?: throw IllegalArgumentException("mastodon Status.idStr should not be null"),
                host = user.user_key.host,
            ),
        platform_type = PlatformType.Mastodon,
        content = dev.dimension.flare.data.database.cache.model.StatusContent.Mastodon(this),
        user_key = user.user_key,
        account_key = accountKey,
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
        user_key =
            MicroBlogKey(
                id = id ?: throw IllegalArgumentException("mastodon Account.id should not be null"),
                host = host,
            ),
        platform_type = PlatformType.Mastodon,
        name =
            displayName
                ?: throw IllegalArgumentException("mastodon Account.displayName should not be null"),
        handle =
            username
                ?: throw IllegalArgumentException("mastodon Account.username should not be null"),
        content = dev.dimension.flare.data.database.cache.model.UserContent.Mastodon(this),
        host = remoteHost,
    )
}

internal fun List<Emoji>.toDb(host: String): DbEmoji {
    return DbEmoji(
        host = host,
        content = EmojiContent.Mastodon(this),
    )
}
