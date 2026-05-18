package dev.dimension.flare.data.datasource.bluesky

import chat.bsky.convo.DeleteMessageForSelfRequest
import chat.bsky.convo.GetConvoForMembersQueryParams
import chat.bsky.convo.GetConvoQueryParams
import chat.bsky.convo.GetLogQueryParams
import chat.bsky.convo.GetLogResponseLogUnion
import chat.bsky.convo.GetMessagesQueryParams
import chat.bsky.convo.GetMessagesResponseMessageUnion
import chat.bsky.convo.LeaveConvoRequest
import chat.bsky.convo.ListConvosQueryParams
import chat.bsky.convo.LogCreateMessageMessageUnion
import chat.bsky.convo.LogDeleteMessageMessageUnion
import chat.bsky.convo.MessageInput
import chat.bsky.convo.SendMessageRequest
import chat.bsky.convo.UpdateReadRequest
import dev.dimension.flare.data.database.cache.mapper.Bluesky
import dev.dimension.flare.data.datasource.microblog.loader.DirectMessageDelta
import dev.dimension.flare.data.datasource.microblog.loader.DirectMessageLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.bluesky.BlueskyService
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.UiDMRoom
import dev.dimension.flare.ui.model.UiState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import sh.christian.ozone.api.Did

internal class BlueskyDirectMessageLoader(
    private val getService: suspend () -> BlueskyService,
    private val accountKey: MicroBlogKey,
) : DirectMessageLoader {
    override val platformType: PlatformType = PlatformType.Bluesky

    override suspend fun loadRooms(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiDMRoom> {
        val response =
            getService()
                .listConvos(
                    params =
                        ListConvosQueryParams(
                            limit = pageSize.toLong(),
                            cursor = (request as? PagingRequest.Append)?.nextKey,
                        ),
                ).requireResponse()
        return PagingResult(
            data = Bluesky.rooms(accountKey, response.convos),
            nextKey = response.cursor,
        )
    }

    override suspend fun loadMessages(
        roomKey: MicroBlogKey,
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiDMItem> {
        val service = getService()
        if (request == PagingRequest.Refresh) {
            service.updateRead(
                request =
                    UpdateReadRequest(
                        convoId = roomKey.id,
                    ),
            )
        }
        val response =
            service
                .getMessages(
                    params =
                        GetMessagesQueryParams(
                            convoId = roomKey.id,
                            limit = pageSize.toLong(),
                            cursor = (request as? PagingRequest.Append)?.nextKey,
                        ),
                ).requireResponse()
        return PagingResult(
            data =
                response.messages.mapNotNull {
                    when (it) {
                        is GetMessagesResponseMessageUnion.MessageView -> {
                            Bluesky
                                .messages(
                                    accountKey = accountKey,
                                    roomKey = roomKey,
                                    data = listOf(it.value),
                                ).single()
                        }

                        is GetMessagesResponseMessageUnion.DeletedMessageView -> {
                            Bluesky.message(
                                accountKey = accountKey,
                                roomKey = roomKey,
                                data = it.value,
                            )
                        }

                        is GetMessagesResponseMessageUnion.Unknown -> {
                            null
                        }
                    }
                },
            nextKey = response.cursor,
        )
    }

    override suspend fun fetchRoomInfo(roomKey: MicroBlogKey): UiDMRoom {
        val response =
            getService()
                .getConvo(params = GetConvoQueryParams(convoId = roomKey.id))
                .requireResponse()
        return Bluesky.rooms(accountKey, listOf(response.convo)).single()
    }

    override suspend fun sendMessage(
        roomKey: MicroBlogKey,
        message: String,
    ): UiDMItem {
        val response =
            getService().sendMessage(
                request =
                    SendMessageRequest(
                        convoId = roomKey.id,
                        message = MessageInput(message),
                    ),
            )
        return Bluesky
            .messages(
                accountKey = accountKey,
                roomKey = roomKey,
                data = listOf(response.requireResponse()),
            ).single()
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
        roomKey: MicroBlogKey,
        cursor: String?,
    ): DirectMessageDelta {
        val service = getService()
        val response =
            service
                .getLog(
                    params =
                        GetLogQueryParams(
                            cursor = cursor,
                        ),
                ).requireResponse()
        service.updateRead(
            request =
                UpdateReadRequest(
                    convoId = roomKey.id,
                ),
        )
        val messages = mutableListOf<UiDMItem>()
        val deletedMessageKeys = mutableListOf<MicroBlogKey>()
        response.logs.forEach {
            when (it) {
                is GetLogResponseLogUnion.CreateMessage -> {
                    when (val message = it.value.message) {
                        is LogCreateMessageMessageUnion.MessageView -> {
                            messages += Bluesky.messages(accountKey, roomKey, listOf(message.value))
                        }

                        is LogCreateMessageMessageUnion.DeletedMessageView -> {
                            deletedMessageKeys += Bluesky.deletedMessageKey(accountKey, message.value)
                        }

                        is LogCreateMessageMessageUnion.Unknown -> {
                            Unit
                        }
                    }
                }

                is GetLogResponseLogUnion.DeleteMessage -> {
                    when (val message = it.value.message) {
                        is LogDeleteMessageMessageUnion.MessageView -> {
                            messages += Bluesky.messages(accountKey, roomKey, listOf(message.value))
                        }

                        is LogDeleteMessageMessageUnion.DeletedMessageView -> {
                            deletedMessageKeys += Bluesky.deletedMessageKey(accountKey, message.value)
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
        return DirectMessageDelta(
            messages = messages,
            deletedMessageKeys = deletedMessageKeys,
        )
    }

    override suspend fun leaveRoom(roomKey: MicroBlogKey) {
        getService().leaveConvo(
            request =
                LeaveConvoRequest(
                    convoId = roomKey.id,
                ),
        )
    }

    override fun createRoom(userKey: MicroBlogKey): Flow<UiState<UiDMRoom>> =
        flow {
            tryRun {
                getService()
                    .getConvoForMembers(
                        params =
                            GetConvoForMembersQueryParams(
                                members = persistentListOf(Did(did = userKey.id)),
                            ),
                    ).requireResponse()
            }.fold(
                onSuccess = {
                    emit(UiState.Success(Bluesky.rooms(accountKey, listOf(it.convo)).single()))
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

    override suspend fun loadBadgeCount(): Int {
        val response =
            getService()
                .listConvos(
                    params = ListConvosQueryParams(),
                ).requireResponse()
        return response.convos.sumOf { it.unreadCount.toInt() }
    }
}
