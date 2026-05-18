package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.common.SerializableImmutableList
import dev.dimension.flare.common.SerializableImmutableMap
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.humanizer.Formatter.humanize
import dev.dimension.flare.ui.render.UiRichText
import dev.dimension.flare.ui.render.toUiPlainText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
@Immutable
public data class UiProfile public constructor(
    val key: MicroBlogKey,
    val handle: UiHandle,
    val avatar: String,
    private val nameInternal: UiRichText,
    val platformType: PlatformType,
    private val clickEvent: ClickEvent,
    public val banner: String?,
    public val description: UiRichText?,
    public val sourceLanguages: SerializableImmutableList<String> = persistentListOf(),
    @Transient
    public val translationDisplayState: TranslationDisplayState = TranslationDisplayState.Hidden,
    public val matrices: Matrices,
    public val mark: SerializableImmutableList<Mark>,
    public val bottomContent: BottomContent?,
) {
    // If name is blank, use handle without @ as display name
    val name: UiRichText by lazy {
        if (nameInternal.raw.isEmpty() || nameInternal.raw.isBlank()) {
            handleWithoutAtAndHost.toUiPlainText(sourceLanguages)
        } else {
            nameInternal
        }
    }

    val onClicked: ClickContext.() -> Unit by lazy {
        clickEvent.onClicked
    }

    public fun mergeWith(existing: UiProfile): UiProfile =
        UiProfile(
            key = key,
            handle =
                if (handle.raw.isBlank() || (isNostrFallbackHandle() && !existing.isNostrFallbackHandle())) {
                    existing.handle
                } else {
                    handle
                },
            avatar = avatar.ifBlank { existing.avatar },
            nameInternal =
                if (nameInternal.raw.isBlank() || (isNostrFallbackName() && !existing.isNostrFallbackName())) {
                    existing.nameInternal
                } else {
                    nameInternal
                },
            platformType = platformType,
            clickEvent = clickEvent,
            banner = banner ?: existing.banner,
            description = description ?: existing.description,
            sourceLanguages = if (sourceLanguages.isEmpty()) existing.sourceLanguages else sourceLanguages,
            matrices = matrices.mergeWith(existing.matrices),
            mark = (existing.mark + mark).distinct().toPersistentList(),
            bottomContent = bottomContent.mergeWith(existing.bottomContent),
        )

    private fun isNostrFallbackHandle(): Boolean =
        platformType == PlatformType.Nostr &&
            handle.raw == derivedNostrFallbackName()

    private fun isNostrFallbackName(): Boolean =
        platformType == PlatformType.Nostr &&
            nameInternal.raw == derivedNostrFallbackName()

    private fun derivedNostrFallbackName(): String? = bech32NostrPublicKey(key.id)?.take(16)

    @Serializable
    @Immutable
    public data class Matrices public constructor(
        val fansCount: Long,
        val followsCount: Long,
        val statusesCount: Long,
        val platformFansCount: String? = null,
    ) {
        val fansCountHumanized: String by lazy { platformFansCount ?: fansCount.humanize() }
        val followsCountHumanized: String by lazy { followsCount.humanize() }
        val statusesCountHumanized: String by lazy { statusesCount.humanize() }
    }

    val handleWithoutAt: String by lazy {
        handle.normalizedRaw
    }

    val handleWithoutAtAndHost: String by lazy {
        handleWithoutAt.let {
            if (platformType == PlatformType.Bluesky) {
                it.removeSuffix(".bsky.social")
            } else {
                it
            }
        }
    }

    val host: String? by lazy {
        handle.normalizedHost
    }

    @Serializable
    public sealed interface BottomContent {
        @Serializable
        public data class Fields public constructor(
            val fields: SerializableImmutableMap<String, UiRichText>,
        ) : BottomContent

        @Serializable
        public data class Iconify public constructor(
            val items: SerializableImmutableMap<Icon, UiRichText>,
        ) : BottomContent {
            public enum class Icon {
                Location,
                Url,
                Verify,
            }
        }
    }

    @Serializable
    public enum class Mark {
        Verified,
        Cat,
        Bot,
        Locked,
    }
}

public fun createSampleUser(): UiProfile =
    UiProfile(
        key = MicroBlogKey("sampleKey", "sampleHost"),
        handle =
            UiHandle(
                raw = "sampleUser",
                host = "sampleHost",
            ),
        avatar = "https://example.com/avatar.jpg",
        nameInternal = "".toUiPlainText(),
        platformType = PlatformType.Mastodon,
        clickEvent = ClickEvent.Noop,
        banner = "https://example.com/banner.jpg",
        description = null,
        matrices =
            UiProfile.Matrices(
                fansCount = 1000,
                followsCount = 500,
                statusesCount = 300,
                platformFansCount = "1K",
            ),
        mark = persistentListOf(),
        bottomContent = null,
    )

private fun UiProfile.Matrices.mergeWith(existing: UiProfile.Matrices): UiProfile.Matrices =
    UiProfile.Matrices(
        fansCount = fansCount.takeUnless { it == 0L && existing.fansCount > 0L } ?: existing.fansCount,
        followsCount = followsCount.takeUnless { it == 0L && existing.followsCount > 0L } ?: existing.followsCount,
        statusesCount = statusesCount.takeUnless { it == 0L && existing.statusesCount > 0L } ?: existing.statusesCount,
        platformFansCount = platformFansCount ?: existing.platformFansCount,
    )

private fun UiProfile.BottomContent?.mergeWith(existing: UiProfile.BottomContent?): UiProfile.BottomContent? =
    when {
        this == null -> {
            existing
        }

        existing == null -> {
            this
        }

        this is UiProfile.BottomContent.Fields && existing is UiProfile.BottomContent.Fields -> {
            UiProfile.BottomContent.Fields(
                fields = (existing.fields + fields).toPersistentMap(),
            )
        }

        this is UiProfile.BottomContent.Iconify && existing is UiProfile.BottomContent.Iconify -> {
            UiProfile.BottomContent.Iconify(
                items = (existing.items + items).toPersistentMap(),
            )
        }

        else -> {
            this
        }
    }

private const val NostrPublicKeyPrefix = "npub"
private const val Bech32Separator = '1'
private const val Bech32Charset = "qpzry9x8gf2tvdw0s3jn54khce6mua7l"

private val Bech32ChecksumGenerators =
    intArrayOf(
        0x3b6a57b2,
        0x26508e6d,
        0x1ea119fa,
        0x3d4233dd,
        0x2a1462b3,
    )

private fun bech32NostrPublicKey(hex: String): String? =
    runCatching {
        val data = convertBits(hexToBytes(hex), fromBits = 8, toBits = 5, pad = true)
        buildString {
            append(NostrPublicKeyPrefix)
            append(Bech32Separator)
            (data + bech32Checksum(NostrPublicKeyPrefix, data)).forEach {
                append(Bech32Charset[it])
            }
        }
    }.getOrNull()

private fun hexToBytes(hex: String): List<Int> {
    require(hex.length == 64)
    require(hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' })

    return hex
        .chunked(2)
        .map { it.toInt(16) }
}

private fun convertBits(
    data: List<Int>,
    fromBits: Int,
    toBits: Int,
    pad: Boolean,
): List<Int> {
    var accumulator = 0
    var bits = 0
    val maxValue = (1 shl toBits) - 1
    val maxAccumulator = (1 shl (fromBits + toBits - 1)) - 1
    val result = mutableListOf<Int>()

    data.forEach { value ->
        require(value >= 0 && value shr fromBits == 0)
        accumulator = ((accumulator shl fromBits) or value) and maxAccumulator
        bits += fromBits
        while (bits >= toBits) {
            bits -= toBits
            result += (accumulator shr bits) and maxValue
        }
    }

    if (pad) {
        if (bits > 0) {
            result += (accumulator shl (toBits - bits)) and maxValue
        }
    } else {
        require(bits < fromBits)
        require(((accumulator shl (toBits - bits)) and maxValue) == 0)
    }

    return result
}

private fun bech32Checksum(
    prefix: String,
    data: List<Int>,
): List<Int> {
    val values = bech32PrefixExpand(prefix) + data + List(6) { 0 }
    val polymod = bech32Polymod(values) xor 1
    return List(6) { index ->
        (polymod shr (5 * (5 - index))) and 31
    }
}

private fun bech32PrefixExpand(prefix: String): List<Int> =
    prefix.map { it.code shr 5 } + 0 + prefix.map { it.code and 31 }

private fun bech32Polymod(values: List<Int>): Int {
    var checksum = 1
    values.forEach { value ->
        val top = checksum shr 25
        checksum = ((checksum and 0x1ffffff) shl 5) xor value
        Bech32ChecksumGenerators.forEachIndexed { index, generator ->
            if (((top shr index) and 1) == 1) {
                checksum = checksum xor generator
            }
        }
    }
    return checksum
}
