package dev.dimension.flare.data.platform

import androidx.compose.runtime.Immutable
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Immutable
@Serializable
@SerialName("MisskeyCredential")
internal data class MisskeyCredential(
    val host: String,
    val accessToken: String,
    val nodeType: String? = null,
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
