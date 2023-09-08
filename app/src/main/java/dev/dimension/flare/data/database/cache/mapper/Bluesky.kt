package dev.dimension.flare.data.database.cache.mapper

import androidx.room.withTransaction
import app.bsky.actor.ProfileView
import app.bsky.actor.ProfileViewBasic
import app.bsky.actor.ProfileViewDetailed
import app.bsky.feed.FeedViewPost
import app.bsky.feed.FeedViewPostReasonUnion
import app.bsky.feed.PostView
import app.bsky.feed.ReplyRefParentUnion
import app.bsky.notification.ListNotificationsNotification
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbPagingTimeline
import dev.dimension.flare.data.database.cache.model.DbPagingTimelineWithStatus
import dev.dimension.flare.data.database.cache.model.DbStatus
import dev.dimension.flare.data.database.cache.model.DbStatusWithReference
import dev.dimension.flare.data.database.cache.model.DbStatusWithUser
import dev.dimension.flare.data.database.cache.model.DbUser
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.data.database.cache.model.UserContent
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.ReferenceType
import java.util.UUID

/**
 * for bluesky's id:
 *     for user:
 *         id = did.did
 *     for status:
 *         id = uri.atUri
 */

context(CacheDatabase, List<FeedViewPost>)
suspend fun save(
    accountKey: MicroBlogKey,
    pagingKey: String,
    sortIdProvider: (FeedViewPost) -> Long = { it.post.indexedAt.toEpochMilliseconds() },
) {
    val data = toDbPagingTimeline(accountKey, pagingKey, sortIdProvider)
    with(data) {
        saveDbPagingTimelineWithStatus()
    }
}

context(CacheDatabase, List<ListNotificationsNotification>)
suspend fun saveNotification(
    accountKey: MicroBlogKey,
    pagingKey: String,
) {
    val data = toDb(accountKey, pagingKey)
    with(data) {
        saveDbPagingTimelineWithStatus()
    }
}

context(CacheDatabase, List<PostView>)
suspend fun savePost(
    accountKey: MicroBlogKey,
    pagingKey: String,
    sortIdProvider: (PostView) -> Long = { it.indexedAt.toEpochMilliseconds() },
) {
    val data = toDb(accountKey, pagingKey, sortIdProvider)
    with(data) {
        saveDbPagingTimelineWithStatus()
    }
}

context(CacheDatabase, List<DbPagingTimelineWithStatus>)
private suspend fun saveDbPagingTimelineWithStatus() {
    withTransaction {
        (
            mapNotNull { it.status.status.user } + flatMap { it.status.references }
                .mapNotNull { it.status.user }
            ).let { allUsers ->
            val exsitingUsers = userDao()
                .findByKeys(allUsers.map { it.userKey })
                .filter {
                    it.content is UserContent.Bluesky
                }.map {
                    val content = it.content as UserContent.Bluesky
                    val user = allUsers.find { user ->
                        user.userKey == it.userKey
                    }

                    if (user != null && user.content is UserContent.BlueskyLite) {
                        it.copy(
                            content = content.copy(
                                data = content.data.copy(
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
            userDao().insertAll(result)
        }
        (
            map { it.status.status.data } + flatMap { it.status.references }
                .map { it.status.data }
            ).let {
            statusDao().insertAll(it)
        }
        flatMap { it.status.references }.map { it.reference }.let {
            statusReferenceDao().insertAll(it)
        }
        pagingTimelineDao().insertAll(map { it.timeline })
    }
}

fun List<PostView>.toDb(
    accountKey: MicroBlogKey,
    pagingKey: String,
    sortIdProvider: (PostView) -> Long = { it.indexedAt.toEpochMilliseconds() },
): List<DbPagingTimelineWithStatus> {
    return this.map {
        it.toDb(accountKey, pagingKey, sortIdProvider)
    }
}

fun PostView.toDb(
    accountKey: MicroBlogKey,
    pagingKey: String,
    sortIdProvider: (PostView) -> Long = { it.indexedAt.toEpochMilliseconds() },
): DbPagingTimelineWithStatus {
    val status = this.toDbStatusWithReference(accountKey)
    return DbPagingTimelineWithStatus(
        timeline = DbPagingTimeline(
            _id = UUID.randomUUID().toString(),
            accountKey = accountKey,
            statusKey = status.status.data.statusKey,
            pagingKey = pagingKey,
            sortId = sortIdProvider(this),
        ),
        status = status,
    )
}

fun PostView.toDbStatusWithReference(
    accountKey: MicroBlogKey,
): DbStatusWithReference {
    val status = this.toDbStatusWithUser(accountKey)
    // bluesky doesn't have "quote" as the same as the other platforms
    return DbStatusWithReference(
        status = status,
        references = listOfNotNull(),
    )
}

fun List<ListNotificationsNotification>.toDb(
    accountKey: MicroBlogKey,
    pagingKey: String,
): List<DbPagingTimelineWithStatus> {
    return this.map {
        it.toDb(accountKey, pagingKey)
    }
}

fun ListNotificationsNotification.toDb(
    accountKey: MicroBlogKey,
    pagingKey: String,
): DbPagingTimelineWithStatus {
    val status = this.toDbStatusWithReference(accountKey)
    return DbPagingTimelineWithStatus(
        timeline = DbPagingTimeline(
            _id = UUID.randomUUID().toString(),
            accountKey = accountKey,
            statusKey = status.status.data.statusKey,
            pagingKey = pagingKey,
            sortId = this.indexedAt.toEpochMilliseconds(),
        ),
        status = status,
    )
}

fun ListNotificationsNotification.toDbStatusWithReference(
    accountKey: MicroBlogKey,
): DbStatusWithReference {
    val status = this.toDbStatusWithUser(accountKey)
    return DbStatusWithReference(
        status = status,
        references = listOfNotNull(),
    )
}

private fun ListNotificationsNotification.toDbStatusWithUser(
    accountKey: MicroBlogKey,
): DbStatusWithUser {
    val user = this.author.toDbUser(accountKey.host)
    val status = this.toDbStatus(accountKey)
    return DbStatusWithUser(
        data = status,
        user = user,
    )
}

private fun ListNotificationsNotification.toDbStatus(
    accountKey: MicroBlogKey,
): DbStatus {
    val user = this.author.toDbUser(accountKey.host)
    return DbStatus(
        statusKey = MicroBlogKey(
            uri.atUri + "_" + user.userKey,
            accountKey.host,
        ),
        platformType = PlatformType.Bluesky,
        userKey = user.userKey,
        content = StatusContent.BlueskyNotification(this),
        accountKey = accountKey,
    )
}

fun List<FeedViewPost>.toDbPagingTimeline(
    accountKey: MicroBlogKey,
    pagingKey: String,
    sortIdProvider: (FeedViewPost) -> Long,
): List<DbPagingTimelineWithStatus> {
    return this.map {
        it.toDbPagingTimeline(accountKey, pagingKey, sortIdProvider)
    }
}

fun FeedViewPost.toDbPagingTimeline(
    accountKey: MicroBlogKey,
    pagingKey: String,
    sortIdProvider: (FeedViewPost) -> Long = { it.post.indexedAt.toEpochMilliseconds() },
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

fun FeedViewPost.toDbStatusWithReference(
    accountKey: MicroBlogKey,
): DbStatusWithReference {
    val status = this.post.toDbStatusWithUser(accountKey)
    val reply = when (val reply = this.reply?.parent) {
        is ReplyRefParentUnion.PostView -> reply.value.toDbStatusWithUser(accountKey)
        else -> null
    }
    when (val data = reason) {
        is FeedViewPostReasonUnion.ReasonRepost -> {
            val user = data.value.by.toDbUser(accountKey.host)
            val reasonStatus = DbStatusWithUser(
                user = user,
                data = DbStatus(
                    statusKey = MicroBlogKey(
                        post.uri.atUri + "_reblog_${user.userKey}",
                        accountKey.host,
                    ),
                    platformType = PlatformType.Bluesky,
                    userKey = data.value.by.toDbUser(accountKey.host).userKey,
                    content = StatusContent.BlueskyReason(data),
                    accountKey = accountKey,
                ),
            )
            return DbStatusWithReference(
                status = reasonStatus,
                references = listOfNotNull(
                    reply?.toDbStatusReference(status.data.statusKey, ReferenceType.Reply),
                    status.toDbStatusReference(status.data.statusKey, ReferenceType.Retweet),
                ),
            )
        }
        else -> {
            // bluesky doesn't have "quote" and "retweet" as the same as the other platforms
            return DbStatusWithReference(
                status = status,
                references = listOfNotNull(
                    reply?.toDbStatusReference(status.data.statusKey, ReferenceType.Reply),
                ),
            )
        }
    }
}

private fun PostView.toDbStatusWithUser(
    accountKey: MicroBlogKey,
): DbStatusWithUser {
    val user = author.toDbUser(accountKey.host)
    val status = DbStatus(
        statusKey = MicroBlogKey(
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

private fun ProfileView.toDbUser(
    host: String,
) = DbUser(
    userKey = MicroBlogKey(
        id = did.did,
        host = host,
    ),
    platformType = PlatformType.Bluesky,
    name = displayName.orEmpty(),
    handle = handle.handle,
    host = host,
    content = UserContent.BlueskyLite(
        ProfileViewBasic(
            did = did,
            handle = handle,
            displayName = displayName,
            avatar = avatar,
        ),
    ),
)

private fun ProfileViewBasic.toDbUser(
    host: String,
) = DbUser(
    userKey = MicroBlogKey(
        id = did.did,
        host = host,
    ),
    platformType = PlatformType.Bluesky,
    name = displayName.orEmpty(),
    handle = handle.handle,
    host = host,
    content = UserContent.BlueskyLite(this),
)

fun ProfileViewDetailed.toDbUser(
    host: String,
) = DbUser(
    userKey = MicroBlogKey(
        id = did.did,
        host = host,
    ),
    platformType = PlatformType.Bluesky,
    name = displayName.orEmpty(),
    handle = handle.handle,
    host = host,
    content = UserContent.Bluesky(this),
)
