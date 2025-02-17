package dev.dimension.flare.data.network.rss

internal actual class NativeWebScraper {
    actual fun parse(
        url: String,
        scriptToInject: String,
        callback: (String) -> Unit,
    ) {
    }
}

