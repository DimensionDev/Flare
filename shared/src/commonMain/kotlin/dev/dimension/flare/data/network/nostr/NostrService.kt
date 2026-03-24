package dev.dimension.flare.data.network.nostr

import com.vitorpamplona.quartz.nip01Core.core.Address
import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.hints.EventHintBundle
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.metadata.UserMetadata
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip01Core.tags.dTag.dTag
import com.vitorpamplona.quartz.nip01Core.tags.events.ETag
import com.vitorpamplona.quartz.nip01Core.tags.people.pTag
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip09Deletions.DeletionEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip10Notes.tags.MarkedETag
import com.vitorpamplona.quartz.nip18Reposts.GenericRepostEvent
import com.vitorpamplona.quartz.nip18Reposts.RepostEvent
import com.vitorpamplona.quartz.nip18Reposts.quotes.QAddressableTag
import com.vitorpamplona.quartz.nip18Reposts.quotes.QEventTag
import com.vitorpamplona.quartz.nip18Reposts.quotes.toQTagArray
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NEvent
import com.vitorpamplona.quartz.nip19Bech32.entities.NNote
import com.vitorpamplona.quartz.nip19Bech32.entities.NProfile
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.entities.NSec
import com.vitorpamplona.quartz.nip19Bech32.toNsec
import com.vitorpamplona.quartz.nip25Reactions.ReactionEvent
import com.vitorpamplona.quartz.nip56Reports.ReportEvent
import com.vitorpamplona.quartz.nip56Reports.ReportType
import com.vitorpamplona.quartz.nip92IMeta.IMetaTag
import com.vitorpamplona.quartz.nip94FileMetadata.tags.DimensionTag
import dev.dimension.flare.common.JSON
import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.datasource.nostr.NostrCache
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.ReferenceType
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiMedia
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
import dev.whyoleg.cryptography.random.CryptographyRandom
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.time.Clock
import kotlin.time.Instant

public val defaultNostrRelays: List<String> =
    listOf(
        "wss://relay.damus.io",
        "wss://nos.lol",
        "wss://relay.primal.net",
        "wss://relay.snort.social",
        "wss://relay.nostr.band",
        "wss://offchain.pub",
        "wss://purplepag.es",
    )

internal object NostrService : KoinComponent {
    internal const val NOSTR_HOST: String = "nostr"

    internal val defaultRelays: List<String> = defaultNostrRelays
    private val cache: NostrCache by inject()
    private val client by lazy {
        ktorClient {
            install(WebSockets)
        }
    }

    internal data class ImportedAccount(
        val pubkeyHex: String,
        val npub: String,
        val nsec: String?,
        val relays: List<String>,
    )

    internal fun importAccount(
        publicKeyInput: String,
        secretKeyInput: String,
        relayInput: String,
    ): ImportedAccount {
        val normalizedSecret = secretKeyInput.trim().takeIf { it.isNotEmpty() }?.let(::normalizeSecret)
        val normalizedPublic = publicKeyInput.trim().takeIf { it.isNotEmpty() }?.let(::normalizePublic)

        val secretHex = normalizedSecret?.hex
        val explicitPubkeyHex = normalizedPublic?.hex
        val derivedPubkeyHex = secretHex?.let { NSec(it).toPubKeyHex() }
        val pubkeyHex = explicitPubkeyHex ?: derivedPubkeyHex ?: error("A public key or secret key is required")
        if (explicitPubkeyHex != null && derivedPubkeyHex != null) {
            require(explicitPubkeyHex == derivedPubkeyHex) {
                "Public key does not match the provided secret key"
            }
        }

        val normalizedRelays = normalizeRelays(relayInput)
        return ImportedAccount(
            pubkeyHex = pubkeyHex,
            npub = NPub.Companion.create(pubkeyHex),
            nsec = secretHex?.hexToByteArray()?.toNsec(),
            relays = normalizedRelays,
        )
    }

    internal fun generateAccount(relayInput: String): ImportedAccount {
        while (true) {
            val secretHex = CryptographyRandom.nextBytes(32).toHexString()
            runCatching {
                return importAccount(
                    publicKeyInput = "",
                    secretKeyInput = secretHex,
                    relayInput = relayInput,
                )
            }
        }
    }

    internal fun exportAccount(credential: UiAccount.Nostr.Credential): ImportedAccount {
        val secretKey =
            requireNotNull(credential.nsec) {
                "Nostr account does not have an exportable private key"
            }
        return importAccount(
            publicKeyInput = credential.pubkey,
            secretKeyInput = secretKey,
            relayInput = credential.relays.joinToString(","),
        )
    }

    internal suspend fun loadHomeTimeline(
        credential: UiAccount.Nostr.Credential,
        accountKey: MicroBlogKey,
        pageSize: Int,
        until: Long?,
    ): List<UiTimelineV2> {
        val relays = credential.relays.ifEmpty { defaultRelays }
        val authors = loadAuthors(relays, credential.pubkey)
        val events =
            queryFirstRelay(
                relays = relays,
                filters =
                    listOf(
                        Filter(
                            authors = authors,
                            kinds = timelineEventKinds,
                            until = until,
                            limit = pageSize,
                        ),
                    ),
                minEventsBeforeReturn = pageSize.coerceAtMost(MIN_EARLY_RETURN_EVENTS).coerceAtLeast(1),
            ).filter(::isTimelineRootEvent)
                .sortedByDescending { it.createdAt }

        if (events.isEmpty()) {
            return emptyList()
        }

        val eventGraph = loadEventGraph(relays = relays, roots = events)
        val interactionStats =
            loadInteractionStats(
                relays = relays,
                accountPubkey = credential.pubkey,
                targetEventIds = eventGraph.keys.toList(),
            )

        val profiles =
            loadProfiles(
                relays = relays,
                pubKeys = eventGraph.values.map { it.pubKey }.distinct(),
                accountKey = accountKey,
            )
        return events.toUiTimeline(
            accountKey = accountKey,
            profiles = profiles,
            eventsById = eventGraph,
            interactionStats = interactionStats,
        )
    }

    internal suspend fun loadProfile(
        credential: UiAccount.Nostr.Credential,
        accountKey: MicroBlogKey,
        targetPubkey: String,
    ): UiProfile {
        val relays = credential.relays.ifEmpty { defaultRelays }
        val metadata =
            loadMetadata(
                relays = relays,
                authors = listOf(targetPubkey),
            )[targetPubkey]
        return profileOf(
            pubKey = targetPubkey,
            metadata = metadata,
            accountKey = accountKey,
        )
    }

    internal suspend fun loadUserTimeline(
        credential: UiAccount.Nostr.Credential,
        accountKey: MicroBlogKey,
        targetPubkey: String,
        pageSize: Int,
        until: Long?,
        mediaOnly: Boolean,
    ): List<UiTimelineV2> {
        if (mediaOnly) {
            return emptyList()
        }
        val relays = credential.relays.ifEmpty { defaultRelays }
        val events =
            queryAllRelays(
                relays = relays,
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
        val eventGraph = loadEventGraph(relays = relays, roots = events)
        val interactionStats =
            loadInteractionStats(
                relays = relays,
                accountPubkey = credential.pubkey,
                targetEventIds = eventGraph.keys.toList(),
            )
        val profiles =
            loadProfiles(
                relays = relays,
                pubKeys = eventGraph.values.map { it.pubKey }.distinct(),
                accountKey = accountKey,
            )
        return events.toUiTimeline(
            accountKey = accountKey,
            profiles = profiles,
            eventsById = eventGraph,
            interactionStats = interactionStats,
        )
    }

    internal suspend fun searchStatus(
        credential: UiAccount.Nostr.Credential,
        accountKey: MicroBlogKey,
        query: String,
        pageSize: Int,
        until: Long?,
    ): List<UiTimelineV2> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) {
            return emptyList()
        }
        val relays = credential.relays.ifEmpty { defaultRelays }
        parseSearchStatusEventId(normalizedQuery)?.let { eventId ->
            return runCatching {
                listOf(
                    loadStatus(
                        credential = credential,
                        accountKey = accountKey,
                        statusKey = MicroBlogKey(eventId, NOSTR_HOST),
                    ),
                )
            }.getOrDefault(emptyList())
        }

        val events =
            queryAllRelays(
                relays = relays,
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
                    event is TextNoteEvent && event.content.contains(normalizedQuery, ignoreCase = true)
                }.sortedByDescending { it.createdAt }
                .take(pageSize)
        if (events.isEmpty()) {
            return emptyList()
        }

        val eventGraph = loadEventGraph(relays = relays, roots = events)
        val interactionStats =
            loadInteractionStats(
                relays = relays,
                accountPubkey = credential.pubkey,
                targetEventIds = eventGraph.keys.toList(),
            )
        val profiles =
            loadProfiles(
                relays = relays,
                pubKeys = eventGraph.values.map { it.pubKey }.distinct(),
                accountKey = accountKey,
            )
        return events.toUiTimeline(
            accountKey = accountKey,
            profiles = profiles,
            eventsById = eventGraph,
            interactionStats = interactionStats,
        )
    }

    internal suspend fun searchUser(
        credential: UiAccount.Nostr.Credential,
        accountKey: MicroBlogKey,
        query: String,
        pageSize: Int,
    ): List<UiProfile> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) {
            return emptyList()
        }
        val relays = credential.relays.ifEmpty { defaultRelays }
        parseSearchProfilePubkey(normalizedQuery)?.let { pubkey ->
            return listOf(
                loadProfile(
                    credential = credential,
                    accountKey = accountKey,
                    targetPubkey = pubkey,
                ),
            )
        }

        val metadataEvents =
            queryAllRelays(
                relays = relays,
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
                    accountKey = accountKey,
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
        credential: UiAccount.Nostr.Credential,
        accountKey: MicroBlogKey,
        pageSize: Int,
        until: Long?,
        type: dev.dimension.flare.data.datasource.microblog.NotificationFilter,
    ): List<UiTimelineV2> {
        val relays = credential.relays.ifEmpty { defaultRelays }
        val events =
            notificationFilters(
                accountPubkey = credential.pubkey,
                pageSize = pageSize,
                until = until,
                type = type,
            ).flatMap { filter ->
                queryAllRelays(
                    relays = relays,
                    filters = listOf(filter),
                )
            }.distinctBy { it.id }
                .filterNot { it.pubKey == credential.pubkey }
                .sortedByDescending { it.createdAt }
                .take(pageSize)
        if (events.isEmpty()) {
            return emptyList()
        }

        val eventGraph = loadEventGraph(relays = relays, roots = events)
        val interactionStats =
            loadInteractionStats(
                relays = relays,
                accountPubkey = credential.pubkey,
                targetEventIds = eventGraph.keys.toList(),
            )
        val profiles =
            loadProfiles(
                relays = relays,
                pubKeys = eventGraph.values.map { it.pubKey }.distinct(),
                accountKey = accountKey,
            )
        return events.toUiNotifications(
            accountKey = accountKey,
            accountPubkey = credential.pubkey,
            profiles = profiles,
            eventsById = eventGraph,
            interactionStats = interactionStats,
        )
    }

    internal suspend fun loadStatus(
        credential: UiAccount.Nostr.Credential,
        accountKey: MicroBlogKey,
        statusKey: MicroBlogKey,
    ): UiTimelineV2 {
        val relays = credential.relays.ifEmpty { defaultRelays }
        val event =
            loadEvent(
                relays = relays,
                statusKey = statusKey,
            ) ?: error("Nostr status not found: $statusKey")
        val eventGraph = loadEventGraph(relays = relays, roots = listOf(event))
        val interactionStats =
            loadInteractionStats(
                relays = relays,
                accountPubkey = credential.pubkey,
                targetEventIds = eventGraph.keys.toList(),
            )
        val profiles =
            loadProfiles(
                relays = relays,
                pubKeys = eventGraph.values.map { it.pubKey }.distinct(),
                accountKey = accountKey,
            )
        return listOf(event)
            .toUiTimeline(
                accountKey = accountKey,
                profiles = profiles,
                eventsById = eventGraph,
                interactionStats = interactionStats,
            ).first()
    }

    internal suspend fun loadStatusContext(
        credential: UiAccount.Nostr.Credential,
        accountKey: MicroBlogKey,
        statusKey: MicroBlogKey,
        pageSize: Int,
    ): List<UiTimelineV2.Post> {
        val relays = credential.relays.ifEmpty { defaultRelays }
        val event =
            loadEvent(
                relays = relays,
                statusKey = statusKey,
            ) ?: error("Nostr status not found: $statusKey")

        val ancestorEvents = buildAncestorChain(event = event, relays = relays)
        val replyEvents =
            loadDirectReplies(
                relays = relays,
                statusKey = statusKey,
                pageSize = pageSize,
            )
        val threadEvents = (ancestorEvents + event + replyEvents).distinctBy(Event::id)
        if (threadEvents.isEmpty()) {
            return emptyList()
        }

        val eventGraph = loadEventGraph(relays = relays, roots = threadEvents)
        val interactionStats =
            loadInteractionStats(
                relays = relays,
                accountPubkey = credential.pubkey,
                targetEventIds = eventGraph.keys.toList(),
            )
        val profiles =
            loadProfiles(
                relays = relays,
                pubKeys = eventGraph.values.map { it.pubKey }.distinct(),
                accountKey = accountKey,
            )

        return threadEvents.toUiTimeline(
            accountKey = accountKey,
            profiles = profiles,
            eventsById = eventGraph,
            interactionStats = interactionStats,
        )
    }

    internal suspend fun relation(
        credential: UiAccount.Nostr.Credential,
        targetPubkey: String,
    ): dev.dimension.flare.ui.model.UiRelation {
        val relays = credential.relays.ifEmpty { defaultRelays }
        val follows = loadAuthors(relays, credential.pubkey)
        return dev.dimension.flare.ui.model.UiRelation(
            following = targetPubkey in follows,
        )
    }

    internal fun createTextNoteEvent(
        secretKey: String,
        content: String,
        createdAt: Long = Clock.System.now().toEpochMilliseconds() / 1000,
    ): TextNoteEvent {
        val imported = importAccount(publicKeyInput = "", secretKeyInput = secretKey, relayInput = "")
        return signEvent(
            pubkeyHex = imported.pubkeyHex,
            secretKey = requireNotNull(imported.nsec),
            template = TextNoteEvent.Companion.build(content, createdAt) {},
        )
    }

    internal suspend fun composeNote(
        credential: UiAccount.Nostr.Credential,
        content: String,
    ): String {
        val imported = exportAccount(credential)
        val event =
            signEvent(
                pubkeyHex = imported.pubkeyHex,
                secretKey = requireNotNull(imported.nsec),
                template = TextNoteEvent.build(content),
            )
        publishEvent(credential, event)
        return event.id
    }

    internal suspend fun composeReply(
        credential: UiAccount.Nostr.Credential,
        statusKey: MicroBlogKey,
        content: String,
    ): String {
        val relays = credential.relays.ifEmpty { defaultRelays }
        val target =
            loadEvent(relays = relays, statusKey = statusKey)
                ?: error("Reply target not found: $statusKey")
        val imported = exportAccount(credential)
        val template =
            if (target is TextNoteEvent) {
                TextNoteEvent.build(
                    note = content,
                    replyingTo = EventHintBundle(target),
                )
            } else {
                TextNoteEvent.build(content) {
                    add(ETag(target.id).toTagArray())
                    pTag(target.pubKey)
                }
            }
        val event =
            signEvent(
                pubkeyHex = imported.pubkeyHex,
                secretKey = requireNotNull(imported.nsec),
                template = template,
            )
        publishEvent(credential, event)
        return event.id
    }

    internal suspend fun composeQuote(
        accountKey: MicroBlogKey,
        credential: UiAccount.Nostr.Credential,
        statusKey: MicroBlogKey,
        content: String,
    ): String {
        val relays = credential.relays.ifEmpty { defaultRelays }
        val target =
            loadEvent(relays = relays, statusKey = statusKey)
        val cachedPost = target?.let { null } ?: cache.getPost(accountKey = accountKey, statusKey = statusKey)
        val quoteTag =
            quoteTagArray(
                target = target,
                statusKey = statusKey,
                relayHint = relays.firstNotNullOfOrNull(RelayUrlNormalizer::normalizeOrNull),
                cachedAuthorPubKey = cachedPost?.user?.key?.id,
            ) ?: error("Quote target not found: $statusKey")
        val authorPubKey = target?.pubKey ?: cachedPost?.user?.key?.id ?: error("Quote target not found: $statusKey")
        val imported = exportAccount(credential)
        val event =
            signEvent(
                pubkeyHex = imported.pubkeyHex,
                secretKey = requireNotNull(imported.nsec),
                template =
                    TextNoteEvent.build(content) {
                        add(quoteTag)
                        pTag(authorPubKey)
                    },
            )
        publishEvent(credential, event)
        return event.id
    }

    internal suspend fun repost(
        credential: UiAccount.Nostr.Credential,
        statusKey: MicroBlogKey,
    ): String {
        val relays = credential.relays.ifEmpty { defaultRelays }
        val target =
            loadEvent(relays = relays, statusKey = statusKey)
                ?: error("Repost target not found: $statusKey")
        val imported = exportAccount(credential)
        val event =
            signEvent(
                pubkeyHex = imported.pubkeyHex,
                secretKey = requireNotNull(imported.nsec),
                template = GenericRepostEvent.build(target, null, null),
            )
        publishEvent(credential, event)
        return event.id
    }

    internal suspend fun react(
        credential: UiAccount.Nostr.Credential,
        statusKey: MicroBlogKey,
    ): String {
        val relays = credential.relays.ifEmpty { defaultRelays }
        val target =
            loadEvent(relays = relays, statusKey = statusKey)
                ?: error("Reaction target not found: $statusKey")
        val imported = exportAccount(credential)
        val event =
            signEvent(
                pubkeyHex = imported.pubkeyHex,
                secretKey = requireNotNull(imported.nsec),
                template = ReactionEvent.like(EventHintBundle(target)),
            )
        publishEvent(credential, event)
        return event.id
    }

    internal suspend fun report(
        credential: UiAccount.Nostr.Credential,
        statusKey: MicroBlogKey,
    ) {
        val relays = credential.relays.ifEmpty { defaultRelays }
        val target =
            loadEvent(relays = relays, statusKey = statusKey)
                ?: error("Report target not found: $statusKey")
        val imported = exportAccount(credential)
        val event =
            signEvent(
                pubkeyHex = imported.pubkeyHex,
                secretKey = requireNotNull(imported.nsec),
                template = ReportEvent.build(target, ReportType.SPAM),
            )
        publishEvent(credential, event)
    }

    internal suspend fun deleteStatus(
        credential: UiAccount.Nostr.Credential,
        statusKey: MicroBlogKey,
    ) {
        val relays = credential.relays.ifEmpty { defaultRelays }
        val target =
            loadEvent(relays = relays, statusKey = statusKey)
                ?: error("Delete target not found: $statusKey")
        val imported = exportAccount(credential)
        val deletionEvent =
            signEvent(
                pubkeyHex = imported.pubkeyHex,
                secretKey = requireNotNull(imported.nsec),
                template = DeletionEvent.build(listOf(target)),
            )
        publishEvent(credential, deletionEvent)
    }

    private suspend fun loadAuthors(
        relays: List<String>,
        accountPubkey: String,
    ): List<String> {
        val latestContacts =
            queryAllRelays(
                relays = relays,
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

        return (latestContacts?.verifiedFollowKeySet().orEmpty() + accountPubkey)
            .distinct()
            .take(MAX_HOME_AUTHORS)
    }

    private suspend fun loadMetadata(
        relays: List<String>,
        authors: List<String>,
    ): Map<String, UserMetadata> {
        if (authors.isEmpty()) {
            return emptyMap()
        }
        val eventsByPubkey =
            queryAllRelays(
                relays = relays,
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

    private suspend fun loadProfiles(
        relays: List<String>,
        pubKeys: List<String>,
        accountKey: MicroBlogKey,
    ): Map<String, UiProfile> {
        if (pubKeys.isEmpty()) {
            return emptyMap()
        }
        val cachedProfiles = cache.getProfiles(pubKeys)

        val missingPubKeys = pubKeys.distinct().filterNot { it in cachedProfiles }
        if (missingPubKeys.isEmpty()) {
            return cachedProfiles
        }

        val fetchedProfiles =
            loadMetadata(relays, missingPubKeys)
                .let { metadata ->
                    missingPubKeys.associateWith { pubKey ->
                        profileOf(
                            pubKey = pubKey,
                            metadata = metadata[pubKey],
                            accountKey = accountKey,
                        )
                    }
                }

        return cachedProfiles + fetchedProfiles
    }

    private suspend fun queryFirstRelay(
        relays: List<String>,
        filters: List<Filter>,
        minEventsBeforeReturn: Int = MIN_EARLY_RETURN_EVENTS,
    ): List<Event> =
        queryRelays(
            relays = relays,
            filters = filters,
            waitForAllRelays = false,
            minEventsBeforeReturn = minEventsBeforeReturn,
        )

    private suspend fun queryAllRelays(
        relays: List<String>,
        filters: List<Filter>,
    ): List<Event> =
        queryRelays(
            relays = relays,
            filters = filters,
            waitForAllRelays = true,
            minEventsBeforeReturn = null,
        )

    private suspend fun queryRelays(
        relays: List<String>,
        filters: List<Filter>,
        waitForAllRelays: Boolean,
        minEventsBeforeReturn: Int?,
    ): List<Event> =
        coroutineScope {
            if (relays.isEmpty()) {
                return@coroutineScope emptyList()
            }
            val results = Channel<List<Event>>(Channel.UNLIMITED)
            relays.forEach { relay ->
                launch {
                    val events =
                        withTimeoutOrNull(RELAY_TIMEOUT_MILLIS + RELAY_SETTLE_TIMEOUT_MILLIS) {
                            runCatching { queryRelay(relay, filters) }.getOrDefault(emptyList())
                        } ?: emptyList()
                    results.send(events)
                }
            }

            val eventsById = LinkedHashMap<String, Event>()
            var remaining = relays.size
            var hasNonEmptyResult = false

            try {
                while (remaining > 0) {
                    val result =
                        if (waitForAllRelays) {
                            results.receive()
                        } else {
                            val timeout =
                                if (hasNonEmptyResult) {
                                    RELAY_SETTLE_TIMEOUT_MILLIS
                                } else {
                                    RELAY_TIMEOUT_MILLIS
                                }
                            withTimeoutOrNull(timeout) {
                                results.receive()
                            } ?: break
                        }
                    remaining -= 1
                    if (result.isEmpty()) {
                        continue
                    }
                    hasNonEmptyResult = true
                    result.forEach { event ->
                        if (event.id !in eventsById) {
                            eventsById[event.id] = event
                        }
                    }
                    if (!waitForAllRelays) {
                        if (eventsById.size >= (minEventsBeforeReturn ?: 1)) {
                            break
                        }
                    }
                }
            } finally {
                coroutineContext.cancelChildren()
                results.close()
            }
            eventsById.values.toList()
        }

    private suspend fun queryRelay(
        relay: String,
        filters: List<Filter>,
    ): List<Event> {
        val session = client.webSocketSession(urlString = relay)
        val subscriptionId = "flare-${Clock.System.now().toEpochMilliseconds()}"
        val events = mutableListOf<Event>()
        return try {
            session.send(
                Frame.Text(
                    buildString {
                        append("[\"REQ\",\"")
                        append(subscriptionId)
                        append("\"")
                        filters.forEach { filter ->
                            append(",")
                            append(filter.toJson())
                        }
                        append("]")
                    },
                ),
            )
            withTimeoutOrNull(RELAY_TIMEOUT_MILLIS) {
                while (true) {
                    when (val frame = session.incoming.receive()) {
                        is Frame.Text -> {
                            when (val message = parseRelayMessage(frame.data.decodeToString())) {
                                RelayMessage.EndOfStoredEvents -> break
                                is RelayMessage.EventEnvelope -> events += message.event
                                else -> Unit
                            }
                        }

                        else -> Unit
                    }
                }
            }
            events
        } finally {
            runCatching { session.send(Frame.Text("[\"CLOSE\",\"$subscriptionId\"]")) }
            runCatching { session.close() }
        }
    }

    private const val MIN_EARLY_RETURN_EVENTS = 4

    private suspend fun publishEvent(
        credential: UiAccount.Nostr.Credential,
        event: Event,
    ) {
        val relays = credential.relays.ifEmpty { defaultRelays }
        val requiredSuccessCount = minOf(PUBLISH_SUCCESS_QUORUM, relays.size)
        if (requiredSuccessCount == 0) {
            return
        }
        supervisorScope {
            val results = Channel<Result<Unit>>(Channel.UNLIMITED)
            relays.forEach { relay ->
                launch {
                    results.send(
                        runCatching {
                            publishEventToRelay(
                                relay = relay,
                                event = event,
                            )
                        },
                    )
                }
            }
            var successCount = 0
            var failureCount = 0
            val failures = mutableListOf<Throwable>()
            try {
                repeat(relays.size) {
                    val result = results.receive()
                    result
                        .onSuccess {
                            successCount += 1
                            if (successCount >= requiredSuccessCount) {
                                return@supervisorScope
                            }
                        }.onFailure {
                            failureCount += 1
                            failures += it
                            val remainingRelays = relays.size - successCount - failureCount
                            if (successCount + remainingRelays < requiredSuccessCount) {
                                throw PublishToRelayException(
                                    requiredSuccessCount = requiredSuccessCount,
                                    successCount = successCount,
                                    failures = failures.toList(),
                                )
                            }
                        }
                }
            } finally {
                coroutineContext.cancelChildren()
                results.close()
            }
        }
    }

    private suspend fun publishEventToRelay(
        relay: String,
        event: Event,
    ) {
        val session = client.webSocketSession(urlString = relay)
        try {
            session.send(
                Frame.Text(
                    buildString {
                        append("[\"EVENT\",")
                        append(event.toJson())
                        append("]")
                    },
                ),
            )
            val ack =
                awaitRelayAck(
                    session = session,
                    eventId = event.id,
                ) ?: throw IllegalStateException("Timed out waiting for relay OK: $relay")
            if (!ack.accepted) {
                throw IllegalStateException("Relay rejected event: $relay${ack.message?.let { " ($it)" } ?: ""}")
            }
        } finally {
            runCatching { session.close() }
        }
    }

    private suspend fun awaitRelayAck(
        session: DefaultClientWebSocketSession,
        eventId: String,
    ): RelayMessage.OkEnvelope? =
        withTimeoutOrNull(RELAY_PUBLISH_TIMEOUT_MILLIS) {
            while (true) {
                when (val frame = session.incoming.receive()) {
                    is Frame.Text -> {
                        val message = parseRelayMessage(frame.data.decodeToString())
                        if (message is RelayMessage.OkEnvelope && message.eventId == eventId) {
                            return@withTimeoutOrNull message
                        }
                    }

                    else -> Unit
                }
            }
            null
        }

    private fun normalizeRelays(relayInput: String): List<String> {
        val candidates =
            relayInput
                .split(Regex("[,\\n\\r\\t ]+"))
                .map(String::trim)
                .filter(String::isNotEmpty)
                .ifEmpty { defaultRelays }

        return candidates
            .map {
                RelayUrlNormalizer.Companion.normalizeOrNull(it)?.url
                    ?: error("Invalid relay URL: $it")
            }.distinct()
    }

    private const val PUBLISH_SUCCESS_QUORUM = 3

    private class PublishToRelayException(
        requiredSuccessCount: Int,
        successCount: Int,
        val failures: List<Throwable>,
    ) : Exception(
            "Failed to publish event to enough relays: $successCount/$requiredSuccessCount succeeded.",
        )

    private fun normalizeSecret(raw: String): NSec {
        val value = raw.removePrefix("nostr:").trim()
        return when {
            value.startsWith("nsec1", ignoreCase = true) -> {
                Nip19Parser
                    .parseAll(value)
                    .singleOrNull()
                    ?.let { it as? NSec }
                    ?: error("Invalid NIP-19 secret key")
            }

            HEX_KEY_REGEX.matches(value) -> NSec(value)

            else -> error("Unsupported secret key format")
        }
    }

    private fun normalizePublic(raw: String): NPub {
        val value = raw.removePrefix("nostr:").trim()
        return when {
            value.startsWith("npub1", ignoreCase = true) -> {
                Nip19Parser
                    .parseAll(value)
                    .singleOrNull()
                    ?.let { it as? NPub }
                    ?: error("Invalid NIP-19 public key")
            }

            HEX_KEY_REGEX.matches(value) -> NPub(value)

            else -> error("Unsupported public key format")
        }
    }

    private fun parseSearchProfilePubkey(raw: String): String? {
        val value = raw.removePrefix("nostr:").trim()
        return when {
            value.startsWith("npub1", ignoreCase = true) ->
                (Nip19Parser.parseAll(value).singleOrNull() as? NPub)?.hex

            value.startsWith("nprofile1", ignoreCase = true) ->
                (Nip19Parser.parseAll(value).singleOrNull() as? NProfile)?.hex

            HEX_KEY_REGEX.matches(value) -> value.lowercase()

            else -> null
        }
    }

    private fun parseSearchStatusEventId(raw: String): String? {
        val value = raw.removePrefix("nostr:").trim()
        return when {
            value.startsWith("note1", ignoreCase = true) ->
                (Nip19Parser.parseAll(value).singleOrNull() as? NNote)?.hex

            value.startsWith("nevent1", ignoreCase = true) ->
                (Nip19Parser.parseAll(value).singleOrNull() as? NEvent)?.hex

            HEX_KEY_REGEX.matches(value) -> value.lowercase()

            else -> null
        }
    }

    private fun <T : Event> signEvent(
        pubkeyHex: String,
        secretKey: String,
        template: EventTemplate<T>,
    ): T {
        val normalizedSecretHex = normalizeSecret(secretKey).hex
        return template.sign(
            pubKey = pubkeyHex,
            privKey = normalizedSecretHex.hexToByteArray(),
            pubKeyByteArray = pubkeyHex.hexToByteArray(),
        )
    }

    private fun <T : Event> EventTemplate<T>.sign(
        pubKey: String,
        privKey: ByteArray,
        pubKeyByteArray: ByteArray,
    ): T =
        com.vitorpamplona.quartz.nip01Core.crypto.EventAssembler.Companion.hashAndSign(
            pubKey,
            createdAt,
            kind,
            tags,
            content,
            privKey,
            pubKeyByteArray,
        )

    private fun parseRelayMessage(raw: String): RelayMessage? =
        runCatching {
            val payload = JSON.parseToJsonElement(raw).jsonArray
            when (payload.firstOrNull()?.jsonPrimitive?.contentOrNull) {
                "EVENT" ->
                    payload.getOrNull(2)?.let {
                        RelayMessage.EventEnvelope(
                            Event.Companion.fromJson(it.toString()),
                        )
                    }

                "EOSE" -> RelayMessage.EndOfStoredEvents
                "OK" ->
                    payload.getOrNull(1)?.jsonPrimitive?.contentOrNull?.let { eventId ->
                        RelayMessage.OkEnvelope(
                            eventId = eventId,
                            accepted = payload.getOrNull(2)?.jsonPrimitive?.booleanOrNull == true,
                            message = payload.getOrNull(3)?.jsonPrimitive?.contentOrNull,
                        )
                    }

                else -> null
            }
        }.getOrNull()

    private fun ByteArray.toHexString(): String {
        val chars = CharArray(size * 2)
        forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xff
            chars[index * 2] = HEX_DIGITS[value ushr 4]
            chars[index * 2 + 1] = HEX_DIGITS[value and 0x0f]
        }
        return chars.concatToString()
    }

    private suspend fun loadEvent(
        relays: List<String>,
        statusKey: MicroBlogKey,
    ): Event? =
        queryAllRelays(
            relays = relays,
            filters = listOf(Filter(ids = listOf(statusKey.id))),
        ).maxByOrNull { it.createdAt }

    private suspend fun buildAncestorChain(
        event: Event,
        relays: List<String>,
    ): List<Event> {
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
                    relays = relays,
                    statusKey = MicroBlogKey(parentId, NOSTR_HOST),
                ) ?: break
            chain += parent
            current = parent
        }
        return chain.reversed()
    }

    private suspend fun loadDirectReplies(
        relays: List<String>,
        statusKey: MicroBlogKey,
        pageSize: Int,
    ): List<Event> =
        queryAllRelays(
            relays = relays,
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
        relayHint: com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl?,
        cachedAuthorPubKey: String?,
    ): Array<String>? =
        when {
            target != null -> EventHintBundle(target).toQTagArray()
            cachedAuthorPubKey != null && statusKey.id.length == 64 -> QEventTag.assemble(statusKey.id, relayHint, cachedAuthorPubKey)
            else -> null
        }

    private suspend fun loadInteractionStats(
        relays: List<String>,
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
                        relays = relays,
                        filters =
                            listOf(
                                Filter(
                                    kinds = listOf(ReactionEvent.KIND, RepostEvent.KIND, GenericRepostEvent.KIND),
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
        accountKey: MicroBlogKey,
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
                            accountKey = accountKey,
                            profile = profiles[event.pubKey] ?: profileOf(event.pubKey, null, accountKey),
                            eventsById = eventsById,
                            profiles = profiles,
                            interactionStats = interactionStats,
                            visited = nextVisited,
                            resolveEvent = ::resolve,
                        )
                    is RepostEvent ->
                        event.toUiRepost(
                            accountKey = accountKey,
                            profiles = profiles,
                            eventsById = eventsById,
                            visited = nextVisited,
                            resolveEvent = ::resolve,
                        )
                    is GenericRepostEvent ->
                        event.toUiGenericRepost(
                            accountKey = accountKey,
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
        accountKey: MicroBlogKey,
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
                            accountKey = accountKey,
                            profile = profiles[event.pubKey] ?: profileOf(event.pubKey, null, accountKey),
                            eventsById = eventsById,
                            profiles = profiles,
                            interactionStats = interactionStats,
                            visited = nextVisited,
                            resolveEvent = ::resolve,
                        )
                    is RepostEvent ->
                        event.toUiRepost(
                            accountKey = accountKey,
                            profiles = profiles,
                            eventsById = eventsById,
                            visited = nextVisited,
                            resolveEvent = ::resolve,
                        )
                    is GenericRepostEvent ->
                        event.toUiGenericRepost(
                            accountKey = accountKey,
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
                accountKey = accountKey,
                accountPubkey = accountPubkey,
                profiles = profiles,
                eventsById = eventsById,
                interactionStats = interactionStats,
                resolveEvent = ::resolve,
            )
        }
    }

    private fun TextNoteEvent.toUi(
        accountKey: MicroBlogKey,
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
                                                        accountType = AccountType.Specific(accountKey),
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
                                resolveAddressReference(address, eventsById, visited, resolveEvent)
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
            it.endsWith(".jpg") || it.endsWith(".jpeg") || it.endsWith(".png") || it.endsWith(".webp") || it.endsWith(".heic")
        }

    private fun isVideoUrl(url: String): Boolean =
        url.substringBefore('?').lowercase().let {
            it.endsWith(".mp4") || it.endsWith(".webm") || it.endsWith(".mov") || it.endsWith(".m4v")
        }

    private fun isGifUrl(url: String): Boolean = url.substringBefore('?').lowercase().endsWith(".gif")

    private fun isAudioUrl(url: String): Boolean =
        url.substringBefore('?').lowercase().let {
            it.endsWith(".mp3") || it.endsWith(".m4a") || it.endsWith(".aac") || it.endsWith(".wav") || it.endsWith(".ogg")
        }

    private fun RepostEvent.toUiRepost(
        accountKey: MicroBlogKey,
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
                    accountKey = accountKey,
                    actor = profiles[pubKey] ?: profileOf(pubKey, null, accountKey),
                    statusKey = MicroBlogKey(id, NOSTR_HOST),
                    createdAt = createdAt,
                ),
            statusKey = MicroBlogKey(id, NOSTR_HOST),
            internalRepost = boostedPost,
        )
    }

    private fun GenericRepostEvent.toUiGenericRepost(
        accountKey: MicroBlogKey,
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
                    accountKey = accountKey,
                    actor = profiles[pubKey] ?: profileOf(pubKey, null, accountKey),
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
        accountKey: MicroBlogKey,
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
        accountKey: MicroBlogKey,
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
                        accountKey = accountKey,
                        profile = profiles[pubKey] ?: profileOf(pubKey, null, accountKey),
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
                            accountKey = accountKey,
                            actor = post.user ?: profiles[pubKey] ?: profileOf(pubKey, null, accountKey),
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
                            accountKey = accountKey,
                            actor = profiles[pubKey] ?: profileOf(pubKey, null, accountKey),
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
                            accountKey = accountKey,
                            actor = profiles[pubKey] ?: profileOf(pubKey, null, accountKey),
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
                            accountKey = accountKey,
                            actor = profiles[pubKey] ?: profileOf(pubKey, null, accountKey),
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

    private suspend fun loadEventGraph(
        relays: List<String>,
        roots: List<Event>,
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
            val eventIdsToFetch = pendingEventIds.filterNot(eventsById::containsKey).take(MAX_EVENT_ID_BATCH)
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
                fetchEventsByIds(relays, eventIdsToFetch) +
                    fetchEventsByAddress(relays, addressesToFetch)

            if (fetchedEvents.isEmpty()) {
                return@repeat
            }

            fetchedEvents.forEach(::register)
        }

        return eventsById
    }

    private suspend fun fetchEventsByIds(
        relays: List<String>,
        eventIds: List<String>,
    ): List<Event> =
        if (eventIds.isEmpty()) {
            emptyList()
        } else {
            eventIds
                .chunked(MAX_EVENT_ID_BATCH)
                .flatMap { ids ->
                    queryAllRelays(
                        relays = relays,
                        filters = listOf(Filter(ids = ids)),
                    )
                }.distinctBy { it.id }
        }

    private suspend fun fetchEventsByAddress(
        relays: List<String>,
        addresses: List<Address>,
    ): List<Event> {
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
            relays = relays,
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
        accountKey: MicroBlogKey,
    ): UiProfile {
        val bestName = metadata?.bestName().orEmpty()
        val npub = NPub.Companion.create(pubKey)
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

    private sealed interface RelayMessage {
        data class EventEnvelope(
            val event: Event,
        ) : RelayMessage

        data class OkEnvelope(
            val eventId: String,
            val accepted: Boolean,
            val message: String?,
        ) : RelayMessage

        data object EndOfStoredEvents : RelayMessage
    }

    internal data class InteractionStats(
        val reactionCount: Long = 0,
        val repostCount: Long = 0,
        val myReactionEventId: String? = null,
        val myRepostEventId: String? = null,
    )

    private val HEX_KEY_REGEX = Regex("^[0-9a-fA-F]{64}\$")
    private val HEX_DIGITS = "0123456789abcdef".toCharArray()
    private val timelineEventKinds = listOf(TextNoteEvent.KIND, RepostEvent.KIND, GenericRepostEvent.KIND)
    private val notificationInteractionKinds = listOf(ReactionEvent.KIND, RepostEvent.KIND, GenericRepostEvent.KIND)
    private const val RELAY_PUBLISH_TIMEOUT_MILLIS = 1_500L
    private const val RELAY_TIMEOUT_MILLIS = 3_500L
    private const val RELAY_SETTLE_TIMEOUT_MILLIS = 350L
    private const val MAX_REFERENCE_FETCH_ROUNDS = 4
    private const val MAX_EVENT_ID_BATCH = 100
    private const val MAX_ADDRESS_FILTER_BATCH = 32
    private const val MAX_HOME_AUTHORS = 250
    private const val MIN_METADATA_EVENT_LIMIT = 50

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
        accountKey: MicroBlogKey,
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
