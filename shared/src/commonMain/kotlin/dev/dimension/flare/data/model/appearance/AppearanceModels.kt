package dev.dimension.flare.data.model.appearance

import androidx.compose.runtime.Immutable
import dev.dimension.flare.data.datasource.microblog.PostActionLayoutConfig
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
    val showBottomBarLabels: Boolean = AppearanceKeys.ShowBottomBarLabels.default,
    val deckMode: Boolean = AppearanceKeys.DeckMode.default,
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
    val expandContentWarning: Boolean = AppearanceKeys.ExpandContentWarning.default,
    val expandMediaSize: Boolean = AppearanceKeys.ExpandMediaSize.default,
    val limitMediaGridToNine: Boolean = AppearanceKeys.LimitMediaGridToNine.default,
    val videoAutoplay: VideoAutoplay = AppearanceKeys.VideoAutoplay.default,
    val showLinkPreview: Boolean = AppearanceKeys.ShowLinkPreview.default,
    val compatLinkPreview: Boolean = AppearanceKeys.CompatLinkPreview.default,
    val showNumbers: Boolean = AppearanceKeys.ShowNumbers.default,
    val postActionStyle: PostActionStyle = AppearanceKeys.PostActionStyle.default,
    val postActionLayout: PostActionLayoutConfig = AppearanceKeys.PostActionLayout.default,
    val postActionFixedWidth: Boolean = AppearanceKeys.PostActionFixedWidth.default,
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
        val agent: Boolean = false,
        val showOriginalWithTranslation: Boolean = false,
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
        showBottomBarLabels = get(AppearanceKeys.ShowBottomBarLabels),
        deckMode = get(AppearanceKeys.DeckMode),
    )

public fun AppearancePatch.toTimelineAppearance(): TimelineAppearance = toTimelineAppearance(override = null)

public fun AppearancePatch.toTimelineAppearance(override: AppearancePatch?): TimelineAppearance =
    TimelineAppearance(
        avatarShape = getTimelineValue(AppearanceKeys.AvatarShape, override),
        showMedia = getTimelineValue(AppearanceKeys.ShowMedia, override),
        showSensitiveContent = getTimelineValue(AppearanceKeys.ShowSensitiveContent, override),
        expandContentWarning = getTimelineValue(AppearanceKeys.ExpandContentWarning, override),
        expandMediaSize = getTimelineValue(AppearanceKeys.ExpandMediaSize, override),
        limitMediaGridToNine = getTimelineValue(AppearanceKeys.LimitMediaGridToNine, override),
        videoAutoplay = getTimelineValue(AppearanceKeys.VideoAutoplay, override),
        showLinkPreview = getTimelineValue(AppearanceKeys.ShowLinkPreview, override),
        compatLinkPreview = getTimelineValue(AppearanceKeys.CompatLinkPreview, override),
        showNumbers = getTimelineValue(AppearanceKeys.ShowNumbers, override),
        postActionStyle = getTimelineValue(AppearanceKeys.PostActionStyle, override),
        postActionLayout = getTimelineValue(AppearanceKeys.PostActionLayout, override),
        postActionFixedWidth = getTimelineValue(AppearanceKeys.PostActionFixedWidth, override),
        fullWidthPost = getTimelineValue(AppearanceKeys.FullWidthPost, override),
        absoluteTimestamp = getTimelineValue(AppearanceKeys.AbsoluteTimestamp, override),
        showPlatformLogo = getTimelineValue(AppearanceKeys.ShowPlatformLogo, override),
        timelineDisplayMode = getTimelineValue(AppearanceKeys.TimelineDisplayMode, override),
    )

public fun TimelineAppearance.withPatch(patch: AppearancePatch?): TimelineAppearance {
    if (patch == null) return this
    return copy(
        avatarShape = patch.getTimelineValue(AppearanceKeys.AvatarShape, avatarShape),
        showMedia = patch.getTimelineValue(AppearanceKeys.ShowMedia, showMedia),
        showSensitiveContent = patch.getTimelineValue(AppearanceKeys.ShowSensitiveContent, showSensitiveContent),
        expandContentWarning = patch.getTimelineValue(AppearanceKeys.ExpandContentWarning, expandContentWarning),
        expandMediaSize = patch.getTimelineValue(AppearanceKeys.ExpandMediaSize, expandMediaSize),
        limitMediaGridToNine = patch.getTimelineValue(AppearanceKeys.LimitMediaGridToNine, limitMediaGridToNine),
        videoAutoplay = patch.getTimelineValue(AppearanceKeys.VideoAutoplay, videoAutoplay),
        showLinkPreview = patch.getTimelineValue(AppearanceKeys.ShowLinkPreview, showLinkPreview),
        compatLinkPreview = patch.getTimelineValue(AppearanceKeys.CompatLinkPreview, compatLinkPreview),
        showNumbers = patch.getTimelineValue(AppearanceKeys.ShowNumbers, showNumbers),
        postActionStyle = patch.getTimelineValue(AppearanceKeys.PostActionStyle, postActionStyle),
        postActionLayout = patch.getTimelineValue(AppearanceKeys.PostActionLayout, postActionLayout),
        postActionFixedWidth = patch.getTimelineValue(AppearanceKeys.PostActionFixedWidth, postActionFixedWidth),
        fullWidthPost = patch.getTimelineValue(AppearanceKeys.FullWidthPost, fullWidthPost),
        absoluteTimestamp = patch.getTimelineValue(AppearanceKeys.AbsoluteTimestamp, absoluteTimestamp),
        showPlatformLogo = patch.getTimelineValue(AppearanceKeys.ShowPlatformLogo, showPlatformLogo),
        timelineDisplayMode = patch.getTimelineValue(AppearanceKeys.TimelineDisplayMode, timelineDisplayMode),
    )
}

private fun <T : Any> AppearancePatch.getTimelineValue(
    key: PerTimelineAppearanceKey<T>,
    override: AppearancePatch?,
): T =
    if (override?.contains(key) == true) {
        override[key]
    } else {
        this[key]
    }

private fun <T : Any> AppearancePatch.getTimelineValue(
    key: PerTimelineAppearanceKey<T>,
    fallback: T,
): T =
    if (contains(key)) {
        this[key]
    } else {
        fallback
    }
