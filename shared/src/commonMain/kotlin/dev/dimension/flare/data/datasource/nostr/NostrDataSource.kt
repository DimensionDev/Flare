package dev.dimension.flare.data.datasource.nostr

import dev.dimension.flare.common.FileType
import dev.dimension.flare.common.SwitchingServiceManager
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datasource.microblog.ComposeType
import dev.dimension.flare.data.datasource.microblog.DatabaseUpdater
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.data.datasource.microblog.StatusMutation
import dev.dimension.flare.data.datasource.microblog.count
import dev.dimension.flare.data.datasource.microblog.datasource.PostDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.RelationDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.UserDataSource
import dev.dimension.flare.data.datasource.microblog.handler.PostEventHandler
import dev.dimension.flare.data.datasource.microblog.handler.PostHandler
import dev.dimension.flare.data.datasource.microblog.handler.RelationHandler
import dev.dimension.flare.data.datasource.microblog.handler.UserHandler
import dev.dimension.flare.data.datasource.microblog.like
import dev.dimension.flare.data.datasource.microblog.loader.RelationActionType
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.notSupported
import dev.dimension.flare.data.datasource.microblog.repost
import dev.dimension.flare.data.datasource.microblog.toggled
import dev.dimension.flare.data.network.nostr.AmberSignerBridge
import dev.dimension.flare.data.network.nostr.NostrService
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.normalized
import dev.dimension.flare.ui.model.signerStableId
import dev.dimension.flare.ui.presenter.compose.ComposeStatus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal typealias NostrServiceManager = SwitchingServiceManager<UiAccount.Nostr.Credential, NostrService>

internal class NostrDataSource(
    override val accountKey: MicroBlogKey,
) : AuthenticatedMicroblogDataSource,
    UserDataSource,
    RelationDataSource,
    PostDataSource,
    PostEventHandler.Handler,
    KoinComponent,
    AutoCloseable {
    private val accountRepository: AccountRepository by inject()
    private val ioScope: CoroutineScope by inject()
    private val nostrCache: NostrCache by inject()
    private val amberSignerBridge: AmberSignerBridge by inject()
    private val credentialFlow by lazy {
        accountRepository.credentialFlow<UiAccount.Nostr.Credential>(accountKey)
    }
    private val loader by lazy {
        NostrLoader(
            accountKey = accountKey,
            serviceManager = serviceManager,
        )
    }

    private val serviceManager by lazy {
        SwitchingServiceManager(
            credentialFlow.distinctUntilChangedBy { it.signerStableId(accountKey) },
            ioScope,
            {
                NostrService(
                    nostrCache,
                    accountKey,
                    credential = it.normalized(accountKey),
                    amberSignerBridge = amberSignerBridge,
                    initialRelays = it.relays,
                ).also {
                    it.ensureConnection()
                }
            },
        )
    }
    private val relaySyncJob by lazy {
        ioScope.launch {
            credentialFlow
                .distinctUntilChangedBy { it.relays }
                .collect { credential ->
                    serviceManager.withService {
                        it.updateRelays(credential.relays)
                    }
                }
        }
    }

    override fun close() {
        relaySyncJob.cancel()
        serviceManager.close()
    }

    override val supportedNotificationFilter: List<NotificationFilter> =
        listOf(
            NotificationFilter.All,
            NotificationFilter.Mention,
        )

    init {
        relaySyncJob
    }

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
                val until =
                    when (request) {
                        PagingRequest.Refresh -> null
                        is PagingRequest.Append -> request.nextKey.toLongOrNull()
                        is PagingRequest.Prepend -> null
                    }
                val data =
                    serviceManager.withService { service ->
                        service.loadHomeTimeline(
                            pageSize = pageSize,
                            until = until,
                        )
                    }
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
                val until =
                    when (request) {
                        PagingRequest.Refresh -> null
                        is PagingRequest.Append -> request.nextKey.toLongOrNull()
                        is PagingRequest.Prepend -> null
                    }
                val data =
                    serviceManager.withService {
                        it.loadUserTimeline(
                            targetPubkey = userKey.id,
                            pageSize = pageSize,
                            until = until,
                            mediaOnly = mediaOnly,
                        )
                    }
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
            serviceManager = serviceManager,
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
                val until =
                    when (request) {
                        PagingRequest.Refresh -> null
                        is PagingRequest.Append -> request.nextKey.toLongOrNull()
                        is PagingRequest.Prepend -> null
                    }
                val data =
                    serviceManager.withService {
                        it.searchStatus(
                            query = query,
                            pageSize = pageSize,
                            until = until,
                        )
                    }
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
                val data =
                    serviceManager.withService {
                        it.searchUser(
                            query = query,
                            pageSize = pageSize,
                        )
                    }
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
                val until =
                    when (request) {
                        PagingRequest.Refresh -> null
                        is PagingRequest.Append -> request.nextKey.toLongOrNull()
                        is PagingRequest.Prepend -> null
                    }
                val data =
                    serviceManager.withService {
                        it.loadNotifications(
                            pageSize = pageSize,
                            until = until,
                            type = type,
                        )
                    }
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
        mutation: StatusMutation,
        updater: DatabaseUpdater,
    ) {
        val toggled = mutation.toggled
        when (mutation.type) {
            StatusMutation.TYPE_REPORT -> {
                serviceManager.withService {
                    it.report(statusKey = mutation.statusKey)
                }
            }

            StatusMutation.TYPE_LIKE -> {
                val reactionEventId = mutation.params["reaction_event_id"]
                if (reactionEventId != null) {
                    serviceManager.withService {
                        it.deleteStatus(
                            statusKey = MicroBlogKey(reactionEventId, NostrService.NOSTR_HOST),
                        )
                    }
                } else {
                    val newReactionEventId =
                        serviceManager.withService {
                            it.react(statusKey = mutation.statusKey)
                        }
                    updater.updateActionMenu(
                        mutation.statusKey,
                        ActionMenu.like(
                            statusKey = mutation.statusKey,
                            accountKey = accountKey,
                            toggled = true,
                            count = mutation.count + 1,
                            extras =
                                buildMap {
                                    newReactionEventId?.let { put("reaction_event_id", it) }
                                },
                        ),
                    )
                }
            }

            StatusMutation.TYPE_REPOST -> {
                val repostEventId = mutation.params["repost_event_id"]
                if (repostEventId != null) {
                    serviceManager.withService {
                        it.deleteStatus(
                            statusKey = MicroBlogKey(repostEventId, NostrService.NOSTR_HOST),
                        )
                    }
                } else {
                    val newRepostEventId =
                        serviceManager.withService {
                            it.repost(statusKey = mutation.statusKey)
                        }
                    updater.updateActionMenu(
                        mutation.statusKey,
                        ActionMenu.repost(
                            statusKey = mutation.statusKey,
                            accountKey = accountKey,
                            toggled = true,
                            count = mutation.count + 1,
                            extras =
                                buildMap {
                                    newRepostEventId?.let { put("repost_event_id", it) }
                                },
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
        val credential = credentialFlow.first()
        val medias =
            data.medias.map { media ->
                val bytes = media.file.readBytes()
                require(media.file.type != FileType.Other) {
                    "Unsupported Nostr media type: ${media.file.name.orEmpty()}"
                }
                serviceManager
                    .withService {
                        it.uploadMedia(
                            serverUrl = credential.mediaServerUrl,
                            name = media.file.name,
                            bytes = bytes,
                            fileType = media.file.type,
                            altText = media.altText,
                        )
                    }.also {
                        progress()
                    }
            }
        when (val composeStatus = data.referenceStatus?.composeStatus) {
            is ComposeStatus.Quote ->
                serviceManager.withService {
                    it.composeQuote(
                        statusKey = composeStatus.statusKey,
                        content = data.content,
                        media = medias,
                        contentWarning = data.spoilerText,
                    )
                }

            is ComposeStatus.Reply ->
                serviceManager.withService {
                    it.composeReply(
                        statusKey = composeStatus.statusKey,
                        content = data.content,
                        media = medias,
                        contentWarning = data.spoilerText,
                    )
                }

            null ->
                serviceManager.withService {
                    it.composeNote(
                        content = data.content,
                        media = medias,
                        contentWarning = data.spoilerText,
                    )
                }
        }
    }

    override fun composeConfig(type: ComposeType): ComposeConfig =
        ComposeConfig(
            text = ComposeConfig.Text(65535),
            contentWarning = ComposeConfig.ContentWarning,
            media =
                ComposeConfig.Media(
                    maxCount = 4,
                    canSensitive = true,
                    altTextMaxLength = 2000,
                    allowMediaOnly = true,
                ),
        )
}
