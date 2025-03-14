package dev.dimension.flare.ui.component

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf

public val LocalComponentAppearance: ProvidableCompositionLocal<ComponentAppearance> = staticCompositionLocalOf { ComponentAppearance() }

public data class ComponentAppearance(
    val dynamicTheme: Boolean = true,
    val avatarShape: AvatarShape = AvatarShape.CIRCLE,
    val showActions: Boolean = true,
    val showNumbers: Boolean = true,
    val showLinkPreview: Boolean = true,
    val showMedia: Boolean = true,
    val showSensitiveContent: Boolean = false,
    val videoAutoplay: VideoAutoplay = VideoAutoplay.WIFI,
    val expandMediaSize: Boolean = false,
    val compatLinkPreview: Boolean = false,
) {
    public enum class AvatarShape {
        CIRCLE,
        SQUARE,
    }

    public enum class VideoAutoplay {
        ALWAYS,
        WIFI,
        NEVER,
    }
}
