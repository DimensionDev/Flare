package dev.dimension.flare.data.model.appearance

import androidx.compose.runtime.Immutable
import dev.dimension.flare.data.model.AvatarShape
import dev.dimension.flare.data.model.BottomBarBehavior
import dev.dimension.flare.data.model.BottomBarStyle
import dev.dimension.flare.data.model.PostActionStyle
import dev.dimension.flare.data.model.Theme
import dev.dimension.flare.data.model.TimelineDisplayMode
import dev.dimension.flare.data.model.VideoAutoplay

@Immutable
public data class GlobalAppearance(
    val theme: Theme = AppearanceKeys.Theme.default,
    val dynamicTheme: Boolean = AppearanceKeys.DynamicTheme.default,
    val colorSeed: ULong = AppearanceKeys.ColorSeed.default,
    val pureColorMode: Boolean = AppearanceKeys.PureColorMode.default,
    val fontSizeDiff: Float = AppearanceKeys.FontSizeDiff.default,
    val lineHeightDiff: Float = AppearanceKeys.LineHeightDiff.default,
    val bottomBarStyle: BottomBarStyle = AppearanceKeys.BottomBarStyle.default,
    val bottomBarBehavior: BottomBarBehavior = AppearanceKeys.BottomBarBehavior.default,
    val inAppBrowser: Boolean = AppearanceKeys.InAppBrowser.default,
    val showComposeInHomeTimeline: Boolean = AppearanceKeys.ShowComposeInHomeTimeline.default,
) {
    public companion object {
        public val Default: GlobalAppearance = GlobalAppearance()
    }
}

@Immutable
public data class TimelineAppearance(
    val avatarShape: AvatarShape = AppearanceKeys.AvatarShape.default,
    val showMedia: Boolean = AppearanceKeys.ShowMedia.default,
    val showSensitiveContent: Boolean = AppearanceKeys.ShowSensitiveContent.default,
    val expandMediaSize: Boolean = AppearanceKeys.ExpandMediaSize.default,
    val videoAutoplay: VideoAutoplay = AppearanceKeys.VideoAutoplay.default,
    val showLinkPreview: Boolean = AppearanceKeys.ShowLinkPreview.default,
    val compatLinkPreview: Boolean = AppearanceKeys.CompatLinkPreview.default,
    val showNumbers: Boolean = AppearanceKeys.ShowNumbers.default,
    val postActionStyle: PostActionStyle = AppearanceKeys.PostActionStyle.default,
    val fullWidthPost: Boolean = AppearanceKeys.FullWidthPost.default,
    val absoluteTimestamp: Boolean = AppearanceKeys.AbsoluteTimestamp.default,
    val showPlatformLogo: Boolean = AppearanceKeys.ShowPlatformLogo.default,
    val timelineDisplayMode: TimelineDisplayMode = AppearanceKeys.TimelineDisplayMode.default,
    val aiConfig: AiConfig = AiConfig(),
    val lineLimit: Int = 5,
    val showTranslateButton: Boolean = true,
) {
    public companion object {
        public val Default: TimelineAppearance = TimelineAppearance()
    }

    public data class AiConfig(
        val translation: Boolean = false,
        val tldr: Boolean = false,
    )
}

public fun AppearancePatch.toGlobalAppearance(): GlobalAppearance =
    GlobalAppearance(
        theme = get(AppearanceKeys.Theme),
        dynamicTheme = get(AppearanceKeys.DynamicTheme),
        colorSeed = get(AppearanceKeys.ColorSeed),
        pureColorMode = get(AppearanceKeys.PureColorMode),
        fontSizeDiff = get(AppearanceKeys.FontSizeDiff),
        lineHeightDiff = get(AppearanceKeys.LineHeightDiff),
        bottomBarStyle = get(AppearanceKeys.BottomBarStyle),
        bottomBarBehavior = get(AppearanceKeys.BottomBarBehavior),
        inAppBrowser = get(AppearanceKeys.InAppBrowser),
        showComposeInHomeTimeline = get(AppearanceKeys.ShowComposeInHomeTimeline),
    )

public fun AppearancePatch.toTimelineAppearance(): TimelineAppearance = toTimelineAppearance(override = null)

public fun AppearancePatch.toTimelineAppearance(override: AppearancePatch?): TimelineAppearance =
    TimelineAppearance(
        avatarShape = getTimelineValue(AppearanceKeys.AvatarShape, override),
        showMedia = getTimelineValue(AppearanceKeys.ShowMedia, override),
        showSensitiveContent = getTimelineValue(AppearanceKeys.ShowSensitiveContent, override),
        expandMediaSize = getTimelineValue(AppearanceKeys.ExpandMediaSize, override),
        videoAutoplay = getTimelineValue(AppearanceKeys.VideoAutoplay, override),
        showLinkPreview = getTimelineValue(AppearanceKeys.ShowLinkPreview, override),
        compatLinkPreview = getTimelineValue(AppearanceKeys.CompatLinkPreview, override),
        showNumbers = getTimelineValue(AppearanceKeys.ShowNumbers, override),
        postActionStyle = getTimelineValue(AppearanceKeys.PostActionStyle, override),
        fullWidthPost = getTimelineValue(AppearanceKeys.FullWidthPost, override),
        absoluteTimestamp = getTimelineValue(AppearanceKeys.AbsoluteTimestamp, override),
        showPlatformLogo = getTimelineValue(AppearanceKeys.ShowPlatformLogo, override),
        timelineDisplayMode = getTimelineValue(AppearanceKeys.TimelineDisplayMode, override),
    )

private fun <T : Any> AppearancePatch.getTimelineValue(
    key: PerTimelineAppearanceKey<T>,
    override: AppearancePatch?,
): T =
    if (override?.contains(key) == true) {
        override[key]
    } else {
        this[key]
    }
