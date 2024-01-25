package dev.dimension.flare.model

import io.ktor.util.decodeBase64String
import kotlinx.serialization.Serializable

@Serializable
enum class PlatformType {
    Mastodon,
    Misskey,
    Bluesky,

    @Suppress("EnumEntryName") // nothing wrong with this name :)
    xQt,
}

val PlatformType.logoUrl: String
    get() =
        when (this) {
            PlatformType.Mastodon -> "https://joinmastodon.org/logos/logo-purple.svg"
            PlatformType.Misskey -> "https://raw.githubusercontent.com/misskey-dev/assets/main/favicon.png"
            PlatformType.Bluesky -> "https://blueskyweb.xyz/images/apple-touch-icon.png"
            PlatformType.xQt -> "https://archive.md/FRp5v/1d3b14a6e115cfd9bb74720cd4569fb846a31fce"
        }

val xqtHost: String =
    buildString {
        append("dHc=".decodeBase64String())
        append("aXR0".decodeBase64String())
        append("ZXI=".decodeBase64String())
        append("LmNvbQ==".decodeBase64String())
    }
