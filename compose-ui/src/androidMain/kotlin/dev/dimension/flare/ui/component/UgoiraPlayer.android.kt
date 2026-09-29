package dev.dimension.flare.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect

@Composable
internal actual fun platformMediaActive(): Boolean {
    var active by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        active = true
        onPauseOrDispose { active = false }
    }
    return active
}
