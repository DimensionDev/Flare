package dev.dimension.flare.data.translation

import dev.dimension.flare.data.database.cache.model.TranslationPayload
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.render.RenderContent
import dev.dimension.flare.ui.render.RenderRun
import dev.dimension.flare.ui.render.RenderTextStyle
import dev.dimension.flare.ui.render.UiRichText

internal object PreTranslationContentRules {
    private val protectedTranslationPattern =
        Regex("""https?://\S+|@[A-Za-z0-9._-]+(?:@[A-Za-z0-9.-]+)?|#[\p{L}\p{N}_]+""")

    fun sourceLanguages(timeline: UiTimelineV2): List<String> =
        when (timeline) {
            is UiTimelineV2.Feed -> timeline.sourceLanguages
            is UiTimelineV2.Post -> timeline.sourceLanguages
            is UiTimelineV2.Message -> emptyList()
            is UiTimelineV2.User -> emptyList()
            is UiTimelineV2.UserList -> emptyList()
        }

    fun shouldSkipForMatchingSourceLanguage(
        sourceLanguages: List<String>,
        targetLanguage: String,
    ): Boolean {
        val canonicalTargetLanguage = canonicalTranslationLanguage(targetLanguage) ?: return false
        return sourceLanguages
            .asSequence()
            .mapNotNull(::canonicalTranslationLanguage)
            .any { it == canonicalTargetLanguage }
    }

    fun shouldSkipForExcludedSourceLanguage(
        sourceLanguages: List<String>,
        excludedLanguages: List<String>,
    ): Boolean {
        val canonicalExcludedLanguages = canonicalExcludedLanguages(excludedLanguages)
        if (canonicalExcludedLanguages.isEmpty()) {
            return false
        }
        return sourceLanguages
            .asSequence()
            .mapNotNull(::canonicalTranslationLanguage)
            .any { it in canonicalExcludedLanguages }
    }

    fun canonicalExcludedLanguages(languages: List<String>): Set<String> =
        languages
            .asSequence()
            .mapNotNull(::canonicalTranslationLanguage)
            .toSet()

    fun isNonTranslatableOnly(payload: TranslationPayload): Boolean {
        val fields = listOfNotNull(payload.content, payload.contentWarning, payload.title, payload.description)
        return fields.isNotEmpty() && fields.all(::isNonTranslatableOnly)
    }

    internal fun canonicalTranslationLanguage(language: String): String? {
        val normalized = language.trim().lowercase().replace('_', '-')
        if (normalized.isBlank()) {
            return null
        }
        val parts = normalized.split('-').filter { it.isNotBlank() }
        if (parts.isEmpty()) {
            return null
        }
        val primary = parts.first()
        if (primary != "zh") {
            return primary
        }
        val regionOrScript = parts.drop(1)
        return when {
            regionOrScript.any { it == "hant" } || regionOrScript.any { it in setOf("tw", "hk", "mo") } -> "zh-hant"
            regionOrScript.any { it == "hans" } || regionOrScript.any { it in setOf("cn", "sg") } -> "zh-hans"
            else -> "zh"
        }
    }

    private fun isNonTranslatableOnly(richText: UiRichText): Boolean {
        var hasVisibleContent = false
        richText.renderRuns.forEach { content ->
            when (content) {
                is RenderContent.BlockImage -> hasVisibleContent = true
                is RenderContent.Text ->
                    content.runs.forEach { run ->
                        when (run) {
                            is RenderRun.Image -> hasVisibleContent = true
                            is RenderRun.Text -> {
                                if (run.text.isBlank()) {
                                    return@forEach
                                }
                                hasVisibleContent = true
                                if (!isNonTranslatableOnlyText(run.text, run.style)) {
                                    return false
                                }
                            }
                        }
                    }
            }
        }
        return hasVisibleContent
    }

    private fun isNonTranslatableOnlyText(
        text: String,
        style: RenderTextStyle,
    ): Boolean {
        if (text.isBlank()) {
            return false
        }
        if (style.code || style.monospace) {
            return true
        }
        var hasVisibleContent = false
        var cursor = 0
        protectedTranslationPattern.findAll(text).forEach { match ->
            if (match.range.first > cursor) {
                val segment = text.substring(cursor, match.range.first)
                if (!segment.isBlank()) {
                    hasVisibleContent = true
                    if (!isEmojiOnlyText(segment)) {
                        return false
                    }
                }
            }
            if (match.value.isNotBlank()) {
                hasVisibleContent = true
            }
            cursor = match.range.last + 1
        }
        if (cursor < text.length) {
            val trailing = text.substring(cursor)
            if (!trailing.isBlank()) {
                hasVisibleContent = true
                if (!isEmojiOnlyText(trailing)) {
                    return false
                }
            }
        }
        return hasVisibleContent
    }

    private fun isEmojiOnlyText(text: String): Boolean {
        if (text.isBlank()) {
            return false
        }
        var hasEmoji = false
        var index = 0
        while (index < text.length) {
            val current = text[index]
            when {
                current.isWhitespace() -> index += 1
                current in '\uD83C'..'\uD83E' && index + 1 < text.length && text[index + 1].isLowSurrogate() -> {
                    hasEmoji = true
                    index += 2
                }

                current.code == 0x200D ||
                    current.code == 0x20E3 ||
                    current.code in 0xFE00..0xFE0F ||
                    current.code in 0x2600..0x27BF -> {
                    hasEmoji = true
                    index += 1
                }

                else -> return false
            }
        }
        return hasEmoji
    }
}
