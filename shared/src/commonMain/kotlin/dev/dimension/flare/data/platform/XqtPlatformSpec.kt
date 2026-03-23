package dev.dimension.flare.data.platform

import dev.dimension.flare.common.deeplink.DeepLinkMapping
import dev.dimension.flare.common.deeplink.DeepLinkPattern
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.model.AllListTabItem
import dev.dimension.flare.data.model.DirectMessageTabItem
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.TabItem
import dev.dimension.flare.data.model.TabMetaData
import dev.dimension.flare.data.model.TitleType
import dev.dimension.flare.data.model.XQT
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.data.network.xqt.XQTPlatformDetector
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
import dev.dimension.flare.model.xqtHost
import dev.dimension.flare.model.xqtOldHost
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiInstanceMetadata
import io.ktor.http.Url
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

internal data object XqtPlatformSpec : PlatformSpec {
    override val type = PlatformType.xQt
    override val metadata =
        PlatformTypeMetadata(
            displayName = "X",
            logoUrl =
                "https://upload.wikimedia.org/wikipedia/commons" +
                    "/thumb/5/53/X_logo_2023_original.svg/1920px-X_logo_2023_original.svg.png",
            icon = UiIcon.X,
        )
    override val detector: PlatformDetector = XQTPlatformDetector

    override fun agreementUrl(host: String): String? = "https://help.x.com/en/rules-and-policies/x-rules"

    override fun deepLinkPatterns(host: String): ImmutableList<DeepLinkPattern<out DeepLinkMapping.Type>> {
        val profile =
            listOf(
                "https://$xqtHost/{handle}",
                "https://$xqtOldHost/{handle}",
                "https://www.$xqtHost/{handle}",
                "https://www.$xqtOldHost/{handle}",
            )
        val post =
            listOf(
                "https://$xqtHost/{handle}/status/{id}",
                "https://$xqtOldHost/{handle}/",
                "https://www.$xqtHost/{handle}/status/{id}",
                "https://www.$xqtOldHost/{handle}/",
            )
        val media =
            listOf(
                "https://$xqtHost/{handle}/status/{id}/photo/{index}",
                "https://$xqtOldHost/{handle}/status/{id}/photo/{index}",
                "https://www.$xqtHost/{handle}/status/{id}/photo/{index}",
                "https://www.$xqtOldHost/{handle}/status/{id}/photo/{index}",
            )
        return (
            profile.map { DeepLinkPattern(DeepLinkMapping.Type.Profile.serializer(), Url(it)) } +
                post.map { DeepLinkPattern(DeepLinkMapping.Type.Post.serializer(), Url(it)) } +
                media.map { DeepLinkPattern(DeepLinkMapping.Type.PostMedia.serializer(), Url(it)) }
        ).toImmutableList()
    }

    override fun secondary(accountKey: MicroBlogKey): ImmutableList<TabItem> =
        persistentListOf(
            XQT.FeaturedTimelineTabItem(
                AccountType.Specific(accountKey),
                TabMetaData(
                    title = TitleType.Localized(TitleType.Localized.LocalizedKey.Featured),
                    icon = IconType.Mixed(dev.dimension.flare.ui.model.UiIcon.Featured, accountKey),
                ),
            ),
            XQT.BookmarkTimelineTabItem(
                AccountType.Specific(accountKey),
                TabMetaData(
                    title = TitleType.Localized(TitleType.Localized.LocalizedKey.Bookmark),
                    icon = IconType.Mixed(dev.dimension.flare.ui.model.UiIcon.Bookmark, accountKey),
                ),
            ),
            AllListTabItem(
                AccountType.Specific(accountKey),
                TabMetaData(
                    title = TitleType.Localized(TitleType.Localized.LocalizedKey.List),
                    icon = IconType.Mixed(dev.dimension.flare.ui.model.UiIcon.List, accountKey),
                ),
            ),
            DirectMessageTabItem(
                AccountType.Specific(accountKey),
                TabMetaData(
                    title = TitleType.Localized(TitleType.Localized.LocalizedKey.DirectMessage),
                    icon = IconType.Mixed(dev.dimension.flare.ui.model.UiIcon.Messages, accountKey),
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
