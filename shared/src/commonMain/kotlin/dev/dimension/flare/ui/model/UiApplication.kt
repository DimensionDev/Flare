package dev.dimension.flare.ui.model

import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.data.database.app.DbApplication
import dev.dimension.flare.data.network.mastodon.api.model.CreateApplicationResponse
import dev.dimension.flare.model.PlatformType


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

    companion object {
        fun DbApplication.toUi(): UiApplication {
            return when (platform_type) {
                PlatformType.Mastodon -> Mastodon(
                    host = host,
                    application = credential_json.decodeJson(),
                )

                PlatformType.Misskey -> Misskey(
                    host = host,
                    session = credential_json,
                )

                PlatformType.Bluesky -> Bluesky(
                    host = host,
                )
            }
        }
    }
}
