package dev.dimension.flare.ui.model

import dev.dimension.flare.ui.render.UiDateTime
import io.ktor.http.Url

public data class UiRssSource internal constructor(
    val id: Int,
    val url: String,
    val title: String?,
    val lastUpdate: UiDateTime,
) {
    val host: String by lazy {
        Url(url).host
    }
    val favIcon: String by lazy {
        favIconUrl(url)
    }

    public companion object {
        public fun favIconUrl(url: String): String {
            val parsedUrl =
                if (url.startsWith("http")) {
                    Url(url)
                } else {
                    Url("https://$url")
                }
            return "https://${parsedUrl.host}/favicon.ico"
        }
    }
}
