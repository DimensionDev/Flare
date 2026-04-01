package dev.dimension.flare.data.network.nostr

import dev.dimension.flare.common.JSON
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import rust.nostr.sdk.Event as RustEvent
import rust.nostr.sdk.Filter as RustFilter
import rust.nostr.sdk.Nip19 as RustNip19
import rust.nostr.sdk.Nip19Enum as RustNip19Enum
import rust.nostr.sdk.PublicKey as RustPublicKey
import rust.nostr.sdk.RelayUrl as RustRelayUrl

internal data class Filter(
    val ids: List<String> = emptyList(),
    val authors: List<String> = emptyList(),
    val kinds: List<Int> = emptyList(),
    val tags: Map<String, List<String>> = emptyMap(),
    val since: Long? = null,
    val until: Long? = null,
    val limit: Int? = null,
    val search: String? = null,
) {
    fun toRust(): RustFilter {
        val json =
            buildJsonObject {
                if (ids.isNotEmpty()) {
                    put("ids", JsonArray(ids.map(::JsonPrimitive)))
                }
                if (authors.isNotEmpty()) {
                    put("authors", JsonArray(authors.map(::JsonPrimitive)))
                }
                if (kinds.isNotEmpty()) {
                    put("kinds", JsonArray(kinds.map(::JsonPrimitive)))
                }
                tags.forEach { (key, values) ->
                    if (values.isNotEmpty()) {
                        put("#$key", JsonArray(values.map(::JsonPrimitive)))
                    }
                }
                since?.let { put("since", JsonPrimitive(it)) }
                until?.let { put("until", JsonPrimitive(it)) }
                limit?.let { put("limit", JsonPrimitive(it)) }
                search?.takeIf { it.isNotBlank() }?.let { put("search", JsonPrimitive(it)) }
            }
        return RustFilter.Companion.fromJson(json.toString())
    }
}

internal sealed class Event(
    val id: String,
    val pubKey: String,
    val createdAt: Long,
    val kind: Int,
    val content: String,
    val tags: Array<Array<String>>,
    private val rawJson: String,
) {
    fun toJson(): String = rawJson

    fun toRust(): RustEvent = RustEvent.Companion.fromJson(rawJson)

    open fun dTag(): String = tags.firstOrNull { it.size > 1 && it[0] == "d" }?.get(1).orEmpty()

    companion object {
        fun fromJson(raw: String): Event = RustEvent.Companion.fromJson(raw).use { it.toCompatEvent() }
    }
}

internal class MetadataEvent(
    id: String,
    pubKey: String,
    createdAt: Long,
    content: String,
    tags: Array<Array<String>>,
    rawJson: String,
) : Event(id, pubKey, createdAt, KIND, content, tags, rawJson) {
    fun contactMetaData(): UserMetadata? =
        runCatching {
            JSON.decodeFromString<UserMetadata>(content).also(UserMetadata::cleanBlankNames)
        }.getOrNull()

    companion object {
        const val KIND: Int = 0
    }
}

internal class TextNoteEvent(
    id: String,
    pubKey: String,
    createdAt: Long,
    content: String,
    tags: Array<Array<String>>,
    rawJson: String,
) : Event(id, pubKey, createdAt, KIND, content, tags, rawJson) {
    companion object {
        const val KIND: Int = 1
    }
}

internal class ContactListEvent(
    id: String,
    pubKey: String,
    createdAt: Long,
    content: String,
    tags: Array<Array<String>>,
    rawJson: String,
) : Event(id, pubKey, createdAt, KIND, content, tags, rawJson) {
    fun verifiedFollowKeySet(): Set<String> = tags.userIdSet()

    companion object {
        const val KIND: Int = 3
    }
}

internal class DeletionEvent(
    id: String,
    pubKey: String,
    createdAt: Long,
    content: String,
    tags: Array<Array<String>>,
    rawJson: String,
) : Event(id, pubKey, createdAt, KIND, content, tags, rawJson) {
    companion object {
        const val KIND: Int = 5
    }
}

internal class RepostEvent(
    id: String,
    pubKey: String,
    createdAt: Long,
    content: String,
    tags: Array<Array<String>>,
    rawJson: String,
) : Event(id, pubKey, createdAt, KIND, content, tags, rawJson) {
    fun boostedEventId(): String? =
        tags
            .asList()
            .asReversed()
            .firstNotNullOfOrNull { tag ->
                tag
                    .getOrNull(0)
                    ?.takeIf { it == "e" }
                    ?.let { tag.getOrNull(1) }
                    ?.takeIf(::isHexKey)
            }

    fun boostedAddress(): Address? =
        tags
            .asList()
            .asReversed()
            .firstNotNullOfOrNull { tag ->
                tag
                    .getOrNull(0)
                    ?.takeIf { it == "a" }
                    ?.let { tag.getOrNull(1) }
                    ?.let(Address::parse)
            }

    fun containedPost(): Event? =
        content
            .takeIf { it.isNotBlank() }
            ?.let {
                runCatching { fromJson(it) }.getOrNull()
            }

    companion object {
        const val KIND: Int = 6
    }
}

internal class ReactionEvent(
    id: String,
    pubKey: String,
    createdAt: Long,
    content: String,
    tags: Array<Array<String>>,
    rawJson: String,
) : Event(id, pubKey, createdAt, KIND, content, tags, rawJson) {
    fun originalPost(): List<String> =
        tags.mapNotNull { tag ->
            tag
                .getOrNull(0)
                ?.takeIf { it == "e" }
                ?.let { tag.getOrNull(1) }
                ?.takeIf(::isHexKey)
        }

    companion object {
        const val KIND: Int = 7
        const val LIKE: String = "+"
    }
}

internal class GenericRepostEvent(
    id: String,
    pubKey: String,
    createdAt: Long,
    content: String,
    tags: Array<Array<String>>,
    rawJson: String,
) : Event(id, pubKey, createdAt, KIND, content, tags, rawJson) {
    fun boostedEventId(): String? =
        tags
            .asList()
            .asReversed()
            .firstNotNullOfOrNull { tag ->
                tag
                    .getOrNull(0)
                    ?.takeIf { it == "e" }
                    ?.let { tag.getOrNull(1) }
                    ?.takeIf(::isHexKey)
            }

    fun boostedAddress(): Address? =
        tags
            .asList()
            .asReversed()
            .firstNotNullOfOrNull { tag ->
                tag
                    .getOrNull(0)
                    ?.takeIf { it == "a" }
                    ?.let { tag.getOrNull(1) }
                    ?.let(Address::parse)
            }

    fun containedPost(): Event? =
        content
            .takeIf { it.isNotBlank() }
            ?.let {
                runCatching { fromJson(it) }.getOrNull()
            }

    companion object {
        const val KIND: Int = 16
    }
}

internal class MuteListEvent(
    id: String,
    pubKey: String,
    createdAt: Long,
    content: String,
    tags: Array<Array<String>>,
    rawJson: String,
) : Event(id, pubKey, createdAt, KIND, content, tags, rawJson) {
    companion object {
        const val KIND: Int = 10000
    }
}

internal class PeopleListEvent(
    id: String,
    pubKey: String,
    createdAt: Long,
    content: String,
    tags: Array<Array<String>>,
    rawJson: String,
) : Event(id, pubKey, createdAt, KIND, content, tags, rawJson) {
    companion object {
        const val KIND: Int = 30000
        const val BLOCK_LIST_D_TAG: String = "mute"
    }
}

internal fun RustEvent.toCompatEvent(): Event {
    val snapshot = snapshot()
    return when (snapshot.kind) {
        MetadataEvent.KIND ->
            MetadataEvent(
                snapshot.id,
                snapshot.pubKey,
                snapshot.createdAt,
                snapshot.content,
                snapshot.tags,
                snapshot.rawJson,
            )
        TextNoteEvent.KIND ->
            TextNoteEvent(
                snapshot.id,
                snapshot.pubKey,
                snapshot.createdAt,
                snapshot.content,
                snapshot.tags,
                snapshot.rawJson,
            )
        ContactListEvent.KIND ->
            ContactListEvent(
                snapshot.id,
                snapshot.pubKey,
                snapshot.createdAt,
                snapshot.content,
                snapshot.tags,
                snapshot.rawJson,
            )
        DeletionEvent.KIND ->
            DeletionEvent(
                snapshot.id,
                snapshot.pubKey,
                snapshot.createdAt,
                snapshot.content,
                snapshot.tags,
                snapshot.rawJson,
            )
        RepostEvent.KIND -> RepostEvent(snapshot.id, snapshot.pubKey, snapshot.createdAt, snapshot.content, snapshot.tags, snapshot.rawJson)
        ReactionEvent.KIND ->
            ReactionEvent(
                snapshot.id,
                snapshot.pubKey,
                snapshot.createdAt,
                snapshot.content,
                snapshot.tags,
                snapshot.rawJson,
            )
        GenericRepostEvent.KIND ->
            GenericRepostEvent(
                snapshot.id,
                snapshot.pubKey,
                snapshot.createdAt,
                snapshot.content,
                snapshot.tags,
                snapshot.rawJson,
            )
        MuteListEvent.KIND ->
            MuteListEvent(
                snapshot.id,
                snapshot.pubKey,
                snapshot.createdAt,
                snapshot.content,
                snapshot.tags,
                snapshot.rawJson,
            )
        PeopleListEvent.KIND ->
            PeopleListEvent(
                snapshot.id,
                snapshot.pubKey,
                snapshot.createdAt,
                snapshot.content,
                snapshot.tags,
                snapshot.rawJson,
            )
        else ->
            GenericEvent(
                snapshot.id,
                snapshot.pubKey,
                snapshot.createdAt,
                snapshot.kind,
                snapshot.content,
                snapshot.tags,
                snapshot.rawJson,
            )
    }
}

private data class EventSnapshot(
    val id: String,
    val pubKey: String,
    val createdAt: Long,
    val kind: Int,
    val content: String,
    val tags: Array<Array<String>>,
    val rawJson: String,
)

private fun RustEvent.snapshot(): EventSnapshot =
    EventSnapshot(
        id = id().use { it.toHex() },
        pubKey = author().use { it.toHex() },
        createdAt = createdAt().use { it.asSecs().toLong() },
        kind = kind().use { it.asU16().toInt() },
        content = content(),
        tags =
            runCatching {
                tags().use { tags ->
                    tags
                        .toVec()
                        .map { tag ->
                            tag.use { it.asVec().toTypedArray() }
                        }.toTypedArray()
                }
            }.getOrDefault(emptyArray()),
        rawJson = asJson(),
    )

internal class GenericEvent(
    id: String,
    pubKey: String,
    createdAt: Long,
    kind: Int,
    content: String,
    tags: Array<Array<String>>,
    rawJson: String,
) : Event(id, pubKey, createdAt, kind, content, tags, rawJson)

internal data class Address(
    val kind: Int,
    val pubKeyHex: String,
    val dTag: String,
) {
    fun toValue(): String = assemble(kind = kind, pubKeyHex = pubKeyHex, dTag = dTag)

    companion object {
        fun assemble(
            kind: Int,
            pubKeyHex: String,
            dTag: String,
        ): String = "$kind:$pubKeyHex:$dTag"

        fun parse(value: String): Address? {
            val parts = value.split(':', limit = 3)
            if (parts.size != 3) {
                return null
            }
            val kind = parts[0].toIntOrNull() ?: return null
            val pubKeyHex = parts[1].takeIf(::isHexKey) ?: return null
            return Address(
                kind = kind,
                pubKeyHex = pubKeyHex,
                dTag = parts[2],
            )
        }
    }
}

internal typealias NormalizedRelayUrl = String

internal object RelayUrlNormalizer {
    fun normalizeOrNull(raw: String): NormalizedRelayUrl? =
        runCatching {
            RustRelayUrl.Companion.parse(raw).use { relayUrl ->
                relayUrl.toString().let {
                    if (it.endsWith("/")) {
                        it
                    } else {
                        "$it/"
                    }
                }
            }
        }.getOrNull()

    fun isRelayUrl(raw: String): Boolean = normalizeOrNull(raw) != null
}

internal data class QEventTag(
    val eventId: String,
    val relay: NormalizedRelayUrl? = null,
    val author: String? = null,
) {
    companion object {
        fun parse(tag: Array<String>): QEventTag? {
            if (tag.getOrNull(0) != "q") {
                return null
            }
            val eventId = tag.getOrNull(1)?.takeIf(::isHexKey) ?: return null
            return QEventTag(
                eventId = eventId,
                relay = tag.getOrNull(2)?.takeIf { it.isNotBlank() }?.let(RelayUrlNormalizer::normalizeOrNull),
                author = tag.getOrNull(3)?.takeIf(::isHexKey),
            )
        }
    }
}

internal data class QAddressableTag(
    val address: Address,
    val relay: NormalizedRelayUrl? = null,
) {
    companion object {
        fun parse(tag: Array<String>): QAddressableTag? {
            if (tag.getOrNull(0) != "q") {
                return null
            }
            val value = tag.getOrNull(1) ?: return null
            if (isHexKey(value)) {
                return null
            }
            return QAddressableTag(
                address = Address.parse(value) ?: return null,
                relay = tag.getOrNull(2)?.takeIf { it.isNotBlank() }?.let(RelayUrlNormalizer::normalizeOrNull),
            )
        }
    }
}

internal data class MarkedETag(
    val eventId: String,
    val relay: NormalizedRelayUrl? = null,
    val marker: Marker? = null,
    val author: String? = null,
) {
    enum class Marker {
        ROOT,
        REPLY,
        MENTION,
        FORK,
    }

    companion object {
        fun parseReply(tag: Array<String>): MarkedETag? {
            if (tag.getOrNull(0) != "e") {
                return null
            }
            val eventId = tag.getOrNull(1)?.takeIf(::isHexKey) ?: return null
            val marker = pickMarker(tag)
            if (marker != Marker.REPLY) {
                return null
            }
            return MarkedETag(
                eventId = eventId,
                relay = pickRelayHint(tag),
                marker = marker,
                author = pickAuthor(tag),
            )
        }

        fun parseRootId(tag: Array<String>): String? {
            if (tag.getOrNull(0) != "e") {
                return null
            }
            val eventId = tag.getOrNull(1)?.takeIf(::isHexKey) ?: return null
            return eventId.takeIf { pickMarker(tag) == Marker.ROOT }
        }

        fun parseOnlyPositionalThreadTagsIds(tag: Array<String>): String? {
            if (tag.getOrNull(0) != "e") {
                return null
            }
            val eventId = tag.getOrNull(1)?.takeIf(::isHexKey) ?: return null
            return when (tag.size) {
                2, 3 -> eventId
                4 -> {
                    val fourth = tag[3]
                    if (fourth.isEmpty() || isHexKey(fourth)) {
                        eventId
                    } else {
                        null
                    }
                }

                else -> {
                    val fourth = tag.getOrNull(3).orEmpty()
                    if (fourth.isEmpty() || isHexKey(fourth)) {
                        eventId
                    } else {
                        null
                    }
                }
            }
        }

        private fun pickRelayHint(tag: Array<String>): NormalizedRelayUrl? =
            listOfNotNull(tag.getOrNull(2), tag.getOrNull(3), tag.getOrNull(4))
                .firstNotNullOfOrNull { candidate ->
                    candidate
                        .takeIf { it.length > 7 && RelayUrlNormalizer.isRelayUrl(it) }
                        ?.let(RelayUrlNormalizer::normalizeOrNull)
                }

        private fun pickAuthor(tag: Array<String>): String? =
            listOfNotNull(tag.getOrNull(3), tag.getOrNull(4), tag.getOrNull(2))
                .firstOrNull(::isHexKey)

        private fun pickMarker(tag: Array<String>): Marker? =
            listOfNotNull(tag.getOrNull(3), tag.getOrNull(4), tag.getOrNull(2))
                .firstNotNullOfOrNull { candidate ->
                    when (candidate) {
                        "root" -> Marker.ROOT
                        "reply" -> Marker.REPLY
                        "mention" -> Marker.MENTION
                        "fork" -> Marker.FORK
                        else -> null
                    }
                }
    }
}

internal data class IMetaTag(
    val url: String,
    val properties: Map<String, List<String>> = emptyMap(),
) {
    companion object {
        fun parse(tag: Array<String>): List<IMetaTag>? {
            if (tag.getOrNull(0) != "imeta") {
                return null
            }
            val parsed = parseProperties(tag)
            return parsed["url"]?.map { url ->
                IMetaTag(
                    url = url,
                    properties = parsed - "url",
                )
            }
        }

        private fun parseProperties(tag: Array<String>): Map<String, List<String>> {
            val properties = mutableMapOf<String, MutableList<String>>()
            tag.forEach { entry ->
                val parts = entry.split(" ", limit = 2)
                when (parts.size) {
                    2 -> properties.getOrPut(parts[0]) { mutableListOf() }.add(parts[1])
                    1 -> properties.getOrPut(parts[0]) { mutableListOf() }.add("")
                }
            }
            return properties
        }
    }
}

internal data class DimensionTag(
    val width: Int,
    val height: Int,
) {
    companion object {
        fun parse(raw: String): DimensionTag? {
            if (raw == "0x0") {
                return null
            }
            val parts = raw.split("x")
            if (parts.size != 2) {
                return null
            }
            val width = parts[0].toIntOrNull() ?: return null
            val height = parts[1].toIntOrNull() ?: return null
            return DimensionTag(width = width, height = height)
        }
    }
}

@Serializable
internal data class UserMetadata(
    var name: String? = null,
    var username: String? = null,
    @SerialName("display_name")
    var displayName: String? = null,
    var picture: String? = null,
    var banner: String? = null,
    var website: String? = null,
    var about: String? = null,
    var bot: Boolean? = null,
    var pronouns: String? = null,
    var nip05: String? = null,
    var nip05Verified: Boolean = false,
    var nip05LastVerificationTime: Long? = 0,
    var domain: String? = null,
    var lud06: String? = null,
    var lud16: String? = null,
    var twitter: String? = null,
    @SerialName("tags")
    private val rawTags: List<List<String>>? = null,
) {
    val tags: MetadataTags?
        get() = rawTags?.let(::MetadataTags)

    fun bestName(): String? = displayName ?: name ?: username

    fun cleanBlankNames() {
        if (pronouns == "null") {
            pronouns = null
        }

        picture = picture?.trim().takeUnless(String?::isNullOrBlank)
        nip05 = nip05?.trim().takeUnless(String?::isNullOrBlank)
        displayName = displayName?.trim().takeUnless(String?::isNullOrBlank)
        name = name?.trim().takeUnless(String?::isNullOrBlank)
        username = username?.trim().takeUnless(String?::isNullOrBlank)
        lud06 = lud06?.trim().takeUnless(String?::isNullOrBlank)
        lud16 = lud16?.trim().takeUnless(String?::isNullOrBlank)
        pronouns = pronouns?.trim().takeUnless(String?::isNullOrBlank)
        banner = banner?.trim().takeUnless(String?::isNullOrBlank)
        website = website?.trim().takeUnless(String?::isNullOrBlank)
        domain = domain?.trim().takeUnless(String?::isNullOrBlank)
    }
}

internal data class MetadataTags(
    val lists: List<List<String>>,
)

internal fun Array<Array<String>>.userIdSet(): Set<String> =
    mapNotNullTo(mutableSetOf()) { tag ->
        tag
            .getOrNull(0)
            ?.takeIf { it == "p" }
            ?.let { tag.getOrNull(1) }
            ?.takeIf(::isHexKey)
    }

internal fun Array<Array<String>>.mutedUserIdSet(): Set<String> = userIdSet()

internal fun bech32PublicKey(hex: String): String = RustPublicKey.Companion.parse(hex).use { it.toBech32() }

internal fun parsePublicKeyHex(raw: String): String? {
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

        isHexKey(value) -> value.lowercase()

        else -> null
    }
}

internal inline fun <T> withNip19(
    value: String,
    block: (RustNip19Enum) -> T,
): T? =
    runCatching {
        RustNip19.Companion
            .fromBech32(value)
            .asEnum()
            .use(block)
    }.getOrNull()

private fun isHexKey(value: String): Boolean = value.length == 64 && value.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
