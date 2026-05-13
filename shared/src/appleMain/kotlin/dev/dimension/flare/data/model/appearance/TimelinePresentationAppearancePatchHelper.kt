package dev.dimension.flare.data.model.appearance

import dev.dimension.flare.data.model.AvatarShape
import dev.dimension.flare.data.model.PostActionStyle
import dev.dimension.flare.data.model.TimelineDisplayMode
import dev.dimension.flare.data.model.VideoAutoplay

public object TimelinePresentationAppearancePatchHelper {
    public val empty: AppearancePatch = AppearancePatch.Companion.EMPTY

    public fun resolve(
        base: TimelineAppearance,
        patch: AppearancePatch?,
    ): TimelineAppearance = base.withPatch(patch)

    public fun layoutOverridesEnabled(patch: AppearancePatch): Boolean =
        patch.contains(AppearanceKeys.TimelineDisplayMode) ||
            patch.contains(AppearanceKeys.FullWidthPost) ||
            patch.contains(AppearanceKeys.PostActionStyle) ||
            patch.contains(AppearanceKeys.ShowNumbers)

    public fun displayOverridesEnabled(patch: AppearancePatch): Boolean =
        patch.contains(AppearanceKeys.AbsoluteTimestamp) ||
            patch.contains(AppearanceKeys.ShowPlatformLogo) ||
            patch.contains(AppearanceKeys.ShowLinkPreview) ||
            patch.contains(AppearanceKeys.CompatLinkPreview)

    public fun mediaOverridesEnabled(patch: AppearancePatch): Boolean =
        patch.contains(AppearanceKeys.ShowMedia) ||
            patch.contains(AppearanceKeys.ShowSensitiveContent) ||
            patch.contains(AppearanceKeys.ExpandMediaSize) ||
            patch.contains(AppearanceKeys.VideoAutoplay)

    public fun themeOverridesEnabled(patch: AppearancePatch): Boolean = patch.contains(AppearanceKeys.AvatarShape)

    public fun enableLayoutOverrides(
        patch: AppearancePatch,
        appearance: TimelineAppearance,
    ): AppearancePatch =
        patch
            .set(AppearanceKeys.TimelineDisplayMode, appearance.timelineDisplayMode)
            .set(AppearanceKeys.FullWidthPost, appearance.fullWidthPost)
            .set(AppearanceKeys.PostActionStyle, appearance.postActionStyle)
            .set(AppearanceKeys.ShowNumbers, appearance.showNumbers)

    public fun disableLayoutOverrides(patch: AppearancePatch): AppearancePatch =
        patch
            .clear(AppearanceKeys.TimelineDisplayMode)
            .clear(AppearanceKeys.FullWidthPost)
            .clear(AppearanceKeys.PostActionStyle)
            .clear(AppearanceKeys.ShowNumbers)

    public fun enableDisplayOverrides(
        patch: AppearancePatch,
        appearance: TimelineAppearance,
    ): AppearancePatch =
        patch
            .set(AppearanceKeys.AbsoluteTimestamp, appearance.absoluteTimestamp)
            .set(AppearanceKeys.ShowPlatformLogo, appearance.showPlatformLogo)
            .set(AppearanceKeys.ShowLinkPreview, appearance.showLinkPreview)
            .set(AppearanceKeys.CompatLinkPreview, appearance.compatLinkPreview)

    public fun disableDisplayOverrides(patch: AppearancePatch): AppearancePatch =
        patch
            .clear(AppearanceKeys.AbsoluteTimestamp)
            .clear(AppearanceKeys.ShowPlatformLogo)
            .clear(AppearanceKeys.ShowLinkPreview)
            .clear(AppearanceKeys.CompatLinkPreview)

    public fun enableMediaOverrides(
        patch: AppearancePatch,
        appearance: TimelineAppearance,
    ): AppearancePatch =
        patch
            .set(AppearanceKeys.ShowMedia, appearance.showMedia)
            .set(AppearanceKeys.ShowSensitiveContent, appearance.showSensitiveContent)
            .set(AppearanceKeys.ExpandMediaSize, appearance.expandMediaSize)
            .set(AppearanceKeys.VideoAutoplay, appearance.videoAutoplay)

    public fun disableMediaOverrides(patch: AppearancePatch): AppearancePatch =
        patch
            .clear(AppearanceKeys.ShowMedia)
            .clear(AppearanceKeys.ShowSensitiveContent)
            .clear(AppearanceKeys.ExpandMediaSize)
            .clear(AppearanceKeys.VideoAutoplay)

    public fun enableThemeOverrides(
        patch: AppearancePatch,
        appearance: TimelineAppearance,
    ): AppearancePatch = patch.set(AppearanceKeys.AvatarShape, appearance.avatarShape)

    public fun disableThemeOverrides(patch: AppearancePatch): AppearancePatch = patch.clear(AppearanceKeys.AvatarShape)

    public fun setTimelineDisplayMode(
        patch: AppearancePatch,
        value: TimelineDisplayMode,
    ): AppearancePatch = patch.set(AppearanceKeys.TimelineDisplayMode, value)

    public fun setFullWidthPost(
        patch: AppearancePatch,
        value: Boolean,
    ): AppearancePatch = patch.set(AppearanceKeys.FullWidthPost, value)

    public fun setPostActionStyle(
        patch: AppearancePatch,
        value: PostActionStyle,
    ): AppearancePatch = patch.set(AppearanceKeys.PostActionStyle, value)

    public fun setShowNumbers(
        patch: AppearancePatch,
        value: Boolean,
    ): AppearancePatch = patch.set(AppearanceKeys.ShowNumbers, value)

    public fun setAbsoluteTimestamp(
        patch: AppearancePatch,
        value: Boolean,
    ): AppearancePatch = patch.set(AppearanceKeys.AbsoluteTimestamp, value)

    public fun setShowPlatformLogo(
        patch: AppearancePatch,
        value: Boolean,
    ): AppearancePatch = patch.set(AppearanceKeys.ShowPlatformLogo, value)

    public fun setShowLinkPreview(
        patch: AppearancePatch,
        value: Boolean,
    ): AppearancePatch = patch.set(AppearanceKeys.ShowLinkPreview, value)

    public fun setCompatLinkPreview(
        patch: AppearancePatch,
        value: Boolean,
    ): AppearancePatch = patch.set(AppearanceKeys.CompatLinkPreview, value)

    public fun setShowMedia(
        patch: AppearancePatch,
        value: Boolean,
    ): AppearancePatch = patch.set(AppearanceKeys.ShowMedia, value)

    public fun setShowSensitiveContent(
        patch: AppearancePatch,
        value: Boolean,
    ): AppearancePatch = patch.set(AppearanceKeys.ShowSensitiveContent, value)

    public fun setExpandMediaSize(
        patch: AppearancePatch,
        value: Boolean,
    ): AppearancePatch = patch.set(AppearanceKeys.ExpandMediaSize, value)

    public fun setVideoAutoplay(
        patch: AppearancePatch,
        value: VideoAutoplay,
    ): AppearancePatch = patch.set(AppearanceKeys.VideoAutoplay, value)

    public fun setAvatarShape(
        patch: AppearancePatch,
        value: AvatarShape,
    ): AppearancePatch = patch.set(AppearanceKeys.AvatarShape, value)
}
