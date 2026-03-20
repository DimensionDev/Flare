package dev.dimension.flare.data.datasource.nostr

import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datasource.microblog.ComposeType
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.data.datasource.microblog.datasource.RelationDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.UserDataSource
import dev.dimension.flare.data.datasource.microblog.handler.RelationHandler
import dev.dimension.flare.data.datasource.microblog.handler.UserHandler
import dev.dimension.flare.data.datasource.microblog.loader.RelationActionType
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
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal class NostrDataSource(
    override val accountKey: MicroBlogKey,
    private val relayHint: String? = null,
) : AuthenticatedMicroblogDataSource,
    UserDataSource,
    RelationDataSource,
    KoinComponent {
    private val accountRepository: AccountRepository by inject()
    private val loader by lazy {
        NostrLoader(
            accountKey = accountKey,
            credentialProvider = {
                accountRepository
                    .credentialFlow<UiAccount.Nostr.Credential>(accountKey)
                    .first()
                    .let {
                        if (relayHint != null && relayHint !in it.relays) {
                            it.copy(relays = listOf(relayHint) + it.relays)
                        } else {
                            it
                        }
                    }
            },
        )
    }

    override val supportedNotificationFilter: List<NotificationFilter> = emptyList()
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

    override fun homeTimeline(): RemoteLoader<UiTimelineV2> =
        object : RemoteLoader<UiTimelineV2> {
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
                        .let {
                            if (relayHint != null && relayHint !in it.relays) {
                                it.copy(relays = listOf(relayHint) + it.relays)
                            } else {
                                it
                            }
                        }
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
        object : RemoteLoader<UiTimelineV2> {
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
                        .let {
                            if (relayHint != null && relayHint !in it.relays) {
                                it.copy(relays = listOf(relayHint) + it.relays)
                            } else {
                                it
                            }
                        }
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

    override fun context(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> = notSupported()

    override fun searchStatus(query: String): RemoteLoader<UiTimelineV2> = notSupported()

    override fun searchUser(query: String): RemoteLoader<UiProfile> = notSupported()

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

    override fun notification(type: NotificationFilter): RemoteLoader<UiTimelineV2> = notSupported()

    override suspend fun compose(
        data: ComposeData,
        progress: () -> Unit,
    ) {
        error("Nostr compose is not implemented yet. relayHint=$relayHint")
    }

    override fun composeConfig(type: ComposeType): ComposeConfig =
        ComposeConfig(
            text = ComposeConfig.Text(65535),
        )
}
