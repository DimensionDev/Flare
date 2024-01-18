package dev.dimension.flare.ui.model

import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.data.database.app.DbApplication
import dev.dimension.flare.data.network.mastodon.api.model.CreateApplicationResponse
import dev.dimension.flare.model.PlatformType
import io.ktor.util.decodeBase64String

sealed interface UiApplication {
    val host: String

    data class Mastodon(
        override val host: String,
        val application: CreateApplicationResponse,
    ) : UiApplication

    data class Misskey(
        override val host: String,
        val session: String,
    ) : UiApplication

    data class Bluesky(
        override val host: String,
    ) : UiApplication

    data object XQT : UiApplication {
        override val host: String =
            buildString {
                append("dHc=".decodeBase64String())
                append("aXR0".decodeBase64String())
                append("ZXI=".decodeBase64String())
                append("LmNvbQ==".decodeBase64String())
            }
    }

    companion object {
        fun DbApplication.toUi(): UiApplication {
            return when (platform_type) {
                PlatformType.Mastodon ->
                    Mastodon(
                        host = host,
                        application = credential_json.decodeJson(),
                    )

                PlatformType.Misskey ->
                    Misskey(
                        host = host,
                        session = credential_json,
                    )

                PlatformType.Bluesky ->
                    Bluesky(
                        host = host,
                    )

                PlatformType.xQt -> XQT
            }
        }
    }
}
