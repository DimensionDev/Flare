package dev.dimension.flare.data.database.cache.mapper

import chat.bsky.convo.ConvoView
import chat.bsky.convo.ConvoViewLastMessageUnion
import chat.bsky.convo.DeletedMessageView
import chat.bsky.convo.MessageView
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbDirectMessageTimeline
import dev.dimension.flare.data.database.cache.model.DbMessageItem
import dev.dimension.flare.data.database.cache.model.DbMessageRoom
import dev.dimension.flare.data.database.cache.model.DbMessageRoomReference
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.UiDMRoom
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.mapper.render
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first

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
                it.lastMessage?.toDbMessageItem(
                    roomKey = it.toDbMessageRoom(accountKey.host).roomKey,
                    users = it.members.associate { member -> member.did.did to member.render(accountKey) },
                    accountKey = accountKey,
                )
            }
        val timeline = data.map { it.toDbDirectMessageTimeline(accountKey) }
        val users =
            data
                .flatMap { it.members }
                .map {
                    it.render(accountKey).toDbUser(host = accountKey.host)
                }
        database.upsertUsers(users)
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
        val users =
            database
                .userDao()
                .findByKeys(data.map { MicroBlogKey(id = it.sender.did.did, host = accountKey.host) })
                .first()
                .associate { it.userKey.id to it.content }
        val messages = data.map { it.toDbMessageItem(roomKey, users, accountKey) }
        database.messageDao().insertMessages(messages)
//        database.messageDao().insert(room)
    }
}

private fun ConvoView.toDbDirectMessageTimeline(accountKey: MicroBlogKey): DbDirectMessageTimeline {
    val roomKey = toDbMessageRoom(accountKey.host).roomKey
    val membersByDid = members.associate { it.did.did to it.render(accountKey) }
    val lastMessageItem =
        lastMessage?.toDbMessageItem(
            roomKey = roomKey,
            users = membersByDid,
            accountKey = accountKey,
        )
    return DbDirectMessageTimeline(
        accountType = AccountType.Specific(accountKey),
        roomKey = roomKey,
        sortId =
            lastMessageItem?.timestamp
                ?: 0L,
        unreadCount = unreadCount,
        content =
            UiDMRoom(
                key = roomKey,
                users =
                    members
                        .filter { it.did.did != accountKey.id }
                        .map { it.render(accountKey) }
                        .toImmutableList(),
                lastMessage = lastMessageItem?.content,
                unreadCount = unreadCount,
            ),
    )
}

private fun ConvoView.toDbMessageRoom(host: String) =
    DbMessageRoom(
        roomKey = MicroBlogKey(id = id, host = host),
        platformType = PlatformType.Bluesky,
        messageKey =
            when (val message = lastMessage) {
                is ConvoViewLastMessageUnion.MessageView -> {
                    MicroBlogKey(
                        id = message.value.id,
                        host = host,
                    )
                }

                is ConvoViewLastMessageUnion.DeletedMessageView -> {
                    MicroBlogKey(
                        id = message.value.id,
                        host = host,
                    )
                }

                null -> {
                    null
                }

                is ConvoViewLastMessageUnion.Unknown -> {
                    null
                }
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

private fun ConvoViewLastMessageUnion.toDbMessageItem(
    roomKey: MicroBlogKey,
    users: Map<String, UiProfile>,
    accountKey: MicroBlogKey,
) = when (this) {
    is ConvoViewLastMessageUnion.MessageView -> value.toDbMessageItem(roomKey, users, accountKey)
    is ConvoViewLastMessageUnion.DeletedMessageView -> value.toDbMessageItem(roomKey, users, accountKey)
    is ConvoViewLastMessageUnion.Unknown -> null
}

private fun MessageView.toDbMessageItem(
    roomKey: MicroBlogKey,
    users: Map<String, UiProfile>,
    accountKey: MicroBlogKey,
): DbMessageItem {
    val messageKey = MicroBlogKey(id = id, host = roomKey.host)
    val userKey = MicroBlogKey(id = sender.did.did, host = roomKey.host)
    return DbMessageItem(
        messageKey = messageKey,
        roomKey = roomKey,
        userKey = userKey,
        timestamp = sentAt.toEpochMilliseconds(),
        content =
            UiDMItem(
                key = messageKey,
                user = users[sender.did.did] ?: fallbackDirectMessageUser(userKey),
                timestamp = sentAt.toUi(),
                content = render(accountKey),
                isFromMe = userKey == accountKey,
                sendState = null,
                showSender = false,
            ),
        showSender = false,
        remoteCursor = rev,
    )
}

private fun DeletedMessageView.toDbMessageItem(
    roomKey: MicroBlogKey,
    users: Map<String, UiProfile>,
    accountKey: MicroBlogKey,
) = run {
    val messageKey = MicroBlogKey(id = id, host = roomKey.host)
    val userKey = MicroBlogKey(id = sender.did.did, host = roomKey.host)
    DbMessageItem(
        messageKey = messageKey,
        roomKey = roomKey,
        userKey = userKey,
        timestamp = sentAt.toEpochMilliseconds(),
        content =
            UiDMItem(
                key = messageKey,
                user = users[sender.did.did] ?: fallbackDirectMessageUser(userKey),
                timestamp = sentAt.toUi(),
                content = render(),
                isFromMe = userKey == accountKey,
                sendState = null,
                showSender = false,
            ),
        showSender = false,
    )
}

private fun fallbackDirectMessageUser(userKey: MicroBlogKey): UiProfile =
    UiProfile(
        key = userKey,
        handle = UiHandle(raw = userKey.id, host = userKey.host),
        avatar = "",
        nameInternal = userKey.id.toUiPlainText(),
        platformType = PlatformType.Bluesky,
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
