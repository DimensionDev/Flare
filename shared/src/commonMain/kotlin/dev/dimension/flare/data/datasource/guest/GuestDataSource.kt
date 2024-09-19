package dev.dimension.flare.data.datasource.guest

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dev.dimension.flare.common.CacheData
import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.common.MemCacheable
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDbUser
import dev.dimension.flare.data.datasource.mastodon.SearchUserPagingSource
import dev.dimension.flare.data.datasource.mastodon.TrendHashtagPagingSource
import dev.dimension.flare.data.datasource.mastodon.TrendsUserPagingSource
import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datasource.microblog.ComposeProgress
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.datasource.microblog.ProfileAction
import dev.dimension.flare.data.datasource.microblog.StatusEvent
import dev.dimension.flare.data.network.mastodon.GuestMastodonService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.mapper.render
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapNotNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object GuestDataSource : MicroblogDataSource, KoinComponent, StatusEvent.Mastodon {
    private val database: CacheDatabase by inject()
    override val account: UiAccount
        get() = UiAccount.Guest

    override fun homeTimeline(
        pageSize: Int,
        pagingKey: String,
        scope: CoroutineScope,
    ): Flow<PagingData<UiTimeline>> =
        Pager(PagingConfig(pageSize = pageSize)) {
            GuestTimelinePagingSource(event = this)
        }.flow.cachedIn(scope)

    override fun notification(
        type: NotificationFilter,
        pageSize: Int,
        pagingKey: String,
        scope: CoroutineScope,
    ): Flow<PagingData<UiTimeline>> {
        TODO("Not yet implemented")
    }

    override val supportedNotificationFilter: List<NotificationFilter>
        get() = emptyList()

    override fun userByAcct(acct: String): CacheData<UiUserV2> {
        val (name, host) = MicroBlogKey.valueOf(acct)
        return Cacheable(
            fetchSource = {
                val user =
                    GuestMastodonService
                        .lookupUserByAcct("$name@$host")
                        ?.toDbUser(GuestMastodonService.HOST) ?: throw Exception("User not found")
                database.userDao().insert(user)
            },
            cacheSource = {
                database
                    .userDao()
                    .findByHandleAndHost(name, host, PlatformType.Mastodon)
                    .mapNotNull { it?.render(account.accountKey) }
            },
        )
    }

    override fun userById(id: String): CacheData<UiProfile> {
        val userKey = MicroBlogKey(id, GuestMastodonService.HOST)
        return Cacheable(
            fetchSource = {
                val user = GuestMastodonService.lookupUser(id).toDbUser(GuestMastodonService.HOST)
                database.userDao().insert(user)
            },
            cacheSource = {
                database
                    .userDao()
                    .findByKey(userKey)
                    .mapNotNull { it?.render(account.accountKey) }
            },
        )
    }

    override fun relation(userKey: MicroBlogKey): Flow<UiState<UiRelation>> = flowOf(UiState.Error(Exception("Not implemented")))

    override fun userTimeline(
        userKey: MicroBlogKey,
        scope: CoroutineScope,
        pageSize: Int,
        mediaOnly: Boolean,
        pagingKey: String,
    ): Flow<PagingData<UiTimeline>> =
        Pager(PagingConfig(pageSize = pageSize)) {
            GuestUserTimelinePagingSource(userKey.id, event = this, onlyMedia = mediaOnly)
        }.flow

    override fun context(
        statusKey: MicroBlogKey,
        scope: CoroutineScope,
        pageSize: Int,
        pagingKey: String,
    ): Flow<PagingData<UiTimeline>> =
        Pager(PagingConfig(pageSize = pageSize)) {
            GuestStatusDetailPagingSource(statusKey, event = this, statusOnly = false)
        }.flow.cachedIn(scope)

    override fun status(statusKey: MicroBlogKey): CacheData<UiTimeline> {
        val pagingKey = "status_only_$statusKey"
        return MemCacheable(
            key = pagingKey,
            fetchSource = {
                GuestMastodonService
                    .lookupStatus(
                        statusKey.id,
                    ).render(GuestMastodonService.GuestKey, this)
            },
        )
    }

    override suspend fun compose(
        data: ComposeData,
        progress: (ComposeProgress) -> Unit,
    ) {
    }

    override suspend fun deleteStatus(statusKey: MicroBlogKey) {
    }

    override fun searchStatus(
        query: String,
        scope: CoroutineScope,
        pageSize: Int,
        pagingKey: String,
    ): Flow<PagingData<UiTimeline>> =
        Pager(PagingConfig(pageSize = pageSize)) {
            GuestSearchStatusPagingSource(query, event = this)
        }.flow.cachedIn(scope)

    override fun searchUser(
        query: String,
        scope: CoroutineScope,
        pageSize: Int,
    ): Flow<PagingData<UiUserV2>> =
        Pager(
            config = PagingConfig(pageSize = pageSize),
        ) {
            SearchUserPagingSource(
                GuestMastodonService,
                GuestMastodonService.GuestKey,
                query,
            )
        }.flow.cachedIn(scope)

    override fun discoverUsers(pageSize: Int): Flow<PagingData<UiUserV2>> =
        Pager(
            config = PagingConfig(pageSize = pageSize),
        ) {
            TrendsUserPagingSource(
                GuestMastodonService,
                GuestMastodonService.GuestKey,
            )
        }.flow

    override fun discoverStatuses(
        pageSize: Int,
        scope: CoroutineScope,
        pagingKey: String,
    ): Flow<PagingData<UiTimeline>> =
        Pager(PagingConfig(pageSize = pageSize)) {
            GuestDiscoverStatusPagingSource(event = this)
        }.flow.cachedIn(scope)

    override fun discoverHashtags(pageSize: Int): Flow<PagingData<UiHashtag>> =
        Pager(
            config = PagingConfig(pageSize = pageSize),
        ) {
            TrendHashtagPagingSource(
                GuestMastodonService,
            )
        }.flow

    override fun composeConfig(statusKey: MicroBlogKey?): ComposeConfig = ComposeConfig()

    override suspend fun follow(
        userKey: MicroBlogKey,
        relation: UiRelation,
    ) {
        TODO("Not yet implemented")
    }

    override fun profileActions(): List<ProfileAction> = emptyList()

    override fun reblog(
        statusKey: MicroBlogKey,
        reblogged: Boolean,
    ) {
    }

    override fun like(
        statusKey: MicroBlogKey,
        liked: Boolean,
    ) {
    }

    override fun bookmark(
        statusKey: MicroBlogKey,
        bookmarked: Boolean,
    ) {
    }
}
