package dev.dimension.flare.ui.render

import kotlinx.datetime.Instant

public expect class UiDateTime

internal expect fun Instant.toUi(): UiDateTime
