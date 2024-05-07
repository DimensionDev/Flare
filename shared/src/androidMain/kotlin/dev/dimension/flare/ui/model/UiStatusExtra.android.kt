package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.datetime.Instant
import java.text.Bidi

@Immutable
actual data class UiStatusExtra(
    val contentDirection: LayoutDirection,
    val createdAt: Instant,
) {
    companion object {
        val Empty =
            UiStatusExtra(
                contentDirection = LayoutDirection.Ltr,
                createdAt = Instant.DISTANT_PAST,
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
                createdAt = status.createdAt,
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
                createdAt = status.createdAt,
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
                createdAt = status.indexedAt,
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
                createdAt = status.createdAt,
            )
        }

        is UiStatus.XQTNotification -> {
            UiStatusExtra.Empty
        }
    }
}
