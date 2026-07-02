package dev.dimension.flare.data.platform

import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.vvo.VVODataSource
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.model.tab.TimelineSpecIds
import dev.dimension.flare.data.model.tab.accountLoader
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformDataSourceContext
import dev.dimension.flare.model.PlatformDeepLink
import dev.dimension.flare.model.PlatformSpec
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.PlatformTypeMetadata
import dev.dimension.flare.model.vvo
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.asType
import dev.dimension.flare.ui.presenter.login.LoginPlatformProvider
import dev.dimension.flare.ui.presenter.login.VVOLoginProvider
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public data object VvoPlatformSpec :
    PlatformSpec,
    LoginPlatformProvider by VVOLoginProvider {
    public override val type: PlatformType = PlatformType.VVo
    public override val metadata: PlatformTypeMetadata =
        PlatformTypeMetadata(
            displayName = vvo,
            icon = UiIcon.Weibo,
        )

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
    ): MicroblogDataSource = throw UnsupportedOperationException("${type.name} guest data source is not supported yet")
}
