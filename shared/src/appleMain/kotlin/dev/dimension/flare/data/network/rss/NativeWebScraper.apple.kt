package dev.dimension.flare.data.network.rss

import dev.dimension.flare.common.encodeJson

internal actual class NativeWebScraper(
    private val appleWebScraper: AppleWebScraper
) {
    actual fun parse(
        url: String,
        scriptToInject: String,
        callback: (String) -> Unit,
    ) {
        appleWebScraper.parse(
            url = url,
            callback = {
                callback.invoke(it.encodeJson())
            },
        )
    }
}

public interface AppleWebScraper {
    public fun parse(
        url: String,
        callback: (DocumentData) -> Unit,
    )
}