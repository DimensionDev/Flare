package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.data.database.app.model.DbApplication
import dev.dimension.flare.data.network.mastodon.api.model.CreateApplicationResponse
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.vvoHost
import dev.dimension.flare.model.xqtHost

@Immutable
sealed interface UiApplication {
    val host: String

    @Immutable
    data class Mastodon internal constructor(
        override val host: String,
        internal val application: CreateApplicationResponse,
    ) : UiApplication

    @Immutable
    data class Misskey(
        override val host: String,
        val session: String,
    ) : UiApplication

    @Immutable
    data class Bluesky(
        override val host: String,
    ) : UiApplication

    @Immutable
    data object XQT : UiApplication {
        override val host: String = xqtHost
    }

    @Immutable
    data object VVo : UiApplication {
        override val host: String = vvoHost
    }

    companion object {
        fun DbApplication.toUi(): UiApplication =
            when (platformType) {
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

                PlatformType.VVo -> VVo
            }
    }
}
