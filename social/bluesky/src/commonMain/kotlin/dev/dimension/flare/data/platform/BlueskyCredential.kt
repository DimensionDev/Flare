package dev.dimension.flare.data.platform

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.christian.ozone.oauth.OAuthToken

@Serializable
internal sealed interface BlueskyCredential {
    val baseUrl: String
    val accessToken: String
    val refreshToken: String

    @Immutable
    @Serializable
    @SerialName("BlueskyCredential")
    data class Password(
        override val baseUrl: String,
        override val accessToken: String,
        override val refreshToken: String,
    ) : BlueskyCredential

    @Immutable
    @Serializable
    @SerialName("BlueskyOAuthCredential")
    data class OAuthCredential(
        override val baseUrl: String,
        val oAuthToken: OAuthToken,
    ) : BlueskyCredential {
        override val accessToken: String
            get() = oAuthToken.accessToken

        override val refreshToken: String
            get() = oAuthToken.refreshToken

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is OAuthCredential) return false

            if (baseUrl != other.baseUrl) return false
            if (oAuthToken.accessToken != other.oAuthToken.accessToken) return false
            if (oAuthToken.refreshToken != other.oAuthToken.refreshToken) return false
            if (oAuthToken.nonce != other.oAuthToken.nonce) return false
            if (oAuthToken.expiresIn != other.oAuthToken.expiresIn) return false

            return true
        }

        override fun hashCode(): Int {
            var result = baseUrl.hashCode()
            result = 31 * result + oAuthToken.hashCode()
            return result
        }
    }
}
