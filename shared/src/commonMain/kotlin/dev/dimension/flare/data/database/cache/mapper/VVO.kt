package dev.dimension.flare.data.database.cache.mapper

import dev.dimension.flare.data.network.vvo.model.Comment
import dev.dimension.flare.data.network.vvo.model.Status
import dev.dimension.flare.data.network.vvo.model.User
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.vvoHost

internal object VVO {
    fun saveStatus(
        accountKey: MicroBlogKey,
        pagingKey: String,
        database: dev.dimension.flare.data.database.cache.CacheDatabase,
        statuses: List<Status>,
        sortIdProvider: (Status) -> Long = { it.createdAt?.toEpochMilliseconds() ?: 0L },
    ) {
        val timeline = statuses.map { it.toDbPagingTimeline(accountKey, pagingKey, sortIdProvider) }
        val status =
            statuses.flatMap {
                listOfNotNull(
                    it.toDbStatus(accountKey),
                    it.retweetedStatus?.toDbStatus(accountKey),
                )
            }
        val user =
            statuses.flatMap {
                listOfNotNull(
                    it.user?.toDbUser(),
                    it.retweetedStatus?.user?.toDbUser(),
                )
            }
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

    fun saveComment(
        accountKey: MicroBlogKey,
        pagingKey: String,
        database: dev.dimension.flare.data.database.cache.CacheDatabase,
        statuses: List<Comment>,
        sortIdProvider: (Comment) -> Long = { it.createdAt?.toEpochMilliseconds() ?: 0L },
    ) {
        val timeline = statuses.map { it.toDbPagingTimeline(accountKey, pagingKey, sortIdProvider) }
        val status =
            statuses.flatMap {
                listOfNotNull(
                    it.toDbStatus(accountKey),
                )
            }
        val user =
            statuses.flatMap {
                listOfNotNull(
                    it.user?.toDbUser(),
                )
            }
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

private fun Status.toDbPagingTimeline(
    accountKey: MicroBlogKey,
    pagingKey: String,
    sortIdProvider: (Status) -> Long = { it.createdAt?.toEpochMilliseconds() ?: 0L },
): dev.dimension.flare.data.cache.DbPagingTimeline =
    dev.dimension.flare.data.cache.DbPagingTimeline(
        id = 0,
        account_key = accountKey,
        status_key = MicroBlogKey(id = id, host = vvoHost),
        paging_key = pagingKey,
        sort_id = sortIdProvider(this),
    )

private fun Status.toDbStatus(accountKey: MicroBlogKey): dev.dimension.flare.data.cache.DbStatus =
    dev.dimension.flare.data.cache.DbStatus(
        id = 0,
        status_key = MicroBlogKey(id = id, host = vvoHost),
        account_key = accountKey,
        user_key = user?.id?.let { MicroBlogKey(id = it.toString(), host = vvoHost) },
        platform_type = dev.dimension.flare.model.PlatformType.VVo,
        content =
            dev.dimension.flare.data.database.cache.model.StatusContent
                .VVO(data = this),
    )

private fun Comment.toDbPagingTimeline(
    accountKey: MicroBlogKey,
    pagingKey: String,
    sortIdProvider: (Comment) -> Long = { it.createdAt?.toEpochMilliseconds() ?: 0L },
): dev.dimension.flare.data.cache.DbPagingTimeline =
    dev.dimension.flare.data.cache.DbPagingTimeline(
        id = 0,
        account_key = accountKey,
        status_key = MicroBlogKey(id = id, host = vvoHost),
        paging_key = pagingKey,
        sort_id = sortIdProvider(this),
    )

private fun Comment.toDbStatus(accountKey: MicroBlogKey): dev.dimension.flare.data.cache.DbStatus =
    dev.dimension.flare.data.cache.DbStatus(
        id = 0,
        status_key = MicroBlogKey(id = id, host = vvoHost),
        account_key = accountKey,
        user_key = user?.id?.let { MicroBlogKey(id = it.toString(), host = vvoHost) },
        platform_type = dev.dimension.flare.model.PlatformType.VVo,
        content =
            dev.dimension.flare.data.database.cache.model.StatusContent
                .VVOComment(data = this),
    )

internal fun User.toDbUser(): dev.dimension.flare.data.cache.DbUser =
    dev.dimension.flare.data.cache.DbUser(
        handle = screenName,
        host = vvoHost,
        name = screenName,
        user_key = MicroBlogKey(id = id.toString(), host = vvoHost),
        platform_type = dev.dimension.flare.model.PlatformType.VVo,
        content =
            dev.dimension.flare.data.database.cache.model.UserContent
                .VVO(data = this),
    )
