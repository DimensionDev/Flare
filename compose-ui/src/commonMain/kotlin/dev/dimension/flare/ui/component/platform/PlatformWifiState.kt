package dev.dimension.flare.ui.component.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State

@Composable
internal expect fun rememberPlatformWifiState(): State<Boolean>
