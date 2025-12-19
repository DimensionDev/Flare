package dev.dimension.flare.common

import android.os.Build

internal actual object SystemUtils {
    actual val isBlurSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}
