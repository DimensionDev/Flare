package dev.dimension.flare.common

import kotlin.experimental.ExperimentalNativeApi

public actual object BuildConfig {
    @OptIn(ExperimentalNativeApi::class)
    public actual val debug: Boolean
        get() = Platform.isDebugBinary
}
