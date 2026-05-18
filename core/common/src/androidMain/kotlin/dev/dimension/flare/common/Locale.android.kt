package dev.dimension.flare.common

import java.util.Locale

public actual object Locale {
    public actual val language: String
        get() = Locale.getDefault().toLanguageTag()
}
