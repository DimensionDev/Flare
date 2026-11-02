package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.data.datasource.microblog.paging.notSupported
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.collections.immutable.persistentListOf

/** Keeps observers alive while a development-only platform is detached. */
internal object UnavailableMicroblogDataSource : MicroblogDataSource {
    override fun homeTimeline() = notSupported<UiTimelineV2>()

    override fun userTimeline(
        userKey: MicroBlogKey,
        mediaOnly: Boolean,
    ) = notSupported<UiTimelineV2>()

    override fun context(statusKey: MicroBlogKey) = notSupported<UiTimelineV2>()

    override fun searchStatus(query: String) = notSupported<UiTimelineV2>()

    override fun searchUser(query: String) = notSupported<UiProfile>()

    override fun discoverUsers() = notSupported<UiProfile>()

    override fun discoverStatuses() = notSupported<UiTimelineV2>()

    override fun discoverHashtags() = notSupported<UiHashtag>()

    override fun following(userKey: MicroBlogKey) = notSupported<UiProfile>()

    override fun fans(userKey: MicroBlogKey) = notSupported<UiProfile>()

    override suspend fun profileTabs(userKey: MicroBlogKey) = persistentListOf<ProfileTab>()
}
