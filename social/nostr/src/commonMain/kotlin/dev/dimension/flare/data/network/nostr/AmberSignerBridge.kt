package dev.dimension.flare.data.network.nostr

import dev.dimension.flare.data.platform.NostrSignerCredential

internal data class AmberConnection(
    val credential: NostrSignerCredential.Amber,
    val pubkeyHex: String,
)

internal expect val isAmberLoginSupported: Boolean

internal interface AmberSignerBridge {
    fun isAvailable(): Boolean

    suspend fun connect(): AmberConnection

    suspend fun getPublicKey(credential: NostrSignerCredential.Amber): String

    suspend fun signEvent(
        credential: NostrSignerCredential.Amber,
        unsignedEventJson: String,
    ): String
}

internal class UnsupportedAmberSignerBridge(
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
