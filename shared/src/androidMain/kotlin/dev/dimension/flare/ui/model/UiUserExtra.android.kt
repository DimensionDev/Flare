package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.LayoutDirection
import java.text.Bidi

@Immutable
actual data class UiUserExtra(
    val nameDirection: LayoutDirection,
    val descriptionDirection: LayoutDirection,
)

val UiUser.nameDirection get() = extra.nameDirection
val UiUser.descriptionDirection get() = extra.descriptionDirection

internal actual fun createUiUserExtra(user: UiUser): UiUserExtra =
    when (user) {
        is UiUser.Mastodon ->
            UiUserExtra(
                nameDirection =
                    if (Bidi(
                            user.name,
                            Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT,
                        ).baseIsLeftToRight()
                    ) {
                        LayoutDirection.Ltr
                    } else {
                        LayoutDirection.Rtl
                    },
                descriptionDirection =
                    if (Bidi(
                            user.raw.note ?: "",
                            Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT,
                        ).baseIsLeftToRight()
                    ) {
                        LayoutDirection.Ltr
                    } else {
                        LayoutDirection.Rtl
                    },
            )
        is UiUser.Misskey ->
            UiUserExtra(
                nameDirection =
                    if (Bidi(
                            user.name,
                            Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT,
                        ).baseIsLeftToRight()
                    ) {
                        LayoutDirection.Ltr
                    } else {
                        LayoutDirection.Rtl
                    },
                descriptionDirection =
                    if (Bidi(
                            user.description ?: "",
                            Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT,
                        ).baseIsLeftToRight()
                    ) {
                        LayoutDirection.Ltr
                    } else {
                        LayoutDirection.Rtl
                    },
            )

        is UiUser.Bluesky ->
            UiUserExtra(
                nameDirection =
                    if (Bidi(
                            user.displayName,
                            Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT,
                        ).baseIsLeftToRight()
                    ) {
                        LayoutDirection.Ltr
                    } else {
                        LayoutDirection.Rtl
                    },
                descriptionDirection =
                    if (Bidi(
                            user.description ?: "",
                            Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT,
                        ).baseIsLeftToRight()
                    ) {
                        LayoutDirection.Ltr
                    } else {
                        LayoutDirection.Rtl
                    },
            )

        is UiUser.XQT ->
            UiUserExtra(
                nameDirection =
                    if (Bidi(
                            user.displayName,
                            Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT,
                        ).baseIsLeftToRight()
                    ) {
                        LayoutDirection.Ltr
                    } else {
                        LayoutDirection.Rtl
                    },
                descriptionDirection =
                    if (Bidi(
                            user.description ?: "",
                            Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT,
                        ).baseIsLeftToRight()
                    ) {
                        LayoutDirection.Ltr
                    } else {
                        LayoutDirection.Rtl
                    },
            )

        is UiUser.VVO ->
            UiUserExtra(
                nameDirection = LayoutDirection.Ltr,
                descriptionDirection = LayoutDirection.Ltr,
            )
    }
