package dev.dimension.flare.data.model.appearance

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import dev.dimension.flare.data.model.AvatarShape as AppearanceAvatarShape
import dev.dimension.flare.data.model.BottomBarBehavior as AppearanceBottomBarBehavior
import dev.dimension.flare.data.model.BottomBarStyle as AppearanceBottomBarStyle
import dev.dimension.flare.data.model.PostActionStyle as AppearancePostActionStyle
import dev.dimension.flare.data.model.Theme as AppearanceTheme
import dev.dimension.flare.data.model.TimelineDisplayMode as AppearanceTimelineDisplayMode
import dev.dimension.flare.data.model.VideoAutoplay as AppearanceVideoAutoplay

public object AppearanceKeys {
    public object Theme : Global<AppearanceTheme>("app.theme", AppearanceTheme.SYSTEM, AppearanceTheme.serializer())

    public object DynamicTheme : Global<Boolean>("app.dynamic_theme", true, Boolean.serializer())

    public object ColorSeed : Global<ULong>("app.color_seed", 0x02EBD2u, ULong.serializer())

    public object AvatarShape : PerTimeline<AppearanceAvatarShape>(
        "timeline.avatar_shape",
        AppearanceAvatarShape.CIRCLE,
        AppearanceAvatarShape.serializer(),
    )

    public object PureColorMode : Global<Boolean>("app.pure_color_mode", true, Boolean.serializer())

    public object FontSizeDiff : Global<Float>("app.font_size_diff", 0f, Float.serializer())

    public object LineHeightDiff : Global<Float>("app.line_height_diff", 0f, Float.serializer())

    public object BottomBarStyle : Global<AppearanceBottomBarStyle>(
        "app.bottom_bar_style",
        AppearanceBottomBarStyle.Floating,
        AppearanceBottomBarStyle.serializer(),
    )

    public object BottomBarBehavior : Global<AppearanceBottomBarBehavior>(
        "app.bottom_bar_behavior",
        AppearanceBottomBarBehavior.MinimizeOnScroll,
        AppearanceBottomBarBehavior.serializer(),
    )

    public object InAppBrowser : Global<Boolean>("app.in_app_browser", true, Boolean.serializer())

    public object ShowComposeInHomeTimeline : Global<Boolean>("app.show_compose_in_home_timeline", true, Boolean.serializer())

    public object ShowMedia : PerTimeline<Boolean>("timeline.show_media", true, Boolean.serializer())

    public object ShowSensitiveContent : PerTimeline<Boolean>("timeline.show_sensitive_content", false, Boolean.serializer())

    public object ExpandMediaSize : PerTimeline<Boolean>("timeline.expand_media_size", true, Boolean.serializer())

    public object VideoAutoplay : PerTimeline<AppearanceVideoAutoplay>(
        "timeline.video_autoplay",
        AppearanceVideoAutoplay.WIFI,
        AppearanceVideoAutoplay.serializer(),
    )

    public object ShowLinkPreview : PerTimeline<Boolean>("timeline.show_link_preview", true, Boolean.serializer())

    public object CompatLinkPreview : PerTimeline<Boolean>("timeline.compat_link_preview", false, Boolean.serializer())

    public object ShowNumbers : PerTimeline<Boolean>("timeline.show_numbers", true, Boolean.serializer())

    public object PostActionStyle : PerTimeline<AppearancePostActionStyle>(
        "timeline.post_action_style",
        AppearancePostActionStyle.LeftAligned,
        AppearancePostActionStyle.serializer(),
    )

    public object FullWidthPost : PerTimeline<Boolean>("timeline.full_width_post", false, Boolean.serializer())

    public object AbsoluteTimestamp : PerTimeline<Boolean>("timeline.absolute_timestamp", false, Boolean.serializer())

    public object ShowPlatformLogo : PerTimeline<Boolean>("timeline.show_platform_logo", true, Boolean.serializer())

    public object TimelineDisplayMode : PerTimeline<AppearanceTimelineDisplayMode>(
        "timeline.display_mode",
        AppearanceTimelineDisplayMode.Card,
        AppearanceTimelineDisplayMode.serializer(),
    )

    internal val all: List<AppearanceKey<*>> =
        listOf(
            Theme,
            DynamicTheme,
            ColorSeed,
            AvatarShape,
            PureColorMode,
            FontSizeDiff,
            LineHeightDiff,
            BottomBarStyle,
            BottomBarBehavior,
            InAppBrowser,
            ShowComposeInHomeTimeline,
            ShowMedia,
            ShowSensitiveContent,
            ExpandMediaSize,
            VideoAutoplay,
            ShowLinkPreview,
            CompatLinkPreview,
            ShowNumbers,
            PostActionStyle,
            FullWidthPost,
            AbsoluteTimestamp,
            ShowPlatformLogo,
            TimelineDisplayMode,
        )

    private val byId: Map<String, AppearanceKey<*>> = all.associateBy { it.id }

    public operator fun get(id: String): AppearanceKey<*>? = byId[id]

    public abstract class Global<T : Any>(
        final override val id: String,
        final override val default: T,
        final override val serializer: KSerializer<T>,
    ) : GlobalAppearanceKey<T>

    public abstract class PerTimeline<T : Any>(
        final override val id: String,
        final override val default: T,
        final override val serializer: KSerializer<T>,
    ) : PerTimelineAppearanceKey<T>
}
