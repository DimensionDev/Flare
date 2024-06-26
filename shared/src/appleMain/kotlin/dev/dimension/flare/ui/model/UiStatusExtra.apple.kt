package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable

@Immutable
actual class UiStatusExtra(
    val contentMarkdown: String,
) {
    companion object {
        val Empty =
            UiStatusExtra(
                contentMarkdown = "",
            )
    }
}

internal actual fun createStatusExtra(status: UiStatus): UiStatusExtra =
    UiStatusExtra(
        contentMarkdown =
            when (status) {
                is UiStatus.Mastodon -> status.contentToken.toMarkdown()
                is UiStatus.MastodonNotification -> ""
                is UiStatus.Misskey -> status.contentToken.toMarkdown()
                is UiStatus.MisskeyNotification -> ""
                is UiStatus.Bluesky -> status.contentToken.toMarkdown()
                is UiStatus.BlueskyNotification -> ""
                is UiStatus.XQT -> status.contentToken.toMarkdown()
                is UiStatus.XQTNotification -> ""
                is UiStatus.VVO -> status.contentToken.toMarkdown()
                is UiStatus.VVONotification -> ""
                is UiStatus.VVOComment -> status.contentToken.toMarkdown()
            },
    )
