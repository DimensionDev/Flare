package dev.dimension.flare.ui.component.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State

@Composable
internal actual fun rememberPlatformWifiState(): State<Boolean> {
    return androidx.compose.runtime.mutableStateOf(true)
}