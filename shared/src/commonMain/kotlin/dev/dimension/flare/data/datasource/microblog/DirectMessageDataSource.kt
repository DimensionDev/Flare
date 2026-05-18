package dev.dimension.flare.data.datasource.microblog

import androidx.paging.PagingData
import dev.dimension.flare.common.CacheData
import dev.dimension.flare.data.database.cache.model.DbMessageItem
import dev.dimension.flare.data.datasource.microblog.handler.DirectMessageHandler
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
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal interface DirectMessageDataSource : AuthenticatedMicroblogDataSource {
    val directMessageHandler: DirectMessageHandler

    fun directMessageList(scope: CoroutineScope): Flow<PagingData<UiDMRoom>> =
        directMessageHandler.list(scope)

    fun directMessageConversation(
        roomKey: MicroBlogKey,
        scope: CoroutineScope,
    ): Flow<PagingData<UiDMItem>> =
        directMessageHandler.conversation(roomKey, scope)

    fun sendDirectMessage(
        roomKey: MicroBlogKey,
        message: String,
    ) {
        directMessageHandler.send(roomKey, message)
    }

    fun retrySendDirectMessage(messageKey: MicroBlogKey) {
        directMessageHandler.retry(messageKey)
    }

    fun deleteDirectMessage(
        roomKey: MicroBlogKey,
        messageKey: MicroBlogKey,
    ) {
        directMessageHandler.delete(roomKey, messageKey)
    }

    fun getDirectMessageConversationInfo(roomKey: MicroBlogKey): CacheData<UiDMRoom> =
        directMessageHandler.roomInfo(roomKey)

    suspend fun fetchNewDirectMessageForConversation(roomKey: MicroBlogKey) {
        directMessageHandler.fetchNew(roomKey)
    }

    val directMessageBadgeCount: CacheData<Int>
        get() = directMessageHandler.badgeCount

    fun leaveDirectMessage(roomKey: MicroBlogKey) {
        directMessageHandler.leave(roomKey)
    }

    fun createDirectMessageRoom(userKey: MicroBlogKey): Flow<UiState<MicroBlogKey>> =
        directMessageHandler.createRoom(userKey)

    suspend fun canSendDirectMessage(userKey: MicroBlogKey): Boolean =
        directMessageHandler.canSend(userKey)
}

internal fun createSendingDirectMessage(
    accountKey: MicroBlogKey,
    roomKey: MicroBlogKey,
    message: String,
    platformType: PlatformType,
    user: UiProfile = fallbackDirectMessageUser(accountKey, platformType),
) =
    MicroBlogKey(Uuid.random().toString(), accountKey.host).let { messageKey ->
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
        avatar = "",
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
