package dev.dimension.flare.data.datasource.nostr

import androidx.paging.PagingData
import dev.dimension.flare.common.BaseTimelineLoader
import dev.dimension.flare.common.CacheData
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.datasource.microblog.AuthenticatedMicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datasource.microblog.ComposeProgress
import dev.dimension.flare.data.datasource.microblog.ComposeType
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.datasource.microblog.ProfileAction
import dev.dimension.flare.data.datasource.microblog.ProfileTab
import dev.dimension.flare.data.network.nostr.NostrService
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiHashtag
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.UiTimeline
import dev.dimension.flare.ui.model.UiUserV2
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

internal class NostrDataSource(
    override val accountKey: MicroBlogKey,
) : AuthenticatedMicroblogDataSource,
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    val service by lazy {
        NostrService(
            privateKeyFlow =
                accountRepository
                    .credentialFlow<UiAccount.Nostr.Credential>(accountKey)
                    .map { it.privateKey },
        )
    }

    override fun notification(
        type: NotificationFilter,
        pageSize: Int,
        scope: CoroutineScope,
    ): Flow<PagingData<UiTimeline>> {
        TODO("Not yet implemented")
    }

    override val supportedNotificationFilter: List<NotificationFilter>
        get() = TODO("Not yet implemented")

    override fun relation(userKey: MicroBlogKey): Flow<UiState<UiRelation>> {
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

    override fun composeConfig(type: ComposeType): ComposeConfig {
        TODO("Not yet implemented")
    }

    override fun profileActions(): List<ProfileAction> {
        TODO("Not yet implemented")
    }

    override suspend fun follow(
        userKey: MicroBlogKey,
        relation: UiRelation,
    ) {
        TODO("Not yet implemented")
    }

    private val database: CacheDatabase by inject()

    override fun homeTimeline(): BaseTimelineLoader =
        HomeTimelineRemoteMediator(
            accountKey = accountKey,
            service = service,
            database = database,
        )

    override fun userByAcct(acct: String): CacheData<UiUserV2> {
        TODO("Not yet implemented")
    }

    override fun userById(id: String): CacheData<UiProfile> {
        TODO("Not yet implemented")
    }

    override fun userTimeline(
        userKey: MicroBlogKey,
        mediaOnly: Boolean,
    ): BaseTimelineLoader {
        TODO("Not yet implemented")
    }

    override fun context(statusKey: MicroBlogKey): BaseTimelineLoader {
        TODO("Not yet implemented")
    }

    override fun status(statusKey: MicroBlogKey): CacheData<UiTimeline> {
        TODO("Not yet implemented")
    }

    override fun searchStatus(query: String): BaseTimelineLoader {
        TODO("Not yet implemented")
    }

    override fun searchUser(
        query: String,
        pageSize: Int,
    ): Flow<PagingData<UiUserV2>> {
        TODO("Not yet implemented")
    }

    override fun discoverUsers(pageSize: Int): Flow<PagingData<UiUserV2>> {
        TODO("Not yet implemented")
    }

    override fun discoverStatuses(): BaseTimelineLoader {
        TODO("Not yet implemented")
    }

    override fun discoverHashtags(pageSize: Int): Flow<PagingData<UiHashtag>> {
        TODO("Not yet implemented")
    }

    override fun following(
        userKey: MicroBlogKey,
        scope: CoroutineScope,
        pageSize: Int,
    ): Flow<PagingData<UiUserV2>> {
        TODO("Not yet implemented")
    }

    override fun fans(
        userKey: MicroBlogKey,
        scope: CoroutineScope,
        pageSize: Int,
    ): Flow<PagingData<UiUserV2>> {
        TODO("Not yet implemented")
    }

    override fun profileTabs(userKey: MicroBlogKey): ImmutableList<ProfileTab> {
        TODO("Not yet implemented")
    }
}
