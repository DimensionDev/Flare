package dev.dimension.flare.data.model

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HideSource
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import dev.dimension.flare.R
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
    val avatarShape: AvatarShape = AvatarShape.CIRCLE,
    val showActions: Boolean = true,
    val showNumbers: Boolean = true,
    val showLinkPreview: Boolean = true,
    val showMedia: Boolean = true,
    val showSensitiveContent: Boolean = false,
    val videoAutoplay: VideoAutoplay = VideoAutoplay.WIFI,
    val swipeGestures: Boolean = false,
    val mastodon: Mastodon = Mastodon(),
    val misskey: Misskey = Misskey(),
    val bluesky: Bluesky = Bluesky(),
) {
    @Serializable
    data class Mastodon(
        val showVisibility: Boolean = true,
        val swipeLeft: SwipeActions = SwipeActions.REPLY,
        val swipeRight: SwipeActions = SwipeActions.NONE,
    ) {
        enum class SwipeActions(
            @StringRes val id: Int,
            val icon: ImageVector,
        ) {
            NONE(R.string.swipe_action_nothing, Icons.Default.HideSource),
            REPLY(R.string.swipe_action_reply, Icons.AutoMirrored.Filled.Reply),
            REBLOG(R.string.swipe_action_reblog, Icons.Default.SyncAlt),
            FAVOURITE(R.string.swipe_action_favourite, Icons.Default.Favorite),
            BOOKMARK(R.string.swipe_action_bookmark, Icons.Default.Bookmark),
        }
    }

    @Serializable
    data class Misskey(
        val showVisibility: Boolean = true,
        val showReaction: Boolean = true,
        val swipeLeft: SwipeActions = SwipeActions.REPLY,
        val swipeRight: SwipeActions = SwipeActions.NONE,
    ) {
        enum class SwipeActions(
            @StringRes val id: Int,
            val icon: ImageVector,
        ) {
            NONE(R.string.swipe_action_nothing, Icons.Default.HideSource),
            REPLY(R.string.swipe_action_reply, Icons.AutoMirrored.Filled.Reply),
            RENOTE(R.string.swipe_action_renote, Icons.Default.SyncAlt),
            FAVOURITE(R.string.swipe_action_favourite, Icons.Default.Favorite),
        }
    }

    @Serializable
    data class Bluesky(
        val swipeLeft: SwipeActions = SwipeActions.REPLY,
        val swipeRight: SwipeActions = SwipeActions.NONE,
    ) {
        enum class SwipeActions(
            @StringRes val id: Int,
            val icon: ImageVector,
        ) {
            NONE(R.string.swipe_action_nothing, Icons.Default.HideSource),
            REPLY(R.string.swipe_action_reply, Icons.AutoMirrored.Filled.Reply),
            REBLOG(R.string.swipe_action_reblog, Icons.Default.SyncAlt),
            FAVOURITE(R.string.swipe_action_favourite, Icons.Default.Favorite),
        }
    }
}

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
    override suspend fun readFrom(input: InputStream): AppearanceSettings {
        return ProtoBuf.decodeFromByteArray(input.readBytes())
    }

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
