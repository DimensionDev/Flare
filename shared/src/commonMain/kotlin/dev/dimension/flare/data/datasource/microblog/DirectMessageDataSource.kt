package dev.dimension.flare.data.datasource.microblog

import androidx.paging.PagingData
import dev.dimension.flare.common.CacheData
import dev.dimension.flare.data.database.cache.model.DbMessageItem
import dev.dimension.flare.data.database.cache.model.MessageContent
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.UiDMRoom
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlin.uuid.Uuid

internal interface DirectMessageDataSource : AuthenticatedMicroblogDataSource {
    fun directMessageList(): Flow<PagingData<UiDMRoom>>

    fun directMessageConversation(roomKey: MicroBlogKey): Flow<PagingData<UiDMItem>>

    fun sendDirectMessage(
        roomKey: MicroBlogKey,
        message: String,
    )

    fun retrySendDirectMessage(messageKey: MicroBlogKey)

    fun deleteDirectMessage(
        roomKey: MicroBlogKey,
        messageKey: MicroBlogKey,
    )

    fun getDirectMessageConversationInfo(roomKey: MicroBlogKey): CacheData<UiDMRoom>

    suspend fun fetchNewDirectMessageForConversation(roomKey: MicroBlogKey)
}

internal fun DirectMessageDataSource.createSendingDirectMessage(
    roomKey: MicroBlogKey,
    message: String,
) = DbMessageItem(
    userKey = accountKey,
    roomKey = roomKey,
    timestamp = Clock.System.now().toEpochMilliseconds(),
    messageKey = MicroBlogKey(Uuid.random().toString(), accountKey.host),
    content =
        MessageContent.Local(
            text = message,
            state = MessageContent.Local.State.SENDING,
        ),
    isLocal = true,
)