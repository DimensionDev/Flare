package dev.dimension.flare.data.database.cache.mapper

import app.bsky.actor.ProfileView
import app.bsky.actor.ProfileViewBasic
import app.bsky.actor.ProfileViewDetailed
import app.bsky.feed.FeedViewPost
import app.bsky.feed.FeedViewPostReasonUnion
import app.bsky.feed.PostView
import app.bsky.notification.ListNotificationsNotification
import dev.dimension.flare.data.cache.DbPagingTimeline
import dev.dimension.flare.data.cache.DbStatus
import dev.dimension.flare.data.cache.DbUser
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.data.database.cache.model.UserContent
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType

object Bluesky {
    fun saveFeed(
        accountKey: MicroBlogKey,
        pagingKey: String,
        database: CacheDatabase,
        data: List<FeedViewPost>,
        sortIdProvider: (FeedViewPost) -> Long = { it.post.indexedAt.toEpochMilliseconds() },
    ) {
        val timeline = data.map { it.toDbPagingTimeline(accountKey, pagingKey, sortIdProvider) }
        val status = data.map { it.toDbStatus(accountKey) }
        val user = data.map { it.post.author.toDbUser(accountKey.host) }
        save(database, timeline, status, user)
    }

    fun saveNotification(
        accountKey: MicroBlogKey,
        pagingKey: String,
        database: CacheDatabase,
        data: List<ListNotificationsNotification>,
    ) {
        val timeline = data.map { it.toDbPagingTimeline(accountKey, pagingKey) }
        val status = data.map { it.toDbStatus(accountKey) }
        val user = data.map { it.author.toDbUser(accountKey.host) }
        save(database, timeline, status, user)
    }

    fun savePost(
        accountKey: MicroBlogKey,
        pagingKey: String,
        database: CacheDatabase,
        data: List<PostView>,
        sortIdProvider: (PostView) -> Long = { it.indexedAt.toEpochMilliseconds() },
    ) {
        val timeline = data.map { it.toDbPagingTimeline(accountKey, pagingKey, sortIdProvider) }
        val status = data.map { it.toDbStatus(accountKey) }
        val user = data.map { it.author.toDbUser(accountKey.host) }
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
                        it.content is UserContent.Bluesky
                    }.map {
                        val content = it.content as UserContent.Bluesky
                        val item =
                            user.find { user ->
                                user.user_key == it.user_key
                            }
                        if (item != null && item.content is UserContent.BlueskyLite) {
                            it.copy(
                                content =
                                    content.copy(
                                        data =
                                            content.data.copy(
                                                displayName = item.content.data.displayName,
                                                handle = item.content.data.handle,
                                                avatar = item.content.data.avatar,
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

fun PostView.toDbPagingTimeline(
    accountKey: MicroBlogKey,
    pagingKey: String,
    sortIdProvider: (PostView) -> Long = { it.indexedAt.toEpochMilliseconds() },
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

fun ListNotificationsNotification.toDbPagingTimeline(
    accountKey: MicroBlogKey,
    pagingKey: String,
): DbPagingTimeline {
    val sortId = this.indexedAt.toEpochMilliseconds()
    val status = this.toDbStatus(accountKey)
    return DbPagingTimeline(
        id = 0,
        account_key = accountKey,
        status_key = status.status_key,
        paging_key = pagingKey,
        sort_id = sortId,
    )
}

private fun ListNotificationsNotification.toDbStatus(accountKey: MicroBlogKey): DbStatus {
    val user = this.author.toDbUser(accountKey.host)
    return DbStatus(
        status_key =
            MicroBlogKey(
                uri.atUri + "_" + user.user_key,
                accountKey.host,
            ),
        platform_type = PlatformType.Bluesky,
        user_key = user.user_key,
        content = StatusContent.BlueskyNotification(this),
        account_key = accountKey,
        id = 0,
    )
}

fun FeedViewPost.toDbPagingTimeline(
    accountKey: MicroBlogKey,
    pagingKey: String,
    sortIdProvider: (FeedViewPost) -> Long = { it.post.indexedAt.toEpochMilliseconds() },
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

fun FeedViewPost.toDbStatus(accountKey: MicroBlogKey): DbStatus {
    when (val data = reason) {
        is FeedViewPostReasonUnion.ReasonRepost -> {
            val user = data.value.by.toDbUser(accountKey.host)
            return DbStatus(
                status_key =
                    MicroBlogKey(
                        post.uri.atUri + "_reblog_${user.user_key}",
                        accountKey.host,
                    ),
                platform_type = PlatformType.Bluesky,
                user_key = user.user_key,
                content = StatusContent.Bluesky(post, data),
                account_key = accountKey,
                id = 0,
            )
        }
        else -> {
            // bluesky doesn't have "quote" and "retweet" as the same as the other platforms
            return with(post) {
                toDbStatus(accountKey)
            }
        }
    }
}

private fun PostView.toDbStatus(accountKey: MicroBlogKey): DbStatus {
    val user = author.toDbUser(accountKey.host)
    return DbStatus(
        status_key =
            MicroBlogKey(
                uri.atUri,
                host = user.user_key.host,
            ),
        platform_type = PlatformType.Bluesky,
        content = StatusContent.Bluesky(this, null),
        user_key = user.user_key,
        account_key = accountKey,
        id = 0,
    )
}

private fun ProfileView.toDbUser(host: String) =
    DbUser(
        user_key =
            MicroBlogKey(
                id = did.did,
                host = host,
            ),
        platform_type = PlatformType.Bluesky,
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
        user_key =
            MicroBlogKey(
                id = did.did,
                host = host,
            ),
        platform_type = PlatformType.Bluesky,
        name = displayName.orEmpty(),
        handle = handle.handle,
        host = host,
        content = UserContent.BlueskyLite(this),
    )

fun ProfileViewDetailed.toDbUser(host: String) =
    DbUser(
        user_key =
            MicroBlogKey(
                id = did.did,
                host = host,
            ),
        platform_type = PlatformType.Bluesky,
        name = displayName.orEmpty(),
        handle = handle.handle,
        host = host,
        content = UserContent.Bluesky(this),
    )
