package dev.dimension.flare.data.datasource.microblog

import androidx.paging.PagingData
import dev.dimension.flare.common.CacheData
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.UiUserV2
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow

interface MicroblogDataSource {
    fun homeTimeline(
        pageSize: Int = 20,
        pagingKey: String,
        scope: CoroutineScope,
    ): Flow<PagingData<UiTimeline>>

    fun userByAcct(acct: String): CacheData<UiUserV2>

    fun userById(id: String): CacheData<UiProfile>

    fun userTimeline(
        userKey: MicroBlogKey,
        scope: CoroutineScope,
        pageSize: Int = 20,
        mediaOnly: Boolean = false,
        pagingKey: String = "user_${userKey}_${if (mediaOnly) "media" else "all"}",
    ): Flow<PagingData<UiTimeline>>

    fun context(
        statusKey: MicroBlogKey,
        scope: CoroutineScope,
        pageSize: Int = 20,
        pagingKey: String = "status_$statusKey",
    ): Flow<PagingData<UiTimeline>>

    fun status(statusKey: MicroBlogKey): CacheData<UiTimeline>

    fun searchStatus(
        query: String,
        scope: CoroutineScope,
        pageSize: Int = 20,
        pagingKey: String = "search_$query",
    ): Flow<PagingData<UiTimeline>>

    fun searchUser(
        query: String,
        scope: CoroutineScope,
        pageSize: Int = 20,
    ): Flow<PagingData<UiUserV2>>

    fun discoverUsers(pageSize: Int = 20): Flow<PagingData<UiUserV2>>

    fun discoverStatuses(
        pageSize: Int = 20,
        scope: CoroutineScope,
        pagingKey: String,
    ): Flow<PagingData<UiTimeline>>

    fun discoverHashtags(pageSize: Int = 20): Flow<PagingData<UiHashtag>>

    fun following(
        userKey: MicroBlogKey,
        scope: CoroutineScope,
        pageSize: Int = 20,
        pagingKey: String = "following_$userKey",
    ): Flow<PagingData<UiUserV2>>

    fun fans(
        userKey: MicroBlogKey,
        scope: CoroutineScope,
        pageSize: Int = 20,
        pagingKey: String = "fans_$userKey",
    ): Flow<PagingData<UiUserV2>>

    fun profileTabs(
        userKey: MicroBlogKey,
        scope: CoroutineScope,
        pagingSize: Int = 20,
    ): ImmutableList<ProfileTab>
}
