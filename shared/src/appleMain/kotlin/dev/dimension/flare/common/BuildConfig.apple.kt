package dev.dimension.flare.common

import kotlin.experimental.ExperimentalNativeApi


internal actual object BuildConfig {
    @OptIn(ExperimentalNativeApi::class)
    actual val debug: Boolean
        get() = Platform.isDebugBinary
}