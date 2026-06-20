package dev.dimension.flare.data.datasource.fanbox

import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.DatabaseUpdater
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.data.datasource.microblog.datasource.ArticleDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.PinnableTimelineTabDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.PinnableTimelineTabSection
import dev.dimension.flare.data.datasource.microblog.datasource.PostDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.RelationDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.TimelineTabConfigurationDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.UserDataSource
import dev.dimension.flare.data.datasource.microblog.handler.PostEventHandler
import dev.dimension.flare.data.datasource.microblog.handler.PostHandler
import dev.dimension.flare.data.datasource.microblog.handler.RelationHandler
import dev.dimension.flare.data.datasource.microblog.handler.UserHandler
import dev.dimension.flare.data.datasource.microblog.loader.RelationActionType
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.notSupported
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.tab.ShortcutSpec
import dev.dimension.flare.data.model.tab.TimelineCandidate
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.network.fanbox.FanboxPostIdRequest
import dev.dimension.flare.data.network.fanbox.FanboxService
import dev.dimension.flare.data.network.fanbox.requireCsrfToken
import dev.dimension.flare.data.platform.CommonTimelineSpecs
import dev.dimension.flare.data.platform.FANBOX_WEB_HOST
import dev.dimension.flare.data.platform.FanboxCredential
import dev.dimension.flare.data.platform.FanboxPlatformSpec
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiArticle
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow

internal class FanboxDataSource(
    override val accountKey: MicroBlogKey,
    private val credentialFlow: Flow<FanboxCredential>,
    private val updateCredential: suspend (FanboxCredential) -> Unit,
) : AuthenticatedMicroblogDataSource,
    PinnableTimelineTabDataSource,
    TimelineTabConfigurationDataSource,
    ArticleDataSource,
    UserDataSource,
    PostDataSource,
    RelationDataSource,
    PostEventHandler.Handler {
    private val service =
        FanboxService(
            credentialFlow = credentialFlow,
            onCredentialRefreshed = updateCredential,
        )
    private val loader by lazy {
        FanboxLoader(
            accountKey = accountKey,
            service = service,
        )
    }

    override val userHandler by lazy {
        UserHandler(
            host = accountKey.host,
            loader = loader,
        )
    }

    override val postHandler by lazy {
        PostHandler(
            accountType = AccountType.Specific(accountKey),
            loader = loader,
        )
    }

    override val postEventHandler by lazy {
        PostEventHandler(
            accountType = AccountType.Specific(accountKey),
            handler = this,
        )
    }

    override val relationHandler by lazy {
        RelationHandler(
            accountType = AccountType.Specific(accountKey),
            dataSource = loader,
        )
    }

    override val supportedRelationTypes: Set<RelationActionType> = loader.supportedTypes

    override val pinnableTimelineTabs: List<PinnableTimelineTabSection> = emptyList()

    override val defaultTabs: ImmutableList<TimelineCandidate<*>> by lazy {
        persistentListOf(
            CommonTimelineSpecs.home.candidate(
                data = TimelineSpec.AccountBasedData(accountKey),
                icon = IconType.FavIcon("https://$FANBOX_WEB_HOST/"),
                title = UiText.Raw("FANBOX"),
            ),
        )
    }

    override val builtInTimelineTabs: ImmutableList<TimelineCandidate<*>> by lazy {
        persistentListOf(
            CommonTimelineSpecs.home.candidate(
                data = TimelineSpec.AccountBasedData(accountKey),
                icon = IconType.Material(UiIcon.Fanbox),
            ),
            CommonTimelineSpecs.discover.candidate(
                data = TimelineSpec.AccountBasedData(accountKey),
                icon = IconType.Material(UiIcon.Fanbox),
            ),
            FanboxPlatformSpec.supportedTimelineSpec.candidate(
                data = TimelineSpec.AccountBasedData(accountKey),
            ),
        )
    }

    override val shortcuts: ImmutableList<ShortcutSpec> by lazy {
        persistentListOf(
            ShortcutSpec(
                title = UiStrings.Home,
                icon = UiIcon.Home,
                target =
                    ShortcutSpec.Target.Timeline(
                        CommonTimelineSpecs.home.candidate(
                            data = TimelineSpec.AccountBasedData(accountKey),
                            icon = IconType.Material(UiIcon.Fanbox),
                        ),
                    ),
            ),
            ShortcutSpec(
                title = UiStrings.FanboxRecommendedCreators,
                icon = UiIcon.Featured,
                target =
                    ShortcutSpec.Target.Timeline(
                        CommonTimelineSpecs.discover.candidate(
                            data = TimelineSpec.AccountBasedData(accountKey),
                            icon = IconType.Material(UiIcon.Fanbox),
                        ),
                    ),
            ),
            ShortcutSpec(
                title = UiStrings.FanboxSupported,
                icon = UiIcon.Heart,
                target =
                    ShortcutSpec.Target.Timeline(
                        FanboxPlatformSpec.supportedTimelineSpec.candidate(
                            data = TimelineSpec.AccountBasedData(accountKey),
                        ),
                    ),
            ),
        )
    }

    override fun homeTimeline(): RemoteLoader<UiTimelineV2> =
        FanboxHomeTimelineLoader(
            service = service,
            accountKey = accountKey,
        )

    override fun userTimeline(
        userKey: MicroBlogKey,
        mediaOnly: Boolean,
    ): RemoteLoader<UiTimelineV2> =
        FanboxCreatorTimelineLoader(
            service = service,
            accountKey = accountKey,
            creatorKey = userKey,
        )

    override fun context(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> =
        FanboxStatusDetailLoader(
            service = service,
            accountKey = accountKey,
            statusKey = statusKey,
        )

    override fun searchStatus(query: String): RemoteLoader<UiTimelineV2> =
        FanboxSearchTimelineLoader(
            service = service,
            accountKey = accountKey,
            query = query,
        )

    override fun searchUser(query: String): RemoteLoader<UiProfile> =
        FanboxSearchCreatorLoader(
            service = service,
            accountKey = accountKey,
            query = query,
        )

    override fun discoverUsers(): RemoteLoader<UiProfile> =
        FanboxRecommendedCreatorLoader(
            service = service,
            accountKey = accountKey,
        )

    override fun discoverStatuses(): RemoteLoader<UiTimelineV2> =
        FanboxRecommendedCreatorsTimelineLoader(
            service = service,
            accountKey = accountKey,
        )

    override fun discoverHashtags(): RemoteLoader<UiHashtag> = notSupported()

    override fun following(userKey: MicroBlogKey): RemoteLoader<UiProfile> =
        FanboxFollowingCreatorLoader(
            service = service,
            accountKey = accountKey,
        )

    override fun fans(userKey: MicroBlogKey): RemoteLoader<UiProfile> = notSupported()

    fun supportedTimelineLoader(): RemoteLoader<UiTimelineV2> =
        FanboxSupportedTimelineLoader(
            service = service,
            accountKey = accountKey,
        )

    override suspend fun article(articleKey: MicroBlogKey): UiArticle =
        service
            .postInfo(postId = articleKey.id)
            .body
            .toUiArticle(
                accountKey = accountKey,
                imageHeaders = service.fanboxImageHeaders(),
            )

    override fun profileTabs(userKey: MicroBlogKey): ImmutableList<ProfileTab> =
        persistentListOf(
            ProfileTab(
                name = UiStrings.Posts,
                loader =
                    FanboxCreatorTimelineLoader(
                        service = service,
                        accountKey = accountKey,
                        creatorKey = userKey,
                    ),
            ),
        )

    override suspend fun handle(
        event: PostEvent,
        updater: DatabaseUpdater,
    ) {
        require(event is PostEvent.Fanbox)
        when (event) {
            is PostEvent.Fanbox.Like -> {
                if (!event.liked) {
                    val credential = service.credentialWithCsrf()
                    service.likePost(
                        csrfToken = credential.requireCsrfToken(),
                        request = FanboxPostIdRequest(event.postKey.id),
                    )
                }
            }
        }
    }
}
