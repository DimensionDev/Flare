package dev.dimension.flare.data.datasource.vvo

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadState
import dev.dimension.flare.common.CacheData
import dev.dimension.flare.common.CacheState
import dev.dimension.flare.common.FileItem
import dev.dimension.flare.common.FileType
import dev.dimension.flare.common.MemCacheable
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datasource.microblog.ComposeType
import dev.dimension.flare.data.datasource.microblog.DatabaseUpdater
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.ui.model.PostEvent
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.data.datasource.microblog.datasource.NotificationDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.PostDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.RelationDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.UserDataSource
import dev.dimension.flare.data.datasource.microblog.handler.EmojiHandler
import dev.dimension.flare.data.datasource.microblog.handler.NotificationHandler
import dev.dimension.flare.data.datasource.microblog.handler.PostEventHandler
import dev.dimension.flare.data.datasource.microblog.handler.PostHandler
import dev.dimension.flare.data.datasource.microblog.handler.RelationHandler
import dev.dimension.flare.data.datasource.microblog.handler.UserHandler
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.datasource.microblog.timeline.CommonTimelineSpecs as SocialCommonTimelineSpecs
import dev.dimension.flare.data.datasource.microblog.timeline.TimelineSpec
import dev.dimension.flare.data.datasource.microblog.timeline.TimelineTabProvider
import dev.dimension.flare.data.datasource.microblog.timeline.toTimelineShortcutDescriptor
import dev.dimension.flare.data.datasource.microblog.timeline.toTimelineTabDescriptor
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.data.network.vvo.model.StatusDetailItem
import dev.dimension.flare.data.platform.VvoTimelineDataSource
import dev.dimension.flare.data.platform.VvoTimelineSpecs
import dev.dimension.flare.data.account.AccountRepository
import dev.dimension.flare.model.LoginExpiredException
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.media.ImageCompressor
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render
import dev.dimension.flare.ui.presenter.compose.ComposeStatus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@OptIn(ExperimentalPagingApi::class)
internal class VVODataSource(
    override val accountKey: MicroBlogKey,
) : AuthenticatedMicroblogDataSource,
    KoinComponent,
    NotificationDataSource,
    UserDataSource,
    RelationDataSource,
    VvoTimelineDataSource,
    VvoStatusDataSource,
    TimelineTabProvider,
    PostDataSource,
    PostEventHandler.Handler {
    private val accountRepository: AccountRepository by inject()
    private val imageCompressor: ImageCompressor by inject()
    private val service by lazy {
        VVOService(
            chocolateFlow =
                accountRepository
                    .credentialFlow<UiAccount.VVo.Credential>(accountKey)
                    .map { it.chocolate },
        )
    }

    private val loader by lazy {
        VVOLoader(
            accountKey = accountKey,
            service = service,
        )
    }

    private val notificationBadgeStore: VVONotificationBadgeStore by lazy {
        VVONotificationBadgeStore(loader) { total ->
            notificationHandler.update(total)
        }
    }

    override val defaultTimelineTabs by lazy {
        persistentListOf(
            SocialCommonTimelineSpecs.home
                .toTimelineTabDescriptor(
                    data = TimelineSpec.AccountBasedData(accountKey),
                    icon = IconType.Material(UiIcon.Weibo),
                ),
        )
    }

    override val builtInTimelineTabs by lazy {
        persistentListOf(
            SocialCommonTimelineSpecs.home.toTimelineTabDescriptor(
                data = TimelineSpec.AccountBasedData(accountKey),
                icon = IconType.Material(UiIcon.Weibo),
            ),
            SocialCommonTimelineSpecs.discover.toTimelineTabDescriptor(
                data = TimelineSpec.AccountBasedData(accountKey),
                icon = IconType.Material(UiIcon.Weibo),
            ),
            VvoTimelineSpecs.favorite.toTimelineTabDescriptor(TimelineSpec.AccountBasedData(accountKey)),
            VvoTimelineSpecs.liked.toTimelineTabDescriptor(TimelineSpec.AccountBasedData(accountKey)),
        )
    }

    override val timelineShortcuts by lazy {
        persistentListOf(
            SocialCommonTimelineSpecs.discover
                .toTimelineTabDescriptor(TimelineSpec.AccountBasedData(accountKey))
                .toTimelineShortcutDescriptor(UiStrings.Featured, UiIcon.Featured),
            VvoTimelineSpecs.favorite
                .toTimelineTabDescriptor(TimelineSpec.AccountBasedData(accountKey))
                .toTimelineShortcutDescriptor(UiStrings.Bookmark, UiIcon.Bookmark),
            VvoTimelineSpecs.liked
                .toTimelineTabDescriptor(TimelineSpec.AccountBasedData(accountKey))
                .toTimelineShortcutDescriptor(UiStrings.Liked, UiIcon.Heart),
        )
    }

    private val emojiHandler by lazy {
        EmojiHandler(
            host = accountKey.host,
            loader = loader,
        )
    }

    override val notificationHandler: NotificationHandler by lazy {
        NotificationHandler(
            accountKey = accountKey,
            loader = loader,
            fetchBadgeCount = {
                notificationBadgeStore.refreshAndGetTotal()
            },
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

    override val relationHandler by lazy {
        RelationHandler(
            dataSource = loader,
            accountType = AccountType.Specific(accountKey),
        )
    }

    override val supportedRelationTypes: Set<dev.dimension.flare.data.datasource.microblog.loader.RelationActionType>
        get() = loader.supportedTypes

    override val postEventHandler by lazy {
        PostEventHandler(
            accountType = AccountType.Specific(accountKey),
            handler = this,
            optimisticActionMenu = { it.vvoNextActionMenu() },
        )
    }

    override suspend fun handle(
        event: PostEvent,
        updater: DatabaseUpdater,
    ) {
        require(event is PostEvent.VVO)
        when (event) {
            is PostEvent.VVO.Favorite -> favorite(event)
            is PostEvent.VVO.Like -> like(event)
            is PostEvent.VVO.LikeComment -> likeComment(event)
        }
    }

    override fun homeTimeline() =
        HomeTimelineRemoteMediator(
            service = service,
            accountKey = accountKey,
        )

    override fun notification(type: NotificationFilter): RemoteLoader<UiTimelineV2> =
        when (type) {
            NotificationFilter.All -> {
                MentionRemoteMediator(
                    service = service,
                    accountKey = accountKey,
                    onClearMarker = {
                        notificationBadgeStore.clearAll()
                    },
                )
            }

            NotificationFilter.Mention -> {
                MentionRemoteMediator(
                    service = service,
                    accountKey = accountKey,
                    onClearMarker = {
                        notificationBadgeStore.clear(NotificationFilter.Mention)
                    },
                )
            }

            NotificationFilter.Comment -> {
                CommentPagingSource(
                    service = service,
                    accountKey = accountKey,
                    onClearMarker = {
                        notificationBadgeStore.clear(NotificationFilter.Comment)
                    },
                )
            }

            NotificationFilter.Like -> {
                LikePagingSource(
                    service = service,
                    accountKey = accountKey,
                    onClearMarker = {
                        notificationBadgeStore.clear(NotificationFilter.Like)
                    },
                )
            }
        }

    override val supportedNotificationFilter: List<NotificationFilter>
        get() =
            listOf(
                NotificationFilter.Mention,
                NotificationFilter.Comment,
                NotificationFilter.Like,
            )

    override fun userTimeline(
        userKey: MicroBlogKey,
        mediaOnly: Boolean,
    ) = UserTimelineRemoteMediator(
        userKey = userKey,
        service = service,
        accountKey = accountKey,
        mediaOnly = mediaOnly,
    )

    override fun context(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> =
        object : RemoteLoader<UiTimelineV2> {
            override suspend fun load(
                pageSize: Int,
                request: PagingRequest,
            ): PagingResult<UiTimelineV2> {
                when (request) {
                    is PagingRequest.Prepend,
                    is PagingRequest.Append,
                    -> {
                        return PagingResult(
                            endOfPaginationReached = true,
                        )
                    }

                    PagingRequest.Refresh -> {
                        Unit
                    }
                }

                val status = loadStatusDetail(statusKey)
                val comments =
                    service
                        .getHotComments(
                            id = statusKey.id,
                            mid = statusKey.id,
                            maxId = null,
                        ).data
                        ?.data
                        .orEmpty()
                        .map { it.render(accountKey) }

                return PagingResult(
                    endOfPaginationReached = true,
                    data = listOf(status) + comments,
                )
            }
        }

    override fun searchStatus(query: String) =
        SearchStatusRemoteMediator(
            service = service,
            accountKey = accountKey,
            query = query,
        )

    override fun searchUser(query: String): RemoteLoader<UiProfile> =
        SearchUserPagingSource(
            service = service,
            accountKey = accountKey,
            query = query,
        )

    override fun discoverUsers(): RemoteLoader<UiProfile> =
        object : RemoteLoader<UiProfile> {
            override suspend fun load(
                pageSize: Int,
                request: PagingRequest,
            ): PagingResult<UiProfile> =
                PagingResult(
                    endOfPaginationReached = true,
                )
        }

    override fun discoverStatuses() =
        DiscoverStatusRemoteMediator(
            service = service,
            accountKey = accountKey,
        )

    override fun discoverHashtags(): RemoteLoader<UiHashtag> =
        TrendHashtagPagingSource(
            accountKey = accountKey,
            service = service,
        )

    override fun following(userKey: MicroBlogKey): RemoteLoader<UiProfile> =
        FollowingPagingSource(
            service = service,
            userKey = userKey,
            accountKey = accountKey,
        )

    override fun fans(userKey: MicroBlogKey): RemoteLoader<UiProfile> =
        FansPagingSource(
            service = service,
            userKey = userKey,
            accountKey = accountKey,
        )

    override fun profileTabs(userKey: MicroBlogKey): ImmutableList<ProfileTab> =
        persistentListOf(
            ProfileTab.Timeline(
                type = ProfileTab.Timeline.Type.Status,
                loader = userTimeline(userKey, false),
            ),
        )

    override suspend fun compose(
        data: ComposeData,
        progress: () -> Unit,
    ) {
        val st = ensureLogin()

        val mediaIds =
            data.medias.mapIndexed { index, (it, _) ->
                uploadMedia(it, st).also {
                    progress()
                }
            }
        val mediaId = mediaIds.joinToString(",")
        val referenceStatus = data.referenceStatus?.composeStatus
        if (referenceStatus is ComposeStatus.VVOComment) {
            service.replyComment(
                cid = referenceStatus.statusKey.id,
                reply = referenceStatus.statusKey.id,
                id = referenceStatus.rootId,
                mid = referenceStatus.rootId,
                content = data.content,
                st = st,
                picId = mediaId,
            )
        } else if (referenceStatus is ComposeStatus.Reply) {
            service.commentStatus(
                id = referenceStatus.statusKey.id,
                content = data.content,
                st = st,
                picId = mediaId,
            )
        } else if (referenceStatus is ComposeStatus.Quote) {
            service.repostStatus(
                id = referenceStatus.statusKey.id,
                content = data.content,
                st = st,
                picId = mediaId,
            )
        } else {
            service.updateStatus(
                content = data.content,
                st = st,
                picId = mediaId,
            )
        }
    }

    override fun composeConfig(type: ComposeType): ComposeConfig =
        ComposeConfig(
            text = ComposeConfig.Text(2000),
            media =
                ComposeConfig.Media(
                    if (type == ComposeType.New) 18 else 1,
                    false,
                    altTextMaxLength = -1,
                    allowMediaOnly = false,
                ),
            emoji =
                ComposeConfig.Emoji(
                    emoji = emojiHandler.emoji,
                    mergeTag = "vvo@${accountKey.host}",
                    accountKey = accountKey,
                ),
        )

    private suspend fun like(event: PostEvent.VVO.Like) {
        val st = ensureLogin()
        if (event.liked) {
            service.unlikeStatus(id = event.postKey.id, st = st)
        } else {
            service.likeStatus(id = event.postKey.id, st = st)
        }
    }

    private suspend fun likeComment(event: PostEvent.VVO.LikeComment) {
        val st = ensureLogin()
        if (event.liked) {
            service.likesDestroy(id = event.postKey.id, st = st)
        } else {
            service.likesUpdate(id = event.postKey.id, st = st)
        }
    }

    private suspend fun favorite(event: PostEvent.VVO.Favorite) {
        val st = ensureLogin()
        if (event.favorited) {
            service.unfavoriteStatus(id = event.postKey.id, st = st)
        } else {
            service.favoriteStatus(id = event.postKey.id, st = st)
        }
    }

    override fun favouriteTimeline() =
        FavouriteRemoteMediator(
            service = service,
            accountKey = accountKey,
        )

    override fun likeRemoteMediator() =
        LikeRemoteMediator(
            service = service,
            accountKey = accountKey,
        )

    override fun statusComment(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> =
        StatusCommentRemoteMediator(
            service = service,
            accountKey = accountKey,
            statusKey = statusKey,
        )

    override fun statusRepost(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> =
        StatusRepostRemoteMediator(
            service = service,
            accountKey = accountKey,
            statusKey = statusKey,
        )

    override fun commentChild(commentKey: MicroBlogKey): RemoteLoader<UiTimelineV2> =
        CommentChildRemoteMediator(
            service = service,
            accountKey = accountKey,
            commentKey = commentKey,
        )

    override fun comment(statusKey: MicroBlogKey): CacheData<UiTimelineV2> =
        MemCacheable("vvo_comment_${accountKey}_$statusKey") {
            service
                .getHotFlowChild(statusKey.id)
                .rootComment
                ?.firstOrNull()
                ?.render(accountKey)
                ?: throw Exception("status not found")
        }

    override fun statusExtendedText(statusKey: MicroBlogKey): Flow<UiState<String>> =
        MemCacheable(
            "status_extended_text_$statusKey",
        ) {
            val st = ensureLogin()
            val response = service.getStatusExtend(statusKey.id, st)
            response.data?.longTextContent.orEmpty()
        }.toUiState()

    override fun status(statusKey: MicroBlogKey): Flow<UiState<UiTimelineV2>> =
        MemCacheable(
            "vvo_status_$statusKey",
        ) {
            loadStatusDetail(statusKey)
        }.toUiState()

    private suspend fun uploadMedia(
        fileItem: FileItem,
        st: String,
    ): String {
        val bytes = fileItem.readBytes()
        val isImage = fileItem.type == FileType.Image

        val finalBytes =
            if (isImage) {
                imageCompressor.compress(
                    imageBytes = bytes,
                    maxSize = 5 * 1024 * 1024,
                    maxDimensions = 4096 to 4096,
                )
            } else {
                bytes
            }
        val response =
            service.uploadPic(
                st = st,
                bytes = finalBytes,
                filename = fileItem.name ?: "file",
            )
        return response.picID ?: throw Exception("upload failed")
    }

    private suspend fun ensureLogin(): String {
        val config = service.config()
        val data = config.data
        if (data?.login != true) {
            throw LoginExpiredException(
                accountKey = accountKey,
                platformType = PlatformType.VVo,
            )
        }
        return requireNotNull(data.st) { "st is null" }
    }

    private suspend fun loadStatusDetail(statusKey: MicroBlogKey): UiTimelineV2 {
        val regex =
            "\\\$render_data\\s*=\\s*(\\[\\{.*?\\}\\])\\[0\\]\\s*\\|\\|\\s*\\{\\};".toRegex()
        val response =
            service
                .getStatusDetail(statusKey.id)
                .split("\n")
                .joinToString("")
        val json =
            regex
                .find(response)
                ?.groupValues
                ?.get(1)
                ?.decodeJson<List<StatusDetailItem>>()
                ?: throw Exception("status not found")

        return json.firstOrNull()?.status?.render(accountKey) ?: throw Exception("status not found")
    }
}

private fun <T : Any> CacheData<T>.toUiState(): Flow<UiState<T>> =
    combine(data, refreshState) { data, refresh ->
        if (data is CacheState.Success) {
            UiState.Success(data.data)
        } else {
            when (refresh) {
                is LoadState.Error -> UiState.Error(refresh.error)
                LoadState.Loading -> UiState.Loading()
                is LoadState.NotLoading -> UiState.Error(IllegalStateException("Data is null"))
            }
        }
    }
