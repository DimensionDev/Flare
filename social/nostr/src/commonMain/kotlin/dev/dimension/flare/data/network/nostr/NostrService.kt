package dev.dimension.flare.data.network.nostr

import com.vitorpamplona.quartz.nip01Core.core.toHexKey
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.relay.client.INostrClient
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.toNsec
import dev.dimension.flare.common.JSON
import dev.dimension.flare.common.UploadMedia
import dev.dimension.flare.common.jsonObjectOrNull
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.microblog.PostActionFamily
import dev.dimension.flare.data.datasource.microblog.userActionsMenu
import dev.dimension.flare.data.datasource.nostr.NostrCache
import dev.dimension.flare.data.platform.NOSTR_PLATFORM_ID
import dev.dimension.flare.data.platform.NostrCredential
import dev.dimension.flare.data.platform.NostrSignerCredential
import dev.dimension.flare.data.platform.effectivePubkeyHex
import dev.dimension.flare.data.platform.effectiveSigner
import dev.dimension.flare.data.platform.normalized
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiNumber
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.UiTranslatableText
import dev.dimension.flare.ui.model.asTimelinePostItem
import dev.dimension.flare.ui.model.mapper.nostrLike
import dev.dimension.flare.ui.model.mapper.nostrRepost
import dev.dimension.flare.ui.model.toUiImage
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import com.vitorpamplona.quartz.nip01Core.core.Event as QuartzEvent
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent as QuartzGenericRepostEvent
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent as QuartzReactionEvent

internal val defaultNostrRelays: List<String> =
    listOf(
        "wss://relay.damus.io",
        "wss://nos.lol",
        "wss://relay.plebstr.com",
        "wss://nostr.bitcoiner.social",
    )

internal class NostrService(
    private val cache: NostrCache,
    private val accountKey: MicroBlogKey,
    credential: NostrCredential,
    private val amberSignerBridge: AmberSignerBridge,
    initialRelays: List<String> = emptyList(),
    private val suppliedClient: INostrClient? = null,
) : AutoCloseable {
    companion object {
        private val HEX_KEY_REGEX = Regex("^[0-9a-fA-F]{64}\$")
        internal const val NOSTR_HOST: String = "nostr"
        private const val RELAY_LIST_METADATA_KIND = 10_002
        private const val MAX_REFERENCE_FETCH_ROUNDS = 4
        private const val MAX_EVENT_ID_BATCH = 100
        private const val MAX_ADDRESS_FILTER_BATCH = 32
        private const val MAX_HOME_AUTHORS = 250
        private const val MIN_METADATA_EVENT_LIMIT = 50

        internal suspend fun importAccount(input: String): ImportedAccount {
            val value = input.removePrefix("nostr:").trim()
            require(value.isNotEmpty()) { "A public key, private key, or bunker URI is required" }

            parseNostrSecret(value)?.let { secretKey ->
                val keys = KeyPair(secretKey)
                val pubkeyHex = keys.pubKey.toHexKey()
                return ImportedAccount.LocalKey(
                    pubkeyHex = pubkeyHex,
                    npub = nostrBech32PublicKey(pubkeyHex),
                    nsec = secretKey.toNsec(),
                )
            }

            parsePublicKeyHex(value)?.let { pubkeyHex ->
                return ImportedAccount.ReadOnly(
                    pubkeyHex = pubkeyHex,
                    npub = nostrBech32PublicKey(pubkeyHex),
                )
            }

            if (value.startsWith("bunker://", ignoreCase = true) || value.startsWith("nostrconnect://", ignoreCase = true)) {
                return importBunkerAccount(value)
            }

            error("Unsupported Nostr account input")
        }

        internal fun generateAccount(): ImportedAccount.LocalKey =
            KeyPair().let { keys ->
                val pubkeyHex = keys.pubKey.toHexKey()
                ImportedAccount.LocalKey(
                    pubkeyHex = pubkeyHex,
                    npub = nostrBech32PublicKey(pubkeyHex),
                    nsec = requireNotNull(keys.privKey).toNsec(),
                )
            }

        internal interface PendingQrLogin : AutoCloseable {
            val connectUri: String

            suspend fun awaitAccount(): ImportedAccount.RemoteSigner
        }

        internal fun beginQrLogin(relays: List<String> = defaultNostrRelays): PendingQrLogin = NostrQrLogin(normalizeRelayUrls(relays))

        internal suspend fun resolvePublicRelays(
            pubkeyHex: String,
            bootstrapRelays: List<String> = defaultNostrRelays,
        ): List<String> {
            val normalizedBootstrap = normalizeRelayUrls(bootstrapRelays)
            return NostrRelayClient().use { transport ->
                val events =
                    transport.client
                        .fetchNostrEvents(
                            relays = normalizedNostrRelays(normalizedBootstrap),
                            filters =
                                listOf(
                                    Filter(
                                        authors = listOf(pubkeyHex),
                                        kinds = listOf(RELAY_LIST_METADATA_KIND, ContactListEvent.KIND),
                                        limit = 10,
                                    ),
                                ),
                            timeout = 10.seconds,
                        ).map { it.toCompatEvent() }
                extractRelayUrls(events).ifEmpty { normalizedBootstrap }
            }
        }

        internal fun exportAccount(credential: NostrCredential): ImportedAccount {
            val secretKey = (credential.effectiveSigner as? NostrSignerCredential.LocalKey)?.nsec
            requireNotNull(secretKey) {
                "Nostr account does not have an exportable private key"
            }
            val normalizedSecret = parseNostrSecret(secretKey)?.toNsec()
            return ImportedAccount.LocalKey(
                pubkeyHex = credential.pubkeyHex.ifBlank { error("Nostr account is missing a public key") },
                npub = nostrBech32PublicKey(credential.pubkeyHex.ifBlank { error("Nostr account is missing a public key") }),
                nsec = normalizedSecret ?: error("Failed to normalize exported secret"),
            )
        }

        private suspend fun importBunkerAccount(uri: String): ImportedAccount.RemoteSigner {
            val credential =
                NostrSignerCredential.Bunker(
                    uri = uri,
                    secret = requireNotNull(KeyPair().privKey).toNsec(),
                )
            return NostrBunkerSession(credential).use { session ->
                val pubkeyHex = session.publicKey(connect = true)
                ImportedAccount.RemoteSigner(
                    pubkeyHex = pubkeyHex,
                    npub = nostrBech32PublicKey(pubkeyHex),
                    signerCredential = credential.copy(userPubkeyHex = pubkeyHex),
                )
            }
        }

        private fun extractRelayUrls(events: List<Event>): List<String> {
            val relayListEvent =
                events
                    .filter { it.kind == RELAY_LIST_METADATA_KIND }
                    .maxByOrNull { it.createdAt }
            relayListEvent?.let { event ->
                relayUrlsFromTags(event.tags).takeIf { it.isNotEmpty() }?.let { return it }
            }

            val contactListEvent = events.filterIsInstance<ContactListEvent>().maxByOrNull { it.createdAt }
            contactListEvent?.let { event ->
                relayUrlsFromTags(event.tags).takeIf { it.isNotEmpty() }?.let { return it }
                runCatching {
                    JSON
                        .parseToJsonElement(event.content)
                        .jsonObjectOrNull
                        ?.keys
                        ?.toList()
                        .orEmpty()
                }.getOrDefault(emptyList())
                    .let(::normalizeRelayUrls)
                    .takeIf { it.isNotEmpty() }
                    ?.let { return it }
            }

            return emptyList()
        }

        private fun relayUrlsFromTags(tags: Array<Array<String>>): List<String> =
            normalizeRelayUrls(
                tags.mapNotNull { tag ->
                    tag
                        .takeIf { it.size > 1 && it[0] == "r" }
                        ?.get(1)
                },
            )

        private fun normalizeRelayUrls(relays: List<String>): List<String> =
            relays
                .ifEmpty { defaultNostrRelays }
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinct()
    }

    private val credential = credential.normalized(accountKey)
    private val pubKeyHex = this.credential.effectivePubkeyHex(accountKey)
    private val signerHandle = nostrEventSigner(this.credential.effectiveSigner, pubKeyHex, amberSignerBridge)
    private val currentRelays = MutableStateFlow(normalizedNostrRelays(normalizeRelayUrls(initialRelays)))
    private val relayClient by lazy { NostrRelayClient(signerHandle) { currentRelays.value } }
    private val client get() = suppliedClient ?: relayClient.client
    internal val canSign: Boolean get() = signerHandle.canSign
    private val blossomUploader by lazy {
        NostrBlossomUploader(
            buildAuthHeader = { sha256 ->
                buildBlossomAuthorizationHeader(buildBlossomUploadAuthEvent(sha256))
            },
        )
    }

    override fun close() {
        if (suppliedClient == null) relayClient.close()
        signerHandle.close()
    }

    suspend fun ensureConnection() {
        client.connect()
    }

    suspend fun updateRelays(relays: List<String>) {
        currentRelays.value = normalizedNostrRelays(normalizeRelayUrls(relays))
    }

    internal sealed interface ImportedAccount {
        val pubkeyHex: String
        val npub: String
        val signerCredential: NostrSignerCredential?

        data class ReadOnly(
            override val pubkeyHex: String,
            override val npub: String,
        ) : ImportedAccount {
            override val signerCredential: NostrSignerCredential? = null
        }

        data class LocalKey(
            override val pubkeyHex: String,
            override val npub: String,
            val nsec: String,
        ) : ImportedAccount {
            override val signerCredential: NostrSignerCredential = NostrSignerCredential.LocalKey(nsec)
        }

        data class RemoteSigner(
            override val pubkeyHex: String,
            override val npub: String,
            override val signerCredential: NostrSignerCredential,
        ) : ImportedAccount
    }

    internal suspend fun loadHomeTimeline(
        pageSize: Int,
        until: Long?,
    ): List<UiTimelineV2> {
        val authors = loadAuthors(pubKeyHex)
        val events =
            queryAllRelays(
                filters =
                    listOf(
                        Filter(
                            authors = authors,
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

        val renderContexts = eventGraph.buildRenderContexts()
        val profiles =
            loadProfiles(
                pubKeys = eventGraph.profilePubKeysForRendering(renderContexts),
            )
        return events.toUiTimeline(
            profiles = profiles,
            eventsById = eventGraph,
            interactionStats = interactionStats,
            renderContexts = renderContexts,
        )
    }

    internal suspend fun loadProfile(targetPubkey: String): UiProfile {
        val cachedProfile = cache.getProfiles(listOf(targetPubkey))[targetPubkey]
        val metadata =
            loadMetadata(
                authors = listOf(targetPubkey),
            )[targetPubkey]
        return profileOf(
            pubKey = targetPubkey,
            metadata = metadata,
            cachedProfile = cachedProfile,
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
        val renderContexts = eventGraph.buildRenderContexts()
        val profiles =
            loadProfiles(
                pubKeys = eventGraph.profilePubKeysForRendering(renderContexts),
            )
        return events.toUiTimeline(
            profiles = profiles,
            eventsById = eventGraph,
            interactionStats = interactionStats,
            renderContexts = renderContexts,
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
        val renderContexts = eventGraph.buildRenderContexts()
        val profiles =
            loadProfiles(
                pubKeys = eventGraph.profilePubKeysForRendering(renderContexts),
            )
        return events.toUiTimeline(
            profiles = profiles,
            eventsById = eventGraph,
            interactionStats = interactionStats,
            renderContexts = renderContexts,
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
                    cachedProfile = null,
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

        val eventGraph = loadEventGraph(roots = events, includeReplyParents = false)
        val interactionStats =
            loadInteractionStats(
                accountPubkey = pubKeyHex,
                targetEventIds = eventGraph.keys.toList(),
            )
        val renderContexts = eventGraph.buildRenderContexts()
        val profiles =
            loadProfiles(
                pubKeys = eventGraph.profilePubKeysForRendering(renderContexts),
            )
        return events.toUiNotifications(
            accountPubkey = pubKeyHex,
            profiles = profiles,
            eventsById = eventGraph,
            interactionStats = interactionStats,
            renderContexts = renderContexts,
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
        val renderContexts = eventGraph.buildRenderContexts()
        val profiles =
            loadProfiles(
                pubKeys = eventGraph.profilePubKeysForRendering(renderContexts),
            )
        return listOf(event)
            .toUiTimeline(
                profiles = profiles,
                eventsById = eventGraph,
                interactionStats = interactionStats,
                renderContexts = renderContexts,
            ).first()
    }

    internal suspend fun loadStatusContext(
        statusKey: MicroBlogKey,
        pageSize: Int,
    ): List<UiTimelineV2> {
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
        val renderContexts = eventGraph.buildRenderContexts()
        val profiles =
            loadProfiles(
                pubKeys = eventGraph.profilePubKeysForRendering(renderContexts),
            )

        return threadEvents.toUiTimeline(
            profiles = profiles,
            eventsById = eventGraph,
            interactionStats = interactionStats,
            renderContexts = renderContexts,
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
        sendEventBuilder(eventTemplate(ContactListEvent.KIND, tags = follows.map(::pTag)))
    }

    internal suspend fun unfollow(targetPubkey: String) {
        val latest = loadLatestContactList(pubKeyHex)
        val follows = latest?.verifiedFollowKeySet().orEmpty().filterNot { it == targetPubkey }
        sendEventBuilder(eventTemplate(ContactListEvent.KIND, tags = follows.map(::pTag)))
    }

    internal suspend fun block(targetPubkey: String) {
        val latest = loadLatestBlockList(pubKeyHex)
        val users = (latest?.tags?.userIdSet().orEmpty() + targetPubkey).distinct()
        sendEventBuilder(
            eventTemplate(
                PeopleListEvent.KIND,
                tags =
                    listOf(arrayOf("d", PeopleListEvent.BLOCK_LIST_D_TAG)) + users.map(::pTag),
            ),
        )
    }

    internal suspend fun unblock(targetPubkey: String) {
        val latest = loadLatestBlockList(pubKeyHex) ?: return
        val users = latest.tags.userIdSet().filterNot { it == targetPubkey }
        sendEventBuilder(
            eventTemplate(
                PeopleListEvent.KIND,
                tags =
                    listOf(arrayOf("d", PeopleListEvent.BLOCK_LIST_D_TAG)) + users.map(::pTag),
            ),
        )
    }

    internal suspend fun mute(targetPubkey: String) {
        val latest = loadLatestMuteList(pubKeyHex)
        val users = (latest?.tags?.mutedUserIdSet().orEmpty() + targetPubkey).distinct()
        sendEventBuilder(eventTemplate(MuteListEvent.KIND, tags = users.map(::pTag)))
    }

    internal suspend fun unmute(targetPubkey: String) {
        val latest = loadLatestMuteList(pubKeyHex) ?: return
        val users = latest.tags.mutedUserIdSet().filterNot { it == targetPubkey }
        sendEventBuilder(eventTemplate(MuteListEvent.KIND, tags = users.map(::pTag)))
    }

    internal suspend fun uploadMedia(
        serverUrl: String,
        media: UploadMedia,
        altText: String?,
    ): UploadedMedia =
        blossomUploader.upload(
            serverUrl = serverUrl,
            media = media,
            altText = altText,
        )

    internal suspend fun composeNote(
        content: String,
        media: List<UploadedMedia> = emptyList(),
        contentWarning: String? = null,
    ): String =
        sendEventBuilder(
            textNoteBuilder(
                content = contentWithMediaUrls(content, media),
                tags = contentWarningTag(contentWarning) + mediaTags(media),
            ),
        )

    internal suspend fun composeReply(
        statusKey: MicroBlogKey,
        content: String,
        media: List<UploadedMedia> = emptyList(),
        contentWarning: String? = null,
    ): String {
        val target =
            loadEvent(statusKey = statusKey)
                ?: error("Reply target not found: $statusKey")
        return sendEventBuilder(
            builder =
                textNoteBuilder(
                    content = contentWithMediaUrls(content, media),
                    tags = buildReplyTags(target) + contentWarningTag(contentWarning) + mediaTags(media),
                ),
        )
    }

    internal suspend fun composeQuote(
        statusKey: MicroBlogKey,
        content: String,
        media: List<UploadedMedia> = emptyList(),
        contentWarning: String? = null,
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
                    content = contentWithMediaUrls(content, media),
                    tags =
                        buildList {
                            add(quoteTag)
                            add(pTag(authorPubKey))
                            addAll(contentWarningTag(contentWarning))
                            addAll(mediaTags(media))
                        },
                ),
        )
    }

    internal suspend fun repost(statusKey: MicroBlogKey): String {
        val target = loadEvent(statusKey) ?: error("Repost target not found: $statusKey")
        return sendEventBuilder(
            if (target.kind == TextNoteEvent.KIND) {
                eventTemplate(RepostEvent.KIND, target.toJson(), listOf(arrayOf("e", target.id), pTag(target.pubKey)))
            } else {
                QuartzGenericRepostEvent.build(EventHintBundle(QuartzEvent.fromJson(target.toJson())))
            },
        )
    }

    internal suspend fun react(statusKey: MicroBlogKey): String {
        val target = loadEvent(statusKey) ?: error("Reaction target not found: $statusKey")
        return sendEventBuilder(QuartzReactionEvent.like(EventHintBundle(QuartzEvent.fromJson(target.toJson()))))
    }

    internal suspend fun report(statusKey: MicroBlogKey) {
        val target = loadEvent(statusKey) ?: error("Report target not found: $statusKey")
        sendEventBuilder(
            eventTemplate(
                kind = 1984,
                tags = listOf(arrayOf("e", target.id, "spam"), arrayOf("p", target.pubKey, "spam")),
            ),
        )
    }

    internal suspend fun deleteStatus(statusKey: MicroBlogKey) {
        val target = loadEvent(statusKey) ?: error("Delete target not found: $statusKey")
        sendEventBuilder(eventTemplate(DeletionEvent.KIND, tags = listOf(arrayOf("e", target.id))))
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
                            cachedProfile = null,
                        )
                    }
                }

        return cachedProfiles + fetchedProfiles
    }

    private fun Map<String, Event>.buildRenderContexts(): Map<String, NostrTextRenderContext> =
        values
            .asSequence()
            .filterIsInstance<TextNoteEvent>()
            .associate { it.id to buildNostrTextRenderContext(it.content, it.tags) }

    private fun Map<String, Event>.profilePubKeysForRendering(renderContexts: Map<String, NostrTextRenderContext>): List<String> =
        values
            .flatMap { event ->
                buildList {
                    add(event.pubKey)
                    if (event is TextNoteEvent) {
                        addAll(renderContexts[event.id]?.mentionedProfilePubKeys.orEmpty())
                    }
                }
            }.distinct()

    private suspend fun queryAllRelays(filters: List<Filter>): List<Event> =
        client.fetchNostrEvents(currentRelays.value, filters).map { it.toCompatEvent() }

    private suspend fun sendEventBuilder(builder: EventTemplate<out QuartzEvent>): String {
        requireWritable()
        val event = signerHandle.sign(builder)
        return client.publishNostrEvent(event, currentRelays.value)
    }

    private fun requireWritable() {
        check(canSign) {
            "This Nostr account is read-only. Connect a signer to publish events."
        }
    }

    private fun parseSearchProfilePubkey(raw: String): String? = parsePublicKeyHex(raw)

    private fun parseSearchStatusEventId(raw: String): String? {
        val value = raw.removePrefix("nostr:").trim()
        return when {
            HEX_KEY_REGEX.matches(value) -> {
                value.lowercase()
            }

            else -> {
                withNip19(value) {
                    when (it) {
                        is NNote -> it.hex
                        is NEvent -> it.hex
                        else -> null
                    }
                }
            }
        }
    }

    private fun pTag(pubKey: String): Array<String> = arrayOf("p", pubKey)

    private fun contentWarningTag(contentWarning: String?): List<Array<String>> =
        contentWarning
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { listOf(arrayOf("content-warning", it)) }
            ?: emptyList()

    private fun mediaTags(media: List<UploadedMedia>): List<Array<String>> =
        media.flatMap { item ->
            buildList {
                add(
                    buildList {
                        add("imeta")
                        add("url ${item.url}")
                        add("m ${item.mimeType}")
                        add("x ${item.sha256}")
                        add("size ${item.size}")
                        item.altText?.takeIf { it.isNotBlank() }?.let { add("alt $it") }
                    }.toTypedArray(),
                )
                add(arrayOf("r", item.url))
            }
        }

    private fun contentWithMediaUrls(
        content: String,
        media: List<UploadedMedia>,
    ): String {
        if (media.isEmpty()) {
            return content
        }
        val mediaUrls = media.joinToString(separator = "\n") { it.url }
        return buildString {
            append(content.trimEnd())
            if (isNotEmpty()) {
                append("\n")
            }
            append(mediaUrls)
        }
    }

    private fun eventTemplate(
        kind: Int,
        content: String = "",
        tags: List<Array<String>> = emptyList(),
    ): EventTemplate<QuartzEvent> =
        EventTemplate(
            createdAt = Clock.System.now().epochSeconds,
            kind = kind,
            tags = tags.toTypedArray(),
            content = content,
        )

    private fun textNoteBuilder(
        content: String,
        tags: List<Array<String>> = emptyList(),
    ): EventTemplate<QuartzEvent> = eventTemplate(TextNoteEvent.KIND, content, tags)

    private suspend fun buildBlossomUploadAuthEvent(sha256: String): String {
        requireWritable()
        return signerHandle
            .sign(
                eventTemplate(
                    kind = 24242,
                    tags =
                        listOf(
                            arrayOf("t", "upload"),
                            arrayOf("x", sha256),
                            arrayOf("expiration", (Clock.System.now() + 5.minutes).epochSeconds.toString()),
                        ),
                ),
            ).toJson()
    }

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

            else -> {
                listOf(
                    arrayOf("e", target.id),
                    pTag(target.pubKey),
                )
            }
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
            target != null -> {
                arrayOf("q", target.id, "", target.pubKey)
            }

            cachedAuthorPubKey != null && statusKey.id.length == 64 -> {
                buildList {
                    add("q")
                    add(statusKey.id)
                    add(cachedAuthorPubKey)
                }.toTypedArray()
            }

            else -> {
                null
            }
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
                    is ReactionEvent -> {
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
                    }

                    is RepostEvent -> {
                        current.copy(
                            repostCount = current.repostCount + 1,
                            myRepostEventId =
                                if (event.pubKey == accountPubkey) {
                                    event.id
                                } else {
                                    current.myRepostEventId
                                },
                        )
                    }

                    is GenericRepostEvent -> {
                        current.copy(
                            repostCount = current.repostCount + 1,
                            myRepostEventId =
                                if (event.pubKey == accountPubkey) {
                                    event.id
                                } else {
                                    current.myRepostEventId
                                },
                        )
                    }

                    else -> {
                        current
                    }
                }
            acc
        }
    }

    internal fun List<Event>.toUiTimeline(
        profiles: Map<String, UiProfile>,
        eventsById: Map<String, Event>,
        interactionStats: Map<String, InteractionStats> = emptyMap(),
        renderContexts: Map<String, NostrTextRenderContext> = emptyMap(),
    ): List<UiTimelineV2> {
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
                    is TextNoteEvent -> {
                        event.toUi(
                            profile = profiles.getValue(event.pubKey),
                            eventsById = eventsById,
                            profiles = profiles,
                            interactionStats = interactionStats,
                            renderContext = renderContexts[event.id],
                            visited = nextVisited,
                            resolveEvent = ::resolve,
                        )
                    }

                    is RepostEvent -> {
                        event.resolvedBoostedEvent(eventsById)?.let { resolve(it, nextVisited) }
                    }

                    is GenericRepostEvent -> {
                        event.resolvedBoostedEvent(eventsById)?.let { resolve(it, nextVisited) }
                    }

                    else -> {
                        null
                    }
                }
            if (resolved != null) {
                cache[event.id] = resolved
            }
            return resolved
        }

        return mapNotNull { event ->
            when (event) {
                is TextNoteEvent -> {
                    event.toUiTimelineItem(
                        profile = profiles.getValue(event.pubKey),
                        eventsById = eventsById,
                        profiles = profiles,
                        interactionStats = interactionStats,
                        renderContext = renderContexts[event.id],
                        visited = setOf(event.id),
                        resolveEvent = ::resolve,
                    )
                }

                is RepostEvent -> {
                    event.toUiRepost(
                        profiles = profiles,
                        eventsById = eventsById,
                        visited = setOf(event.id),
                        resolveEvent = ::resolve,
                    )
                }

                is GenericRepostEvent -> {
                    event.toUiGenericRepost(
                        profiles = profiles,
                        eventsById = eventsById,
                        visited = setOf(event.id),
                        resolveEvent = ::resolve,
                    )
                }

                else -> {
                    null
                }
            }
        }
    }

    internal fun List<Event>.toUiNotifications(
        accountPubkey: String,
        profiles: Map<String, UiProfile>,
        eventsById: Map<String, Event>,
        interactionStats: Map<String, InteractionStats> = emptyMap(),
        renderContexts: Map<String, NostrTextRenderContext> = emptyMap(),
    ): List<UiTimelineV2> {
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
                    is TextNoteEvent -> {
                        event.toUi(
                            profile = profiles.getValue(event.pubKey),
                            eventsById = eventsById,
                            profiles = profiles,
                            interactionStats = interactionStats,
                            renderContext = renderContexts[event.id],
                            visited = nextVisited,
                            resolveEvent = ::resolve,
                        )
                    }

                    is RepostEvent -> {
                        event.resolvedBoostedEvent(eventsById)?.let { resolve(it, nextVisited) }
                    }

                    is GenericRepostEvent -> {
                        event.resolvedBoostedEvent(eventsById)?.let { resolve(it, nextVisited) }
                    }

                    else -> {
                        null
                    }
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
        renderContext: NostrTextRenderContext?,
        visited: Set<String>,
        resolveEvent: (Event, Set<String>) -> UiTimelineV2.Post?,
    ): UiTimelineV2.Post {
        val statusKey = MicroBlogKey(id, NOSTR_HOST)
        val stats = interactionStats[id] ?: InteractionStats()
        val actualRenderContext = renderContext ?: buildNostrTextRenderContext(content, tags)
        val contentWarning = contentWarningReason()?.toUiPlainText()?.let { UiTranslatableText(original = it) }
        return UiTimelineV2.Post(
            platformId = NOSTR_PLATFORM_ID,
            images = mediaFromTextAndTags(actualRenderContext.preprocessedText.extractedMediaUrls).toImmutableList(),
            sensitive = false,
            contentWarning = contentWarning,
            user = profile,
            content =
                UiTranslatableText(
                    original = parseNostrRichText(actualRenderContext, accountKey = accountKey, profiles = profiles),
                ),
            actions =
                buildList {
                    if (canSign) {
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
                                actionFamily = PostActionFamily.Reply,
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
                                            actionFamily = PostActionFamily.Quote,
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
                    }
                    add(
                        ActionMenu.Group(
                            displayItem =
                                ActionMenu.Item(
                                    icon = UiIcon.More,
                                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.More),
                                ),
                            actions =
                                buildList {
                                    add(
                                        ActionMenu.Item(
                                            icon = UiIcon.Share,
                                            text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Share),
                                            clickEvent =
                                                ClickEvent.Deeplink(
                                                    DeeplinkRoute.Status.ShareSheet(
                                                        statusKey = statusKey,
                                                        accountType = AccountType.Specific(accountKey),
                                                        shareUrl = statusShareUrl(statusKey.id),
                                                    ),
                                                ),
                                            actionFamily = PostActionFamily.Share,
                                        ),
                                    )
                                    if (canSign && pubKey == accountKey.id) {
                                        add(
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
                                                actionFamily = PostActionFamily.Delete,
                                            ),
                                        )
                                    } else {
                                        add(ActionMenu.Divider)
                                        addAll(
                                            userActionsMenu(
                                                accountKey = accountKey,
                                                userKey = profile.key,
                                                handle = profile.handle.canonical,
                                            ),
                                        )
                                        add(ActionMenu.Divider)
                                        add(
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
                                                actionFamily = PostActionFamily.Report,
                                            ),
                                        )
                                    }
                                }.toImmutableList(),
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
            clickEvent =
                ClickEvent.Deeplink(
                    DeeplinkRoute.Status.Detail(
                        statusKey = statusKey,
                        accountType = AccountType.Specific(accountKey),
                    ),
                ),
            accountType = AccountType.Specific(accountKey),
        )
    }

    private fun TextNoteEvent.toUiTimelineItem(
        profile: UiProfile,
        eventsById: Map<String, Event>,
        profiles: Map<String, UiProfile>,
        interactionStats: Map<String, InteractionStats>,
        renderContext: NostrTextRenderContext?,
        visited: Set<String>,
        resolveEvent: (Event, Set<String>) -> UiTimelineV2.Post?,
        includeReplyParents: Boolean = true,
    ): UiTimelineV2 {
        val post =
            toUi(
                profile = profile,
                eventsById = eventsById,
                profiles = profiles,
                interactionStats = interactionStats,
                renderContext = renderContext,
                visited = visited,
                resolveEvent = resolveEvent,
            )
        val inlineParents =
            parentEventIds()
                .takeIf { includeReplyParents }
                .orEmpty()
                .mapNotNull { parentId ->
                    val event = parentId.takeUnless { it in visited }?.let(eventsById::get) ?: return@mapNotNull null
                    val parent = resolveEvent(event, visited) ?: return@mapNotNull null
                    UiTimelineV2.TimelinePostItem(
                        post = parent,
                        presentation =
                            UiTimelineV2.PostPresentation(
                                quotes =
                                    (event as? TextNoteEvent)
                                        ?.resolveQuotes(
                                            eventsById,
                                            visited + parentId,
                                            resolveEvent,
                                        ).orEmpty()
                                        .toImmutableList(),
                            ),
                    )
                }.toImmutableList()
        val quotes = resolveQuotes(eventsById, visited, resolveEvent)
        return if (inlineParents.isNotEmpty() || quotes.isNotEmpty()) {
            UiTimelineV2.TimelinePostItem(
                post = post,
                presentation =
                    UiTimelineV2.PostPresentation(
                        inlineParents = inlineParents,
                        quotes = quotes,
                    ),
            )
        } else {
            post
        }
    }

    private fun TextNoteEvent.resolveQuotes(
        eventsById: Map<String, Event>,
        visited: Set<String>,
        resolveEvent: (Event, Set<String>) -> UiTimelineV2.Post?,
    ) = (
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
        .toImmutableList()

    private fun statusShareUrl(eventIdHex: String): String = "https://nostter.app/${NNote.create(eventIdHex)}"

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

    private fun TextNoteEvent.contentWarningReason(): String? =
        tags
            .firstOrNull { it.getOrNull(0) == "content-warning" }
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun TextNoteEvent.quoteAddressReferences(): List<Address> =
        tags
            .mapNotNull(QAddressableTag::parse)
            .map { it.address }
            .distinctBy { it.toValue() }

    private fun TextNoteEvent.mediaFromTextAndTags(textMediaUrls: List<String> = emptyList()): List<UiMedia> {
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

        val urlsFromText =
            textMediaUrls
                .filterNot { url -> mediaFromIMeta.any { it.url == url } || urlsFromR.any { it.url == url } }
                .mapNotNull { url -> toUiMedia(url = url, dimensions = null, description = null) }

        return (mediaFromIMeta + urlsFromR + urlsFromText).distinctBy { it.url }
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
            isVideoUrl(url) -> {
                UiMedia.Video(
                    url = url,
                    thumbnailUrl = url,
                    description = description,
                    height = height,
                    width = width,
                )
            }

            isGifUrl(url) -> {
                UiMedia.Gif(
                    url = url,
                    previewUrl = url,
                    description = description,
                    height = height,
                    width = width,
                )
            }

            isAudioUrl(url) -> {
                UiMedia.Audio(
                    url = url,
                    description = description,
                    previewUrl = null,
                )
            }

            isImageUrl(url) -> {
                UiMedia.Image(
                    url = url,
                    previewUrl = url,
                    description = description,
                    height = height,
                    width = width,
                    sensitive = false,
                )
            }

            else -> {
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
    ): UiTimelineV2? {
        val boostedEvent = resolvedBoostedEvent(eventsById)
        val boostedPost = boostedEvent?.let { resolveEvent(it, visited) } ?: return null
        val actor = profiles.getValue(pubKey)
        val wrapperStatusKey = MicroBlogKey(id, NOSTR_HOST)
        val wrapperPost = boostedPost.toRepostWrapperPost(actor, wrapperStatusKey, createdAt)
        return UiTimelineV2.TimelinePostItem(
            post = wrapperPost,
            presentation =
                UiTimelineV2.PostPresentation(
                    message =
                        repostMessage(
                            actor = actor,
                            statusKey = wrapperStatusKey,
                            createdAt = createdAt,
                        ),
                    repost = boostedPost,
                ),
        )
    }

    private fun GenericRepostEvent.toUiGenericRepost(
        profiles: Map<String, UiProfile>,
        eventsById: Map<String, Event>,
        visited: Set<String>,
        resolveEvent: (Event, Set<String>) -> UiTimelineV2.Post?,
    ): UiTimelineV2? {
        val boostedEvent = resolvedBoostedEvent(eventsById)
        val boostedPost = boostedEvent?.let { resolveEvent(it, visited) } ?: return null
        val actor = profiles.getValue(pubKey)
        val wrapperStatusKey = MicroBlogKey(id, NOSTR_HOST)
        val wrapperPost = boostedPost.toRepostWrapperPost(actor, wrapperStatusKey, createdAt)
        return UiTimelineV2.TimelinePostItem(
            post = wrapperPost,
            presentation =
                UiTimelineV2.PostPresentation(
                    message =
                        repostMessage(
                            actor = actor,
                            statusKey = wrapperStatusKey,
                            createdAt = createdAt,
                        ),
                    repost = boostedPost,
                ),
        )
    }

    private fun UiTimelineV2.Post.toRepostWrapperPost(
        actor: UiProfile,
        wrapperStatusKey: MicroBlogKey,
        createdAt: Long,
    ): UiTimelineV2.Post =
        copy(
            statusKey = wrapperStatusKey,
            user = actor,
            createdAt = Instant.fromEpochSeconds(createdAt).toUi(),
            images = persistentListOf(),
            contentWarning = null,
            content = UiTranslatableText(original = uiRichTextOf(emptyList())),
            poll = null,
            card = null,
            references =
                listOf(
                    UiTimelineV2.Post.Reference(
                        statusKey = statusKey,
                        type = ReferenceType.Retweet,
                    ),
                ).toImmutableList(),
        )

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

    private fun UiTimelineV2.withPresentationMessage(message: UiTimelineV2.Message): UiTimelineV2 {
        val post = asTimelinePostItem() ?: return this
        return post.copy(
            presentation =
                post.presentation.copy(
                    message = message,
                    notificationKey = message.statusKey,
                ),
        )
    }

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
    ): UiTimelineV2? =
        when (this) {
            is TextNoteEvent -> {
                val post =
                    toUiTimelineItem(
                        profile = profiles.getValue(pubKey),
                        eventsById = eventsById,
                        profiles = profiles,
                        interactionStats = interactionStats,
                        renderContext = null,
                        visited = setOf(id),
                        resolveEvent = { event, visited -> resolveEvent(event, visited) },
                        includeReplyParents = false,
                    )
                val hasParent = parentEventIds().isNotEmpty()
                post.withPresentationMessage(
                    notificationMessage(
                        actor =
                            post.asTimelinePostItem()?.displayPost?.user ?: profiles.getValue(pubKey),
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
                target.withPresentationMessage(
                    notificationMessage(
                        actor = profiles.getValue(pubKey),
                        statusKey = MicroBlogKey(id, NOSTR_HOST),
                        createdAt = createdAt,
                        icon = UiIcon.Like,
                        type =
                            UiTimelineV2.Message.Type.Localized(
                                UiTimelineV2.Message.Type.Localized.MessageId.Like,
                            ),
                    ),
                )
            }

            is RepostEvent -> {
                if (accountPubkey !in taggedUsers()) {
                    return null
                }
                val boosted =
                    resolvedBoostedEvent(eventsById)?.let { resolveEvent(it, setOf(id)) }
                        ?: return null
                val actor = profiles.getValue(pubKey)
                val wrapperStatusKey = MicroBlogKey(id, NOSTR_HOST)
                UiTimelineV2.TimelinePostItem(
                    post = boosted.toRepostWrapperPost(actor, wrapperStatusKey, createdAt),
                    presentation =
                        UiTimelineV2.PostPresentation(
                            message =
                                notificationMessage(
                                    actor = actor,
                                    statusKey = wrapperStatusKey,
                                    createdAt = createdAt,
                                    icon = UiIcon.Retweet,
                                    type =
                                        UiTimelineV2.Message.Type.Localized(
                                            UiTimelineV2.Message.Type.Localized.MessageId.Repost,
                                        ),
                                ),
                            repost = boosted,
                            notificationKey = wrapperStatusKey,
                        ),
                )
            }

            is GenericRepostEvent -> {
                if (accountPubkey !in taggedUsers()) {
                    return null
                }
                val boosted =
                    resolvedBoostedEvent(eventsById)?.let { resolveEvent(it, setOf(id)) }
                        ?: return null
                val actor = profiles.getValue(pubKey)
                val wrapperStatusKey = MicroBlogKey(id, NOSTR_HOST)
                UiTimelineV2.TimelinePostItem(
                    post = boosted.toRepostWrapperPost(actor, wrapperStatusKey, createdAt),
                    presentation =
                        UiTimelineV2.PostPresentation(
                            message =
                                notificationMessage(
                                    actor = actor,
                                    statusKey = wrapperStatusKey,
                                    createdAt = createdAt,
                                    icon = UiIcon.Retweet,
                                    type =
                                        UiTimelineV2.Message.Type.Localized(
                                            UiTimelineV2.Message.Type.Localized.MessageId.Repost,
                                        ),
                                ),
                            repost = boosted,
                            notificationKey = wrapperStatusKey,
                        ),
                )
            }

            else -> {
                null
            }
        }

    private suspend fun loadEventGraph(
        roots: List<Event>,
        includeReplyParents: Boolean = true,
    ): Map<String, Event> {
        val eventsById = LinkedHashMap<String, Event>()
        val pendingEventIds = LinkedHashSet<String>()
        val pendingAddresses = LinkedHashMap<String, Address>()

        fun register(event: Event) {
            if (eventsById.containsKey(event.id)) {
                return
            }
            eventsById[event.id] = event
            embeddedEvents(event).forEach(::register)
            referencedEventIds(event, includeReplyParents)
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

    internal fun referencedEventIds(
        event: Event,
        includeReplyParents: Boolean,
    ): List<String> =
        when (event) {
            is TextNoteEvent -> event.parentEventIds().takeIf { includeReplyParents }.orEmpty() + event.quoteEventIds()
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

    internal fun profileOf(
        pubKey: String,
        metadata: UserMetadata?,
        cachedProfile: UiProfile?,
    ): UiProfile {
        val bestName = metadata?.bestName().orEmpty()
        val npub = nostrBech32PublicKey(pubKey)
        val defaultHandle = npub.take(16)
        val handleRaw =
            metadata?.name?.takeIf { it.isNotBlank() }
                ?: metadata?.nip05?.substringBefore("@")?.takeIf { it.isNotBlank() }
                ?: bestName.takeIf { it.isNotBlank() }
                ?: cachedProfile?.handle?.raw?.takeIf { it.isNotBlank() }
                ?: defaultHandle
        val name =
            bestName.takeIf { it.isNotBlank() }
                ?: cachedProfile?.name?.raw.orEmpty()
        return UiProfile(
            key = MicroBlogKey(pubKey, NOSTR_HOST),
            handle =
                UiHandle(
                    raw = handleRaw,
                    host = NOSTR_HOST,
                ),
            avatar = metadata?.picture.toUiImage(),
            nameInternal = name.toUiPlainText(),
            platformId = NOSTR_PLATFORM_ID,
            platformIcon = dev.dimension.flare.ui.model.UiIcon.Nostr,
            clickEvent =
                ClickEvent.Deeplink(
                    DeeplinkRoute.Profile.User(
                        accountType = AccountType.Specific(accountKey),
                        userKey = MicroBlogKey(pubKey, NOSTR_HOST),
                    ),
                ),
            banner = metadata?.banner.toUiImage(),
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
            dev.dimension.flare.data.datasource.microblog.NotificationFilter.All -> {
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
            }

            dev.dimension.flare.data.datasource.microblog.NotificationFilter.Mention -> {
                listOf(
                    Filter(
                        kinds = listOf(TextNoteEvent.KIND),
                        tags = mapOf("p" to listOf(accountPubkey)),
                        until = until,
                        limit = pageSize,
                    ),
                )
            }

            else -> {
                emptyList()
            }
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
