package dev.dimension.flare.data.platform

import dev.dimension.flare.common.deeplink.DeepLinkMapping
import dev.dimension.flare.common.deeplink.DeepLinkPattern
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.xqt.XQTDataSource
import dev.dimension.flare.data.datasource.microblog.timeline.TimelineSpec
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.data.network.xqt.XQTPlatformDetector
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
import dev.dimension.flare.model.SocialPlatformPlugin
import dev.dimension.flare.model.SocialPlatformSpec
import dev.dimension.flare.model.xqtHost
import dev.dimension.flare.model.xqtOldHost
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiInstance
import dev.dimension.flare.ui.model.UiInstanceMetadata
import io.ktor.http.Url
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

public data object XqtSocialPlatformPlugin : SocialPlatformPlugin {
    public override val spec: SocialPlatformSpec = XqtSocialPlatformSpec

    public override fun createDataSource(account: UiAccount): MicroblogDataSource? =
        (account as? UiAccount.XQT)?.let {
            XQTDataSource(
                accountKey = it.accountKey,
            )
        }

    public override suspend fun recommendedInstances(): List<UiInstance> =
        listOf(
            UiInstance(
                name = "X",
                description =
                    "From breaking news and entertainment to sports and politics," +
                        " get the full story with all the live commentary.",
                iconUrl = null,
                domain = "x.com",
                type = PlatformType.xQt,
                bannerUrl = null,
                usersCount = 0,
            ),
        )
}

public data object XqtSocialPlatformSpec : SocialPlatformSpec {
    public override val type: PlatformType = PlatformType.xQt
    public override val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> = XqtTimelineSpecs.timelineSpecs
    public override val metadata: PlatformTypeMetadata =
        PlatformTypeMetadata(
            displayName = "X",
            icon = UiIcon.X,
        )
    public override val detector: PlatformDetector = XQTPlatformDetector

    public override fun agreementUrl(host: String): String = "https://help.x.com/en/rules-and-policies/x-rules"

    public override fun deepLinkPatterns(host: String): ImmutableList<DeepLinkPattern<out DeepLinkMapping.Type>> {
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

    public override suspend fun instanceMetadata(host: String): UiInstanceMetadata =
        throw UnsupportedOperationException("${type.name} is not supported yet")

    public override fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource =
        throw UnsupportedOperationException("${type.name} guest data source is not supported yet")
}
