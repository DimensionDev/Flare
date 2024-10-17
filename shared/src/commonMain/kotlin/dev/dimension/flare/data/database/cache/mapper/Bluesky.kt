package dev.dimension.flare.data.database.cache.mapper

import app.bsky.actor.ProfileView
import app.bsky.actor.ProfileViewBasic
import app.bsky.actor.ProfileViewDetailed
import app.bsky.feed.FeedViewPost
import app.bsky.feed.FeedViewPostReasonUnion
import app.bsky.feed.PostView
import app.bsky.feed.ReplyRefParentUnion
import app.bsky.notification.ListNotificationsNotification
import app.bsky.notification.ListNotificationsReason
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.DbStatus
import dev.dimension.flare.data.database.cache.model.DbStatusWithUser
import dev.dimension.flare.data.database.cache.model.DbUser
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.data.database.cache.model.UserContent
import dev.dimension.flare.data.datasource.bluesky.bskyJson
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.ReferenceType
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.flow.firstOrNull
import sh.christian.ozone.api.AtUri
import sh.christian.ozone.api.model.JsonContent

internal object Bluesky {
    suspend fun saveFeed(
        accountKey: MicroBlogKey,
        pagingKey: String,
        database: CacheDatabase,
        data: List<FeedViewPost>,
        sortIdProvider: (FeedViewPost) -> Long = {
            val reason = it.reason
            if (reason is FeedViewPostReasonUnion.ReasonRepost) {
                reason.value.indexedAt.toEpochMilliseconds()
            } else {
                it.post.indexedAt.toEpochMilliseconds()
            }
        },
    ) {
        save(database, data.toDbPagingTimeline(accountKey, pagingKey, sortIdProvider))
    }

    suspend fun saveNotification(
        accountKey: MicroBlogKey,
        pagingKey: String,
        database: CacheDatabase,
        data: List<ListNotificationsNotification>,
        references: ImmutableMap<AtUri, PostView>,
    ) {
        save(database, data.toDb(accountKey, pagingKey, references))
    }

    suspend fun savePost(
        accountKey: MicroBlogKey,
        pagingKey: String,
        database: CacheDatabase,
        data: List<PostView>,
        sortIdProvider: (PostView) -> Long = { it.indexedAt.toEpochMilliseconds() },
    ) {
        save(database, data.toDb(accountKey, pagingKey, sortIdProvider))
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
                        it.content is UserContent.Bluesky
                    }.map {
                        val content = it.content as UserContent.Bluesky
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

private fun List<PostView>.toDb(
    accountKey: MicroBlogKey,
    pagingKey: String,
    sortIdProvider: (PostView) -> Long,
): List<DbPagingTimelineWithStatus> =
    this.map {
        createDbPagingTimelineWithStatus(
            accountKey = accountKey,
            pagingKey = pagingKey,
            sortId = sortIdProvider(it),
            status = it.toDbStatusWithUser(accountKey),
            references = mapOf(),
        )
    }

internal fun List<ListNotificationsNotification>.toDb(
    accountKey: MicroBlogKey,
    pagingKey: String,
    references: ImmutableMap<AtUri, PostView>,
): List<DbPagingTimelineWithStatus> {
    // merge same type
    val grouped = this.groupBy { it.reason }.filter { it.value.any() }
    return grouped.flatMap { (reason, items) ->
        when (reason) {
            ListNotificationsReason.UNKNOWN, ListNotificationsReason.STARTERPACK_JOINED ->
                items.map {
                    createDbPagingTimelineWithStatus(
                        accountKey = accountKey,
                        pagingKey = pagingKey,
                        sortId = it.indexedAt.toEpochMilliseconds(),
                        status = it.toDbStatusWithUser(accountKey),
                        references = mapOf(),
                    )
                }

            ListNotificationsReason.REPOST, ListNotificationsReason.LIKE -> {
                val post =
                    items
                        .first()
                        .record
                        .let {
                            when (reason) {
                                ListNotificationsReason.REPOST -> it.bskyJson<JsonContent, app.bsky.feed.Repost>().subject
                                ListNotificationsReason.LIKE -> it.bskyJson<JsonContent, app.bsky.feed.Like>().subject
                                else -> null
                            }
                        }?.uri
                        .let {
                            references[it]
                        }
                val content =
                    StatusContent.BlueskyNotification.UserList(
                        data = items,
                        post = post,
                    )
                val idSuffix =
                    when (reason) {
                        ListNotificationsReason.REPOST -> "_repost"
                        ListNotificationsReason.LIKE -> "_like"
                        else -> ""
                    }
                val data =
                    DbStatusWithUser(
                        user = null,
                        data =
                            DbStatus(
                                statusKey =
                                    MicroBlogKey(
                                        id = items.joinToString("_") { it.uri.atUri } + idSuffix,
                                        host = accountKey.host,
                                    ),
                                accountKey = accountKey,
                                userKey = null,
                                platformType = PlatformType.Bluesky,
                                content = content,
                            ),
                    )
                listOf(
                    createDbPagingTimelineWithStatus(
                        accountKey = accountKey,
                        pagingKey = pagingKey,
                        sortId = items.first().indexedAt.toEpochMilliseconds(),
                        status = data,
                        references =
                            listOfNotNull(
                                post,
                            ).associate {
                                ReferenceType.Notification to it.toDbStatusWithUser(accountKey = accountKey)
                            },
                    ),
                )
            }

            ListNotificationsReason.FOLLOW -> {
                val content = StatusContent.BlueskyNotification.UserList(data = items, post = null)
                val data =
                    DbStatusWithUser(
                        user = null,
                        data =
                            DbStatus(
                                statusKey =
                                    MicroBlogKey(
                                        id = items.joinToString("_") { it.uri.atUri } + "_follow",
                                        host = accountKey.host,
                                    ),
                                accountKey = accountKey,
                                userKey = null,
                                platformType = PlatformType.Bluesky,
                                content = content,
                            ),
                    )
                listOfNotNull(
                    createDbPagingTimelineWithStatus(
                        accountKey = accountKey,
                        pagingKey = pagingKey,
                        sortId = items.first().indexedAt.toEpochMilliseconds(),
                        status = data,
                        references = mapOf(),
                    ),
                )
            }

            ListNotificationsReason.MENTION, ListNotificationsReason.REPLY, ListNotificationsReason.QUOTE -> {
                items.mapNotNull {
                    val post = references[it.uri] ?: return@mapNotNull null
                    val content = StatusContent.BlueskyNotification.Post(post = post)
                    val user = post.author.toDbUser(accountKey.host)
                    val data =
                        DbStatusWithUser(
                            user = user,
                            data =
                                DbStatus(
                                    statusKey =
                                        MicroBlogKey(
                                            id = it.uri.atUri,
                                            host = accountKey.host,
                                        ),
                                    accountKey = accountKey,
                                    userKey = user.userKey,
                                    platformType = PlatformType.Bluesky,
                                    content = content,
                                ),
                        )
                    createDbPagingTimelineWithStatus(
                        accountKey = accountKey,
                        pagingKey = pagingKey,
                        sortId = it.indexedAt.toEpochMilliseconds(),
                        status = data,
                        references =
                            mapOf(
                                ReferenceType.Notification to post.toDbStatusWithUser(accountKey),
                            ),
                    )
                }
            }
        }
    }
}

private fun ListNotificationsNotification.toDbStatusWithUser(accountKey: MicroBlogKey): DbStatusWithUser {
    val user = this.author.toDbUser(accountKey.host)
    val status = this.toDbStatus(accountKey)
    return DbStatusWithUser(
        data = status,
        user = user,
    )
}

private fun ListNotificationsNotification.toDbStatus(accountKey: MicroBlogKey): DbStatus {
    val user = this.author.toDbUser(accountKey.host)
    return DbStatus(
        statusKey =
            MicroBlogKey(
                uri.atUri + "_" + user.userKey,
                accountKey.host,
            ),
        platformType = PlatformType.Bluesky,
        userKey = user.userKey,
        content = StatusContent.BlueskyNotification.Normal(this),
        accountKey = accountKey,
    )
}

internal fun List<FeedViewPost>.toDbPagingTimeline(
    accountKey: MicroBlogKey,
    pagingKey: String,
    sortIdProvider: (FeedViewPost) -> Long,
): List<DbPagingTimelineWithStatus> =
    this.map {
        val reply =
            when (val reply = it.reply?.parent) {
                is ReplyRefParentUnion.PostView -> reply.value.toDbStatusWithUser(accountKey)
                else -> null
            }
        val status =
            when (val data = it.reason) {
                is FeedViewPostReasonUnion.ReasonRepost -> {
                    val user = data.value.by.toDbUser(accountKey.host)
                    DbStatusWithUser(
                        user = user,
                        data =
                            DbStatus(
                                statusKey =
                                    MicroBlogKey(
                                        it.post.uri.atUri + "_reblog_${user.userKey}",
                                        accountKey.host,
                                    ),
                                platformType = PlatformType.Bluesky,
                                userKey =
                                    data.value.by
                                        .toDbUser(accountKey.host)
                                        .userKey,
                                content = StatusContent.BlueskyReason(data),
                                accountKey = accountKey,
                            ),
                    )
                }

                else -> {
                    // bluesky doesn't have "quote" and "retweet" as the same as the other platforms
                    it.post.toDbStatusWithUser(accountKey)
                }
            }
        val references =
            listOfNotNull(
                if (reply != null) {
                    ReferenceType.Reply to reply
                } else {
                    null
                },
                if (it.reason is FeedViewPostReasonUnion.ReasonRepost) {
                    ReferenceType.Retweet to it.post.toDbStatusWithUser(accountKey)
                } else {
                    null
                },
            ).toMap()
        createDbPagingTimelineWithStatus(
            accountKey = accountKey,
            pagingKey = pagingKey,
            sortId = sortIdProvider(it),
            status = status,
            references = references,
        )
    }

private fun PostView.toDbStatusWithUser(accountKey: MicroBlogKey): DbStatusWithUser {
    val user = author.toDbUser(accountKey.host)
    val status =
        DbStatus(
            statusKey =
                MicroBlogKey(
                    uri.atUri,
                    host = user.userKey.host,
                ),
            platformType = PlatformType.Bluesky,
            content = StatusContent.Bluesky(this),
            userKey = user.userKey,
            accountKey = accountKey,
        )
    return DbStatusWithUser(
        data = status,
        user = user,
    )
}

private fun ProfileView.toDbUser(host: String) =
    DbUser(
        userKey =
            MicroBlogKey(
                id = did.did,
                host = host,
            ),
        platformType = PlatformType.Bluesky,
        name = displayName.orEmpty(),
        handle = handle.handle,
        host = host,
        content =
            UserContent.BlueskyLite(
                ProfileViewBasic(
                    did = did,
                    handle = handle,
                    displayName = displayName,
                    avatar = avatar,
                ),
            ),
    )

private fun ProfileViewBasic.toDbUser(host: String) =
    DbUser(
        userKey =
            MicroBlogKey(
                id = did.did,
                host = host,
            ),
        platformType = PlatformType.Bluesky,
        name = displayName.orEmpty(),
        handle = handle.handle,
        host = host,
        content = UserContent.BlueskyLite(this),
    )

internal fun ProfileViewDetailed.toDbUser(host: String) =
    DbUser(
        userKey =
            MicroBlogKey(
                id = did.did,
                host = host,
            ),
        platformType = PlatformType.Bluesky,
        name = displayName.orEmpty(),
        handle = handle.handle,
        host = host,
        content = UserContent.Bluesky(this),
    )
