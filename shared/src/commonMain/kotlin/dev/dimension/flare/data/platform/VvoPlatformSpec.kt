package dev.dimension.flare.data.platform

import dev.dimension.flare.common.deeplink.DeepLinkMapping
import dev.dimension.flare.common.deeplink.DeepLinkPattern
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.TabItem
import dev.dimension.flare.data.model.TabMetaData
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.data.model.VVo
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.data.network.vvo.VVOPlatformDetector
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
import dev.dimension.flare.model.vvo
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiInstanceMetadata
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

internal data object VvoPlatformSpec : PlatformSpec {
    override val type = PlatformType.VVo
    override val metadata =
        PlatformTypeMetadata(
            displayName = vvo,
            logoUrl = "https://upload.wikimedia.org/wikipedia/en/thumb/6/6e/Sina_Weibo.svg/2560px-Sina_Weibo.svg.png",
            icon = UiIcon.Weibo,
        )
    override val detector: PlatformDetector = VVOPlatformDetector

    override fun agreementUrl(host: String): String? = null

    override fun deepLinkPatterns(host: String): ImmutableList<DeepLinkPattern<out DeepLinkMapping.Type>> = persistentListOf()

    override fun secondary(accountKey: MicroBlogKey): ImmutableList<TabItem> =
        persistentListOf(
            VVo.FeaturedTimelineTabItem(
                AccountType.Specific(accountKey),
                TabMetaData(
                    title = TitleType.Localized(TitleType.Localized.LocalizedKey.Featured),
                    icon = IconType.Mixed(dev.dimension.flare.ui.model.UiIcon.Featured, accountKey),
                ),
            ),
            VVo.FavoriteTimelineTabItem(
                AccountType.Specific(accountKey),
                TabMetaData(
                    title = TitleType.Localized(TitleType.Localized.LocalizedKey.Bookmark),
                    icon = IconType.Mixed(dev.dimension.flare.ui.model.UiIcon.Bookmark, accountKey),
                ),
            ),
            VVo.LikedTimelineTabItem(
                AccountType.Specific(accountKey),
                TabMetaData(
                    title = TitleType.Localized(TitleType.Localized.LocalizedKey.Liked),
                    icon = IconType.Mixed(dev.dimension.flare.ui.model.UiIcon.Heart, accountKey),
                ),
            ),
        )

    override suspend fun instanceMetadata(host: String): UiInstanceMetadata =
        throw UnsupportedOperationException("${type.name} is not supported yet")

    override fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource = throw UnsupportedOperationException("${type.name} guest data source is not supported yet")
}
