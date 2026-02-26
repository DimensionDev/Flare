package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.collections.immutable.ImmutableList

internal interface MicroblogDataSource {
    fun homeTimeline(): RemoteLoader<UiTimelineV2>

//    suspend fun userByAcct(acct: String): UiProfile

    suspend fun userByNameAndHost(
        name: String,
        host: String,
    ): UiProfile

    suspend fun userById(id: String): UiProfile

    fun userTimeline(
        userKey: MicroBlogKey,
        mediaOnly: Boolean = false,
    ): RemoteLoader<UiTimelineV2>

    fun context(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2>

    suspend fun status(statusKey: MicroBlogKey): UiTimelineV2

    fun searchStatus(query: String): RemoteLoader<UiTimelineV2>

    fun searchUser(query: String): RemoteLoader<UiProfile>

    fun discoverUsers(): RemoteLoader<UiProfile>

    fun discoverStatuses(): RemoteLoader<UiTimelineV2>

    fun discoverHashtags(): RemoteLoader<UiHashtag>

    fun following(userKey: MicroBlogKey): RemoteLoader<UiProfile>

    fun fans(userKey: MicroBlogKey): RemoteLoader<UiProfile>

    fun profileTabs(userKey: MicroBlogKey): ImmutableList<ProfileTab>
}
