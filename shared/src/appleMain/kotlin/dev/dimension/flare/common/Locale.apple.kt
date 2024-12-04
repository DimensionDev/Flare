package dev.dimension.flare.common

import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.languageCode

internal actual object Locale {
    actual val language: String = NSLocale.currentLocale.languageCode
}
