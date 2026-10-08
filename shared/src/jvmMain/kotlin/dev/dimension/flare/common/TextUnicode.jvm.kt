package dev.dimension.flare.common

import java.text.Normalizer
import java.util.regex.Pattern

private val graphemePattern = Pattern.compile("\\X")

public actual fun String.graphemeCount(): Int {
    val matcher = graphemePattern.matcher(this)
    var count = 0
    while (matcher.find()) count++
    return count
}

public actual fun String.normalizeNfc(): String = Normalizer.normalize(this, Normalizer.Form.NFC)
