package dev.dimension.flare.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalWindowInfo

@Composable
internal actual fun platformMediaActive(): Boolean = LocalWindowInfo.current.isWindowFocused
