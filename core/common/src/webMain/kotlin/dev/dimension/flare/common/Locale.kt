package dev.dimension.flare.common

public actual object Locale {
    public actual val language: String
        // TODO: get language from platform
        get() = "en"
}
