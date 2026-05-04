package dev.dimension.flare.data.model

import kotlinx.serialization.Serializable

@Serializable
public data class AppearanceSettings(
    val theme: Theme = Theme.SYSTEM,
    val dynamicTheme: Boolean = true,
    val colorSeed: ULong = 0x02EBD2u,
    val avatarShape: AvatarShape = AvatarShape.CIRCLE,
    @Deprecated(
        "Use postActionStyle instead",
        ReplaceWith("postActionStyle"),
        DeprecationLevel.ERROR,
    )
    val showActions: Boolean = true,
    val pureColorMode: Boolean = true,
    val showNumbers: Boolean = true,
    val showLinkPreview: Boolean = true,
    val showMedia: Boolean = true,
    val showSensitiveContent: Boolean = false,
    val videoAutoplay: VideoAutoplay = VideoAutoplay.WIFI,
    val expandMediaSize: Boolean = true,
    val compatLinkPreview: Boolean = false,
    val fontSizeDiff: Float = 0f,
    val lineHeightDiff: Float = 0f,
    val showComposeInHomeTimeline: Boolean = true,
    val bottomBarStyle: BottomBarStyle = BottomBarStyle.Floating,
    val bottomBarBehavior: BottomBarBehavior = BottomBarBehavior.MinimizeOnScroll,
    val inAppBrowser: Boolean = true,
    val fullWidthPost: Boolean = false,
    val postActionStyle: PostActionStyle = PostActionStyle.LeftAligned,
    val absoluteTimestamp: Boolean = false,
    val showPlatformLogo: Boolean = true,
    val timelineDisplayMode: TimelineDisplayMode = TimelineDisplayMode.Card,
) {
    public companion object {
        // for iOS
        public val Default: AppearanceSettings = AppearanceSettings()
    }
}

@Serializable
public enum class PostActionStyle {
    Hidden,
    LeftAligned,
    RightAligned,
    Stretch,
}

@Serializable
public enum class BottomBarStyle {
    Floating,
    Classic,
}

@Serializable
public enum class BottomBarBehavior {
    AlwaysShow,
    HideOnScroll,
    MinimizeOnScroll,
}

@Serializable
public enum class Theme {
    LIGHT,
    DARK,
    SYSTEM,
}

@Serializable
public enum class AvatarShape {
    CIRCLE,
    SQUARE,
}

@Serializable
public enum class VideoAutoplay {
    ALWAYS,
    WIFI,
    NEVER,
}

@Serializable
public enum class TimelineDisplayMode {
    Card,
    Plain,
    Gallery,
}
