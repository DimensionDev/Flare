package dev.dimension.flare.data.network.tumblr

import dev.dimension.flare.data.network.tumblr.model.TumblrApplicationCredential
import dev.dimension.flare.ui.route.DeeplinkRoute

internal object TumblrOAuth2Config {
    private const val CLIENT_ID: String = ""
    private const val CLIENT_SECRET: String = ""
    internal const val HOST: String = "tumblr.com"
    internal val REDIRECT_URI: String = DeeplinkRoute.Companion.Callback.TUMBLR
    private const val DEFAULT_SCOPE: String = "basic write offline_access"

    fun applicationCredential(state: String? = null): TumblrApplicationCredential {
        require(CLIENT_ID.isNotBlank() && CLIENT_SECRET.isNotBlank()) {
            "Tumblr OAuth2 is not configured. Please set CLIENT_ID and CLIENT_SECRET in TumblrOAuth2Config."
        }
        return TumblrApplicationCredential(
            consumerKey = CLIENT_ID,
            consumerSecret = CLIENT_SECRET,
            authState = state,
        )
    }

    fun buildAuthorizeUrl(
        state: String,
        scope: String = DEFAULT_SCOPE,
    ): String =
        "https://www.tumblr.com/oauth2/authorize?" +
            "response_type=code" +
            "&client_id=${encode(CLIENT_ID)}" +
            "&redirect_uri=${encode(REDIRECT_URI)}" +
            "&scope=${encode(scope)}" +
            "&state=${encode(state)}"

    private fun encode(value: String): String =
        buildString {
            value.encodeToByteArray().forEach { byte ->
                val c = byte.toInt().toChar()
                if (
                    c in 'A'..'Z' ||
                    c in 'a'..'z' ||
                    c in '0'..'9' ||
                    c == '-' ||
                    c == '.' ||
                    c == '_' ||
                    c == '~'
                ) {
                    append(c)
                } else {
                    append('%')
                    append(
                        byte
                            .toUByte()
                            .toString(16)
                            .uppercase()
                            .padStart(2, '0'),
                    )
                }
            }
        }
}
