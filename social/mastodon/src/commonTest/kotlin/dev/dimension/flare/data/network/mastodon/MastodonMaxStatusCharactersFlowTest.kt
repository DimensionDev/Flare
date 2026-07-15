package dev.dimension.flare.data.network.mastodon

import dev.dimension.flare.data.network.mastodon.api.InstanceResources
import dev.dimension.flare.data.network.mastodon.api.model.InstanceData
import dev.dimension.flare.data.network.mastodon.api.model.InstanceInfoV1
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class MastodonMaxStatusCharactersFlowTest {
    @Test
    fun emitsCachedThenResolvedLimit() =
        runTest {
            val provider = FakeMaxStatusCharactersProvider(cached = 750, resolved = 4_000)

            val limits =
                mastodonMaxStatusCharactersFlow(
                    host = "example.social",
                    resources = UnusedInstanceResources,
                    provider = provider,
                ).toList()

            assertEquals(listOf(750, 4_000), limits)
            assertEquals(1, provider.resolveCount)
        }

    @Test
    fun usesDefaultUntilFirstResolution() =
        runTest {
            val provider = FakeMaxStatusCharactersProvider(cached = null, resolved = 2_000)

            val limits =
                mastodonMaxStatusCharactersFlow(
                    host = "example.social",
                    resources = UnusedInstanceResources,
                    provider = provider,
                ).toList()

            assertEquals(listOf(500, 2_000), limits)
        }

    @Test
    fun doesNotEmitDuplicateResolvedLimit() =
        runTest {
            val provider = FakeMaxStatusCharactersProvider(cached = 1_000, resolved = 1_000)

            val limits =
                mastodonMaxStatusCharactersFlow(
                    host = "example.social",
                    resources = UnusedInstanceResources,
                    provider = provider,
                ).toList()

            assertEquals(listOf(1_000), limits)
        }
}

private class FakeMaxStatusCharactersProvider(
    private val cached: Int?,
    private val resolved: Int,
) : MastodonMaxStatusCharactersProvider {
    var resolveCount: Int = 0
        private set

    override suspend fun snapshot(host: String): Int? = cached

    override suspend fun resolve(
        host: String,
        resources: InstanceResources,
    ): Int {
        resolveCount += 1
        return resolved
    }
}

private object UnusedInstanceResources : InstanceResources {
    override suspend fun instance(): InstanceData = error("Not used")

    override suspend fun instanceV1(): InstanceInfoV1 = error("Not used")
}
