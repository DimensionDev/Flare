package dev.dimension.flare.data.datasource.microblog

import androidx.paging.PagingData
import dev.dimension.flare.common.CacheData
import dev.dimension.flare.data.datasource.microblog.paging.BaseTimelineLoader
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimeline
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

internal interface MicroblogDataSource {
    fun homeTimeline(): BaseTimelineLoader

    fun userByAcct(acct: String): CacheData<UiProfile>

    fun userById(id: String): CacheData<UiProfile>

    fun userTimeline(
        userKey: MicroBlogKey,
        mediaOnly: Boolean = false,
    ): BaseTimelineLoader

    fun context(statusKey: MicroBlogKey): BaseTimelineLoader

    fun status(statusKey: MicroBlogKey): CacheData<UiTimeline>

    fun searchStatus(query: String): BaseTimelineLoader

    fun searchUser(
        query: String,
        pageSize: Int = 20,
    ): Flow<PagingData<UiProfile>>

    fun discoverUsers(pageSize: Int = 20): Flow<PagingData<UiProfile>>

    fun discoverStatuses(): BaseTimelineLoader

    fun discoverHashtags(pageSize: Int = 20): Flow<PagingData<UiHashtag>>

    fun following(
        userKey: MicroBlogKey,
        scope: CoroutineScope,
        pageSize: Int = 20,
    ): Flow<PagingData<UiProfile>>

    fun fans(
        userKey: MicroBlogKey,
        scope: CoroutineScope,
        pageSize: Int = 20,
    ): Flow<PagingData<UiProfile>>

    fun profileTabs(userKey: MicroBlogKey): ImmutableList<ProfileTab>
}
