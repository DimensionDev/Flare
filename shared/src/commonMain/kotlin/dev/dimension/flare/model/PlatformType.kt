package dev.dimension.flare.model

import androidx.compose.runtime.Immutable
import io.ktor.util.decodeBase64String
import kotlinx.serialization.Serializable

@Immutable
@Serializable
public enum class PlatformType {
    Mastodon,
    Misskey,
    Bluesky,

    @Suppress("EnumEntryName") // nothing wrong with this name :)
    xQt,

    VVo,
}

public val PlatformType.logoUrl: String
    get() =
        when (this) {
            PlatformType.Mastodon -> "https://joinmastodon.org/logos/logo-purple.svg"
            PlatformType.Misskey ->
                "https://github.com/misskey-dev/misskey/blob/develop/packages" +
                    "/backend/assets/favicon.png?raw=true"
            PlatformType.Bluesky -> "https://blueskyweb.xyz/images/apple-touch-icon.png"
            PlatformType.xQt ->
                "https://upload.wikimedia.org/wikipedia/commons/thumb/5/53" +
                    "/X_logo_2023_original.svg/1920px-X_logo_2023_original.svg.png"
            PlatformType.VVo ->
                "https://symbl-world.akamaized.net/i/webp/48/" +
                    "0a286d8ee91af2e78ff2ee8e5065c3.webp"
        }

public val xqtOldHost: String =
    buildString {
        append("dHc=".decodeBase64String())
        append("aXR0".decodeBase64String())
        append("ZXI=".decodeBase64String())
        append("LmNvbQ==".decodeBase64String())
    }

public val xqtHost: String =
    buildString {
        append("x")
        append(".com")
    }

public val vvo: String =
    buildString {
        append("d2Vp".decodeBase64String())
        append("Ym8=".decodeBase64String())
    }

public val vvoHost: String =
    buildString {
        append("bS53".decodeBase64String())
        append("ZWli".decodeBase64String())
        append("by5jbg==".decodeBase64String())
    }

public val vvoHostShort: String =
    buildString {
        append(vvo)
        append("LmNu".decodeBase64String())
    }

public val vvoHostLong: String =
    buildString {
        append("d2Vp".decodeBase64String())
        append("Ym8uY29t".decodeBase64String())
    }
