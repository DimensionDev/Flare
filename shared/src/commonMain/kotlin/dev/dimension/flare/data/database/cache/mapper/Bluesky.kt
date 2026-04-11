package dev.dimension.flare.data.database.cache.mapper

import chat.bsky.convo.ConvoView
import chat.bsky.convo.ConvoViewLastMessageUnion
import chat.bsky.convo.DeletedMessageView
import chat.bsky.convo.MessageView
import dev.dimension.flare.data.database.cache.model.DbDirectMessageTimeline
import dev.dimension.flare.data.database.cache.model.DbMessageItem
import dev.dimension.flare.data.database.cache.model.DbUser
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.UiDMRoom
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.mapper.render
import dev.dimension.flare.ui.render.toUi
import kotlinx.collections.immutable.toImmutableList
import kotlin.time.Instant

internal data class BlueskyDirectMessages(
    val users: List<DbUser>,
    val timeline: List<DbDirectMessageTimeline>,
    val messages: List<DbMessageItem>,
)

internal fun mapDirectMessages(
    accountKey: MicroBlogKey,
    data: List<ConvoView>,
): BlueskyDirectMessages {
    val users =
        data
            .flatMap { it.members }
            .map {
                it.render(accountKey).toDbUser(host = accountKey.host)
            }
    val userMap =
        users.associate { it.userKey to it.content }
    val timeline =
        data.map { convo ->
            convo.toDbDirectMessageTimeline(accountKey, userMap)
        }
    val messages =
        data.mapNotNull { convo ->
            convo.lastMessage?.toDbMessageItem(
                accountKey = accountKey,
                roomKey = MicroBlogKey(convo.id, accountKey.host),
                userMap = userMap,
            )
        }
    return BlueskyDirectMessages(
        users = users,
        timeline = timeline,
        messages = messages,
    )
}

internal fun mapConversationMessages(
    accountKey: MicroBlogKey,
    roomKey: MicroBlogKey,
    data: List<MessageView>,
): List<DbMessageItem> {
    val userMap =
        data.associate { message ->
            val senderKey = MicroBlogKey(message.sender.did.did, accountKey.host)
            senderKey to UiProfile.placeholder(senderKey)
        }
    return data.map { message ->
        message.toDbMessageItem(
            accountKey = accountKey,
            roomKey = roomKey,
            userMap = userMap,
        )
    }
}

internal fun mapConversationDeletedMessage(
    accountKey: MicroBlogKey,
    roomKey: MicroBlogKey,
    data: DeletedMessageView,
): DbMessageItem {
    val senderKey = MicroBlogKey(data.sender.did.did, accountKey.host)
    val userMap = mapOf(senderKey to UiProfile.placeholder(senderKey))
    return data.toDbMessageItem(
        accountKey = accountKey,
        roomKey = roomKey,
        userMap = userMap,
    )
}

private fun ConvoView.toDbDirectMessageTimeline(
    accountKey: MicroBlogKey,
    userMap: Map<MicroBlogKey, UiProfile>,
): DbDirectMessageTimeline {
    val roomKey = MicroBlogKey(id = id, host = accountKey.host)
    val memberProfiles =
        members
            .filter { it.did.did != accountKey.id }
            .mapNotNull { member ->
                val key = MicroBlogKey(member.did.did, accountKey.host)
                userMap[key]
            }.toImmutableList()
    val lastMessageItem =
        lastMessage?.toUiDMItem(
            accountKey = accountKey,
            roomKey = roomKey,
            userMap = userMap,
        )
    return DbDirectMessageTimeline(
        accountType = AccountType.Specific(accountKey),
        roomKey = roomKey,
        sortId =
            lastMessage?.timestamp()
                ?: 0L,
        unreadCount = unreadCount,
        content =
            UiDMRoom(
                key = roomKey,
                users = memberProfiles,
                lastMessage = lastMessageItem,
                unreadCount = unreadCount,
            ),
    )
}

private fun ConvoViewLastMessageUnion.timestamp(): Long =
    when (this) {
        is ConvoViewLastMessageUnion.MessageView -> value.sentAt.toEpochMilliseconds()
        is ConvoViewLastMessageUnion.DeletedMessageView -> value.sentAt.toEpochMilliseconds()
        is ConvoViewLastMessageUnion.Unknown -> 0L
    }

private fun ConvoViewLastMessageUnion.toDbMessageItem(
    accountKey: MicroBlogKey,
    roomKey: MicroBlogKey,
    userMap: Map<MicroBlogKey, UiProfile>,
): DbMessageItem? =
    when (this) {
        is ConvoViewLastMessageUnion.MessageView ->
            value.toDbMessageItem(accountKey, roomKey, userMap)
        is ConvoViewLastMessageUnion.DeletedMessageView ->
            value.toDbMessageItem(accountKey, roomKey, userMap)
        is ConvoViewLastMessageUnion.Unknown -> null
    }

private fun ConvoViewLastMessageUnion.toUiDMItem(
    accountKey: MicroBlogKey,
    roomKey: MicroBlogKey,
    userMap: Map<MicroBlogKey, UiProfile>,
): UiDMItem? = toDbMessageItem(accountKey, roomKey, userMap)?.content

private fun MessageView.toDbMessageItem(
    accountKey: MicroBlogKey,
    roomKey: MicroBlogKey,
    userMap: Map<MicroBlogKey, UiProfile>,
): DbMessageItem {
    val senderKey = MicroBlogKey(sender.did.did, accountKey.host)
    val messageKey = MicroBlogKey(id, accountKey.host)
    return DbMessageItem(
        messageKey = messageKey,
        roomKey = roomKey,
        timestamp = sentAt.toEpochMilliseconds(),
        content =
            UiDMItem(
                key = messageKey,
                user = userMap[senderKey] ?: UiProfile.placeholder(senderKey),
                content = render(accountKey),
                timestamp = Instant.fromEpochMilliseconds(sentAt.toEpochMilliseconds()).toUi(),
                isFromMe = sender.did.did == accountKey.id,
                sendState = null,
                showSender = false,
            ),
    )
}

internal fun MessageView.render(accountKey: MicroBlogKey): UiDMItem.Message =
    UiDMItem.Message.Text(
        text =
            dev.dimension.flare.ui.model.mapper.parseBluesky(
                text,
                facets.orEmpty(),
                accountKey,
            ),
    )

private fun DeletedMessageView.toDbMessageItem(
    accountKey: MicroBlogKey,
    roomKey: MicroBlogKey,
    userMap: Map<MicroBlogKey, UiProfile>,
): DbMessageItem {
    val senderKey = MicroBlogKey(sender.did.did, accountKey.host)
    val messageKey = MicroBlogKey(id, accountKey.host)
    return DbMessageItem(
        messageKey = messageKey,
        roomKey = roomKey,
        timestamp = sentAt.toEpochMilliseconds(),
        content =
            UiDMItem(
                key = messageKey,
                user = userMap[senderKey] ?: UiProfile.placeholder(senderKey),
                content = UiDMItem.Message.Deleted,
                timestamp = Instant.fromEpochMilliseconds(sentAt.toEpochMilliseconds()).toUi(),
                isFromMe = sender.did.did == accountKey.id,
                sendState = null,
                showSender = false,
            ),
    )
}
