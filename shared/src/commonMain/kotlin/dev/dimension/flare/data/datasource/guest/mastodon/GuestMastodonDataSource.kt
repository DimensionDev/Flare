package dev.dimension.flare.data.datasource.guest.mastodon

import dev.dimension.flare.data.datasource.mastodon.MastodonFansPagingSource
import dev.dimension.flare.data.datasource.mastodon.MastodonFollowingPagingSource
import dev.dimension.flare.data.datasource.mastodon.SearchUserPagingSource
import dev.dimension.flare.data.datasource.mastodon.TrendHashtagPagingSource
import dev.dimension.flare.data.datasource.microblog.DatabaseUpdater
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.data.datasource.microblog.StatusMutation
import dev.dimension.flare.data.datasource.microblog.datasource.PostDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.RelationDataSource
import dev.dimension.flare.data.datasource.microblog.datasource.UserDataSource
import dev.dimension.flare.data.datasource.microblog.handler.PostEventHandler
import dev.dimension.flare.data.datasource.microblog.handler.PostHandler
import dev.dimension.flare.data.datasource.microblog.handler.RelationHandler
import dev.dimension.flare.data.datasource.microblog.handler.UserHandler
import dev.dimension.flare.data.datasource.microblog.loader.RelationActionType
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.RemoteLoader
import dev.dimension.flare.data.network.mastodon.GuestMastodonService
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.koin.core.component.KoinComponent

internal class GuestMastodonDataSource(
    private val host: String,
    private val locale: String,
) : MicroblogDataSource,
    UserDataSource,
    RelationDataSource,
    PostDataSource,
    PostEventHandler.Handler,
    KoinComponent {
    private val service by lazy {
        GuestMastodonService("https://$host/", locale)
    }

    private val loader by lazy {
        GuestMastodonLoader(
            host = host,
            service = service,
        )
    }

    override val userHandler by lazy {
        UserHandler(
            host = host,
            loader = loader,
        )
    }

    override val relationHandler by lazy {
        RelationHandler(
            dataSource = loader,
            accountType = AccountType.GuestHost(host),
        )
    }

    override val postHandler by lazy {
        PostHandler(
            accountType = AccountType.GuestHost(host),
            loader = loader,
        )
    }

    override val postEventHandler by lazy {
        PostEventHandler(
            accountType = AccountType.GuestHost(host),
            handler = this,
        )
    }

    override val supportedRelationTypes: Set<RelationActionType>
        get() = loader.supportedTypes

    override suspend fun handle(
        mutation: StatusMutation,
        updater: DatabaseUpdater,
    ): Unit = throw UnsupportedOperationException("Guest Mastodon data source does not support post events")

    override fun homeTimeline(): RemoteLoader<UiTimelineV2> = GuestTimelinePagingSource(service = service, host = host)

    override fun userTimeline(
        userKey: MicroBlogKey,
        mediaOnly: Boolean,
    ): RemoteLoader<UiTimelineV2> =
        GuestUserTimelinePagingSource(
            service = service,
            host = host,
            userId = userKey.id,
            onlyMedia = mediaOnly,
        )

    override fun context(statusKey: MicroBlogKey): RemoteLoader<UiTimelineV2> =
        GuestStatusDetailPagingSource(
            service = service,
            host = host,
            statusKey = statusKey,
            statusOnly = false,
        )

    override fun searchStatus(query: String): RemoteLoader<UiTimelineV2> =
        GuestSearchStatusPagingSource(
            service = service,
            host = host,
            query = query,
        )

    override fun searchUser(query: String): RemoteLoader<UiProfile> =
        SearchUserPagingSource(
            service = service,
            host = host,
            accountKey = null,
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
                    data = emptyList(),
                )
        }

    override fun discoverStatuses(): RemoteLoader<UiTimelineV2> =
        GuestDiscoverStatusPagingSource(
            service = service,
            host = host,
        )

    override fun discoverHashtags(): RemoteLoader<UiHashtag> = TrendHashtagPagingSource(service)

    override fun following(userKey: MicroBlogKey): RemoteLoader<UiProfile> =
        MastodonFollowingPagingSource(
            service = service,
            accountKey = null,
            host = host,
            userKey = userKey,
        )

    override fun fans(userKey: MicroBlogKey): RemoteLoader<UiProfile> =
        MastodonFansPagingSource(
            service = service,
            accountKey = null,
            host = host,
            userKey = userKey,
        )

    override fun profileTabs(userKey: MicroBlogKey): ImmutableList<ProfileTab> =
        persistentListOf(
            ProfileTab.Timeline(
                type = ProfileTab.Timeline.Type.Status,
                loader =
                    GuestUserTimelinePagingSource(
                        service = service,
                        host = host,
                        userId = userKey.id,
                        withPinned = true,
                    ),
            ),
            ProfileTab.Timeline(
                type = ProfileTab.Timeline.Type.StatusWithReplies,
                loader =
                    GuestUserTimelinePagingSource(
                        service = service,
                        host = host,
                        userId = userKey.id,
                        withReply = true,
                    ),
            ),
            ProfileTab.Media,
        )
}
