package dev.dimension.flare.data.model

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

val LocalAppearanceSettings = staticCompositionLocalOf { AppearanceSettings() }

@Serializable
data class AppearanceSettings(
    val theme: Theme = Theme.SYSTEM,
    val dynamicTheme: Boolean = true,
    val colorSeed: ULong = Color.Blue.value,
    val avatarShape: AvatarShape = AvatarShape.CIRCLE,
    val showActions: Boolean = true,
    val pureColorMode: Boolean = true,
    val showNumbers: Boolean = true,
    val showLinkPreview: Boolean = true,
    val showMedia: Boolean = true,
    val showSensitiveContent: Boolean = false,
    val videoAutoplay: VideoAutoplay = VideoAutoplay.WIFI,
    val expandMediaSize: Boolean = false,
    val compatLinkPreview: Boolean = false,
)

enum class Theme {
    LIGHT,
    DARK,
    SYSTEM,
}

enum class AvatarShape {
    CIRCLE,
    SQUARE,
}

enum class VideoAutoplay {
    ALWAYS,
    WIFI,
    NEVER,
}
