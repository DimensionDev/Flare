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
import dev.dimension.flare.data.network.pixiv.PixivRankingMode
import dev.dimension.flare.data.network.pixiv.PixivService
import dev.dimension.flare.data.network.pixiv.PixivWorkType
import dev.dimension.flare.data.platform.CommonTimelineSpecs
import dev.dimension.flare.data.platform.PixivCredential
import dev.dimension.flare.data.platform.PixivPlatformSpec
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiStrings
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
            CommonTimelineSpecs.home.galleryTabItem(
                data = TimelineSpec.AccountBasedData(accountKey),
                icon = IconType.FavIcon("https://pixiv.net/"),
                title = UiText.Raw("pixiv"),
            ),
        )
    }

    override val builtInTimelineTabs: ImmutableList<TimelineTabItemV2> by lazy {
        persistentListOf(
            CommonTimelineSpecs.home.galleryTabItem(
                data = TimelineSpec.AccountBasedData(accountKey),
                icon = IconType.Material(UiIcon.Pixiv),
            ),
            CommonTimelineSpecs.discover.galleryTabItem(
                data = TimelineSpec.AccountBasedData(accountKey),
                icon = IconType.Material(UiIcon.Pixiv),
            ),
            PixivPlatformSpec.followingTimelineSpec.galleryTabItem(
                data = TimelineSpec.AccountBasedData(accountKey),
            ),
            PixivPlatformSpec.bookmarkTimelineSpec.galleryTabItem(
                data = TimelineSpec.AccountBasedData(accountKey),
            ),
            PixivPlatformSpec.rankingWeekTimelineSpec.galleryTabItem(
                data = TimelineSpec.AccountBasedData(accountKey),
            ),
            PixivPlatformSpec.rankingMonthTimelineSpec.galleryTabItem(
                data = TimelineSpec.AccountBasedData(accountKey),
            ),
            PixivPlatformSpec.rankingDayMaleTimelineSpec.galleryTabItem(
                data = TimelineSpec.AccountBasedData(accountKey),
            ),
            PixivPlatformSpec.rankingDayFemaleTimelineSpec.galleryTabItem(
                data = TimelineSpec.AccountBasedData(accountKey),
            ),
            PixivPlatformSpec.rankingWeekOriginalTimelineSpec.galleryTabItem(
                data = TimelineSpec.AccountBasedData(accountKey),
            ),
            PixivPlatformSpec.rankingWeekRookieTimelineSpec.galleryTabItem(
                data = TimelineSpec.AccountBasedData(accountKey),
            ),
            PixivPlatformSpec.rankingDayMangaTimelineSpec.galleryTabItem(
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
                        CommonTimelineSpecs.home.galleryTabItem(
                            data = TimelineSpec.AccountBasedData(accountKey),
                            icon = IconType.Material(UiIcon.Pixiv),
                        ),
                    ),
            ),
            ShortcutSpec(
                title = UiStrings.Discover,
                icon = UiIcon.Search,
                target =
                    ShortcutSpec.Target.Timeline(
                        CommonTimelineSpecs.discover.galleryTabItem(
                            data = TimelineSpec.AccountBasedData(accountKey),
                            icon = IconType.Material(UiIcon.Pixiv),
                        ),
                    ),
            ),
            ShortcutSpec(
                title = UiStrings.Following,
                icon = UiIcon.Follow,
                target =
                    ShortcutSpec.Target.Timeline(
                        PixivPlatformSpec.followingTimelineSpec.galleryTabItem(
                            data = TimelineSpec.AccountBasedData(accountKey),
                        ),
                    ),
            ),
            ShortcutSpec(
                title = UiStrings.Bookmark,
                icon = UiIcon.Bookmark,
                target =
                    ShortcutSpec.Target.Timeline(
                        PixivPlatformSpec.bookmarkTimelineSpec.galleryTabItem(
                            data = TimelineSpec.AccountBasedData(accountKey),
                        ),
                    ),
            ),
            ShortcutSpec(
                title = UiStrings.PixivRankingWeek,
                icon = UiIcon.Featured,
                target =
                    ShortcutSpec.Target.Timeline(
                        PixivPlatformSpec.rankingWeekTimelineSpec.galleryTabItem(
                            data = TimelineSpec.AccountBasedData(accountKey),
                        ),
                    ),
            ),
            ShortcutSpec(
                title = UiStrings.PixivRankingMonth,
                icon = UiIcon.Featured,
                target =
                    ShortcutSpec.Target.Timeline(
                        PixivPlatformSpec.rankingMonthTimelineSpec.galleryTabItem(
                            data = TimelineSpec.AccountBasedData(accountKey),
                        ),
                    ),
            ),
            ShortcutSpec(
                title = UiStrings.PixivRankingDayMale,
                icon = UiIcon.Featured,
                target =
                    ShortcutSpec.Target.Timeline(
                        PixivPlatformSpec.rankingDayMaleTimelineSpec.galleryTabItem(
                            data = TimelineSpec.AccountBasedData(accountKey),
                        ),
                    ),
            ),
            ShortcutSpec(
                title = UiStrings.PixivRankingDayFemale,
                icon = UiIcon.Featured,
                target =
                    ShortcutSpec.Target.Timeline(
                        PixivPlatformSpec.rankingDayFemaleTimelineSpec.galleryTabItem(
                            data = TimelineSpec.AccountBasedData(accountKey),
                        ),
                    ),
            ),
            ShortcutSpec(
                title = UiStrings.PixivRankingWeekOriginal,
                icon = UiIcon.Featured,
                target =
                    ShortcutSpec.Target.Timeline(
                        PixivPlatformSpec.rankingWeekOriginalTimelineSpec.galleryTabItem(
                            data = TimelineSpec.AccountBasedData(accountKey),
                        ),
                    ),
            ),
            ShortcutSpec(
                title = UiStrings.PixivRankingWeekRookie,
                icon = UiIcon.Featured,
                target =
                    ShortcutSpec.Target.Timeline(
                        PixivPlatformSpec.rankingWeekRookieTimelineSpec.galleryTabItem(
                            data = TimelineSpec.AccountBasedData(accountKey),
                        ),
                    ),
            ),
            ShortcutSpec(
                title = UiStrings.PixivRankingDayManga,
                icon = UiIcon.Featured,
                target =
                    ShortcutSpec.Target.Timeline(
                        PixivPlatformSpec.rankingDayMangaTimelineSpec.galleryTabItem(
                            data = TimelineSpec.AccountBasedData(accountKey),
                        ),
                    ),
            ),
        )
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
            type = PixivWorkType.Illust,
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

    fun rankingTimelineLoader(mode: PixivRankingMode): RemoteLoader<UiTimelineV2> =
        PixivRankingTimelineLoader(
            service = service,
            accountKey = accountKey,
            mode = mode,
        )

    override fun following(userKey: MicroBlogKey): RemoteLoader<UiProfile> = notSupported()

    override fun fans(userKey: MicroBlogKey): RemoteLoader<UiProfile> = notSupported()

    override fun profileTabs(userKey: MicroBlogKey): ImmutableList<ProfileTab> =
        persistentListOf(
            ProfileTab(
                name = UiStrings.Illustrations,
                displayType = ProfileTab.DisplayType.Gallery,
                showAllImagesInGallery = false,
                loader =
                    PixivUserTimelineLoader(
                        service = service,
                        accountKey = accountKey,
                        userKey = userKey,
                        type = PixivWorkType.Illust,
                    ),
            ),
            ProfileTab(
                name = UiStrings.Manga,
                displayType = ProfileTab.DisplayType.Gallery,
                showAllImagesInGallery = false,
                loader =
                    PixivUserTimelineLoader(
                        service = service,
                        accountKey = accountKey,
                        userKey = userKey,
                        type = PixivWorkType.Manga,
                    ),
            ),
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
