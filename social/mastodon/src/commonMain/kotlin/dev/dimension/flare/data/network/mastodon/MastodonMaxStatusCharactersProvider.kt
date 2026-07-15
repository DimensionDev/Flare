package dev.dimension.flare.data.network.mastodon

import dev.dimension.flare.data.network.mastodon.api.InstanceResources
import io.ktor.http.Url
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

internal const val DEFAULT_MASTODON_MAX_STATUS_CHARACTERS: Int = 500

internal interface MastodonMaxStatusCharactersProvider {
    suspend fun snapshot(host: String): Int?

    suspend fun resolve(
        host: String,
        resources: InstanceResources,
    ): Int
}

internal object DefaultMastodonMaxStatusCharactersProvider :
    MastodonMaxStatusCharactersProvider by CachedMastodonMaxStatusCharactersProvider()

internal fun mastodonMaxStatusCharactersFlow(
    host: String,
    resources: InstanceResources,
    provider: MastodonMaxStatusCharactersProvider = DefaultMastodonMaxStatusCharactersProvider,
): Flow<Int> =
    flow {
        emit(provider.snapshot(host) ?: DEFAULT_MASTODON_MAX_STATUS_CHARACTERS)
        emit(provider.resolve(host, resources))
    }.distinctUntilChanged()

internal class CachedMastodonMaxStatusCharactersProvider(
    private val defaultValue: Int = DEFAULT_MASTODON_MAX_STATUS_CHARACTERS,
    private val requestTimeoutMillis: Long = 5_000,
) : MastodonMaxStatusCharactersProvider {
    private val stateMutex = Mutex()
    private val cache = mutableMapOf<String, Int>()
    private val inFlight = mutableMapOf<String, CompletableDeferred<Int>>()

    init {
        require(defaultValue > 0) { "Default max status characters must be positive" }
        require(requestTimeoutMillis > 0) { "Instance configuration timeout must be positive" }
    }

    override suspend fun snapshot(host: String): Int? =
        stateMutex.withLock {
            cache[normalizeMastodonHost(host)]
        }

    override suspend fun resolve(
        host: String,
        resources: InstanceResources,
    ): Int {
        val normalizedHost = normalizeMastodonHost(host)
        var ownsRequest = false
        val result =
            stateMutex.withLock {
                inFlight[normalizedHost]
                    ?: CompletableDeferred<Int>().also {
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
                    resources.fetchMaxStatusCharacters()
                }
            val resolved = fresh ?: stale ?: defaultValue
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

private suspend fun InstanceResources.fetchMaxStatusCharacters(): Int? =
    fetchCharacters {
        instance().configuration?.statuses?.maxCharacters
    } ?: fetchCharacters {
        instanceV1().configuration?.statuses?.maxCharacters
    }

private suspend fun fetchCharacters(fetch: suspend () -> Long?): Int? =
    try {
        fetch().toValidMaxStatusCharacters()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }

private fun Long?.toValidMaxStatusCharacters(): Int? =
    this
        ?.takeIf { it in 1..Int.MAX_VALUE.toLong() }
        ?.toInt()
