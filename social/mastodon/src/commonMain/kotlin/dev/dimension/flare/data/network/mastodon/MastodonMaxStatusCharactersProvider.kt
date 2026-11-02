package dev.dimension.flare.data.network.mastodon

import dev.dimension.flare.data.network.mastodon.api.InstanceResources
import dev.dimension.flare.data.network.mastodon.api.model.Statuses
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

internal const val DEFAULT_MASTODON_MAX_STATUS_CHARACTERS: Int = 500

internal data class MastodonTextLimits(
    val maxCharacters: Int = 500,
    val urlCharacters: Int = 23,
)

internal interface MastodonMaxStatusCharactersProvider {
    suspend fun snapshotLimits(host: String): MastodonTextLimits? = snapshot(host)?.let { MastodonTextLimits(it) }

    suspend fun resolveLimits(
        host: String,
        resources: InstanceResources,
    ): MastodonTextLimits = MastodonTextLimits(resolve(host, resources))

    suspend fun snapshot(host: String): Int?

    suspend fun resolve(
        host: String,
        resources: InstanceResources,
    ): Int
}

internal object DefaultMastodonMaxStatusCharactersProvider :
    MastodonMaxStatusCharactersProvider by CachedMastodonMaxStatusCharactersProvider()

internal fun mastodonTextLimitsFlow(
    host: String,
    resources: InstanceResources,
    provider: MastodonMaxStatusCharactersProvider = DefaultMastodonMaxStatusCharactersProvider,
): Flow<MastodonTextLimits> =
    flow {
        emit(provider.snapshotLimits(host) ?: MastodonTextLimits())
        emit(provider.resolveLimits(host, resources))
    }.distinctUntilChanged()

internal fun mastodonMaxStatusCharactersFlow(
    host: String,
    resources: InstanceResources,
    provider: MastodonMaxStatusCharactersProvider = DefaultMastodonMaxStatusCharactersProvider,
): Flow<Int> = mastodonTextLimitsFlow(host, resources, provider).map { it.maxCharacters }.distinctUntilChanged()

internal class CachedMastodonMaxStatusCharactersProvider(
    private val defaultValue: Int = DEFAULT_MASTODON_MAX_STATUS_CHARACTERS,
    private val requestTimeoutMillis: Long = 5_000,
) : MastodonMaxStatusCharactersProvider {
    private val stateMutex = Mutex()
    private val cache = mutableMapOf<String, MastodonTextLimits>()
    private val inFlight = mutableMapOf<String, CompletableDeferred<MastodonTextLimits>>()

    init {
        require(defaultValue > 0) { "Default max status characters must be positive" }
        require(requestTimeoutMillis > 0) { "Instance configuration timeout must be positive" }
    }

    override suspend fun snapshot(host: String): Int? = snapshotLimits(host)?.maxCharacters

    override suspend fun snapshotLimits(host: String): MastodonTextLimits? =
        stateMutex.withLock {
            cache[normalizeMastodonHost(host)]
        }

    override suspend fun resolve(
        host: String,
        resources: InstanceResources,
    ): Int = resolveLimits(host, resources).maxCharacters

    override suspend fun resolveLimits(
        host: String,
        resources: InstanceResources,
    ): MastodonTextLimits {
        val normalizedHost = normalizeMastodonHost(host)
        var ownsRequest = false
        val result =
            stateMutex.withLock {
                inFlight[normalizedHost]
                    ?: CompletableDeferred<MastodonTextLimits>().also {
                        inFlight[normalizedHost] = it
                        ownsRequest = true
                    }
            }

        if (!ownsRequest) {
            return result.await()
        }

        try {
            val stale = stateMutex.withLock { cache[normalizedHost] }
            val fresh =
                withTimeoutOrNull(requestTimeoutMillis) {
                    resources.fetchTextLimits()
                }
            val resolved = fresh ?: stale ?: MastodonTextLimits(defaultValue)
            if (fresh != null) {
                stateMutex.withLock {
                    cache[normalizedHost] = fresh
                }
            }
            result.complete(resolved)
            return resolved
        } catch (error: CancellationException) {
            result.completeExceptionally(error)
            throw error
        } catch (error: Throwable) {
            result.completeExceptionally(error)
            throw error
        } finally {
            stateMutex.withLock {
                if (inFlight[normalizedHost] === result) {
                    inFlight.remove(normalizedHost)
                }
            }
        }
    }
}

internal fun normalizeMastodonHost(host: String): String {
    val value = host.trim()
    require(value.isNotEmpty()) { "Mastodon host must not be blank" }
    val url = Url(if (value.contains("://")) value else "https://$value")
    return url.host
        .lowercase()
        .removeSuffix(".")
        .also {
            require(it.isNotEmpty()) { "Mastodon host must not be blank" }
        }
}

private suspend fun InstanceResources.fetchTextLimits(): MastodonTextLimits? =
    fetchLimits { instance().configuration?.statuses }
        ?: fetchLimits {
            val legacy = instanceV1()
            legacy.configuration?.statuses ?: legacy.maxTootChars?.let { Statuses(maxCharacters = it) }
        }

private suspend fun fetchLimits(fetch: suspend () -> Statuses?): MastodonTextLimits? =
    try {
        fetch()?.let { statuses ->
            statuses.maxCharacters.toValidMaxStatusCharacters()?.let { max ->
                MastodonTextLimits(
                    max,
                    statuses.charactersReservedPerURL?.takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt() ?: 23,
                )
            }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

private fun Long?.toValidMaxStatusCharacters(): Int? = this?.takeIf { it in 1..Int.MAX_VALUE.toLong() }?.toInt()
