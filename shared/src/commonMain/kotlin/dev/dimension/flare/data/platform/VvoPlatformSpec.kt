package dev.dimension.flare.data.platform

import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.vvo.VVODataSource
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.model.tab.TimelineSpecIds
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.data.network.vvo.VVOPlatformDetector
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformDeepLink
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
import dev.dimension.flare.model.vvo
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiInstanceMetadata
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.asType
import dev.dimension.flare.ui.presenter.home.vvo.VVOFavouriteTimelinePresenter
import dev.dimension.flare.ui.presenter.home.vvo.VVOLikeTimelinePresenter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

public data object VvoPlatformSpec : PlatformSpec {
    public override val type: PlatformType = PlatformType.VVo
    public override val metadata: PlatformTypeMetadata =
        PlatformTypeMetadata(
            displayName = vvo,
            icon = UiIcon.Weibo,
        )
    override val detector: PlatformDetector = VVOPlatformDetector

    override fun agreementUrl(host: String): String? = null

    override fun deepLinks(accountKey: MicroBlogKey): ImmutableList<PlatformDeepLink<*>> = persistentListOf()

    internal val favoriteTimelineSpec =
        TimelineSpec(
            id = TimelineSpecIds.VVO_FAVORITE,
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
            id = TimelineSpecIds.VVO_LIKED,
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
            CommonTimelineSpecs.discover,
            favoriteTimelineSpec,
            likedTimelineSpec,
        )

    override suspend fun instanceMetadata(host: String): UiInstanceMetadata =
        throw UnsupportedOperationException("${type.name} is not supported yet")

    override fun restoreAccount(
        accountKey: MicroBlogKey,
        credentialJson: String,
    ): UiAccount =
        UiAccount.VVo(
            accountKey = accountKey,
        )

    override fun createDataSource(account: UiAccount): MicroblogDataSource {
        require(account is UiAccount.VVo) {
            "Expected VVo account for ${type.name}, got ${account.platformType.name}"
        }
        return VVODataSource(
            accountKey = account.accountKey,
        )
    }

    override fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource = throw UnsupportedOperationException("${type.name} guest data source is not supported yet")
}
