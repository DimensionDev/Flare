package dev.dimension.flare.data.database.cache.mapper

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
import dev.dimension.flare.data.network.xqt.model.TweetWithVisibilityResults
import dev.dimension.flare.data.network.xqt.model.User
import dev.dimension.flare.data.network.xqt.model.UserLegacy
import dev.dimension.flare.data.network.xqt.model.UserResultCore
import dev.dimension.flare.data.network.xqt.model.UserResults
import dev.dimension.flare.data.network.xqt.model.UserUnavailable
import dev.dimension.flare.data.network.xqt.model.legacy.TopLevel
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.UiDMRoom
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.mapper.render
import dev.dimension.flare.ui.model.mapper.renderDirectMessage
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.time.Instant

public object XQT {
    public fun rooms(
        accountKey: MicroBlogKey,
        propertyEntries: List<InboxTimelineEntry>?,
        users: Map<String, InboxUser>?,
        conversations: Map<String, InboxConversation>?,
    ): List<UiDMRoom> {
        val trustedConversations =
            conversations?.values.orEmpty().filter { it.trusted == true }
        val userProfiles =
            users
                ?.values
                .orEmpty()
                .mapNotNull { it.toUiProfile(accountKey) }
        val userProfilesById = userProfiles.associateBy { it.key.id }
        return trustedConversations.map { conversation ->
            val roomKey = MicroBlogKey(conversation.conversationId.orEmpty(), accountKey.host)
            val roomMessages =
                propertyEntries
                    ?.filter {
                        it.message?.conversationId == conversation.conversationId
                    }.orEmpty()
                    .mapNotNull {
                        it.toUiDMItem(
                            accountKey,
                            users = userProfilesById,
                            showSender = conversation.participants.orEmpty().size > 2,
                        )
                    }
            val unreadCount =
                roomMessages
                    .count { message ->
                        message.key.id
                            .toLongOrNull()
                            ?.let { id ->
                                conversation.lastReadEventId
                                    ?.toLongOrNull()
                                    ?.let { lastRead -> id > lastRead }
                            } == true
                    }.toLong()
            val lastMessage =
                roomMessages
                    .filter { it.key.id == conversation.maxEntryId }
                    .maxByOrNull { it.timestamp.value }
                    ?: roomMessages.maxByOrNull { it.timestamp.value }
            UiDMRoom(
                key = roomKey,
                users =
                    conversation.participants
                        .orEmpty()
                        .mapNotNull { userProfilesById[it.userId] }
                        .filter { it.key != accountKey }
                        .toImmutableList(),
                lastMessage = lastMessage,
                unreadCount = unreadCount,
            )
        }
    }

    public fun messages(
        accountKey: MicroBlogKey,
        propertyEntries: List<InboxTimelineEntry>?,
        users: Map<String, InboxUser>?,
        conversations: Map<String, InboxConversation>?,
    ): List<UiDMItem> {
        val trustedConversations =
            conversations?.values.orEmpty().filter { it.trusted == true }
        val userProfilesById =
            users
                ?.values
                .orEmpty()
                .mapNotNull { it.toUiProfile(accountKey) }
                .associateBy { it.key.id }
        return trustedConversations
            .flatMap { conversation ->
                propertyEntries
                    ?.filter {
                        it.message?.conversationId == conversation.conversationId
                    }.orEmpty()
                    .mapNotNull {
                        it.toUiDMItem(
                            accountKey,
                            users = userProfilesById,
                            showSender = conversation.participants.orEmpty().size > 2,
                        )
                    }
            }
    }
}

private fun InboxUser.toUiProfile(accountKey: MicroBlogKey): UiProfile? {
    if (name == null || screenName == null || idStr == null) {
        return null
    }
    return User(
        legacy =
            with(this) {
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
        isBlueVerified = isBlueVerified == true,
        restId = idStr,
    ).render(accountKey)
}

private fun InboxTimelineEntry.toUiDMItem(
    accountKey: MicroBlogKey,
    users: Map<String, UiProfile>,
    showSender: Boolean,
): UiDMItem? {
    if (message == null) {
        return null
    }
    val messageData = message.messageData ?: return null
    val messageKey = MicroBlogKey(message.id ?: return null, accountKey.host)
    val userKey = MicroBlogKey(messageData.senderId ?: return null, accountKey.host)
    val timestamp = message.time?.toLongOrNull() ?: 0L
    return UiDMItem(
        key = messageKey,
        user = users[userKey.id] ?: fallbackDirectMessageUser(userKey),
        content = messageData.renderDirectMessage(accountKey),
        timestamp = Instant.fromEpochMilliseconds(timestamp).toUi(),
        isFromMe = userKey == accountKey,
        sendState = null,
        showSender = showSender && userKey != accountKey,
    )
}

private fun fallbackDirectMessageUser(userKey: MicroBlogKey): UiProfile =
    UiProfile(
        key = userKey,
        handle = UiHandle(raw = userKey.id, host = userKey.host),
        avatar = "",
        nameInternal = userKey.id.toUiPlainText(),
        platformType = PlatformType.xQt,
        clickEvent = ClickEvent.Noop,
        banner = null,
        description = null,
        matrices =
            UiProfile.Matrices(
                fansCount = 0,
                followsCount = 0,
                statusesCount = 0,
                platformFansCount = null,
            ),
        mark = persistentListOf(),
        bottomContent = null,
    )

public data class XQTTimeline(
    val parents: List<XQTTimeline>,
    val tweets: TimelineTweet,
    val id: String?,
    val sortedIndex: Long,
)

public fun List<InstructionUnion>.tweets(includePin: Boolean = true): List<XQTTimeline> =
    flatMap { union ->
        when (union) {
            is TimelineAddEntries -> {
                union.propertyEntries
            }

            is TimelinePinEntry -> {
                if (!includePin) {
                    emptyList()
                } else {
                    listOf(union.entry)
                }
            }

            is TimelineAddToModule -> {
                union.moduleItems.mapNotNull {
                    val itemContent = it.item.itemContent
                    if (itemContent !is TimelineTweet) {
                        return@mapNotNull null
                    }
                    val sortIndex =
                        when (val result = itemContent.tweetResults.result) {
                            is Tweet -> result.restId
                            is TweetWithVisibilityResults -> result.tweet.restId
                            else -> return@mapNotNull null
                        }
                    TimelineAddEntry(
                        content =
                            TimelineTimelineModule(
                                items = listOf(it),
                            ),
                        entryId = it.entryId,
                        sortIndex = sortIndex,
                    )
                }
            }

            else -> {
                emptyList()
            }
        }
    }.flatMap { entry ->
        when (entry.content) {
            is TimelineTimelineCursor -> {
                listOf()
            }

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
                        entry.content.items
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
                            }.groupBy {
                                when (it.tweets.tweetResults.result) {
                                    is Tweet -> {
                                        it.tweets.tweetResults.result.legacy
                                            ?.conversationIdStr
                                    }

                                    is TweetTombstone -> {
                                        null
                                    }

                                    is TweetWithVisibilityResults -> {
                                        it.tweets.tweetResults.result.tweet.legacy
                                            ?.conversationIdStr
                                    }

                                    null -> {
                                        null
                                    }
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
                    }
                }
            }

            null -> {
                listOf()
            }
        }
    }.filter {
        it.tweets.promotedMetadata == null
    }

public fun List<InstructionUnion>.cursor(): String? =
    flatMap {
        when (it) {
            is TimelineAddEntries -> {
                it.propertyEntries.mapNotNull {
                    when (it.content) {
                        is TimelineTimelineCursor -> {
                            if (it.content.cursorType == CursorType.bottom) {
                                it.content.value
                            } else {
                                null
                            }
                        }

                        else -> {
                            null
                        }
                    }
                }
            }

            else -> {
                emptyList()
            }
        }
    }.firstOrNull()

public fun List<InstructionUnion>.isBottomEnd(): Boolean =
    filterIsInstance<TimelineTerminateTimeline>()
        .firstOrNull {
            it.direction == TimelineTerminateTimeline.Direction.bottom || it.direction == TimelineTerminateTimeline.Direction.topAndBottom
        } != null

public fun TopLevel.tweets(): List<XQTTimeline> =
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

public fun List<InstructionUnion>.users(): List<User> =
    flatMap { union ->
        when (union) {
            is TimelineAddEntries -> {
                union.propertyEntries
                    .flatMap { entry ->
                        when (entry.content) {
                            null -> {
                                emptyList()
                            }

                            is TimelineTimelineCursor -> {
                                emptyList()
                            }

                            is TimelineTimelineItem -> {
                                listOf(entry.content.itemContent)
                            }

                            is TimelineTimelineModule -> {
                                entry.content.items
                                    ?.map {
                                        it.item.itemContent
                                    }.orEmpty()
                            }
                        }
                    }
            }

            else -> {
                emptyList()
            }
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

public fun TopLevel.cursor(type: CursorType = CursorType.bottom): String? =
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
