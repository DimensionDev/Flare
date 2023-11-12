package dev.dimension.flare.ui.model

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

internal actual fun createStatusExtra(status: UiStatus): UiStatusExtra {
    return UiStatusExtra(
        contentMarkdown =
            when (status) {
                is UiStatus.Mastodon -> status.contentToken.toMarkdown()
                is UiStatus.MastodonNotification -> ""
                is UiStatus.Misskey -> status.contentToken.toMarkdown()
                is UiStatus.MisskeyNotification -> ""
                is UiStatus.Bluesky -> status.contentToken.toMarkdown()
                is UiStatus.BlueskyNotification -> ""
            },
    )
}
