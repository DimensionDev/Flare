package dev.dimension.flare.ui.component.platform

import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
internal actual fun isBigScreen(): Boolean {
    val windowInfo = calculateWindowSizeClass()
    return windowInfo.widthSizeClass >= WindowWidthSizeClass.Medium
}
