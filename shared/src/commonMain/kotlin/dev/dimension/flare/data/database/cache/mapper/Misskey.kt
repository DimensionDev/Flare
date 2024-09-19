@file:OptIn(ExperimentalUuidApi::class)

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
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.ReferenceType
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.Instant
import kotlin.uuid.ExperimentalUuidApi

internal object Misskey {
    suspend fun save(
        accountKey: MicroBlogKey,
        pagingKey: String,
        database: CacheDatabase,
        data: List<Note>,
        sortIdProvider: (Note) -> Long = { Instant.parse(it.createdAt).toEpochMilliseconds() },
    ) {
        save(database, data.toDbPagingTimeline(accountKey, pagingKey, sortIdProvider))
    }

    suspend fun save(
        accountKey: MicroBlogKey,
        pagingKey: String,
        database: CacheDatabase,
        data: List<Notification>,
    ) {
        save(database, data.toDb(accountKey, pagingKey))
    }

    private suspend fun save(
        database: CacheDatabase,
        timeline: List<DbPagingTimelineWithStatus>,
    ) {
        (
            timeline.mapNotNull { it.status.status.user } +
                timeline
                    .flatMap { it.status.references }
                    .mapNotNull { it.status.user }
        ).let { allUsers ->
            val exsitingUsers =
                database
                    .userDao()
                    .findByKeys(allUsers.map { it.userKey })
                    .firstOrNull()
                    .orEmpty()
                    .filter {
                        it.content is UserContent.Misskey
                    }.map {
                        val content = it.content as UserContent.Misskey
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

            val result = (exsitingUsers + allUsers).distinctBy { it.userKey }
            database.userDao().insertAll(result)
        }
        (
            timeline.map { it.status.status.data } +
                timeline
                    .flatMap { it.status.references }
                    .map { it.status.data }
        ).let {
            database.statusDao().insertAll(it)
        }
        timeline.flatMap { it.status.references }.map { it.reference }.let {
            database.statusReferenceDao().insertAll(it)
        }
        database.pagingTimelineDao().insertAll(timeline.map { it.timeline })
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
                        ReferenceType.Notification to it.note.toDbStatusWithUser(accountKey)
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
        platformType = PlatformType.Misskey,
        userKey = user?.userKey,
        content = StatusContent.MisskeyNotification(this),
        accountKey = accountKey,
    )
}

private fun List<Note>.toDbPagingTimeline(
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
                        ReferenceType.Retweet to it.renote.toDbStatusWithUser(accountKey)
                    } else {
                        null
                    },
                    if (it.reply != null) {
                        ReferenceType.Reply to it.reply.toDbStatusWithUser(accountKey)
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
            platformType = PlatformType.Misskey,
            content = StatusContent.Misskey(this),
            userKey = user.userKey,
            accountKey = accountKey,
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
