package dev.dimension.flare.ui.render

import kotlinx.datetime.Instant

public actual data class UiDateTime internal constructor(
    val value: Instant,
)

internal actual fun Instant.toUi(): UiDateTime = UiDateTime(this)
