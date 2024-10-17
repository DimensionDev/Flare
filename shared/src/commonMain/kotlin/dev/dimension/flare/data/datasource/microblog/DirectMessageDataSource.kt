package dev.dimension.flare.data.datasource.microblog

import androidx.paging.PagingData
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.UiDMList
import kotlinx.coroutines.flow.Flow

internal interface DirectMessageDataSource {
    fun directMessageList(): Flow<PagingData<UiDMList>>

    fun directMessageConversation(id: String): Flow<PagingData<UiDMItem>>

    fun sendDirectMessage(
        id: String,
        message: String,
    )

    fun getDirectMessageConversationInfo(id: String): Flow<UiDMList>
}
