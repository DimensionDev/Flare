package dev.dimension.flare.ui.render

import kotlinx.datetime.Instant

actual data class UiDateTime(
    val value: Instant,
)

actual fun Instant.toUi(): UiDateTime = UiDateTime(this)
