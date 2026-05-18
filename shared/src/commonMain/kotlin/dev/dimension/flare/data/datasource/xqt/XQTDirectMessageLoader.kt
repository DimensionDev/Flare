package dev.dimension.flare.data.datasource.xqt

import androidx.paging.PagingData
import androidx.paging.map
import dev.dimension.flare.common.CacheData
import dev.dimension.flare.common.MemCacheable
import dev.dimension.flare.data.database.cache.CacheDatabase
import dev.dimension.flare.data.database.cache.mapper.XQT
import dev.dimension.flare.data.database.cache.model.DbDirectMessageTimeline
import dev.dimension.flare.data.database.cache.model.DbMessageItem
import dev.dimension.flare.data.datasource.microblog.loader.DirectMessageLoader
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.data.network.xqt.model.AddToConversationRequest
import dev.dimension.flare.data.network.xqt.model.PostDmNew2Request
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.UiDMRoom
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiState
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.flow
import kotlin.uuid.Uuid

internal class XQTDirectMessageLoader(
    private val service: XQTService,
    private val accountKey: MicroBlogKey,
    private val credentialFlow: Flow<UiAccount.XQT.Credential>,
) : DirectMessageLoader {
    override val platformType: PlatformType = PlatformType.xQt

    private val dmNotificationMarkerKey: String
        get() = "dm_notificationBadgeCount_$accountKey"

    private val badgeCount by lazy {
        MemCacheable(
            key = dmNotificationMarkerKey,
            fetchSource = {
                service.getBadgeCount().dmUnreadCount?.toInt() ?: 0
            },
        )
    }

    override fun listRemoteMediator(
        database: CacheDatabase,
        accountKey: MicroBlogKey,
    ) = DMListRemoteMediator(
        service = service,
        accountKey = accountKey,
        database = database,
    )

    override fun conversationRemoteMediator(
        database: CacheDatabase,
        accountKey: MicroBlogKey,
        roomKey: MicroBlogKey,
    ) = DMConversationRemoteMediator(
        service = service,
        accountKey = accountKey,
        database = database,
        roomKey = roomKey,
        clearBadge = { _, _ -> badgeCount.refresh() },
    )

    override fun listFlow(
        source: Flow<PagingData<DbDirectMessageTimeline>>,
        scope: CoroutineScope,
    ): Flow<PagingData<UiDMRoom>> =
        source.combine(credentialFlow) { paging, credential ->
            paging.map {
                it.content
                    .copy(unreadCount = it.unreadCount)
                    .withXqtMediaAuth(credential, accountKey.host)
            }
        }

    override fun conversationFlow(
        source: Flow<PagingData<DbMessageItem>>,
        scope: CoroutineScope,
    ): Flow<PagingData<UiDMItem>> =
        source.combine(credentialFlow) { paging, credential ->
            paging.map {
                it.content.withXqtMediaAuth(credential, accountKey.host)
            }
        }

    override fun roomInfoFlow(source: Flow<DbDirectMessageTimeline?>): Flow<UiDMRoom> =
        source.combine(credentialFlow) { room, credential ->
            room?.content
                ?.copy(unreadCount = room.unreadCount)
                ?.withXqtMediaAuth(credential, accountKey.host)
        }.mapNotNull { it }

    override suspend fun fetchRoomInfo(
        database: CacheDatabase,
        accountKey: MicroBlogKey,
        roomKey: MicroBlogKey,
    ) {
        val response =
            service.getDMConversationTimeline(
                conversationId = roomKey.id,
                context = "FETCH_DM_CONVERSATION",
                maxId = "0",
            )
        XQT.saveDM(
            accountKey = accountKey,
            database = database,
            propertyEntries = response.conversationTimeline?.propertyEntries,
            users = response.conversationTimeline?.users,
            conversations = response.conversationTimeline?.conversations,
        )
    }

    override suspend fun sendMessage(
        database: CacheDatabase,
        accountKey: MicroBlogKey,
        roomKey: MicroBlogKey,
        message: String,
    ) {
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
        XQT.saveDM(
            accountKey = accountKey,
            database = database,
            propertyEntries = sendResponse.propertyEntries,
            users = sendResponse.users,
            conversations = response.conversationTimeline?.conversations,
        )
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
        database: CacheDatabase,
        accountKey: MicroBlogKey,
        roomKey: MicroBlogKey,
    ) {
        val response = service.getDMConversationTimeline(conversationId = roomKey.id)
        service.postDMConversationMarkRead(
            conversationId = roomKey.id,
            conversationId2 = roomKey.id,
            lastReadEventId = response.conversationTimeline?.maxEntryId.orEmpty(),
        )
        XQT.saveDM(
            accountKey = accountKey,
            database = database,
            propertyEntries = response.conversationTimeline?.propertyEntries,
            users = response.conversationTimeline?.users,
            conversations = response.conversationTimeline?.conversations,
        )
    }

    override suspend fun leaveRoom(roomKey: MicroBlogKey) {
        service.postDMConversationDelete(conversationId = roomKey.id)
    }

    override fun createRoom(
        database: CacheDatabase,
        accountKey: MicroBlogKey,
        userKey: MicroBlogKey,
    ): Flow<UiState<MicroBlogKey>> =
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
            }.onSuccess { response ->
                XQT.saveDM(
                    accountKey = accountKey,
                    database = database,
                    propertyEntries = response.conversationTimeline?.propertyEntries,
                    users = response.conversationTimeline?.users,
                    conversations = response.conversationTimeline?.conversations,
                )
            }.fold(
                onSuccess = {
                    emit(
                        UiState.Success(
                            MicroBlogKey(
                                id = roomId,
                                host = accountKey.host,
                            ),
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

    override fun badgeCount(
        database: CacheDatabase,
        accountKey: MicroBlogKey,
    ): CacheData<Int> = badgeCount
}

private fun UiDMRoom.withXqtMediaAuth(
    credential: UiAccount.XQT.Credential,
    host: String,
): UiDMRoom =
    copy(lastMessage = lastMessage?.withXqtMediaAuth(credential, host))

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
