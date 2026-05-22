package dev.dimension.flare.data.platform

import androidx.compose.runtime.Immutable
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import sh.christian.ozone.oauth.OAuthToken

@Immutable
@Serializable
@SerialName("MastodonCredential")
internal data class MastodonCredential(
    val instance: String,
    val accessToken: String,
    val forkType: ForkType = ForkType.Mastodon,
    // to support more forks in the future
    val nodeType: String? = null,
) {
    enum class ForkType {
        Mastodon,
        Pleroma,
    }
}

@Immutable
@Serializable
@SerialName("MisskeyCredential")
internal data class MisskeyCredential(
    val host: String,
    val accessToken: String,
    val nodeType: String? = null,
)

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

@Immutable
@Serializable
@SerialName("XQTCredential")
internal data class XQTCredential(
    val chocolate: String,
)

@Immutable
@Serializable
@SerialName("VVoCredential")
internal data class VVoCredential(
    val chocolate: String,
)

@Immutable
@Serializable
internal sealed interface NostrSignerCredential {
    val stableId: String

    @Immutable
    @Serializable
    @SerialName("NostrSignerLocalKey")
    data class LocalKey(
        val nsec: String,
    ) : NostrSignerCredential {
        override val stableId: String
            get() = "local:$nsec"
    }

    @Immutable
    @Serializable
    @SerialName("NostrSignerBunker")
    data class Bunker(
        val uri: String,
        val userPubkeyHex: String? = null,
        val signerRelay: String? = null,
        val secret: String? = null,
    ) : NostrSignerCredential {
        override val stableId: String
            get() = "bunker:$uri"
    }

    @Immutable
    @Serializable
    @SerialName("NostrSignerAmber")
    data class Amber(
        val userPubkeyHex: String,
        val packageName: String? = null,
        val approvedSignerPubkey: String? = null,
    ) : NostrSignerCredential {
        override val stableId: String
            get() = "amber:$userPubkeyHex:${packageName.orEmpty()}:${approvedSignerPubkey.orEmpty()}"
    }
}

@Immutable
@Serializable
@SerialName("NostrCredential")
internal data class NostrCredential(
    val pubkeyHex: String = "",
    val relays: List<String> = emptyList(),
    val mediaServerUrl: String = "https://blossom.nostr.build/",
    val signer: NostrSignerCredential? = null,
    @SerialName("nsec")
    internal val legacyNsec: String? = null,
)

internal val NostrCredential.effectiveSigner: NostrSignerCredential?
    get() = signer ?: legacyNsec?.let(NostrSignerCredential::LocalKey)

internal fun NostrCredential.effectivePubkeyHex(accountKey: MicroBlogKey): String = pubkeyHex.ifBlank { accountKey.id }

internal fun NostrCredential.normalized(accountKey: MicroBlogKey): NostrCredential =
    copy(
        pubkeyHex = effectivePubkeyHex(accountKey),
        signer = effectiveSigner,
    )

internal fun NostrCredential.signerStableId(accountKey: MicroBlogKey): String =
    effectiveSigner?.stableId ?: "readonly:${effectivePubkeyHex(accountKey)}"
