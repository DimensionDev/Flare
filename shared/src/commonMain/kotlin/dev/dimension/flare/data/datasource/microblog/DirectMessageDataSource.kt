package dev.dimension.flare.data.datasource.microblog

import androidx.paging.PagingData
import dev.dimension.flare.common.CacheData
import dev.dimension.flare.data.database.cache.model.DbMessageItem
import dev.dimension.flare.data.datasource.microblog.handler.DirectMessageHandler
import dev.dimension.flare.data.datasource.microblog.loader.DirectMessagePinCodeStatus
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.UiDMRoom
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlin.native.HiddenFromObjC
import kotlin.time.Clock
import kotlin.uuid.Uuid

@HiddenFromObjC
public interface DirectMessageDataSource : AuthenticatedMicroblogDataSource {
    public val directMessageHandler: DirectMessageHandler

    public val directMessagePinCodeStatus: Flow<DirectMessagePinCodeStatus>
        get() = directMessageHandler.pinCodeStatus

    public suspend fun submitDirectMessagePinCode(pinCode: String): DirectMessagePinCodeStatus = directMessageHandler.submitPinCode(pinCode)

    public fun directMessageList(scope: CoroutineScope): Flow<PagingData<UiDMRoom>> = directMessageHandler.list(scope)

    public fun directMessageConversation(
        roomKey: MicroBlogKey,
        scope: CoroutineScope,
    ): Flow<PagingData<UiDMItem>> = directMessageHandler.conversation(roomKey, scope)

    public fun sendDirectMessage(
        roomKey: MicroBlogKey,
        message: String,
    ) {
        directMessageHandler.send(roomKey, message)
    }

    public fun retrySendDirectMessage(messageKey: MicroBlogKey) {
        directMessageHandler.retry(messageKey)
    }

    public fun deleteDirectMessage(
        roomKey: MicroBlogKey,
        messageKey: MicroBlogKey,
    ) {
        directMessageHandler.delete(roomKey, messageKey)
    }

    public fun getDirectMessageConversationInfo(roomKey: MicroBlogKey): CacheData<UiDMRoom> = directMessageHandler.roomInfo(roomKey)

    public suspend fun fetchNewDirectMessageForConversation(roomKey: MicroBlogKey) {
        directMessageHandler.fetchNew(roomKey)
    }

    public val directMessageBadgeCount: CacheData<Int>
        get() = directMessageHandler.badgeCount

    public fun leaveDirectMessage(roomKey: MicroBlogKey) {
        directMessageHandler.leave(roomKey)
    }

    public fun createDirectMessageRoom(userKey: MicroBlogKey): Flow<UiState<MicroBlogKey>> = directMessageHandler.createRoom(userKey)

    public suspend fun canSendDirectMessage(userKey: MicroBlogKey): Boolean = directMessageHandler.canSend(userKey)
}

internal fun createSendingDirectMessage(
    accountKey: MicroBlogKey,
    roomKey: MicroBlogKey,
    message: String,
    platformType: PlatformType,
    user: UiProfile = fallbackDirectMessageUser(accountKey, platformType),
) = MicroBlogKey(Uuid.random().toString(), accountKey.host).let { messageKey ->
    val timestamp = Clock.System.now()
    DbMessageItem(
        userKey = accountKey,
        roomKey = roomKey,
        timestamp = timestamp.toEpochMilliseconds(),
        messageKey = messageKey,
        content =
            UiDMItem(
                key = messageKey,
                user = user,
                content = UiDMItem.Message.Text(message.toUiPlainText()),
                timestamp = timestamp.toUi(),
                isFromMe = true,
                sendState = UiDMItem.SendState.Sending,
                showSender = false,
            ),
        isLocal = true,
        showSender = false,
    )
}

private fun fallbackDirectMessageUser(
    accountKey: MicroBlogKey,
    platformType: PlatformType,
): UiProfile =
    UiProfile(
        key = accountKey,
        handle = UiHandle(raw = accountKey.id, host = accountKey.host),
        avatar = null,
        nameInternal = accountKey.id.toUiPlainText(),
        platformType = platformType,
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
