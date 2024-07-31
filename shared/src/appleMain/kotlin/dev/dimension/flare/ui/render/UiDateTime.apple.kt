package dev.dimension.flare.ui.render

import kotlinx.datetime.Instant

actual typealias UiDateTime = Long

actual fun Instant.toUi(): UiDateTime = toEpochMilliseconds()
