package dev.dimension.flare.data.database.cache.mapper

import app.bsky.actor.ProfileView
import app.bsky.actor.ProfileViewBasic
import app.bsky.actor.ProfileViewDetailed
import app.bsky.feed.FeedViewPost
import app.bsky.feed.FeedViewPostReasonUnion
import app.bsky.feed.Like
import app.bsky.feed.PostView
import app.bsky.feed.ReplyRefParentUnion
import app.bsky.feed.Repost
import app.bsky.notification.ListNotificationsNotification
import app.bsky.notification.ListNotificationsReason
import chat.bsky.convo.ConvoView
import chat.bsky.convo.ConvoViewLastMessageUnion
import chat.bsky.convo.MessageView
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbDirectMessageTimeline
import dev.dimension.flare.data.database.cache.model.DbMessageItem
import dev.dimension.flare.data.database.cache.model.DbMessageRoom
import dev.dimension.flare.data.database.cache.model.DbMessageRoomReference
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.DbStatus
import dev.dimension.flare.data.database.cache.model.DbStatusWithUser
import dev.dimension.flare.data.database.cache.model.DbUser
import dev.dimension.flare.data.database.cache.model.MessageContent
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.data.database.cache.model.StatusContent.BlueskyNotification.Post
import dev.dimension.flare.data.database.cache.model.StatusContent.BlueskyNotification.UserList
import dev.dimension.flare.data.database.cache.model.UserContent
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.ui.model.mapper.parseBlueskyJson
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.datetime.toStdlibInstant
import sh.christian.ozone.api.AtUri

internal object Bluesky {
    suspend fun saveDM(
        accountKey: MicroBlogKey,
        database: CacheDatabase,
        data: List<ConvoView>,
    ) {
        val rooms = data.map { it.toDbMessageRoom(accountKey.host) }
        val references = data.flatMap { it.toDbMessageRoomReference(accountKey.host) }
        val messages =
            data.mapNotNull {
                it.lastMessage?.toDbMessageItem(it.toDbMessageRoom(accountKey.host).roomKey)
            }
        val timeline = data.map { it.toDbDirectMessageTimeline(accountKey) }
        val users = data.flatMap { it.members }.map { it.toDbUser(accountKey.host) }
        database.userDao().insertAll(users)
        database.messageDao().insertMessages(messages)
        database.messageDao().insertReferences(references)
        database.messageDao().insert(rooms)
        database.messageDao().insertTimeline(timeline)
    }

    suspend fun saveMessage(
        accountKey: MicroBlogKey,
        roomKey: MicroBlogKey,
        database: CacheDatabase,
        data: List<MessageView>,
    ) {
//        val room =
//            DbMessageRoom(
//                roomKey = roomKey,
//                platformType = PlatformType.Bluesky,
//                messageKey = null,
//            )
        val messages = data.map { it.toDbMessageItem(roomKey) }
        database.messageDao().insertMessages(messages)
//        database.messageDao().insert(room)
    }

    suspend fun savePost(
        accountKey: MicroBlogKey,
        pagingKey: String,
        database: CacheDatabase,
        data: List<PostView>,
        sortIdProvider: (PostView) -> Long = { it.indexedAt.toStdlibInstant().toEpochMilliseconds() },
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
            database.statusReferenceDao().delete(it.map { it.statusKey })
            database.statusReferenceDao().insertAll(it)
        }
        database.pagingTimelineDao().insertAll(timeline.map { it.timeline })
    }
}

internal fun List<PostView>.toDb(
    accountKey: MicroBlogKey,
    pagingKey: String,
    sortIdProvider: (PostView) -> Long = { it.indexedAt.toStdlibInstant().toEpochMilliseconds() },
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
            is ListNotificationsReason.Unknown,
            ListNotificationsReason.StarterpackJoined,
            ListNotificationsReason.Verified,
            ListNotificationsReason.Unverified,
            ->
                items.map {
                    createDbPagingTimelineWithStatus(
                        accountKey = accountKey,
                        pagingKey = pagingKey,
                        sortId = it.indexedAt.toStdlibInstant().toEpochMilliseconds(),
                        status = it.toDbStatusWithUser(accountKey),
                        references = mapOf(),
                    )
                }

            ListNotificationsReason.Repost, ListNotificationsReason.Like -> {
                val post =
                    items
                        .first()
                        .record
                        .let {
                            when (reason) {
                                ListNotificationsReason.Repost -> it.decodeAs<Repost>().subject
                                ListNotificationsReason.Like -> it.decodeAs<Like>().subject
                                else -> null
                            }
                        }?.uri
                        .let {
                            references[it]
                        }
                val content =
                    UserList(
                        data = items,
                        post = post,
                    )
                val idSuffix =
                    when (reason) {
                        ListNotificationsReason.Repost -> "_repost"
                        ListNotificationsReason.Like -> "_like"
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
                                accountType = AccountType.Specific(accountKey),
                                userKey = null,
                                content = content,
                                text = null,
                                createdAt = items.first().indexedAt.toStdlibInstant(),
                            ),
                    )
                listOf(
                    createDbPagingTimelineWithStatus(
                        accountKey = accountKey,
                        pagingKey = pagingKey,
                        sortId =
                            items
                                .first()
                                .indexedAt
                                .toStdlibInstant()
                                .toEpochMilliseconds(),
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

            ListNotificationsReason.Follow -> {
                val content = UserList(data = items, post = null)
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
                                accountType = AccountType.Specific(accountKey),
                                userKey = null,
                                content = content,
                                text = null,
                                createdAt = items.first().indexedAt.toStdlibInstant(),
                            ),
                    )
                listOfNotNull(
                    createDbPagingTimelineWithStatus(
                        accountKey = accountKey,
                        pagingKey = pagingKey,
                        sortId =
                            items
                                .first()
                                .indexedAt
                                .toStdlibInstant()
                                .toEpochMilliseconds(),
                        status = data,
                        references = mapOf(),
                    ),
                )
            }

            ListNotificationsReason.Mention, ListNotificationsReason.Reply, ListNotificationsReason.Quote -> {
                items.mapNotNull {
                    val post = references[it.uri] ?: return@mapNotNull null
                    val content = Post(post = post)
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
                                    accountType = AccountType.Specific(accountKey),
                                    userKey = user.userKey,
                                    content = content,
                                    text = null,
                                    createdAt = it.indexedAt.toStdlibInstant(),
                                ),
                        )
                    createDbPagingTimelineWithStatus(
                        accountKey = accountKey,
                        pagingKey = pagingKey,
                        sortId = it.indexedAt.toStdlibInstant().toEpochMilliseconds(),
                        status = data,
                        references =
                            mapOf(
                                ReferenceType.Notification to post.toDbStatusWithUser(accountKey),
                            ),
                    )
                }
            }

            ListNotificationsReason.LikeViaRepost ->
                items.mapNotNull {
                    val post = references[it.uri] ?: return@mapNotNull null
                    val content = Post(post = post)
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
                                    accountType = AccountType.Specific(accountKey),
                                    userKey = user.userKey,
                                    content = content,
                                    text = null,
                                    createdAt = it.indexedAt.toStdlibInstant(),
                                ),
                        )
                    createDbPagingTimelineWithStatus(
                        accountKey = accountKey,
                        pagingKey = pagingKey,
                        sortId = it.indexedAt.toStdlibInstant().toEpochMilliseconds(),
                        status = data,
                        references =
                            mapOf(
                                ReferenceType.Notification to post.toDbStatusWithUser(accountKey),
                            ),
                    )
                }
            ListNotificationsReason.RepostViaRepost ->
                items.mapNotNull {
                    val post = references[it.uri] ?: return@mapNotNull null
                    val content = Post(post = post)
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
                                    accountType = AccountType.Specific(accountKey),
                                    userKey = user.userKey,
                                    content = content,
                                    text = null,
                                    createdAt = it.indexedAt.toStdlibInstant(),
                                ),
                        )
                    createDbPagingTimelineWithStatus(
                        accountKey = accountKey,
                        pagingKey = pagingKey,
                        sortId = it.indexedAt.toStdlibInstant().toEpochMilliseconds(),
                        status = data,
                        references =
                            mapOf(
                                ReferenceType.Notification to post.toDbStatusWithUser(accountKey),
                            ),
                    )
                }
            ListNotificationsReason.SubscribedPost -> {
                items.mapNotNull {
                    val post = references[it.uri] ?: return@mapNotNull null
                    val content = Post(post = post)
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
                                    accountType = AccountType.Specific(accountKey),
                                    userKey = user.userKey,
                                    content = content,
                                    text = null,
                                    createdAt = it.indexedAt.toStdlibInstant(),
                                ),
                        )
                    createDbPagingTimelineWithStatus(
                        accountKey = accountKey,
                        pagingKey = pagingKey,
                        sortId = it.indexedAt.toStdlibInstant().toEpochMilliseconds(),
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
        userKey = user.userKey,
        content = StatusContent.BlueskyNotification.Normal(this),
        accountType = AccountType.Specific(accountKey),
        text = null,
        createdAt = indexedAt.toStdlibInstant(),
    )
}

internal fun List<FeedViewPost>.toDbPagingTimeline(
    accountKey: MicroBlogKey,
    pagingKey: String,
    sortIdProvider: (FeedViewPost) -> Long = {
        when (val reason = it.reason) {
            is FeedViewPostReasonUnion.ReasonRepost -> {
                reason.value.indexedAt
                    .toStdlibInstant()
                    .toEpochMilliseconds()
            }

            is FeedViewPostReasonUnion.ReasonPin -> {
                Long.MAX_VALUE
            }

            else -> {
                it.post.indexedAt
                    .toStdlibInstant()
                    .toEpochMilliseconds()
            }
        }
    },
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
                                userKey =
                                    data.value.by
                                        .toDbUser(accountKey.host)
                                        .userKey,
                                content = StatusContent.BlueskyReason(data),
                                accountType = AccountType.Specific(accountKey),
                                text = null,
                                createdAt = it.post.indexedAt.toStdlibInstant(),
                            ),
                    )
                }

                is FeedViewPostReasonUnion.ReasonPin -> {
                    val status = it.post.toDbStatusWithUser(accountKey)
                    DbStatusWithUser(
                        user = status.user,
                        data =
                            DbStatus(
                                statusKey =
                                    MicroBlogKey(
                                        it.post.uri.atUri + "_pin_${status.user?.userKey}",
                                        accountKey.host,
                                    ),
                                userKey = status.user?.userKey,
                                content = StatusContent.BlueskyReason(data),
                                accountType = AccountType.Specific(accountKey),
                                text = status.data.text,
                                createdAt = it.post.indexedAt.toStdlibInstant(),
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
                if (it.reason != null) {
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
            content = StatusContent.Bluesky(this),
            userKey = user.userKey,
            accountType = AccountType.Specific(accountKey),
            text = parseBlueskyJson(record, accountKey).raw,
            createdAt = indexedAt.toStdlibInstant(),
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

private fun chat.bsky.actor.ProfileViewBasic.toDbUser(host: String) =
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
                    associated = associated,
                    viewer = viewer,
                    labels = labels,
                ),
            ),
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

private fun ConvoView.toDbDirectMessageTimeline(accountKey: MicroBlogKey): DbDirectMessageTimeline {
    val roomKey = toDbMessageRoom(accountKey.host).roomKey
    return DbDirectMessageTimeline(
        accountType = AccountType.Specific(accountKey),
        roomKey = roomKey,
        sortId =
            lastMessage?.toDbMessageItem(roomKey)?.timestamp
                ?: 0L,
        unreadCount = unreadCount,
    )
}

private fun ConvoView.toDbMessageRoom(host: String) =
    DbMessageRoom(
        roomKey = MicroBlogKey(id = id, host = host),
        platformType = PlatformType.Bluesky,
        messageKey =
            when (val message = lastMessage) {
                is ConvoViewLastMessageUnion.MessageView -> MicroBlogKey(id = message.value.id, host = host)
                is ConvoViewLastMessageUnion.DeletedMessageView -> MicroBlogKey(id = message.value.id, host = host)
                null -> null
                is ConvoViewLastMessageUnion.Unknown -> null
            },
    )

private fun ConvoView.toDbMessageRoomReference(host: String): List<DbMessageRoomReference> {
    val roomKey = toDbMessageRoom(host).roomKey
    return members.map {
        DbMessageRoomReference(
            roomKey = roomKey,
            userKey = MicroBlogKey(id = it.did.did, host = host),
        )
    }
}

private fun ConvoViewLastMessageUnion.toDbMessageItem(roomKey: MicroBlogKey) =
    when (this) {
        is ConvoViewLastMessageUnion.MessageView -> toDbMessageItem(roomKey)
        is ConvoViewLastMessageUnion.DeletedMessageView -> toDbMessageItem(roomKey)
        is ConvoViewLastMessageUnion.Unknown -> null
    }

private fun MessageView.toDbMessageItem(roomKey: MicroBlogKey) =
    DbMessageItem(
        messageKey = MicroBlogKey(id = id, host = roomKey.host),
        roomKey = roomKey,
        userKey = MicroBlogKey(id = sender.did.did, host = roomKey.host),
        timestamp = sentAt.toStdlibInstant().toEpochMilliseconds(),
        content = MessageContent.Bluesky.Message(this),
        showSender = false,
    )

private fun ConvoViewLastMessageUnion.MessageView.toDbMessageItem(roomKey: MicroBlogKey) = value.toDbMessageItem(roomKey)

private fun ConvoViewLastMessageUnion.DeletedMessageView.toDbMessageItem(roomKey: MicroBlogKey) =
    with(value) {
        DbMessageItem(
            messageKey = MicroBlogKey(id = id, host = roomKey.host),
            roomKey = roomKey,
            userKey = MicroBlogKey(id = sender.did.did, host = roomKey.host),
            timestamp = sentAt.toStdlibInstant().toEpochMilliseconds(),
            content = MessageContent.Bluesky.Deleted(this),
            showSender = false,
        )
    }
