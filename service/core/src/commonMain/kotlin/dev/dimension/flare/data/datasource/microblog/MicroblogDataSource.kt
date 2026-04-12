package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.collections.immutable.ImmutableList

public interface MicroblogDataSource {
    public fun homeTimeline(): RemoteLoader<UiTimelineV2>

    public fun userTimeline(
        userKey: MicroBlogKey,
        mediaOnly: Boolean = false,
    ): RemoteLoader<UiTimelineV2>

    public fun context(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2>

    public fun searchStatus(query: String): RemoteLoader<UiTimelineV2>

    public fun searchUser(query: String): RemoteLoader<UiProfile>

    public fun discoverUsers(): RemoteLoader<UiProfile>

    public fun discoverStatuses(): RemoteLoader<UiTimelineV2>

    public fun discoverHashtags(): RemoteLoader<UiHashtag>

    public fun following(userKey: MicroBlogKey): RemoteLoader<UiProfile>

    public fun fans(userKey: MicroBlogKey): RemoteLoader<UiProfile>

    public fun profileTabs(userKey: MicroBlogKey): ImmutableList<ProfileTab>
}
