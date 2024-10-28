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
import dev.dimension.flare.data.database.cache.model.UserContent
import dev.dimension.flare.data.datasource.mastodon.MastodonFansPagingSource
import dev.dimension.flare.data.datasource.mastodon.MastodonFollowingPagingSource
import dev.dimension.flare.data.datasource.mastodon.SearchUserPagingSource
import dev.dimension.flare.data.datasource.mastodon.TrendHashtagPagingSource
import dev.dimension.flare.data.datasource.mastodon.TrendsUserPagingSource
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.network.mastodon.GuestMastodonService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.mapper.render
import dev.dimension.flare.ui.model.mapper.renderGuest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object GuestDataSource : MicroblogDataSource, KoinComponent {
    private val database: CacheDatabase by inject()

    override fun homeTimeline(
        pageSize: Int,
        pagingKey: String,
        scope: CoroutineScope,
    ): Flow<PagingData<UiTimeline>> =
        Pager(PagingConfig(pageSize = pageSize)) {
            GuestTimelinePagingSource(host = GuestMastodonService.HOST)
        }.flow.cachedIn(scope)

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
                    .mapNotNull {
                        val content = it?.content
                        if (content is UserContent.Mastodon) {
                            content.data.render(null, host = host)
                        } else {
                            null
                        }
                    }
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
                    .mapNotNull {
                        val content = it?.content
                        if (content is UserContent.Mastodon) {
                            content.data.render(null, host = GuestMastodonService.HOST)
                        } else {
                            null
                        }
                    }
            },
        )
    }

    override fun userTimeline(
        userKey: MicroBlogKey,
        scope: CoroutineScope,
        pageSize: Int,
        mediaOnly: Boolean,
        pagingKey: String,
    ): Flow<PagingData<UiTimeline>> =
        Pager(PagingConfig(pageSize = pageSize)) {
            GuestUserTimelinePagingSource(
                host = GuestMastodonService.HOST,
                userId = userKey.id,
                onlyMedia = mediaOnly,
            )
        }.flow

    override fun context(
        statusKey: MicroBlogKey,
        scope: CoroutineScope,
        pageSize: Int,
        pagingKey: String,
    ): Flow<PagingData<UiTimeline>> =
        Pager(PagingConfig(pageSize = pageSize)) {
            GuestStatusDetailPagingSource(
                host = GuestMastodonService.HOST,
                statusKey = statusKey,
                statusOnly = false,
            )
        }.flow.cachedIn(scope)

    override fun status(statusKey: MicroBlogKey): CacheData<UiTimeline> {
        val pagingKey = "status_only_$statusKey"
        return MemCacheable(
            key = pagingKey,
            fetchSource = {
                GuestMastodonService
                    .lookupStatus(
                        statusKey.id,
                    ).renderGuest(GuestMastodonService.HOST)
            },
        )
    }

    override fun searchStatus(
        query: String,
        scope: CoroutineScope,
        pageSize: Int,
        pagingKey: String,
    ): Flow<PagingData<UiTimeline>> =
        Pager(PagingConfig(pageSize = pageSize)) {
            GuestSearchStatusPagingSource(host = GuestMastodonService.HOST, query = query)
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
                service = GuestMastodonService,
                host = GuestMastodonService.HOST,
                accountKey = null,
                query,
            )
        }.flow.cachedIn(scope)

    override fun discoverUsers(pageSize: Int): Flow<PagingData<UiUserV2>> =
        Pager(
            config = PagingConfig(pageSize = pageSize),
        ) {
            TrendsUserPagingSource(
                service = GuestMastodonService,
                accountKey = null,
                host = GuestMastodonService.HOST,
            )
        }.flow

    override fun discoverStatuses(
        pageSize: Int,
        scope: CoroutineScope,
        pagingKey: String,
    ): Flow<PagingData<UiTimeline>> =
        Pager(PagingConfig(pageSize = pageSize)) {
            GuestDiscoverStatusPagingSource(host = GuestMastodonService.HOST)
        }.flow.cachedIn(scope)

    override fun discoverHashtags(pageSize: Int): Flow<PagingData<UiHashtag>> =
        Pager(
            config = PagingConfig(pageSize = pageSize),
        ) {
            TrendHashtagPagingSource(
                GuestMastodonService,
            )
        }.flow

    override fun following(
        userKey: MicroBlogKey,
        scope: CoroutineScope,
        pageSize: Int,
        pagingKey: String,
    ): Flow<PagingData<UiUserV2>> =
        Pager(
            config = PagingConfig(pageSize = pageSize),
        ) {
            MastodonFollowingPagingSource(
                service = GuestMastodonService,
                host = GuestMastodonService.HOST,
                userKey = userKey,
                accountKey = null,
            )
        }.flow.cachedIn(scope)

    override fun fans(
        userKey: MicroBlogKey,
        scope: CoroutineScope,
        pageSize: Int,
        pagingKey: String,
    ): Flow<PagingData<UiUserV2>> =
        Pager(
            config = PagingConfig(pageSize = pageSize),
        ) {
            MastodonFansPagingSource(
                service = GuestMastodonService,
                host = GuestMastodonService.HOST,
                userKey = userKey,
                accountKey = null,
            )
        }.flow.cachedIn(scope)
}
