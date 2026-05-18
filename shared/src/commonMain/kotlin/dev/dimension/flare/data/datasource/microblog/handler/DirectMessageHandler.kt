package dev.dimension.flare.data.datasource.microblog.handler

import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dev.dimension.flare.common.CacheData
import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.datasource.microblog.createSendingDirectMessage
import dev.dimension.flare.data.datasource.microblog.loader.DirectMessageLoader
import dev.dimension.flare.data.datasource.microblog.pagingConfig
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.UiDMRoom
import dev.dimension.flare.ui.model.UiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

@OptIn(ExperimentalPagingApi::class)
internal class DirectMessageHandler(
    private val accountKey: MicroBlogKey,
    private val loader: DirectMessageLoader,
    private val coroutineScope: CoroutineScope,
) : KoinComponent {
    private val database: CacheDatabase by inject()
    private val accountType = AccountType.Specific(accountKey)

    fun list(scope: CoroutineScope): Flow<PagingData<UiDMRoom>> =
        loader
            .listFlow(
                source =
                    Pager(
                        config = pagingConfig,
                        remoteMediator =
                            loader.listRemoteMediator(
                                database = database,
                                accountKey = accountKey,
                            ),
                        pagingSourceFactory = {
                            database.messageDao().getRoomPagingSource(accountType = accountType)
                        },
                    ).flow.cachedIn(scope),
                scope = scope,
            ).cachedIn(scope)

    fun conversation(
        roomKey: MicroBlogKey,
        scope: CoroutineScope,
    ): Flow<PagingData<UiDMItem>> =
        loader
            .conversationFlow(
                source =
                    Pager(
                        config = pagingConfig,
                        remoteMediator =
                            loader.conversationRemoteMediator(
                                database = database,
                                accountKey = accountKey,
                                roomKey = roomKey,
                            ),
                        pagingSourceFactory = {
                            database.messageDao().getRoomMessagesPagingSource(roomKey = roomKey)
                        },
                    ).flow.cachedIn(scope),
                scope = scope,
            ).cachedIn(scope)

    fun roomInfo(roomKey: MicroBlogKey): CacheData<UiDMRoom> =
        Cacheable(
            fetchSource = {
                loader.fetchRoomInfo(
                    database = database,
                    accountKey = accountKey,
                    roomKey = roomKey,
                )
            },
            cacheSource = {
                loader.roomInfoFlow(
                    database
                        .messageDao()
                        .getRoomInfo(
                            roomKey = roomKey,
                            accountType = accountType,
                        ).distinctUntilChanged(),
                )
            },
        )

    fun send(
        roomKey: MicroBlogKey,
        message: String,
    ) {
        coroutineScope.launch {
            val tempMessage = createSendingDirectMessage(accountKey, roomKey, message, loader.platformType)
            database.messageDao().insertMessages(listOf(tempMessage))
            tryRun {
                loader.sendMessage(
                    database = database,
                    accountKey = accountKey,
                    roomKey = roomKey,
                    message = message,
                )
            }.onSuccess {
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

    fun retry(messageKey: MicroBlogKey) {
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
                        database = database,
                        accountKey = accountKey,
                        roomKey = current.roomKey,
                        message = text,
                    )
                }.onSuccess {
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

    fun delete(
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
                }
            }
        }
    }

    suspend fun fetchNew(roomKey: MicroBlogKey) {
        loader.fetchNewMessages(
            database = database,
            accountKey = accountKey,
            roomKey = roomKey,
        )
    }

    val badgeCount: CacheData<Int> by lazy {
        loader.badgeCount(
            database = database,
            accountKey = accountKey,
        )
    }

    fun leave(roomKey: MicroBlogKey) {
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

    fun createRoom(userKey: MicroBlogKey): Flow<UiState<MicroBlogKey>> =
        loader.createRoom(
            database = database,
            accountKey = accountKey,
            userKey = userKey,
        )

    suspend fun canSend(userKey: MicroBlogKey): Boolean = loader.canSend(userKey)
}
