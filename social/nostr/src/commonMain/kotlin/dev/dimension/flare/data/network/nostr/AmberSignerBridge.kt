package dev.dimension.flare.data.network.nostr

import dev.dimension.flare.ui.model.NostrSignerCredential

public data class AmberConnection(
    val credential: NostrSignerCredential.Amber,
    val pubkeyHex: String,
)

public interface AmberSignerBridge {
    public fun isAvailable(): Boolean

    public suspend fun connect(): AmberConnection

    public suspend fun getPublicKey(credential: NostrSignerCredential.Amber): String

    public suspend fun signEvent(
        credential: NostrSignerCredential.Amber,
        unsignedEventJson: String,
    ): String
}

public class UnsupportedAmberSignerBridge(
    private val message: String,
) : AmberSignerBridge {
    override fun isAvailable(): Boolean = false

    override suspend fun connect(): AmberConnection = unsupported()

    override suspend fun getPublicKey(credential: NostrSignerCredential.Amber): String = unsupported()

    override suspend fun signEvent(
        credential: NostrSignerCredential.Amber,
        unsignedEventJson: String,
    ): String = unsupported()

    private fun unsupported(): Nothing = throw UnsupportedOperationException(message)
}
