package dev.dimension.flare.data.datasource.xqt

import dev.dimension.flare.data.datasource.microblog.loader.DirectMessageDelta
import dev.dimension.flare.data.datasource.microblog.loader.DirectMessageLoader
import dev.dimension.flare.data.datasource.microblog.loader.DirectMessagePinCodeStatus
import dev.dimension.flare.data.datasource.microblog.loader.DirectMessageRuntimeTransformer
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.data.network.xqt.XQTService
import dev.dimension.flare.data.network.xqt.xchat.XChatConversation
import dev.dimension.flare.data.network.xqt.xchat.XChatDecodedEvent
import dev.dimension.flare.data.network.xqt.xchat.XChatDecodedEventKind
import dev.dimension.flare.data.network.xqt.xchat.XChatLoadedIdentity
import dev.dimension.flare.data.network.xqt.xchat.XChatService
import dev.dimension.flare.data.network.xqt.xchat.recoverIdentityWithPin
import dev.dimension.flare.data.platform.XChatIdentityCredential
import dev.dimension.flare.data.platform.XQTCredential
import dev.dimension.flare.data.repository.tryRun
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.UiDMItem
import dev.dimension.flare.ui.model.UiDMRoom
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiState
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlin.time.Clock

internal class XQTDirectMessageLoader(
    private val service: XQTService,
    private val accountKey: MicroBlogKey,
    private val credentialFlow: Flow<XQTCredential>,
    private val updateCredential: suspend (XQTCredential) -> Unit,
) : DirectMessageLoader {
    override val platformType: PlatformType = PlatformType.xQt

    private var cachedIdentityCredential: XChatIdentityCredential? = null
    private var cachedIdentity: XChatLoadedIdentity? = null
    private var recoveredIdentityCredential: XChatIdentityCredential? = null
    private var messagePullVersion: Int? = null
    private val roomParticipants = mutableMapOf<String, List<String>>()
    private val roomUsers = mutableMapOf<String, Map<String, UiProfile>>()
    private val pinCodeOverride = MutableStateFlow<DirectMessagePinCodeStatus?>(null)

    override val runtimeTransformer: Flow<DirectMessageRuntimeTransformer> =
        credentialFlow.map { credential ->
            DirectMessageRuntimeTransformer(
                room = { it.withXqtMediaAuth(credential, accountKey.host) },
                item = { it.withXqtMediaAuth(credential, accountKey.host) },
            )
        }

    override val pinCodeStatus: Flow<DirectMessagePinCodeStatus> =
        combine(
            credentialFlow,
            pinCodeOverride,
        ) { credential, override ->
            when {
                !credential.requiresPinCode() -> DirectMessagePinCodeStatus.NotRequired
                override is DirectMessagePinCodeStatus.Verifying -> override
                override is DirectMessagePinCodeStatus.Error -> override
                else -> DirectMessagePinCodeStatus.Required
            }
        }

    override suspend fun submitPinCode(pinCode: String): DirectMessagePinCodeStatus {
        val credential = credentialFlow.first()
        val identity = credential.xchatIdentity
        if (identity != null && identity.pinBacked != true) {
            pinCodeOverride.value = DirectMessagePinCodeStatus.NotRequired
            return DirectMessagePinCodeStatus.NotRequired
        }
        if (pinCode.isBlank()) {
            return DirectMessagePinCodeStatus
                .Error("PIN code is required")
                .also { pinCodeOverride.value = it }
        }
        pinCodeOverride.value = DirectMessagePinCodeStatus.Verifying
        return runCatching {
            val recoveredIdentity =
                if (identity == null) {
                    service.xchat.recoverIdentityWithPin(
                        userId = accountKey.id,
                        pinCode = pinCode,
                    )
                } else {
                    service.xchat.recoverIdentityWithPin(identity, pinCode)
                }
            val loadedIdentity = service.xchat.loadIdentity(recoveredIdentity)
            updateCredential(credential.copy(xchatIdentity = recoveredIdentity))
            recoveredIdentityCredential = recoveredIdentity
            cachedIdentityCredential = recoveredIdentity
            cachedIdentity = loadedIdentity
            messagePullVersion = null
            roomParticipants.clear()
            roomUsers.clear()
            DirectMessagePinCodeStatus.Verified
        }.getOrElse { throwable ->
            DirectMessagePinCodeStatus
                .Error(throwable.message ?: throwable::class.simpleName)
        }.also {
            pinCodeOverride.value = it
        }
    }

    override suspend fun loadRooms(
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiDMRoom> {
        val identity = requireIdentity()
        val nextKey = (request as? PagingRequest.Append)?.nextKey
        if (request is PagingRequest.Append && nextKey == null) {
            return PagingResult(endOfPaginationReached = true)
        }
        val page =
            loadInitialPage(
                identity = identity,
                maxLocalSequenceId = nextKey,
            )
        return PagingResult(
            data = page.conversations.map { rememberConversation(it) },
            nextKey = page.nextKey,
        )
    }

    override suspend fun loadMessages(
        roomKey: MicroBlogKey,
        pageSize: Int,
        request: PagingRequest,
    ): PagingResult<UiDMItem> {
        val identity = requireIdentity()
        val nextKey = (request as? PagingRequest.Append)?.nextKey
        if (request is PagingRequest.Append && nextKey == null) {
            return PagingResult(endOfPaginationReached = true)
        }
        val page =
            service.xchat.conversationPage(
                conversationId = roomKey.id,
                identity = identity,
                before = nextKey,
            )
        val participantIds = participantIdsFor(roomKey.id, identity)
        val users = usersFor(roomKey.id, participantIds)
        val showSender = participantIds.size > 2
        if (request == PagingRequest.Refresh) {
            page.messages.maxSequenceId()?.let { sequenceId ->
                tryRun {
                    service.xchat.markRead(
                        conversationId = roomKey.id,
                        sequenceId = sequenceId,
                        identity = identity,
                        participantIds = participantIds,
                    )
                }
            }
        }
        return PagingResult(
            data =
                page.messages
                    .mapNotNull {
                        it.toUiDMItem(
                            accountKey = accountKey,
                            users = users,
                            showSender = showSender,
                        )
                    },
            nextKey = if (page.hasMore) page.nextKey else null,
        )
    }

    override suspend fun fetchRoomInfo(roomKey: MicroBlogKey): UiDMRoom {
        val identity = requireIdentity()
        val conversation =
            loadInitialPage(identity = identity)
                .conversations
                .firstOrNull { it.conversationId == roomKey.id }
        return conversation
            ?.let { rememberConversation(it) }
            ?: fallbackRoom(roomKey.id, participantIdsFor(roomKey.id, identity))
    }

    override suspend fun sendMessage(
        roomKey: MicroBlogKey,
        message: String,
    ): UiDMItem {
        val identity = requireIdentity()
        val participantIds = participantIdsFor(roomKey.id, identity)
        val sendResult =
            service.xchat.sendText(
                conversationId = roomKey.id,
                text = message,
                identity = identity,
                participantIds = participantIds,
            )
        val users = usersFor(roomKey.id, participantIds)
        return (
            sendResult.decodedEvent
                ?: XChatDecodedEvent(
                    sequenceId = sendResult.sequenceId,
                    messageId = sendResult.messageId,
                    senderId = accountKey.id,
                    conversationId = roomKey.id,
                    createdAtMillis = Clock.System.now().toEpochMilliseconds(),
                    kind = XChatDecodedEventKind.Message,
                    text = message,
                )
        ).toUiDMItem(
            accountKey = accountKey,
            users = users,
            showSender = participantIds.size > 2,
        ) ?: error("XChat send response did not contain a renderable message")
    }

    override suspend fun deleteMessage(
        roomKey: MicroBlogKey,
        messageKey: MicroBlogKey,
    ) {
        service.xchat.deleteMessages(
            conversationId = roomKey.id,
            sequenceIds = listOf(messageKey.id),
        )
    }

    override suspend fun fetchNewMessages(
        roomKey: MicroBlogKey,
        cursor: String?,
    ): DirectMessageDelta {
        val identity = requireIdentity()
        val page =
            service.xchat.conversationPage(
                conversationId = roomKey.id,
                identity = identity,
            )
        val participantIds = participantIdsFor(roomKey.id, identity)
        val users = usersFor(roomKey.id, participantIds)
        val newEvents = page.messages.filter { it.isAfter(cursor) }
        newEvents.maxSequenceId()?.let { sequenceId ->
            tryRun {
                service.xchat.markRead(
                    conversationId = roomKey.id,
                    sequenceId = sequenceId,
                    identity = identity,
                    participantIds = participantIds,
                )
            }
        }
        return DirectMessageDelta(
            messages =
                newEvents.mapNotNull {
                    it.toUiDMItem(
                        accountKey = accountKey,
                        users = users,
                        showSender = participantIds.size > 2,
                    )
                },
            deletedMessageKeys =
                newEvents
                    .filter { it.kind == XChatDecodedEventKind.Delete }
                    .flatMap { it.targetSequenceIds }
                    .map { MicroBlogKey(it, accountKey.host) },
        )
    }

    override suspend fun leaveRoom(roomKey: MicroBlogKey) {
        roomParticipants.remove(roomKey.id)
        roomUsers.remove(roomKey.id)
    }

    override fun createRoom(userKey: MicroBlogKey): Flow<UiState<UiDMRoom>> =
        flow {
            val roomId = XChatService.conversationId1on1(accountKey.id, userKey.id)
            val participantIds = listOf(accountKey.id, userKey.id)
            roomParticipants[roomId] = participantIds
            roomUsers[roomId] = usersFor(roomId, participantIds)
            emit(
                UiState.Success(
                    UiDMRoom(
                        key = MicroBlogKey(roomId, accountKey.host),
                        users = listOf(fallbackXChatDirectMessageUser(userKey)).toImmutableList(),
                        lastMessage = null,
                        unreadCount = 0,
                    ),
                ),
            )
        }

    override suspend fun canSend(userKey: MicroBlogKey): Boolean =
        tryRun {
            requireIdentity()
            val canMessage = service.xchat.canMessage(userKey.id)
            if (!canMessage) {
                throw Exception("Cannot send XChat message")
            }
        }.isSuccess

    override suspend fun loadBadgeCount(): Int =
        tryRun {
            loadInitialPage(identity = requireIdentity())
                .conversations
                .sumOf { it.unreadCount }
                .toInt()
        }.getOrDefault(0)

    private suspend fun requireIdentity(): XChatLoadedIdentity {
        val credential = credentialFlow.first()
        val identityCredential =
            credential.xchatIdentity
                ?: recoveredIdentityCredential
                    ?.takeIf { it.userId == accountKey.id && it.hasPrivateKeys }
                ?: throw IllegalStateException("XChat PIN code is required")
        val effectiveIdentityCredential =
            if (identityCredential.requiresRecoveredPrivateKey()) {
                recoveredIdentityCredential
                    ?.takeIf { it.pinLockKey == identityCredential.pinLockKey && it.hasPrivateKeys }
                    ?: throw IllegalStateException("XChat PIN code is required")
            } else {
                identityCredential
            }
        if (effectiveIdentityCredential != cachedIdentityCredential) {
            cachedIdentityCredential = effectiveIdentityCredential
            cachedIdentity = service.xchat.loadIdentity(effectiveIdentityCredential)
            messagePullVersion = null
            roomParticipants.clear()
            roomUsers.clear()
        }
        return cachedIdentity ?: error("XChat identity cache was not initialized")
    }

    private fun rememberConversation(conversation: XChatConversation): UiDMRoom {
        val room = conversation.toUiDMRoom(accountKey)
        val participantIds = conversation.participantIds(accountKey.id)
        roomParticipants[conversation.conversationId] = participantIds
        roomUsers[conversation.conversationId] =
            (
                room.users +
                    participantIds.map {
                        fallbackXChatDirectMessageUser(MicroBlogKey(it, accountKey.host))
                    }
            ).distinctBy { it.key.id }
                .associateBy { it.key.id }
        return room
    }

    private suspend fun participantIdsFor(
        conversationId: String,
        identity: XChatLoadedIdentity,
    ): List<String> =
        roomParticipants[conversationId]
            ?: conversationId
                .split(":")
                .takeIf { it.size == 2 }
            ?: loadInitialPage(identity = identity)
                .conversations
                .firstOrNull { it.conversationId == conversationId }
                ?.let { conversation ->
                    rememberConversation(conversation)
                    conversation.participantIds(accountKey.id)
                }
            ?: throw IllegalStateException("XChat conversation participants are not available")

    private fun usersFor(
        conversationId: String,
        participantIds: List<String>,
    ): Map<String, UiProfile> =
        roomUsers[conversationId]
            ?: participantIds
                .associateWith { fallbackXChatDirectMessageUser(MicroBlogKey(it, accountKey.host)) }
                .also { roomUsers[conversationId] = it }

    private suspend fun loadInitialPage(
        identity: XChatLoadedIdentity,
        maxLocalSequenceId: String? = null,
    ) = service
        .xchat
        .initialPage(
            identity = identity,
            maxLocalSequenceId = maxLocalSequenceId,
            messagePullVersion = messagePullVersion,
        ).also { page ->
            messagePullVersion = page.messagePullVersion ?: messagePullVersion
        }

    private fun fallbackRoom(
        conversationId: String,
        participantIds: List<String>,
    ): UiDMRoom =
        UiDMRoom(
            key = MicroBlogKey(conversationId, accountKey.host),
            users =
                participantIds
                    .filterNot { it == accountKey.id }
                    .map { fallbackXChatDirectMessageUser(MicroBlogKey(it, accountKey.host)) }
                    .toImmutableList(),
            lastMessage = null,
            unreadCount = 0,
        )

    private fun XQTCredential.requiresPinCode(): Boolean {
        val identity =
            xchatIdentity
                ?: return recoveredIdentityCredential
                    ?.takeIf { it.userId == accountKey.id && it.hasPrivateKeys } == null
        return identity.requiresRecoveredPrivateKey() &&
            recoveredIdentityCredential?.pinLockKey != identity.pinLockKey
    }
}

private fun List<XChatDecodedEvent>.maxSequenceId(): String? =
    mapNotNull { it.sequenceId?.toULongOrNull() }
        .maxOrNull()
        ?.toString()

private fun XChatDecodedEvent.isAfter(cursor: String?): Boolean {
    val sequenceId = sequenceId ?: return cursor == null
    val current = sequenceId.toULongOrNull()
    val previous = cursor?.toULongOrNull()
    return when {
        cursor == null -> true
        current != null && previous != null -> current > previous
        else -> sequenceId > cursor
    }
}

private val XChatIdentityCredential.pinLockKey: String
    get() = "$userId:$version"

private val XChatIdentityCredential.hasPrivateKeys: Boolean
    get() = identityPrivateJwk != null && signingPrivateJwk != null

private fun XChatIdentityCredential.requiresRecoveredPrivateKey(): Boolean = pinBacked == true && !hasPrivateKeys

private fun UiDMRoom.withXqtMediaAuth(
    credential: XQTCredential,
    host: String,
): UiDMRoom = copy(lastMessage = lastMessage?.withXqtMediaAuth(credential, host))

private fun UiDMItem.withXqtMediaAuth(
    credential: XQTCredential,
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
    credential: XQTCredential,
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
