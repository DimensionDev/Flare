package dev.dimension.flare.data.datasource.microblog

import dev.dimension.flare.common.CacheData
import dev.dimension.flare.ui.model.UiEmoji
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap

public data class ComposeConfig internal constructor(
    val text: Text? = null,
    val media: Media? = null,
    val poll: Poll? = null,
    val emoji: Emoji? = null,
    val contentWarning: ContentWarning? = null,
    val visibility: Visibility? = null,
    val language: Language? = null,
) {
    public data class Text internal constructor(
        val maxLength: Int,
    ) {
        internal fun merge(other: Text): Text =
            Text(
                maxLength = minOf(maxLength, other.maxLength),
            )
    }

    // in ISO 639-1 format
    public data class Language internal constructor(
        val maxCount: Int,
    ) {
        private val popularCodes =
            listOf(
                "en",
                "zh",
                "ja",
                "ko",
                "pt",
                "de",
                "fr",
                "es",
                "ru",
                "ar",
                "hi",
                "it",
                "id",
                "th",
                "vi",
            )

        // since ISO 639-1 will never change(maybe), hard code the codes here
        private val allCodes =
            listOf(
                "aa",
                "ab",
                "ae",
                "af",
                "ak",
                "am",
                "an",
                "ar",
                "as",
                "av",
                "ay",
                "az",
                "ba",
                "be",
                "bg",
                "bi",
                "bm",
                "bn",
                "bo",
                "br",
                "bs",
                "ca",
                "ce",
                "ch",
                "co",
                "cr",
                "cs",
                "cu",
                "cv",
                "cy",
                "da",
                "de",
                "dv",
                "dz",
                "ee",
                "el",
                "en",
                "eo",
                "es",
                "et",
                "eu",
                "fa",
                "ff",
                "fi",
                "fj",
                "fo",
                "fr",
                "fy",
                "ga",
                "gd",
                "gl",
                "gn",
                "gu",
                "gv",
                "ha",
                "he",
                "hi",
                "ho",
                "hr",
                "ht",
                "hu",
                "hy",
                "hz",
                "ia",
                "id",
                "ie",
                "ig",
                "ii",
                "ik",
                "io",
                "is",
                "it",
                "iu",
                "ja",
                "jv",
                "ka",
                "kg",
                "ki",
                "kj",
                "kk",
                "kl",
                "km",
                "kn",
                "ko",
                "kr",
                "ks",
                "ku",
                "kv",
                "kw",
                "ky",
                "la",
                "lb",
                "lg",
                "li",
                "ln",
                "lo",
                "lt",
                "lu",
                "lv",
                "mg",
                "mh",
                "mi",
                "mk",
                "ml",
                "mn",
                "mr",
                "ms",
                "mt",
                "my",
                "na",
                "nb",
                "nd",
                "ne",
                "ng",
                "nl",
                "nn",
                "no",
                "nr",
                "nv",
                "ny",
                "oc",
                "oj",
                "om",
                "or",
                "os",
                "pa",
                "pi",
                "pl",
                "ps",
                "pt",
                "qu",
                "rm",
                "rn",
                "ro",
                "ru",
                "rw",
                "sa",
                "sc",
                "sd",
                "se",
                "sg",
                "si",
                "sk",
                "sl",
                "sm",
                "sn",
                "so",
                "sq",
                "sr",
                "ss",
                "st",
                "su",
                "sv",
                "sw",
                "ta",
                "te",
                "tg",
                "th",
                "ti",
                "tk",
                "tl",
                "tn",
                "to",
                "tr",
                "ts",
                "tt",
                "tw",
                "ty",
                "ug",
                "uk",
                "ur",
                "uz",
                "ve",
                "vi",
                "vo",
                "wa",
                "wo",
                "xh",
                "yi",
                "yo",
                "za",
                "zh",
                "zu",
            )

        val sortedIsoCodes: List<String> by lazy {
            val remainder = allCodes.filterNot { it in popularCodes }
            popularCodes + remainder
        }
    }

    public data class Media internal constructor(
        val maxCount: Int,
        val canSensitive: Boolean,
        val altTextMaxLength: Int,
        val allowMediaOnly: Boolean,
    ) {
        internal fun merge(other: Media): Media =
            Media(
                maxCount = minOf(maxCount, other.maxCount),
                canSensitive = canSensitive && other.canSensitive,
                altTextMaxLength = minOf(altTextMaxLength, other.altTextMaxLength),
                allowMediaOnly = allowMediaOnly && other.allowMediaOnly,
            )
    }

    public data class Poll internal constructor(
        val maxOptions: Int,
    ) {
        internal fun merge(other: Poll): Poll =
            Poll(
                maxOptions = minOf(maxOptions, other.maxOptions),
            )
    }

    public data class Emoji internal constructor(
        internal val emoji: CacheData<ImmutableMap<String, ImmutableList<UiEmoji>>>,
        // Emojis picker can be merged only if their mergeTag is the same.
        val mergeTag: String,
    ) {
        internal fun merge(other: Emoji): Emoji? =
            if (mergeTag == other.mergeTag) {
                Emoji(
                    emoji = emoji,
                    mergeTag = mergeTag,
                )
            } else {
                null
            }
    }

    public data object ContentWarning

    public data object Visibility

    internal fun merge(other: ComposeConfig): ComposeConfig {
        val text =
            if (text != null && other.text != null) {
                text.merge(other.text)
            } else {
                null
            }
        val media =
            if (media != null && other.media != null) {
                media.merge(other.media)
            } else {
                null
            }
        val poll =
            if (poll != null && other.poll != null) {
                poll.merge(other.poll)
            } else {
                null
            }
        val emoji =
            if (emoji != null && other.emoji != null) {
                emoji.merge(other.emoji)
            } else {
                null
            }
        val contentWarning =
            if (contentWarning != null && other.contentWarning != null) {
                contentWarning
            } else {
                null
            }
        return ComposeConfig(
            text = text,
            media = media,
            poll = poll,
            emoji = emoji,
            contentWarning = contentWarning,
            visibility = null,
        )
    }
}
