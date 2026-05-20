package dev.dimension.flare.data.datasource.xqt

import dev.dimension.flare.common.tryRun
import dev.dimension.flare.data.database.cache.mapper.XQT
import dev.dimension.flare.data.datasource.microblog.loader.DirectMessageDelta
import dev.dimension.flare.data.datasource.microblog.loader.DirectMessageLoader
import dev.dimension.flare.data.datasource.microblog.loader.DirectMessageRuntimeTransformer
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.data.network.xqt.model.AddToConversationRequest
import dev.dimension.flare.data.network.xqt.model.PostDmNew2Request
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.UiDMRoom
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiState
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
public class XQTDirectMessageLoader(
    private val service: XQTService,
    private val accountKey: MicroBlogKey,
    private val credentialFlow: Flow<UiAccount.XQT.Credential>,
) : DirectMessageLoader {
    override val platformType: PlatformType = PlatformType.xQt

    override val runtimeTransformer: Flow<DirectMessageRuntimeTransformer> =
        credentialFlow.map { credential ->
            DirectMessageRuntimeTransformer(
                room = { it.withXqtMediaAuth(credential, accountKey.host) },
                item = { it.withXqtMediaAuth(credential, accountKey.host) },
            )
        }

    override suspend fun loadRooms(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiDMRoom> {
        if (request == PagingRequest.Refresh) {
            val response = service.getDMUserUpdates().inboxInitialState
            return PagingResult(
                data =
                    XQT.rooms(
                        accountKey = accountKey,
                        propertyEntries = response?.propertyEntries,
                        users = response?.users,
                        conversations = response?.conversations,
                    ),
                nextKey =
                    if (response?.inboxTimelines?.trusted?.status == "AT_END") {
                        null
                    } else {
                        response?.inboxTimelines?.trusted?.minEntryId
                    },
            )
        }
        val maxId =
            (request as? PagingRequest.Append)?.nextKey
                ?: return PagingResult(endOfPaginationReached = true)
        val response =
            service.getDMInboxTimelineTrusted(
                maxId = maxId,
            )
        return PagingResult(
            data =
                XQT.rooms(
                    accountKey = accountKey,
                    propertyEntries = response.inboxTimeline?.propertyEntries,
                    users = response.inboxTimeline?.users,
                    conversations = response.inboxTimeline?.conversations,
                ),
            nextKey =
                if (response.inboxTimeline?.status == "AT_END") {
                    null
                } else {
                    response.inboxTimeline?.minEntryId
                },
        )
    }

    override suspend fun loadMessages(
        roomKey: MicroBlogKey,
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiDMItem> {
        val response =
            service.getDMConversationTimeline(
                conversationId = roomKey.id,
                maxId = (request as? PagingRequest.Append)?.nextKey,
            )
        if (request == PagingRequest.Refresh) {
            service.postDMConversationMarkRead(
                conversationId = roomKey.id,
                conversationId2 = roomKey.id,
                lastReadEventId = response.conversationTimeline?.maxEntryId.orEmpty(),
            )
        }
        return PagingResult(
            data =
                XQT.messages(
                    accountKey = accountKey,
                    propertyEntries = response.conversationTimeline?.propertyEntries,
                    users = response.conversationTimeline?.users,
                    conversations = response.conversationTimeline?.conversations,
                ),
            nextKey =
                if (response.conversationTimeline?.status == "AT_END") {
                    null
                } else {
                    response.conversationTimeline?.minEntryId
                },
        )
    }

    override suspend fun fetchRoomInfo(roomKey: MicroBlogKey): UiDMRoom {
        val response =
            service.getDMConversationTimeline(
                conversationId = roomKey.id,
                context = "FETCH_DM_CONVERSATION",
                maxId = "0",
            )
        return XQT
            .rooms(
                accountKey = accountKey,
                propertyEntries = response.conversationTimeline?.propertyEntries,
                users = response.conversationTimeline?.users,
                conversations = response.conversationTimeline?.conversations,
            ).single()
    }

    override suspend fun sendMessage(
        roomKey: MicroBlogKey,
        message: String,
    ): UiDMItem {
        val response =
            service.getDMConversationTimeline(
                conversationId = roomKey.id,
                context = "FETCH_DM_CONVERSATION",
                maxId = "0",
            )
        val sendResponse =
            service.postDmNew2(
                PostDmNew2Request(
                    conversationId = roomKey.id,
                    requestId = Uuid.random().toString(),
                    text = message,
                ),
            )
        return XQT
            .messages(
                accountKey = accountKey,
                propertyEntries = sendResponse.propertyEntries,
                users = sendResponse.users,
                conversations = response.conversationTimeline?.conversations,
            ).single()
    }

    override suspend fun deleteMessage(
        roomKey: MicroBlogKey,
        messageKey: MicroBlogKey,
    ) {
        service.postDMMessageDeleteMutation(
            messageId = messageKey.id,
            requestId = Uuid.random().toString(),
        )
    }

    override suspend fun fetchNewMessages(
        roomKey: MicroBlogKey,
        cursor: String?,
    ): DirectMessageDelta {
        val response = service.getDMConversationTimeline(conversationId = roomKey.id)
        service.postDMConversationMarkRead(
            conversationId = roomKey.id,
            conversationId2 = roomKey.id,
            lastReadEventId = response.conversationTimeline?.maxEntryId.orEmpty(),
        )
        return DirectMessageDelta(
            messages =
                XQT.messages(
                    accountKey = accountKey,
                    propertyEntries = response.conversationTimeline?.propertyEntries,
                    users = response.conversationTimeline?.users,
                    conversations = response.conversationTimeline?.conversations,
                ),
        )
    }

    override suspend fun leaveRoom(roomKey: MicroBlogKey) {
        service.postDMConversationDelete(conversationId = roomKey.id)
    }

    override fun createRoom(userKey: MicroBlogKey): Flow<UiState<UiDMRoom>> =
        flow {
            val accountIdLong =
                accountKey.id.toLongOrNull()
                    ?: throw Exception("Invalid account key")
            val userIdLong =
                userKey.id.toLongOrNull()
                    ?: throw Exception("Invalid user key")
            val roomId =
                listOf(
                    accountIdLong,
                    userIdLong,
                ).sortedBy { it }
                    .joinToString("-")
            tryRun {
                val response =
                    service.getDMConversationTimeline(
                        conversationId = roomId,
                    )
                if (response.conversationTimeline?.propertyEntries.isNullOrEmpty()) {
                    service
                        .postDMWelcomeMessagesAddToConversation(
                            requestId = Uuid.random().toString(),
                            body =
                                AddToConversationRequest(
                                    conversationId = roomId,
                                ),
                        )
                    service.getDMConversationTimeline(
                        conversationId = roomId,
                    )
                } else {
                    response
                }
            }.fold(
                onSuccess = { response ->
                    emit(
                        UiState.Success(
                            XQT
                                .rooms(
                                    accountKey = accountKey,
                                    propertyEntries = response.conversationTimeline?.propertyEntries,
                                    users = response.conversationTimeline?.users,
                                    conversations = response.conversationTimeline?.conversations,
                                ).single(),
                        ),
                    )
                },
                onFailure = {
                    emit(UiState.Error(it))
                },
            )
        }

    override suspend fun canSend(userKey: MicroBlogKey): Boolean =
        tryRun {
            val canDm =
                service
                    .getDMPermissions(userKey.id)
                    .body()
                    ?.permissions
                    ?.idKeys
                    ?.get(userKey.id)
                    ?.canDm == true
            if (!canDm) {
                throw Exception("Cannot send DM")
            }
        }.isSuccess

    override suspend fun loadBadgeCount(): Int = service.getBadgeCount().dmUnreadCount?.toInt() ?: 0
}

private fun UiDMRoom.withXqtMediaAuth(
    credential: UiAccount.XQT.Credential,
    host: String,
): UiDMRoom = copy(lastMessage = lastMessage?.withXqtMediaAuth(credential, host))

private fun UiDMItem.withXqtMediaAuth(
    credential: UiAccount.XQT.Credential,
    host: String,
): UiDMItem =
    copy(
        content =
            when (val message = content) {
                is UiDMItem.Message.Media -> UiDMItem.Message.Media(message.media.withXqtMediaAuth(credential, host))
                else -> message
            },
    )

private fun UiMedia.withXqtMediaAuth(
    credential: UiAccount.XQT.Credential,
    host: String,
): UiMedia {
    val headers =
        persistentMapOf(
            "Cookie" to credential.chocolate,
            "Referer" to "https://$host/",
        )
    return when (this) {
        is UiMedia.Audio -> copy(customHeaders = headers)
        is UiMedia.Gif -> copy(customHeaders = headers)
        is UiMedia.Image -> copy(customHeaders = headers)
        is UiMedia.Video -> copy(customHeaders = headers)
    }
}
