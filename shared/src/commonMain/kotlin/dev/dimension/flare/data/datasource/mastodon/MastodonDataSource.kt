package dev.dimension.flare.data.datasource.mastodon

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.common.FileType
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datasource.microblog.ComposeProgress
import dev.dimension.flare.data.datasource.microblog.ComposeType
import dev.dimension.flare.data.datasource.microblog.DatabaseUpdater
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.data.datasource.microblog.datasource.ListDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.NotificationDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.PostDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.RelationDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.UserDataSource
import dev.dimension.flare.data.datasource.microblog.handler.EmojiHandler
import dev.dimension.flare.data.datasource.microblog.handler.ListHandler
import dev.dimension.flare.data.datasource.microblog.handler.ListMemberHandler
import dev.dimension.flare.data.datasource.microblog.handler.NotificationHandler
import dev.dimension.flare.data.datasource.microblog.handler.PostEventHandler
import dev.dimension.flare.data.datasource.microblog.handler.PostHandler
import dev.dimension.flare.data.datasource.microblog.handler.RelationHandler
import dev.dimension.flare.data.datasource.microblog.handler.UserHandler
import dev.dimension.flare.data.datasource.microblog.loader.ListLoader
import dev.dimension.flare.data.datasource.microblog.loader.ListMemberLoader
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.datasource.pleroma.PleromaDataSource
import dev.dimension.flare.data.network.mastodon.MastodonService
import dev.dimension.flare.data.network.mastodon.api.model.PostPoll
import dev.dimension.flare.data.network.mastodon.api.model.PostReport
import dev.dimension.flare.data.network.mastodon.api.model.PostStatus
import dev.dimension.flare.data.network.mastodon.api.model.PostVote
import dev.dimension.flare.data.network.mastodon.api.model.Visibility
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.shared.image.ImageCompressor
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiNumber
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.mastodonBookmark
import dev.dimension.flare.ui.model.mapper.mastodonLike
import dev.dimension.flare.ui.model.mapper.mastodonRepost
import dev.dimension.flare.ui.presenter.compose.ComposeStatus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.uuid.Uuid

@OptIn(ExperimentalPagingApi::class)
internal open class MastodonDataSource(
    override val accountKey: MicroBlogKey,
    val instance: String,
) : AuthenticatedMicroblogDataSource,
    NotificationDataSource,
    UserDataSource,
    PostDataSource,
    KoinComponent,
    ListDataSource,
    RelationDataSource,
    PostEventHandler.Handler {
    private val accountRepository: AccountRepository by inject()
    private val imageCompressor: ImageCompressor by inject()
    private val service by lazy {
        MastodonService(
            baseUrl = "https://$instance/",
            accessTokenFlow =
                accountRepository
                    .credentialFlow<UiAccount.Mastodon.Credential>(accountKey)
                    .map { it.accessToken },
        )
    }

    val loader by lazy {
        MastodonLoader(
            accountKey = accountKey,
            service = service,
        )
    }

    val emojiHandler by lazy {
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
        require(event is PostEvent.Mastodon)
        when (event) {
            is PostEvent.Mastodon.AcceptFollowRequest ->
                acceptFollowRequest(event, updater)
            is PostEvent.Mastodon.Bookmark ->
                bookmark(event, updater)
            is PostEvent.Mastodon.Like ->
                like(event, updater)
            is PostEvent.Mastodon.Reblog ->
                reblog(event, updater)
            is PostEvent.Mastodon.RejectFollowRequest ->
                rejectFollowRequest(event, updater)
            is PostEvent.Mastodon.Vote ->
                vote(event, updater)
        }
    }

    override fun homeTimeline() =
        HomeTimelineRemoteMediator(
            service,
            accountKey,
        )

    fun bookmarkTimelineLoader() =
        BookmarkTimelineRemoteMediator(
            service,
            accountKey,
        )

    fun favouriteTimelineLoader() =
        FavouriteTimelineRemoteMediator(
            service,
            accountKey,
        )

    override fun listTimeline(listId: String) =
        ListTimelineRemoteMediator(
            listId,
            service,
            accountKey,
        )

    fun publicTimelineLoader(local: Boolean) =
        PublicTimelineRemoteMediator(
            service,
            accountKey,
            local = local,
        )

    override fun notification(type: NotificationFilter): RemoteLoader<UiTimelineV2> =
        when (type) {
            NotificationFilter.All ->
                NotificationRemoteMediator(
                    service,
                    accountKey,
                    onClearMarker = {
                        notificationHandler.clear()
                    },
                )

            NotificationFilter.Mention ->
                MentionRemoteMediator(
                    service,
                    accountKey,
                )

            else -> throw IllegalStateException("Unsupported notification type")
        }

    override val supportedNotificationFilter: List<NotificationFilter>
        get() =
            listOf(
                NotificationFilter.All,
                NotificationFilter.Mention,
            )

    override fun userTimeline(
        userKey: MicroBlogKey,
        mediaOnly: Boolean,
    ) = UserTimelineRemoteMediator(
        service,
        accountKey,
        userKey,
        onlyMedia = mediaOnly,
    )

    override fun context(statusKey: MicroBlogKey) =
        StatusDetailRemoteMediator(
            statusKey,
            service,
            accountKey,
            statusOnly = false,
        )

    override suspend fun compose(
        data: ComposeData,
        progress: (ComposeProgress) -> Unit,
    ) {
        val inReplyToID =
            data.referenceStatus
                ?.composeStatus
                ?.let {
                    it as? ComposeStatus.Reply
                }?.statusKey
                ?.id
        val quoteID =
            data.referenceStatus
                ?.composeStatus
                ?.let {
                    it as? ComposeStatus.Quote
                }?.statusKey
                ?.id
        val maxProgress = data.medias.size + 1
        val mediaIds =
            data.medias
                .mapIndexed { index, (file, altText) ->
                    val bytes = file.readBytes()
                    val isImage = file.type == FileType.Image

                    val finalBytes =
                        if (isImage) {
                            imageCompressor.compress(
                                imageBytes = bytes,
                                maxSize = 16 * 1024 * 1024,
                                maxDimensions = 2880 to 2880,
                            )
                        } else {
                            bytes
                        }
                    service
                        .upload(
                            finalBytes,
                            name = file.name ?: "unknown",
                            description = altText,
                        ).also {
                            progress(ComposeProgress(index + 1, maxProgress))
                        }
                }.mapNotNull {
                    it.id
                }
        service.post(
            Uuid.random().toString(),
            PostStatus(
                status = data.content,
                visibility =
                    when (data.visibility) {
                        UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Public -> Visibility.Public
                        UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Home -> Visibility.Unlisted
                        UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Followers -> Visibility.Private
                        UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Specified -> Visibility.Direct
                        UiTimeline.ItemContent.Status.TopEndContent.Visibility.Type.Channel -> Visibility.Public
                    },
                inReplyToID = inReplyToID,
                mediaIDS = mediaIds.takeIf { it.isNotEmpty() },
                sensitive = data.sensitive.takeIf { mediaIds.isNotEmpty() },
                spoilerText = data.spoilerText.takeIf { it?.isNotEmpty() == true && it.isNotBlank() },
                poll =
                    data.poll?.let { poll ->
                        PostPoll(
                            options = poll.options,
                            expiresIn = poll.expiredAfter,
                            multiple = poll.multiple,
                        )
                    },
                quoteID =
                    if (this is PleromaDataSource) {
                        quoteID
                    } else {
                        null
                    },
                quotedStatusID =
                    if (this !is PleromaDataSource) {
                        quoteID
                    } else {
                        null
                    },
                language = data.language.firstOrNull(),
            ),
        )
//        progress(ComposeProgress(maxProgress, maxProgress))
    }

    suspend fun like(
        event: PostEvent.Mastodon.Like,
        updater: DatabaseUpdater,
    ) {
        val statusKey = event.postKey
        val liked = event.liked
        val newButton =
            ActionMenu.mastodonLike(
                favourited = !liked,
                favouritesCount = 0,
                accountKey = accountKey,
                statusKey = statusKey,
            )
        updater.updateCache(event.postKey) { item ->
            require(item is UiTimelineV2.Post)
            item.copy(
                actions =
                    item.actions
                        .map {
                            if (it is ActionMenu.Item && it.updateKey == newButton.updateKey) {
                                newButton.copy(
                                    count =
                                        if (!liked) {
                                            it.count?.value?.plus(1)
                                        } else {
                                            it.count?.value?.minus(1)
                                        }.let {
                                            UiNumber(it ?: 0)
                                        },
                                )
                            } else {
                                it
                            }
                        }.toImmutableList(),
            )
        }
        if (liked) {
            service.unfavourite(statusKey.id)
        } else {
            service.favourite(statusKey.id)
        }
    }

    suspend fun reblog(
        event: PostEvent.Mastodon.Reblog,
        updater: DatabaseUpdater,
    ) {
        val statusKey = event.postKey
        val reblogged = event.reblogged
        val newButton =
            ActionMenu.mastodonRepost(
                reblogged = !reblogged,
                reblogsCount = 0,
                accountKey = accountKey,
                statusKey = statusKey,
            )
        updater.updateCache(event.postKey) { item ->
            require(item is UiTimelineV2.Post)
            item.copy(
                actions =
                    item.actions
                        .map {
                            if (it is ActionMenu.Item && it.updateKey == newButton.updateKey) {
                                newButton.copy(
                                    count =
                                        if (!reblogged) {
                                            it.count?.value?.plus(1)
                                        } else {
                                            it.count?.value?.minus(1)
                                        }.let {
                                            UiNumber(it ?: 0)
                                        },
                                )
                            } else {
                                it
                            }
                        }.toImmutableList(),
            )
        }
        if (reblogged) {
            service.unreblog(statusKey.id)
        } else {
            service.reblog(statusKey.id)
        }
    }

    suspend fun bookmark(
        event: PostEvent.Mastodon.Bookmark,
        updater: DatabaseUpdater,
    ) {
        val statusKey = event.postKey
        val bookmarked = event.bookmarked
        val newBookmark =
            ActionMenu.mastodonBookmark(
                bookmarked = !bookmarked,
                accountKey = accountKey,
                statusKey = statusKey,
            )
        updater.updateCache(event.postKey) { item ->
            require(item is UiTimelineV2.Post)
            item.copy(
                actions =
                    item.actions
                        .map {
                            if (it is ActionMenu.Item && it.updateKey == newBookmark.updateKey) {
                                newBookmark
                            } else {
                                it
                            }
                        }.toImmutableList(),
            )
        }
        if (bookmarked) {
            service.unbookmark(statusKey.id)
        } else {
            service.bookmark(statusKey.id)
        }
    }

    suspend fun report(
        userKey: MicroBlogKey,
        statusKey: MicroBlogKey?,
    ) {
        tryRun {
            service.report(
                PostReport(
                    accountId = userKey.id,
                    statusIds = statusKey?.let { listOf(it.id) },
                ),
            )
        }
    }

    override fun discoverUsers(): RemoteLoader<UiProfile> =
        TrendsUserLoader(
            service = service,
            accountKey = accountKey,
            host = accountKey.host,
        )

    override fun discoverStatuses() =
        DiscoverStatusRemoteMediator(
            service,
            accountKey,
        )

    override fun discoverHashtags(): RemoteLoader<UiHashtag> =
        TrendHashtagPagingSource(
            service = service,
        )

    override fun searchStatus(query: String) =
        SearchStatusPagingSource(
            service,
            accountKey,
            query,
        )

    override fun searchUser(query: String): RemoteLoader<UiProfile> =
        SearchUserPagingSource(
            service = service,
            accountKey = accountKey,
            query = query,
            host = accountKey.host,
        )

    override fun composeConfig(type: ComposeType): ComposeConfig =
        ComposeConfig(
            text = ComposeConfig.Text(500),
            media =
                if (type == ComposeType.Quote) {
                    null
                } else {
                    ComposeConfig.Media(
                        maxCount = 4,
                        canSensitive = true,
                        altTextMaxLength = 1500,
                        allowMediaOnly = true,
                    )
                },
            poll =
                if (type == ComposeType.Quote) {
                    null
                } else {
                    ComposeConfig.Poll(4)
                },
            emoji = ComposeConfig.Emoji(emojiHandler.emoji, mergeTag = "mastodon@${accountKey.host}"),
            contentWarning = ComposeConfig.ContentWarning,
            visibility = ComposeConfig.Visibility,
            language = ComposeConfig.Language(1),
        )

    val listLoader: ListLoader by lazy {
        MastodonListLoader(
            service = service,
            accountKey = accountKey,
        )
    }

    val listMemberLoader: ListMemberLoader by lazy {
        MastodonListMemberLoader(
            service = service,
            accountKey = accountKey,
        )
    }

    override val listHandler: ListHandler by lazy {
        ListHandler(
            pagingKey = "lists_$accountKey",
            accountKey = accountKey,
            loader = listLoader,
        )
    }

    override val listMemberHandler: ListMemberHandler by lazy {
        ListMemberHandler(
            pagingKey = "list_members_$accountKey",
            accountKey = accountKey,
            loader = listMemberLoader,
        )
    }

    suspend fun vote(
        event: PostEvent.Mastodon.Vote,
        updater: DatabaseUpdater,
    ) {
        val options = event.options
        val postKey = event.postKey
        updater.updateCache(postKey) {
            require(it is UiTimelineV2.Post)
            it.copy(
                poll =
                    it.poll?.copy(
                        ownVotes = options,
                        options =
                            it.poll.options
                                .mapIndexed { index, option ->
                                    if (options.contains(index)) {
                                        option.copy(
                                            votesCount =
                                                option.votesCount.plus(
                                                    1,
                                                ),
                                        )
                                    } else {
                                        option
                                    }
                                }.toImmutableList(),
                    ),
            )
        }

        service.vote(id = event.id, data = PostVote(choices = options.map { it.toString() }))
    }

    override fun fans(userKey: MicroBlogKey): RemoteLoader<UiProfile> =
        MastodonFansPagingSource(
            service = service,
            host = accountKey.host,
            userKey = userKey,
            accountKey = accountKey,
        )

    override fun following(userKey: MicroBlogKey): RemoteLoader<UiProfile> =
        MastodonFollowingPagingSource(
            service = service,
            host = accountKey.host,
            userKey = userKey,
            accountKey = accountKey,
        )

    override fun profileTabs(userKey: MicroBlogKey): ImmutableList<ProfileTab> =
        listOfNotNull(
            ProfileTab.Timeline(
                type = ProfileTab.Timeline.Type.Status,
                loader =
                    UserTimelineRemoteMediator(
                        service = service,
                        accountKey = accountKey,
                        userKey = userKey,
                        withPinned = true,
                    ),
            ),
            ProfileTab.Timeline(
                type = ProfileTab.Timeline.Type.StatusWithReplies,
                loader =
                    UserTimelineRemoteMediator(
                        service = service,
                        accountKey = accountKey,
                        userKey = userKey,
                        withReplies = true,
                    ),
            ),
            ProfileTab.Media,
        ).toPersistentList()

    suspend fun acceptFollowRequest(
        event: PostEvent.Mastodon.AcceptFollowRequest,
        updater: DatabaseUpdater,
    ) {
        service.authorizeFollowRequest(
            id = event.userKey.id,
        )
        updater.deleteFromCache(event.postKey)
        relationHandler.approveFollowRequest(event.userKey)
    }

    suspend fun rejectFollowRequest(
        event: PostEvent.Mastodon.RejectFollowRequest,
        updater: DatabaseUpdater,
    ) {
        service.rejectFollowRequest(
            id = event.userKey.id,
        )
        updater.deleteFromCache(event.postKey)
        relationHandler.rejectFollowRequest(event.userKey)
    }
}
