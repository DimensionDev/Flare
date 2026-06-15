package dev.dimension.flare.data.datasource.xqt

import dev.dimension.flare.data.network.xqt.xchat.XChatConversation
import dev.dimension.flare.data.network.xqt.xchat.XChatConversationType
import dev.dimension.flare.data.network.xqt.xchat.XChatDecodedEvent
import dev.dimension.flare.data.network.xqt.xchat.XChatDecodedEventKind
import dev.dimension.flare.data.network.xqt.xchat.XChatUser
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.UiDMRoom
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.toUiImage
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.time.Instant

internal fun XChatConversation.toUiDMRoom(accountKey: MicroBlogKey): UiDMRoom {
    val usersById = participants.associate { it.userId to it.toUiProfile(accountKey.host) }
    return UiDMRoom(
        key = MicroBlogKey(conversationId, accountKey.host),
        users =
            participants
                .mapNotNull { usersById[it.userId] }
                .filter { it.key != accountKey }
                .toImmutableList(),
        lastMessage =
            latestMessage?.toUiDMItem(
                accountKey = accountKey,
                users = usersById,
                showSender = type == XChatConversationType.Group,
            ),
        unreadCount = unreadCount,
    )
}

internal fun XChatDecodedEvent.toUiDMItem(
    accountKey: MicroBlogKey,
    users: Map<String, UiProfile>,
    showSender: Boolean,
): UiDMItem? {
    val senderId = senderId ?: return null
    val itemKey = MicroBlogKey(sequenceId ?: messageId ?: return null, accountKey.host)
    val userKey = MicroBlogKey(senderId, accountKey.host)
    val content =
        when (kind) {
            XChatDecodedEventKind.Message,
            XChatDecodedEventKind.Edit,
            -> UiDMItem.Message.Text((text?.takeIf { it.isNotBlank() } ?: encryptedPreviewText() ?: return null).toUiPlainText())

            XChatDecodedEventKind.Delete -> UiDMItem.Message.Deleted

            else -> return null
        }
    return UiDMItem(
        key = itemKey,
        user = users[senderId] ?: fallbackXChatDirectMessageUser(userKey),
        content = content,
        timestamp = Instant.fromEpochMilliseconds(createdAtMillis ?: 0L).toUi(),
        isFromMe = userKey == accountKey,
        sendState = null,
        showSender = showSender && userKey != accountKey,
        remoteCursor = sequenceId,
    )
}

internal fun XChatConversation.participantIds(accountId: String): List<String> =
    participants
        .map { it.userId }
        .takeIf { it.isNotEmpty() }
        ?: conversationId
            .split(":")
            .takeIf { it.size == 2 }
            .orEmpty()
            .ifEmpty { listOf(accountId) }

internal fun fallbackXChatDirectMessageUser(userKey: MicroBlogKey): UiProfile =
    UiProfile(
        key = userKey,
        handle = UiHandle(raw = userKey.id, host = userKey.host),
        avatar = null,
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

private fun XChatUser.toUiProfile(host: String): UiProfile =
    UiProfile(
        key = MicroBlogKey(userId, host),
        handle = UiHandle(raw = screenName ?: userId, host = host),
        avatar = avatarUrl.toUiImage(),
        nameInternal = (name ?: screenName ?: userId).toUiPlainText(),
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

private fun XChatDecodedEvent.encryptedPreviewText(): String? =
    if (encrypted || decryptError) {
        "Encrypted message"
    } else {
        null
    }
