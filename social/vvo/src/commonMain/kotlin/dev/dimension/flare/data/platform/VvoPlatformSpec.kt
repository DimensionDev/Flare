package dev.dimension.flare.data.platform

import dev.dimension.flare.data.datasource.microblog.ComposeType
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.vvo.VVODataSource
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.model.tab.TimelineSpecIds
import dev.dimension.flare.data.model.tab.accountLoader
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.ComposeInitialTextContext
import dev.dimension.flare.model.InitialText
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformCapability
import dev.dimension.flare.model.PlatformDataSourceContext
import dev.dimension.flare.model.PlatformDeepLink
import dev.dimension.flare.model.PlatformMetadata
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.model.vvo
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.asType
import dev.dimension.flare.ui.presenter.login.LoginPlatformProvider
import dev.dimension.flare.ui.presenter.login.VVOLoginProvider
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlin.native.HiddenFromObjC

internal const val VVO_PLATFORM_ID: String = "VVo"

@HiddenFromObjC
public data object VvoPlatformSpec :
    PlatformSpec,
    LoginPlatformProvider by VVOLoginProvider {
    public override val platformId: String = VVO_PLATFORM_ID
    public override val metadata: PlatformMetadata =
        PlatformMetadata(
            displayName = vvo,
            icon = UiIcon.Weibo,
            agentAliases = listOf("weibo", "sina weibo", "微博", "新浪微博"),
        )
    public override val order: Int = 70
    public override val capabilities: Set<PlatformCapability> = setOf(PlatformCapability.FirstEmbeddedQuoteTarget)

    override fun resolveInitialText(context: ComposeInitialTextContext): InitialText? {
        if (context.composeType != ComposeType.Quote || context.quotes.isEmpty()) return null
        return InitialText(
            text = "//@${context.post.user?.name?.raw}:${context.post.content.original.raw}",
            cursorPosition = 0,
        )
    }

    override fun deepLinks(accountKey: MicroBlogKey): ImmutableList<PlatformDeepLink<*>> = persistentListOf()

    internal val favoriteTimelineSpec =
        TimelineSpec(
            id = TimelineSpecIds.VVO_FAVORITE,
            title = UiStrings.Bookmark,
            icon = UiIcon.Bookmark.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            loaderFactory =
                accountLoader<VVODataSource, TimelineSpec.AccountBasedData> {
                    favouriteTimeline()
                },
        )

    internal val likedTimelineSpec =
        TimelineSpec(
            id = TimelineSpecIds.VVO_LIKED,
            title = UiStrings.Liked,
            icon = UiIcon.Heart.asType(),
            serializer = TimelineSpec.AccountBasedData.serializer(),
            targetId = { it.accountKey.toString() },
            loaderFactory =
                accountLoader<VVODataSource, TimelineSpec.AccountBasedData> {
                    likeRemoteMediator()
                },
        )

    override val timelineSpecs: ImmutableList<TimelineSpec<out TimelineSpec.Data>> =
        persistentListOf(
            CommonTimelineSpecs.home,
            CommonTimelineSpecs.discover,
            favoriteTimelineSpec,
            likedTimelineSpec,
        )

    override fun createDataSource(context: PlatformDataSourceContext): MicroblogDataSource =
        VVODataSource(
            accountKey = context.accountKey,
            credentialFlow = context.credentialFlow(VVoCredential.serializer()),
            updateCredential = { credential ->
                context.updateCredential(
                    serializer = VVoCredential.serializer(),
                    credential = credential,
                )
            },
        )

    override fun guestDataSource(
        host: String,
        locale: String,
    ): MicroblogDataSource = throw UnsupportedOperationException("$platformId guest data source is not supported yet")
}
