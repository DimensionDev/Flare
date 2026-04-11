package dev.dimension.flare.data.datasource.tumblr

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
import dev.dimension.flare.data.datasource.microblog.handler.PostEventHandler
import dev.dimension.flare.data.datasource.microblog.handler.PostHandler
import dev.dimension.flare.data.datasource.microblog.loader.PostLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.notSupported
import dev.dimension.flare.data.network.tumblr.TumblrService
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.toUi
import dev.dimension.flare.ui.model.mapper.tumblrReblog
import dev.dimension.flare.ui.presenter.compose.ComposeStatus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.first
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal class TumblrDataSource(
    override val accountKey: MicroBlogKey,
) : AuthenticatedMicroblogDataSource,
    PostDataSource,
    PostEventHandler.Handler,
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    private suspend fun credential(): UiAccount.Tumblr.Credential =
        accountRepository.credentialFlow<UiAccount.Tumblr.Credential>(accountKey).first()

    private suspend fun service(): TumblrService {
        val credential = credential()
        return TumblrService(
            consumerKey = credential.consumerKey,
            accessToken = credential.accessToken,
        )
    }

    private val postLoader by lazy {
        object : PostLoader {
            override suspend fun status(statusKey: MicroBlogKey): UiTimelineV2 =
                service()
                    .post(
                        blogIdentifier = statusKey.host.toTumblrBlogIdentifier(),
                        postId = statusKey.id,
                    ).toUi(AccountType.Specific(accountKey))

            override suspend fun deleteStatus(statusKey: MicroBlogKey) {
                service().deletePost(
                    blogIdentifier = credential().blogIdentifier,
                    postId = statusKey.id,
                )
            }
        }
    }

    override val postHandler by lazy {
        PostHandler(
            accountType = AccountType.Specific(accountKey),
            loader = postLoader,
        )
    }

    override val postEventHandler by lazy {
        PostEventHandler(
            accountType = AccountType.Specific(accountKey),
            handler = this,
        )
    }

    override fun homeTimeline(): RemoteLoader<UiTimelineV2> =
        object : RemoteLoader<UiTimelineV2> {
            override suspend fun load(
                pageSize: Int,
                request: PagingRequest,
            ): PagingResult<UiTimelineV2> {
                val offset = request.offset()
                val response = service().dashboard(offset = offset, limit = pageSize.coerceIn(1, 20))
                val data =
                    response.posts.map {
                        it.toUi(AccountType.Specific(accountKey))
                    }
                val nextOffset = offset + data.size
                return PagingResult(
                    endOfPaginationReached = data.size < pageSize.coerceIn(1, 20),
                    data = data,
                    nextKey = nextOffset.toString(),
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
                val offset = request.offset()
                val response =
                    service().blogPosts(
                        blogIdentifier = userKey.id.toTumblrBlogIdentifier(),
                        offset = offset,
                        limit = pageSize.coerceIn(1, 20),
                    )
                val data =
                    response.posts
                        .map { it.toUi(AccountType.Specific(accountKey)) }
                        .filterNot { mediaOnly && it.images.isEmpty() }
                val nextOffset = offset + response.posts.size
                return PagingResult(
                    endOfPaginationReached = response.posts.size < pageSize.coerceIn(1, 20),
                    data = data,
                    nextKey = nextOffset.toString(),
                )
            }
        }

    override fun context(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> =
        object : RemoteLoader<UiTimelineV2> {
            override suspend fun load(
                pageSize: Int,
                request: PagingRequest,
            ): PagingResult<UiTimelineV2> {
                val post =
                    service()
                        .post(
                            blogIdentifier = statusKey.host.toTumblrBlogIdentifier(),
                            postId = statusKey.id,
                        ).toUi(AccountType.Specific(accountKey))
                return PagingResult(endOfPaginationReached = true, data = listOf(post))
            }
        }

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
                loader = userTimeline(userKey, mediaOnly = false),
            ),
            ProfileTab.Media,
        )

    override fun notification(type: NotificationFilter): RemoteLoader<UiTimelineV2> = notSupported()

    override val supportedNotificationFilter: List<NotificationFilter> = emptyList()

    override suspend fun compose(
        data: ComposeData,
        progress: () -> Unit,
    ) {
        require(data.medias.isEmpty()) {
            "Tumblr media upload is not implemented yet"
        }
        require(data.poll == null) {
            "Tumblr polls are not implemented yet"
        }
        when (val reference = data.referenceStatus?.composeStatus) {
            is ComposeStatus.Quote -> {
                val sourcePost =
                    service().post(
                        blogIdentifier = reference.statusKey.host.toTumblrBlogIdentifier(),
                        postId = reference.statusKey.id,
                    )
                val reblogKey = requireNotNull(sourcePost.reblogKey) { "Tumblr post cannot be reblogged" }
                service().reblogPost(
                    blogIdentifier = credential().blogIdentifier,
                    postId = reference.statusKey.id,
                    reblogKey = reblogKey,
                    comment = data.content.takeIf { it.isNotBlank() },
                )
            }

            else -> {
                service().createTextPost(
                    blogIdentifier = credential().blogIdentifier,
                    content = data.content,
                )
            }
        }
    }

    override fun composeConfig(type: ComposeType): ComposeConfig =
        ComposeConfig(
            text = ComposeConfig.Text(maxLength = 4096),
        )

    override suspend fun handle(
        event: PostEvent,
        updater: DatabaseUpdater,
    ) {
        require(event is PostEvent.Tumblr)
        when (event) {
            is PostEvent.Tumblr.Like -> like(event)
            is PostEvent.Tumblr.Reblog -> reblog(event, updater)
        }
    }

    private suspend fun like(event: PostEvent.Tumblr.Like) {
        val sourcePost =
            service().post(
                blogIdentifier = event.postKey.host.toTumblrBlogIdentifier(),
                postId = event.postKey.id,
            )
        val reblogKey = requireNotNull(sourcePost.reblogKey) { "Tumblr post cannot be liked" }
        if (event.liked) {
            service().unlike(event.postKey.id, reblogKey)
        } else {
            service().like(event.postKey.id, reblogKey)
        }
    }

    private suspend fun reblog(
        event: PostEvent.Tumblr.Reblog,
        updater: DatabaseUpdater,
    ) {
        require(event.canReblog) { "Tumblr post cannot be reblogged again" }
        val sourcePost =
            service().post(
                blogIdentifier = event.postKey.host.toTumblrBlogIdentifier(),
                postId = event.postKey.id,
            )
        val reblogKey = requireNotNull(sourcePost.reblogKey) { "Tumblr post cannot be reblogged" }
        service().reblogPost(
            blogIdentifier = credential().blogIdentifier,
            postId = event.postKey.id,
            reblogKey = reblogKey,
        )
        updater.updateActionMenu(
            event.postKey,
            newActionMenu =
                ActionMenu.tumblrReblog(
                    statusKey = event.postKey,
                    canReblog = false,
                    accountKey = event.accountKey,
                ),
        )
    }
}

private fun PagingRequest.offset(): Int =
    when (this) {
        PagingRequest.Refresh -> 0
        is PagingRequest.Append -> nextKey.toIntOrNull() ?: 0
        is PagingRequest.Prepend -> previousKey.toIntOrNull() ?: 0
    }

private fun String.toTumblrBlogIdentifier(): String =
    when {
        contains('.') -> this
        else -> "$this.tumblr.com"
    }
