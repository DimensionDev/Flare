package dev.dimension.flare.ui.render

import kotlinx.datetime.Instant

expect class UiDateTime

expect fun Instant.toUi(): UiDateTime
