package dev.dimension.flare.data.datasource.nostr

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datasource.microblog.ComposeType
import dev.dimension.flare.data.datasource.microblog.DatabaseUpdater
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.data.datasource.microblog.datasource.PostDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.RelationDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.UserDataSource
import dev.dimension.flare.data.datasource.microblog.handler.PostEventHandler
import dev.dimension.flare.data.datasource.microblog.handler.PostHandler
import dev.dimension.flare.data.datasource.microblog.handler.RelationHandler
import dev.dimension.flare.data.datasource.microblog.handler.UserHandler
import dev.dimension.flare.data.datasource.microblog.loader.RelationActionType
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.notSupported
import dev.dimension.flare.data.network.nostr.NostrService
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.nostrLike
import dev.dimension.flare.ui.model.mapper.nostrRepost
import dev.dimension.flare.ui.presenter.compose.ComposeStatus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal class NostrDataSource(
    override val accountKey: MicroBlogKey,
) : AuthenticatedMicroblogDataSource,
    UserDataSource,
    RelationDataSource,
    PostDataSource,
    PostEventHandler.Handler,
    KoinComponent {
    private val accountRepository: AccountRepository by inject()
    private val loader by lazy {
        NostrLoader(
            accountKey = accountKey,
            credentialProvider = {
                accountRepository
                    .credentialFlow<UiAccount.Nostr.Credential>(accountKey)
                    .first()
            },
        )
    }

    override val supportedNotificationFilter: List<NotificationFilter> =
        listOf(
            NotificationFilter.All,
            NotificationFilter.Mention,
        )
    override val userHandler by lazy {
        UserHandler(
            host = NostrService.NOSTR_HOST,
            loader = loader,
        )
    }
    override val relationHandler by lazy {
        RelationHandler(
            accountType =
                dev.dimension.flare.model.AccountType
                    .Specific(accountKey),
            dataSource = loader,
        )
    }
    override val supportedRelationTypes: Set<RelationActionType>
        get() = loader.supportedTypes

    override val postHandler by lazy {
        PostHandler(
            accountType =
                dev.dimension.flare.model.AccountType
                    .Specific(accountKey),
            loader = loader,
        )
    }

    override val postEventHandler by lazy {
        PostEventHandler(
            accountType =
                dev.dimension.flare.model.AccountType
                    .Specific(accountKey),
            handler = this,
        )
    }

    override fun homeTimeline(): RemoteLoader<UiTimelineV2> =
        object : CacheableRemoteLoader<UiTimelineV2> {
            override val pagingKey: String = "home_$accountKey"

            override suspend fun load(
                pageSize: Int,
                request: PagingRequest,
            ): PagingResult<UiTimelineV2> {
                if (request is PagingRequest.Prepend) {
                    return PagingResult(endOfPaginationReached = true)
                }
                val credential =
                    accountRepository
                        .credentialFlow<UiAccount.Nostr.Credential>(accountKey)
                        .first()
                val until =
                    when (request) {
                        PagingRequest.Refresh -> null
                        is PagingRequest.Append -> request.nextKey.toLongOrNull()
                        is PagingRequest.Prepend -> null
                    }
                val data =
                    NostrService.loadHomeTimeline(
                        credential = credential,
                        accountKey = accountKey,
                        pageSize = pageSize,
                        until = until,
                    )
                val nextKey =
                    data
                        .filterIsInstance<UiTimelineV2.Post>()
                        .minOfOrNull { it.createdAt.value.epochSeconds - 1 }
                        ?.takeIf { data.isNotEmpty() }
                        ?.toString()
                return PagingResult(
                    endOfPaginationReached = data.isEmpty(),
                    data = data,
                    nextKey = nextKey,
                )
            }
        }

    override fun userTimeline(
        userKey: MicroBlogKey,
        mediaOnly: Boolean,
    ): RemoteLoader<UiTimelineV2> =
        object : CacheableRemoteLoader<UiTimelineV2> {
            override val pagingKey: String =
                buildString {
                    append("user_timeline")
                    if (mediaOnly) {
                        append("media")
                    }
                    append(accountKey.toString())
                    append(userKey.toString())
                }

            override suspend fun load(
                pageSize: Int,
                request: PagingRequest,
            ): PagingResult<UiTimelineV2> {
                if (request is PagingRequest.Prepend) {
                    return PagingResult(endOfPaginationReached = true)
                }
                val credential =
                    accountRepository
                        .credentialFlow<UiAccount.Nostr.Credential>(accountKey)
                        .first()
                val until =
                    when (request) {
                        PagingRequest.Refresh -> null
                        is PagingRequest.Append -> request.nextKey.toLongOrNull()
                        is PagingRequest.Prepend -> null
                    }
                val data =
                    NostrService.loadUserTimeline(
                        credential = credential,
                        accountKey = accountKey,
                        targetPubkey = userKey.id,
                        pageSize = pageSize,
                        until = until,
                        mediaOnly = mediaOnly,
                    )
                val nextKey =
                    data
                        .filterIsInstance<UiTimelineV2.Post>()
                        .minOfOrNull { it.createdAt.value.epochSeconds - 1 }
                        ?.takeIf { data.isNotEmpty() }
                        ?.toString()
                return PagingResult(
                    endOfPaginationReached = data.isEmpty(),
                    data = data,
                    nextKey = nextKey,
                )
            }
        }

    override fun context(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> =
        StatusDetailRemoteMediator(
            statusKey = statusKey,
            accountKey = accountKey,
            credentialProvider = {
                accountRepository
                    .credentialFlow<UiAccount.Nostr.Credential>(accountKey)
                    .first()
            },
        )

    override fun searchStatus(query: String): RemoteLoader<UiTimelineV2> =
        object : CacheableRemoteLoader<UiTimelineV2> {
            override val pagingKey: String = "search_status_${accountKey}_$query"

            override suspend fun load(
                pageSize: Int,
                request: PagingRequest,
            ): PagingResult<UiTimelineV2> {
                if (request is PagingRequest.Prepend) {
                    return PagingResult(endOfPaginationReached = true)
                }
                val credential =
                    accountRepository
                        .credentialFlow<UiAccount.Nostr.Credential>(accountKey)
                        .first()
                val until =
                    when (request) {
                        PagingRequest.Refresh -> null
                        is PagingRequest.Append -> request.nextKey.toLongOrNull()
                        is PagingRequest.Prepend -> null
                    }
                val data =
                    NostrService.searchStatus(
                        credential = credential,
                        accountKey = accountKey,
                        query = query,
                        pageSize = pageSize,
                        until = until,
                    )
                val nextKey =
                    data
                        .filterIsInstance<UiTimelineV2.Post>()
                        .minOfOrNull { it.createdAt.value.epochSeconds - 1 }
                        ?.takeIf { data.isNotEmpty() }
                        ?.toString()
                return PagingResult(
                    endOfPaginationReached = data.isEmpty(),
                    data = data,
                    nextKey = nextKey,
                )
            }
        }

    override fun searchUser(query: String): RemoteLoader<UiProfile> =
        object : CacheableRemoteLoader<UiProfile> {
            override val pagingKey: String = "search_user_${accountKey}_$query"

            override suspend fun load(
                pageSize: Int,
                request: PagingRequest,
            ): PagingResult<UiProfile> {
                if (request is PagingRequest.Prepend || request is PagingRequest.Append) {
                    return PagingResult(endOfPaginationReached = true)
                }
                val credential =
                    accountRepository
                        .credentialFlow<UiAccount.Nostr.Credential>(accountKey)
                        .first()
                val data =
                    NostrService.searchUser(
                        credential = credential,
                        accountKey = accountKey,
                        query = query,
                        pageSize = pageSize,
                    )
                return PagingResult(
                    endOfPaginationReached = true,
                    data = data,
                )
            }
        }

    override fun discoverUsers(): RemoteLoader<UiProfile> = notSupported()

    override fun discoverStatuses(): RemoteLoader<UiTimelineV2> = notSupported()

    override fun discoverHashtags(): RemoteLoader<UiHashtag> = notSupported()

    override fun following(userKey: MicroBlogKey): RemoteLoader<UiProfile> = notSupported()

    override fun fans(userKey: MicroBlogKey): RemoteLoader<UiProfile> = notSupported()

    override fun profileTabs(userKey: MicroBlogKey): ImmutableList<ProfileTab> =
        persistentListOf(
            ProfileTab.Timeline(
                type = ProfileTab.Timeline.Type.Status,
                loader = userTimeline(userKey = userKey, mediaOnly = false),
            ),
        )

    override fun notification(type: NotificationFilter): RemoteLoader<UiTimelineV2> =
        object : CacheableRemoteLoader<UiTimelineV2> {
            override val pagingKey: String = "notification_${type.name.lowercase()}_$accountKey"

            override suspend fun load(
                pageSize: Int,
                request: PagingRequest,
            ): PagingResult<UiTimelineV2> {
                if (request is PagingRequest.Prepend) {
                    return PagingResult(endOfPaginationReached = true)
                }
                val credential =
                    accountRepository
                        .credentialFlow<UiAccount.Nostr.Credential>(accountKey)
                        .first()
                val until =
                    when (request) {
                        PagingRequest.Refresh -> null
                        is PagingRequest.Append -> request.nextKey.toLongOrNull()
                        is PagingRequest.Prepend -> null
                    }
                val data =
                    NostrService.loadNotifications(
                        credential = credential,
                        accountKey = accountKey,
                        pageSize = pageSize,
                        until = until,
                        type = type,
                    )
                val nextKey =
                    data
                        .filterIsInstance<UiTimelineV2.Post>()
                        .minOfOrNull { it.createdAt.value.epochSeconds - 1 }
                        ?.takeIf { data.isNotEmpty() }
                        ?.toString()
                return PagingResult(
                    endOfPaginationReached = data.isEmpty(),
                    data = data,
                    nextKey = nextKey,
                )
            }
        }

    override suspend fun handle(
        event: PostEvent,
        updater: DatabaseUpdater,
    ) {
        require(event is PostEvent.Nostr)
        val credential =
            accountRepository
                .credentialFlow<UiAccount.Nostr.Credential>(accountKey)
                .first()
        when (event) {
            is PostEvent.Nostr.Like -> {
                if (event.reactionEventId != null) {
                    NostrService.deleteStatus(
                        credential = credential,
                        statusKey = MicroBlogKey(event.reactionEventId, NostrService.NOSTR_HOST),
                    )
                } else {
                    val reactionEventId =
                        NostrService.react(
                            credential = credential,
                            statusKey = event.postKey,
                        )
                    updater.updateActionMenu(
                        event.postKey,
                        ActionMenu.nostrLike(
                            statusKey = event.postKey,
                            reactionEventId = reactionEventId,
                            count = event.count + 1,
                            accountKey = accountKey,
                        ),
                    )
                }
            }

            is PostEvent.Nostr.Report ->
                NostrService.report(
                    credential = credential,
                    statusKey = event.postKey,
                )

            is PostEvent.Nostr.Repost -> {
                if (event.repostEventId != null) {
                    NostrService.deleteStatus(
                        credential = credential,
                        statusKey = MicroBlogKey(event.repostEventId, NostrService.NOSTR_HOST),
                    )
                } else {
                    val repostEventId =
                        NostrService.repost(
                            credential = credential,
                            statusKey = event.postKey,
                        )
                    updater.updateActionMenu(
                        event.postKey,
                        ActionMenu.nostrRepost(
                            statusKey = event.postKey,
                            repostEventId = repostEventId,
                            count = event.count + 1,
                            accountKey = accountKey,
                        ),
                    )
                }
            }
        }
    }

    override suspend fun compose(
        data: ComposeData,
        progress: () -> Unit,
    ) {
        val credential =
            accountRepository
                .credentialFlow<UiAccount.Nostr.Credential>(accountKey)
                .first()
        when (val composeStatus = data.referenceStatus?.composeStatus) {
            is ComposeStatus.Quote ->
                NostrService.composeQuote(
                    accountKey = accountKey,
                    credential = credential,
                    statusKey = composeStatus.statusKey,
                    content = data.content,
                )

            is ComposeStatus.Reply ->
                NostrService.composeReply(
                    credential = credential,
                    statusKey = composeStatus.statusKey,
                    content = data.content,
                )

            null ->
                NostrService.composeNote(
                    credential = credential,
                    content = data.content,
                )
        }
    }

    override fun composeConfig(type: ComposeType): ComposeConfig =
        ComposeConfig(
            text = ComposeConfig.Text(65535),
        )
}
