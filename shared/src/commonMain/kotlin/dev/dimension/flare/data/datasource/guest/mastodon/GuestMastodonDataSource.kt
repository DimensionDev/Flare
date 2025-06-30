package dev.dimension.flare.data.datasource.guest.mastodon

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
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.data.network.mastodon.GuestMastodonService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.UiUserV2
import dev.dimension.flare.ui.model.mapper.render
import dev.dimension.flare.ui.model.mapper.renderGuest
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapNotNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal class GuestMastodonDataSource(
    private val host: String,
    private val locale: String,
) : MicroblogDataSource,
    KoinComponent {
    private val service by lazy {
        GuestMastodonService("https://$host/", locale)
    }
    private val database: CacheDatabase by inject()

    override fun homeTimeline(
        pageSize: Int,
        scope: CoroutineScope,
    ): Flow<PagingData<UiTimeline>> =
        Pager(PagingConfig(pageSize = pageSize)) {
            GuestTimelinePagingSource(host = host, service = service)
        }.flow.cachedIn(scope)

    override fun userByAcct(acct: String): CacheData<UiUserV2> {
        val (name, host) = MicroBlogKey.valueOf(acct)
        return Cacheable(
            fetchSource = {
                val user =
                    service
                        .lookupUserByAcct("$name@$host")
                        ?.toDbUser(host) ?: throw Exception("User not found")
                database.userDao().insert(user)
            },
            cacheSource = {
                database
                    .userDao()
                    .findByHandleAndHost(name, host, PlatformType.Mastodon)
                    .distinctUntilChanged()
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
        val userKey = MicroBlogKey(id, host)
        return Cacheable(
            fetchSource = {
                val user = service.lookupUser(id).toDbUser(host)
                database.userDao().insert(user)
            },
            cacheSource = {
                database
                    .userDao()
                    .findByKey(userKey)
                    .distinctUntilChanged()
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

    override fun userTimeline(
        userKey: MicroBlogKey,
        scope: CoroutineScope,
        pageSize: Int,
        mediaOnly: Boolean,
    ): Flow<PagingData<UiTimeline>> =
        Pager(PagingConfig(pageSize = pageSize)) {
            GuestUserTimelinePagingSource(
                host = host,
                userId = userKey.id,
                onlyMedia = mediaOnly,
                service = service,
            )
        }.flow.cachedIn(scope)

    override fun context(
        statusKey: MicroBlogKey,
        scope: CoroutineScope,
        pageSize: Int,
    ): Flow<PagingData<UiTimeline>> =
        Pager(PagingConfig(pageSize = pageSize)) {
            GuestStatusDetailPagingSource(
                host = host,
                statusKey = statusKey,
                statusOnly = false,
                service = service,
            )
        }.flow.cachedIn(scope)

    override fun status(statusKey: MicroBlogKey): CacheData<UiTimeline> {
        val pagingKey = "status_only_$statusKey"
        return MemCacheable(
            key = pagingKey,
            fetchSource = {
                service
                    .lookupStatus(
                        statusKey.id,
                    ).renderGuest(host)
            },
        )
    }

    override fun searchStatus(
        query: String,
        scope: CoroutineScope,
        pageSize: Int,
    ): Flow<PagingData<UiTimeline>> =
        Pager(PagingConfig(pageSize = pageSize)) {
            GuestSearchStatusPagingSource(host = host, query = query, service = service)
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
                service = service,
                host = host,
                accountKey = null,
                query,
            )
        }.flow.cachedIn(scope)

    override fun discoverUsers(pageSize: Int): Flow<PagingData<UiUserV2>> {
        // not supported
        throw UnsupportedOperationException("Discover users not supported")
    }

    override fun discoverStatuses(
        pageSize: Int,
        scope: CoroutineScope,
    ): Flow<PagingData<UiTimeline>> = TODO()
//        Pager(PagingConfig(pageSize = pageSize)) {
//            GuestDiscoverStatusPagingSource(host = host, service = service)
//        }.flow.cachedIn(scope)

    override fun discoverHashtags(pageSize: Int): Flow<PagingData<UiHashtag>> =
        Pager(
            config = PagingConfig(pageSize = pageSize),
        ) {
            TrendHashtagPagingSource(
                service,
            )
        }.flow

    override fun following(
        userKey: MicroBlogKey,
        scope: CoroutineScope,
        pageSize: Int,
    ): Flow<PagingData<UiUserV2>> =
        Pager(
            config = PagingConfig(pageSize = pageSize),
        ) {
            MastodonFollowingPagingSource(
                service = service,
                host = host,
                userKey = userKey,
                accountKey = null,
            )
        }.flow.cachedIn(scope)

    override fun fans(
        userKey: MicroBlogKey,
        scope: CoroutineScope,
        pageSize: Int,
    ): Flow<PagingData<UiUserV2>> =
        Pager(
            config = PagingConfig(pageSize = pageSize),
        ) {
            MastodonFansPagingSource(
                service = service,
                host = host,
                userKey = userKey,
                accountKey = null,
            )
        }.flow.cachedIn(scope)

    override fun profileTabs(
        userKey: MicroBlogKey,
        scope: CoroutineScope,
        pagingSize: Int,
    ): ImmutableList<ProfileTab> =
        persistentListOf(
            ProfileTab.Timeline(
                type = ProfileTab.Timeline.Type.Status,
                flow =
                    Pager(PagingConfig(pageSize = pagingSize)) {
                        GuestUserTimelinePagingSource(
                            host = host,
                            userId = userKey.id,
                            service = service,
                            withPinned = true,
                        )
                    }.flow.cachedIn(scope),
            ),
            ProfileTab.Timeline(
                type = ProfileTab.Timeline.Type.StatusWithReplies,
                flow =
                    Pager(PagingConfig(pageSize = pagingSize)) {
                        GuestUserTimelinePagingSource(
                            host = host,
                            userId = userKey.id,
                            withReply = true,
                            service = service,
                        )
                    }.flow.cachedIn(scope),
            ),
            ProfileTab.Media,
        )
}
