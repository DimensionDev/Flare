package dev.dimension.flare.data.datasource.xqt

import de.cketti.codepoints.deluxe.codePointSequence
import moe.tlaster.twitter.parser.TwitterParser
import moe.tlaster.twitter.parser.UrlToken

private const val X_URL_LENGTH = 23
private const val ZERO_WIDTH_JOINER = 0x200D
private const val VARIATION_SELECTOR_16 = 0xFE0F
private const val COMBINING_KEYCAP = 0x20E3
private const val BLACK_FLAG = 0x1F3F4
private const val CANCEL_TAG = 0xE007F

private val xTextParser by lazy {
    TwitterParser(
        enableDomainDetection = true,
        enableNonAsciiInUrl = false,
    )
}

internal fun String.xWeightedLength(): Int =
    xTextParser.parse(this).sumOf { token ->
        if (token is UrlToken) X_URL_LENGTH else token.value.xWeightedUnicodeLength()
    }

private fun String.xWeightedUnicodeLength(): Int {
    // ponytail: Compact emoji matcher; replace with generated twitter-text data if X adds sequences this misses.
    val codePoints = codePointSequence().map { it.value }.toList()
    var index = 0
    var length = 0
    while (index < codePoints.size) {
        val emojiEnd = codePoints.emojiEnd(index)
        if (emojiEnd > index) {
            length += 2
            index = emojiEnd
        } else {
            length += codePoints[index].xWeight()
            index++
        }
    }
    return length
}

private fun List<Int>.emojiEnd(start: Int): Int {
    val first = this[start]
    if (first.isRegionalIndicator() && getOrNull(start + 1)?.isRegionalIndicator() == true) {
        return start + 2
    }
    if (first.isKeycapBase()) {
        var end = start + 1
        if (getOrNull(end) == VARIATION_SELECTOR_16) end++
        return if (getOrNull(end) == COMBINING_KEYCAP) end + 1 else start
    }
    if (!first.isEmojiBase()) return start

    var end = consumeEmojiModifiers(start + 1)
    if (first == BLACK_FLAG) {
        var tagEnd = end
        while (getOrNull(tagEnd)?.let { it in 0xE0020..0xE007E } == true) tagEnd++
        if (getOrNull(tagEnd) == CANCEL_TAG) return tagEnd + 1
    }
    while (getOrNull(end) == ZERO_WIDTH_JOINER && getOrNull(end + 1)?.isEmojiBase() == true) {
        end = consumeEmojiModifiers(end + 2)
    }
    return end
}

private fun List<Int>.consumeEmojiModifiers(start: Int): Int {
    var end = start
    while (
        getOrNull(end) == VARIATION_SELECTOR_16 ||
        getOrNull(end)?.let { it in 0x1F3FB..0x1F3FF } == true
    ) {
        end++
    }
    return end
}

private fun Int.xWeight(): Int =
    if (
        this in 0x0000..0x10FF ||
        this in 0x2000..0x200D ||
        this in 0x2010..0x201F ||
        this in 0x2032..0x2037
    ) {
        1
    } else {
        2
    }

private fun Int.isKeycapBase(): Boolean = this == '#'.code || this == '*'.code || this in '0'.code..'9'.code

private fun Int.isRegionalIndicator(): Boolean = this in 0x1F1E6..0x1F1FF

private fun Int.isEmojiBase(): Boolean =
    this == 0x00A9 ||
        this == 0x00AE ||
        this == 0x203C ||
        this == 0x2049 ||
        this == 0x2122 ||
        this == 0x2139 ||
        this in 0x2194..0x2199 ||
        this in 0x21A9..0x21AA ||
        this in 0x231A..0x231B ||
        this == 0x2328 ||
        this == 0x23CF ||
        this in 0x23E9..0x23F3 ||
        this in 0x23F8..0x23FA ||
        this == 0x24C2 ||
        this in 0x25AA..0x25AB ||
        this == 0x25B6 ||
        this == 0x25C0 ||
        this in 0x25FB..0x25FE ||
        this in 0x2600..0x27BF ||
        this in 0x2934..0x2935 ||
        this in 0x2B05..0x2B07 ||
        this in 0x2B1B..0x2B1C ||
        this == 0x2B50 ||
        this == 0x2B55 ||
        this == 0x3030 ||
        this == 0x303D ||
        this == 0x3297 ||
        this == 0x3299 ||
        this in 0x1F000..0x1FAFF
