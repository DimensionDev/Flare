package dev.dimension.flare.ui.model

import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.logoUrl
import dev.dimension.flare.model.vvo
import dev.dimension.flare.model.vvoHost
import dev.dimension.flare.model.vvoHostLong
import dev.dimension.flare.model.vvoHostShort
import dev.dimension.flare.ui.render.UiDateTime
import io.ktor.http.Url
import sh.christian.ozone.api.xrpc.BSKY_SOCIAL

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
            if (parsedUrl.host == BSKY_SOCIAL.host) {
                return "https://web-cdn.bsky.app/static/apple-touch-icon.png"
            } else if (parsedUrl.host in listOf(vvo, vvoHostShort, vvoHost, vvoHostLong)) {
                return PlatformType.VVo.logoUrl
            } else {
                return "https://${parsedUrl.host}/favicon.ico"
            }
        }
    }
}
