package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.data.database.app.model.RssDisplayMode
import dev.dimension.flare.data.database.app.model.SubscriptionType
import dev.dimension.flare.ui.render.UiDateTime
import io.ktor.http.Url

@Immutable
public data class UiRssSource public constructor(
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
}
