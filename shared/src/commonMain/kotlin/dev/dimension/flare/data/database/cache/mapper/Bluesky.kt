package dev.dimension.flare.data.database.cache.mapper

import chat.bsky.convo.ConvoView
import chat.bsky.convo.ConvoViewLastMessageUnion
import chat.bsky.convo.DeletedMessageView
import chat.bsky.convo.MessageView
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

internal object Bluesky {
    fun rooms(
        accountKey: MicroBlogKey,
        data: List<ConvoView>,
    ): List<UiDMRoom> = data.map { it.toUiDMRoom(accountKey) }

    fun messages(
        accountKey: MicroBlogKey,
        roomKey: MicroBlogKey,
        data: List<MessageView>,
        users: Map<String, UiProfile> = emptyMap(),
    ): List<UiDMItem> = data.map { it.toUiDMItem(roomKey, users, accountKey) }

    fun message(
        accountKey: MicroBlogKey,
        roomKey: MicroBlogKey,
        data: DeletedMessageView,
        users: Map<String, UiProfile> = emptyMap(),
    ): UiDMItem = data.toUiDMItem(roomKey, users, accountKey)

    fun deletedMessageKey(
        accountKey: MicroBlogKey,
        message: DeletedMessageView,
    ): MicroBlogKey =
        MicroBlogKey(
            id = message.id,
            host = accountKey.host,
        )
}

private fun ConvoView.toUiDMRoom(accountKey: MicroBlogKey): UiDMRoom {
    val roomKey = MicroBlogKey(id = id, host = accountKey.host)
    val membersByDid = members.associate { it.did.did to it.render(accountKey) }
    return UiDMRoom(
        key = roomKey,
        users =
            members
                .filter { it.did.did != accountKey.id }
                .map { it.render(accountKey) }
                .toImmutableList(),
        lastMessage =
            lastMessage?.toUiDMItem(
                roomKey = roomKey,
                users = membersByDid,
                accountKey = accountKey,
            ),
        unreadCount = unreadCount,
    )
}

private fun ConvoViewLastMessageUnion.toUiDMItem(
    roomKey: MicroBlogKey,
    users: Map<String, UiProfile>,
    accountKey: MicroBlogKey,
): UiDMItem? =
    when (this) {
        is ConvoViewLastMessageUnion.MessageView -> value.toUiDMItem(roomKey, users, accountKey)
        is ConvoViewLastMessageUnion.DeletedMessageView -> value.toUiDMItem(roomKey, users, accountKey)
        is ConvoViewLastMessageUnion.Unknown -> null
    }

private fun MessageView.toUiDMItem(
    roomKey: MicroBlogKey,
    users: Map<String, UiProfile>,
    accountKey: MicroBlogKey,
): UiDMItem {
    val messageKey = MicroBlogKey(id = id, host = roomKey.host)
    val userKey = MicroBlogKey(id = sender.did.did, host = roomKey.host)
    return UiDMItem(
        key = messageKey,
        user = users[sender.did.did] ?: fallbackDirectMessageUser(userKey),
        timestamp = sentAt.toUi(),
        content = render(accountKey),
        isFromMe = userKey == accountKey,
        sendState = null,
        showSender = false,
        remoteCursor = rev,
    )
}

private fun DeletedMessageView.toUiDMItem(
    roomKey: MicroBlogKey,
    users: Map<String, UiProfile>,
    accountKey: MicroBlogKey,
): UiDMItem {
    val messageKey = MicroBlogKey(id = id, host = roomKey.host)
    val userKey = MicroBlogKey(id = sender.did.did, host = roomKey.host)
    return UiDMItem(
        key = messageKey,
        user = users[sender.did.did] ?: fallbackDirectMessageUser(userKey),
        timestamp = sentAt.toUi(),
        content = render(),
        isFromMe = userKey == accountKey,
        sendState = null,
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
