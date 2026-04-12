package dev.dimension.flare.data.datasource.mastodon

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.common.FileType
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datasource.microblog.ComposeType
import dev.dimension.flare.data.datasource.microblog.CredentialRepository
import dev.dimension.flare.data.datasource.microblog.DatabaseUpdater
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.data.datasource.microblog.StatusMutation
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
import dev.dimension.flare.data.datasource.microblog.paging.notSupported
import dev.dimension.flare.data.datasource.microblog.toggled
import dev.dimension.flare.data.datasource.pleroma.PleromaDataSource
import dev.dimension.flare.data.network.mastodon.MastodonCredential
import dev.dimension.flare.data.network.mastodon.MastodonService
import dev.dimension.flare.data.network.mastodon.api.model.PostPoll
import dev.dimension.flare.data.network.mastodon.api.model.PostReport
import dev.dimension.flare.data.network.mastodon.api.model.PostStatus
import dev.dimension.flare.data.network.mastodon.api.model.PostVote
import dev.dimension.flare.data.network.mastodon.api.model.Visibility
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.shared.image.ImageCompressor
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.presenter.compose.ComposeStatus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalPagingApi::class, ExperimentalUuidApi::class)
public open class MastodonDataSource(
    override val accountKey: MicroBlogKey,
    public val instance: String,
) : AuthenticatedMicroblogDataSource,
    NotificationDataSource,
    UserDataSource,
    PostDataSource,
    KoinComponent,
    ListDataSource,
    RelationDataSource,
    PostEventHandler.Handler {
    private val credentialRepository: CredentialRepository by inject()
    private val imageCompressor: ImageCompressor by inject()
    private val service by lazy {
        MastodonService(
            baseUrl = "https://$instance/",
            accessTokenFlow =
                credentialRepository
                    .credentialJsonFlow(accountKey)
                    .map { it.decodeJson<MastodonCredential>() }
                    .map { it.accessToken },
        )
    }

    private val loader by lazy {
        MastodonLoader(
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

    override val notificationHandler: NotificationHandler by lazy {
        NotificationHandler(
            accountKey = accountKey,
            loader = loader,
        )
    }

    override val userHandler: UserHandler by lazy {
        UserHandler(
            host = accountKey.host,
            loader = loader,
        )
    }

    override val postHandler: PostHandler by lazy {
        PostHandler(
            accountType = AccountType.Specific(accountKey),
            loader = loader,
        )
    }

    override val relationHandler: RelationHandler by lazy {
        RelationHandler(
            accountType = AccountType.Specific(accountKey),
            dataSource = loader,
        )
    }

    override val supportedRelationTypes: Set<dev.dimension.flare.data.datasource.microblog.loader.RelationActionType>
        get() = loader.supportedTypes

    override val postEventHandler: PostEventHandler by lazy {
        PostEventHandler(
            accountType = AccountType.Specific(accountKey),
            handler = this,
        )
    }

    override suspend fun handle(
        mutation: StatusMutation,
        updater: DatabaseUpdater,
    ) {
        val toggled = mutation.toggled
        when (mutation.type) {
            StatusMutation.TYPE_LIKE -> {
                if (toggled) {
                    service.unfavourite(mutation.statusKey.id)
                } else {
                    service.favourite(mutation.statusKey.id)
                }
            }

            StatusMutation.TYPE_REPOST -> {
                if (toggled) {
                    service.unreblog(mutation.statusKey.id)
                } else {
                    service.reblog(mutation.statusKey.id)
                }
            }

            StatusMutation.TYPE_BOOKMARK -> {
                if (toggled) {
                    service.unbookmark(mutation.statusKey.id)
                } else {
                    service.bookmark(mutation.statusKey.id)
                }
            }

            StatusMutation.TYPE_VOTE -> {
                val pollId = mutation.params[StatusMutation.PARAM_POLL_ID] ?: return
                val options =
                    mutation.params[StatusMutation.PARAM_OPTIONS]
                        ?.split(",")
                        ?.mapNotNull { it.trim().toIntOrNull() }
                        ?: return
                service.vote(id = pollId, data = PostVote(choices = options.map { it.toString() }))
            }

            StatusMutation.TYPE_ACCEPT_FOLLOW_REQUEST -> {
                val userKeyId = mutation.params[StatusMutation.PARAM_USER_KEY] ?: return
                service.authorizeFollowRequest(id = userKeyId)
                updater.deleteFromCache(mutation.statusKey)
                relationHandler.approveFollowRequest(MicroBlogKey(userKeyId, mutation.statusKey.host))
            }

            StatusMutation.TYPE_REJECT_FOLLOW_REQUEST -> {
                val userKeyId = mutation.params[StatusMutation.PARAM_USER_KEY] ?: return
                service.rejectFollowRequest(id = userKeyId)
                updater.deleteFromCache(mutation.statusKey)
                relationHandler.rejectFollowRequest(MicroBlogKey(userKeyId, mutation.statusKey.host))
            }
        }
    }

    override fun homeTimeline(): RemoteLoader<UiTimelineV2> =
        HomeTimelineRemoteMediator(
            service,
            accountKey,
        )

    public fun bookmarkTimelineLoader(): RemoteLoader<UiTimelineV2> =
        BookmarkTimelineRemoteMediator(
            service,
            accountKey,
        )

    public fun favouriteTimelineLoader(): RemoteLoader<UiTimelineV2> =
        FavouriteTimelineRemoteMediator(
            service,
            accountKey,
        )

    override fun listTimeline(listId: String): RemoteLoader<UiTimelineV2> =
        ListTimelineRemoteMediator(
            listId,
            service,
            accountKey,
        )

    public fun publicTimelineLoader(local: Boolean): RemoteLoader<UiTimelineV2> =
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

            else -> notSupported()
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
    ): RemoteLoader<UiTimelineV2> = UserTimelineRemoteMediator(
        service,
        accountKey,
        userKey,
        onlyMedia = mediaOnly,
    )

    override fun context(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> =
        StatusDetailRemoteMediator(
            statusKey,
            service,
            accountKey,
            statusOnly = false,
        )

    override suspend fun compose(
        data: ComposeData,
        progress: () -> Unit,
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
                            progress()
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
                        UiTimelineV2.Post.Visibility.Public -> Visibility.Public
                        UiTimelineV2.Post.Visibility.Home -> Visibility.Unlisted
                        UiTimelineV2.Post.Visibility.Followers -> Visibility.Private
                        UiTimelineV2.Post.Visibility.Specified -> Visibility.Direct
                        UiTimelineV2.Post.Visibility.Channel -> Visibility.Public
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

    public suspend fun report(
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

    override fun discoverStatuses(): RemoteLoader<UiTimelineV2> =
        DiscoverStatusRemoteMediator(
            service,
            accountKey,
        )

    override fun discoverHashtags(): RemoteLoader<UiHashtag> =
        TrendHashtagPagingSource(
            loadTrends = {
                service.trendsTags()
            },
        )

    override fun searchStatus(query: String): RemoteLoader<UiTimelineV2> =
        SearchStatusPagingSource(
            service,
            accountKey,
            query,
        )

    override fun searchUser(query: String): RemoteLoader<UiProfile> =
        SearchUserPagingSource(
            search = { value, maxId, limit, type, following, resolve ->
                service.searchV2(
                    query = value,
                    limit = limit,
                    max_id = maxId,
                    type = type,
                    following = following,
                    resolve = resolve,
                ).accounts ?: emptyList()
            },
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
            emoji =
                ComposeConfig.Emoji(
                    emojiHandler.emoji,
                    mergeTag = "mastodon@${accountKey.host}",
                    accountKey = accountKey,
                ),
            contentWarning = ComposeConfig.ContentWarning,
            visibility = ComposeConfig.Visibility,
            language = ComposeConfig.Language(1),
        )

    public val listLoader: ListLoader by lazy {
        MastodonListLoader(
            service = service,
            accountKey = accountKey,
        )
    }

    public val listMemberLoader: ListMemberLoader by lazy {
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

    override fun fans(userKey: MicroBlogKey): RemoteLoader<UiProfile> =
        MastodonFansPagingSource(
            loadFollowers = { id, maxId, limit ->
                service.followers(id = id, max_id = maxId, limit = limit)
            },
            host = accountKey.host,
            userKey = userKey,
            accountKey = accountKey,
        )

    override fun following(userKey: MicroBlogKey): RemoteLoader<UiProfile> =
        MastodonFollowingPagingSource(
            loadFollowing = { id, maxId, limit ->
                service.following(id = id, max_id = maxId, limit = limit)
            },
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
}
