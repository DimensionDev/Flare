package dev.dimension.flare.data.model

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.datastore.core.Serializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.InputStream
import java.io.OutputStream

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
)

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
public object AccountPreferencesSerializer : Serializer<AppearanceSettings> {
    override suspend fun readFrom(input: InputStream): AppearanceSettings = ProtoBuf.decodeFromByteArray(input.readBytes())

    override suspend fun writeTo(
        t: AppearanceSettings,
        output: OutputStream,
    ): Unit =
        withContext(Dispatchers.IO) {
            output.write(ProtoBuf.encodeToByteArray(t))
        }

    override val defaultValue: AppearanceSettings
        get() = AppearanceSettings()
}
