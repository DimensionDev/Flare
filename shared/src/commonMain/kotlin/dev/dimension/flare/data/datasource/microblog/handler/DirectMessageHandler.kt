package dev.dimension.flare.data.datasource.microblog.handler

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import dev.dimension.flare.common.CacheData
import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.connect
import dev.dimension.flare.data.database.cache.mapper.toDbUser
import dev.dimension.flare.data.database.cache.mapper.upsertUsers
import dev.dimension.flare.data.database.cache.model.DbDirectMessageTimeline
import dev.dimension.flare.data.database.cache.model.DbMessageItem
import dev.dimension.flare.data.database.cache.model.DbMessageRoom
import dev.dimension.flare.data.database.cache.model.DbMessageRoomReference
import dev.dimension.flare.data.datasource.microblog.createSendingDirectMessage
import dev.dimension.flare.data.datasource.microblog.loader.DirectMessageDelta
import dev.dimension.flare.data.datasource.microblog.loader.DirectMessageLoader
import dev.dimension.flare.data.datasource.microblog.loader.DirectMessagePinCodeStatus
import dev.dimension.flare.data.datasource.microblog.offsetPagingConfig
import dev.dimension.flare.data.datasource.microblog.paging.DirectMessageItemDbPageLoader
import dev.dimension.flare.data.datasource.microblog.paging.DirectMessageTimelineDbPageLoader
import dev.dimension.flare.data.datasource.microblog.paging.OffsetFromStartPagingKey
import dev.dimension.flare.data.datasource.microblog.paging.OffsetFromStartPagingSource
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.createPagingRemoteMediator
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.UiDMRoom
import dev.dimension.flare.ui.model.UiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.native.HiddenFromObjC

@OptIn(ExperimentalPagingApi::class)
@HiddenFromObjC
public class DirectMessageHandler(
    private val accountKey: MicroBlogKey,
    private val loader: DirectMessageLoader,
    private val coroutineScope: CoroutineScope,
) : KoinComponent {
    private val database: CacheDatabase by inject()
    private val accountType = AccountType.Specific(accountKey)
    private val inMemoryBadgeCount = MutableStateFlow<Int?>(null)

    public val pinCodeStatus: Flow<DirectMessagePinCodeStatus>
        get() = loader.pinCodeStatus

    public suspend fun submitPinCode(pinCode: String): DirectMessagePinCodeStatus = loader.submitPinCode(pinCode)

    public fun list(scope: CoroutineScope): Flow<PagingData<UiDMRoom>> =
        Pager(
            config = offsetPagingConfig,
            remoteMediator =
                createPagingRemoteMediator<
                    OffsetFromStartPagingKey,
                    DbDirectMessageTimeline,
                    UiDMRoom,
                >(
                    database = database,
                    pagingKey = "direct-message-list-$accountKey",
                    onLoad = { pageSize, request ->
                        loader.loadRooms(pageSize, request)
                    },
                    onSaveInTransaction = { request, data ->
                        if (request == PagingRequest.Refresh) {
                            database.messageDao().clearMessageTimeline(accountType)
                        }
                        saveRooms(data)
                    },
                ),
            pagingSourceFactory = {
                OffsetFromStartPagingSource(
                    DirectMessageTimelineDbPageLoader(
                        database = database,
                        accountType = accountType,
                    ),
                )
            },
        ).flow
            .map { paging ->
                paging.map { it.content.copy(unreadCount = it.unreadCount) }
            }.combine(loader.runtimeTransformer) { paging, transformer ->
                paging.map(transformer.room)
            }.cachedIn(scope)

    public fun conversation(
        roomKey: MicroBlogKey,
        scope: CoroutineScope,
    ): Flow<PagingData<UiDMItem>> =
        Pager(
            config = offsetPagingConfig,
            remoteMediator =
                createPagingRemoteMediator<
                    OffsetFromStartPagingKey,
                    DbMessageItem,
                    UiDMItem,
                >(
                    database = database,
                    pagingKey = "direct-message-conversation-$accountKey-$roomKey",
                    onLoad = { pageSize, request ->
                        loader.loadMessages(roomKey, pageSize, request)
                    },
                    onSaveInTransaction = { request, data ->
                        if (request == PagingRequest.Refresh) {
                            database.messageDao().clearRoomMessage(roomKey = roomKey)
                            database.messageDao().clearUnreadCount(roomKey, accountType)
                        }
                        saveMessages(roomKey, data)
                        if (request == PagingRequest.Refresh) {
                            updateRoomTimelineFromMessages(roomKey, unreadCount = 0)
                        }
                    },
                ),
            pagingSourceFactory = {
                OffsetFromStartPagingSource(
                    DirectMessageItemDbPageLoader(
                        database = database,
                        roomKey = roomKey,
                    ),
                )
            },
        ).flow
            .map { paging ->
                paging.map { it.content }
            }.combine(loader.runtimeTransformer) { paging, transformer ->
                paging.map(transformer.item)
            }.cachedIn(scope)

    public fun roomInfo(roomKey: MicroBlogKey): CacheData<UiDMRoom> =
        Cacheable(
            fetchSource = {
                saveRooms(listOf(loader.fetchRoomInfo(roomKey)))
            },
            cacheSource = {
                database
                    .messageDao()
                    .getRoomInfo(
                        roomKey = roomKey,
                        accountType = accountType,
                    ).distinctUntilChanged()
                    .map { room -> room?.content?.copy(unreadCount = room.unreadCount) }
                    .filterNotNull()
                    .combine(loader.runtimeTransformer) { room, transformer ->
                        transformer.room(room)
                    }
            },
        )

    public fun send(
        roomKey: MicroBlogKey,
        message: String,
    ) {
        coroutineScope.launch {
            val tempMessage = createSendingDirectMessage(accountKey, roomKey, message, loader.platformType)
            database.messageDao().insertMessages(listOf(tempMessage))
            tryRun {
                loader.sendMessage(
                    roomKey = roomKey,
                    message = message,
                )
            }.onSuccess { sentMessage ->
                saveMessagesAndUpdateRoom(roomKey, listOf(sentMessage))
                database.messageDao().deleteMessage(tempMessage.messageKey)
            }.onFailure {
                database.messageDao().insertMessages(
                    listOf(
                        tempMessage.copy(
                            content = tempMessage.content.copy(sendState = UiDMItem.SendState.Failed),
                        ),
                    ),
                )
            }
        }
    }

    public fun retry(messageKey: MicroBlogKey) {
        coroutineScope.launch {
            val current = database.messageDao().getMessage(messageKey)
            val text = (current?.content?.content as? UiDMItem.Message.Text)?.text?.raw
            if (current?.content?.sendState != null && text != null) {
                database.messageDao().insertMessages(
                    listOf(
                        current.copy(
                            content = current.content.copy(sendState = UiDMItem.SendState.Sending),
                        ),
                    ),
                )
                tryRun {
                    loader.sendMessage(
                        roomKey = current.roomKey,
                        message = text,
                    )
                }.onSuccess { sentMessage ->
                    saveMessagesAndUpdateRoom(current.roomKey, listOf(sentMessage))
                    database.messageDao().deleteMessage(current.messageKey)
                }.onFailure {
                    database.messageDao().insertMessages(
                        listOf(
                            current.copy(
                                content = current.content.copy(sendState = UiDMItem.SendState.Failed),
                            ),
                        ),
                    )
                }
            }
        }
    }

    public fun delete(
        roomKey: MicroBlogKey,
        messageKey: MicroBlogKey,
    ) {
        coroutineScope.launch {
            val current = database.messageDao().getMessage(messageKey)
            if (current?.content?.sendState != null) {
                database.messageDao().deleteMessage(messageKey)
            } else {
                tryRun {
                    loader.deleteMessage(
                        roomKey = roomKey,
                        messageKey = messageKey,
                    )
                }.onSuccess {
                    database.messageDao().deleteMessage(messageKey)
                    updateRoomTimelineFromMessages(roomKey)
                }
            }
        }
    }

    public suspend fun fetchNew(roomKey: MicroBlogKey) {
        val cursor = database.messageDao().getLatestMessage(roomKey)?.remoteCursor
        saveDelta(
            roomKey = roomKey,
            delta = loader.fetchNewMessages(roomKey, cursor),
        )
        database.messageDao().clearUnreadCount(roomKey, accountType)
        updateRoomTimelineFromMessages(roomKey, unreadCount = 0)
    }

    public val badgeCount: CacheData<Int> by lazy {
        Cacheable(
            fetchSource = {
                inMemoryBadgeCount.value =
                    if (loader.pinCodeStatus.first().canLoadDirectMessage) {
                        loader.loadBadgeCount()
                    } else {
                        0
                    }
            },
            cacheSource = {
                inMemoryBadgeCount.filterNotNull()
            },
        )
    }

    public fun leave(roomKey: MicroBlogKey) {
        coroutineScope.launch {
            tryRun {
                loader.leaveRoom(roomKey)
            }.onSuccess {
                database.messageDao().deleteRoomTimeline(roomKey, accountType)
                database.messageDao().deleteRoom(roomKey)
                database.messageDao().deleteRoomReference(roomKey)
                database.messageDao().deleteRoomMessages(roomKey)
            }
        }
    }

    public fun createRoom(userKey: MicroBlogKey): Flow<UiState<MicroBlogKey>> =
        loader.createRoom(userKey).map { state ->
            when (state) {
                is UiState.Success -> {
                    saveRooms(listOf(state.data))
                    UiState.Success(state.data.key)
                }

                is UiState.Error -> {
                    UiState.Error(state.throwable)
                }

                is UiState.Loading -> {
                    UiState.Loading()
                }
            }
        }

    public suspend fun canSend(userKey: MicroBlogKey): Boolean = loader.canSend(userKey)

    private suspend fun saveDelta(
        roomKey: MicroBlogKey,
        delta: DirectMessageDelta,
    ) {
        database.connect {
            delta.deletedMessageKeys.forEach {
                database.messageDao().deleteMessage(it)
            }
            saveMessages(roomKey, delta.messages)
            updateRoomTimelineFromMessages(roomKey)
        }
    }

    private suspend fun saveMessagesAndUpdateRoom(
        roomKey: MicroBlogKey,
        messages: List<UiDMItem>,
    ) {
        database.connect {
            saveMessages(roomKey, messages)
            updateRoomTimelineFromMessages(roomKey)
        }
    }

    private suspend fun saveRooms(rooms: List<UiDMRoom>) {
        if (rooms.isEmpty()) {
            return
        }
        val lastMessages = rooms.mapNotNull { it.lastMessage }
        val users = rooms.flatMap { it.users } + lastMessages.map { it.user }
        database.upsertUsers(users.map { it.toDbUser(host = accountKey.host) })
        val lastMessageItems =
            rooms.mapNotNull { room ->
                room.lastMessage?.toDbMessageItem(room.key)
            }
        if (lastMessageItems.isNotEmpty()) {
            database.messageDao().insertMessages(lastMessageItems)
        }
        database.messageDao().insertReferences(
            rooms.flatMap { room ->
                room.users.map { user ->
                    DbMessageRoomReference(
                        roomKey = room.key,
                        userKey = user.key,
                    )
                }
            },
        )
        database.messageDao().insert(
            rooms.map { room ->
                DbMessageRoom(
                    roomKey = room.key,
                    platformType = loader.platformType,
                    messageKey = room.lastMessage?.key,
                )
            },
        )
        database.messageDao().insertTimeline(
            rooms.map { room ->
                DbDirectMessageTimeline(
                    accountType = accountType,
                    roomKey = room.key,
                    sortId =
                        room.lastMessage
                            ?.timestamp
                            ?.value
                            ?.toEpochMilliseconds() ?: 0L,
                    unreadCount = room.unreadCount,
                    content = room,
                )
            },
        )
    }

    private suspend fun saveMessages(
        roomKey: MicroBlogKey,
        messages: List<UiDMItem>,
    ) {
        if (messages.isEmpty()) {
            return
        }
        val resolvedMessages = messages.withCachedUsers()
        database.upsertUsers(resolvedMessages.map { it.user.toDbUser(host = accountKey.host) })
        database.messageDao().insertMessages(resolvedMessages.map { it.toDbMessageItem(roomKey) })
    }

    private suspend fun updateRoomTimelineFromMessages(
        roomKey: MicroBlogKey,
        unreadCount: Long? = null,
    ) {
        val current =
            database
                .messageDao()
                .getRoomInfo(
                    roomKey = roomKey,
                    accountType = accountType,
                ).first()
                ?: return
        val latest = database.messageDao().getLatestMessage(roomKey)
        val nextUnreadCount = unreadCount ?: current.unreadCount
        database.messageDao().insert(
            listOf(
                DbMessageRoom(
                    roomKey = roomKey,
                    platformType = loader.platformType,
                    messageKey = latest?.messageKey,
                ),
            ),
        )
        database.messageDao().insertTimeline(
            listOf(
                current.copy(
                    sortId = latest?.timestamp ?: 0L,
                    unreadCount = nextUnreadCount,
                    content =
                        current.content.copy(
                            lastMessage = latest?.content,
                            unreadCount = nextUnreadCount,
                        ),
                ),
            ),
        )
    }

    private suspend fun List<UiDMItem>.withCachedUsers(): List<UiDMItem> {
        val cachedUsers =
            database
                .userDao()
                .findByKeys(map { it.user.key })
                .first()
                .associateBy { it.userKey }
        return map { item ->
            cachedUsers[item.user.key]?.content?.let { cachedUser ->
                item.copy(user = cachedUser)
            } ?: item
        }
    }
}

private val DirectMessagePinCodeStatus.canLoadDirectMessage: Boolean
    get() =
        this == DirectMessagePinCodeStatus.NotRequired ||
            this == DirectMessagePinCodeStatus.Verified

private fun UiDMItem.toDbMessageItem(roomKey: MicroBlogKey): DbMessageItem =
    DbMessageItem(
        messageKey = key,
        roomKey = roomKey,
        userKey = user.key,
        timestamp = timestamp.value.toEpochMilliseconds(),
        content = this,
        showSender = showSender,
        isLocal = sendState != null,
        remoteCursor = remoteCursor,
    )
