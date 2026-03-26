package dev.dimension.flare.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.ui.model.UiIcon
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64

@Immutable
@Serializable
public enum class PlatformType {
    Nostr,
    Mastodon,
    Misskey,
    Bluesky,

    @Suppress("EnumEntryName") // nothing wrong with this name :)
    xQt,

    VVo,
}

@Immutable
public data class PlatformTypeMetadata(
    val displayName: String,
    val icon: UiIcon,
)

public val xqtOldHost: String =
    buildString {
        append(Base64.decode("dHc=").decodeToString())
        append(Base64.decode("aXR0").decodeToString())
        append(Base64.decode("ZXI=").decodeToString())
        append(Base64.decode("LmNvbQ==").decodeToString())
    }

public val xqtHost: String =
    buildString {
        append("x")
        append(".com")
    }

public val vvo: String =
    buildString {
        append(Base64.decode("d2Vp").decodeToString())
        append(Base64.decode("Ym8=").decodeToString())
    }

public val vvoHost: String =
    buildString {
        append(Base64.decode("bS53").decodeToString())
        append(Base64.decode("ZWli").decodeToString())
        append(Base64.decode("by5jbg==").decodeToString())
    }

public val vvoHostShort: String =
    buildString {
        append(vvo)
        append(Base64.decode("LmNu").decodeToString())
    }

public val vvoHostLong: String =
    buildString {
        append(Base64.decode("d2Vp").decodeToString())
        append(Base64.decode("Ym8uY29t").decodeToString())
    }
