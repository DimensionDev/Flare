package dev.dimension.flare.data.datasource.misskey

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import dev.dimension.flare.common.FileType
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datasource.microblog.ComposeType
import dev.dimension.flare.data.datasource.microblog.DatabaseUpdater
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.data.datasource.microblog.ReactionDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.ListDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.PinnableTimelineTabDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.PinnableTimelineTabSection
import dev.dimension.flare.data.datasource.microblog.datasource.PostDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.RelationDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.TimelineTabConfigurationDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.UserDataSource
import dev.dimension.flare.data.datasource.microblog.handler.EmojiHandler
import dev.dimension.flare.data.datasource.microblog.handler.ListHandler
import dev.dimension.flare.data.datasource.microblog.handler.ListMemberHandler
import dev.dimension.flare.data.datasource.microblog.handler.PostEventHandler
import dev.dimension.flare.data.datasource.microblog.handler.PostHandler
import dev.dimension.flare.data.datasource.microblog.handler.RelationHandler
import dev.dimension.flare.data.datasource.microblog.handler.UserHandler
import dev.dimension.flare.data.datasource.microblog.loader.ListLoader
import dev.dimension.flare.data.datasource.microblog.loader.ListMemberLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.notSupported
import dev.dimension.flare.data.datasource.microblog.paging.toPagingSource
import dev.dimension.flare.data.datasource.microblog.pagingConfig
import dev.dimension.flare.data.model.IconType
import dev.dimension.flare.data.model.tab.ShortcutSpec
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.network.misskey.api.model.AdminAccountsDeleteRequest
import dev.dimension.flare.data.network.misskey.api.model.ChannelsFeaturedRequest
import dev.dimension.flare.data.network.misskey.api.model.ChannelsFollowRequest
import dev.dimension.flare.data.network.misskey.api.model.IPinRequest
import dev.dimension.flare.data.network.misskey.api.model.NotesCreateRequest
import dev.dimension.flare.data.network.misskey.api.model.NotesCreateRequestPoll
import dev.dimension.flare.data.network.misskey.api.model.NotesPollsVoteRequest
import dev.dimension.flare.data.network.misskey.api.model.NotesReactionsCreateRequest
import dev.dimension.flare.data.platform.CommonTimelineSpecs
import dev.dimension.flare.data.platform.MisskeyCredential
import dev.dimension.flare.data.platform.MisskeyPlatformSpec
import dev.dimension.flare.data.platform.toTimelineTabItemV2
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.shared.image.ImageCompressor
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiList
import dev.dimension.flare.ui.model.UiNumber
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiStrings
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.render
import dev.dimension.flare.ui.presenter.compose.ComposeStatus
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@OptIn(ExperimentalPagingApi::class)
internal class MisskeyDataSource(
    override val accountKey: MicroBlogKey,
    private val host: String,
    private val credentialFlow: Flow<MisskeyCredential>,
) : AuthenticatedMicroblogDataSource,
    UserDataSource,
    PostDataSource,
    KoinComponent,
    ListDataSource,
    PinnableTimelineTabDataSource,
    TimelineTabConfigurationDataSource,
    ReactionDataSource,
    RelationDataSource,
    PostEventHandler.Handler {
    private val imageCompressor: ImageCompressor by inject()
    private val service by lazy {
        dev.dimension.flare.data.network.misskey.MisskeyService(
            baseUrl = "https://$host/api/",
            accountKey = accountKey,
            accessTokenFlow = credentialFlow.map { it.accessToken },
        )
    }

    private val loader by lazy {
        MisskeyLoader(
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
            accountType = AccountType.Specific(accountKey),
            dataSource = loader,
        )
    }

    override val supportedRelationTypes: Set<dev.dimension.flare.data.datasource.microblog.loader.RelationActionType>
        get() = loader.supportedTypes

    override val postEventHandler by lazy {
        PostEventHandler(
            accountType = AccountType.Specific(accountKey),
            handler = this,
        )
    }

    override suspend fun handle(
        event: PostEvent,
        updater: DatabaseUpdater,
    ) {
        require(event is PostEvent.Misskey)
        when (event) {
            is PostEvent.Misskey.React -> {
                val reacted = !event.hasReacted
                val nextActionCount = (event.count + if (reacted) 1 else -1).coerceAtLeast(0)
                updater.updateCache(event.postKey) { current ->
                    if (current !is UiTimelineV2.Post) {
                        return@updateCache current
                    }
                    val updatedReactions =
                        current.emojiReactions
                            .toMutableList()
                            .let { reactions ->
                                val index = reactions.indexOfFirst { it.name == event.reaction }
                                if (reacted) {
                                    if (index >= 0) {
                                        val original = reactions[index]
                                        reactions[index] =
                                            original.copy(
                                                count = UiNumber(original.count.value + 1),
                                                me = true,
                                                clickEvent =
                                                    ClickEvent.event(
                                                        accountKey,
                                                        PostEvent.Misskey.React(
                                                            postKey = event.postKey,
                                                            hasReacted = true,
                                                            reaction = event.reaction,
                                                            count = nextActionCount,
                                                            accountKey = accountKey,
                                                        ),
                                                    ),
                                            )
                                    } else {
                                        reactions.add(
                                            UiTimelineV2.Post.EmojiReaction(
                                                name = event.reaction,
                                                url = "",
                                                count = UiNumber(1),
                                                clickEvent =
                                                    ClickEvent.event(
                                                        accountKey,
                                                        PostEvent.Misskey.React(
                                                            postKey = event.postKey,
                                                            hasReacted = true,
                                                            reaction = event.reaction,
                                                            count = nextActionCount,
                                                            accountKey = accountKey,
                                                        ),
                                                    ),
                                                isUnicode =
                                                    !event.reaction.startsWith(':') &&
                                                        !event.reaction.endsWith(
                                                            ':',
                                                        ),
                                                me = true,
                                            ),
                                        )
                                    }
                                } else if (index >= 0) {
                                    val original = reactions[index]
                                    val newCount = (original.count.value - 1).coerceAtLeast(0)
                                    if (newCount == 0L) {
                                        reactions.removeAt(index)
                                    } else {
                                        reactions[index] =
                                            original.copy(
                                                count = UiNumber(newCount),
                                                me = false,
                                                clickEvent =
                                                    ClickEvent.event(
                                                        accountKey,
                                                        PostEvent.Misskey.React(
                                                            postKey = event.postKey,
                                                            hasReacted = false,
                                                            reaction = event.reaction,
                                                            count = nextActionCount,
                                                            accountKey = accountKey,
                                                        ),
                                                    ),
                                            )
                                    }
                                }
                                reactions
                            }.sortedByDescending { it.count.value }
                            .toImmutableList()
                    current.copy(
                        emojiReactions = updatedReactions,
                    )
                }
                if (event.hasReacted) {
                    service.notesReactionsDelete(IPinRequest(noteId = event.postKey.id))
                } else {
                    service.notesReactionsCreate(
                        NotesReactionsCreateRequest(
                            noteId = event.postKey.id,
                            reaction = event.reaction,
                        ),
                    )
                }
            }

            is PostEvent.Misskey.Renote -> {
                service.notesCreate(
                    NotesCreateRequest(
                        renoteId = event.postKey.id,
                    ),
                )
            }

            is PostEvent.Misskey.Vote -> {
                event.options.forEach {
                    service.notesPollsVote(
                        notesPollsVoteRequest =
                            NotesPollsVoteRequest(
                                noteId = event.postKey.id,
                                choice = it,
                            ),
                    )
                }
            }

            is PostEvent.Misskey.Favourite -> {
                if (event.favourited) {
                    service.notesFavoritesDelete(IPinRequest(noteId = event.postKey.id))
                } else {
                    service.notesFavoritesCreate(IPinRequest(noteId = event.postKey.id))
                }
            }

            is PostEvent.Misskey.AcceptFollowRequest -> {
                service.followingRequestsAccept(
                    adminAccountsDeleteRequest = AdminAccountsDeleteRequest(userId = event.userKey.id),
                )
                updater.deleteFromCache(event.notificationStatusKey)
                relationHandler.approveFollowRequest(event.userKey)
            }

            is PostEvent.Misskey.RejectFollowRequest -> {
                service.followingRequestsReject(
                    adminAccountsDeleteRequest = AdminAccountsDeleteRequest(userId = event.userKey.id),
                )
                updater.deleteFromCache(event.notificationStatusKey)
                relationHandler.rejectFollowRequest(event.userKey)
            }
        }
    }

    override fun homeTimeline() =
        HomeTimelineRemoteMediator(
            accountKey,
            service,
        )

    fun localTimelineLoader() =
        LocalTimelineRemoteMediator(
            accountKey,
            service,
        )

    fun hybridTimelineLoader() =
        HybridTimelineRemoteMediator(
            accountKey,
            service,
        )

    fun publicTimelineLoader() =
        PublicTimelineRemoteMediator(
            accountKey,
            service,
        )

    fun featuredChannels(scope: CoroutineScope): Flow<PagingData<UiList.Channel>> =
        Pager(
            config = pagingConfig,
        ) {
            object : RemoteLoader<UiList.Channel> {
                override suspend fun load(
                    pageSize: Int,
                    request: PagingRequest,
                ): PagingResult<UiList.Channel> =
                    when (request) {
                        is PagingRequest.Prepend,
                        is PagingRequest.Append,
                        -> {
                            PagingResult<UiList.Channel>(
                                endOfPaginationReached = true,
                            )
                        }

                        PagingRequest.Refresh -> {
                            PagingResult(
                                endOfPaginationReached = true,
                                data =
                                    service
                                        .channelsFeatured(
                                            request =
                                                ChannelsFeaturedRequest(
                                                    limit = pageSize,
                                                ),
                                        ).map {
                                            it.render(accountKey)
                                        },
                            )
                        }
                    }
            }.toPagingSource()
        }.flow
            .cachedIn(scope)
            .let { channels ->
                combine(
                    channels,
                    channelHandler.cacheData,
                ) { featured, followed ->
                    featured.map { item ->
                        item.copy(
                            isFollowing = followed.any { it.id == item.id },
                        )
                    }
                }
            }.cachedIn(scope)

    override fun notification(type: NotificationFilter): RemoteLoader<UiTimelineV2> =
        when (type) {
            NotificationFilter.All -> {
                NotificationRemoteMediator(
                    accountKey,
                    service,
                )
            }

            NotificationFilter.Mention -> {
                MentionTimelineRemoteMediator(
                    accountKey,
                    service,
                )
            }

            else -> {
                notSupported()
            }
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
        accountKey,
        service,
        userKey,
        onlyMedia = mediaOnly,
    )

    override fun context(statusKey: MicroBlogKey) =
        StatusDetailRemoteMediator(
            statusKey,
            accountKey,
            service,
            statusOnly = false,
        )

    override suspend fun compose(
        data: ComposeData,
        progress: () -> Unit,
    ) {
        val renoteId =
            data.referenceStatus
                ?.composeStatus
                ?.let {
                    it as? ComposeStatus.Quote
                }?.statusKey
                ?.id
        val inReplyToID =
            data.referenceStatus
                ?.composeStatus
                ?.let {
                    it as? ComposeStatus.Reply
                }?.statusKey
                ?.id
        val mediaIds =
            data.medias
                .mapIndexed { index, (item, altText) ->
                    val bytes = item.readBytes()
                    val isImage = item.type == FileType.Image

                    val finalBytes =
                        if (isImage) {
                            imageCompressor.compress(
                                imageBytes = bytes,
                                maxSize = 200L * 1024 * 1024,
                                maxDimensions = 8192 to 8192,
                            )
                        } else {
                            bytes
                        }
                    service
                        .upload(
                            finalBytes,
                            name = item.name ?: "unknown",
                            sensitive = data.sensitive,
                            comment = altText,
                        ).also {
                            progress()
                        }
                }.mapNotNull {
                    it?.id
                }
        service.notesCreate(
            NotesCreateRequest(
                text = data.content.takeIf { it.isNotEmpty() && it.isNotBlank() },
                visibility =
                    when (data.visibility) {
                        UiTimelineV2.Post.Visibility.Public -> "public"
                        UiTimelineV2.Post.Visibility.Home -> "home"
                        UiTimelineV2.Post.Visibility.Followers -> "followers"
                        UiTimelineV2.Post.Visibility.Specified -> "specified"
                        UiTimelineV2.Post.Visibility.Channel -> "public"
                    },
                renoteId = renoteId,
                replyId = inReplyToID,
                fileIds = mediaIds.takeIf { it.isNotEmpty() },
                cw = data.spoilerText.takeIf { it?.isNotEmpty() == true && it.isNotBlank() },
                poll =
                    data.poll?.let { poll ->
                        NotesCreateRequestPoll(
                            choices = poll.options.toSet(),
                            expiredAfter = poll.expiredAfter.toInt(),
                            multiple = poll.multiple,
                        )
                    },
                localOnly = data.localOnly,
            ),
        )
//        progress(ComposeProgress(maxProgress, maxProgress))
    }

    suspend fun report(
        userKey: MicroBlogKey,
        statusKey: MicroBlogKey?,
        comment: String,
    ) {
        tryRun {
            val status =
                statusKey
                    ?.let {
                        service
                            .notesShow(
                                IPinRequest(
                                    noteId = it.id,
                                ),
                            )
                    }

            val actualComment =
                buildString {
                    if (status != null) {
                        if (status.uri != null) {
                            appendLine("Note: ${status.uri}")
                        }
                        append("Local Note: https://${userKey.host}/note/${status.id}")
                        append("-----")
                    }
                    append(comment)
                }

            service.usersReportAbuse(
                dev.dimension.flare.data.network.misskey.api.model.UsersReportAbuseRequest(
                    userId = userKey.id,
                    comment = actualComment,
                ),
            )
        }
    }

    override fun searchStatus(query: String) =
        SearchStatusRemoteMediator(
            service,
            accountKey,
            query,
        )

    override fun searchUser(query: String): RemoteLoader<UiProfile> =
        SearchUserPagingSource(
            service,
            accountKey,
            query,
        )

    override fun discoverUsers(): RemoteLoader<UiProfile> =
        TrendsUserPagingSource(
            service,
            accountKey,
        )

    override fun discoverStatuses() =
        DiscoverStatusRemoteMediator(
            service,
            accountKey,
        )

    override fun discoverHashtags(): RemoteLoader<UiHashtag> =
        TrendHashtagPagingSource(
            service,
        )

    override fun composeConfig(type: ComposeType): ComposeConfig =
        ComposeConfig(
            text = ComposeConfig.Text(3000),
            media =
                ComposeConfig.Media(
                    maxCount = 18,
                    canSensitive = true,
                    altTextMaxLength = 512,
                    allowMediaOnly = true,
                ),
            poll = ComposeConfig.Poll(9),
            emoji =
                ComposeConfig.Emoji(
                    emojiHandler.emoji,
                    "misskey@${accountKey.host}",
                    accountKey = accountKey,
                ),
            contentWarning = ComposeConfig.ContentWarning,
            visibility = ComposeConfig.Visibility,
        )

    fun favouriteState(statusKey: MicroBlogKey): Flow<Boolean> =
        flow {
            tryRun {
                service.notesState(
                    IPinRequest(
                        noteId = statusKey.id,
                    ),
                )
            }.fold(
                onSuccess = {
                    emit(it.isFavorited == true)
                },
                onFailure = {
                    emit(false)
                },
            )
        }

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
        listOfNotNull(
            ProfileTab.Timeline(
                type = ProfileTab.Timeline.Type.Status,
                loader =
                    UserTimelineRemoteMediator(
                        accountKey = accountKey,
                        service = service,
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

    fun favouriteTimelineLoader() =
        FavouriteTimelineRemoteMediator(
            service = service,
            accountKey = accountKey,
        )

    private val listKey: String
        get() = "allLists_$accountKey"

    override fun listTimeline(listId: String) =
        ListTimelineRemoteMediator(
            listId,
            service,
            accountKey,
        )

    val listLoader: ListLoader<UiList.List> by lazy {
        MisskeyListLoader(
            service = service,
            accountKey = accountKey,
        )
    }

    val listMemberLoader: ListMemberLoader by lazy {
        MisskeyListMemberLoader(
            service = service,
            accountKey = accountKey,
        )
    }

    override val listHandler: ListHandler<UiList.List> by lazy {
        ListHandler(
            pagingKey = listKey,
            accountKey = accountKey,
            loader = listLoader,
        )
    }

    override val pinnableTimelineTabs: List<PinnableTimelineTabSection> by lazy {
        listOf(
            PinnableTimelineTabSection(
                title = UiStrings.List,
                data =
                    listHandler.data.map { paging ->
                        paging.map { it.toTimelineTabItemV2(accountKey) }
                    },
            ),
            PinnableTimelineTabSection(
                title = UiStrings.Antenna,
                data =
                    antennasList().map { paging ->
                        paging.map { it.toTimelineTabItemV2(accountKey) }
                    },
            ),
            PinnableTimelineTabSection(
                title = UiStrings.Channel,
                data =
                    channelHandler.data.map { paging ->
                        paging.map { it.toTimelineTabItemV2(accountKey) }
                    },
            ),
        )
    }

    override val defaultTabs by lazy {
        persistentListOf(
            CommonTimelineSpecs.home
                .tabItem(
                    data = TimelineSpec.AccountBasedData(accountKey),
                    icon = IconType.FavIcon(accountKey.host),
                    title = UiText.Raw("Misskey"),
                ),
        )
    }

    override val builtInTimelineTabs by lazy {
        persistentListOf(
            CommonTimelineSpecs.home.tabItem(
                data = TimelineSpec.AccountBasedData(accountKey),
                icon = IconType.FavIcon(accountKey.host),
            ),
            CommonTimelineSpecs.discover.tabItem(
                data = TimelineSpec.AccountBasedData(accountKey),
                icon = IconType.FavIcon(accountKey.host),
            ),
            MisskeyPlatformSpec.favouriteTimelineSpec.tabItem(TimelineSpec.AccountBasedData(accountKey)),
            MisskeyPlatformSpec.hybridTimelineSpec.tabItem(TimelineSpec.AccountBasedData(accountKey)),
            MisskeyPlatformSpec.localTimelineSpec.tabItem(TimelineSpec.AccountBasedData(accountKey)),
            MisskeyPlatformSpec.globalTimelineSpec.tabItem(TimelineSpec.AccountBasedData(accountKey)),
        )
    }

    override val shortcuts by lazy {
        persistentListOf(
            ShortcutSpec(
                title = UiStrings.Favourite,
                icon = UiIcon.Favourite,
                target =
                    ShortcutSpec.Target.Timeline(
                        MisskeyPlatformSpec.favouriteTimelineSpec.tabItem(TimelineSpec.AccountBasedData(accountKey)),
                    ),
            ),
            ShortcutSpec(
                title = UiStrings.List,
                icon = UiIcon.List,
                target =
                    ShortcutSpec.Target.Route(
                        DeeplinkRoute.AllLists(accountKey),
                    ),
            ),
            ShortcutSpec(
                title = UiStrings.Social,
                icon = UiIcon.Featured,
                target =
                    ShortcutSpec.Target.Timeline(
                        MisskeyPlatformSpec.hybridTimelineSpec.tabItem(TimelineSpec.AccountBasedData(accountKey)),
                    ),
            ),
            ShortcutSpec(
                title = UiStrings.MastodonLocal,
                icon = UiIcon.Local,
                target =
                    ShortcutSpec.Target.Timeline(
                        MisskeyPlatformSpec.localTimelineSpec.tabItem(TimelineSpec.AccountBasedData(accountKey)),
                    ),
            ),
            ShortcutSpec(
                title = UiStrings.MastodonPublic,
                icon = UiIcon.World,
                target =
                    ShortcutSpec.Target.Timeline(
                        MisskeyPlatformSpec.globalTimelineSpec.tabItem(TimelineSpec.AccountBasedData(accountKey)),
                    ),
            ),
            ShortcutSpec(
                title = UiStrings.Antenna,
                icon = UiIcon.Rss,
                target =
                    ShortcutSpec.Target.Route(
                        DeeplinkRoute.Misskey.AllAntennas(accountKey),
                    ),
            ),
            ShortcutSpec(
                title = UiStrings.Channel,
                icon = UiIcon.Channel,
                target =
                    ShortcutSpec.Target.Route(
                        DeeplinkRoute.Misskey.AllChannels(accountKey),
                    ),
            ),
        )
    }

    override val listMemberHandler: ListMemberHandler by lazy {
        ListMemberHandler(
            pagingKey = "list_members_$accountKey",
            accountKey = accountKey,
            loader = listMemberLoader,
        )
    }

    val channelHandler: ListHandler<UiList.Channel> by lazy {
        ListHandler(
            pagingKey = "followedChannels_$accountKey",
            accountKey = accountKey,
            loader =
                MisskeyChannelLoader(
                    service = service,
                    accountKey = accountKey,
                    source = MisskeyChannelLoader.Source.Followed,
                ),
        )
    }

    val myFavoriteChannelHandler: ListHandler<UiList.Channel> by lazy {
        ListHandler(
            pagingKey = "myFavoriteChannels_$accountKey",
            accountKey = accountKey,
            loader =
                MisskeyChannelLoader(
                    service = service,
                    accountKey = accountKey,
                    source = MisskeyChannelLoader.Source.MyFavorites,
                ),
        )
    }

    val ownedChannelHandler: ListHandler<UiList.Channel> by lazy {
        ListHandler(
            pagingKey = "ownedChannels_$accountKey",
            accountKey = accountKey,
            loader =
                MisskeyChannelLoader(
                    service = service,
                    accountKey = accountKey,
                    source = MisskeyChannelLoader.Source.Owned,
                ),
        )
    }

    suspend fun followChannel(data: UiList) {
        tryRun {
            service.channelsFollow(
                ChannelsFollowRequest(channelId = data.id),
            )
            channelHandler.insertToDatabase(data)
        }
    }

    suspend fun unfollowChannel(data: UiList) {
        tryRun {
            service.channelsUnfollow(
                ChannelsFollowRequest(channelId = data.id),
            )
            channelHandler.delete(data.id)
        }
    }

    suspend fun favoriteChannel(data: UiList) {
        tryRun {
            service.channelsFavorite(
                ChannelsFollowRequest(channelId = data.id),
            )
            myFavoriteChannelHandler.insertToDatabase(data)
        }
    }

    suspend fun unfavoriteChannel(data: UiList) {
        tryRun {
            service.channelsUnfavorite(
                ChannelsFollowRequest(channelId = data.id),
            )
            myFavoriteChannelHandler.delete(data.id)
        }
    }

    fun antennasList(): Flow<PagingData<UiList.Antenna>> =
        Pager(
            config = pagingConfig,
        ) {
            AntennasListPagingSource(
                service = service,
            ).toPagingSource()
        }.flow

    fun antennasTimelineLoader(id: String) =
        AntennasTimelineRemoteMediator(
            service = service,
            accountKey = accountKey,
            id = id,
        )

    fun channelTimelineLoader(id: String) =
        ChannelTimelineRemoteMediator(
            service = service,
            accountKey = accountKey,
            id = id,
        )
}
