package dev.dimension.flare.data.platform

import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.nostr.NostrDataSource
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.network.nodeinfo.PlatformDetector
import dev.dimension.flare.data.network.nostr.NostrPlatformDetector
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformDataSourceContext
import dev.dimension.flare.model.PlatformDeepLink
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
import dev.dimension.flare.model.RecommendedInstance
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiInstance
import dev.dimension.flare.ui.model.UiInstanceMetadata
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public data object NostrPlatformSpec : PlatformSpec {
    public override val type: PlatformType = PlatformType.Nostr
    public override val metadata: PlatformTypeMetadata =
        PlatformTypeMetadata(
            displayName = "Nostr",
            icon = UiIcon.Nostr,
        )
    override val detector: PlatformDetector = NostrPlatformDetector

    override fun agreementUrl(host: String): String? = null

    override fun deepLinks(accountKey: MicroBlogKey): ImmutableList<PlatformDeepLink<*>> = persistentListOf()

    override val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> =
        persistentListOf(
            CommonTimelineSpecs.home,
        )

    override suspend fun instanceMetadata(host: String): UiInstanceMetadata =
        throw UnsupportedOperationException("${type.name} is not supported yet")

    override suspend fun recommendInstances(): List<RecommendedInstance> =
        listOf(
            RecommendedInstance(
                instance =
                    UiInstance(
                        name = metadata.displayName,
                        description =
                            "A decentralized network based on cryptographic keypairs and that is not peer-to-peer, " +
                                "it is super simple and scalable and therefore has a chance of working.",
                        iconUrl = null,
                        domain = "nostr",
                        type = type,
                        bannerUrl = null,
                        usersCount = 0,
                    ),
                priority = 60,
            ),
        )

    override fun createDataSource(context: PlatformDataSourceContext): MicroblogDataSource =
        NostrDataSource(
            accountKey = context.accountKey,
            credentialFlow = context.credentialFlow(NostrCredential.serializer()),
        )

    override fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource = throw UnsupportedOperationException("${type.name} guest data source is not supported yet")
}
