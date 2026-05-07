package dev.dimension.flare.data.platform

import dev.dimension.flare.common.deeplink.DeepLinkMapping
import dev.dimension.flare.common.deeplink.DeepLinkPattern
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.data.network.vvo.VVOPlatformDetector
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
import dev.dimension.flare.model.vvo
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiInstanceMetadata
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.asType
import dev.dimension.flare.ui.presenter.home.DiscoverStatusTimelinePresenter
import dev.dimension.flare.ui.presenter.home.vvo.VVOFavouriteTimelinePresenter
import dev.dimension.flare.ui.presenter.home.vvo.VVOLikeTimelinePresenter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

internal data object VvoPlatformSpec : PlatformSpec {
    override val type = PlatformType.VVo
    override val metadata =
        PlatformTypeMetadata(
            displayName = vvo,
            icon = UiIcon.Weibo,
        )
    override val detector: PlatformDetector = VVOPlatformDetector

    override fun agreementUrl(host: String): String? = null

    override fun deepLinkPatterns(host: String): ImmutableList<DeepLinkPattern<out DeepLinkMapping.Type>> = persistentListOf()

    internal val featuredTimelineSpec =
        TimelineSpec(
            id = "vvo.featured",
            title = UiStrings.Featured,
            icon = UiIcon.Featured.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            presenterFactory = {
                DiscoverStatusTimelinePresenter(
                    AccountType.Specific(it.accountKey),
                )
            },
        )

    internal val favoriteTimelineSpec =
        TimelineSpec(
            id = "vvo.favorite",
            title = UiStrings.Bookmark,
            icon = UiIcon.Bookmark.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            presenterFactory = {
                VVOFavouriteTimelinePresenter(
                    AccountType.Specific(it.accountKey),
                )
            },
        )

    internal val likedTimelineSpec =
        TimelineSpec(
            id = "vvo.liked",
            title = UiStrings.Liked,
            icon = UiIcon.Heart.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            presenterFactory = {
                VVOLikeTimelinePresenter(
                    AccountType.Specific(it.accountKey),
                )
            },
        )

    override val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> =
        persistentListOf(
            CommonTimelineSpecs.home,
            featuredTimelineSpec,
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
