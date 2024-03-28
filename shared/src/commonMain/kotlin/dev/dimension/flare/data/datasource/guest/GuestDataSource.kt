package dev.dimension.flare.data.datasource.guest

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneNotNull
import dev.dimension.flare.common.CacheData
import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.common.MemCacheable
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.toDbUser
import dev.dimension.flare.data.datasource.mastodon.SearchUserPagingSource
import dev.dimension.flare.data.datasource.mastodon.TrendHashtagPagingSource
import dev.dimension.flare.data.datasource.mastodon.TrendsUserPagingSource
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datasource.microblog.ComposeProgress
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.datasource.microblog.SupportedComposeEvent
import dev.dimension.flare.data.network.mastodon.GuestMastodonService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.UiUser
import dev.dimension.flare.ui.model.mapper.toUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object GuestDataSource : MicroblogDataSource, KoinComponent {
    private val database: CacheDatabase by inject()
    override val account: UiAccount
        get() = UiAccount.Guest

    override fun homeTimeline(
        pageSize: Int,
        pagingKey: String,
        scope: CoroutineScope,
    ): Flow<PagingData<UiStatus>> {
        return Pager(PagingConfig(pageSize = pageSize)) {
            GuestTimelinePagingSource()
        }.flow
    }

    override fun notification(
        type: NotificationFilter,
        pageSize: Int,
        pagingKey: String,
        scope: CoroutineScope,
    ): Flow<PagingData<UiStatus>> {
        TODO("Not yet implemented")
    }

    override val supportedNotificationFilter: List<NotificationFilter>
        get() = emptyList()

    override fun userByAcct(acct: String): CacheData<UiUser> {
        val (name, host) = MicroBlogKey.valueOf(acct)
        return Cacheable(
            fetchSource = {
                val user =
                    GuestMastodonService.lookupUserByAcct("$name@$host")
                        ?.toDbUser(GuestMastodonService.host) ?: throw Exception("User not found")
                database.dbUserQueries.insert(
                    user_key = user.user_key,
                    platform_type = user.platform_type,
                    name = user.name,
                    handle = user.handle,
                    host = user.host,
                    content = user.content,
                )
            },
            cacheSource = {
                database.dbUserQueries.findByHandleAndHost(name, host, PlatformType.Mastodon)
                    .asFlow()
                    .mapToOneNotNull(Dispatchers.IO)
                    .map { it.toUi() }
            },
        )
    }

    override fun userById(id: String): CacheData<UiUser> {
        val userKey = MicroBlogKey(id, GuestMastodonService.host)
        return Cacheable(
            fetchSource = {
                val user = GuestMastodonService.lookupUser(id).toDbUser(GuestMastodonService.host)
                database.dbUserQueries.insert(
                    user_key = user.user_key,
                    platform_type = user.platform_type,
                    name = user.name,
                    handle = user.handle,
                    host = user.host,
                    content = user.content,
                )
            },
            cacheSource = {
                database.dbUserQueries.findByKey(userKey).asFlow()
                    .mapToOneNotNull(Dispatchers.IO)
                    .map { it.toUi() }
            },
        )
    }

    override fun relation(userKey: MicroBlogKey): Flow<UiState<UiRelation>> {
        return flowOf(UiState.Error(Exception("Not implemented")))
    }

    override fun userTimeline(
        userKey: MicroBlogKey,
        scope: CoroutineScope,
        pageSize: Int,
        mediaOnly: Boolean,
        pagingKey: String,
    ): Flow<PagingData<UiStatus>> {
        return Pager(PagingConfig(pageSize = pageSize)) {
            GuestUserTimelinePagingSource(userKey.id, onlyMedia = mediaOnly)
        }.flow
    }

    override fun context(
        statusKey: MicroBlogKey,
        scope: CoroutineScope,
        pageSize: Int,
        pagingKey: String,
    ): Flow<PagingData<UiStatus>> {
        return Pager(PagingConfig(pageSize = pageSize)) {
            GuestStatusDetailPagingSource(statusKey, statusOnly = false)
        }.flow
    }

    override fun status(statusKey: MicroBlogKey): CacheData<UiStatus> {
        val pagingKey = "status_only_$statusKey"
        return MemCacheable(
            key = pagingKey,
            fetchSource = {
                GuestMastodonService.lookupStatus(
                    statusKey.id,
                ).toUi(GuestMastodonService.GuestKey)
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
    ): Flow<PagingData<UiStatus>> {
        return Pager(PagingConfig(pageSize = pageSize)) {
            GuestSearchStatusPagingSource(query)
        }.flow
    }

    override fun searchUser(
        query: String,
        scope: CoroutineScope,
        pageSize: Int,
    ): Flow<PagingData<UiUser>> {
        return Pager(
            config = PagingConfig(pageSize = pageSize),
        ) {
            SearchUserPagingSource(
                GuestMastodonService,
                GuestMastodonService.host,
                query,
            )
        }.flow
    }

    override fun discoverUsers(pageSize: Int): Flow<PagingData<UiUser>> {
        return Pager(
            config = PagingConfig(pageSize = pageSize),
        ) {
            TrendsUserPagingSource(
                GuestMastodonService,
                GuestMastodonService.host,
            )
        }.flow
    }

    override fun discoverStatuses(
        pageSize: Int,
        scope: CoroutineScope,
        pagingKey: String,
    ): Flow<PagingData<UiStatus>> {
        return Pager(PagingConfig(pageSize = pageSize)) {
            GuestDiscoverStatusPagingSource()
        }.flow
    }

    override fun discoverHashtags(pageSize: Int): Flow<PagingData<UiHashtag>> {
        return Pager(
            config = PagingConfig(pageSize = pageSize),
        ) {
            TrendHashtagPagingSource(
                GuestMastodonService,
            )
        }.flow
    }

    override fun supportedComposeEvent(statusKey: MicroBlogKey?): List<SupportedComposeEvent> {
        return emptyList()
    }
}
