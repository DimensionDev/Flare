package dev.dimension.flare.data.platform

import dev.dimension.flare.common.deeplink.DeepLinkMapping
import dev.dimension.flare.common.deeplink.DeepLinkPattern
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.vvo.VVODataSource
import dev.dimension.flare.data.datasource.microblog.timeline.TimelineSpec
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.data.network.vvo.VVOPlatformDetector
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
import dev.dimension.flare.model.SocialPlatformPlugin
import dev.dimension.flare.model.SocialPlatformSpec
import dev.dimension.flare.model.vvo
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiInstanceMetadata
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

public data object VvoSocialPlatformPlugin : SocialPlatformPlugin {
    public override val spec: SocialPlatformSpec = VvoSocialPlatformSpec

    public override fun createDataSource(account: UiAccount): MicroblogDataSource? =
        (account as? UiAccount.VVo)?.let {
            VVODataSource(
                accountKey = it.accountKey,
            )
        }
}

public data object VvoSocialPlatformSpec : SocialPlatformSpec {
    public override val type: PlatformType = PlatformType.VVo
    public override val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> = VvoTimelineSpecs.timelineSpecs
    public override val metadata: PlatformTypeMetadata =
        PlatformTypeMetadata(
            displayName = vvo,
            icon = UiIcon.Weibo,
        )
    public override val detector: PlatformDetector = VVOPlatformDetector

    public override fun agreementUrl(host: String): String? = null

    public override fun deepLinkPatterns(host: String): ImmutableList<DeepLinkPattern<out DeepLinkMapping.Type>> = persistentListOf()

    public override suspend fun instanceMetadata(host: String): UiInstanceMetadata =
        throw UnsupportedOperationException("${type.name} is not supported yet")

    public override fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource =
        throw UnsupportedOperationException("${type.name} guest data source is not supported yet")
}
