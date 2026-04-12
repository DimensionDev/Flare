package dev.dimension.flare.common

import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier

public actual object Locale {
    public actual val language: String
        get() =
            NSLocale.Companion.currentLocale.localeIdentifier
                .replace('_', '-')
}
