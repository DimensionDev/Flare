package dev.dimension.flare.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jthemedetecor.OsThemeDetector
import java.util.function.Consumer

private val detector = OsThemeDetector.getDetector()

@Composable
internal fun isSystemInDarkTheme(): Boolean {
    var isDarkTheme by remember {
        mutableStateOf(detector.isDark)
    }
    val listener =
        remember {
            Consumer<Boolean> { isDark ->
                isDarkTheme = isDark
            }
        }
    DisposableEffect(Unit) {
        detector.registerListener(listener)
        onDispose {
            detector.removeListener(listener)
        }
    }
    return isDarkTheme
}
