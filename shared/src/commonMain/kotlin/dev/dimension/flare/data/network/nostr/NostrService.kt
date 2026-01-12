package dev.dimension.flare.data.network.nostr

import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.RelayAuthenticator
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import dev.dimension.flare.data.network.ktorClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob

internal class NostrService(
    private val keyPair: KeyPair,
    private val appScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) : AutoCloseable {
    private val client by lazy {
        val socketBuilder =
            BasicKtorWebSocket.Builder {
                ktorClient()
            }
        NostrClient(socketBuilder, appScope)
    }

    private val authCoordinator by lazy {
        val signer = NostrSignerInternal(keyPair)
        RelayAuthenticator(client, appScope) { authTemplate ->
            listOf(
                signer.sign(authTemplate),
            )
        }
    }

    override fun close() {
        authCoordinator.destroy()
    }
}
