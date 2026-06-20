package dev.dimension.flare.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.ui.model.UiIcon
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64
import kotlin.native.HiddenFromObjC

@Immutable
@Serializable
public enum class PlatformType {
    Mastodon,
    Misskey,
    Bluesky,
    Pixiv,

    @Suppress("EnumEntryName") // nothing wrong with this name :)
    xQt,

    VVo,
    Nostr,
    Fanbox,
}

@Immutable
public data class PlatformTypeMetadata(
    val displayName: String,
    val icon: UiIcon,
)

@HiddenFromObjC
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

@HiddenFromObjC
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

@HiddenFromObjC
public val vvoHostShort: String =
    buildString {
        append(vvo)
        append(Base64.decode("LmNu").decodeToString())
    }

@HiddenFromObjC
public val vvoHostLong: String =
    buildString {
        append(Base64.decode("d2Vp").decodeToString())
        append(Base64.decode("Ym8uY29t").decodeToString())
    }

@HiddenFromObjC
public val ilink: String =
    buildString {
        append(Base64.decode("aHR0cHM6Ly90").decodeToString())
        append(Base64.decode("Lm1lLytWWjYzZnFOUQ==").decodeToString())
        append(Base64.decode("WElBME16Vmw=").decodeToString())
    }
