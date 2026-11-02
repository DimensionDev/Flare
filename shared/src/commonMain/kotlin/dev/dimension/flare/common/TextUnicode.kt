package dev.dimension.flare.common

import de.cketti.codepoints.codePointAt

// ponytail: Pin Unicode 16.0 counts across OS versions; upgrade when server rules require newer data.
public fun String.graphemeCount(): Int {
    var index = 0
    var count = 0
    var previous = -1
    var regionalIndicators = 0
    var emojiSuffix = false
    var emojiBeforeZwj = false
    var indicConsonant = false
    var indicLinker = false
    while (index < length) {
        val point = codePointAt(index)
        val properties = graphemeProperties(point)
        val current = properties and 0x0F
        val indic = properties and 0x30
        val emoji = properties and 0x40 != 0
        // UAX #29 GB1, GB3–GB9c, GB11–GB13, and GB999, in precedence order.
        val boundary =
            when {
                previous == -1 -> true
                previous == CR && current == LF -> false
                previous in CR..CONTROL || current in CR..CONTROL -> true
                previous == L && (current == L || current == V || current == LV || current == LVT) -> false
                (previous == LV || previous == V) && (current == V || current == T) -> false
                (previous == LVT || previous == T) && current == T -> false
                current == EXTEND || current == ZWJ || current == SPACING_MARK || previous == PREPEND -> false
                indic == INDIC_CONSONANT && indicLinker -> false
                previous == ZWJ && emojiBeforeZwj && emoji -> false
                previous == REGIONAL_INDICATOR && current == REGIONAL_INDICATOR && regionalIndicators % 2 == 1 -> false
                else -> true
            }
        if (boundary) count++
        emojiBeforeZwj = current == ZWJ && emojiSuffix
        emojiSuffix = emoji || (current == EXTEND && emojiSuffix)
        when (indic) {
            INDIC_CONSONANT -> {
                indicConsonant = true
                indicLinker = false
            }
            INDIC_LINKER -> indicLinker = indicConsonant
            INDIC_EXTEND -> Unit
            else -> {
                indicConsonant = false
                indicLinker = false
            }
        }
        regionalIndicators = if (current == REGIONAL_INDICATOR) regionalIndicators + 1 else 0
        previous = current
        index += if (point > 0xFFFF) 2 else 1
    }
    return count
}

public expect fun String.normalizeNfc(): String

private const val CR = 1
private const val LF = 2
private const val CONTROL = 3
private const val EXTEND = 4
private const val ZWJ = 5
private const val REGIONAL_INDICATOR = 6
private const val PREPEND = 7
private const val SPACING_MARK = 8
private const val L = 9
private const val V = 10
private const val T = 11
private const val LV = 12
private const val LVT = 13
private const val INDIC_CONSONANT = 0x10
private const val INDIC_EXTEND = 0x20
private const val INDIC_LINKER = 0x30
