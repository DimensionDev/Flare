package dev.dimension.flare.model

import androidx.compose.runtime.Immutable
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
    val logoUrl: String,
)

public val PlatformType.metadata: PlatformTypeMetadata
    get() =
        when (this) {
            PlatformType.Nostr ->
                PlatformTypeMetadata(
                    displayName = "Nostr",
                    logoUrl = "https://nostr.com/favicon.ico",
                )
            PlatformType.Mastodon ->
                PlatformTypeMetadata(
                    displayName = "Mastodon",
                    logoUrl = "https://joinmastodon.org/logos/logo-purple.svg",
                )
            PlatformType.Misskey ->
                PlatformTypeMetadata(
                    displayName = "Misskey",
                    logoUrl =
                        "https://github.com/misskey-dev/misskey/blob/develop/packages" +
                            "/backend/assets/favicon.png?raw=true",
                )
            PlatformType.Bluesky ->
                PlatformTypeMetadata(
                    displayName = "Bluesky",
                    logoUrl = "https://blueskyweb.xyz/images/apple-touch-icon.png",
                )
            PlatformType.xQt ->
                PlatformTypeMetadata(
                    displayName = "X",
                    logoUrl =
                        "https://upload.wikimedia.org/wikipedia/commons/thumb/5/53" +
                            "/X_logo_2023_original.svg/1920px-X_logo_2023_original.svg.png",
                )
            PlatformType.VVo ->
                PlatformTypeMetadata(
                    displayName = vvo,
                    logoUrl =
                        "https://upload.wikimedia.org/wikipedia/en/thumb/6/" +
                            "6e/Sina_Weibo.svg/2560px-Sina_Weibo.svg.png",
                )
        }

public val PlatformType.displayName: String
    get() = metadata.displayName

public val PlatformType.logoUrl: String
    get() = metadata.logoUrl

public fun PlatformType.agreementUrl(host: String): String? =
    when (this) {
        PlatformType.Nostr,
        PlatformType.VVo,
        -> null
        PlatformType.Bluesky -> "https://bsky.social/about/support/tos"
        PlatformType.xQt -> "https://help.x.com/en/rules-and-policies/x-rules"
        PlatformType.Mastodon,
        PlatformType.Misskey,
        -> "https://$host/about"
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
