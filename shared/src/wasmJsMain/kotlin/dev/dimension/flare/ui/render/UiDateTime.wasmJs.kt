package dev.dimension.flare.ui.render

import kotlin.time.Instant

public actual typealias PlatformDateTime = Instant

internal actual fun Instant.toPlatform(): PlatformDateTime = this
