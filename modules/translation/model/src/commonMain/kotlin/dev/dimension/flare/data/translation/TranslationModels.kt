package dev.dimension.flare.data.translation

import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.ui.render.UiRichText
import kotlinx.serialization.Serializable

@Serializable
public enum class TranslationEntityType {
    Status,
    Profile,
}

@Serializable
public enum class TranslationStatus {
    Pending,
    Translating,
    Completed,
    Failed,
    Skipped,
}

@Serializable
public enum class TranslationDisplayMode {
    Auto,
    Original,
    Translated,
}

public const val TRANSLATION_SKIPPED_EXCLUDED_LANGUAGE_REASON: String = "source_language_excluded"

@Serializable
public data class TranslationPayload(
    public val content: UiRichText? = null,
    public val contentWarning: UiRichText? = null,
    public val title: UiRichText? = null,
    public val description: UiRichText? = null,
)

public fun TranslationPayload.sourceHash(providerCacheKey: String): String =
    buildString {
        append(providerCacheKey)
        append('\u0000')
        append(encodeJson(TranslationPayload.serializer()))
    }.stableTranslationHash()

private fun String.stableTranslationHash(): String {
    var hash = -0x340d631b8c4674c3L
    encodeToByteArray().forEach { byte ->
        hash = hash xor (byte.toLong() and 0xffL)
        hash *= 0x100000001b3L
    }
    return hash.toULong().toString(16)
}
