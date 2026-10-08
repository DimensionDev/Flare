package dev.dimension.flare.common

import android.icu.text.BreakIterator
import java.text.Normalizer
import java.util.Locale

public actual fun String.graphemeCount(): Int {
    val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
    iterator.setText(this)
    var count = 0
    while (iterator.next() != BreakIterator.DONE) count++
    return count
}

public actual fun String.normalizeNfc(): String = Normalizer.normalize(this, Normalizer.Form.NFC)
