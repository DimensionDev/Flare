package dev.dimension.flare.data.datasource.microblog

import androidx.paging.PagingData
import dev.dimension.flare.common.BaseTimelineLoader
import dev.dimension.flare.common.CacheData
import dev.dimension.flare.common.FileItem
import dev.dimension.flare.common.MemCacheable
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiUserV2
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

internal interface ListDataSource {
    fun myList(scope: CoroutineScope): Flow<PagingData<UiList>>

    fun listInfo(listId: String): CacheData<UiList>

    fun listMembers(
        listId: String,
        scope: CoroutineScope,
        pageSize: Int = 20,
    ): Flow<PagingData<UiUserV2>>

    suspend fun addMember(
        listId: String,
        userKey: MicroBlogKey,
    )

    suspend fun removeMember(
        listId: String,
        userKey: MicroBlogKey,
    )

    fun listTimeline(
        listId: String,
    ): BaseTimelineLoader

    suspend fun deleteList(listId: String)

    val supportedMetaData: ImmutableList<ListMetaDataType>

    suspend fun updateList(
        listId: String,
        metaData: ListMetaData,
    )

    suspend fun createList(metaData: ListMetaData)

    fun listMemberCache(listId: String): Flow<ImmutableList<UiUserV2>>

    fun userLists(userKey: MicroBlogKey): MemCacheable<ImmutableList<UiList>>
}

public data class ListMetaData(
    val title: String,
    val description: String? = null,
    val avatar: FileItem? = null,
)

public enum class ListMetaDataType {
    TITLE,
    DESCRIPTION,
    AVATAR,
}
