package dev.dimension.flare.data.database.cache.mapper

import dev.dimension.flare.data.cache.DbPagingTimeline
import dev.dimension.flare.data.cache.DbStatus
import dev.dimension.flare.data.cache.DbUser
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.data.database.cache.model.UserContent
import dev.dimension.flare.data.network.xqt.model.CursorType
import dev.dimension.flare.data.network.xqt.model.InstructionUnion
import dev.dimension.flare.data.network.xqt.model.ItemResult
import dev.dimension.flare.data.network.xqt.model.TimelineAddEntries
import dev.dimension.flare.data.network.xqt.model.TimelinePinEntry
import dev.dimension.flare.data.network.xqt.model.TimelineTimelineCursor
import dev.dimension.flare.data.network.xqt.model.TimelineTimelineItem
import dev.dimension.flare.data.network.xqt.model.TimelineTimelineModule
import dev.dimension.flare.data.network.xqt.model.TimelineTweet
import dev.dimension.flare.data.network.xqt.model.TimelineUser
import dev.dimension.flare.data.network.xqt.model.Tweet
import dev.dimension.flare.data.network.xqt.model.TweetTombstone
import dev.dimension.flare.data.network.xqt.model.TweetWithVisibilityResults
import dev.dimension.flare.data.network.xqt.model.User
import dev.dimension.flare.data.network.xqt.model.UserResultCore
import dev.dimension.flare.data.network.xqt.model.UserResults
import dev.dimension.flare.data.network.xqt.model.UserUnavailable
import dev.dimension.flare.data.network.xqt.model.legacy.TopLevel
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.xqtHost

object XQT {
    fun save(
        accountKey: MicroBlogKey,
        pagingKey: String,
        database: dev.dimension.flare.data.database.cache.CacheDatabase,
        tweet: List<XQTTimeline>,
        sortIdProvider: (XQTTimeline) -> Long = { it.sortedIndex },
    ) {
        val timeline = tweet.map { it.toDbPagingTimeline(accountKey, pagingKey, sortIdProvider) }
        val status = tweet.map { it.tweets.toDbStatus(accountKey) }
        val user = tweet.map { it.tweets.toDbUser() }
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

internal fun XQTTimeline.toDbPagingTimeline(
    accountKey: MicroBlogKey,
    pagingKey: String,
    sortIdProvider: (XQTTimeline) -> Long = { sortedIndex },
): DbPagingTimeline {
    val status = tweets.toDbStatus(accountKey)
    return DbPagingTimeline(
        id = 0,
        account_key = accountKey,
        paging_key = pagingKey,
        status_key = status.status_key,
        sort_id = sortIdProvider(this),
    )
}

internal fun TimelineTweet.toDbStatus(accountKey: MicroBlogKey): DbStatus {
    val tweet =
        when (tweetResults.result) {
            is Tweet -> tweetResults.result
            null, is TweetTombstone -> throw IllegalStateException("Tweet tombstone should not be saved")
            is TweetWithVisibilityResults -> tweetResults.result.tweet
        }
    val user =
        tweet.core
            ?.userResults
            ?.result
            ?.let {
                it as? User
            }
            ?.toDbUser() ?: throw IllegalStateException("Tweet.user should not be null")
    return DbStatus(
        id = 0,
        status_key =
            MicroBlogKey(
                id = tweet.restId,
                host = user.user_key.host,
            ),
        platform_type = PlatformType.xQt,
        content = StatusContent.XQT(tweet),
        user_key = user.user_key,
        account_key = accountKey,
    )
}

private fun TimelineTweet.toDbUser(): DbUser {
    val tweet =
        when (tweetResults.result) {
            is Tweet -> tweetResults.result
            null, is TweetTombstone -> throw IllegalStateException("Tweet tombstone should not be saved")
            is TweetWithVisibilityResults -> tweetResults.result.tweet
        }
    val user =
        tweet.core
            ?.userResults
            ?.result
            ?.let {
                it as? User
            }
            ?: throw IllegalStateException("Tweet.user should not be null")
    return user.toDbUser()
}

internal fun User.toDbUser() =
    DbUser(
        user_key =
            MicroBlogKey(
                id = restId,
                host = xqtHost,
            ),
        platform_type = PlatformType.xQt,
        name = legacy.name,
        handle = legacy.screenName,
        host = xqtHost,
        content = UserContent.XQT(this),
    )

data class XQTTimeline(
    val tweets: TimelineTweet,
    val id: String?,
    val sortedIndex: Long,
)

internal fun List<InstructionUnion>.tweets(): List<XQTTimeline> =
    flatMap { union ->
        when (union) {
            is TimelineAddEntries ->
                union.propertyEntries

            is TimelinePinEntry ->
                listOf(union.entry)

            else -> emptyList()
        }
    }.flatMap { entry ->
        // * 100 for sorted index for module entries sorting
        when (entry.content) {
            is TimelineTimelineCursor -> emptyList()
            is TimelineTimelineItem -> listOf(entry.content.itemContent to entry.sortIndex.toLong() * 100)
            is TimelineTimelineModule ->
                entry.content.items?.mapIndexed { index, it ->
                    it.item.itemContent to ((entry.sortIndex.toLong() * 100) - index)
                }.orEmpty()
        }
    }.mapNotNull { pair ->
        pair.first.let {
            when (it) {
                is TimelineTweet -> {
                    XQTTimeline(
                        tweets = it,
                        sortedIndex = pair.second,
                        id =
                            when (it.tweetResults.result) {
                                is Tweet -> it.tweetResults.result.restId
                                is TweetTombstone -> null
                                is TweetWithVisibilityResults -> it.tweetResults.result.tweet.restId
                                null -> null
                            },
                    )
                }

                else -> null
            }
        }
    }.filter {
        it.tweets.promotedMetadata == null
    }

internal fun List<InstructionUnion>.cursor() =
    flatMap {
        when (it) {
            is TimelineAddEntries ->
                it.propertyEntries.mapNotNull {
                    when (it.content) {
                        is TimelineTimelineCursor ->
                            if (it.content.cursorType == CursorType.bottom) {
                                it.content.value
                            } else {
                                null
                            }

                        else -> null
                    }
                }

            else -> emptyList()
        }
    }.firstOrNull()

internal fun TopLevel.tweets(): List<XQTTimeline> {
    return timeline
        ?.instructions
        ?.asSequence()
        ?.flatMap {
            it.addEntries?.entries.orEmpty()
        }
        ?.mapNotNull { entry ->
            val id = entry.content?.item?.content?.tweet?.id
            val index = entry.sortIndex?.toLong()
            if (id != null && index != null) {
                id to index
            } else {
                null
            }
        }
        ?.mapNotNull { (id, index) ->
            globalObjects?.tweets?.get(id)?.let {
                it to index
            }
        }
        ?.map { (tweetLegacy, index) ->
            // build tweet
            Tweet(
                restId = tweetLegacy.idStr,
//
                core =
                    globalObjects?.users?.get(tweetLegacy.userIdStr)?.let {
                        UserResultCore(
                            userResults =
                                UserResults(
                                    result =
                                        User(
                                            legacy = it,
                                            isBlueVerified = it.verified,
                                            restId = tweetLegacy.userIdStr,
                                        ),
                                ),
                        )
                    },
                legacy = tweetLegacy,
            ) to index
        }
        ?.map { (tweet, index) ->
            XQTTimeline(
                tweets =
                    TimelineTweet(
                        tweetResults =
                            ItemResult(
                                result = tweet,
                            ),
                    ),
                id = tweet.restId,
                sortedIndex = index,
            )
        }
        ?.toList()
        .orEmpty()
}

internal fun List<InstructionUnion>.users(): List<User> =
    flatMap { union ->
        when (union) {
            is TimelineAddEntries ->
                union.propertyEntries
                    .flatMap { entry ->
                        when (entry.content) {
                            is TimelineTimelineCursor -> emptyList()
                            is TimelineTimelineItem -> listOf(entry.content.itemContent)
                            is TimelineTimelineModule ->
                                entry.content.items?.map {
                                    it.item.itemContent
                                }.orEmpty()
                        }
                    }

            else -> emptyList()
        }
    }
        .mapNotNull { content ->
            when (content) {
                is TimelineUser -> content.userResults.result
                else -> null
            }
        }
        .mapNotNull {
            when (it) {
                is User -> it
                is UserUnavailable -> null
            }
        }

internal fun TopLevel.cursor(): String? =
    timeline
        ?.instructions
        ?.asSequence()
        ?.flatMap {
            it.addEntries?.entries.orEmpty()
        }
        ?.mapNotNull {
            it.content?.operation?.cursor
        }
        ?.filter {
            it.cursorType == CursorType.bottom
        }
        ?.map {
            it.value
        }
        ?.firstOrNull()
