package dev.dimension.flare.data.datasource.microblog.datasource

import androidx.paging.PagingData
import dev.dimension.flare.common.CacheData
import dev.dimension.flare.data.datasource.microblog.handler.DirectMessageHandler
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.UiDMRoom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

public interface DirectMessageDataSource {
    public val directMessageHandler: DirectMessageHandler

    public fun directMessageList(scope: CoroutineScope): Flow<PagingData<UiDMRoom>>

    public fun directMessageConversation(
        roomKey: MicroBlogKey,
        scope: CoroutineScope,
    ): Flow<PagingData<UiDMItem>>

    public fun getDirectMessageConversationInfo(roomKey: MicroBlogKey): CacheData<UiDMRoom>

    public suspend fun fetchNewDirectMessageForConversation(roomKey: MicroBlogKey)
}
