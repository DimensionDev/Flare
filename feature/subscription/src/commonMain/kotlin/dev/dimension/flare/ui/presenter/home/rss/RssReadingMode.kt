package dev.dimension.flare.ui.presenter.home.rss

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateMapOf
import dev.dimension.flare.data.database.app.model.RssDisplayMode

@Immutable
public data class RssReadingMode(
    val url: String,
    val displayMode: RssDisplayMode,
    val checking: Boolean,
    val recommended: RssDisplayMode?,
    val automatic: Boolean,
)

internal class RssReadingModeSelection {
    private val choices = mutableStateMapOf<String, RssDisplayMode>()
    private val recommendations = mutableStateMapOf<String, RssDisplayMode?>()

    fun select(
        url: String,
        mode: RssDisplayMode,
    ) {
        choices[url] = mode
    }

    fun recommend(
        url: String,
        mode: RssDisplayMode?,
    ) {
        recommendations[url] = mode
    }

    fun state(
        url: String,
        preset: RssDisplayMode? = null,
        checking: Boolean = true,
    ): RssReadingMode =
        RssReadingMode(
            url = url,
            displayMode = choices[url] ?: preset ?: recommendations[url] ?: RssDisplayMode.FULL_CONTENT,
            checking = checking && preset == null && !recommendations.containsKey(url),
            recommended = recommendations[url],
            automatic = preset == null && !choices.containsKey(url),
        )
}
