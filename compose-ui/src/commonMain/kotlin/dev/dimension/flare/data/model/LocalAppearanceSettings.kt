package dev.dimension.flare.data.model

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public val LocalAppearanceSettings: ProvidableCompositionLocal<AppearanceSettings> =
    staticCompositionLocalOf { AppearanceSettings() }
