package dev.dimension.flare.data.network.nostr

import com.vitorpamplona.quartz.nip01Core.core.Event
import com.vitorpamplona.quartz.nip01Core.crypto.KeyPair
import com.vitorpamplona.quartz.nip01Core.relay.client.NostrClient
import com.vitorpamplona.quartz.nip01Core.relay.client.auth.RelayAuthenticator
import com.vitorpamplona.quartz.nip01Core.relay.client.reqs.IRequestListener
import com.vitorpamplona.quartz.nip01Core.relay.client.single.newSubId
import com.vitorpamplona.quartz.nip01Core.relay.filters.Filter
import com.vitorpamplona.quartz.nip01Core.relay.normalizer.NormalizedRelayUrl
import com.vitorpamplona.quartz.nip01Core.signers.NostrSignerInternal
import com.vitorpamplona.quartz.nip19Bech32.bech32.bechToBytes
import dev.dimension.flare.data.network.ktorClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull

internal class NostrService(
    private val privateKeyFlow: Flow<String>,
    private val appScope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) : AutoCloseable {
    internal val nostrClient by lazy {
        val socketBuilder =
            BasicKtorWebSocket.Builder {
                ktorClient()
            }
        NostrClient(socketBuilder, appScope)
    }

    internal val signerFlow by lazy {
        privateKeyFlow
            .map {
                KeyPair(it.bechToBytes())
            }.map {
                NostrSignerInternal(it)
            }
    }

    private val authCoordinator by lazy {
        RelayAuthenticator(nostrClient, appScope) { authTemplate ->
            listOfNotNull(
                signerFlow.firstOrNull()?.sign(authTemplate),
            )
        }
    }

    suspend fun homeTimeline(
        relay: String,
        limit: Int = 20,
        until: Long? = null,
    ): List<Event> {
        val events = mutableListOf<Event>()
        val completionSignal = CompletableDeferred<Unit>()
        val subId = newSubId()

        val filter =
            Filter(
                kinds = listOf(1), // TextNote kind
                limit = limit,
                until = until,
            )

        val normalizedRelay = NormalizedRelayUrl(relay)
        val filters = mapOf<NormalizedRelayUrl, List<Filter>>(normalizedRelay to listOf(filter))

        val listener =
            object : IRequestListener {
                override fun onEvent(
                    event: Event,
                    isLive: Boolean,
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    events.add(event)
                }

                override fun onEose(
                    relay: NormalizedRelayUrl,
                    forFilters: List<Filter>?,
                ) {
                    completionSignal.complete(Unit)
                }
            }

        nostrClient.connect()
        nostrClient.openReqSubscription(
            subId = subId,
            filters = filters,
            listener = listener,
        )

        // Wait for EOSE or timeout
        withTimeoutOrNull(10000L) {
            completionSignal.await()
        }

        nostrClient.close(subId)
        return events.sortedByDescending { it.createdAt }
    }

    override fun close() {
        authCoordinator.destroy()
    }
}
