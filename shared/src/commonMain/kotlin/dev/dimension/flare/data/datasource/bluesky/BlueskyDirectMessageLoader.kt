package dev.dimension.flare.data.datasource.bluesky

import chat.bsky.convo.DeleteMessageForSelfRequest
import chat.bsky.convo.DeletedMessageView
import chat.bsky.convo.GetConvoForMembersQueryParams
import chat.bsky.convo.GetConvoQueryParams
import chat.bsky.convo.GetLogQueryParams
import chat.bsky.convo.GetLogResponseLogUnion
import chat.bsky.convo.LeaveConvoRequest
import chat.bsky.convo.ListConvosQueryParams
import chat.bsky.convo.LogCreateMessageMessageUnion
import chat.bsky.convo.LogDeleteMessageMessageUnion
import chat.bsky.convo.MessageInput
import chat.bsky.convo.MessageView
import chat.bsky.convo.SendMessageRequest
import chat.bsky.convo.UpdateReadRequest
import dev.dimension.flare.common.CacheData
import dev.dimension.flare.common.Cacheable
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.Bluesky
import dev.dimension.flare.data.database.cache.model.DbDirectMessageTimeline
import dev.dimension.flare.data.database.cache.model.DbMessageItem
import dev.dimension.flare.data.datasource.microblog.loader.DirectMessageLoader
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import sh.christian.ozone.api.Did

internal class BlueskyDirectMessageLoader(
    private val getService: suspend () -> BlueskyService,
    private val accountKey: MicroBlogKey,
    private val coroutineScope: CoroutineScope,
) : DirectMessageLoader {
    override val platformType: PlatformType = PlatformType.Bluesky

    override fun listRemoteMediator(
        database: CacheDatabase,
        accountKey: MicroBlogKey,
    ) = DMListRemoteMediator(
        getService = getService,
        accountKey = accountKey,
        database = database,
    )

    override fun conversationRemoteMediator(
        database: CacheDatabase,
        accountKey: MicroBlogKey,
        roomKey: MicroBlogKey,
    ) = DMConversationRemoteMediator(
        getService = getService,
        accountKey = accountKey,
        database = database,
        roomKey = roomKey,
        clearBadge = { room ->
            coroutineScope.launch {
                database
                    .messageDao()
                    .clearUnreadCount(room, accountType = AccountType.Specific(accountKey))
            }
        },
    )

    override suspend fun fetchRoomInfo(
        database: CacheDatabase,
        accountKey: MicroBlogKey,
        roomKey: MicroBlogKey,
    ) {
        val response =
            getService()
                .getConvo(params = GetConvoQueryParams(convoId = roomKey.id))
                .requireResponse()
        Bluesky.saveDM(
            accountKey = accountKey,
            database = database,
            data = listOf(response.convo),
        )
    }

    override suspend fun sendMessage(
        database: CacheDatabase,
        accountKey: MicroBlogKey,
        roomKey: MicroBlogKey,
        message: String,
    ) {
        val response =
            getService().sendMessage(
                request =
                    SendMessageRequest(
                        convoId = roomKey.id,
                        message = MessageInput(message),
                    ),
            )
        Bluesky.saveMessage(
            accountKey = accountKey,
            database = database,
            roomKey = roomKey,
            data = listOf(response.requireResponse()),
        )
    }

    override suspend fun deleteMessage(
        roomKey: MicroBlogKey,
        messageKey: MicroBlogKey,
    ) {
        getService().deleteMessageForSelf(
            request =
                DeleteMessageForSelfRequest(
                    convoId = roomKey.id,
                    messageId = messageKey.id,
                ),
        )
    }

    override suspend fun fetchNewMessages(
        database: CacheDatabase,
        accountKey: MicroBlogKey,
        roomKey: MicroBlogKey,
    ) {
        val service = getService()
        val cursor = database.messageDao().getLatestMessage(roomKey)?.remoteCursor
        val response =
            service.getLog(
                params =
                    GetLogQueryParams(
                        cursor = cursor,
                    ),
            )
        service.updateRead(
            request =
                UpdateReadRequest(
                    convoId = roomKey.id,
                ),
        )
        response.requireResponse().logs.forEach {
            when (it) {
                is GetLogResponseLogUnion.CreateMessage -> {
                    when (val message = it.value.message) {
                        is LogCreateMessageMessageUnion.MessageView -> {
                            handleMessage(database, accountKey, roomKey, message.value)
                        }

                        is LogCreateMessageMessageUnion.DeletedMessageView -> {
                            handleMessage(database, accountKey, message.value)
                        }

                        is LogCreateMessageMessageUnion.Unknown -> {
                            Unit
                        }
                    }
                }

                is GetLogResponseLogUnion.DeleteMessage -> {
                    when (val message = it.value.message) {
                        is LogDeleteMessageMessageUnion.MessageView -> {
                            handleMessage(database, accountKey, roomKey, message.value)
                        }

                        is LogDeleteMessageMessageUnion.DeletedMessageView -> {
                            handleMessage(database, accountKey, message.value)
                        }

                        is LogDeleteMessageMessageUnion.Unknown -> {
                            Unit
                        }
                    }
                }

                else -> {
                    Unit
                }
            }
        }
    }

    override suspend fun leaveRoom(roomKey: MicroBlogKey) {
        getService().leaveConvo(
            request =
                LeaveConvoRequest(
                    convoId = roomKey.id,
                ),
        )
    }

    override fun createRoom(
        database: CacheDatabase,
        accountKey: MicroBlogKey,
        userKey: MicroBlogKey,
    ): Flow<UiState<MicroBlogKey>> =
        flow {
            tryRun {
                getService()
                    .getConvoForMembers(
                        params =
                            GetConvoForMembersQueryParams(
                                members = persistentListOf(Did(did = userKey.id)),
                            ),
                    ).requireResponse()
            }.onSuccess {
                Bluesky.saveDM(
                    accountKey = accountKey,
                    database = database,
                    data = listOf(it.convo),
                )
            }.fold(
                onSuccess = {
                    emit(UiState.Success(MicroBlogKey(id = it.convo.id, host = accountKey.host)))
                },
                onFailure = {
                    emit(UiState.Error(it))
                },
            )
        }

    override suspend fun canSend(userKey: MicroBlogKey): Boolean =
        tryRun {
            getService()
                .getConvoForMembers(
                    params =
                        GetConvoForMembersQueryParams(
                            members = persistentListOf(Did(did = userKey.id)),
                        ),
                ).requireResponse()
        }.isSuccess

    override fun badgeCount(
        database: CacheDatabase,
        accountKey: MicroBlogKey,
    ): CacheData<Int> =
        Cacheable(
            fetchSource = {
                val response =
                    getService()
                        .listConvos(
                            params = ListConvosQueryParams(),
                        ).requireResponse()
                Bluesky.saveDM(
                    accountKey = accountKey,
                    database = database,
                    data = response.convos,
                )
            },
            cacheSource = {
                database
                    .messageDao()
                    .getRoomTimeline(accountType = AccountType.Specific(accountKey))
                    .distinctUntilChanged()
                    .map {
                        it.sumOf { room -> room.unreadCount.toInt() }
                    }
            },
        )

    private suspend fun handleMessage(
        database: CacheDatabase,
        accountKey: MicroBlogKey,
        roomKey: MicroBlogKey,
        message: MessageView,
    ) {
        Bluesky.saveMessage(
            accountKey = accountKey,
            roomKey = roomKey,
            database = database,
            data = listOf(message),
        )
    }

    private suspend fun handleMessage(
        database: CacheDatabase,
        accountKey: MicroBlogKey,
        message: DeletedMessageView,
    ) {
        database.messageDao().deleteMessage(
            MicroBlogKey(
                id = message.id,
                host = accountKey.host,
            ),
        )
    }
}
