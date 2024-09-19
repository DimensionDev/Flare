package dev.dimension.flare.data.datasource.vvo

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dev.dimension.flare.common.CacheData
import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.common.FileItem
import dev.dimension.flare.common.MemCacheable
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.VVO
import dev.dimension.flare.data.database.cache.mapper.toDbUser
import dev.dimension.flare.data.database.cache.model.StatusContent
import dev.dimension.flare.data.database.cache.model.updateStatusUseCase
import dev.dimension.flare.data.datasource.microblog.ComposeConfig
import dev.dimension.flare.data.datasource.microblog.ComposeData
import dev.dimension.flare.data.datasource.microblog.ComposeProgress
import dev.dimension.flare.data.datasource.microblog.MicroblogDataSource
import dev.dimension.flare.data.datasource.microblog.NotificationFilter
import dev.dimension.flare.data.datasource.microblog.ProfileAction
import dev.dimension.flare.data.datasource.microblog.StatusEvent
import dev.dimension.flare.data.datasource.microblog.VVOComposeData
import dev.dimension.flare.data.datasource.microblog.relationKeyWithUserKey
import dev.dimension.flare.data.datasource.microblog.timelinePager
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.data.network.vvo.model.StatusDetailItem
import dev.dimension.flare.data.repository.LocalFilterRepository
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
import dev.dimension.flare.ui.model.toUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@OptIn(ExperimentalPagingApi::class)
class VVODataSource(
    override val account: UiAccount.VVo,
) : MicroblogDataSource,
    KoinComponent,
    StatusEvent.VVO {
    private val database: CacheDatabase by inject()
    private val localFilterRepository: LocalFilterRepository by inject()
    private val coroutineScope: CoroutineScope by inject()
    private val service by lazy {
        VVOService(account.credential.chocolate)
    }

    override fun homeTimeline(
        pageSize: Int,
        pagingKey: String,
        scope: CoroutineScope,
    ): Flow<PagingData<UiTimeline>> =
        timelinePager(
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

    override fun notification(
        type: NotificationFilter,
        pageSize: Int,
        pagingKey: String,
        scope: CoroutineScope,
    ): Flow<PagingData<UiTimeline>> =
        when (type) {
            NotificationFilter.All -> TODO()
            NotificationFilter.Mention ->
                timelinePager(
                    pageSize = pageSize,
                    pagingKey = pagingKey,
                    accountKey = account.accountKey,
                    database = database,
                    filterFlow = localFilterRepository.getFlow(forTimeline = true),
                    scope = scope,
                    mediator =
                        MentionRemoteMediator(
                            service,
                            database,
                            account.accountKey,
                            pagingKey,
                        ),
                )

            NotificationFilter.Comment ->
                Pager(
                    config = PagingConfig(pageSize = pageSize),
                ) {
                    CommentPagingSource(
                        service = service,
                        accountKey = account.accountKey,
                        event = this,
                    )
                }.flow.cachedIn(scope)

            NotificationFilter.Like ->
                Pager(
                    config = PagingConfig(pageSize = pageSize),
                ) {
                    LikePagingSource(
                        service = service,
                        accountKey = account.accountKey,
                        event = this,
                    )
                }.flow.cachedIn(scope)
        }

    override val supportedNotificationFilter: List<NotificationFilter>
        get() =
            listOf(
                NotificationFilter.Mention,
                NotificationFilter.Comment,
                NotificationFilter.Like,
            )

    override fun userByAcct(acct: String): CacheData<UiUserV2> {
        val (name, host) = MicroBlogKey.valueOf(acct.removePrefix("@"))
        return Cacheable(
            fetchSource = {
                val config = service.config()
                val uid = service.getUid(name)
                requireNotNull(uid) { "user not found" }
                val st = config.data?.st
                requireNotNull(st) { "st is null" }
                val profile = service.profileInfo(uid, st)
                val user = profile.data?.user?.toDbUser()
                requireNotNull(user) { "user not found" }
                database.userDao().insert(user)
            },
            cacheSource = {
                database
                    .userDao()
                    .findByHandleAndHost(name, host, PlatformType.VVo)
                    .mapNotNull { it?.render(account.accountKey) }
            },
        )
    }

    override fun userById(id: String): CacheData<UiProfile> {
        val userKey = MicroBlogKey(id, account.accountKey.host)
        return Cacheable(
            fetchSource = {
                val config = service.config()
                val st = config.data?.st
                requireNotNull(st) { "st is null" }
                val profile = service.profileInfo(id, st)
                val user = profile.data?.user?.toDbUser()
                requireNotNull(user) { "user not found" }
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

    override fun relation(userKey: MicroBlogKey): Flow<UiState<UiRelation>> =
        MemCacheable<UiRelation>(
            relationKeyWithUserKey(userKey),
        ) {
            val config = service.config()
            val st = config.data?.st
            requireNotNull(st) { "st is null" }
            val profile = service.profileInfo(userKey.id, st)
            val user =
                profile.data
                    ?.user
            requireNotNull(user) { "user not found" }
            UiRelation(
                following = user.following ?: false,
                isFans = user.followMe ?: false,
            )
        }.toUi()

    override fun userTimeline(
        userKey: MicroBlogKey,
        scope: CoroutineScope,
        pageSize: Int,
        mediaOnly: Boolean,
        pagingKey: String,
    ): Flow<PagingData<UiTimeline>> =
        timelinePager(
            pageSize = pageSize,
            pagingKey = pagingKey,
            accountKey = account.accountKey,
            database = database,
            filterFlow = localFilterRepository.getFlow(forTimeline = true),
            scope = scope,
            mediator =
                UserTimelineRemoteMediator(
                    userKey = userKey,
                    service = service,
                    database = database,
                    accountKey = account.accountKey,
                    pagingKey = pagingKey,
                    mediaOnly = mediaOnly,
                ),
        )

    override fun context(
        statusKey: MicroBlogKey,
        scope: CoroutineScope,
        pageSize: Int,
        pagingKey: String,
    ): Flow<PagingData<UiTimeline>> {
        TODO("Not yet implemented")
    }

    override fun status(statusKey: MicroBlogKey): CacheData<UiTimeline> {
        val pagingKey = "status_only_$statusKey"
        val regex =
            "\\\$render_data\\s*=\\s*(\\[\\{.*?\\}\\])\\[0\\]\\s*\\|\\|\\s*\\{\\};"
                .toRegex()
        return Cacheable(
            fetchSource = {
                val response =
                    service
                        .getStatusDetail(statusKey.id)
                        .split("\n")
                        .joinToString("")
                val json =
                    regex
                        .find(response)
                        ?.groupValues
                        ?.get(1)
                        ?.decodeJson<List<StatusDetailItem>>()
                        ?: throw Exception("status not found")
                val item = json.firstOrNull()?.status

                if (item != null) {
                    VVO.saveStatus(
                        accountKey = account.accountKey,
                        pagingKey = pagingKey,
                        database = database,
                        statuses = listOf(item),
                    )
                } else {
                    throw Exception("status not found")
                }
            },
            cacheSource = {
                database
                    .statusDao()
                    .get(statusKey, account.accountKey)
                    .mapNotNull { it?.content?.render(account.accountKey, this) }
            },
        )
    }

    fun comment(statusKey: MicroBlogKey): CacheData<UiTimeline> {
        val pagingKey = "comment_only_$statusKey"
        return Cacheable(
            fetchSource = {
                val item =
                    service
                        .getHotFlowChild(statusKey.id)
                        .rootComment
                        ?.firstOrNull()
                if (item != null) {
                    VVO.saveComment(
                        accountKey = account.accountKey,
                        pagingKey = pagingKey,
                        database = database,
                        statuses = listOf(item),
                    )
                } else {
                    throw Exception("status not found")
                }
            },
            cacheSource = {
                database
                    .statusDao()
                    .get(statusKey, account.accountKey)
                    .mapNotNull { it?.content?.render(account.accountKey, event = this) }
            },
        )
    }

    private suspend fun uploadMedia(
        fileItem: FileItem,
        st: String,
    ): String {
        val bytes = fileItem.readBytes()
        val response =
            service.uploadPic(
                st = st,
                bytes = bytes,
                filename = fileItem.name ?: "file",
            )
        return response.picID ?: throw Exception("upload failed")
    }

    override suspend fun compose(
        data: ComposeData,
        progress: (ComposeProgress) -> Unit,
    ) {
        require(data is VVOComposeData)
        val maxProgress = data.medias.size + 1
        val config = service.config()
        val st = config.data?.st
        requireNotNull(st) { "st is null" }
        val mediaIds =
            data.medias.mapIndexed { index, it ->
                uploadMedia(it, st).also {
                    progress(ComposeProgress(index + 1, maxProgress))
                }
            }
        val mediaId = mediaIds.joinToString(",")
        if (data.replyId != null && data.commentId != null) {
            service.replyComment(
                id = data.commentId,
                cid = data.replyId,
                content = data.content,
                st = st,
                picId = mediaId,
            )
        } else if (data.commentId != null) {
            service.commentStatus(
                id = data.commentId,
                content = data.content,
                st = st,
                picId = mediaId,
            )
        } else if (data.repostId != null) {
            service.repostStatus(
                id = data.repostId,
                content = data.content,
                st = st,
                picId = mediaId,
            )
        } else {
            service.updateStatus(
                content = data.content,
                st = st,
                picId = mediaId,
            )
        }
    }

    override suspend fun deleteStatus(statusKey: MicroBlogKey) {
        val config = service.config()
        val st = config.data?.st
        requireNotNull(st) { "st is null" }
        service.deleteStatus(
            mid = statusKey.id,
            st = st,
        )
    }

    override fun searchStatus(
        query: String,
        scope: CoroutineScope,
        pageSize: Int,
        pagingKey: String,
    ): Flow<PagingData<UiTimeline>> =
        timelinePager(
            pageSize = pageSize,
            pagingKey = pagingKey,
            accountKey = account.accountKey,
            database = database,
            filterFlow = localFilterRepository.getFlow(forSearch = true),
            scope = scope,
            mediator =
                SearchStatusRemoteMediator(
                    service,
                    database,
                    account.accountKey,
                    pagingKey,
                    query,
                ),
        )

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
                accountKey = account.accountKey,
                query = query,
            )
        }.flow

    override fun discoverUsers(pageSize: Int): Flow<PagingData<UiUserV2>> {
        TODO("Not yet implemented")
    }

    override fun discoverStatuses(
        pageSize: Int,
        scope: CoroutineScope,
        pagingKey: String,
    ): Flow<PagingData<UiTimeline>> =
        timelinePager(
            pageSize = pageSize,
            pagingKey = pagingKey,
            accountKey = account.accountKey,
            database = database,
            filterFlow = localFilterRepository.getFlow(forTimeline = true),
            scope = scope,
            mediator =
                DiscoverStatusRemoteMediator(
                    service,
                    database,
                    account.accountKey,
                    pagingKey,
                ),
        )

    override fun discoverHashtags(pageSize: Int): Flow<PagingData<UiHashtag>> =
        Pager(
            config = PagingConfig(pageSize = pageSize),
        ) {
            TrendHashtagPagingSource(
                service,
            )
        }.flow

    override fun composeConfig(statusKey: MicroBlogKey?): ComposeConfig =
        ComposeConfig(
            text = ComposeConfig.Text(2000),
            media = ComposeConfig.Media(if (statusKey == null) 18 else 1, false),
        )

    override suspend fun follow(
        userKey: MicroBlogKey,
        relation: UiRelation,
    ) {
        if (relation.following) {
            unfollow(userKey)
        } else {
            follow(userKey)
        }
    }

    override fun profileActions(): List<ProfileAction> = emptyList()

    suspend fun follow(userKey: MicroBlogKey) {
        val key = relationKeyWithUserKey(userKey)
        MemCacheable.updateWith<UiRelation>(
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
            MemCacheable.updateWith<UiRelation>(
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
        MemCacheable.updateWith<UiRelation>(
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
            MemCacheable.updateWith<UiRelation>(
                key = key,
            ) {
                it.copy(
                    following = true,
                )
            }
        }
    }

    fun statusComment(
        statusKey: MicroBlogKey,
        scope: CoroutineScope,
    ): Flow<PagingData<UiTimeline>> {
        val pagingKey = "status_comment_$statusKey"
        return timelinePager(
            pageSize = 20,
            pagingKey = pagingKey,
            accountKey = account.accountKey,
            database = database,
            filterFlow = localFilterRepository.getFlow(forTimeline = true),
            scope = scope,
            mediator =
                StatusCommentRemoteMediator(
                    service = service,
                    accountKey = account.accountKey,
                    statusKey = statusKey,
                    pagingKey = pagingKey,
                    database = database,
                ),
        )
    }

    fun statusRepost(
        statusKey: MicroBlogKey,
        scope: CoroutineScope,
    ): Flow<PagingData<UiTimeline>> {
        val pagingKey = "status_repost_$statusKey"
        return timelinePager(
            pageSize = 20,
            pagingKey = pagingKey,
            accountKey = account.accountKey,
            database = database,
            filterFlow = localFilterRepository.getFlow(forTimeline = true),
            scope = scope,
            mediator =
                StatusRepostRemoteMediator(
                    service = service,
                    accountKey = account.accountKey,
                    statusKey = statusKey,
                    pagingKey = pagingKey,
                    database = database,
                ),
        )
    }

    fun commentChild(
        commentKey: MicroBlogKey,
        scope: CoroutineScope,
    ): Flow<PagingData<UiTimeline>> {
        val pagingKey = "comment_child_$commentKey"
        return timelinePager(
            pageSize = 20,
            pagingKey = pagingKey,
            accountKey = account.accountKey,
            database = database,
            filterFlow = localFilterRepository.getFlow(forTimeline = true),
            scope = scope,
            mediator =
                CommentChildRemoteMediator(
                    service = service,
                    accountKey = account.accountKey,
                    commentKey = commentKey,
                    pagingKey = pagingKey,
                    database = database,
                ),
        )
    }

    fun statusExtendedText(statusKey: MicroBlogKey): Flow<UiState<String>> =
        MemCacheable(
            "status_extended_text_$statusKey",
        ) {
            val config = service.config()
            val st = config.data?.st
            requireNotNull(st) { "st is null" }
            val response = service.getStatusExtend(statusKey.id, st)
            response.data?.longTextContent.orEmpty()
        }.toUi()

    override fun like(
        statusKey: MicroBlogKey,
        liked: Boolean,
    ) {
        coroutineScope.launch {
            updateStatusUseCase<StatusContent.VVO>(
                statusKey = statusKey,
                accountKey = account.accountKey,
                cacheDatabase = database,
                update = {
                    it.copy(
                        data =
                            it.data.copy(
                                favorited = !liked,
                                attitudesCount =
                                    if (liked) {
                                        it.data.attitudesCount?.minus(1)
                                    } else {
                                        it.data.attitudesCount?.plus(1)
                                    },
                            ),
                    )
                },
            )

            runCatching {
                val st = service.config().data?.st
                requireNotNull(st) { "st is null" }
                if (liked) {
                    service.unlikeStatus(id = statusKey.id, st = st)
                } else {
                    service.likeStatus(id = statusKey.id, st = st)
                }
            }.onFailure {
                updateStatusUseCase<StatusContent.VVO>(
                    statusKey = statusKey,
                    accountKey = account.accountKey,
                    cacheDatabase = database,
                    update = {
                        it.copy(
                            data =
                                it.data.copy(
                                    favorited = liked,
                                    attitudesCount =
                                        if (liked) {
                                            it.data.attitudesCount?.plus(1)
                                        } else {
                                            it.data.attitudesCount?.minus(1)
                                        },
                                ),
                        )
                    },
                )
            }.onSuccess {
            }
        }
    }

    override fun likeComment(
        statusKey: MicroBlogKey,
        liked: Boolean,
    ) {
        coroutineScope.launch {
            updateStatusUseCase<StatusContent.VVOComment>(
                statusKey = statusKey,
                accountKey = account.accountKey,
                cacheDatabase = database,
                update = {
                    it.copy(
                        data =
                            it.data.copy(
                                liked = !liked,
                                likeCount =
                                    if (liked) {
                                        it.data.likeCount?.minus(1)
                                    } else {
                                        it.data.likeCount?.plus(1)
                                    },
                            ),
                    )
                },
            )

            runCatching {
                val st = service.config().data?.st
                requireNotNull(st) { "st is null" }
                if (liked) {
                    service.likesDestroy(id = statusKey.id, st = st)
                } else {
                    service.likesUpdate(id = statusKey.id, st = st)
                }
            }.onFailure {
                updateStatusUseCase<StatusContent.VVOComment>(
                    statusKey = statusKey,
                    accountKey = account.accountKey,
                    cacheDatabase = database,
                    update = {
                        it.copy(
                            data =
                                it.data.copy(
                                    liked = liked,
                                    likeCount =
                                        if (liked) {
                                            it.data.likeCount?.plus(1)
                                        } else {
                                            it.data.likeCount?.minus(1)
                                        },
                                ),
                        )
                    },
                )
            }.onSuccess {
            }
        }
    }
}
