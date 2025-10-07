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
import okio.BufferedSink
import okio.BufferedSource

public val LocalAppearanceSettings: ProvidableCompositionLocal<AppearanceSettings> = staticCompositionLocalOf { AppearanceSettings() }

@Serializable
public data class AppearanceSettings(
    val theme: Theme = Theme.SYSTEM,
    val dynamicTheme: Boolean = true,
    val colorSeed: ULong = Color(red = 103, green = 80, blue = 164).value,
    val avatarShape: AvatarShape = AvatarShape.CIRCLE,
    val showActions: Boolean = true,
    val pureColorMode: Boolean = true,
    val showNumbers: Boolean = true,
    val showLinkPreview: Boolean = true,
    val showMedia: Boolean = true,
    val showSensitiveContent: Boolean = false,
    val videoAutoplay: VideoAutoplay = VideoAutoplay.WIFI,
    val expandMediaSize: Boolean = false,
    val compatLinkPreview: Boolean = false,
    val fontSizeDiff: Float = 0f,
    val lineHeightDiff: Float = 0f,
) {
    public companion object {
        // for iOS
        public val Default: AppearanceSettings = AppearanceSettings()
    }
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
            ProtoBuf.decodeFromByteArray(source.readByteArray())
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
