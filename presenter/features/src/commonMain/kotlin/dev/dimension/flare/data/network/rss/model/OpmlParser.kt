package dev.dimension.flare.data.network.rss.model

import dev.dimension.flare.common.Xml
import kotlinx.serialization.decodeFromString

// Matches XML attributes so we can sanitize their values without touching text nodes.
private val attributeRegex = Regex("""([:\w-]+)(\s*=\s*)("([^"]*)"|'([^']*)')""")

// Detects ampersands that are not already part of a valid entity (e.g. &amp; or &#123;).
private val danglingAmpersandRegex = Regex("&(?!(?:[a-zA-Z]+|#[0-9]+|#x[0-9a-fA-F]+);)")

internal fun decodeOpml(opmlContent: String): Opml {
    val sanitized = sanitizeDanglingAmpersands(opmlContent)
    return Xml.decodeFromString<Opml>(sanitized)
}

private fun sanitizeDanglingAmpersands(xml: String): String =
    attributeRegex.replace(xml) { matchResult ->
        val attrName = matchResult.groupValues[1]
        val spacing = matchResult.groupValues[2]
        val doubleQuotedValue = matchResult.groupValues[4]
        val singleQuotedValue = matchResult.groupValues[5]

        val isDoubleQuoted = doubleQuotedValue.isNotEmpty()
        val rawValue = if (isDoubleQuoted) doubleQuotedValue else singleQuotedValue
        val sanitizedValue = danglingAmpersandRegex.replace(rawValue) { "&amp;" }
        val quotedValue = if (isDoubleQuoted) "\"$sanitizedValue\"" else "'$sanitizedValue'"

        "$attrName$spacing$quotedValue"
    }
