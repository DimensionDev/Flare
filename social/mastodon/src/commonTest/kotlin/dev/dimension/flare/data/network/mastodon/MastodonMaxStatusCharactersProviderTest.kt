package dev.dimension.flare.data.network.mastodon

import dev.dimension.flare.data.network.mastodon.api.InstanceResources
import dev.dimension.flare.data.network.mastodon.api.model.Configuration
import dev.dimension.flare.data.network.mastodon.api.model.InstanceData
import dev.dimension.flare.data.network.mastodon.api.model.InstanceInfoV1
import dev.dimension.flare.data.network.mastodon.api.model.Statuses
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MastodonMaxStatusCharactersProviderTest {
    @Test
    fun resolvesFromV2AndCachesByNormalizedHost() =
        runTest {
            val provider = CachedMastodonMaxStatusCharactersProvider()
            val resources = FakeInstanceResources(v2 = { v2Instance(1_234) })

            val result = provider.resolve(" HTTPS://EXAMPLE.COM/about ", resources)

            assertEquals(1_234, result)
            assertEquals(1_234, provider.snapshot("example.com."))
            assertEquals(1, resources.v2Calls)
            assertEquals(0, resources.v1Calls)
        }

    @Test
    fun fallsBackToV1WhenV2Fails() =
        runTest {
            val provider = CachedMastodonMaxStatusCharactersProvider()
            val resources =
                FakeInstanceResources(
                    v2 = { error("v2 unavailable") },
                    v1 = { v1Instance(2_048) },
                )

            val result = provider.resolve("example.com", resources)

            assertEquals(2_048, result)
            assertEquals(1, resources.v2Calls)
            assertEquals(1, resources.v1Calls)
        }

    @Test
    fun fallsBackToV1WhenV2DoesNotAdvertiseALimit() =
        runTest {
            val provider = CachedMastodonMaxStatusCharactersProvider()
            val resources =
                FakeInstanceResources(
                    v2 = { InstanceData() },
                    v1 = { v1Instance(1_500) },
                )

            val result = provider.resolve("example.com", resources)

            assertEquals(1_500, result)
            assertEquals(1, resources.v2Calls)
            assertEquals(1, resources.v1Calls)
        }

    @Test
    fun rejectsOutOfRangeValuesBeforeFallingBack() =
        runTest {
            val provider = CachedMastodonMaxStatusCharactersProvider(defaultValue = 700)
            val resources =
                FakeInstanceResources(
                    v2 = { v2Instance(Int.MAX_VALUE.toLong() + 1) },
                    v1 = { v1Instance(0) },
                )

            val result = provider.resolve("example.com", resources)

            assertEquals(700, result)
            assertNull(provider.snapshot("example.com"))
            assertEquals(1, resources.v2Calls)
            assertEquals(1, resources.v1Calls)
        }

    @Test
    fun returnsStaleValueWhenRefreshFails() =
        runTest {
            val provider = CachedMastodonMaxStatusCharactersProvider()
            provider.resolve(
                host = "example.com",
                resources = FakeInstanceResources(v2 = { v2Instance(900) }),
            )
            val failingResources = FakeInstanceResources()

            val result = provider.resolve("https://EXAMPLE.com/", failingResources)

            assertEquals(900, result)
            assertEquals(900, provider.snapshot("example.com"))
            assertEquals(1, failingResources.v2Calls)
            assertEquals(1, failingResources.v1Calls)
        }

    @Test
    fun returnsDefaultWithoutCachingWhenBothVersionsFail() =
        runTest {
            val provider = CachedMastodonMaxStatusCharactersProvider()

            val result = provider.resolve("example.com", FakeInstanceResources())

            assertEquals(DEFAULT_MASTODON_MAX_STATUS_CHARACTERS, result)
            assertNull(provider.snapshot("example.com"))
        }

    @Test
    fun concurrentRequestsForSameNormalizedHostShareOneFetch() =
        runTest {
            val provider = CachedMastodonMaxStatusCharactersProvider()
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            val resources =
                FakeInstanceResources(
                    v2 = {
                        started.complete(Unit)
                        release.await()
                        v2Instance(4_000)
                    },
                )

            val first = async { provider.resolve("EXAMPLE.com", resources) }
            started.await()
            val second = async { provider.resolve("https://example.com/path", resources) }
            yield()

            assertEquals(1, resources.v2Calls)
            release.complete(Unit)
            assertEquals(4_000, first.await())
            assertEquals(4_000, second.await())
            assertEquals(1, resources.v2Calls)
            assertEquals(0, resources.v1Calls)
        }

    @Test
    fun requestTimeoutFallsBackWithoutWaitingForever() =
        runTest {
            val provider =
                CachedMastodonMaxStatusCharactersProvider(
                    requestTimeoutMillis = 1_000,
                )
            val resources = FakeInstanceResources(v2 = { awaitCancellation() })

            val result = provider.resolve("example.com", resources)

            assertEquals(DEFAULT_MASTODON_MAX_STATUS_CHARACTERS, result)
            assertEquals(1, resources.v2Calls)
            assertEquals(0, resources.v1Calls)
        }
}

private class FakeInstanceResources(
    private val v2: suspend () -> InstanceData = { error("v2 unavailable") },
    private val v1: suspend () -> InstanceInfoV1 = { error("v1 unavailable") },
) : InstanceResources {
    var v2Calls: Int = 0
        private set
    var v1Calls: Int = 0
        private set

    override suspend fun instance(): InstanceData {
        v2Calls++
        return v2()
    }

    override suspend fun instanceV1(): InstanceInfoV1 {
        v1Calls++
        return v1()
    }
}

private fun v2Instance(maxCharacters: Long): InstanceData =
    InstanceData(
        configuration =
            Configuration(
                statuses = Statuses(maxCharacters = maxCharacters),
            ),
    )

private fun v1Instance(maxCharacters: Long): InstanceInfoV1 =
    InstanceInfoV1(
        configuration =
            Configuration(
                statuses = Statuses(maxCharacters = maxCharacters),
            ),
    )
