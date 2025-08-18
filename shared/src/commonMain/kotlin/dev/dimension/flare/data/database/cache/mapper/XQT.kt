package dev.dimension.flare.data.database.cache.mapper

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
import dev.dimension.flare.data.database.cache.model.UserContent
import dev.dimension.flare.data.network.xqt.model.CursorType
import dev.dimension.flare.data.network.xqt.model.InboxConversation
import dev.dimension.flare.data.network.xqt.model.InboxTimelineEntry
import dev.dimension.flare.data.network.xqt.model.InboxUser
import dev.dimension.flare.data.network.xqt.model.InstructionUnion
import dev.dimension.flare.data.network.xqt.model.ItemResult
import dev.dimension.flare.data.network.xqt.model.TimelineAddEntries
import dev.dimension.flare.data.network.xqt.model.TimelineAddEntry
import dev.dimension.flare.data.network.xqt.model.TimelineAddToModule
import dev.dimension.flare.data.network.xqt.model.TimelinePinEntry
import dev.dimension.flare.data.network.xqt.model.TimelineTerminateTimeline
import dev.dimension.flare.data.network.xqt.model.TimelineTimelineCursor
import dev.dimension.flare.data.network.xqt.model.TimelineTimelineItem
import dev.dimension.flare.data.network.xqt.model.TimelineTimelineModule
import dev.dimension.flare.data.network.xqt.model.TimelineTweet
import dev.dimension.flare.data.network.xqt.model.TimelineUser
import dev.dimension.flare.data.network.xqt.model.Tweet
import dev.dimension.flare.data.network.xqt.model.TweetTombstone
import dev.dimension.flare.data.network.xqt.model.TweetUnion
import dev.dimension.flare.data.network.xqt.model.TweetWithVisibilityResults
import dev.dimension.flare.data.network.xqt.model.User
import dev.dimension.flare.data.network.xqt.model.UserLegacy
import dev.dimension.flare.data.network.xqt.model.UserResultCore
import dev.dimension.flare.data.network.xqt.model.UserResults
import dev.dimension.flare.data.network.xqt.model.UserUnavailable
import dev.dimension.flare.data.network.xqt.model.legacy.TopLevel
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.ui.model.mapper.name
import dev.dimension.flare.ui.model.mapper.parseXQTCustomDateTime
import dev.dimension.flare.ui.model.mapper.screenName
import kotlin.collections.toMap
import kotlin.time.Clock

internal object XQT {
    suspend fun save(
        accountKey: MicroBlogKey,
        pagingKey: String,
        database: CacheDatabase,
        tweet: List<XQTTimeline>,
        sortIdProvider: (XQTTimeline) -> Long = { it.sortedIndex },
    ) {
        val items =
            tweet.mapNotNull { it.toDbPagingTimeline(accountKey, pagingKey, sortIdProvider) }
        saveToDatabase(database, items)
    }

    suspend fun saveDM(
        accountKey: MicroBlogKey,
        database: CacheDatabase,
        propertyEntries: List<InboxTimelineEntry>?,
        users: Map<String, InboxUser>?,
        conversations: Map<String, InboxConversation>?,
        updateRoom: Boolean = true,
    ) {
        val trustedConversations =
            conversations?.values.orEmpty().filter { it.trusted == true }
        val references =
            trustedConversations.flatMap { conversation ->
                conversation.participants.orEmpty().map {
                    DbMessageRoomReference(
                        roomKey =
                            MicroBlogKey(
                                conversation.conversationId.orEmpty(),
                                accountKey.host,
                            ),
                        userKey = MicroBlogKey(it.userId.orEmpty(), accountKey.host),
                    )
                }
            }
        val messages =
            trustedConversations
                .flatMap { conversation ->
                    propertyEntries
                        ?.filter {
                            it.message?.conversationId == conversation.conversationId
                        }.orEmpty()
                        .map {
                            it.toDbMessageItem(
                                accountKey,
                                showSender = conversation.participants.orEmpty().size > 2,
                            )
                        }
                }.mapNotNull {
                    it
                }
        val timeline =
            trustedConversations.map { conversation ->
                DbDirectMessageTimeline(
                    accountType = AccountType.Specific(accountKey),
                    roomKey = MicroBlogKey(conversation.conversationId.orEmpty(), accountKey.host),
                    sortId = conversation.sortTimestamp?.toLongOrNull() ?: 0L,
                    unreadCount =
                        messages
                            .filter {
                                it.roomKey.id == conversation.conversationId
                            }.count { message ->
                                message.messageKey.id
                                    .toLongOrNull()
                                    ?.let { id ->
                                        conversation.lastReadEventId
                                            ?.toLongOrNull()
                                            ?.let { lastRead -> id > lastRead }
                                    } == true
                            }.toLong(),
                )
            }
        if (updateRoom) {
            val rooms =
                trustedConversations.map {
                    DbMessageRoom(
                        roomKey = MicroBlogKey(it.conversationId.orEmpty(), accountKey.host),
                        platformType = PlatformType.xQt,
                        messageKey =
                            propertyEntries
                                ?.firstOrNull { entries -> entries.message?.id == it.lastReadEventId }
                                ?.let {
                                    MicroBlogKey(it.message?.id.orEmpty(), accountKey.host)
                                },
                    )
                }
            database.messageDao().insert(rooms)
        }
        database.userDao().insertAll(
            users
                ?.values
                .orEmpty()
                .toList()
                .toDbUser(accountKey),
        )
        database.messageDao().insertMessages(messages)
        database.messageDao().insertReferences(references)
        database.messageDao().insertTimeline(timeline)
    }
}

private fun List<InboxUser>.toDbUser(accountKey: MicroBlogKey): List<DbUser> {
    return mapNotNull {
        if (it.name == null || it.screenName == null || it.idStr == null) {
            return@mapNotNull null
        }
        User(
            legacy =
                with(it) {
                    UserLegacy(
                        name = name.orEmpty(),
                        screenName = screenName.orEmpty(),
                        location = location,
                        description = description,
                        url = url,
                        entities = entities,
                        `protected` = `protected`,
                        followersCount = followersCount ?: 0,
                        friendsCount = friendsCount ?: 0,
                        listedCount = listedCount ?: 0,
                        createdAt = createdAt.orEmpty(),
                        favouritesCount = favouritesCount ?: 0,
                        verified = verified == true,
                        statusesCount = statusesCount ?: 0,
                        isTranslator = isTranslator == true,
                        profileImageUrlHttps = profileImageUrlHttps.orEmpty(),
                        profileBannerUrl = profileBannerUrl,
                        translatorType = translatorType.orEmpty(),
                    )
                },
            isBlueVerified = it.isBlueVerified == true,
            restId = it.idStr,
        ).toDbUser(accountKey)
    }
}

private fun InboxTimelineEntry.toDbMessageItem(
    accountKey: MicroBlogKey,
    showSender: Boolean,
): DbMessageItem? {
    if (message == null) {
        return null
    }
    return DbMessageItem(
        messageKey = MicroBlogKey(message.id ?: return null, accountKey.host),
        roomKey = MicroBlogKey(message.conversationId ?: return null, accountKey.host),
        content = MessageContent.XQT.Message(message.messageData ?: return null),
        timestamp = message.time?.toLongOrNull() ?: 0L,
        userKey = MicroBlogKey(message.messageData.senderId ?: return null, accountKey.host),
        showSender = showSender,
    )
}

private fun TweetUnion.getRetweet(): TweetUnion? =
    when (this) {
        is Tweet -> this.legacy?.retweetedStatusResult?.result
        is TweetTombstone -> null
        is TweetWithVisibilityResults ->
            this.tweet.legacy
                ?.retweetedStatusResult
                ?.result
    }

private fun TweetUnion.getQuoted(): TweetUnion? =
    when (this) {
        is Tweet -> this.quotedStatusResult?.result
        is TweetTombstone -> null
        is TweetWithVisibilityResults -> this.tweet.quotedStatusResult?.result
    }

internal fun XQTTimeline.toDbPagingTimeline(
    accountKey: MicroBlogKey,
    pagingKey: String,
    sortIdProvider: (XQTTimeline) -> Long = { sortedIndex },
): DbPagingTimelineWithStatus? =
    tweets.toDbStatusWithUser(accountKey)?.let { tweet ->
        createDbPagingTimelineWithStatus(
            accountKey = accountKey,
            pagingKey = pagingKey,
            sortId = sortIdProvider(this),
            status = tweet,
            references =
                listOfNotNull(
                    tweets.tweetResults.result?.getRetweet()?.toDbStatusWithUser(accountKey)?.let {
                        ReferenceType.Retweet to listOfNotNull(it)
                    },
                    (
                        tweets.tweetResults.result
                            ?.getRetweet()
                            ?.getQuoted() ?: tweets.tweetResults.result?.getQuoted()
                    )?.toDbStatusWithUser(accountKey)
                        ?.let {
                            ReferenceType.Quote to listOfNotNull(it)
                        },
                    parents
                        .mapNotNull { it.tweets.toDbStatusWithUser(accountKey) }
                        .takeIf { it.isNotEmpty() }
                        ?.let { ReferenceType.Reply to it },
                ).toMap(),
        )
    }

private fun TimelineTweet.toDbStatusWithUser(accountKey: MicroBlogKey): DbStatusWithUser? =
    tweetResults.result?.toDbStatusWithUser(accountKey)

private fun TweetUnion.toDbStatusWithUser(accountKey: MicroBlogKey): DbStatusWithUser? =
    when (this) {
        is Tweet -> toDbStatusWithUser(this, accountKey)
        // You’re unable to view this Post because
        // this account owner limits who can view their Posts. Learn more
        // throw IllegalStateException("Tweet tombstone should not be saved")
        is TweetTombstone -> null
        is TweetWithVisibilityResults -> toDbStatusWithUser(this.tweet, accountKey)
    }

private fun toDbStatusWithUser(
    tweet: Tweet,
    accountKey: MicroBlogKey,
): DbStatusWithUser {
    val user =
        tweet.core
            ?.userResults
            ?.result
            ?.let {
                it as? User
            }?.toDbUser(accountKey) ?: throw IllegalStateException("Tweet.user should not be null")
    return DbStatusWithUser(
        data =
            DbStatus(
                statusKey =
                    MicroBlogKey(
                        id = tweet.restId,
                        host = user.userKey.host,
                    ),
                content = StatusContent.XQT(tweet),
                userKey = user.userKey,
                accountType = AccountType.Specific(accountKey),
                text = tweet.legacy?.fullText,
                createdAt =
                    tweet.legacy?.createdAt?.let { parseXQTCustomDateTime(it) }
                        ?: Clock.System.now(),
            ),
        user = user,
    )
}

private fun TimelineTweet.toDbUser(accountKey: MicroBlogKey): DbUser {
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
    return user.toDbUser(accountKey = accountKey)
}

internal fun User.toDbUser(accountKey: MicroBlogKey) =
    DbUser(
        userKey =
            MicroBlogKey(
                id = restId,
                host = accountKey.host,
            ),
        platformType = PlatformType.xQt,
        name = name,
        handle = screenName,
        host = accountKey.host,
        content = UserContent.XQT(this),
    )

internal data class XQTTimeline(
    val parents: List<XQTTimeline>,
    val tweets: TimelineTweet,
    val id: String?,
    val sortedIndex: Long,
)

internal fun List<InstructionUnion>.tweets(includePin: Boolean = true): List<XQTTimeline> =
    flatMap { union ->
        when (union) {
            is TimelineAddEntries ->
                union.propertyEntries

            is TimelinePinEntry ->
                if (!includePin) {
                    emptyList()
                } else {
                    listOf(union.entry)
                }

            is TimelineAddToModule ->
                union.moduleItems.mapNotNull {
                    if (it.item.itemContent is TimelineTweet &&
                        it.item.itemContent.tweetResults.result is Tweet
                    ) {
                        TimelineAddEntry(
                            content =
                                TimelineTimelineModule(
                                    items =
                                        listOf(
                                            it,
                                        ),
                                ),
                            entryId = it.entryId,
                            sortIndex = it.item.itemContent.tweetResults.result.restId,
                        )
                    } else {
                        null
                    }
                }

            else -> emptyList()
        }
    }.flatMap { entry ->
        when (entry.content) {
            is TimelineTimelineCursor -> listOf()
            is TimelineTimelineItem -> {
                listOfNotNull(
                    if (entry.content.itemContent is TimelineTweet) {
                        XQTTimeline(
                            tweets = entry.content.itemContent,
                            sortedIndex = entry.sortIndex.toLong(),
                            id =
                                when (entry.content.itemContent.tweetResults.result) {
                                    is Tweet -> entry.content.itemContent.tweetResults.result.restId
                                    is TweetTombstone -> null
                                    is TweetWithVisibilityResults -> entry.content.itemContent.tweetResults.result.tweet.restId
                                    null -> null
                                },
                            parents = emptyList(),
                        )
                    } else {
                        null
                    },
                )
            }

            is TimelineTimelineModule -> {
                if (entry.content.items == null) {
                    listOf()
                } else {
                    if (entry.content.items.size == 1) {
                        val item =
                            entry.content.items
                                .first()
                                .item.itemContent
                        if (item is TimelineTweet) {
                            listOf(
                                XQTTimeline(
                                    tweets = item,
                                    sortedIndex = entry.sortIndex.toLong(),
                                    id =
                                        when (item.tweetResults.result) {
                                            is Tweet -> item.tweetResults.result.restId
                                            is TweetTombstone -> null
                                            is TweetWithVisibilityResults -> item.tweetResults.result.tweet.restId
                                            null -> null
                                        },
                                    parents = emptyList(),
                                ),
                            )
                        } else {
                            listOf()
                        }
                    } else {
                        val items =
                            entry.content.items
//                                .take(entry.content.items.size - 1)
                                .mapNotNull {
                                    if (it.item.itemContent is TimelineTweet) {
                                        XQTTimeline(
                                            tweets = it.item.itemContent,
                                            sortedIndex = entry.sortIndex.toLong(),
                                            id =
                                                when (it.item.itemContent.tweetResults.result) {
                                                    is Tweet -> it.item.itemContent.tweetResults.result.restId
                                                    is TweetTombstone -> null
                                                    is TweetWithVisibilityResults -> it.item.itemContent.tweetResults.result.tweet.restId
                                                    null -> null
                                                },
                                            parents = emptyList(),
                                        )
                                    } else {
                                        null
                                    }
                                }
                        items
                            .groupBy {
                                when (it.tweets.tweetResults.result) {
                                    is Tweet ->
                                        it.tweets.tweetResults.result.legacy
                                            ?.conversationIdStr
                                    is TweetTombstone -> null
                                    is TweetWithVisibilityResults ->
                                        it.tweets.tweetResults.result.tweet.legacy
                                            ?.conversationIdStr
                                    null -> null
                                }
                            }.map { (_, items) ->
                                val parents = items.take(items.size - 1)
                                val item = items.last()
                                XQTTimeline(
                                    parents = parents,
                                    tweets = item.tweets,
                                    sortedIndex = item.sortedIndex,
                                    id =
                                        when (val item = item.tweets.tweetResults.result) {
                                            is Tweet -> item.restId
                                            is TweetTombstone -> null
                                            is TweetWithVisibilityResults -> item.tweet.restId
                                            null -> null
                                        },
                                )
                            }
//                        val lastItem =
//                            entry.content.items
//                                .last()
//                                .item.itemContent
//                        if (lastItem is TimelineTweet) {
//                            XQTTimeline(
//                                tweets = lastItem,
//                                sortedIndex = entry.sortIndex.toLong(),
//                                id =
//                                    when (lastItem.tweetResults.result) {
//                                        is Tweet -> lastItem.tweetResults.result.restId
//                                        is TweetTombstone -> null
//                                        is TweetWithVisibilityResults -> lastItem.tweetResults.result.tweet.restId
//                                        null -> null
//                                    },
//                                parents = parent,
//                            )
//                        } else {
//                            null
//                        }
                    }
                }
            }

            null -> listOf()
        }
//        pair.first.let {
//            when (it) {
//                is TimelineTweet -> {
//                    XQTTimeline(
//                        tweets = it,
//                        sortedIndex = pair.second,
//                        id =
//                            when (it.tweetResults.result) {
//                                is Tweet -> it.tweetResults.result.restId
//                                is TweetTombstone -> null
//                                is TweetWithVisibilityResults -> it.tweetResults.result.tweet.restId
//                                null -> null
//                            },
//                    )
//                }
//
//                else -> null
//            }
//        }
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

internal fun List<InstructionUnion>.isBottomEnd() =
    filterIsInstance<TimelineTerminateTimeline>()
        .firstOrNull {
            it.direction == TimelineTerminateTimeline.Direction.bottom || it.direction == TimelineTerminateTimeline.Direction.topAndBottom
        } != null

internal fun TopLevel.tweets(): List<XQTTimeline> =
    timeline
        ?.instructions
        ?.asSequence()
        ?.flatMap {
            it.addEntries?.entries.orEmpty()
        }?.mapNotNull { entry ->
            val id =
                entry.content
                    ?.item
                    ?.content
                    ?.tweet
                    ?.id
            val index = entry.sortIndex?.toLong()
            if (id != null && index != null) {
                id to index
            } else {
                null
            }
        }?.mapNotNull { (id, index) ->
            globalObjects?.tweets?.get(id)?.let {
                it to index
            }
        }?.map { (tweetLegacy, index) ->
            // build tweet
            Tweet(
                restId = tweetLegacy.idStr,
                core =
                    tweetLegacy.userIdStr?.let {
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
                        }
                    },
                legacy = tweetLegacy,
            ) to index
        }?.map { (tweet, index) ->
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
                parents = emptyList(),
            )
        }?.toList()
        .orEmpty()

internal fun List<InstructionUnion>.users(): List<User> =
    flatMap { union ->
        when (union) {
            is TimelineAddEntries ->
                union.propertyEntries
                    .flatMap { entry ->
                        when (entry.content) {
                            null -> emptyList()
                            is TimelineTimelineCursor -> emptyList()
                            is TimelineTimelineItem -> listOf(entry.content.itemContent)
                            is TimelineTimelineModule ->
                                entry.content.items
                                    ?.map {
                                        it.item.itemContent
                                    }.orEmpty()
                        }
                    }

            else -> emptyList()
        }
    }.mapNotNull { content ->
        when (content) {
            is TimelineUser -> content.userResults.result
            else -> null
        }
    }.mapNotNull {
        when (it) {
            is User -> it
            is UserUnavailable -> null
        }
    }

internal fun TopLevel.cursor(type: CursorType = CursorType.bottom): String? =
    timeline
        ?.instructions
        ?.asSequence()
        ?.flatMap {
            it.addEntries?.entries.orEmpty()
        }?.mapNotNull {
            it.content?.operation?.cursor
        }?.filter {
            it.cursorType == type
        }?.map {
            it.value
        }?.firstOrNull()
