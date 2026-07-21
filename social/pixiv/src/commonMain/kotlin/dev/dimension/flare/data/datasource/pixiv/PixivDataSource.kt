package dev.dimension.flare.data.datasource.pixiv

import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.DatabaseUpdater
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.data.datasource.microblog.datasource.GalleryDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.GalleryDetail
import dev.dimension.flare.data.datasource.microblog.datasource.GalleryOrientation
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
import dev.dimension.flare.data.network.pixiv.PixivRankingMode
import dev.dimension.flare.data.network.pixiv.PixivRestrict
import dev.dimension.flare.data.network.pixiv.PixivService
import dev.dimension.flare.data.network.pixiv.PixivWorkType
import dev.dimension.flare.data.platform.CommonTimelineSpecs
import dev.dimension.flare.data.platform.PixivCredential
import dev.dimension.flare.data.platform.PixivPlatformSpec
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.contentPostOrNull
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow

internal class PixivDataSource(
    override val accountKey: MicroBlogKey,
    private val credentialFlow: Flow<PixivCredential>,
    private val updateCredential: suspend (PixivCredential) -> Unit,
) : AuthenticatedMicroblogDataSource,
    PinnableTimelineTabDataSource,
    TimelineTabConfigurationDataSource,
    UserDataSource,
    GalleryDataSource,
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
            credentialFlow = credentialFlow,
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
            CommonTimelineSpecs.home.galleryCandidate(
                data = TimelineSpec.AccountBasedData(accountKey),
                icon = IconType.FavIcon("https://pixiv.net/"),
                title = UiText.Raw("pixiv"),
            ),
        )
    }

    override val builtInTimelineTabs: ImmutableList<TimelineCandidate<*>> by lazy {
        persistentListOf(
            CommonTimelineSpecs.home.galleryCandidate(
                data = TimelineSpec.AccountBasedData(accountKey),
                icon = IconType.Material(UiIcon.Pixiv),
            ),
            CommonTimelineSpecs.discover.galleryCandidate(
                data = TimelineSpec.AccountBasedData(accountKey),
                icon = IconType.Material(UiIcon.Pixiv),
            ),
            PixivPlatformSpec.followingTimelineSpec.galleryCandidate(
                data = TimelineSpec.AccountBasedData(accountKey),
            ),
            PixivPlatformSpec.privateFollowingTimelineSpec.galleryCandidate(
                data = TimelineSpec.AccountBasedData(accountKey),
            ),
            PixivPlatformSpec.bookmarkTimelineSpec.galleryCandidate(
                data = TimelineSpec.AccountBasedData(accountKey),
            ),
            PixivPlatformSpec.privateBookmarkTimelineSpec.galleryCandidate(
                data = TimelineSpec.AccountBasedData(accountKey),
            ),
            PixivPlatformSpec.rankingWeekTimelineSpec.galleryCandidate(
                data = TimelineSpec.AccountBasedData(accountKey),
            ),
            PixivPlatformSpec.rankingMonthTimelineSpec.galleryCandidate(
                data = TimelineSpec.AccountBasedData(accountKey),
            ),
            PixivPlatformSpec.rankingDayMaleTimelineSpec.galleryCandidate(
                data = TimelineSpec.AccountBasedData(accountKey),
            ),
            PixivPlatformSpec.rankingDayFemaleTimelineSpec.galleryCandidate(
                data = TimelineSpec.AccountBasedData(accountKey),
            ),
            PixivPlatformSpec.rankingWeekOriginalTimelineSpec.galleryCandidate(
                data = TimelineSpec.AccountBasedData(accountKey),
            ),
            PixivPlatformSpec.rankingWeekRookieTimelineSpec.galleryCandidate(
                data = TimelineSpec.AccountBasedData(accountKey),
            ),
            PixivPlatformSpec.rankingDayMangaTimelineSpec.galleryCandidate(
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
                        CommonTimelineSpecs.home.galleryCandidate(
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
                        CommonTimelineSpecs.discover.galleryCandidate(
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
                        PixivPlatformSpec.followingTimelineSpec.galleryCandidate(
                            data = TimelineSpec.AccountBasedData(accountKey),
                        ),
                    ),
            ),
            ShortcutSpec(
                title = UiStrings.PixivPrivateFollowing,
                icon = UiIcon.Follow,
                target =
                    ShortcutSpec.Target.Timeline(
                        PixivPlatformSpec.privateFollowingTimelineSpec.galleryCandidate(
                            data = TimelineSpec.AccountBasedData(accountKey),
                        ),
                    ),
            ),
            ShortcutSpec(
                title = UiStrings.Favourite,
                icon = UiIcon.Heart,
                target =
                    ShortcutSpec.Target.Timeline(
                        PixivPlatformSpec.bookmarkTimelineSpec.galleryCandidate(
                            data = TimelineSpec.AccountBasedData(accountKey),
                        ),
                    ),
            ),
            ShortcutSpec(
                title = UiStrings.PixivPrivateFavourites,
                icon = UiIcon.Heart,
                target =
                    ShortcutSpec.Target.Timeline(
                        PixivPlatformSpec.privateBookmarkTimelineSpec.galleryCandidate(
                            data = TimelineSpec.AccountBasedData(accountKey),
                        ),
                    ),
            ),
            ShortcutSpec(
                title = UiStrings.PixivRankingWeek,
                icon = UiIcon.Featured,
                target =
                    ShortcutSpec.Target.Timeline(
                        PixivPlatformSpec.rankingWeekTimelineSpec.galleryCandidate(
                            data = TimelineSpec.AccountBasedData(accountKey),
                        ),
                    ),
            ),
            ShortcutSpec(
                title = UiStrings.PixivRankingMonth,
                icon = UiIcon.Featured,
                target =
                    ShortcutSpec.Target.Timeline(
                        PixivPlatformSpec.rankingMonthTimelineSpec.galleryCandidate(
                            data = TimelineSpec.AccountBasedData(accountKey),
                        ),
                    ),
            ),
            ShortcutSpec(
                title = UiStrings.PixivRankingDayMale,
                icon = UiIcon.Featured,
                target =
                    ShortcutSpec.Target.Timeline(
                        PixivPlatformSpec.rankingDayMaleTimelineSpec.galleryCandidate(
                            data = TimelineSpec.AccountBasedData(accountKey),
                        ),
                    ),
            ),
            ShortcutSpec(
                title = UiStrings.PixivRankingDayFemale,
                icon = UiIcon.Featured,
                target =
                    ShortcutSpec.Target.Timeline(
                        PixivPlatformSpec.rankingDayFemaleTimelineSpec.galleryCandidate(
                            data = TimelineSpec.AccountBasedData(accountKey),
                        ),
                    ),
            ),
            ShortcutSpec(
                title = UiStrings.PixivRankingWeekOriginal,
                icon = UiIcon.Featured,
                target =
                    ShortcutSpec.Target.Timeline(
                        PixivPlatformSpec.rankingWeekOriginalTimelineSpec.galleryCandidate(
                            data = TimelineSpec.AccountBasedData(accountKey),
                        ),
                    ),
            ),
            ShortcutSpec(
                title = UiStrings.PixivRankingWeekRookie,
                icon = UiIcon.Featured,
                target =
                    ShortcutSpec.Target.Timeline(
                        PixivPlatformSpec.rankingWeekRookieTimelineSpec.galleryCandidate(
                            data = TimelineSpec.AccountBasedData(accountKey),
                        ),
                    ),
            ),
            ShortcutSpec(
                title = UiStrings.PixivRankingDayManga,
                icon = UiIcon.Featured,
                target =
                    ShortcutSpec.Target.Timeline(
                        PixivPlatformSpec.rankingDayMangaTimelineSpec.galleryCandidate(
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

    @Suppress("UNCHECKED_CAST")
    override fun galleryDetail(statusKey: MicroBlogKey): Cacheable<GalleryDetail> =
        postHandler.post(statusKey).map {
            val post = it.contentPostOrNull() ?: error("Gallery detail should be a post")
            val actionItems = post.actions.filterIsInstance<ActionMenu.Item>()
            val favouriteAction =
                actionItems.firstOrNull { action ->
                    val text = action.text as? ActionMenu.Item.Text.Localized
                    text?.type == ActionMenu.Item.Text.Localized.Type.Favorite ||
                        text?.type == ActionMenu.Item.Text.Localized.Type.UnFavorite
                }
            val favouriteText = favouriteAction?.text as? ActionMenu.Item.Text.Localized
            val favourited = favouriteText?.type == ActionMenu.Item.Text.Localized.Type.UnFavorite
            val favouriteCount = favouriteAction?.count?.value ?: 0L
            GalleryDetail(
                orientation = GalleryOrientation.Vertical,
                statusKey = post.statusKey,
                accountType = post.accountType,
                url = "https://www.pixiv.net/artworks/${post.statusKey.id}",
                images = post.images.filterIsInstance<UiMedia.Image>().toImmutableList(),
                title =
                    post.contentWarning
                        ?.original
                        ?.raw
                        .orEmpty(),
                author = post.user,
                createdAt = post.createdAt,
                content = post.content.original.takeUnless { content -> content.isEmpty },
                isBookmarked = favourited,
                bookmarkAction =
                    ClickEvent.event(
                        accountKey = accountKey,
                        postEvent =
                            PostEvent.Pixiv.Bookmark(
                                postKey = post.statusKey,
                                bookmarked = favourited,
                                count = favouriteCount,
                                accountKey = accountKey,
                            ),
                    ),
                matrix =
                    actionItems
                        .mapNotNull { action ->
                            GalleryDetail.Matrix(
                                icon = action.icon ?: return@mapNotNull null,
                                count = action.count?.value ?: return@mapNotNull null,
                            )
                        }.toImmutableList(),
            )
        } as Cacheable<GalleryDetail>

    override fun galleryComments(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> =
        PixivGalleryCommentsLoader(
            service = service,
            accountKey = accountKey,
            statusKey = statusKey,
        )

    override fun galleryRecommendations(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> =
        PixivGalleryRecommendationsLoader(
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

    fun bookmarkTimelineLoader(restrict: PixivRestrict = PixivRestrict.Public): RemoteLoader<UiTimelineV2> =
        PixivBookmarkTimelineLoader(
            service = service,
            credentialFlow = credentialFlow,
            accountKey = accountKey,
            restrict = restrict,
        )

    fun followingTimelineLoader(restrict: PixivRestrict = PixivRestrict.Public): RemoteLoader<UiTimelineV2> =
        PixivFollowingTimelineLoader(
            service = service,
            accountKey = accountKey,
            restrict = restrict,
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
