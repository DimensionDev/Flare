package dev.dimension.flare.feature.plugin.adapter

import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.feature.plugin.wire.PageDirectionV1
import dev.dimension.flare.feature.plugin.wire.PageV1
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PluginInvocationV1Test {
    @Test
    fun unsupportedPagingDirectionEndsWithoutCallingPlugin() =
        runTest {
            var calls = 0
            val loader =
                pluginRemoteLoader(
                    directions = setOf(PageDirectionV1.Refresh),
                    load = { _, _ ->
                        calls += 1
                        PageV1(items = listOf("unexpected"))
                    },
                    map = { it },
                )

            val result = loader.load(20, PagingRequest.Append("older"))

            assertEquals(0, calls)
            assertEquals(emptyList(), result.data)
            assertEquals(null, result.nextKey)
        }
}
