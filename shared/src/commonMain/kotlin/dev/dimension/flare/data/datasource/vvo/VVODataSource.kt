package dev.dimension.flare.data.datasource.vvo

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.common.CacheData
import dev.dimension.flare.common.FileItem
import dev.dimension.flare.common.FileType
import dev.dimension.flare.common.InAppNotification
import dev.dimension.flare.common.MemCacheable
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datasource.microblog.ComposeProgress
import dev.dimension.flare.data.datasource.microblog.ComposeType
import dev.dimension.flare.data.datasource.microblog.DatabaseUpdater
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.datasource.microblog.PostEvent
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
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.data.network.vvo.model.StatusDetailItem
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.shared.image.ImageCompressor
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render
import dev.dimension.flare.ui.model.toUi
import dev.dimension.flare.ui.presenter.compose.ComposeStatus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
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
    PostDataSource,
    PostEventHandler.Handler {
    private val accountRepository: AccountRepository by inject()
    private val inAppNotification: InAppNotification by inject()
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

    private val emojiHandler by lazy {
        EmojiHandler(
            host = accountKey.host,
            loader = loader,
        )
    }

    override val notificationHandler by lazy {
        NotificationHandler(
            accountKey = accountKey,
            loader = loader,
        )
    }

    override val userHandler by lazy {
        UserHandler(
            accountKey = accountKey,
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
        )
    }

    override val postEventHandler by lazy {
        PostEventHandler(
            accountKey = accountKey,
            handler = this,
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
            inAppNotification = inAppNotification,
        )

    override fun notification(type: NotificationFilter): RemoteLoader<UiTimelineV2> =
        when (type) {
            NotificationFilter.All,
            NotificationFilter.Mention,
            ->
                MentionRemoteMediator(
                    service = service,
                    accountKey = accountKey,
                    onClearMarker = {
                        MemCacheable.update(notificationMarkerMentionKey, 0)
                    },
                )

            NotificationFilter.Comment ->
                CommentPagingSource(
                    service = service,
                    accountKey = accountKey,
                    onClearMarker = {
                        MemCacheable.update(notificationMarkerCommentKey, 0)
                    },
                )

            NotificationFilter.Like ->
                LikePagingSource(
                    service = service,
                    accountKey = accountKey,
                    onClearMarker = {
                        MemCacheable.update(notificationMarkerLikeKey, 0)
                    },
                )
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
                    ->
                        return PagingResult(
                            endOfPaginationReached = true,
                        )

                    PagingRequest.Refresh -> Unit
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
        progress: (ComposeProgress) -> Unit,
    ) {
        val maxProgress = data.medias.size + 1
        val st = ensureLogin()

        val mediaIds =
            data.medias.mapIndexed { index, (it, _) ->
                uploadMedia(it, st).also {
                    progress(ComposeProgress(index + 1, maxProgress))
                }
            }
        val mediaId = mediaIds.joinToString(",")
        if (data.referenceStatus != null && data.referenceStatus.composeStatus is ComposeStatus.VVOComment) {
            service.replyComment(
                cid = data.referenceStatus.composeStatus.statusKey.id,
                reply = data.referenceStatus.composeStatus.statusKey.id,
                id = data.referenceStatus.composeStatus.rootId,
                mid = data.referenceStatus.composeStatus.rootId,
                content = data.content,
                st = st,
                picId = mediaId,
            )
        } else if (data.referenceStatus != null && data.referenceStatus.composeStatus is ComposeStatus.Reply) {
            service.commentStatus(
                id = data.referenceStatus.composeStatus.statusKey.id,
                content = data.content,
                st = st,
                picId = mediaId,
            )
        } else if (data.referenceStatus != null && data.referenceStatus.composeStatus is ComposeStatus.Quote) {
            service.repostStatus(
                id = data.referenceStatus.composeStatus.statusKey.id,
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
            emoji = ComposeConfig.Emoji(emoji = emojiHandler.emoji, mergeTag = "vvo@${accountKey.host}"),
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

    fun favouriteTimeline() =
        FavouriteRemoteMediator(
            service = service,
            accountKey = accountKey,
        )

    fun likeRemoteMediator() =
        LikeRemoteMediator(
            service = service,
            accountKey = accountKey,
        )

    fun statusComment(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> =
        StatusCommentRemoteMediator(
            service = service,
            accountKey = accountKey,
            statusKey = statusKey,
        )

    fun statusRepost(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> =
        StatusRepostRemoteMediator(
            service = service,
            accountKey = accountKey,
            statusKey = statusKey,
        )

    fun commentChild(commentKey: MicroBlogKey): RemoteLoader<UiTimelineV2> =
        CommentChildRemoteMediator(
            service = service,
            accountKey = accountKey,
            commentKey = commentKey,
        )

    fun comment(statusKey: MicroBlogKey): CacheData<UiTimelineV2> =
        MemCacheable("vvo_comment_${accountKey}_$statusKey") {
            service
                .getHotFlowChild(statusKey.id)
                .rootComment
                ?.firstOrNull()
                ?.render(accountKey)
                ?: throw Exception("status not found")
        }

    fun statusExtendedText(statusKey: MicroBlogKey): Flow<UiState<String>> =
        MemCacheable(
            "status_extended_text_$statusKey",
        ) {
            val st = ensureLogin()
            val response = service.getStatusExtend(statusKey.id, st)
            response.data?.longTextContent.orEmpty()
        }.toUi()

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
        if (config.data?.login != true) {
            throw LoginExpiredException(
                accountKey = accountKey,
                platformType = PlatformType.VVo,
            )
        }
        return requireNotNull(config.data.st) { "st is null" }
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

    private val notificationMarkerMentionKey: String
        get() = "notificationBadgeCount_mention_$accountKey"

    private val notificationMarkerCommentKey: String
        get() = "notificationBadgeCount_comment_$accountKey"

    private val notificationMarkerLikeKey: String
        get() = "notificationBadgeCount_like_$accountKey"
}
