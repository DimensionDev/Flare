package dev.dimension.flare.data.platform

import dev.dimension.flare.common.deeplink.DeepLinkMapping
import dev.dimension.flare.common.deeplink.DeepLinkPattern
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiInstanceMetadata
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.asType
import dev.dimension.flare.ui.presenter.home.xqt.XQTBookmarkTimelinePresenter
import dev.dimension.flare.ui.presenter.home.xqt.XQTDeviceFollowTimelinePresenter
import dev.dimension.flare.ui.presenter.home.xqt.XQTFeaturedTimelinePresenter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

internal data object XqtPlatformSpec : PlatformSpec {
    override val type = XqtSocialPlatformSpec.type
    override val metadata = XqtSocialPlatformSpec.metadata
    override val detector = XqtSocialPlatformSpec.detector

    override fun agreementUrl(host: String): String = XqtSocialPlatformSpec.agreementUrl(host)

    override fun deepLinkPatterns(host: String): ImmutableList<DeepLinkPattern<out DeepLinkMapping.Type>> =
        XqtSocialPlatformSpec.deepLinkPatterns(host)

    internal val featuredTimelineSpec =
        TimelineSpec(
            id = "xqt.featured",
            title = UiStrings.Featured,
            icon = UiIcon.Featured.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            presenterFactory = {
                XQTFeaturedTimelinePresenter(
                    AccountType.Specific(it.accountKey),
                )
            },
        )

    internal val bookmarkTimelineSpec =
        TimelineSpec(
            id = "xqt.bookmark",
            title = UiStrings.Bookmark,
            icon = UiIcon.Bookmark.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            presenterFactory = {
                XQTBookmarkTimelinePresenter(
                    AccountType.Specific(it.accountKey),
                )
            },
        )

    internal val deviceFollowTimelineSpec =
        TimelineSpec(
            id = "xqt.device_follow",
            title = UiStrings.Posts,
            icon = UiIcon.List.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            presenterFactory = {
                XQTDeviceFollowTimelinePresenter(
                    AccountType.Specific(it.accountKey),
                )
            },
        )

    override val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> =
        persistentListOf(
            CommonTimelineSpecs.home,
            CommonTimelineSpecs.list,
            featuredTimelineSpec,
            bookmarkTimelineSpec,
            deviceFollowTimelineSpec,
        )

    override suspend fun instanceMetadata(host: String): UiInstanceMetadata =
        throw UnsupportedOperationException("${type.name} is not supported yet")

    override fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource = throw UnsupportedOperationException("${type.name} guest data source is not supported yet")
}
