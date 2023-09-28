package dev.dimension.flare.ui.model

actual class UiStatusExtra(
    val content: String
)

internal actual fun createStatusExtra(status: UiStatus): UiStatusExtra {
    return UiStatusExtra(
        content = when (status) {
            is UiStatus.Mastodon -> status.content
            is UiStatus.MastodonNotification -> status.type.name
            is UiStatus.Misskey -> status.content
            is UiStatus.MisskeyNotification -> status.type.value
        }
    )
}