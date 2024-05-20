package dev.dimension.flare.data.datasource.vvo

import androidx.paging.ExperimentalPagingApi
import androidx.paging.PagingData
import dev.dimension.flare.common.CacheData
import dev.dimension.flare.common.MemCacheable
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datasource.microblog.ComposeProgress
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.datasource.microblog.SupportedComposeEvent
import dev.dimension.flare.data.datasource.microblog.relationKeyWithUserKey
import dev.dimension.flare.data.datasource.microblog.timelinePager
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.data.repository.LocalFilterRepository
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiStatus
import dev.dimension.flare.ui.model.UiUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@OptIn(ExperimentalPagingApi::class)
class VVODataSource(
    override val account: UiAccount.VVo,
) : MicroblogDataSource, KoinComponent {
    private val database: CacheDatabase by inject()
    private val localFilterRepository: LocalFilterRepository by inject()
    private val service by lazy {
        VVOService(account.credential.chocolate)
    }

    override fun homeTimeline(
        pageSize: Int,
        pagingKey: String,
        scope: CoroutineScope,
    ): Flow<PagingData<UiStatus>> {
        return timelinePager(
            pageSize = pageSize,
            pagingKey = pagingKey,
            accountKey = account.accountKey,
            database = database,
            filterFlow = localFilterRepository.getFlow(forTimeline = true),
            scope = scope,
            mediator =
                HomeTimelineRemoteMediator(
                    service,
                    database,
                    account.accountKey,
                    pagingKey,
                ),
        )
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
        get() = TODO("Not yet implemented")

    override fun userByAcct(acct: String): CacheData<UiUser> {
        TODO("Not yet implemented")
    }

    override fun userById(id: String): CacheData<UiUser> {
        TODO("Not yet implemented")
    }

    override fun relation(userKey: MicroBlogKey): Flow<UiState<UiRelation>> {
        TODO("Not yet implemented")
    }

    override fun userTimeline(
        userKey: MicroBlogKey,
        scope: CoroutineScope,
        pageSize: Int,
        mediaOnly: Boolean,
        pagingKey: String,
    ): Flow<PagingData<UiStatus>> {
        TODO("Not yet implemented")
    }

    override fun context(
        statusKey: MicroBlogKey,
        scope: CoroutineScope,
        pageSize: Int,
        pagingKey: String,
    ): Flow<PagingData<UiStatus>> {
        TODO("Not yet implemented")
    }

    override fun status(statusKey: MicroBlogKey): CacheData<UiStatus> {
        TODO("Not yet implemented")
    }

    override suspend fun compose(
        data: ComposeData,
        progress: (ComposeProgress) -> Unit,
    ) {
        TODO("Not yet implemented")
    }

    override suspend fun deleteStatus(statusKey: MicroBlogKey) {
        TODO("Not yet implemented")
    }

    override fun searchStatus(
        query: String,
        scope: CoroutineScope,
        pageSize: Int,
        pagingKey: String,
    ): Flow<PagingData<UiStatus>> {
        TODO("Not yet implemented")
    }

    override fun searchUser(
        query: String,
        scope: CoroutineScope,
        pageSize: Int,
    ): Flow<PagingData<UiUser>> {
        TODO("Not yet implemented")
    }

    override fun discoverUsers(pageSize: Int): Flow<PagingData<UiUser>> {
        TODO("Not yet implemented")
    }

    override fun discoverStatuses(
        pageSize: Int,
        scope: CoroutineScope,
        pagingKey: String,
    ): Flow<PagingData<UiStatus>> {
        TODO("Not yet implemented")
    }

    override fun discoverHashtags(pageSize: Int): Flow<PagingData<UiHashtag>> {
        TODO("Not yet implemented")
    }

    override fun supportedComposeEvent(statusKey: MicroBlogKey?): List<SupportedComposeEvent> {
        TODO("Not yet implemented")
    }

    override suspend fun follow(
        userKey: MicroBlogKey,
        relation: UiRelation,
    ) {
        require(relation is UiRelation.VVO)
        if (relation.following) {
            unfollow(userKey)
        } else {
            follow(userKey)
        }
    }

    override suspend fun block(
        userKey: MicroBlogKey,
        relation: UiRelation,
    ) {
    }

    override suspend fun mute(
        userKey: MicroBlogKey,
        relation: UiRelation,
    ) {
    }

    suspend fun follow(userKey: MicroBlogKey) {
        val key = relationKeyWithUserKey(userKey)
        MemCacheable.updateWith<UiRelation.Mastodon>(
            key = key,
        ) {
            it.copy(
                following = true,
            )
        }
        runCatching {
            val config = service.config()
            val st = config.data?.st
            requireNotNull(st) { "st is null" }
            service.follow(
                st = st,
                uid = userKey.id,
            )
        }.onFailure {
            MemCacheable.updateWith<UiRelation.Mastodon>(
                key = key,
            ) {
                it.copy(
                    following = false,
                )
            }
        }
    }

    suspend fun unfollow(userKey: MicroBlogKey) {
        val key = relationKeyWithUserKey(userKey)
        MemCacheable.updateWith<UiRelation.Mastodon>(
            key = key,
        ) {
            it.copy(
                following = false,
            )
        }
        runCatching {
            val config = service.config()
            val st = config.data?.st
            requireNotNull(st) { "st is null" }
            service.unfollow(
                st = st,
                uid = userKey.id,
            )
        }.onFailure {
            MemCacheable.updateWith<UiRelation.Mastodon>(
                key = key,
            ) {
                it.copy(
                    following = true,
                )
            }
        }
    }
}
