package dev.dimension.flare.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

internal fun iOSDarkColorScheme(secondary: Boolean = false): ColorScheme {
    val scheme =
        io.github.alexzhirkevich.cupertino.theme
            .darkColorScheme()
    val backgroundColor =
        if (secondary) {
            scheme.secondarySystemBackground
        } else {
            scheme.systemBackground
        }
    val onBackground = scheme.label
    return darkColorScheme(
        background = backgroundColor,
        onBackground = onBackground,
        primary = scheme.accent,
        tertiary = backgroundColor,
        tertiaryContainer = backgroundColor,
        onTertiary = onBackground,
        onTertiaryContainer = onBackground,
        secondary = backgroundColor,
        secondaryContainer = backgroundColor,
        onSecondary = onBackground,
        onSecondaryContainer = onBackground,
        surface = backgroundColor,
        onSurface = onBackground,
    )
}

internal fun iOSLightColorScheme(secondary: Boolean = false): ColorScheme {
    val scheme =
        io.github.alexzhirkevich.cupertino.theme
            .lightColorScheme()
    val backgroundColor =
        if (secondary) {
            scheme.secondarySystemBackground
        } else {
            scheme.systemBackground
        }
    val onBackground = scheme.label
    return lightColorScheme(
        background = backgroundColor,
        onBackground = onBackground,
        primary = scheme.accent,
        tertiary = backgroundColor,
        tertiaryContainer = backgroundColor,
        onTertiary = onBackground,
        onTertiaryContainer = onBackground,
        secondary = backgroundColor,
        secondaryContainer = backgroundColor,
        onSecondary = onBackground,
        onSecondaryContainer = onBackground,
        surface = backgroundColor,
        onSurface = onBackground,
    )
}
