package dev.dimension.flare.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64

@Immutable
@Serializable
public enum class PlatformType {
    Mastodon,
    Misskey,
    Bluesky,

    @Suppress("EnumEntryName") // nothing wrong with this name :)
    xQt,

    VVo,
    Nostr,
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
                "https://upload.wikimedia.org/wikipedia/en/thumb/6/" +
                    "6e/Sina_Weibo.svg/2560px-Sina_Weibo.svg.png"
            PlatformType.Nostr ->
                "https://github.com/mbarulli/nostr-logo/blob/" +
                    "main/PNG/nostr-icon-purple-transparent-256x256.png?raw=true"
        }

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
