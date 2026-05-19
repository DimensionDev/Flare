package dev.dimension.flare.data.model.appearance

import androidx.datastore.core.DataStore
import dev.dimension.flare.data.io.FileStorage
import dev.dimension.flare.data.model.deleteLegacySettingsFile
import dev.dimension.flare.data.model.legacySettingsFileExists
import dev.dimension.flare.data.model.readLegacySettingsFile
import kotlinx.coroutines.flow.first
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.protobuf.ProtoBuf
import okio.Path

public suspend fun migrateAppearanceV1ToV2(
    fileStorage: FileStorage,
    legacyAppearanceSettingsPath: Path,
    bagStore: DataStore<AppearanceBag>,
) {
    if (!legacyAppearanceSettingsExists(fileStorage, legacyAppearanceSettingsPath)) return
    if (bagStore.data
            .first()
            .entries
            .isNotEmpty()
    ) {
        deleteLegacyAppearanceSettings(fileStorage, legacyAppearanceSettingsPath)
        return
    }

    val v1 = readLegacyAppearanceSettings(fileStorage, legacyAppearanceSettingsPath)
    if (v1 != null) {
        bagStore.updateData { v1.toBag() }
    }
    deleteLegacyAppearanceSettings(fileStorage, legacyAppearanceSettingsPath)
}

internal suspend fun legacyAppearanceSettingsExists(
    fileStorage: FileStorage,
    path: Path,
): Boolean = legacySettingsFileExists(fileStorage, path)

@OptIn(ExperimentalSerializationApi::class)
internal suspend fun readLegacyAppearanceSettings(
    fileStorage: FileStorage,
    path: Path,
): LegacyAppearanceSettings? =
    readLegacySettingsFile(fileStorage, path)
        ?.let { bytes ->
            runCatching {
                ProtoBuf.decodeFromByteArray<LegacyAppearanceSettings>(bytes)
            }.getOrNull()
        }

internal suspend fun deleteLegacyAppearanceSettings(
    fileStorage: FileStorage,
    path: Path,
) {
    deleteLegacySettingsFile(fileStorage, path)
}

@Serializable
internal data class LegacyAppearanceSettings(
    val theme: LegacyTheme = LegacyTheme.SYSTEM,
    val dynamicTheme: Boolean = true,
    val colorSeed: ULong = 0x02EBD2u,
    val avatarShape: LegacyAvatarShape = LegacyAvatarShape.CIRCLE,
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
    val videoAutoplay: LegacyVideoAutoplay = LegacyVideoAutoplay.WIFI,
    val expandMediaSize: Boolean = true,
    val compatLinkPreview: Boolean = false,
    val fontSizeDiff: Float = 0f,
    val lineHeightDiff: Float = 0f,
    val showComposeInHomeTimeline: Boolean = true,
    val bottomBarStyle: LegacyBottomBarStyle = LegacyBottomBarStyle.Classic,
    val bottomBarBehavior: LegacyBottomBarBehavior = LegacyBottomBarBehavior.MinimizeOnScroll,
    val inAppBrowser: Boolean = true,
    val fullWidthPost: Boolean = false,
    val postActionStyle: LegacyPostActionStyle = LegacyPostActionStyle.LeftAligned,
    val absoluteTimestamp: Boolean = false,
    val showPlatformLogo: Boolean = true,
    val timelineDisplayMode: LegacyTimelineDisplayMode = LegacyTimelineDisplayMode.Card,
)

@Serializable
internal enum class LegacyPostActionStyle {
    Hidden,
    LeftAligned,
    RightAligned,
    Stretch,
}

@Serializable
internal enum class LegacyBottomBarStyle {
    Floating,
    Classic,
}

@Serializable
internal enum class LegacyBottomBarBehavior {
    AlwaysShow,
    HideOnScroll,
    MinimizeOnScroll,
}

@Serializable
internal enum class LegacyTheme {
    LIGHT,
    DARK,
    SYSTEM,
}

@Serializable
internal enum class LegacyAvatarShape {
    CIRCLE,
    SQUARE,
}

@Serializable
internal enum class LegacyVideoAutoplay {
    ALWAYS,
    WIFI,
    NEVER,
}

@Serializable
internal enum class LegacyTimelineDisplayMode {
    Card,
    Plain,
    Gallery,
}

@OptIn(ExperimentalSerializationApi::class)
internal fun LegacyAppearanceSettings.toBag(): AppearanceBag =
    AppearanceBag(
        entries =
            mapOf(
                entry("app.theme", LegacyTheme.serializer(), theme),
                entry("app.dynamic_theme", Boolean.serializer(), dynamicTheme),
                entry("app.color_seed", ULong.serializer(), colorSeed),
                entry("timeline.avatar_shape", LegacyAvatarShape.serializer(), avatarShape),
                entry("app.pure_color_mode", Boolean.serializer(), pureColorMode),
                entry("timeline.show_numbers", Boolean.serializer(), showNumbers),
                entry("timeline.show_link_preview", Boolean.serializer(), showLinkPreview),
                entry("timeline.show_media", Boolean.serializer(), showMedia),
                entry("timeline.show_sensitive_content", Boolean.serializer(), showSensitiveContent),
                entry("timeline.video_autoplay", LegacyVideoAutoplay.serializer(), videoAutoplay),
                entry("timeline.expand_media_size", Boolean.serializer(), expandMediaSize),
                entry("timeline.compat_link_preview", Boolean.serializer(), compatLinkPreview),
                entry("app.font_size_diff", Float.serializer(), fontSizeDiff),
                entry("app.line_height_diff", Float.serializer(), lineHeightDiff),
                entry("app.show_compose_in_home_timeline", Boolean.serializer(), showComposeInHomeTimeline),
                entry("app.bottom_bar_style", LegacyBottomBarStyle.serializer(), bottomBarStyle),
                entry("app.bottom_bar_behavior", LegacyBottomBarBehavior.serializer(), bottomBarBehavior),
                entry("app.in_app_browser", Boolean.serializer(), inAppBrowser),
                entry("timeline.full_width_post", Boolean.serializer(), fullWidthPost),
                entry("timeline.post_action_style", LegacyPostActionStyle.serializer(), postActionStyle),
                entry("timeline.absolute_timestamp", Boolean.serializer(), absoluteTimestamp),
                entry("timeline.show_platform_logo", Boolean.serializer(), showPlatformLogo),
                entry("timeline.display_mode", LegacyTimelineDisplayMode.serializer(), timelineDisplayMode),
            ),
    )

@OptIn(ExperimentalSerializationApi::class)
private fun <T> entry(
    id: String,
    serializer: KSerializer<T>,
    value: T,
): Pair<String, String> = id to ProtoBuf.encodeToHexString(serializer, value)
