package dev.dimension.flare.ui.render

import kotlin.time.Instant

public expect class UiDateTime

internal expect fun Instant.toUi(): UiDateTime

internal expect operator fun UiDateTime.compareTo(other: UiDateTime): Int
