package dev.dimension.flare.ui.model

import dev.dimension.flare.ui.render.UiDateTime
import io.ktor.http.Url

public data class UiRssSource(
    val id: Int,
    val url: String,
    val title: String?,
    val lastUpdate: UiDateTime,
) {
    val host: String by lazy {
        Url(url).host
    }
}
