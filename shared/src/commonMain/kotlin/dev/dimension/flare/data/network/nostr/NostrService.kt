package dev.dimension.flare.data.network.nostr

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.core.hexToByteArray
import com.vitorpamplona.quartz.nip01Core.metadata.MetadataEvent
import com.vitorpamplona.quartz.nip01Core.metadata.UserMetadata
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.RelayUrlNormalizer
import com.vitorpamplona.quartz.nip01Core.signers.EventTemplate
import com.vitorpamplona.quartz.nip02FollowList.ContactListEvent
import com.vitorpamplona.quartz.nip10Notes.TextNoteEvent
import com.vitorpamplona.quartz.nip19Bech32.Nip19Parser
import com.vitorpamplona.quartz.nip19Bech32.entities.NPub
import com.vitorpamplona.quartz.nip19Bech32.entities.NSec
import com.vitorpamplona.quartz.nip19Bech32.toNsec
import dev.dimension.flare.common.JSON
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.render.toUi
import dev.dimension.flare.ui.render.toUiPlainText
import dev.dimension.flare.ui.route.DeeplinkRoute
import dev.whyoleg.cryptography.random.CryptographyRandom
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
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

internal object NostrService {
    internal const val NOSTR_HOST: String = "nostr"

    internal val defaultRelays: List<String> = defaultNostrRelays

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
                            kinds = listOf(TextNoteEvent.KIND),
                            until = until,
                            limit = pageSize,
                        ),
                    ),
            ).filterIsInstance<TextNoteEvent>()
                .sortedByDescending { it.createdAt }

        if (events.isEmpty()) {
            return emptyList()
        }

        val metadata = loadMetadata(relays, events.map { it.pubKey }.distinct())
        return events.map { event ->
            event.toUi(
                accountKey = accountKey,
                metadata = metadata[event.pubKey],
            )
        }
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
                            kinds = listOf(TextNoteEvent.KIND),
                            until = until,
                            limit = pageSize,
                        ),
                    ),
            ).filterIsInstance<TextNoteEvent>()
                .sortedByDescending { it.createdAt }
        if (events.isEmpty()) {
            return emptyList()
        }
        val metadata = loadMetadata(relays, listOf(targetPubkey))
        return events.map { event ->
            event.toUi(
                accountKey = accountKey,
                metadata = metadata[event.pubKey],
            )
        }
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
        val latestByPubkey =
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
                .mapValues { (_, values) -> values.maxByOrNull { it.createdAt } }

        return buildMap {
            latestByPubkey.forEach { (pubkey, event) ->
                event?.runCatching { contactMetaData() }?.getOrNull()?.let {
                    put(pubkey, it)
                }
            }
        }
    }

    private suspend fun queryFirstRelay(
        relays: List<String>,
        filters: List<Filter>,
    ): List<Event> =
        relays
            .firstNotNullOfOrNull { relay ->
                runCatching { queryRelay(relay, filters) }.getOrNull()?.takeIf { it.isNotEmpty() }
            }.orEmpty()

    private suspend fun queryAllRelays(
        relays: List<String>,
        filters: List<Filter>,
    ): List<Event> =
        coroutineScope {
            relays
                .map { relay ->
                    async {
                        runCatching { queryRelay(relay, filters) }.getOrDefault(emptyList())
                    }
                }.awaitAll()
                .flatten()
                .distinctBy { it.id }
        }

    private suspend fun queryRelay(
        relay: String,
        filters: List<Filter>,
    ): List<Event> {
        val client =
            ktorClient {
                install(WebSockets)
            }
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
                                null -> Unit
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
            client.close()
        }
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

    private fun <T : Event> signEvent(
        pubkeyHex: String,
        secretKey: String,
        template: EventTemplate<T>,
    ): T =
        template.sign(
            pubKey = pubkeyHex,
            privKey = NSec(secretKey.removePrefix("nostr:")).hex.hexToByteArray(),
            pubKeyByteArray = pubkeyHex.hexToByteArray(),
        )

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

    private fun TextNoteEvent.toUi(
        accountKey: MicroBlogKey,
        metadata: UserMetadata?,
    ): UiTimelineV2.Post =
        UiTimelineV2.Post(
            message = null,
            platformType = PlatformType.Nostr,
            images = persistentListOf(),
            sensitive = false,
            contentWarning = null,
            user = profileOf(pubKey, metadata, accountKey),
            quote = persistentListOf(),
            content = content.toUiPlainText(),
            actions = persistentListOf(),
            poll = null,
            statusKey = MicroBlogKey(id, NOSTR_HOST),
            card = null,
            createdAt = Instant.fromEpochSeconds(createdAt).toUi(),
            emojiReactions = persistentListOf(),
            sourceChannel = null,
            visibility = UiTimelineV2.Post.Visibility.Public,
            replyToHandle = null,
            references = persistentListOf(),
            parents = persistentListOf(),
            internalRepost = null,
            clickEvent = ClickEvent.Noop,
            extraKey = null,
            accountType = AccountType.Specific(accountKey),
        )

    private fun profileOf(
        pubKey: String,
        metadata: UserMetadata?,
        accountKey: MicroBlogKey,
    ): UiProfile {
        val bestName = metadata?.bestName().orEmpty()
        val npub = NPub.Companion.create(pubKey)
        return UiProfile(
            key = MicroBlogKey(pubKey, NOSTR_HOST),
            handle =
                UiHandle(
                    raw = bestName.ifBlank { npub.take(16) },
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
                    if (metadata?.bot == true) {
                        UiProfile.Mark.Bot
                    } else {
                        null
                    },
                ).toImmutableList(),
            bottomContent = null,
        )
    }

    private sealed interface RelayMessage {
        data class EventEnvelope(
            val event: Event,
        ) : RelayMessage

        data object EndOfStoredEvents : RelayMessage
    }

    private val HEX_KEY_REGEX = Regex("^[0-9a-fA-F]{64}\$")
    private val HEX_DIGITS = "0123456789abcdef".toCharArray()
    private const val RELAY_TIMEOUT_MILLIS = 8_000L
    private const val MAX_HOME_AUTHORS = 250
    private const val MIN_METADATA_EVENT_LIMIT = 50
}
