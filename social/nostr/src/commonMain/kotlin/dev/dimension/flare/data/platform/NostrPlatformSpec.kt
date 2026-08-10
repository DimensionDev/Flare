package dev.dimension.flare.data.platform

import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.nostr.NostrDataSource
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformCapability
import dev.dimension.flare.model.PlatformDataSourceContext
import dev.dimension.flare.model.PlatformDeepLink
import dev.dimension.flare.model.PlatformMetadata
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.presenter.login.LoginPlatformProvider
import dev.dimension.flare.ui.presenter.login.NostrLoginProvider
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlin.native.HiddenFromObjC

internal const val NOSTR_PLATFORM_ID: String = "Nostr"

@HiddenFromObjC
public data object NostrPlatformSpec :
    PlatformSpec,
    LoginPlatformProvider by NostrLoginProvider {
    public override val platformId: String = NOSTR_PLATFORM_ID
    public override val metadata: PlatformMetadata =
        PlatformMetadata(
            displayName = "Nostr",
            icon = UiIcon.Nostr,
            agentAliases = listOf("nostr"),
        )
    public override val order: Int = 0
    public override val capabilities: Set<PlatformCapability> = setOf(PlatformCapability.RelayManagement)

    override fun deepLinks(accountKey: MicroBlogKey): ImmutableList<PlatformDeepLink<*>> = persistentListOf()

    override val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> =
        persistentListOf(
            CommonTimelineSpecs.home,
        )

    override fun createDataSource(context: PlatformDataSourceContext): MicroblogDataSource =
        NostrDataSource(
            accountKey = context.accountKey,
            credentialFlow = context.credentialFlow(NostrCredential.serializer()),
        )

    override fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource = throw UnsupportedOperationException("$platformId guest data source is not supported yet")
}
