package dev.dimension.flare.data.datasource.microblog.handler

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import dev.dimension.flare.common.CacheData
import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.common.MemCacheable
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.model.DbDirectMessageTimeline
import dev.dimension.flare.data.database.cache.model.DbMessageItem
import dev.dimension.flare.data.datasource.microblog.loader.DirectMessageLoader
import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.datasource.microblog.paging.createPagingRemoteMediator
import dev.dimension.flare.data.datasource.microblog.pagingConfig
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.UiDMRoom
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Clock
import kotlin.uuid.Uuid

@OptIn(ExperimentalPagingApi::class)
internal class DirectMessageHandler(
    private val accountKey: MicroBlogKey,
    private val loader: DirectMessageLoader,
) : KoinComponent {
    private val database: CacheDatabase by inject()
    private val coroutineScope: CoroutineScope by inject()
    private val accountType get() = AccountType.Specific(accountKey)
    private val roomPagingKey by lazy { "dm_rooms_$accountKey" }

    fun directMessageList(scope: CoroutineScope): Flow<PagingData<UiDMRoom>> =
        Pager(
            config = pagingConfig,
            remoteMediator =
                createPagingRemoteMediator(
                    database = database,
                    pagingKey = roomListLoader.pagingKey,
                    onLoad = roomListLoader::load,
                    onSave = ::saveRooms,
                ),
            pagingSourceFactory = {
                database.messageDao().getRoomPagingSource(accountType)
            },
        ).flow
            .cachedIn(scope)
            .map { paging ->
                paging.map { it.content }
            }.cachedIn(scope)

    fun directMessageConversation(
        roomKey: MicroBlogKey,
        scope: CoroutineScope,
    ): Flow<PagingData<UiDMItem>> =
        Pager(
            config = pagingConfig,
            remoteMediator =
                createPagingRemoteMediator(
                    database = database,
                    pagingKey = conversationLoader(roomKey).pagingKey,
                    onLoad = conversationLoader(roomKey)::load,
                    onSave = { request, data ->
                        saveMessages(roomKey, request, data)
                    },
                ),
            pagingSourceFactory = {
                database.messageDao().getRoomMessagesPagingSource(roomKey)
            },
        ).flow
            .cachedIn(scope)
            .map { paging ->
                paging.map { it.content }
            }.cachedIn(scope)

    fun getDirectMessageConversationInfo(roomKey: MicroBlogKey): CacheData<UiDMRoom> =
        Cacheable(
            fetchSource = {
                val info = loader.loadConversationInfo(roomKey)
                upsertRoom(info)
            },
            cacheSource = {
                database
                    .messageDao()
                    .getRoomInfo(roomKey, accountType)
                    .distinctUntilChanged()
                    .mapNotNull { it?.content }
            },
        )

    suspend fun fetchNewDirectMessageForConversation(roomKey: MicroBlogKey) {
        val result = tryRun { loader.fetchNewMessages(roomKey) }
        result.onSuccess {
            saveMessages(
                roomKey = roomKey,
                request = PagingRequest.Append(nextKey = ""),
                data = it,
            )
            database.messageDao().getRoomInfo(roomKey, accountType).first()?.content?.let { room ->
                val latestMessage =
                    (it.maxByOrNull { item -> item.timestamp.value.toEpochMilliseconds() } ?: room.lastMessage)
                upsertRoom(
                    room.copy(
                        lastMessage = latestMessage,
                        unreadCount = 0L,
                    ),
                )
            }
        }
    }

    private val roomListLoader =
        object : CacheableRemoteLoader<UiDMRoom> {
            override val pagingKey: String = roomPagingKey

            override suspend fun load(
                pageSize: Int,
                request: PagingRequest,
            ): PagingResult<UiDMRoom> =
                when (request) {
                    PagingRequest.Refresh ->
                        loader.loadRoomList(
                            pageSize = pageSize,
                            cursor = null,
                        )

                    is PagingRequest.Prepend ->
                        PagingResult(
                            endOfPaginationReached = true,
                        )

                    is PagingRequest.Append ->
                        loader.loadRoomList(
                            pageSize = pageSize,
                            cursor = request.nextKey,
                        )
                }
        }

    private fun conversationLoader(roomKey: MicroBlogKey) =
        object : CacheableRemoteLoader<UiDMItem> {
            override val pagingKey: String = "dm_conversation_${roomKey}_$accountKey"

            override suspend fun load(
                pageSize: Int,
                request: PagingRequest,
            ): PagingResult<UiDMItem> =
                when (request) {
                    PagingRequest.Refresh ->
                        loader.loadConversation(
                            roomKey = roomKey,
                            pageSize = pageSize,
                            cursor = null,
                        )

                    is PagingRequest.Prepend ->
                        PagingResult(
                            endOfPaginationReached = true,
                        )

                    is PagingRequest.Append ->
                        loader.loadConversation(
                            roomKey = roomKey,
                            pageSize = pageSize,
                            cursor = request.nextKey,
                        )
                }
        }

    private suspend fun saveRooms(
        request: PagingRequest,
        data: List<UiDMRoom>,
    ) {
        database.connect {
            if (request is PagingRequest.Refresh) {
                database.messageDao().clearMessageTimeline(accountType)
            }
            database.messageDao().insertTimeline(
                data.map { room ->
                    room.toDbDirectMessageTimeline()
                },
            )
        }
    }

    private suspend fun saveMessages(
        roomKey: MicroBlogKey,
        request: PagingRequest,
        data: List<UiDMItem>,
    ) {
        database.connect {
            if (request is PagingRequest.Refresh) {
                database.messageDao().clearRoomMessage(roomKey)
            }
            database.messageDao().insertMessages(
                data.map { item ->
                    item.toDbMessageItem(roomKey)
                },
            )
        }
    }

    private suspend fun upsertRoom(room: UiDMRoom) {
        database.connect {
            database.messageDao().insertTimeline(
                listOf(room.toDbDirectMessageTimeline()),
            )
        }
    }

    private fun UiDMRoom.toDbDirectMessageTimeline() =
        DbDirectMessageTimeline(
            accountType = accountType,
            roomKey = key,
            sortId = lastMessage?.timestamp?.value?.toEpochMilliseconds() ?: 0L,
            unreadCount = unreadCount,
            content = this,
        )

    private fun UiDMItem.toDbMessageItem(roomKey: MicroBlogKey) =
        DbMessageItem(
            messageKey = key,
            roomKey = roomKey,
            timestamp = timestamp.value.toEpochMilliseconds(),
            content = this,
        )

    fun sendDirectMessage(
        roomKey: MicroBlogKey,
        message: String,
    ) {
        coroutineScope.launch {
            val tempMessage = createSendingDirectMessage(roomKey, message)
            database.messageDao().insertMessages(listOf(tempMessage))
            tryRun {
                loader.sendMessage(roomKey, message)
            }.onSuccess {
                database.messageDao().deleteMessage(tempMessage.messageKey)
            }.onFailure {
                database.messageDao().insertMessages(
                    listOf(
                        tempMessage.copy(
                            content =
                                tempMessage.content.copy(
                                    sendState = UiDMItem.SendState.Failed,
                                ),
                        ),
                    ),
                )
            }
        }
    }

    fun retrySendDirectMessage(messageKey: MicroBlogKey) {
        coroutineScope.launch {
            val current = database.messageDao().getMessage(messageKey)
            if (current != null && current.isLocal) {
                database.messageDao().insertMessages(
                    listOf(
                        current.copy(
                            content =
                                current.content.copy(
                                    sendState = UiDMItem.SendState.Sending,
                                ),
                        ),
                    ),
                )
                tryRun {
                    loader.sendMessage(
                        current.roomKey,
                        (current.content.content as? UiDMItem.Message.Text)?.text?.raw.orEmpty(),
                    )
                }.onSuccess {
                    database.messageDao().deleteMessage(current.messageKey)
                }.onFailure {
                    database.messageDao().insertMessages(
                        listOf(
                            current.copy(
                                content =
                                    current.content.copy(
                                        sendState = UiDMItem.SendState.Failed,
                                    ),
                            ),
                        ),
                    )
                }
            }
        }
    }

    fun deleteDirectMessage(
        roomKey: MicroBlogKey,
        messageKey: MicroBlogKey,
    ) {
        coroutineScope.launch {
            val current = database.messageDao().getMessage(messageKey)
            if (current != null && current.isLocal) {
                database.messageDao().deleteMessage(messageKey)
            } else {
                tryRun {
                    loader.deleteMessage(roomKey, messageKey)
                }.onSuccess {
                    database.messageDao().deleteMessage(messageKey)
                }
            }
        }
    }

    fun leaveDirectMessage(roomKey: MicroBlogKey) {
        coroutineScope.launch {
            tryRun {
                loader.leaveConversation(roomKey)
            }.onSuccess {
                database.messageDao().deleteRoomTimeline(roomKey, accountType)
                database.messageDao().deleteRoomMessages(roomKey)
            }
        }
    }

    fun createDirectMessageRoom(userKey: MicroBlogKey): Flow<UiState<MicroBlogKey>> =
        flow {
            tryRun {
                loader.createRoom(userKey)
            }.fold(
                onSuccess = {
                    emit(UiState.Success(it))
                },
                onFailure = {
                    emit(UiState.Error(it))
                },
            )
        }

    suspend fun canSendDirectMessage(userKey: MicroBlogKey): Boolean =
        tryRun {
            loader.canSendMessage(userKey)
        }.getOrDefault(false)

    private val badgeCountKey by lazy {
        "dm_badgeCount_$accountKey"
    }

    val directMessageBadgeCount: CacheData<Int> by lazy {
        MemCacheable(
            key = badgeCountKey,
            fetchSource = {
                loader.fetchBadgeCount()
            },
        )
    }

    private suspend fun createSendingDirectMessage(
        roomKey: MicroBlogKey,
        message: String,
    ): DbMessageItem {
        val now = Clock.System.now()
        val messageKey = MicroBlogKey(Uuid.random().toString(), accountKey.host)
        val userProfile =
            database.userDao().getUser(accountKey)?.content
                ?: UiProfile.placeholder(accountKey)
        return DbMessageItem(
            roomKey = roomKey,
            timestamp = now.toEpochMilliseconds(),
            messageKey = messageKey,
            content =
                UiDMItem(
                    key = messageKey,
                    user = userProfile,
                    content =
                        UiDMItem.Message.Text(
                            text = message.toUiPlainText(),
                        ),
                    timestamp =
                        kotlin.time.Instant
                            .fromEpochMilliseconds(now.toEpochMilliseconds())
                            .toUi(),
                    isFromMe = true,
                    sendState = UiDMItem.SendState.Sending,
                    showSender = false,
                ),
            isLocal = true,
        )
    }
}
