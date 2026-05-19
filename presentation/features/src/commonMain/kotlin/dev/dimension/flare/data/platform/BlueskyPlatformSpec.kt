package dev.dimension.flare.data.platform

import dev.dimension.flare.common.deeplink.DeepLinkMapping
import dev.dimension.flare.common.deeplink.DeepLinkPattern
import dev.dimension.flare.data.datasource.bluesky.BlueskyDataSource
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.tab.AccountTimelineSpec
import dev.dimension.flare.data.model.tab.TimelineResolver
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.model.tab.TimelineTabItemV2
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiInstanceMetadata
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.asType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

internal data object BlueskyPlatformSpec : PlatformSpec {
    override val type = BlueskySocialPlatformSpec.type
    override val timelineSpecs = BlueskySocialPlatformSpec.timelineSpecs
    override val metadata = BlueskySocialPlatformSpec.metadata
    override val detector = BlueskySocialPlatformSpec.detector

    override fun agreementUrl(host: String): String? = BlueskySocialPlatformSpec.agreementUrl(host)

    override fun deepLinkPatterns(host: String): ImmutableList<DeepLinkPattern<out DeepLinkMapping.Type>> =
        BlueskySocialPlatformSpec.deepLinkPatterns(host)

    internal val bookmarkTimelineSpec =
        AccountTimelineSpec(
            id = "bluesky.bookmark",
            title = UiStrings.Bookmark,
            icon = UiIcon.Bookmark.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            loaderFactory = { service, _ ->
                require(service is BlueskyDataSource)
                service.bookmarkTimeline()
            },
        )

    internal val feedTimelineSpec =
        AccountTimelineSpec(
            id = "bluesky.feed",
            title = UiStrings.Feeds,
            icon = UiIcon.Feeds.asType(),
            serializer = TimelineSpec.AccountResourceData.serializer(),
            targetId = { "${it.accountKey}:${it.resourceId}" },
            loaderFactory = { service, data ->
                require(service is BlueskyDataSource)
                service.feedTimelineLoader(data.resourceId)
            },
        )

    override val legacyTimelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> =
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

internal fun UiList.Feed.toTimelineTabItemV2(
    accountKey: MicroBlogKey,
    timelineResolver: TimelineResolver,
): TimelineTabItemV2 {
    val source =
        BlueskyPlatformSpec.feedTimelineSpec.target(
            data = TimelineSpec.AccountResourceData(accountKey, id),
            title = UiText.Raw(title),
            icon = avatar?.let { IconType.Url(it) } ?: UiIcon.Feeds.asType(),
        )
    return timelineResolver.toTabItem(source)
}
