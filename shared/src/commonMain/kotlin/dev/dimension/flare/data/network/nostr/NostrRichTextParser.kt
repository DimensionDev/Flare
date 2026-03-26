package dev.dimension.flare.data.network.nostr

import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.render.RenderContent
import dev.dimension.flare.ui.render.RenderRun
import dev.dimension.flare.ui.render.RenderTextStyle
import dev.dimension.flare.ui.render.UiRichText
import dev.dimension.flare.ui.render.toUiPlainText
import dev.dimension.flare.ui.render.uiRichTextOf
import dev.dimension.flare.ui.route.DeeplinkRoute
import dev.dimension.flare.ui.route.toUri
import kotlinx.collections.immutable.toImmutableList
import rust.nostr.sdk.Nip19Enum as RustNip19Enum

internal data class NostrTextPreprocessResult(
    val text: String,
    val extractedMediaUrls: List<String>,
)

internal data class NostrTextRenderContext(
    val preprocessedText: NostrTextPreprocessResult,
    val mentionedProfilePubKeys: Set<String>,
)

internal fun buildNostrTextRenderContext(
    text: String,
    tags: Array<Array<String>>,
): NostrTextRenderContext {
    val preprocessedText = preprocessNostrText(text, tags)
    return NostrTextRenderContext(
        preprocessedText = preprocessedText,
        mentionedProfilePubKeys = extractMentionedProfilePubkeys(preprocessedText.text),
    )
}

internal fun extractMentionedProfilePubkeys(text: String): Set<String> {
    if (text.isEmpty()) {
        return emptySet()
    }

    val result = mutableSetOf<String>()
    var index = 0
    while (index < text.length) {
        if (text.startsWith("nostr:", index, ignoreCase = true)) {
            val end = text.consumeTokenEnd(index)
            val token = text.substring(index, end).trimTrailingPunctuation()
            token.extractMentionedProfilePubkey()?.let(result::add)
            index = end
        } else {
            index++
        }
    }
    return result
}

internal fun parseNostrRichText(
    text: String,
    accountKey: MicroBlogKey,
): UiRichText =
    parseNostrRichText(
        text = text,
        tags = emptyArray(),
        accountKey = accountKey,
        profiles = emptyMap(),
    )

internal fun parseNostrRichText(
    text: String,
    tags: Array<Array<String>> = emptyArray(),
    accountKey: MicroBlogKey,
    profiles: Map<String, UiProfile> = emptyMap(),
): UiRichText =
    parseNostrRichText(
        context = buildNostrTextRenderContext(text, tags),
        accountKey = accountKey,
        profiles = profiles,
    )

internal fun parseNostrRichText(
    context: NostrTextRenderContext,
    accountKey: MicroBlogKey,
    profiles: Map<String, UiProfile> = emptyMap(),
): UiRichText {
    val sanitizedText = context.preprocessedText.text
    if (sanitizedText.isEmpty()) {
        return "".toUiPlainText()
    }

    val runs =
        buildList<RenderRun> {
            var index = 0
            while (index < sanitizedText.length) {
                when {
                    sanitizedText[index] == '#' -> {
                        val end = sanitizedText.consumeHashtagEnd(index)
                        if (end > index + 1) {
                            val value = sanitizedText.substring(index, end)
                            add(
                                RenderRun.Text(
                                    text = value,
                                    style = RenderTextStyle(link = DeeplinkRoute.Search(AccountType.Specific(accountKey), value).toUri()),
                                ),
                            )
                            index = end
                        } else {
                            add(RenderRun.Text("#"))
                            index++
                        }
                    }

                    sanitizedText.startsWith("nostr:", index, ignoreCase = true) -> {
                        val end = sanitizedText.consumeTokenEnd(index)
                        val token = sanitizedText.substring(index, end)
                        token.toNostrRenderRuns(accountKey, profiles).forEach(::add)
                        index = end
                    }

                    sanitizedText.startsWith("http://", index, ignoreCase = true) ||
                        sanitizedText.startsWith("https://", index, ignoreCase = true) -> {
                        val end = sanitizedText.consumeTokenEnd(index)
                        val token = sanitizedText.substring(index, end)
                        token.toUrlRenderRuns().forEach(::add)
                        index = end
                    }

                    else -> {
                        val end = sanitizedText.consumePlainTextEnd(index)
                        add(RenderRun.Text(sanitizedText.substring(index, end)))
                        index = end
                    }
                }
            }
        }

    return uiRichTextOf(
        renderRuns =
            listOf(
                RenderContent.Text(
                    runs = runs.toImmutableList(),
                ),
            ),
    )
}

internal fun preprocessNostrText(
    text: String,
    tags: Array<Array<String>>,
): NostrTextPreprocessResult {
    val mediaUrlsFromTags =
        buildSet {
            tags.mapNotNull(IMetaTag::parse).flatten().mapTo(this) { it.url }
            tags
                .filter { it.size > 1 && it[0] == "r" }
                .map { it[1] }
                .filter(::looksLikeMediaUrl)
                .forEach(::add)
        }
    val quoteEventIds = tags.mapNotNull(QEventTag::parse).map { it.eventId }.toSet()
    val withoutQuoteText =
        text
            .lineSequence()
            .map { line -> line.removeQuoteReferences(quoteEventIds).trimEnd() }
            .joinToString(separator = "\n")

    val withoutTaggedMediaText = withoutQuoteText.removeTaggedMediaText(mediaUrlsFromTags)
    val extracted = withoutTaggedMediaText.extractTrailingMediaUrls()
    return NostrTextPreprocessResult(
        text = extracted.first.trimTrailingBlankLines(),
        extractedMediaUrls = extracted.second,
    )
}

private fun String.extractMentionedProfilePubkey(): String? {
    val value = removePrefix("nostr:").trim()
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

        else -> null
    }
}

private fun String.removeTaggedMediaText(mediaUrls: Set<String>): String {
    val mediaUrls =
        mediaUrls
    if (mediaUrls.isEmpty()) {
        return this
    }

    val cleaned =
        lineSequence()
            .joinToString(separator = "\n") { line ->
                mediaUrls
                    .fold(line) { acc, url ->
                        acc.replace(url, "")
                    }.trimEnd()
            }
    return cleaned.trimTrailingBlankLines()
}

private fun String.removeQuoteReferences(quoteEventIds: Set<String>): String =
    splitToSequence(' ')
        .filterNot { token ->
            val trimmed = token.trim()
            if (trimmed.isEmpty()) {
                return@filterNot false
            }
            val normalized = trimmed.trimTrailingPunctuation()
            when {
                normalized.startsWith("nostr:", ignoreCase = true) ->
                    normalized.referencesQuotedEvent(quoteEventIds)

                else -> false
            }
        }.joinToString(separator = " ")

private fun String.referencesQuotedEvent(quoteEventIds: Set<String>): Boolean {
    val value = removePrefix("nostr:").trim()
    return when {
        value.startsWith("note1", ignoreCase = true) ->
            withNip19(value) { nip19 ->
                (nip19 as? RustNip19Enum.Note)?.eventId?.use { it.toHex() in quoteEventIds }
            } == true

        value.startsWith("nevent1", ignoreCase = true) ->
            withNip19(value) { nip19 ->
                (nip19 as? RustNip19Enum.Event)?.event?.use { event ->
                    event.eventId().use { it.toHex() in quoteEventIds }
                }
            } == true

        else -> false
    }
}

private fun String.extractTrailingMediaUrls(): Pair<String, List<String>> {
    val lines = lines().toMutableList()
    val extracted = mutableListOf<String>()

    while (lines.isNotEmpty() && lines.last().isBlank()) {
        lines.removeLast()
    }

    while (lines.isNotEmpty()) {
        val line = lines.last()
        val candidate = line.lastWhitespaceSeparatedTokenOrNull()?.trimTrailingPunctuation() ?: break
        if (!looksLikeMediaUrl(candidate)) {
            break
        }
        extracted.add(0, candidate)
        val updatedLine = line.removeTrailingToken(candidate).trimEnd()
        if (updatedLine.isBlank()) {
            lines.removeLast()
            while (lines.isNotEmpty() && lines.last().isBlank()) {
                lines.removeLast()
            }
        } else {
            lines[lines.lastIndex] = updatedLine
        }
    }

    return lines.joinToString(separator = "\n") to extracted
}

private fun String.lastWhitespaceSeparatedTokenOrNull(): String? =
    trimEnd()
        .substringAfterLast(' ', missingDelimiterValue = trimEnd())
        .substringAfterLast('\t', missingDelimiterValue = trimEnd().substringAfterLast(' ', missingDelimiterValue = trimEnd()))
        .takeIf { it.isNotBlank() }

private fun String.removeTrailingToken(token: String): String {
    val trimmedEnd = trimEnd()
    val tokenIndex = trimmedEnd.lastIndexOf(token)
    if (tokenIndex < 0) {
        return this
    }
    val suffix = trimmedEnd.substring(tokenIndex)
    val normalizedSuffix = suffix.trimTrailingPunctuation()
    if (normalizedSuffix != token) {
        return this
    }
    return trimmedEnd.removeRange(tokenIndex, trimmedEnd.length)
}

private fun String.trimTrailingBlankLines(): String =
    lines()
        .dropLastWhile { it.isBlank() }
        .joinToString(separator = "\n")

private fun String.toNostrRenderRuns(
    accountKey: MicroBlogKey,
    profiles: Map<String, UiProfile>,
): List<RenderRun.Text> {
    val trimmed = trimTrailingPunctuation()
    val suffix = removePrefix(trimmed)
    val resolved = trimmed.resolveNostrReference(accountKey, profiles)
    return buildList {
        add(RenderRun.Text(resolved.displayText, resolved.style))
        if (suffix.isNotEmpty()) {
            add(RenderRun.Text(suffix))
        }
    }
}

private fun String.toUrlRenderRuns(): List<RenderRun.Text> {
    val trimmed = trimTrailingPunctuation()
    val suffix = removePrefix(trimmed)
    return buildList {
        add(
            RenderRun.Text(
                text = trimmed.trimUrl(),
                style = RenderTextStyle(link = trimmed),
            ),
        )
        if (suffix.isNotEmpty()) {
            add(RenderRun.Text(suffix))
        }
    }
}

private data class ResolvedNostrReference(
    val displayText: String,
    val style: RenderTextStyle,
)

private fun String.resolveNostrReference(
    accountKey: MicroBlogKey,
    profiles: Map<String, UiProfile>,
): ResolvedNostrReference {
    val value = removePrefix("nostr:").trim()
    return when {
        value.startsWith("npub1", ignoreCase = true) ->
            parseProfileReference(raw = this, value = value, accountKey = accountKey, profiles = profiles)

        value.startsWith("nprofile1", ignoreCase = true) ->
            parseProfileReference(raw = this, value = value, accountKey = accountKey, profiles = profiles)

        value.startsWith("note1", ignoreCase = true) ->
            parseEventReference(raw = this, value = value, accountKey = accountKey)

        value.startsWith("nevent1", ignoreCase = true) ->
            parseEventReference(raw = this, value = value, accountKey = accountKey)

        else -> ResolvedNostrReference(displayText = this, style = RenderTextStyle())
    }
}

private fun parseProfileReference(
    raw: String,
    value: String,
    accountKey: MicroBlogKey,
    profiles: Map<String, UiProfile>,
): ResolvedNostrReference {
    val pubKey =
        when {
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

            else -> null
        }
    if (pubKey == null) {
        return ResolvedNostrReference(displayText = raw, style = RenderTextStyle())
    }

    val displayText =
        profiles[pubKey]
            ?.handleWithoutAt
            ?.takeIf { it.isNotBlank() }
            ?.let { "@$it" }
            ?: raw

    return ResolvedNostrReference(
        displayText = displayText,
        style =
            RenderTextStyle(
                link =
                    DeeplinkRoute.Profile
                        .User(
                            accountType = AccountType.Specific(accountKey),
                            userKey = MicroBlogKey(pubKey, NostrService.NOSTR_HOST),
                        ).toUri(),
            ),
    )
}

private fun parseEventReference(
    raw: String,
    value: String,
    accountKey: MicroBlogKey,
): ResolvedNostrReference {
    val eventId =
        when {
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

            else -> null
        }
    if (eventId == null) {
        return ResolvedNostrReference(displayText = raw, style = RenderTextStyle())
    }

    return ResolvedNostrReference(
        displayText = raw,
        style =
            RenderTextStyle(
                link =
                    DeeplinkRoute.Status
                        .Detail(
                            statusKey = MicroBlogKey(eventId, NostrService.NOSTR_HOST),
                            accountType = AccountType.Specific(accountKey),
                        ).toUri(),
            ),
    )
}

private fun String.consumePlainTextEnd(start: Int): Int {
    var index = start
    while (index < length) {
        if (this[index] == '#') {
            return index
        }
        if (startsWith("nostr:", index, ignoreCase = true) ||
            startsWith("http://", index, ignoreCase = true) ||
            startsWith("https://", index, ignoreCase = true)
        ) {
            return index
        }
        index++
    }
    return index
}

private fun String.consumeTokenEnd(start: Int): Int {
    var index = start
    while (index < length && !this[index].isWhitespace()) {
        index++
    }
    return index
}

private fun String.consumeHashtagEnd(start: Int): Int {
    var index = start + 1
    while (index < length) {
        val ch = this[index]
        if (ch.isWhitespace() || ch in setOf('#', ',', '.', '!', '?', ':', ';', ')', '(', '[', ']', '{', '}', '"', '\'')) {
            break
        }
        index++
    }
    return index
}

private fun String.trimTrailingPunctuation(): String {
    var end = length
    while (end > 0 && this[end - 1] in setOf('.', ',', '!', '?', ':', ';', ')')) {
        end--
    }
    return substring(0, end)
}

private fun String.trimUrl(): String =
    removePrefix("http://")
        .removePrefix("https://")
        .removePrefix("www.")
        .removeSuffix("/")
        .let {
            if (it.length > 30) {
                it.substring(0, 30) + "..."
            } else {
                it
            }
        }

private fun looksLikeMediaUrl(url: String): Boolean =
    url.substringBefore('?').lowercase().let {
        it.endsWith(".jpg") ||
            it.endsWith(".jpeg") ||
            it.endsWith(".png") ||
            it.endsWith(".webp") ||
            it.endsWith(".heic") ||
            it.endsWith(".mp4") ||
            it.endsWith(".webm") ||
            it.endsWith(".mov") ||
            it.endsWith(".m4v") ||
            it.endsWith(".gif") ||
            it.endsWith(".mp3") ||
            it.endsWith(".m4a") ||
            it.endsWith(".aac") ||
            it.endsWith(".wav") ||
            it.endsWith(".ogg")
    }
