package dev.dimension.flare.ui.component.platform

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

public val LocalWifiState: ProvidableCompositionLocal<Boolean> =
    staticCompositionLocalOf {
        false
    }
