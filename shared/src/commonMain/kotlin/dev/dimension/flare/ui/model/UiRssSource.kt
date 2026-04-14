package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.data.database.app.model.RssDisplayMode
import dev.dimension.flare.data.database.app.model.SubscriptionType
import dev.dimension.flare.model.vvo
import dev.dimension.flare.model.vvoHost
import dev.dimension.flare.model.vvoHostLong
import dev.dimension.flare.model.vvoHostShort
import dev.dimension.flare.ui.render.UiDateTime
import io.ktor.http.Url
import sh.christian.ozone.api.xrpc.BSKY_SOCIAL

@Immutable
public data class UiRssSource internal constructor(
    val id: Int,
    val url: String,
    val title: String?,
    val lastUpdate: UiDateTime,
    val favIcon: String?,
    val displayMode: RssDisplayMode = RssDisplayMode.FULL_CONTENT,
    val type: SubscriptionType = SubscriptionType.RSS,
) {
    val host: String by lazy {
        when (type) {
            SubscriptionType.RSS -> Url(url).host
            else -> url // For Mastodon types, url is already the host
        }
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
                return "https://upload.wikimedia.org/wikipedia/en/thumb/6/6e/Sina_Weibo.svg/2560px-Sina_Weibo.svg.png"
            } else {
                return "https://${parsedUrl.host}/favicon.ico"
            }
        }
    }
}
