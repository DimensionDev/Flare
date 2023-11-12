package dev.dimension.flare.data.database.cache.mapper

import dev.dimension.flare.data.cache.DbEmoji
import dev.dimension.flare.data.cache.DbPagingTimeline
import dev.dimension.flare.data.cache.DbStatus
import dev.dimension.flare.data.cache.DbUser
import dev.dimension.flare.data.database.cache.CacheDatabase
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
import kotlinx.datetime.toInstant

internal object Misskey {
    fun save(
        accountKey: MicroBlogKey,
        pagingKey: String,
        database: CacheDatabase,
        data: List<Note>,
        sortIdProvider: (Note) -> Long = { it.createdAt.toInstant().toEpochMilliseconds() },
    ) {
        val timeline = data.map { it.toDbPagingTimeline(accountKey, pagingKey, sortIdProvider) }
        val status = data.map { it.toDbStatus(accountKey) }
        val user = data.map { it.user.toDbUser(accountKey.host) }
        save(database, timeline, status, user)
    }

    fun save(
        accountKey: MicroBlogKey,
        pagingKey: String,
        database: CacheDatabase,
        data: List<Notification>,
    ) {
        val timeline = data.map { it.toDbPagingTimeline(accountKey, pagingKey) }
        val status = data.map { it.toDbStatus(accountKey) }
        val user = data.mapNotNull { it.user?.toDbUser(accountKey.host) }
        save(database, timeline, status, user)
    }

    private fun save(
        database: CacheDatabase,
        timeline: List<DbPagingTimeline>,
        status: List<DbStatus>,
        user: List<DbUser>,
    ) {
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
            val exsitingUsers =
                database.dbUserQueries.findByKeys(user.map { it.user_key }).executeAsList()
                    .filter {
                        it.content is UserContent.Misskey
                    }.map {
                        val content = it.content as UserContent.Misskey
                        val item =
                            user.find { user ->
                                user.user_key == it.user_key
                            }
                        if (item != null && item.content is UserContent.MisskeyLite) {
                            it.copy(
                                content =
                                    content.copy(
                                        data =
                                            content.data.copy(
                                                name = item.content.data.name,
                                                username = item.content.data.username,
                                                avatarUrl = item.content.data.avatarUrl,
                                            ),
                                    ),
                            )
                        } else {
                            it
                        }
                    }
            val result = (exsitingUsers + user).distinctBy { it.user_key }
            result.forEach {
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
    val sortId = this.createdAt.toInstant().toEpochMilliseconds()
    val status = this.toDbStatus(accountKey)
    return DbPagingTimeline(
        id = 0,
        account_key = accountKey,
        status_key = status.status_key,
        paging_key = pagingKey,
        sort_id = sortId,
    )
}

private fun Notification.toDbStatus(accountKey: MicroBlogKey): DbStatus {
    val user = this.user?.toDbUser(accountKey.host)
    return DbStatus(
        id = 0,
        status_key =
            MicroBlogKey(
                this.id,
                accountKey.host,
            ),
        platform_type = PlatformType.Misskey,
        user_key = user?.user_key,
        content = StatusContent.MisskeyNotification(this),
        account_key = accountKey,
    )
}

private fun Note.toDbPagingTimeline(
    accountKey: MicroBlogKey,
    pagingKey: String,
    sortIdProvider: (Note) -> Long = { it.createdAt.toInstant().toEpochMilliseconds() },
): DbPagingTimeline {
    val sortId = sortIdProvider(this)
    val status = this.toDbStatus(accountKey)
    return DbPagingTimeline(
        id = 0,
        account_key = accountKey,
        status_key = status.status_key,
        paging_key = pagingKey,
        sort_id = sortId,
    )
}

private fun Note.toDbStatus(accountKey: MicroBlogKey): DbStatus {
    val user = this.user.toDbUser(accountKey.host)
    return DbStatus(
        id = 0,
        status_key =
            MicroBlogKey(
                id = id,
                host = user.user_key.host,
            ),
        platform_type = PlatformType.Misskey,
        content = StatusContent.Misskey(this),
        user_key = user.user_key,
        account_key = accountKey,
    )
}

private fun UserLite.toDbUser(accountHost: String) =
    DbUser(
        user_key =
            MicroBlogKey(
                id = id,
                host = accountHost,
            ),
        platform_type = dev.dimension.flare.model.PlatformType.Misskey,
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
        user_key =
            MicroBlogKey(
                id = id,
                host = accountHost,
            ),
        platform_type = PlatformType.Misskey,
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

internal fun List<EmojiSimple>.toDb(host: String): DbEmoji {
    return DbEmoji(
        host = host,
        content = EmojiContent.Misskey(this),
    )
}
