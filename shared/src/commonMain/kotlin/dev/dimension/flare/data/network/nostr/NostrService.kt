package dev.dimension.flare.data.network.nostr

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.nostr.NostrCache
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiNumber
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.nostrLike
import dev.dimension.flare.ui.model.mapper.nostrRepost
import dev.dimension.flare.ui.render.RenderContent
import dev.dimension.flare.ui.render.RenderRun
import dev.dimension.flare.ui.render.RenderTextStyle
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import dev.dimension.flare.ui.render.uiRichTextOf
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import rust.nostr.sdk.Client
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import rust.nostr.sdk.Contact as RustContact
import rust.nostr.sdk.EventBuilder as RustEventBuilder
import rust.nostr.sdk.EventDeletionRequest as RustEventDeletionRequest
import rust.nostr.sdk.EventId as RustEventId
import rust.nostr.sdk.Keys as RustKeys
import rust.nostr.sdk.MuteList as RustMuteList
import rust.nostr.sdk.Nip19Enum as RustNip19Enum
import rust.nostr.sdk.NostrSigner as RustNostrSigner
import rust.nostr.sdk.PublicKey as RustPublicKey
import rust.nostr.sdk.RelayUrl as RustRelayUrl
import rust.nostr.sdk.Report as RustReport
import rust.nostr.sdk.SecretKey as RustSecretKey
import rust.nostr.sdk.SendEventOutput as RustSendEventOutput
import rust.nostr.sdk.Tag as RustTag

public val defaultNostrRelays: List<String> =
    listOf(
        "wss://relay.damus.io",
        "wss://nos.lol",
        "wss://relay.nostr.band",
    )

internal class NostrService(
    private val cache: NostrCache,
    private val accountKey: MicroBlogKey,
    private val credential: UiAccount.Nostr.Credential,
) : AutoCloseable {
    companion object {
        private val HEX_KEY_REGEX = Regex("^[0-9a-fA-F]{64}\$")
        private val HEX_DIGITS = "0123456789abcdef".toCharArray()
        internal const val NOSTR_HOST: String = "nostr"
        private const val MIN_EARLY_RETURN_EVENTS = 4
        private const val PUBLISH_SUCCESS_QUORUM = 3
        private const val RELAY_PUBLISH_TIMEOUT_MILLIS = 1_500L
        private const val RELAY_TIMEOUT_MILLIS = 3_500L
        private const val RELAY_SETTLE_TIMEOUT_MILLIS = 350L
        private const val MAX_REFERENCE_FETCH_ROUNDS = 4
        private const val MAX_EVENT_ID_BATCH = 100
        private const val MAX_ADDRESS_FILTER_BATCH = 32
        private const val MAX_HOME_AUTHORS = 250
        private const val MIN_METADATA_EVENT_LIMIT = 50

        internal fun importAccount(secretKeyInput: String): ImportedAccount {
            val normalizedSecret =
                secretKeyInput.trim().takeIf { it.isNotEmpty() }?.let(::normalizeSecret)
            return normalizedSecret?.use { secretKey ->
                val pubkeyHex =
                    RustKeys(secretKey).use { keys ->
                        keys.publicKey().use { it.toHex() }
                    }
                ImportedAccount(
                    pubkeyHex = pubkeyHex,
                    npub = bech32PublicKey(pubkeyHex),
                    nsec = secretKey.toBech32(),
                )
            } ?: error("A public key or secret key is required")
        }

        internal fun generateAccount(): ImportedAccount =
            RustKeys.Companion.generate().use { keys ->
                ImportedAccount(
                    pubkeyHex = keys.publicKey().use { it.toHex() },
                    npub = keys.publicKey().use { it.toBech32() },
                    nsec = keys.secretKey().use { it.toBech32() },
                )
            }

        internal fun exportAccount(credential: UiAccount.Nostr.Credential): ImportedAccount {
            val secretKey =
                requireNotNull(credential.nsec) {
                    "Nostr account does not have an exportable private key"
                }
            return importAccount(
                secretKeyInput = secretKey,
            )
        }

        private fun normalizeSecret(raw: String): RustSecretKey {
            val value = raw.removePrefix("nostr:").trim()
            return when {
                value.startsWith("nsec1", ignoreCase = true) ->
                    RustKeys.parse(value).use { it.secretKey() }

                HEX_KEY_REGEX.matches(value) -> RustSecretKey.Companion.parse(value.lowercase())

                else -> error("Unsupported secret key format")
            }
        }
    }

    private var connected = false

    private val secretKey by lazy {
        RustSecretKey.parse(credential.nsec)
    }

    private val keys by lazy {
        RustKeys(secretKey)
    }

    private val pubKey by lazy {
        keys.publicKey()
    }

    private val pubKeyHex by lazy {
        pubKey.toHex()
    }

    private val relays by lazy {
        credential.relays.map {
            RustRelayUrl.parse(it)
        }
    }

    private val client by lazy {
        Client(RustNostrSigner.keys(keys))
    }

    override fun close() {
        client.close()
        relays.forEach { it.close() }
        pubKey.close()
        keys.close()
        secretKey.close()
    }

    suspend fun ensureConnection() {
        if (connected) {
            return
        }
        relays.forEach { relay ->
            client.addRelay(relay)
        }
        client.connect()
        connected = false
    }

    internal data class ImportedAccount(
        val pubkeyHex: String,
        val npub: String,
        val nsec: String,
    )

    internal suspend fun loadHomeTimeline(
        pageSize: Int,
        until: Long?,
    ): List<UiTimelineV2> {
        val authors = loadAuthors(pubKeyHex)
        val events =
            queryFirstRelay(
                filters =
                    listOf(
                        Filter(
                            authors = authors,
                            kinds = timelineEventKinds,
                            until = until,
                            limit = pageSize,
                        ),
                    ),
                minEventsBeforeReturn =
                    pageSize
                        .coerceAtMost(MIN_EARLY_RETURN_EVENTS)
                        .coerceAtLeast(1),
            ).filter(::isTimelineRootEvent)
                .sortedByDescending { it.createdAt }

        if (events.isEmpty()) {
            return emptyList()
        }

        val eventGraph = loadEventGraph(roots = events)
        val interactionStats =
            loadInteractionStats(
                accountPubkey = pubKeyHex,
                targetEventIds = eventGraph.keys.toList(),
            )

        val profiles =
            loadProfiles(
                pubKeys = eventGraph.values.map { it.pubKey }.distinct(),
            )
        return events.toUiTimeline(
            profiles = profiles,
            eventsById = eventGraph,
            interactionStats = interactionStats,
        )
    }

    internal suspend fun loadProfile(targetPubkey: String): UiProfile {
        val metadata =
            loadMetadata(
                authors = listOf(targetPubkey),
            )[targetPubkey]
        return profileOf(
            pubKey = targetPubkey,
            metadata = metadata,
        )
    }

    internal suspend fun loadUserTimeline(
        targetPubkey: String,
        pageSize: Int,
        until: Long?,
        mediaOnly: Boolean,
    ): List<UiTimelineV2> {
        if (mediaOnly) {
            return emptyList()
        }

        val events =
            queryAllRelays(
                filters =
                    listOf(
                        Filter(
                            authors = listOf(targetPubkey),
                            kinds = timelineEventKinds,
                            until = until,
                            limit = pageSize,
                        ),
                    ),
            ).filter(::isTimelineRootEvent)
                .sortedByDescending { it.createdAt }
        if (events.isEmpty()) {
            return emptyList()
        }
        val eventGraph = loadEventGraph(roots = events)
        val interactionStats =
            loadInteractionStats(
                accountPubkey = pubKeyHex,
                targetEventIds = eventGraph.keys.toList(),
            )
        val profiles =
            loadProfiles(
                pubKeys = eventGraph.values.map { it.pubKey }.distinct(),
            )
        return events.toUiTimeline(
            profiles = profiles,
            eventsById = eventGraph,
            interactionStats = interactionStats,
        )
    }

    internal suspend fun searchStatus(
        query: String,
        pageSize: Int,
        until: Long?,
    ): List<UiTimelineV2> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) {
            return emptyList()
        }

        parseSearchStatusEventId(normalizedQuery)?.let { eventId ->
            return runCatching {
                listOf(
                    loadStatus(
                        statusKey = MicroBlogKey(eventId, NOSTR_HOST),
                    ),
                )
            }.getOrDefault(emptyList())
        }

        val events =
            queryAllRelays(
                filters =
                    listOf(
                        Filter(
                            kinds = listOf(TextNoteEvent.KIND),
                            until = until,
                            limit = pageSize,
                            search = normalizedQuery,
                        ),
                    ),
            ).filter(::isTimelineRootEvent)
                .filter { event ->
                    event is TextNoteEvent &&
                        event.content.contains(
                            normalizedQuery,
                            ignoreCase = true,
                        )
                }.sortedByDescending { it.createdAt }
                .take(pageSize)
        if (events.isEmpty()) {
            return emptyList()
        }

        val eventGraph = loadEventGraph(roots = events)
        val interactionStats =
            loadInteractionStats(
                accountPubkey = pubKeyHex,
                targetEventIds = eventGraph.keys.toList(),
            )
        val profiles =
            loadProfiles(
                pubKeys = eventGraph.values.map { it.pubKey }.distinct(),
            )
        return events.toUiTimeline(
            profiles = profiles,
            eventsById = eventGraph,
            interactionStats = interactionStats,
        )
    }

    internal suspend fun searchUser(
        query: String,
        pageSize: Int,
    ): List<UiProfile> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) {
            return emptyList()
        }

        parseSearchProfilePubkey(normalizedQuery)?.let { pubkey ->
            return listOf(
                loadProfile(
                    targetPubkey = pubkey,
                ),
            )
        }

        val metadataEvents =
            queryAllRelays(
                filters =
                    listOf(
                        Filter(
                            kinds = listOf(MetadataEvent.KIND),
                            limit = maxOf(pageSize * 4, MIN_METADATA_EVENT_LIMIT),
                            search = normalizedQuery,
                        ),
                    ),
            ).filterIsInstance<MetadataEvent>()
                .groupBy { it.pubKey }

        if (metadataEvents.isEmpty()) {
            return emptyList()
        }

        return metadataEvents
            .mapNotNull { (pubkey, events) ->
                val metadata = resolveMetadata(events) ?: return@mapNotNull null
                if (!metadata.matchesSearchQuery(normalizedQuery)) {
                    return@mapNotNull null
                }
                profileOf(
                    pubKey = pubkey,
                    metadata = metadata,
                )
            }.sortedBy { profile ->
                profile.name.raw.indexOf(normalizedQuery, ignoreCase = true).let {
                    if (it >= 0) {
                        it
                    } else {
                        Int.MAX_VALUE
                    }
                }
            }.take(pageSize)
    }

    internal suspend fun loadNotifications(
        pageSize: Int,
        until: Long?,
        type: dev.dimension.flare.data.datasource.microblog.NotificationFilter,
    ): List<UiTimelineV2> {
        val events =
            notificationFilters(
                accountPubkey = pubKeyHex,
                pageSize = pageSize,
                until = until,
                type = type,
            ).flatMap { filter ->
                queryAllRelays(
                    filters = listOf(filter),
                )
            }.distinctBy { it.id }
                .filterNot { it.pubKey == pubKeyHex }
                .sortedByDescending { it.createdAt }
                .take(pageSize)
        if (events.isEmpty()) {
            return emptyList()
        }

        val eventGraph = loadEventGraph(roots = events)
        val interactionStats =
            loadInteractionStats(
                accountPubkey = pubKeyHex,
                targetEventIds = eventGraph.keys.toList(),
            )
        val profiles =
            loadProfiles(
                pubKeys = eventGraph.values.map { it.pubKey }.distinct(),
            )
        return events.toUiNotifications(
            accountPubkey = pubKeyHex,
            profiles = profiles,
            eventsById = eventGraph,
            interactionStats = interactionStats,
        )
    }

    internal suspend fun loadStatus(statusKey: MicroBlogKey): UiTimelineV2 {
        val event =
            loadEvent(
                statusKey = statusKey,
            ) ?: error("Nostr status not found: $statusKey")
        val eventGraph = loadEventGraph(roots = listOf(event))
        val interactionStats =
            loadInteractionStats(
                accountPubkey = pubKeyHex,
                targetEventIds = eventGraph.keys.toList(),
            )
        val profiles =
            loadProfiles(
                pubKeys = eventGraph.values.map { it.pubKey }.distinct(),
            )
        return listOf(event)
            .toUiTimeline(
                profiles = profiles,
                eventsById = eventGraph,
                interactionStats = interactionStats,
            ).first()
    }

    internal suspend fun loadStatusContext(
        statusKey: MicroBlogKey,
        pageSize: Int,
    ): List<UiTimelineV2.Post> {
        val event =
            loadEvent(
                statusKey = statusKey,
            ) ?: error("Nostr status not found: $statusKey")

        val ancestorEvents = buildAncestorChain(event = event)
        val replyEvents =
            loadDirectReplies(
                statusKey = statusKey,
                pageSize = pageSize,
            )
        val threadEvents = (ancestorEvents + event + replyEvents).distinctBy(Event::id)
        if (threadEvents.isEmpty()) {
            return emptyList()
        }

        val eventGraph = loadEventGraph(roots = threadEvents)
        val interactionStats =
            loadInteractionStats(
                accountPubkey = pubKeyHex,
                targetEventIds = eventGraph.keys.toList(),
            )
        val profiles =
            loadProfiles(
                pubKeys = eventGraph.values.map { it.pubKey }.distinct(),
            )

        return threadEvents.toUiTimeline(
            profiles = profiles,
            eventsById = eventGraph,
            interactionStats = interactionStats,
        )
    }

    internal suspend fun relation(targetPubkey: String): dev.dimension.flare.ui.model.UiRelation {
        val follows =
            loadLatestContactList(pubKeyHex)?.verifiedFollowKeySet().orEmpty()
        val blocks = loadLatestBlockList(pubKeyHex)?.tags?.userIdSet().orEmpty()
        val mutes = loadLatestMuteList(pubKeyHex)?.tags?.mutedUserIdSet().orEmpty()
        return dev.dimension.flare.ui.model.UiRelation(
            following = targetPubkey in follows,
            blocking = targetPubkey in blocks,
            muted = targetPubkey in mutes,
        )
    }

    internal suspend fun follow(targetPubkey: String) {
        val latest = loadLatestContactList(pubKeyHex)
        val follows = (latest?.verifiedFollowKeySet().orEmpty() + targetPubkey).distinct()
        sendEventBuilder(
            builder =
                RustEventBuilder.Companion.contactList(
                    follows.map { RustContact(publicKey(it), null, null) },
                ),
        )
    }

    internal suspend fun unfollow(targetPubkey: String) {
        val latest = loadLatestContactList(pubKeyHex)
        val follows = latest?.verifiedFollowKeySet().orEmpty().filterNot { it == targetPubkey }
        sendEventBuilder(
            builder =
                RustEventBuilder.Companion.contactList(
                    follows.map { RustContact(publicKey(it), null, null) },
                ),
        )
    }

    internal suspend fun block(targetPubkey: String) {
        val latest = loadLatestBlockList(pubKeyHex)
        val blockList = (latest?.tags?.userIdSet().orEmpty() + targetPubkey).distinct()
        sendEventBuilder(
            builder =
                RustEventBuilder.Companion.followSet(
                    PeopleListEvent.BLOCK_LIST_D_TAG,
                    blockList.map(::publicKey),
                ),
        )
    }

    internal suspend fun unblock(targetPubkey: String) {
        val latest = loadLatestBlockList(pubKeyHex) ?: return
        val blockList = latest.tags.userIdSet().filterNot { it == targetPubkey }
        sendEventBuilder(
            builder =
                RustEventBuilder.Companion.followSet(
                    PeopleListEvent.BLOCK_LIST_D_TAG,
                    blockList.map(::publicKey),
                ),
        )
    }

    internal suspend fun mute(targetPubkey: String) {
        val latest = loadLatestMuteList(pubKeyHex)
        val mutedUsers = (latest?.tags?.mutedUserIdSet().orEmpty() + targetPubkey).distinct()
        sendEventBuilder(
            builder =
                RustEventBuilder.Companion.muteList(
                    RustMuteList(
                        mutedUsers.map(::publicKey),
                        emptyList(),
                        emptyList(),
                        emptyList(),
                    ),
                ),
        )
    }

    internal suspend fun unmute(targetPubkey: String) {
        val latest = loadLatestMuteList(pubKeyHex) ?: return
        val mutedUsers = latest.tags.mutedUserIdSet().filterNot { it == targetPubkey }
        sendEventBuilder(
            builder =
                RustEventBuilder.Companion.muteList(
                    RustMuteList(
                        mutedUsers.map(::publicKey),
                        emptyList(),
                        emptyList(),
                        emptyList(),
                    ),
                ),
        )
    }

    internal suspend fun composeNote(content: String): String = sendEventBuilder(RustEventBuilder.Companion.textNote(content))

    internal suspend fun composeReply(
        statusKey: MicroBlogKey,
        content: String,
    ): String {
        val target =
            loadEvent(statusKey = statusKey)
                ?: error("Reply target not found: $statusKey")
        return sendEventBuilder(
            builder = textNoteBuilder(content = content, tags = buildReplyTags(target)),
        )
    }

    internal suspend fun composeQuote(
        statusKey: MicroBlogKey,
        content: String,
    ): String {
        val target =
            loadEvent(statusKey = statusKey)
        val cachedPost =
            target?.let { null } ?: cache.getPost(accountKey = accountKey, statusKey = statusKey)
        val quoteTag =
            quoteTagArray(
                target = target,
                statusKey = statusKey,
                cachedAuthorPubKey = cachedPost?.user?.key?.id,
            ) ?: error("Quote target not found: $statusKey")
        val authorPubKey =
            target?.pubKey ?: cachedPost?.user?.key?.id
                ?: error("Quote target not found: $statusKey")
        return sendEventBuilder(
            builder =
                textNoteBuilder(
                    content = content,
                    tags =
                        buildList {
                            add(quoteTag)
                            add(pTag(authorPubKey))
                        },
                ),
        )
    }

    internal suspend fun repost(statusKey: MicroBlogKey): String {
        val target =
            loadEvent(statusKey = statusKey)
                ?: error("Repost target not found: $statusKey")
        return sendEventBuilder(
            builder =
                RustEventBuilder.Companion.repost(
                    target.toRust(),
                ),
        )
    }

    internal suspend fun react(statusKey: MicroBlogKey): String {
        val target =
            loadEvent(statusKey = statusKey)
                ?: error("Reaction target not found: $statusKey")
        return sendEventBuilder(
            builder = RustEventBuilder.Companion.reaction(target.toRust(), ReactionEvent.LIKE),
        )
    }

    internal suspend fun report(statusKey: MicroBlogKey) {
        val target =
            loadEvent(statusKey = statusKey)
                ?: error("Report target not found: $statusKey")
        sendEventBuilder(
            builder =
                RustEventBuilder.Companion.report(
                    listOf(
                        RustTag.Companion.eventReport(eventId(target.id), RustReport.SPAM),
                        RustTag.Companion.publicKeyReport(
                            publicKey(target.pubKey),
                            RustReport.SPAM,
                        ),
                    ),
                    "",
                ),
        )
    }

    internal suspend fun deleteStatus(statusKey: MicroBlogKey) {
        val target =
            loadEvent(statusKey = statusKey)
                ?: error("Delete target not found: $statusKey")
        sendEventBuilder(
            builder =
                RustEventBuilder.Companion.delete(
                    RustEventDeletionRequest(
                        listOf(eventId(target.id)),
                        emptyList(),
                        "",
                    ),
                ),
        )
    }

    private suspend fun loadAuthors(accountPubkey: String): List<String> {
        val latestContacts = loadLatestContactList(accountPubkey)

        return (latestContacts?.verifiedFollowKeySet().orEmpty() + accountPubkey)
            .distinct()
            .take(MAX_HOME_AUTHORS)
    }

    private suspend fun loadLatestContactList(accountPubkey: String): ContactListEvent? =
        queryAllRelays(
            filters =
                listOf(
                    Filter(
                        authors = listOf(accountPubkey),
                        kinds = listOf(ContactListEvent.KIND),
                        limit = 1,
                    ),
                ),
        ).filterIsInstance<ContactListEvent>()
            .maxByOrNull { it.createdAt }

    private suspend fun loadLatestMuteList(accountPubkey: String): MuteListEvent? =
        queryAllRelays(
            filters =
                listOf(
                    Filter(
                        authors = listOf(accountPubkey),
                        kinds = listOf(MuteListEvent.KIND),
                        limit = 1,
                    ),
                ),
        ).filterIsInstance<MuteListEvent>()
            .maxByOrNull { it.createdAt }

    private suspend fun loadLatestBlockList(accountPubkey: String): PeopleListEvent? =
        queryAllRelays(
            filters =
                listOf(
                    Filter(
                        authors = listOf(accountPubkey),
                        kinds = listOf(PeopleListEvent.KIND),
                        tags = mapOf("d" to listOf(PeopleListEvent.BLOCK_LIST_D_TAG)),
                        limit = 10,
                    ),
                ),
        ).filterIsInstance<PeopleListEvent>()
            .maxByOrNull { it.createdAt }

    private suspend fun loadMetadata(authors: List<String>): Map<String, UserMetadata> {
        if (authors.isEmpty()) {
            return emptyMap()
        }
        val eventsByPubkey =
            queryAllRelays(
                filters =
                    listOf(
                        Filter(
                            authors = authors,
                            kinds = listOf(MetadataEvent.KIND),
                            limit = maxOf(authors.size * 3, MIN_METADATA_EVENT_LIMIT),
                        ),
                    ),
            ).filterIsInstance<MetadataEvent>()
                .groupBy { it.pubKey }

        return buildMap {
            eventsByPubkey.forEach { (pubkey, events) ->
                resolveMetadata(events)?.let {
                    put(pubkey, it)
                }
            }
        }
    }

    internal fun resolveMetadata(events: List<MetadataEvent>): UserMetadata? =
        events
            .sortedByDescending { it.createdAt }
            .firstNotNullOfOrNull { event ->
                runCatching { event.contactMetaData() }.getOrNull()
            }

    private suspend fun loadProfiles(pubKeys: List<String>): Map<String, UiProfile> {
        if (pubKeys.isEmpty()) {
            return emptyMap()
        }
        val cachedProfiles = cache.getProfiles(pubKeys)

        val missingPubKeys = pubKeys.distinct().filterNot { it in cachedProfiles }
        if (missingPubKeys.isEmpty()) {
            return cachedProfiles
        }

        val fetchedProfiles =
            loadMetadata(missingPubKeys)
                .let { metadata ->
                    missingPubKeys.associateWith { pubKey ->
                        profileOf(
                            pubKey = pubKey,
                            metadata = metadata[pubKey],
                        )
                    }
                }

        return cachedProfiles + fetchedProfiles
    }

    private suspend fun queryFirstRelay(
        filters: List<Filter>,
        minEventsBeforeReturn: Int = MIN_EARLY_RETURN_EVENTS,
    ): List<Event> =
        queryRelays(
            filters = filters,
            waitForAllRelays = false,
            minEventsBeforeReturn = minEventsBeforeReturn,
        )

    private suspend fun queryAllRelays(filters: List<Filter>): List<Event> =
        queryRelays(
            filters = filters,
            waitForAllRelays = true,
            minEventsBeforeReturn = null,
        )

    private suspend fun queryRelays(
        filters: List<Filter>,
        waitForAllRelays: Boolean,
        minEventsBeforeReturn: Int?,
    ): List<Event> =
        coroutineScope {
            val events =
                filters
                    .map {
                        async {
                            client.fetchEvents(it.toRust(), timeout = 1.minutes).toVec()
                        }
                    }.awaitAll()
                    .flatten()

            events
                .map { it.use { it.toCompatEvent() } }
        }

    private suspend fun sendEventBuilder(builder: RustEventBuilder): String {
        val requiredSuccessCount = minOf(PUBLISH_SUCCESS_QUORUM, relays.size)
        if (requiredSuccessCount == 0) {
            error("No valid relay URLs available for publishing")
        }

        val output = client.sendEventBuilder(builder)
        ensurePublishQuorum(output, requiredSuccessCount)
        return output.id.toHex()
    }

    private fun ensurePublishQuorum(
        output: RustSendEventOutput,
        requiredSuccessCount: Int,
    ) {
        val successCount = output.success.size
        if (successCount >= requiredSuccessCount) {
            return
        }
        throw PublishToRelayException(
            requiredSuccessCount = requiredSuccessCount,
            successCount = successCount,
            failures = output.failed.map { (relay, message) -> IllegalStateException("$relay: $message") },
        )
    }

    private class PublishToRelayException(
        requiredSuccessCount: Int,
        successCount: Int,
        val failures: List<Throwable>,
    ) : Exception(
            "Failed to publish event to enough relays: $successCount/$requiredSuccessCount succeeded.",
        )

    private fun parseSearchProfilePubkey(raw: String): String? {
        val value = raw.removePrefix("nostr:").trim()
        return when {
            value.startsWith("npub1", ignoreCase = true) ->
                withNip19(value) { nip19 ->
                    (nip19 as? RustNip19Enum.Pubkey)?.npub?.use { it.toHex() }
                }

            value.startsWith("nprofile1", ignoreCase = true) ->
                withNip19(value) { nip19 ->
                    (nip19 as? RustNip19Enum.Profile)?.nprofile?.use { profile ->
                        profile.publicKey().use { it.toHex() }
                    }
                }

            HEX_KEY_REGEX.matches(value) -> value.lowercase()

            else -> null
        }
    }

    private fun parseSearchStatusEventId(raw: String): String? {
        val value = raw.removePrefix("nostr:").trim()
        return when {
            value.startsWith("note1", ignoreCase = true) ->
                withNip19(value) { nip19 ->
                    (nip19 as? RustNip19Enum.Note)?.eventId?.use { it.toHex() }
                }

            value.startsWith("nevent1", ignoreCase = true) ->
                withNip19(value) { nip19 ->
                    (nip19 as? RustNip19Enum.Event)?.event?.use { event ->
                        event.eventId().use { it.toHex() }
                    }
                }

            HEX_KEY_REGEX.matches(value) -> value.lowercase()

            else -> null
        }
    }

    private fun publicKey(hex: String): RustPublicKey = RustPublicKey.Companion.parse(hex)

    private fun eventId(hex: String): RustEventId = RustEventId.Companion.parse(hex)

    private fun pTag(pubKey: String): Array<String> = arrayOf("p", pubKey)

    private fun textNoteBuilder(
        content: String,
        tags: List<Array<String>> = emptyList(),
    ): RustEventBuilder =
        RustEventBuilder.Companion
            .textNote(content)
            .tags(tags.map { RustTag.Companion.parse(it.toList()) })

    private fun buildReplyTags(target: Event): List<Array<String>> =
        when (target) {
            is TextNoteEvent -> {
                val rootId = target.parentEventIds().firstOrNull() ?: target.id
                buildList {
                    add(arrayOf("e", rootId, "", "root"))
                    add(arrayOf("e", target.id, "", "reply"))
                    add(pTag(target.pubKey))
                }.distinctBy { it.joinToString(separator = "\u0000") }
            }

            else ->
                listOf(
                    arrayOf("e", target.id),
                    pTag(target.pubKey),
                )
        }

    private suspend fun loadEvent(statusKey: MicroBlogKey): Event? =
        queryAllRelays(
            filters = listOf(Filter(ids = listOf(statusKey.id))),
        ).maxByOrNull { it.createdAt }

    private suspend fun buildAncestorChain(event: Event): List<Event> {
        val chain = mutableListOf<Event>()
        var current: Event? = event
        val visited = mutableSetOf<String>()
        while (current is TextNoteEvent) {
            val parentId = current.immediateParentEventId() ?: break
            if (!visited.add(parentId)) {
                break
            }
            val parent =
                loadEvent(
                    statusKey = MicroBlogKey(parentId, NOSTR_HOST),
                ) ?: break
            chain += parent
            current = parent
        }
        return chain.reversed()
    }

    private suspend fun loadDirectReplies(
        statusKey: MicroBlogKey,
        pageSize: Int,
    ): List<Event> =
        queryAllRelays(
            filters =
                listOf(
                    Filter(
                        kinds = listOf(TextNoteEvent.KIND),
                        tags = mapOf("e" to listOf(statusKey.id)),
                        limit = maxOf(pageSize * 3, pageSize, 20),
                    ),
                ),
        ).filterIsInstance<TextNoteEvent>()
            .filter { it.isDirectReplyTo(statusKey.id) }
            .sortedBy(Event::createdAt)
            .take(pageSize)

    internal fun quoteTagArray(
        target: Event?,
        statusKey: MicroBlogKey,
        cachedAuthorPubKey: String?,
    ): Array<String>? =
        when {
            target != null -> arrayOf("q", target.id, "", target.pubKey)
            cachedAuthorPubKey != null && statusKey.id.length == 64 ->
                buildList {
                    add("q")
                    add(statusKey.id)
                    add(cachedAuthorPubKey)
                }.toTypedArray()

            else -> null
        }

    private suspend fun loadInteractionStats(
        accountPubkey: String,
        targetEventIds: List<String>,
    ): Map<String, InteractionStats> {
        val distinctIds = targetEventIds.distinct()
        if (distinctIds.isEmpty()) {
            return emptyMap()
        }

        val interactions =
            distinctIds
                .chunked(MAX_EVENT_ID_BATCH)
                .flatMap { ids ->
                    queryAllRelays(
                        filters =
                            listOf(
                                Filter(
                                    kinds =
                                        listOf(
                                            ReactionEvent.KIND,
                                            RepostEvent.KIND,
                                            GenericRepostEvent.KIND,
                                        ),
                                    tags = mapOf("e" to ids),
                                    limit = maxOf(ids.size * 20, 200),
                                ),
                            ),
                    )
                }.distinctBy { it.id }

        if (interactions.isEmpty()) {
            return emptyMap()
        }

        return interactions.fold(mutableMapOf<String, InteractionStats>()) { acc, event ->
            val targetId =
                when (event) {
                    is ReactionEvent -> event.originalPost().lastOrNull()
                    is RepostEvent -> event.boostedEventId()
                    is GenericRepostEvent -> event.boostedEventId()
                    else -> null
                } ?: return@fold acc

            if (targetId !in distinctIds) {
                return@fold acc
            }

            val current = acc[targetId] ?: InteractionStats()
            acc[targetId] =
                when (event) {
                    is ReactionEvent ->
                        if (event.content == ReactionEvent.LIKE || event.content.isBlank()) {
                            current.copy(
                                reactionCount = current.reactionCount + 1,
                                myReactionEventId =
                                    if (event.pubKey == accountPubkey) {
                                        event.id
                                    } else {
                                        current.myReactionEventId
                                    },
                            )
                        } else {
                            current
                        }

                    is RepostEvent ->
                        current.copy(
                            repostCount = current.repostCount + 1,
                            myRepostEventId =
                                if (event.pubKey == accountPubkey) {
                                    event.id
                                } else {
                                    current.myRepostEventId
                                },
                        )

                    is GenericRepostEvent ->
                        current.copy(
                            repostCount = current.repostCount + 1,
                            myRepostEventId =
                                if (event.pubKey == accountPubkey) {
                                    event.id
                                } else {
                                    current.myRepostEventId
                                },
                        )

                    else -> current
                }
            acc
        }
    }

    internal fun List<Event>.toUiTimeline(
        profiles: Map<String, UiProfile>,
        eventsById: Map<String, Event>,
        interactionStats: Map<String, InteractionStats> = emptyMap(),
    ): List<UiTimelineV2.Post> {
        val cache = mutableMapOf<String, UiTimelineV2.Post>()

        fun resolve(
            event: Event,
            visited: Set<String>,
        ): UiTimelineV2.Post? {
            if (event.id in visited) {
                return null
            }
            cache[event.id]?.let { return it }
            val nextVisited = visited + event.id
            val resolved =
                when (event) {
                    is TextNoteEvent ->
                        event.toUi(
                            profile =
                                profiles[event.pubKey] ?: profileOf(
                                    event.pubKey,
                                    null,
                                ),
                            eventsById = eventsById,
                            profiles = profiles,
                            interactionStats = interactionStats,
                            visited = nextVisited,
                            resolveEvent = ::resolve,
                        )

                    is RepostEvent ->
                        event.toUiRepost(
                            profiles = profiles,
                            eventsById = eventsById,
                            visited = nextVisited,
                            resolveEvent = ::resolve,
                        )

                    is GenericRepostEvent ->
                        event.toUiGenericRepost(
                            profiles = profiles,
                            eventsById = eventsById,
                            visited = nextVisited,
                            resolveEvent = ::resolve,
                        )

                    else -> null
                }
            if (resolved != null) {
                cache[event.id] = resolved
            }
            return resolved
        }

        return mapNotNull { event ->
            resolve(event, emptySet())
        }
    }

    private fun List<Event>.toUiNotifications(
        accountPubkey: String,
        profiles: Map<String, UiProfile>,
        eventsById: Map<String, Event>,
        interactionStats: Map<String, InteractionStats> = emptyMap(),
    ): List<UiTimelineV2.Post> {
        val cache = mutableMapOf<String, UiTimelineV2.Post>()

        fun resolve(
            event: Event,
            visited: Set<String>,
        ): UiTimelineV2.Post? {
            if (event.id in visited) {
                return null
            }
            cache[event.id]?.let { return it }
            val nextVisited = visited + event.id
            val resolved =
                when (event) {
                    is TextNoteEvent ->
                        event.toUi(
                            profile =
                                profiles[event.pubKey] ?: profileOf(
                                    event.pubKey,
                                    null,
                                ),
                            eventsById = eventsById,
                            profiles = profiles,
                            interactionStats = interactionStats,
                            visited = nextVisited,
                            resolveEvent = ::resolve,
                        )

                    is RepostEvent ->
                        event.toUiRepost(
                            profiles = profiles,
                            eventsById = eventsById,
                            visited = nextVisited,
                            resolveEvent = ::resolve,
                        )

                    is GenericRepostEvent ->
                        event.toUiGenericRepost(
                            profiles = profiles,
                            eventsById = eventsById,
                            visited = nextVisited,
                            resolveEvent = ::resolve,
                        )

                    else -> null
                }
            if (resolved != null) {
                cache[event.id] = resolved
            }
            return resolved
        }

        return mapNotNull { event ->
            event.toUiNotification(
                accountPubkey = accountPubkey,
                profiles = profiles,
                eventsById = eventsById,
                interactionStats = interactionStats,
                resolveEvent = ::resolve,
            )
        }
    }

    private fun TextNoteEvent.toUi(
        profile: UiProfile,
        eventsById: Map<String, Event>,
        profiles: Map<String, UiProfile>,
        interactionStats: Map<String, InteractionStats>,
        visited: Set<String>,
        resolveEvent: (Event, Set<String>) -> UiTimelineV2.Post?,
    ): UiTimelineV2.Post {
        val statusKey = MicroBlogKey(id, NOSTR_HOST)
        val stats = interactionStats[id] ?: InteractionStats()
        return UiTimelineV2.Post(
            message = null,
            platformType = PlatformType.Nostr,
            images = mediaFromTags().toImmutableList(),
            sensitive = false,
            contentWarning = null,
            user = profile,
            content = content.toUiPlainText(),
            actions =
                buildList {
                    add(
                        ActionMenu.Item(
                            icon = UiIcon.Reply,
                            text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Reply),
                            clickEvent =
                                ClickEvent.Deeplink(
                                    DeeplinkRoute.Compose.Reply(
                                        accountKey = accountKey,
                                        statusKey = statusKey,
                                    ),
                                ),
                            count = UiNumber(0L),
                        ),
                    )
                    add(
                        ActionMenu.Group(
                            displayItem =
                                ActionMenu.nostrRepost(
                                    statusKey = statusKey,
                                    repostEventId = stats.myRepostEventId,
                                    count = stats.repostCount,
                                    accountKey = accountKey,
                                ),
                            actions =
                                listOf(
                                    ActionMenu.nostrRepost(
                                        statusKey = statusKey,
                                        repostEventId = stats.myRepostEventId,
                                        count = stats.repostCount,
                                        accountKey = accountKey,
                                    ),
                                    ActionMenu.Item(
                                        icon = UiIcon.Quote,
                                        text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Quote),
                                        clickEvent =
                                            ClickEvent.Deeplink(
                                                DeeplinkRoute.Compose.Quote(
                                                    accountKey = accountKey,
                                                    statusKey = statusKey,
                                                ),
                                            ),
                                    ),
                                ).toImmutableList(),
                        ),
                    )
                    add(
                        ActionMenu.nostrLike(
                            statusKey = statusKey,
                            reactionEventId = stats.myReactionEventId,
                            count = stats.reactionCount,
                            accountKey = accountKey,
                        ),
                    )
                    add(
                        ActionMenu.Group(
                            displayItem =
                                ActionMenu.Item(
                                    icon = UiIcon.More,
                                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.More),
                                ),
                            actions =
                                listOf(
                                    if (pubKey == accountKey.id) {
                                        ActionMenu.Item(
                                            icon = UiIcon.Delete,
                                            text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Delete),
                                            color = ActionMenu.Item.Color.Red,
                                            clickEvent =
                                                ClickEvent.Deeplink(
                                                    DeeplinkRoute.Status.DeleteConfirm(
                                                        accountType =
                                                            AccountType.Specific(
                                                                accountKey,
                                                            ),
                                                        statusKey = statusKey,
                                                    ),
                                                ),
                                        )
                                    } else {
                                        ActionMenu.Item(
                                            icon = UiIcon.Report,
                                            text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Report),
                                            color = ActionMenu.Item.Color.Red,
                                            clickEvent =
                                                ClickEvent.event(accountKey) {
                                                    dev.dimension.flare.data.datasource.microblog.PostEvent.Nostr.Report(
                                                        postKey = statusKey,
                                                        accountKey = accountKey,
                                                    )
                                                },
                                        )
                                    },
                                ).toImmutableList(),
                        ),
                    )
                }.toImmutableList(),
            poll = null,
            statusKey = statusKey,
            card = null,
            createdAt = Instant.fromEpochSeconds(createdAt).toUi(),
            emojiReactions = persistentListOf(),
            sourceChannel = null,
            visibility = null,
            replyToHandle = null,
            references =
                (
                    parentEventIds().map {
                        UiTimelineV2.Post.Reference(
                            statusKey = MicroBlogKey(it, NOSTR_HOST),
                            type = ReferenceType.Reply,
                        )
                    } +
                        quoteEventIds()
                            .map {
                                UiTimelineV2.Post.Reference(
                                    statusKey = MicroBlogKey(it, NOSTR_HOST),
                                    type = ReferenceType.Quote,
                                )
                            }
                ).distinctBy { it.type to it.statusKey }
                    .toImmutableList(),
            parents =
                parentEventIds()
                    .mapNotNull { parentId ->
                        parentId
                            .takeUnless { it in visited }
                            ?.let(eventsById::get)
                            ?.let { resolveEvent(it, visited) }
                    }.toImmutableList(),
            quote =
                (
                    quoteEventIds()
                        .mapNotNull { quoteId ->
                            quoteId
                                .takeUnless { it in visited }
                                ?.let(eventsById::get)
                                ?.let { resolveEvent(it, visited) }
                        } +
                        quoteAddressReferences()
                            .mapNotNull { address ->
                                resolveAddressReference(
                                    address,
                                    eventsById,
                                    visited,
                                    resolveEvent,
                                )
                            }
                ).distinctBy { it.statusKey }
                    .toImmutableList(),
            internalRepost = null,
            clickEvent =
                ClickEvent.Deeplink(
                    DeeplinkRoute.Status.Detail(
                        statusKey = statusKey,
                        accountType = AccountType.Specific(accountKey),
                    ),
                ),
            extraKey = null,
            accountType = AccountType.Specific(accountKey),
        )
    }

    private fun TextNoteEvent.parentEventIds(): List<String> {
        val rootIds = tags.mapNotNull(MarkedETag::parseRootId)
        val replyIds = tags.mapNotNull(MarkedETag::parseReply).map { it.eventId }
        val positionalIds =
            if (rootIds.isEmpty() && replyIds.isEmpty()) {
                tags.mapNotNull(MarkedETag::parseOnlyPositionalThreadTagsIds)
            } else {
                emptyList()
            }
        return (rootIds + replyIds + positionalIds).distinct()
    }

    private fun TextNoteEvent.immediateParentEventId(): String? {
        val replyId = tags.mapNotNull(MarkedETag::parseReply).lastOrNull()?.eventId
        if (replyId != null) {
            return replyId
        }
        val rootIds = tags.mapNotNull(MarkedETag::parseRootId)
        if (rootIds.isNotEmpty()) {
            return rootIds.lastOrNull()
        }
        return tags.mapNotNull(MarkedETag::parseOnlyPositionalThreadTagsIds).lastOrNull()
    }

    private fun TextNoteEvent.isDirectReplyTo(statusId: String): Boolean {
        val replyIds = tags.mapNotNull(MarkedETag::parseReply).map { it.eventId }
        if (replyIds.isNotEmpty()) {
            return replyIds.lastOrNull() == statusId
        }
        val rootIds = tags.mapNotNull(MarkedETag::parseRootId)
        if (rootIds.isNotEmpty()) {
            return rootIds.lastOrNull() == statusId
        }
        val positionalIds = tags.mapNotNull(MarkedETag::parseOnlyPositionalThreadTagsIds)
        return positionalIds.lastOrNull() == statusId
    }

    private fun TextNoteEvent.quoteEventIds(): List<String> = tags.mapNotNull(QEventTag::parse).map { it.eventId }.distinct()

    private fun TextNoteEvent.quoteAddressReferences(): List<Address> =
        tags
            .mapNotNull(QAddressableTag::parse)
            .map { it.address }
            .distinctBy { it.toValue() }

    private fun TextNoteEvent.mediaFromTags(): List<UiMedia> {
        val mediaFromIMeta =
            tags
                .mapNotNull(IMetaTag::parse)
                .flatten()
                .mapNotNull(::toUiMedia)

        val urlsFromR =
            tags
                .filter { it.size > 1 && it[0] == "r" }
                .map { it[1] }
                .filter(::looksLikeMediaUrl)
                .filterNot { url -> mediaFromIMeta.any { it.url == url } }
                .mapNotNull { url -> toUiMedia(url = url, dimensions = null, description = null) }

        return (mediaFromIMeta + urlsFromR).distinctBy { it.url }
    }

    private fun toUiMedia(iMeta: IMetaTag): UiMedia? =
        toUiMedia(
            url = iMeta.url,
            dimensions = iMeta.properties["dim"]?.firstOrNull()?.let(DimensionTag::parse),
            description = iMeta.properties["alt"]?.firstOrNull(),
        )

    private fun toUiMedia(
        url: String,
        dimensions: DimensionTag?,
        description: String?,
    ): UiMedia? {
        val width = dimensions?.width?.toFloat() ?: 0f
        val height = dimensions?.height?.toFloat() ?: 0f
        return when {
            isVideoUrl(url) ->
                UiMedia.Video(
                    url = url,
                    thumbnailUrl = url,
                    description = description,
                    height = height,
                    width = width,
                )

            isGifUrl(url) ->
                UiMedia.Gif(
                    url = url,
                    previewUrl = url,
                    description = description,
                    height = height,
                    width = width,
                )

            isAudioUrl(url) ->
                UiMedia.Audio(
                    url = url,
                    description = description,
                    previewUrl = null,
                )

            isImageUrl(url) ->
                UiMedia.Image(
                    url = url,
                    previewUrl = url,
                    description = description,
                    height = height,
                    width = width,
                    sensitive = false,
                )

            else ->
                UiMedia.Image(
                    url = url,
                    previewUrl = url,
                    description = description,
                    height = height,
                    width = width,
                    sensitive = false,
                )
        }
    }

    private fun looksLikeMediaUrl(url: String): Boolean = isImageUrl(url) || isVideoUrl(url) || isGifUrl(url) || isAudioUrl(url)

    private fun isImageUrl(url: String): Boolean =
        url.substringBefore('?').lowercase().let {
            it.endsWith(".jpg") ||
                it.endsWith(".jpeg") ||
                it.endsWith(".png") ||
                it.endsWith(".webp") ||
                it.endsWith(
                    ".heic",
                )
        }

    private fun isVideoUrl(url: String): Boolean =
        url.substringBefore('?').lowercase().let {
            it.endsWith(".mp4") || it.endsWith(".webm") || it.endsWith(".mov") || it.endsWith(".m4v")
        }

    private fun isGifUrl(url: String): Boolean = url.substringBefore('?').lowercase().endsWith(".gif")

    private fun isAudioUrl(url: String): Boolean =
        url.substringBefore('?').lowercase().let {
            it.endsWith(".mp3") ||
                it.endsWith(".m4a") ||
                it.endsWith(".aac") ||
                it.endsWith(".wav") ||
                it.endsWith(
                    ".ogg",
                )
        }

    private fun RepostEvent.toUiRepost(
        profiles: Map<String, UiProfile>,
        eventsById: Map<String, Event>,
        visited: Set<String>,
        resolveEvent: (Event, Set<String>) -> UiTimelineV2.Post?,
    ): UiTimelineV2.Post? {
        val boostedEvent = resolvedBoostedEvent(eventsById)
        val boostedPost = boostedEvent?.let { resolveEvent(it, visited) } ?: return null
        return boostedPost.copy(
            message =
                repostMessage(
                    actor = profiles[pubKey] ?: profileOf(pubKey, null),
                    statusKey = MicroBlogKey(id, NOSTR_HOST),
                    createdAt = createdAt,
                ),
            statusKey = MicroBlogKey(id, NOSTR_HOST),
            internalRepost = boostedPost,
        )
    }

    private fun GenericRepostEvent.toUiGenericRepost(
        profiles: Map<String, UiProfile>,
        eventsById: Map<String, Event>,
        visited: Set<String>,
        resolveEvent: (Event, Set<String>) -> UiTimelineV2.Post?,
    ): UiTimelineV2.Post? {
        val boostedEvent = resolvedBoostedEvent(eventsById)
        val boostedPost = boostedEvent?.let { resolveEvent(it, visited) } ?: return null
        return boostedPost.copy(
            message =
                repostMessage(
                    actor = profiles[pubKey] ?: profileOf(pubKey, null),
                    statusKey = MicroBlogKey(id, NOSTR_HOST),
                    createdAt = createdAt,
                ),
            statusKey = MicroBlogKey(id, NOSTR_HOST),
            internalRepost = boostedPost,
        )
    }

    private fun RepostEvent.resolvedBoostedEvent(eventsById: Map<String, Event>): Event? =
        containedPost()
            ?: boostedEventId()?.let(eventsById::get)
            ?: boostedAddress()?.let { address ->
                eventsById.values
                    .filter { it.addressValue() == address.toValue() }
                    .maxByOrNull { it.createdAt }
            }

    private fun GenericRepostEvent.resolvedBoostedEvent(eventsById: Map<String, Event>): Event? =
        containedPost()
            ?: boostedEventId()?.let(eventsById::get)
            ?: boostedAddress()?.let { address ->
                eventsById.values
                    .filter { it.addressValue() == address.toValue() }
                    .maxByOrNull { it.createdAt }
            }

    private fun repostMessage(
        actor: UiProfile,
        statusKey: MicroBlogKey,
        createdAt: Long,
    ): UiTimelineV2.Message =
        UiTimelineV2.Message(
            user = actor,
            statusKey = statusKey,
            icon = UiIcon.Retweet,
            type = UiTimelineV2.Message.Type.Localized(UiTimelineV2.Message.Type.Localized.MessageId.Repost),
            createdAt = Instant.fromEpochSeconds(createdAt).toUi(),
            clickEvent =
                ClickEvent.Deeplink(
                    DeeplinkRoute.Profile.User(
                        accountType = AccountType.Specific(accountKey),
                        userKey = actor.key,
                    ),
                ),
            accountType = AccountType.Specific(accountKey),
        )

    private fun resolveAddressReference(
        address: Address,
        eventsById: Map<String, Event>,
        visited: Set<String>,
        resolveEvent: (Event, Set<String>) -> UiTimelineV2.Post?,
    ): UiTimelineV2.Post? =
        eventsById.values
            .filter { it.addressValue() == address.toValue() }
            .maxByOrNull { it.createdAt }
            ?.let { resolveEvent(it, visited) }

    private fun Event.toUiNotification(
        accountPubkey: String,
        profiles: Map<String, UiProfile>,
        eventsById: Map<String, Event>,
        interactionStats: Map<String, InteractionStats>,
        resolveEvent: (Event, Set<String>) -> UiTimelineV2.Post?,
    ): UiTimelineV2.Post? =
        when (this) {
            is TextNoteEvent -> {
                val post =
                    toUi(
                        profile = profiles[pubKey] ?: profileOf(pubKey, null),
                        eventsById = eventsById,
                        profiles = profiles,
                        interactionStats = interactionStats,
                        visited = setOf(id),
                        resolveEvent = { event, visited -> resolveEvent(event, visited) },
                    )
                val hasParent = parentEventIds().isNotEmpty()
                post.copy(
                    message =
                        notificationMessage(
                            actor =
                                post.user ?: profiles[pubKey] ?: profileOf(
                                    pubKey,
                                    null,
                                ),
                            statusKey = MicroBlogKey(id, NOSTR_HOST),
                            createdAt = createdAt,
                            icon = if (hasParent) UiIcon.Reply else UiIcon.Mention,
                            type =
                                UiTimelineV2.Message.Type.Localized(
                                    if (hasParent) {
                                        UiTimelineV2.Message.Type.Localized.MessageId.Reply
                                    } else {
                                        UiTimelineV2.Message.Type.Localized.MessageId.Mention
                                    },
                                ),
                        ),
                )
            }

            is ReactionEvent -> {
                if (content != ReactionEvent.LIKE && content.isNotBlank()) {
                    return null
                }
                if (accountPubkey !in taggedUsers()) {
                    return null
                }
                val targetId = originalPost().lastOrNull() ?: return null
                val target =
                    eventsById[targetId]?.let { resolveEvent(it, setOf(id)) }
                        ?: return null
                target.copy(
                    message =
                        notificationMessage(
                            actor = profiles[pubKey] ?: profileOf(pubKey, null),
                            statusKey = MicroBlogKey(id, NOSTR_HOST),
                            createdAt = createdAt,
                            icon = UiIcon.Favourite,
                            type =
                                UiTimelineV2.Message.Type.Localized(
                                    UiTimelineV2.Message.Type.Localized.MessageId.Favourite,
                                ),
                        ),
                    extraKey = id,
                )
            }

            is RepostEvent -> {
                if (accountPubkey !in taggedUsers()) {
                    return null
                }
                val boosted =
                    resolvedBoostedEvent(eventsById)?.let { resolveEvent(it, setOf(id)) }
                        ?: return null
                boosted.copy(
                    message =
                        notificationMessage(
                            actor = profiles[pubKey] ?: profileOf(pubKey, null),
                            statusKey = MicroBlogKey(id, NOSTR_HOST),
                            createdAt = createdAt,
                            icon = UiIcon.Retweet,
                            type =
                                UiTimelineV2.Message.Type.Localized(
                                    UiTimelineV2.Message.Type.Localized.MessageId.Repost,
                                ),
                        ),
                    statusKey = MicroBlogKey(id, NOSTR_HOST),
                    internalRepost = boosted,
                    extraKey = id,
                )
            }

            is GenericRepostEvent -> {
                if (accountPubkey !in taggedUsers()) {
                    return null
                }
                val boosted =
                    resolvedBoostedEvent(eventsById)?.let { resolveEvent(it, setOf(id)) }
                        ?: return null
                boosted.copy(
                    message =
                        notificationMessage(
                            actor = profiles[pubKey] ?: profileOf(pubKey, null),
                            statusKey = MicroBlogKey(id, NOSTR_HOST),
                            createdAt = createdAt,
                            icon = UiIcon.Retweet,
                            type =
                                UiTimelineV2.Message.Type.Localized(
                                    UiTimelineV2.Message.Type.Localized.MessageId.Repost,
                                ),
                        ),
                    statusKey = MicroBlogKey(id, NOSTR_HOST),
                    internalRepost = boosted,
                    extraKey = id,
                )
            }

            else -> null
        }

    private suspend fun loadEventGraph(roots: List<Event>): Map<String, Event> {
        val eventsById = LinkedHashMap<String, Event>()
        val pendingEventIds = LinkedHashSet<String>()
        val pendingAddresses = LinkedHashMap<String, Address>()

        fun register(event: Event) {
            if (eventsById.containsKey(event.id)) {
                return
            }
            eventsById[event.id] = event
            embeddedEvents(event).forEach(::register)
            referencedEventIds(event)
                .filterNot(eventsById::containsKey)
                .forEach(pendingEventIds::add)
            referencedAddresses(event).forEach { address ->
                val addressValue = address.toValue()
                if (!pendingAddresses.containsKey(addressValue)) {
                    pendingAddresses[addressValue] = address
                }
            }
        }

        roots.forEach(::register)

        repeat(MAX_REFERENCE_FETCH_ROUNDS) {
            val eventIdsToFetch =
                pendingEventIds.filterNot(eventsById::containsKey).take(MAX_EVENT_ID_BATCH)
            val addressesToFetch =
                pendingAddresses.values
                    .filter { address ->
                        eventsById.values.none { it.addressValue() == address.toValue() }
                    }.take(MAX_ADDRESS_FILTER_BATCH)

            if (eventIdsToFetch.isEmpty() && addressesToFetch.isEmpty()) {
                return@repeat
            }

            eventIdsToFetch.forEach(pendingEventIds::remove)
            addressesToFetch.forEach { pendingAddresses.remove(it.toValue()) }

            val fetchedEvents =
                fetchEventsByIds(eventIdsToFetch) +
                    fetchEventsByAddress(addressesToFetch)

            if (fetchedEvents.isEmpty()) {
                return@repeat
            }

            fetchedEvents.forEach(::register)
        }

        return eventsById
    }

    private suspend fun fetchEventsByIds(eventIds: List<String>): List<Event> =
        if (eventIds.isEmpty()) {
            emptyList()
        } else {
            eventIds
                .chunked(MAX_EVENT_ID_BATCH)
                .flatMap { ids ->
                    queryAllRelays(
                        filters = listOf(Filter(ids = ids)),
                    )
                }.distinctBy { it.id }
        }

    private suspend fun fetchEventsByAddress(addresses: List<Address>): List<Event> {
        if (addresses.isEmpty()) {
            return emptyList()
        }
        val filters =
            addresses.map { address ->
                Filter(
                    authors = listOf(address.pubKeyHex),
                    kinds = listOf(address.kind),
                    tags = mapOf("d" to listOf(address.dTag)),
                    limit = 1,
                )
            }
        return queryAllRelays(
            filters = filters,
        ).groupBy { it.addressValue() }
            .mapNotNull { (_, values) -> values.maxByOrNull { it.createdAt } }
    }

    private fun referencedEventIds(event: Event): List<String> =
        when (event) {
            is TextNoteEvent -> event.parentEventIds() + event.quoteEventIds()
            is ReactionEvent -> event.originalPost()
            is RepostEvent -> listOfNotNull(event.boostedEventId())
            is GenericRepostEvent -> listOfNotNull(event.boostedEventId())
            else -> emptyList()
        }.distinct()

    private fun referencedAddresses(event: Event): List<Address> =
        when (event) {
            is TextNoteEvent -> event.quoteAddressReferences()
            is RepostEvent -> listOfNotNull(event.boostedAddress())
            is GenericRepostEvent -> listOfNotNull(event.boostedAddress())
            else -> emptyList()
        }.distinctBy { it.toValue() }

    private fun embeddedEvents(event: Event): List<Event> =
        when (event) {
            is RepostEvent -> listOfNotNull(event.containedPost())
            is GenericRepostEvent -> listOfNotNull(event.containedPost())
            else -> emptyList()
        }

    private fun Event.addressValue(): String = Address.assemble(kind = kind, pubKeyHex = pubKey, dTag = dTag())

    private fun Event.taggedUsers(): List<String> =
        tags
            .filter { it.size > 1 && it[0] == "p" }
            .map { it[1] }
            .distinct()

    private fun isTimelineRootEvent(event: Event): Boolean =
        event is TextNoteEvent ||
            event is RepostEvent ||
            event is GenericRepostEvent

    private fun profileOf(
        pubKey: String,
        metadata: UserMetadata?,
    ): UiProfile {
        val bestName = metadata?.bestName().orEmpty()
        val npub = bech32PublicKey(pubKey)
        val handleRaw =
            metadata?.name?.takeIf { it.isNotBlank() }
                ?: metadata?.nip05?.substringBefore("@")?.takeIf { it.isNotBlank() }
                ?: bestName.ifBlank { npub.take(16) }
        return UiProfile(
            key = MicroBlogKey(pubKey, NOSTR_HOST),
            handle =
                UiHandle(
                    raw = handleRaw,
                    host = NOSTR_HOST,
                ),
            avatar = metadata?.picture.orEmpty(),
            nameInternal = bestName.ifBlank { npub.take(16) }.toUiPlainText(),
            platformType = PlatformType.Nostr,
            clickEvent =
                ClickEvent.Deeplink(
                    DeeplinkRoute.Profile.User(
                        accountType = AccountType.Specific(accountKey),
                        userKey = MicroBlogKey(pubKey, NOSTR_HOST),
                    ),
                ),
            banner = metadata?.banner,
            description = metadata?.about?.takeIf { it.isNotBlank() }?.toUiPlainText(),
            matrices =
                UiProfile.Matrices(
                    fansCount = 0,
                    followsCount = 0,
                    statusesCount = 0,
                ),
            mark =
                listOfNotNull(
                    if (metadata?.nip05Verified == true) {
                        UiProfile.Mark.Verified
                    } else {
                        null
                    },
                    if (metadata?.bot == true) {
                        UiProfile.Mark.Bot
                    } else {
                        null
                    },
                ).toImmutableList(),
            bottomContent = metadata.toBottomContent(),
        )
    }

    private fun UserMetadata?.toBottomContent(): UiProfile.BottomContent? {
        val fields =
            this
                ?.let { metadata ->
                    buildMap<String, dev.dimension.flare.ui.render.UiRichText> {
                        metadata.pronouns?.takeIf { it.isNotBlank() }?.let {
                            put("Pronouns", it.toUiPlainText())
                        }
                        metadata.website?.takeIf { it.isNotBlank() }?.let {
                            put("Website", externalLink(display = it, target = it))
                        }
                        metadata.nip05?.takeIf { it.isNotBlank() }?.let {
                            put("NIP-05", it.toUiPlainText())
                        }
                        metadata.lud16?.takeIf { it.isNotBlank() }?.let {
                            put("LUD16", it.toUiPlainText())
                        }
                        metadata.lud06?.takeIf { it.isNotBlank() }?.let {
                            put("LUD06", it.toUiPlainText())
                        }
                        metadata.domain?.takeIf { it.isNotBlank() }?.let {
                            put("Domain", externalLink(display = it, target = it))
                        }
                        metadata.twitter?.takeIf { it.isNotBlank() }?.let {
                            val handle = it.removePrefix("@")
                            put(
                                "Twitter",
                                externalLink(
                                    display = "@$handle",
                                    target = "https://x.com/$handle",
                                ),
                            )
                        }
                        metadata.tags
                            ?.lists
                            ?.mapNotNull { tag ->
                                tag
                                    .takeIf { it.isNotEmpty() }
                                    ?.joinToString(separator = " / ")
                                    ?.takeIf { it.isNotBlank() }
                            }?.toList()
                            ?.takeIf { it.isNotEmpty() }
                            ?.let {
                                put("Tags", it.joinToString(separator = "\n").toUiPlainText())
                            }
                    }
                }?.takeIf { it.isNotEmpty() }
                ?.toImmutableMap()
        return fields?.let(UiProfile.BottomContent::Fields)
    }

    private fun UserMetadata.matchesSearchQuery(query: String): Boolean =
        listOfNotNull(
            bestName()?.takeIf { it.isNotBlank() },
            name?.takeIf { it.isNotBlank() },
            displayName?.takeIf { it.isNotBlank() },
            nip05?.takeIf { it.isNotBlank() },
            about?.takeIf { it.isNotBlank() },
        ).any { it.contains(query, ignoreCase = true) }

    private fun externalLink(
        display: String,
        target: String,
    ) = uiRichTextOf(
        renderRuns =
            listOf(
                RenderContent.Text(
                    runs =
                        listOf(
                            RenderRun.Text(
                                text = display,
                                style = RenderTextStyle(link = target.toHttpsUrl()),
                            ),
                        ).toImmutableList(),
                ),
            ),
    )

    private fun String.toHttpsUrl(): String =
        if (startsWith("http://") || startsWith("https://")) {
            this
        } else {
            "https://$this"
        }

    internal data class InteractionStats(
        val reactionCount: Long = 0,
        val repostCount: Long = 0,
        val myReactionEventId: String? = null,
        val myRepostEventId: String? = null,
    )

    private val timelineEventKinds =
        listOf(TextNoteEvent.KIND, RepostEvent.KIND, GenericRepostEvent.KIND)
    private val notificationInteractionKinds =
        listOf(ReactionEvent.KIND, RepostEvent.KIND, GenericRepostEvent.KIND)

    private fun notificationFilters(
        accountPubkey: String,
        pageSize: Int,
        until: Long?,
        type: dev.dimension.flare.data.datasource.microblog.NotificationFilter,
    ): List<Filter> =
        when (type) {
            dev.dimension.flare.data.datasource.microblog.NotificationFilter.All ->
                listOf(
                    Filter(
                        kinds = listOf(TextNoteEvent.KIND),
                        tags = mapOf("p" to listOf(accountPubkey)),
                        until = until,
                        limit = pageSize,
                    ),
                    Filter(
                        kinds = notificationInteractionKinds,
                        tags = mapOf("p" to listOf(accountPubkey)),
                        until = until,
                        limit = pageSize,
                    ),
                )

            dev.dimension.flare.data.datasource.microblog.NotificationFilter.Mention ->
                listOf(
                    Filter(
                        kinds = listOf(TextNoteEvent.KIND),
                        tags = mapOf("p" to listOf(accountPubkey)),
                        until = until,
                        limit = pageSize,
                    ),
                )

            else -> emptyList()
        }

    private fun notificationMessage(
        actor: UiProfile,
        statusKey: MicroBlogKey,
        createdAt: Long,
        icon: UiIcon,
        type: UiTimelineV2.Message.Type,
    ): UiTimelineV2.Message =
        UiTimelineV2.Message(
            user = actor,
            statusKey = statusKey,
            icon = icon,
            type = type,
            createdAt = Instant.fromEpochSeconds(createdAt).toUi(),
            clickEvent =
                ClickEvent.Deeplink(
                    DeeplinkRoute.Profile.User(
                        accountType = AccountType.Specific(accountKey),
                        userKey = actor.key,
                    ),
                ),
            accountType = AccountType.Specific(accountKey),
        )
}
