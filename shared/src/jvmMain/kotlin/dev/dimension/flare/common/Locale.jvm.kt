package dev.dimension.flare.common

import java.util.Locale

internal actual object Locale {
    actual val language: String = Locale.getDefault().language
}
