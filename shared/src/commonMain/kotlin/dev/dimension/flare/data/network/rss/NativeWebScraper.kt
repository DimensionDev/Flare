package dev.dimension.flare.data.network.rss

internal expect class NativeWebScraper {
    fun parse(
        url: String,
        scriptToInject: String = ReadabilityJS,
        callback: (String) -> Unit = {},
    )
}
