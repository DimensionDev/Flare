package dev.dimension.flare.data.model

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.InputStream
import java.io.OutputStream

val LocalAppearanceSettings = staticCompositionLocalOf { AppearanceSettings() }

@Serializable
data class AppearanceSettings(
    val theme: Theme = Theme.SYSTEM,
    val dynamicTheme: Boolean = true,
    val colorSeed: ULong = Color.Blue.value,
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

enum class Theme {
    LIGHT,
    DARK,
    SYSTEM,
}

enum class AvatarShape {
    CIRCLE,
    SQUARE,
}

enum class VideoAutoplay {
    ALWAYS,
    WIFI,
    NEVER,
}

@OptIn(ExperimentalSerializationApi::class)
private object AccountPreferencesSerializer : Serializer<AppearanceSettings> {
    override suspend fun readFrom(input: InputStream): AppearanceSettings = ProtoBuf.decodeFromByteArray(input.readBytes())

    override suspend fun writeTo(
        t: AppearanceSettings,
        output: OutputStream,
    ) = withContext(Dispatchers.IO) {
        output.write(ProtoBuf.encodeToByteArray(t))
    }

    override val defaultValue: AppearanceSettings
        get() = AppearanceSettings()
}

internal val Context.appearanceSettings: DataStore<AppearanceSettings> by dataStore(
    fileName = "appearance_settings.pb",
    serializer = AccountPreferencesSerializer,
)
