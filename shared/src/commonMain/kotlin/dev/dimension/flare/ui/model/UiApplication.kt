package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.data.database.app.model.DbApplication
import dev.dimension.flare.data.network.mastodon.api.model.CreateApplicationResponse
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.vvoHost
import dev.dimension.flare.model.xqtHost

@Immutable
public sealed interface UiApplication {
    public val host: String

    @Immutable
    public data class Mastodon internal constructor(
        override val host: String,
        internal val application: CreateApplicationResponse,
    ) : UiApplication

    @Immutable
    public data class Misskey internal constructor(
        override val host: String,
        val session: String,
    ) : UiApplication

    @Immutable
    public data class Bluesky internal constructor(
        override val host: String,
    ) : UiApplication

    @Immutable
    public data object XQT : UiApplication {
        override val host: String = xqtHost
    }

    @Immutable
    public data object VVo : UiApplication {
        override val host: String = vvoHost
        val loginUrl: String = "https://$host/login?backURL=https://$host/"
    }

    public companion object {
        internal fun DbApplication.toUi(): UiApplication =
            when (platform_type) {
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
