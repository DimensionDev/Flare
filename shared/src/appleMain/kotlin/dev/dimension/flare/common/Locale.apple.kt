package dev.dimension.flare.common

import platform.Foundation.NSLocale

internal actual object Locale {
    actual val language: String = NSLocale.languageCode
}
