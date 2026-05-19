package dev.dimension.flare.data.model

import kotlinx.serialization.Serializable

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
