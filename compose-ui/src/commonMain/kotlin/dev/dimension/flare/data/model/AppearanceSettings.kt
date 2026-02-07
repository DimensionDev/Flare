package dev.dimension.flare.data.model

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.datastore.core.okio.OkioSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import okio.BufferedSink
import okio.BufferedSource

public val LocalAppearanceSettings: ProvidableCompositionLocal<AppearanceSettings> =
    staticCompositionLocalOf { AppearanceSettings() }

@OptIn(ExperimentalSerializationApi::class)
@Serializable
public data class AppearanceSettings(
    @ProtoNumber(1)
    val theme: Theme = Theme.SYSTEM,
    @ProtoNumber(2)
    val dynamicTheme: Boolean = true,
    @ProtoNumber(3)
    val colorSeed: ULong = Color(red = 103, green = 80, blue = 164).value,
    @ProtoNumber(4)
    val avatarShape: AvatarShape = AvatarShape.CIRCLE,
    @Deprecated(
        "Use postActionStyle instead",
        ReplaceWith("postActionStyle"),
        DeprecationLevel.ERROR,
    )
    @ProtoNumber(5)
    val showActions: Boolean = true,
    @ProtoNumber(6)
    val pureColorMode: Boolean = true,
    @ProtoNumber(7)
    val showNumbers: Boolean = true,
    @ProtoNumber(8)
    val showLinkPreview: Boolean = true,
    @ProtoNumber(9)
    val showMedia: Boolean = true,
    // Hide reposts toggle: when true, client-side filters should remove reposts from timelines (not search).
    @ProtoNumber(10)
    val hideReposts: Boolean = false,
    @ProtoNumber(11)
    val showSensitiveContent: Boolean = false,
    @ProtoNumber(12)
    val videoAutoplay: VideoAutoplay = VideoAutoplay.WIFI,
    @ProtoNumber(13)
    val expandMediaSize: Boolean = true,
    @ProtoNumber(14)
    val compatLinkPreview: Boolean = false,
    @ProtoNumber(15)
    val fontSizeDiff: Float = 0f,
    @ProtoNumber(16)
    val lineHeightDiff: Float = 0f,
    @ProtoNumber(17)
    val showComposeInHomeTimeline: Boolean = true,
    @ProtoNumber(18)
    val bottomBarStyle: BottomBarStyle = BottomBarStyle.Floating,
    @ProtoNumber(19)
    val bottomBarBehavior: BottomBarBehavior = BottomBarBehavior.MinimizeOnScroll,
    @ProtoNumber(20)
    val inAppBrowser: Boolean = true,
    @ProtoNumber(21)
    val fullWidthPost: Boolean = false,
    @ProtoNumber(22)
    val postActionStyle: PostActionStyle = PostActionStyle.LeftAligned,
    @ProtoNumber(23)
    val absoluteTimestamp: Boolean = false,
    @ProtoNumber(24)
    val showPlatformLogo: Boolean = true,
    // Hide replies toggle: when true, client-side filters should remove replies to others from timelines (excludes self-replies).
    @ProtoNumber(25)
    val hideReplies: Boolean = false,
) {
    public companion object {
        // for iOS
        public val Default: AppearanceSettings = AppearanceSettings()
    }
}

public enum class PostActionStyle {
    Hidden,
    LeftAligned,
    RightAligned,
    Stretch,
}

public enum class BottomBarStyle {
    Floating,
    Classic,
}

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

@OptIn(ExperimentalSerializationApi::class)
public object AccountPreferencesSerializer : OkioSerializer<AppearanceSettings> {
    override val defaultValue: AppearanceSettings
        get() = AppearanceSettings()

    override suspend fun readFrom(source: BufferedSource): AppearanceSettings =
        withContext(Dispatchers.IO) {
            // Be tolerant of corrupted or legacy protobuf data.
            // If decoding fails (for example an unexpected value in the stored bytes),
            // return the default AppearanceSettings instead of throwing and crashing the app.
            runCatching {
                ProtoBuf.decodeFromByteArray<AppearanceSettings>(source.readByteArray())
            }.getOrElse { _ ->
                // Returning default will allow the app to start with sane settings.
                AppearanceSettings()
            }
        }

    override suspend fun writeTo(
        t: AppearanceSettings,
        sink: BufferedSink,
    ) {
        withContext(Dispatchers.IO) {
            sink.write(ProtoBuf.encodeToByteArray(t))
        }
    }
}
