package dev.dimension.flare.data.platform

import dev.dimension.flare.common.deeplink.DeepLinkMapping
import dev.dimension.flare.common.deeplink.DeepLinkPattern
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.tab.SourceTimelineTabItemV2
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.model.tab.TimelineTabItemV2
import dev.dimension.flare.data.network.bluesky.BlueskyPlatformDetector
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiInstanceMetadata
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.asType
import dev.dimension.flare.ui.presenter.home.bluesky.BlueskyBookmarkTimelinePresenter
import dev.dimension.flare.ui.presenter.home.bluesky.BlueskyFeedTimelinePresenter
import io.ktor.http.Url
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

internal data object BlueskyPlatformSpec : PlatformSpec {
    override val type = PlatformType.Bluesky
    override val metadata =
        PlatformTypeMetadata(
            displayName = "Bluesky",
            icon = UiIcon.Bluesky,
        )
    override val detector: PlatformDetector = BlueskyPlatformDetector

    override fun agreementUrl(host: String): String? = "https://bsky.social/about/support/tos"

    override fun deepLinkPatterns(host: String): ImmutableList<DeepLinkPattern<out DeepLinkMapping.Type>> =
        buildList {
            add(DeepLinkPattern(DeepLinkMapping.Type.Profile.serializer(), Url("https://$host/profile/{handle}")))
            add(DeepLinkPattern(DeepLinkMapping.Type.BlueskyPost.serializer(), Url("https://$host/profile/{handle}/post/{id}")))
            if (host == "bsky.social") {
                add(DeepLinkPattern(DeepLinkMapping.Type.Profile.serializer(), Url("https://bsky.app/profile/{handle}")))
                add(DeepLinkPattern(DeepLinkMapping.Type.BlueskyPost.serializer(), Url("https://bsky.app/profile/{handle}/post/{id}")))
            }
        }.toImmutableList()

    internal val bookmarkTimelineSpec =
        TimelineSpec(
            id = "bluesky.bookmark",
            title = UiStrings.Bookmark,
            icon = UiIcon.Bookmark.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            presenterFactory = {
                BlueskyBookmarkTimelinePresenter(
                    AccountType.Specific(it.accountKey),
                )
            },
        )

    internal val feedTimelineSpec =
        TimelineSpec(
            id = "bluesky.feed",
            title = UiStrings.Feeds,
            icon = UiIcon.Feeds.asType(),
            serializer = TimelineSpec.AccountResourceData.serializer(),
            targetId = { "${it.accountKey}:${it.resourceId}" },
            presenterFactory = {
                BlueskyFeedTimelinePresenter(
                    accountType = AccountType.Specific(it.accountKey),
                    uri = it.resourceId,
                )
            },
        )

    override val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> =
        persistentListOf(
            CommonTimelineSpecs.home,
            CommonTimelineSpecs.list,
            bookmarkTimelineSpec,
            feedTimelineSpec,
        )

    override suspend fun instanceMetadata(host: String): UiInstanceMetadata =
        throw UnsupportedOperationException("${type.name} is not supported yet")

    override fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource = throw UnsupportedOperationException("${type.name} guest data source is not supported yet")
}

internal fun UiList.Feed.toTimelineTabItemV2(accountKey: MicroBlogKey): TimelineTabItemV2 {
    val source =
        BlueskyPlatformSpec.feedTimelineSpec.target(
            data = TimelineSpec.AccountResourceData(accountKey, id),
            title = UiText.Raw(title),
            icon = avatar?.let { IconType.Url(it) } ?: UiIcon.Feeds.asType(),
        )
    return SourceTimelineTabItemV2.fromSource(source) {
        BlueskyPlatformSpec.feedTimelineSpec.createPresenter(source.data)
    }
}
