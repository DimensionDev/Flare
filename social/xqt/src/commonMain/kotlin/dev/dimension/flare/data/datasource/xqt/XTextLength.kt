package dev.dimension.flare.data.datasource.xqt

import de.cketti.codepoints.codePointAt
import dev.dimension.flare.common.normalizeNfc
import moe.tlaster.twitter.parser.TwitterParser
import moe.tlaster.twitter.parser.UrlToken

private const val X_URL_LENGTH = 23
private val xTextParser = TwitterParser(enableDomainDetection = true, enableNonAsciiInUrl = false, enableEscapeInUrl = true)

internal fun String.xWeightedLength(): Int =
    xTextParser.parse(normalizeNfc()).sumOf { token ->
        if (token is UrlToken) X_URL_LENGTH else token.value.xWeightedUnicodeLength()
    }

private fun String.xWeightedUnicodeLength(): Int {
    var index = 0
    var length = 0
    while (index < this.length) {
        val emojiLength = xEmojiLengthAt(this, index)
        if (emojiLength > 0) {
            length += 2
            index += emojiLength
        } else {
            val point = codePointAt(index)
            length += if (point in 0x0000..0x10FF || point in 0x2000..0x200D || point in 0x2010..0x201F || point in 0x2032..0x2037) 1 else 2
            index += if (point > 0xFFFF) 2 else 1
        }
    }
    return length
}
