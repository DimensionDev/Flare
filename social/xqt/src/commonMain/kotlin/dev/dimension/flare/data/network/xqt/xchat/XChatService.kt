package dev.dimension.flare.data.network.xqt.xchat

import dev.dimension.flare.data.network.xqt.emusks.EmusksApiException
import dev.dimension.flare.data.network.xqt.emusks.EmusksRawClient
import dev.dimension.flare.data.platform.XChatIdentityCredential
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.uuid.Uuid

internal class XChatService(
    private val rawClient: EmusksRawClient,
) {
    suspend fun loadIdentity(identity: XChatIdentityCredential): XChatLoadedIdentity = XChatCrypto.loadIdentity(identity)

    suspend fun publicKeys(
        userIds: List<String>,
        includeJuiceboxTokens: Boolean = false,
    ): List<XChatUserPublicKeys> {
        if (userIds.isEmpty()) return emptyList()
        val response =
            gql(
                name = "GetPublicKeys",
                variables =
                    buildJsonObject {
                        put(
                            "ids",
                            buildJsonArray {
                                userIds.forEach { add(JsonPrimitive(it)) }
                            },
                        )
                        put("include_juicebox_tokens", includeJuiceboxTokens)
                    },
            )
        return response
            .obj("data")
            ?.array("user_results_by_rest_ids")
            .orEmpty()
                .mapNotNull { it.asObjectOrNull()?.toUserPublicKeys() }
    }

    suspend fun profile(userId: String): XChatUserPublicKeys? = publicKeys(listOf(userId)).firstOrNull()

    suspend fun publicKey(userId: String): XChatPublicKey? = profile(userId)?.keys?.firstOrNull()

    suspend fun permissions(userId: String): XChatPermissions? = profile(userId)?.permissions

    suspend fun isOnXChat(userId: String): Boolean = profile(userId)?.onXChat == true

    suspend fun canMessage(userId: String): Boolean =
        permissions(userId)?.canDmOnXChat == true

    suspend fun fingerprint(publicKeyB64: String): String =
        XChatCrypto
            .sha256(XChatBase64.decode(publicKeyB64))
            .joinToString("") { byte ->
                (byte.toInt() and 0xff).toString(radix = 16).padStart(2, '0')
            }
            .chunked(4)
            .joinToString(":")

    suspend fun token(): String? =
        gql(name = "GenerateXChatTokenMutation")
            .obj("data")
            ?.obj("user_get_x_chat_auth_token")
            ?.string("token")

    suspend fun callPermissions(userIds: List<String>): List<XChatCallPermission> {
        if (userIds.isEmpty()) return emptyList()
        val response =
            gql(
                name = "DmAvPermissionsQuery",
                variables =
                    buildJsonObject {
                        put(
                            "recipient_ids",
                            buildJsonArray {
                                userIds.forEach { add(JsonPrimitive(it)) }
                            },
                        )
                    },
            )
        return response
            .obj("data")
            ?.obj("get_av_permissions")
            ?.array("result")
            .orEmpty()
            .mapNotNull { it.asObjectOrNull()?.toCallPermission() }
    }

    suspend fun initialPage(
        identity: XChatLoadedIdentity?,
        maxLocalSequenceId: String? = null,
        messagePullVersion: Int? = null,
        viewerId: String? = identity?.userId,
    ): XChatInboxPage {
        val response =
            gql(
                name = "GetInitialXChatPageQuery",
                variables =
                    buildJsonObject {
                        put("max_local_sequence_id", maxLocalSequenceId?.let(::JsonPrimitive) ?: JsonNull)
                        put("query_settings", initialPageQuerySettings())
                        put(
                            "message_pull_version",
                            JsonPrimitive(messagePullVersion ?: DEFAULT_INITIAL_MESSAGE_PULL_VERSION),
                        )
                    },
            )
        val page = response.obj("data")?.obj("get_initial_chat_page") ?: JsonObject(emptyMap())
        val conversations =
            page
                .array("items")
                .orEmpty()
                .mapNotNull { item ->
                    item
                        .asObjectOrNull()
                        ?.toConversation(identity, viewerId)
                }
        val cursor = page.obj("cursor") ?: page.obj("inboxCursor")
        val nextKey =
            cursor?.string("max_local_sequence_id")
                ?: cursor?.string("cursor_id")
        return XChatInboxPage(
            conversations = conversations,
            nextKey = nextKey,
            hasMore = nextKey != null || cursor?.boolean("inbox_exhausted") == false,
            messageRequestsCount = page.int("message_requests_count"),
            messagePullVersion = page.int("message_pull_version"),
            maxUserSequenceId = page.string("max_user_sequence_id"),
        )
    }

    suspend fun conversationPage(
        conversationId: String,
        identity: XChatLoadedIdentity,
        before: String? = null,
    ): XChatConversationPage {
        val response =
            gql(
                name = "GetConversationPageQuery",
                variables =
                    buildJsonObject {
                        put("conversation_id", conversationId)
                        put("min_local_sequence_id", before ?: MAX_SEQUENCE_ID)
                        put("min_conversation_key_version", MAX_SEQUENCE_ID)
                        put("query_settings", JsonNull)
                    },
            )
        val page = response.obj("data")?.obj("get_conversation_page") ?: JsonObject(emptyMap())
        val cKeyMap =
            buildConversationKeyMap(
                keyChangeB64s = page.array("missing_conversation_key_change_events").asStringList(),
                identity = identity,
            )
        identity.rememberConversationKeys(conversationId, cKeyMap)
        identity.conversationKeys[conversationId]?.let { cached ->
            if (cKeyMap[cached.version] == null) {
                cKeyMap[cached.version] = cached.key
            }
        }
        val messages =
            page
                .array("encoded_message_events")
                .asStringList()
                .mapNotNull { decodeMessageEvent(it, cKeyMap) }
        return XChatConversationPage(
            messages = messages,
            hasMore = page.boolean("has_more") == true,
            nextKey = messages.minSequenceId(),
        )
    }

    suspend fun sendText(
        conversationId: String,
        text: String,
        identity: XChatLoadedIdentity,
        participantIds: List<String>,
        ttlMsec: Long? = null,
    ): XChatSendResult =
        sendEntry(
            conversationId = conversationId,
            holderBytes = XChatThrift.textHolder(text),
            identity = identity,
            participantIds = participantIds,
            ttlMsec = ttlMsec,
        )

    suspend fun react(
        conversationId: String,
        sequenceId: String,
        emoji: String,
        identity: XChatLoadedIdentity,
        participantIds: List<String>,
        attachmentId: String? = null,
    ): XChatSendResult =
        sendEntry(
            conversationId = conversationId,
            holderBytes =
                XChatThrift.reactionAddHolder(
                    sequenceId = sequenceId,
                    emoji = emoji,
                    attachmentId = attachmentId,
                ),
            identity = identity,
            participantIds = participantIds,
        )

    suspend fun unreact(
        conversationId: String,
        sequenceId: String,
        emoji: String,
        identity: XChatLoadedIdentity,
        participantIds: List<String>,
        attachmentId: String? = null,
    ): XChatSendResult =
        sendEntry(
            conversationId = conversationId,
            holderBytes =
                XChatThrift.reactionRemoveHolder(
                    sequenceId = sequenceId,
                    emoji = emoji,
                    attachmentId = attachmentId,
                ),
            identity = identity,
            participantIds = participantIds,
        )

    suspend fun edit(
        conversationId: String,
        sequenceId: String,
        text: String,
        identity: XChatLoadedIdentity,
        participantIds: List<String>,
    ): XChatSendResult =
        sendEntry(
            conversationId = conversationId,
            holderBytes =
                XChatThrift.editHolder(
                    sequenceId = sequenceId,
                    updatedText = text,
                ),
            identity = identity,
            participantIds = participantIds,
        )

    suspend fun markRead(
        conversationId: String,
        sequenceId: String,
        identity: XChatLoadedIdentity,
        participantIds: List<String>,
    ): XChatSendResult =
        sendEntry(
            conversationId = conversationId,
            holderBytes =
                XChatThrift.markReadHolder(
                    sequenceId = sequenceId,
                    seenAtMillis = Clock.System.now().toEpochMilliseconds(),
                ),
            identity = identity,
            participantIds = participantIds,
        )

    suspend fun markUnread(
        conversationId: String,
        sequenceId: String,
        identity: XChatLoadedIdentity,
        participantIds: List<String>,
    ): XChatSendResult =
        sendEntry(
            conversationId = conversationId,
            holderBytes = XChatThrift.markUnreadHolder(sequenceId),
            identity = identity,
            participantIds = participantIds,
        )

    suspend fun pinConversation(
        conversationId: String,
        identity: XChatLoadedIdentity,
        participantIds: List<String>,
    ): XChatSendResult =
        sendEntry(
            conversationId = conversationId,
            holderBytes = XChatThrift.pinHolder(conversationId),
            identity = identity,
            participantIds = participantIds,
        )

    suspend fun unpinConversation(
        conversationId: String,
        identity: XChatLoadedIdentity,
        participantIds: List<String>,
    ): XChatSendResult =
        sendEntry(
            conversationId = conversationId,
            holderBytes = XChatThrift.unpinHolder(conversationId),
            identity = identity,
            participantIds = participantIds,
        )

    suspend fun setNickname(
        conversationId: String,
        targetUserId: String,
        nickname: String,
        identity: XChatLoadedIdentity,
        participantIds: List<String>,
    ): XChatSendResult =
        sendEntry(
            conversationId = conversationId,
            holderBytes =
                XChatThrift.nicknameHolder(
                    userId = targetUserId,
                    nickname = nickname,
                ),
            identity = identity,
            participantIds = participantIds,
        )

    suspend fun reportScreenCapture(
        conversationId: String,
        identity: XChatLoadedIdentity,
        participantIds: List<String>,
        recording: Boolean = false,
    ): XChatSendResult =
        sendEntry(
            conversationId = conversationId,
            holderBytes =
                XChatThrift.screenCaptureHolder(
                    if (recording) ScreenCaptureType.Recording else ScreenCaptureType.Screenshot,
                ),
            identity = identity,
            participantIds = participantIds,
        )

    suspend fun deleteMessages(
        conversationId: String,
        sequenceIds: List<String>,
        forEveryone: Boolean = false,
        identity: XChatLoadedIdentity? = null,
    ): JsonObject {
        if (sequenceIds.isEmpty()) return JsonObject(emptyMap())
        val actionSignatures =
            if (forEveryone) {
                val loadedIdentity = identity ?: error("XChat identity is required to delete for everyone")
                val conversationToken = conversationToken(conversationId, loadedIdentity)
                val signature =
                    XChatCrypto.actionSignature(
                        identity = loadedIdentity,
                        typeName = "MessageDeleteEvent",
                        conversationToken = conversationToken,
                        conversationId = conversationId,
                        dataElements = listOf("2") + sequenceIds,
                        eventDetailBytes =
                            XChatThrift.deleteEventDetail(
                                sequenceIds = sequenceIds,
                                actionValue = 2,
                            ),
                    )
                buildJsonArray {
                    add(signature)
                }
            } else {
                JsonNull
            }
        val response =
            gql(
                name = "DeleteMessageMutation",
                variables =
                    buildJsonObject {
                        put("conversation_id", conversationId)
                        put(
                            "sequence_ids",
                            buildJsonArray {
                                sequenceIds.forEach { add(JsonPrimitive(it)) }
                            },
                        )
                        put("delete_message_action", if (forEveryone) "DeleteForAll" else "DeleteForSelf")
                        put("action_signatures", actionSignatures)
                    },
            )
        return response.obj("data")?.obj("xchat_delete_messages") ?: JsonObject(emptyMap())
    }

    suspend fun gql(
        name: String,
        variables: JsonObject = JsonObject(emptyMap()),
    ): JsonObject {
        val operation = XChatOperations[name] ?: throw EmusksApiException("xchat operation $name not found")
        return rawClient.apolloGraphqlJson(
            operationId = operation.id,
            operationName = name,
            query = operation.document,
            variables = variables,
        )
    }

    private suspend fun sendEntry(
        conversationId: String,
        holderBytes: ByteArray,
        identity: XChatLoadedIdentity,
        participantIds: List<String>,
        shouldNotify: Boolean = true,
        ttlMsec: Long? = null,
    ): XChatSendResult {
        val conversationKey = ensureConversationKey(conversationId, identity, participantIds)
        val conversationToken = conversationToken(conversationId, identity)
        val frame = XChatCrypto.encryptBody(holderBytes, conversationKey.key)
        val messageCreateEvent =
            XChatThrift.messageCreateEvent(
                frame = frame,
                conversationKeyVersion = conversationKey.version,
                shouldNotify = shouldNotify,
                ttlMsec = ttlMsec,
            )
        val signature =
            XChatCrypto.eventSignature(
                identity = identity,
                conversationToken = conversationToken,
                conversationId = conversationId,
                conversationKeyVersion = conversationKey.version,
                frame = frame,
            )
        val messageId = Uuid.random().toString()
        val response =
            gql(
                name = "SendMessageCreateMutation",
                variables =
                    buildJsonObject {
                        put("conversation_id", conversationId)
                        put("message_id", messageId)
                        put("conversation_token", conversationToken.takeIf { it.isNotEmpty() }?.let(::JsonPrimitive) ?: JsonNull)
                        put("encoded_message_create_event", XChatBase64.encode(messageCreateEvent))
                        put("encoded_message_event_signature", signature)
                    },
            )
        val encodedEvent =
            response
                .obj("data")
                ?.obj("xchat_send_create_message_event")
                ?.string("encoded_message_event")
        val decodedEvent =
            encodedEvent?.let {
                decodeMessageEvent(
                    eventB64 = it,
                    cKeyMap = mapOf(conversationKey.version to conversationKey.key),
                )
            }
        return XChatSendResult(
            conversationId = conversationId,
            messageId = messageId,
            sequenceId = XChatThrift.readLeadingSequenceId(encodedEvent),
            encodedMessageEvent = encodedEvent,
            decodedEvent = decodedEvent,
        )
    }

    private suspend fun ensureConversationKey(
        conversationId: String,
        identity: XChatLoadedIdentity,
        participantIds: List<String>,
    ): XChatConversationKey {
        identity.conversationKeys[conversationId]?.let { return it }
        val distinctParticipantIds = (participantIds + identity.userId).distinct()
        val recipientKeys =
            publicKeys(distinctParticipantIds.filterNot { it == identity.userId })
                .mapNotNull { user ->
                    val key = user.keys.firstOrNull { it.publicKey != null && it.version != null } ?: return@mapNotNull null
                    XChatRecipientKey(
                        userId = user.userId ?: return@mapNotNull null,
                        publicKeySpki = XChatBase64.decode(key.publicKey ?: return@mapNotNull null),
                        version = key.version ?: return@mapNotNull null,
                    )
                }
        val missingRecipients = distinctParticipantIds.filterNot { id -> id == identity.userId || recipientKeys.any { it.userId == id } }
        if (missingRecipients.isNotEmpty()) {
            throw EmusksApiException("xchat public key not found for ${missingRecipients.joinToString()}")
        }
        val conversationKeyBytes = XChatCrypto.secureRandomBytes(32)
        val version = Clock.System.now().toEpochMilliseconds().toString()
        val participantKeys =
            listOf(
                XChatRecipientKey(
                    userId = identity.userId,
                    publicKeySpki = identity.publicKeySpki,
                    version = identity.version,
                ),
            ) + recipientKeys
        gql(
            name = "AddEncryptedConversationKeysMutation",
            variables =
                buildJsonObject {
                    put("conversation_id", conversationId)
                    put("conversation_key_version", version)
                    put(
                        "conversation_participant_keys",
                        buildJsonArray {
                            participantKeys.forEach { recipient ->
                                add(
                                    buildJsonObject {
                                        put("user_id", recipient.userId)
                                        put(
                                            "encrypted_conversation_key",
                                            XChatCrypto.eciesWrap(conversationKeyBytes, recipient.publicKeySpki),
                                        )
                                        put("public_key_version", recipient.version)
                                    },
                                )
                            }
                        },
                    )
                },
        )
        return XChatConversationKey(
            key = conversationKeyBytes,
            version = version,
        ).also {
            identity.conversationKeys[conversationId] = it
        }
    }

    private suspend fun conversationToken(
        conversationId: String,
        identity: XChatLoadedIdentity,
    ): String {
        identity.conversationTokens[conversationId]?.let { return it }
        val token =
            runCatching {
                val page = conversationPage(conversationId = conversationId, identity = identity)
                page.messages
                    .firstNotNullOfOrNull { it.conversationToken }
                    .orEmpty()
            }.getOrDefault("")
        identity.conversationTokens[conversationId] = token
        return token
    }

    private suspend fun JsonObject.toConversation(
        identity: XChatLoadedIdentity?,
        viewerId: String?,
    ): XChatConversation? {
        val detail = obj("conversation_detail") ?: return null
        val conversationId = detail.string("conversation_id") ?: return null
        val isGroup = detail.string("__typename") == "XChatGroupConversationDetail"
        val participantResults =
            if (isGroup) {
                (
                    detail.array("participants_results").orEmpty() +
                        detail.array("group_members_results").orEmpty()
                ).distinctBy { it.asObjectOrNull()?.string("rest_id") }
            } else {
                detail.array("participants_results").orEmpty()
            }
        val parsedParticipants =
            participantResults
                .mapNotNull { it.asObjectOrNull()?.toXChatUser() }
        val participants =
            if (isGroup || viewerId == null) {
                parsedParticipants
            } else {
                val parsedIds = parsedParticipants.map { it.userId }.toSet()
                parsedParticipants +
                    conversationId
                        .split(":")
                        .filter { it.isNotBlank() && it != viewerId && it !in parsedIds }
                        .map { XChatUser(userId = it, name = null, screenName = null, avatarUrl = null) }
            }
        if (!isGroup && viewerId != null && conversationId.isSelfConversation(viewerId, participants)) {
            return null
        }
        if (boolean("is_deleted_by_viewer") == true) {
            return null
        }
        val cKeyMap =
            if (identity != null) {
                buildConversationKeyMap(
                    keyChangeB64s = array("latest_conversation_key_change_events").asStringList(),
                    identity = identity,
                )
            } else {
                emptyMap()
            }
        identity?.rememberConversationKeys(conversationId, cKeyMap)
        val latestEvents =
            (
                listOfNotNull(string("latest_notifiable_message_create_event")) +
                    listOfNotNull(string("latest_non_notifiable_message_create_event")) +
                    array("latest_message_events").asStringList()
            ).distinct()
                .mapNotNull { decodeMessageEvent(it, cKeyMap) }
        if (latestEvents.latestTimelineEvent()?.kind == XChatDecodedEventKind.ConversationDelete) {
            return null
        }
        val latestMessage = latestEvents.latestDisplayableMessage()
        return XChatConversation(
            conversationId = conversationId,
            type = if (isGroup) XChatConversationType.Group else XChatConversationType.Direct,
            isMuted = detail.boolean("is_muted"),
            groupName = detail.obj("group_metadata")?.string("group_name"),
            groupAvatarUrl = detail.obj("group_metadata")?.string("group_avatar_url"),
            participants = participants,
            latestSequenceId = string("latest_message_sequence_id"),
            latestMessage = latestMessage,
            unreadCount = unreadCount(viewerId, latestMessage),
        )
    }

    private fun JsonObject.unreadCount(
        viewerId: String?,
        latestMessage: XChatDecodedEvent?,
    ): Long {
        val message = latestMessage ?: return 0
        if (viewerId == null || message.senderId == viewerId) return 0
        val latestMessageIsDisplayable = message.kind == XChatDecodedEventKind.Message
        val latestSequenceId = string("latest_message_sequence_id")?.toULongOrNull() ?: return 0
        val lastReadSequenceId =
            array("latest_read_events_per_participant")
                .orEmpty()
                .firstNotNullOfOrNull { entry ->
                    val item = entry.asObjectOrNull() ?: return@firstNotNullOfOrNull null
                    val participantId = item.obj("participant_id_results")?.string("rest_id")
                    if (participantId != viewerId) return@firstNotNullOfOrNull null
                    XChatThrift
                        .readLeadingSequenceId(item.string("latest_mark_conversation_read_event"))
                        ?.toULongOrNull()
                } ?: return if (latestMessageIsDisplayable) 1 else 0
        return if (latestSequenceId > lastReadSequenceId && latestMessageIsDisplayable) 1 else 0
    }

    private suspend fun buildConversationKeyMap(
        keyChangeB64s: List<String>,
        identity: XChatLoadedIdentity,
    ): MutableMap<String, ByteArray> {
        val map = mutableMapOf<String, ByteArray>()
        keyChangeB64s.forEach { keyChangeB64 ->
            runCatching {
                val keyChangeEvent =
                    XChatThrift
                        .parse(XChatBase64.decode(keyChangeB64))
                        .struct(7)
                        ?.struct(3)
                        ?: return@runCatching
                val version = keyChangeEvent.string(1) ?: return@runCatching
                keyChangeEvent
                    .list(2)
                    .orEmpty()
                    .mapNotNull { it.asStruct() }
                    .firstOrNull { it.string(1) == identity.userId }
                    ?.string(2)
                    ?.let { wrappedKey ->
                        map[version] = XChatCrypto.eciesUnwrap(wrappedKey, identity.identityPrivateKey)
                    }
            }
        }
        return map
    }

    private fun XChatLoadedIdentity.rememberConversationKeys(
        conversationId: String,
        keys: Map<String, ByteArray>,
    ) {
        val latest =
            keys.maxWithOrNull(
                compareBy<Map.Entry<String, ByteArray>> {
                    it.key.toULongOrNull() ?: ULong.MIN_VALUE
                }.thenBy { it.key },
            ) ?: return
        conversationKeys[conversationId] =
            XChatConversationKey(
                key = latest.value,
                version = latest.key,
            )
    }

    internal fun decodeMessageEvent(
        eventB64: String,
        cKeyMap: Map<String, ByteArray>,
    ): XChatDecodedEvent? =
        runCatching {
            val event = XChatThrift.parse(XChatBase64.decode(eventB64))
            val detail = event.struct(7).orEmpty()
            val conversationToken = event.string(5)
            val base =
                XChatDecodedEvent(
                    sequenceId = event.string(1),
                    messageId = event.string(2),
                    senderId = event.string(3),
                    conversationId = event.string(4),
                    conversationToken = conversationToken,
                    createdAtMillis = event.string(6)?.toLongOrNull() ?: event.long(6),
                    kind = detail.kind(),
                )
            val messageCreateEvent = detail.struct(1) ?: return@runCatching base.withDeleteTargets(detail)
            val frame = messageCreateEvent.binary(100)
            val conversationKeyVersion = messageCreateEvent.string(101)
            if (frame == null) {
                return@runCatching base.copy(
                    kind = XChatDecodedEventKind.Message,
                    conversationKeyVersion = conversationKeyVersion,
                    encrypted = conversationKeyVersion != null,
                )
            }
            val plaintext =
                if (conversationKeyVersion == null) {
                    frame
                } else {
                    val conversationKey =
                        cKeyMap[conversationKeyVersion]
                            ?: return@runCatching base.copy(
                                kind = XChatDecodedEventKind.Message,
                                conversationKeyVersion = conversationKeyVersion,
                                encrypted = true,
                            )
                    XChatCrypto.decryptBody(
                        frame = frame,
                        conversationKey = conversationKey,
                    ) ?: return@runCatching base.copy(
                        kind = XChatDecodedEventKind.Message,
                        conversationKeyVersion = conversationKeyVersion,
                        decryptError = true,
                    )
                }
            base
                .copy(conversationKeyVersion = conversationKeyVersion)
                .withEntry(XChatThrift.parse(plaintext).struct(1).orEmpty())
        }.getOrNull()

    private fun XChatDecodedEvent.withDeleteTargets(detail: XChatThriftStruct): XChatDecodedEvent {
        val deleteEvent = detail.struct(7) ?: return this
        return copy(
            targetSequenceIds =
                deleteEvent
                    .list(1)
                    .orEmpty()
                    .mapNotNull { (it as? XChatThriftValue.Binary)?.bytes?.decodeToString() },
        )
    }

    private fun XChatDecodedEvent.withEntry(entry: XChatThriftStruct): XChatDecodedEvent =
        when {
            entry.struct(1) != null ->
                copy(
                    kind = XChatDecodedEventKind.Message,
                    text = entry.struct(1)?.string(1),
                )
            entry.struct(2) != null ->
                copy(
                    kind = XChatDecodedEventKind.ReactionAdd,
                    targetSequenceIds = listOfNotNull(entry.struct(2)?.string(1)),
                    emoji = entry.struct(2)?.string(2),
                )
            entry.struct(3) != null ->
                copy(
                    kind = XChatDecodedEventKind.ReactionRemove,
                    targetSequenceIds = listOfNotNull(entry.struct(3)?.string(1)),
                    emoji = entry.struct(3)?.string(2),
                )
            entry.struct(4) != null ->
                copy(
                    kind = XChatDecodedEventKind.Edit,
                    targetSequenceIds = listOfNotNull(entry.struct(4)?.string(1)),
                    text = entry.struct(4)?.string(2),
                )
            entry.struct(5) != null ->
                copy(
                    kind = XChatDecodedEventKind.Read,
                    targetSequenceIds = listOfNotNull(entry.struct(5)?.string(1)),
                )
            entry.struct(6) != null ->
                copy(
                    kind = XChatDecodedEventKind.Unread,
                    targetSequenceIds = listOfNotNull(entry.struct(6)?.string(1)),
                )
            entry.struct(10) != null ->
                copy(
                    kind = XChatDecodedEventKind.AvCallEnded,
                    targetSequenceIds = emptyList(),
                )
            entry.struct(11) != null ->
                copy(kind = XChatDecodedEventKind.AvCallMissed)
            entry.struct(16) != null ->
                copy(kind = XChatDecodedEventKind.AvCallStarted)
            else -> this
        }

    private fun XChatThriftStruct.kind(): XChatDecodedEventKind =
        when {
            struct(1) != null -> XChatDecodedEventKind.Message
            struct(3) != null -> XChatDecodedEventKind.ConversationKeyChange
            struct(6) != null -> XChatDecodedEventKind.Typing
            struct(7) != null -> XChatDecodedEventKind.Delete
            struct(8) != null -> XChatDecodedEventKind.ConversationDelete
            struct(9) != null -> XChatDecodedEventKind.MetadataChange
            struct(12) != null -> XChatDecodedEventKind.Read
            struct(13) != null -> XChatDecodedEventKind.Unread
            else -> XChatDecodedEventKind.Unknown
        }

    private fun List<XChatDecodedEvent>.latestDisplayableMessage(): XChatDecodedEvent? =
        filter { it.hasRenderableContent() }
            .latestTimelineEvent()
            ?: filter { it.isEncryptedPlaceholder() }
                .latestTimelineEvent()

    private fun List<XChatDecodedEvent>.latestTimelineEvent(): XChatDecodedEvent? =
        maxWithOrNull(
            compareBy<XChatDecodedEvent> {
                it.sequenceId?.toULongOrNull() ?: ULong.MIN_VALUE
            }.thenBy {
                it.createdAtMillis ?: Long.MIN_VALUE
            },
        )

    private fun XChatDecodedEvent.hasRenderableContent(): Boolean =
        when (kind) {
            XChatDecodedEventKind.Message,
            XChatDecodedEventKind.Edit,
            -> !text.isNullOrBlank()
            XChatDecodedEventKind.Delete -> true
            else -> false
        }

    private fun XChatDecodedEvent.isEncryptedPlaceholder(): Boolean =
        when (kind) {
            XChatDecodedEventKind.Message,
            XChatDecodedEventKind.Edit,
            -> text.isNullOrBlank() && (encrypted || decryptError)
            else -> false
        }

    private fun String.isSelfConversation(
        viewerId: String,
        participants: List<XChatUser>,
    ): Boolean {
        val ids = split(":").filter { it.isNotBlank() } + participants.map { it.userId }
        return ids.isNotEmpty() && ids.all { it == viewerId }
    }

    private fun initialPageQuerySettings(): JsonObject =
        buildJsonObject {
            put("conversation_event_limit", 200)
            put("inbox_conversation_event_limit", 20)
            put("inbox_conversation_limit", 20)
            put("user_event_limit", 500)
        }

    companion object {
        const val MAX_SEQUENCE_ID: String = "9223372036854775807"
        const val DEFAULT_INITIAL_MESSAGE_PULL_VERSION: Int = 1761251295

        fun conversationId1on1(
            a: String,
            b: String,
        ): String {
            val first = a.toULongOrNull()
            val second = b.toULongOrNull()
            val values =
                if (first != null && second != null) {
                    listOf(first to a, second to b).sortedBy { it.first }.map { it.second }
                } else {
                    listOf(a, b).sorted()
                }
            return values.joinToString(":")
        }
    }
}

internal data class XChatInboxPage(
    val conversations: List<XChatConversation>,
    val nextKey: String?,
    val hasMore: Boolean,
    val messageRequestsCount: Int?,
    val messagePullVersion: Int?,
    val maxUserSequenceId: String?,
)

internal data class XChatConversationPage(
    val messages: List<XChatDecodedEvent>,
    val hasMore: Boolean,
    val nextKey: String?,
)

internal data class XChatSendResult(
    val conversationId: String,
    val messageId: String,
    val sequenceId: String?,
    val encodedMessageEvent: String?,
    val decodedEvent: XChatDecodedEvent?,
)

internal data class XChatConversation(
    val conversationId: String,
    val type: XChatConversationType,
    val isMuted: Boolean?,
    val groupName: String?,
    val groupAvatarUrl: String?,
    val participants: List<XChatUser>,
    val latestSequenceId: String?,
    val latestMessage: XChatDecodedEvent?,
    val unreadCount: Long,
)

internal enum class XChatConversationType {
    Direct,
    Group,
}

internal data class XChatUser(
    val userId: String,
    val name: String?,
    val screenName: String?,
    val avatarUrl: String?,
)

internal data class XChatDecodedEvent(
    val sequenceId: String?,
    val messageId: String?,
    val senderId: String?,
    val conversationId: String?,
    val conversationToken: String? = null,
    val createdAtMillis: Long?,
    val kind: XChatDecodedEventKind,
    val text: String? = null,
    val conversationKeyVersion: String? = null,
    val targetSequenceIds: List<String> = emptyList(),
    val emoji: String? = null,
    val encrypted: Boolean = false,
    val decryptError: Boolean = false,
)

internal enum class XChatDecodedEventKind {
    Message,
    ConversationKeyChange,
    Typing,
    Delete,
    ConversationDelete,
    MetadataChange,
    Read,
    Unread,
    ReactionAdd,
    ReactionRemove,
    Edit,
    AvCallStarted,
    AvCallEnded,
    AvCallMissed,
    Unknown,
}

internal data class XChatUserPublicKeys(
    val userId: String?,
    val onXChat: Boolean,
    val permissions: XChatPermissions?,
    val keys: List<XChatPublicKey>,
)

internal data class XChatPermissions(
    val canDm: Boolean?,
    val canDmOnXChat: Boolean?,
    val dmBlocking: Boolean?,
    val passesPremiumCheck: Boolean?,
)

internal data class XChatCallPermission(
    val canDm: Boolean?,
    val errorCode: String?,
)

internal data class XChatPublicKey(
    val version: String?,
    val publicKey: String?,
    val signingPublicKey: String?,
    val identityPublicKeySignature: String?,
    val registrationMethod: String?,
    val tokenMap: XChatJuiceboxTokenMap?,
    val targetTokenMap: XChatJuiceboxTokenMap?,
)

internal data class XChatJuiceboxTokenMap(
    val realmState: String?,
    val realmStateString: String?,
    val maxGuessCount: Int?,
    val recoverThreshold: Int?,
    val registerThreshold: Int?,
    val keyStoreTokenMapJson: String?,
    val entries: List<XChatJuiceboxTokenEntry>,
)

internal data class XChatJuiceboxTokenEntry(
    val realmId: String,
    val token: String,
    val address: String,
    val publicKey: String?,
)

private data class XChatRecipientKey(
    val userId: String,
    val publicKeySpki: ByteArray,
    val version: String,
)

private fun JsonObject.toUserPublicKeys(): XChatUserPublicKeys {
    val result = obj("result")
    val publicKeysResult = result?.obj("get_public_keys")
    val keys =
        publicKeysResult
            ?.array("public_keys_with_token_map")
            .orEmpty()
            .mapNotNull { entry ->
                val entryObject = entry.asObjectOrNull() ?: return@mapNotNull null
                val metadata = entryObject.obj("public_key_with_metadata") ?: return@mapNotNull null
                val publicKey = metadata.obj("public_key")
                XChatPublicKey(
                    version = metadata.string("version"),
                    publicKey = publicKey?.string("public_key"),
                    signingPublicKey = publicKey?.string("signing_public_key"),
                    identityPublicKeySignature = publicKey?.string("identity_public_key_signature"),
                    registrationMethod = publicKey?.string("registration_method"),
                    tokenMap = entryObject.obj("token_map")?.toJuiceboxTokenMap(),
                    targetTokenMap = entryObject.obj("target_token_map")?.toJuiceboxTokenMap(),
                )
            }
    return XChatUserPublicKeys(
        userId = string("rest_id"),
        onXChat = keys.isNotEmpty(),
        permissions = result?.obj("chat_permissions")?.toPermissions(),
        keys = keys,
    )
}

private fun JsonObject.toPermissions(): XChatPermissions =
    XChatPermissions(
        canDm = boolean("can_dm"),
        canDmOnXChat = boolean("can_dm_on_xchat"),
        dmBlocking = boolean("dm_blocking"),
        passesPremiumCheck = boolean("passes_premium_check"),
    )

private fun JsonObject.toCallPermission(): XChatCallPermission =
    XChatCallPermission(
        canDm = boolean("can_dm"),
        errorCode = string("error_code"),
    )

private fun JsonObject.toJuiceboxTokenMap(): XChatJuiceboxTokenMap =
    XChatJuiceboxTokenMap(
        realmState = string("realm_state"),
        realmStateString = string("realm_state_string"),
        maxGuessCount = int("max_guess_count"),
        recoverThreshold = int("recover_threshold"),
        registerThreshold = int("register_threshold"),
        keyStoreTokenMapJson = string("key_store_token_map_json"),
        entries =
            array("token_map")
                .orEmpty()
                .mapNotNull { entry ->
                    val entryObject = entry.asObjectOrNull() ?: return@mapNotNull null
                    val value = entryObject.obj("value") ?: return@mapNotNull null
                    XChatJuiceboxTokenEntry(
                        realmId = entryObject.string("key") ?: return@mapNotNull null,
                        token = value.string("token") ?: return@mapNotNull null,
                        address = value.string("address") ?: return@mapNotNull null,
                        publicKey = value.string("public_key"),
                    )
                },
    )

private fun JsonObject.toXChatUser(): XChatUser? {
    val userId = string("rest_id") ?: return null
    val result = obj("result")
    return XChatUser(
        userId = userId,
        name = result?.obj("core")?.string("name"),
        screenName = result?.obj("core")?.string("screen_name"),
        avatarUrl = result?.obj("avatar")?.string("image_url"),
    )
}

private fun List<XChatDecodedEvent>.minSequenceId(): String? =
    mapNotNull { it.sequenceId?.toULongOrNull() }
        .minOrNull()
        ?.toString()

private fun JsonArray?.asStringList(): List<String> =
    this
        .orEmpty()
        .mapNotNull { it.jsonPrimitive.contentOrNull }

private fun JsonObject.obj(name: String): JsonObject? = get(name).asObjectOrNull()

private fun JsonObject.array(name: String): JsonArray? = get(name) as? JsonArray

private fun JsonObject.string(name: String): String? =
    get(name)
        ?.jsonPrimitive
        ?.contentOrNull

private fun JsonObject.boolean(name: String): Boolean? =
    get(name)
        ?.jsonPrimitive
        ?.booleanOrNull

private fun JsonObject.int(name: String): Int? =
    get(name)
        ?.jsonPrimitive
        ?.intOrNull

private fun JsonObject.long(name: String): Long? =
    get(name)
        ?.jsonPrimitive
        ?.longOrNull

private fun JsonElement?.asObjectOrNull(): JsonObject? = this as? JsonObject
