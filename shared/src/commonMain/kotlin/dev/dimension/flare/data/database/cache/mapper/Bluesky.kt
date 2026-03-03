package dev.dimension.flare.data.database.cache.mapper

import chat.bsky.convo.ConvoView
import chat.bsky.convo.ConvoViewLastMessageUnion
import chat.bsky.convo.MessageView
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.model.DbDirectMessageTimeline
import dev.dimension.flare.data.database.cache.model.DbMessageItem
import dev.dimension.flare.data.database.cache.model.DbMessageRoom
import dev.dimension.flare.data.database.cache.model.DbMessageRoomReference
import dev.dimension.flare.data.database.cache.model.MessageContent
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType

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
}

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
                is ConvoViewLastMessageUnion.MessageView ->
                    MicroBlogKey(
                        id = message.value.id,
                        host = host,
                    )

                is ConvoViewLastMessageUnion.DeletedMessageView ->
                    MicroBlogKey(
                        id = message.value.id,
                        host = host,
                    )

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
        timestamp = sentAt.toEpochMilliseconds(),
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
            timestamp = sentAt.toEpochMilliseconds(),
            content = MessageContent.Bluesky.Deleted(this),
            showSender = false,
        )
    }
