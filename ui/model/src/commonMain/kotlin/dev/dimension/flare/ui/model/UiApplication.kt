package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import dev.dimension.flare.model.vvoHost
import dev.dimension.flare.model.xqtHost
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Immutable
public sealed interface UiApplication {
    public val host: String

    @Immutable
    public data class Nostr(
        override val host: String,
    ) : UiApplication

    @Immutable
    public data class Mastodon(
        override val host: String,
        val application: MastodonApplicationCredential,
    ) : UiApplication

    @Immutable
    public data class Misskey(
        override val host: String,
        val session: String,
    ) : UiApplication

    @Immutable
    public data class Bluesky(
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
}

@Immutable
@Serializable
public data class MastodonApplicationCredential(
    val id: String? = null,
    val name: String? = null,
    val website: String? = null,
    @SerialName("redirect_uri")
    val redirectURI: String,
    @SerialName("client_id")
    val clientID: String,
    @SerialName("client_secret")
    val clientSecret: String,
    @SerialName("vapid_key")
    val vapidKey: String? = null,
)
