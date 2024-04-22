package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.LayoutDirection
import java.text.Bidi

@Immutable
actual data class UiStatusExtra(
    val contentDirection: LayoutDirection,
) {
    companion object {
        val Empty =
            UiStatusExtra(
                contentDirection = LayoutDirection.Ltr,
            )
    }
}

val UiStatus.contentDirection get() = extra.contentDirection

internal actual fun createStatusExtra(status: UiStatus): UiStatusExtra {
    return when (status) {
        is UiStatus.Mastodon -> {
            UiStatusExtra(
                contentDirection =
                    if (Bidi(
                            status.content,
                            Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT,
                        ).baseIsLeftToRight()
                    ) {
                        LayoutDirection.Ltr
                    } else {
                        LayoutDirection.Rtl
                    },
            )
        }

        is UiStatus.MastodonNotification -> {
            UiStatusExtra.Empty
        }

        is UiStatus.Misskey -> {
            UiStatusExtra(
                contentDirection =
                    if (Bidi(
                            status.content,
                            Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT,
                        ).baseIsLeftToRight()
                    ) {
                        LayoutDirection.Ltr
                    } else {
                        LayoutDirection.Rtl
                    },
            )
        }

        is UiStatus.MisskeyNotification -> {
            UiStatusExtra.Empty
        }

        is UiStatus.Bluesky -> {
            UiStatusExtra(
                contentDirection =
                    if (Bidi(
                            status.content,
                            Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT,
                        ).baseIsLeftToRight()
                    ) {
                        LayoutDirection.Ltr
                    } else {
                        LayoutDirection.Rtl
                    },
            )
        }
        is UiStatus.BlueskyNotification -> {
            UiStatusExtra.Empty
        }

        is UiStatus.XQT -> {
            UiStatusExtra(
                contentDirection =
                    if (Bidi(
                            status.content,
                            Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT,
                        ).baseIsLeftToRight()
                    ) {
                        LayoutDirection.Ltr
                    } else {
                        LayoutDirection.Rtl
                    },
            )
        }

        is UiStatus.XQTNotification -> {
            UiStatusExtra.Empty
        }
    }
}
