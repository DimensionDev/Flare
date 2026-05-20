package dev.dimension.flare.data.platform

import dev.dimension.flare.common.deeplink.DeepLinkMapping
import dev.dimension.flare.common.deeplink.DeepLinkPattern
import dev.dimension.flare.common.tryRun
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.timeline.TimelineSpec
import dev.dimension.flare.data.datasource.misskey.MisskeyDataSource
import dev.dimension.flare.data.network.misskey.JoinMisskeyService
import dev.dimension.flare.data.network.misskey.MisskeyPlatformDetector
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.data.network.misskey.api.model.MetaRequest
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
import dev.dimension.flare.model.SocialPlatformPlugin
import dev.dimension.flare.model.SocialPlatformSpec
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiInstance
import dev.dimension.flare.ui.model.UiInstanceMetadata
import dev.dimension.flare.ui.model.mapper.render
import io.ktor.http.Url
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

public data object MisskeySocialPlatformPlugin : SocialPlatformPlugin {
    public override val spec: SocialPlatformSpec = MisskeySocialPlatformSpec

    public override fun createDataSource(account: UiAccount): MicroblogDataSource? =
        (account as? UiAccount.Misskey)?.let {
            MisskeyDataSource(
                accountKey = it.accountKey,
                host = it.host,
            )
        }

    public override suspend fun recommendedInstances(): List<UiInstance> =
        tryRun {
            JoinMisskeyService
                .instances()
                .instancesInfos
                .map {
                    UiInstance(
                        name = it.name,
                        description = it.description,
                        iconUrl = it.meta?.iconURL,
                        domain = it.url,
                        type = PlatformType.Misskey,
                        bannerUrl = it.meta?.bannerURL,
                        usersCount =
                            it.stats?.usersCount ?: it.nodeinfo
                                ?.usage
                                ?.users
                                ?.total ?: 0,
                    )
                }.sortedByDescending { it.usersCount }
        }.getOrDefault(emptyList())
}

public data object MisskeySocialPlatformSpec : SocialPlatformSpec {
    public override val type: PlatformType = PlatformType.Misskey
    public override val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> = MisskeyTimelineSpecs.timelineSpecs
    public override val metadata: PlatformTypeMetadata =
        PlatformTypeMetadata(
            displayName = "Misskey",
            icon = UiIcon.Misskey,
        )
    public override val detector: PlatformDetector = MisskeyPlatformDetector

    public override fun agreementUrl(host: String): String = "https://$host/about"

    public override fun deepLinkPatterns(host: String): ImmutableList<DeepLinkPattern<out DeepLinkMapping.Type>> =
        persistentListOf(
            DeepLinkPattern(DeepLinkMapping.Type.Profile.serializer(), Url("https://$host/@{handle}")),
            DeepLinkPattern(DeepLinkMapping.Type.Post.serializer(), Url("https://$host/notes/{id}")),
        )

    public override suspend fun instanceMetadata(host: String): UiInstanceMetadata =
        MisskeyService("https://$host/api/").meta(MetaRequest()).render()

    public override fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource = unsupportedGuestDataSource()
}

private fun unsupportedGuestDataSource(): Nothing = throw UnsupportedOperationException("Misskey guest data source is not supported yet.")
