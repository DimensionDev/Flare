package dev.dimension.flare.data.model.appearance

import androidx.compose.runtime.Immutable

@Immutable
public data class AppearancePatch internal constructor(
    private val values: Map<String, Any>,
) {
    public operator fun <T : Any> get(key: AppearanceKey<T>): T {
        @Suppress("UNCHECKED_CAST")
        return values[key.id] as? T ?: key.default
    }

    public fun <T : Any> contains(key: AppearanceKey<T>): Boolean = values.containsKey(key.id)

    public fun <T : Any> set(
        key: AppearanceKey<T>,
        value: T,
    ): AppearancePatch = copy(values = values + (key.id to value))

    public fun clear(key: AppearanceKey<*>): AppearancePatch = copy(values = values - key.id)

    internal val explicitEntries: Map<String, Any>
        get() = values

    public val theme: dev.dimension.flare.data.model.Theme get() = get(AppearanceKeys.Theme)
    public val dynamicTheme: Boolean get() = get(AppearanceKeys.DynamicTheme)
    public val colorSeed: ULong get() = get(AppearanceKeys.ColorSeed)
    public val avatarShape: dev.dimension.flare.data.model.AvatarShape get() = get(AppearanceKeys.AvatarShape)
    public val pureColorMode: Boolean get() = get(AppearanceKeys.PureColorMode)
    public val fontSizeDiff: Float get() = get(AppearanceKeys.FontSizeDiff)
    public val lineHeightDiff: Float get() = get(AppearanceKeys.LineHeightDiff)
    public val bottomBarStyle: dev.dimension.flare.data.model.BottomBarStyle get() = get(AppearanceKeys.BottomBarStyle)
    public val bottomBarBehavior: dev.dimension.flare.data.model.BottomBarBehavior get() = get(AppearanceKeys.BottomBarBehavior)
    public val inAppBrowser: Boolean get() = get(AppearanceKeys.InAppBrowser)
    public val showComposeInHomeTimeline: Boolean get() = get(AppearanceKeys.ShowComposeInHomeTimeline)
    public val showMedia: Boolean get() = get(AppearanceKeys.ShowMedia)
    public val showSensitiveContent: Boolean get() = get(AppearanceKeys.ShowSensitiveContent)
    public val expandMediaSize: Boolean get() = get(AppearanceKeys.ExpandMediaSize)
    public val videoAutoplay: dev.dimension.flare.data.model.VideoAutoplay get() = get(AppearanceKeys.VideoAutoplay)
    public val showLinkPreview: Boolean get() = get(AppearanceKeys.ShowLinkPreview)
    public val compatLinkPreview: Boolean get() = get(AppearanceKeys.CompatLinkPreview)
    public val showNumbers: Boolean get() = get(AppearanceKeys.ShowNumbers)
    public val postActionStyle: dev.dimension.flare.data.model.PostActionStyle get() = get(AppearanceKeys.PostActionStyle)
    public val fullWidthPost: Boolean get() = get(AppearanceKeys.FullWidthPost)
    public val absoluteTimestamp: Boolean get() = get(AppearanceKeys.AbsoluteTimestamp)
    public val showPlatformLogo: Boolean get() = get(AppearanceKeys.ShowPlatformLogo)
    public val timelineDisplayMode: dev.dimension.flare.data.model.TimelineDisplayMode get() = get(AppearanceKeys.TimelineDisplayMode)

    public companion object {
        public val EMPTY: AppearancePatch = AppearancePatch(emptyMap())
    }
}
