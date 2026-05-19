package dev.dimension.flare.data.platform

import dev.dimension.flare.common.deeplink.DeepLinkMapping
import dev.dimension.flare.common.deeplink.DeepLinkPattern
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.vvo.VVODataSource
import dev.dimension.flare.data.model.tab.AccountTimelineSpec
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiInstanceMetadata
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.asType
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

internal data object VvoPlatformSpec : PlatformSpec {
    override val type = VvoSocialPlatformSpec.type
    override val timelineSpecs = VvoSocialPlatformSpec.timelineSpecs
    override val metadata = VvoSocialPlatformSpec.metadata
    override val detector = VvoSocialPlatformSpec.detector

    override fun agreementUrl(host: String): String? = VvoSocialPlatformSpec.agreementUrl(host)

    override fun deepLinkPatterns(host: String): ImmutableList<DeepLinkPattern<out DeepLinkMapping.Type>> =
        VvoSocialPlatformSpec.deepLinkPatterns(host)

    internal val favoriteTimelineSpec =
        AccountTimelineSpec(
            id = "vvo.favorite",
            title = UiStrings.Bookmark,
            icon = UiIcon.Bookmark.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            loaderFactory = { service, _ ->
                require(service is VVODataSource)
                service.favouriteTimeline()
            },
        )

    internal val likedTimelineSpec =
        AccountTimelineSpec(
            id = "vvo.liked",
            title = UiStrings.Liked,
            icon = UiIcon.Heart.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            loaderFactory = { service, _ ->
                require(service is VVODataSource)
                service.likeRemoteMediator()
            },
        )

    override val legacyTimelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> =
        persistentListOf(
            CommonTimelineSpecs.home,
            CommonTimelineSpecs.discover,
            favoriteTimelineSpec,
            likedTimelineSpec,
        )

    override suspend fun instanceMetadata(host: String): UiInstanceMetadata =
        throw UnsupportedOperationException("${type.name} is not supported yet")

    override fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource = throw UnsupportedOperationException("${type.name} guest data source is not supported yet")
}
