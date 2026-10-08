package dev.dimension.flare.common

import io.github.kotlinmania.unicodesegmentation.UnicodeSegmentation

// ponytail: Pin Unicode 16.0 counts across OS versions; upgrade when server rules require newer data.
public fun String.graphemeCount(): Int = UnicodeSegmentation.graphemes(this, isExtended = true).count()

public expect fun String.normalizeNfc(): String
