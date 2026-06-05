package dev.dimension.flare.data.datasource.pixiv

import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.DatabaseUpdater
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.data.datasource.microblog.ProfileTab
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
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.model.tab.TimelineTabItemV2
import dev.dimension.flare.data.network.pixiv.PixivService
import dev.dimension.flare.data.platform.CommonTimelineSpecs
import dev.dimension.flare.data.platform.PixivCredential
import dev.dimension.flare.data.platform.PixivPlatformSpec
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow

internal class PixivDataSource(
    override val accountKey: MicroBlogKey,
    private val credentialFlow: Flow<PixivCredential>,
    private val updateCredential: suspend (PixivCredential) -> Unit,
) : AuthenticatedMicroblogDataSource,
    PinnableTimelineTabDataSource,
    TimelineTabConfigurationDataSource,
    UserDataSource,
    PostDataSource,
    RelationDataSource,
    PostEventHandler.Handler {
    private val service =
        PixivService(
            accountKey = accountKey,
            credentialFlow = credentialFlow,
            onCredentialRefreshed = updateCredential,
        )
    private val loader by lazy {
        PixivLoader(
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

    override val defaultTabs: ImmutableList<TimelineTabItemV2> by lazy {
        persistentListOf(
            CommonTimelineSpecs.home.tabItem(
                data = TimelineSpec.AccountBasedData(accountKey),
                icon = IconType.Material(UiIcon.Pixiv),
                title = UiText.Raw("pixiv"),
            ),
        )
    }

    override val builtInTimelineTabs: ImmutableList<TimelineTabItemV2> by lazy {
        persistentListOf(
            CommonTimelineSpecs.home.tabItem(
                data = TimelineSpec.AccountBasedData(accountKey),
                icon = IconType.Material(UiIcon.Pixiv),
            ),
            CommonTimelineSpecs.discover.tabItem(
                data = TimelineSpec.AccountBasedData(accountKey),
                icon = IconType.Material(UiIcon.Pixiv),
            ),
            PixivPlatformSpec.followingTimelineSpec.tabItem(
                data = TimelineSpec.AccountBasedData(accountKey),
            ),
            PixivPlatformSpec.bookmarkTimelineSpec.tabItem(
                data = TimelineSpec.AccountBasedData(accountKey),
            ),
        )
    }

    override val shortcuts: ImmutableList<ShortcutSpec> by lazy {
        persistentListOf()
    }

    override fun homeTimeline(): RemoteLoader<UiTimelineV2> =
        PixivHomeTimelineLoader(
            service = service,
            accountKey = accountKey,
        )

    override fun userTimeline(
        userKey: MicroBlogKey,
        mediaOnly: Boolean,
    ): RemoteLoader<UiTimelineV2> =
        PixivUserTimelineLoader(
            service = service,
            accountKey = accountKey,
            userKey = userKey,
        )

    override fun context(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> =
        PixivStatusDetailLoader(
            service = service,
            accountKey = accountKey,
            statusKey = statusKey,
        )

    override fun searchStatus(query: String): RemoteLoader<UiTimelineV2> =
        PixivSearchTimelineLoader(
            service = service,
            accountKey = accountKey,
            query = query,
        )

    override fun searchUser(query: String): RemoteLoader<UiProfile> =
        PixivSearchUserLoader(
            service = service,
            accountKey = accountKey,
            query = query,
        )

    override fun discoverUsers(): RemoteLoader<UiProfile> =
        PixivDiscoverUserLoader(
            service = service,
            accountKey = accountKey,
        )

    override fun discoverStatuses(): RemoteLoader<UiTimelineV2> =
        PixivDiscoverTimelineLoader(
            service = service,
            accountKey = accountKey,
        )

    override fun discoverHashtags(): RemoteLoader<UiHashtag> =
        PixivTrendHashtagLoader(
            service = service,
        )

    fun bookmarkTimelineLoader(): RemoteLoader<UiTimelineV2> =
        PixivBookmarkTimelineLoader(
            service = service,
            credentialFlow = credentialFlow,
            accountKey = accountKey,
        )

    fun followingTimelineLoader(): RemoteLoader<UiTimelineV2> =
        PixivFollowingTimelineLoader(
            service = service,
            accountKey = accountKey,
        )

    override fun following(userKey: MicroBlogKey): RemoteLoader<UiProfile> = notSupported()

    override fun fans(userKey: MicroBlogKey): RemoteLoader<UiProfile> = notSupported()

    override fun profileTabs(userKey: MicroBlogKey): ImmutableList<ProfileTab> =
        persistentListOf(
            ProfileTab.Timeline(
                type = ProfileTab.Timeline.Type.Status,
                loader = userTimeline(userKey),
            ),
            ProfileTab.Media,
        )

    override suspend fun handle(
        event: PostEvent,
        updater: DatabaseUpdater,
    ) {
        require(event is PostEvent.Pixiv)
        when (event) {
            is PostEvent.Pixiv.Bookmark -> bookmark(event)
        }
    }

    private suspend fun bookmark(event: PostEvent.Pixiv.Bookmark) {
        val illustId = event.postKey.id.toLongOrNull() ?: throw IllegalArgumentException("Invalid Pixiv illust id: ${event.postKey.id}")
        if (event.bookmarked) {
            service.deleteBookmark(
                illustId = illustId,
            )
        } else {
            service.addBookmark(
                illustId = illustId,
            )
        }
    }
}
