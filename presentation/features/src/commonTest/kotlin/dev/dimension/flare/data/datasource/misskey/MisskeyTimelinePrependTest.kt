package dev.dimension.flare.data.datasource.misskey

import dev.dimension.flare.data.datasource.microblog.paging.CacheableRemoteLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MisskeyTimelinePrependTest {
    private val accountKey = MicroBlogKey("account", "example.com")
    private val service = MisskeyService(baseUrl = "https://example.com/api/")

    @Test
    fun `home timeline prepend is disabled`() =
        runTest {
            assertPrependDisabled(
                HomeTimelineRemoteMediator(
                    accountKey = accountKey,
                    service = service,
                ),
            )
        }

    @Test
    fun `hybrid timeline prepend is disabled`() =
        runTest {
            assertPrependDisabled(
                HybridTimelineRemoteMediator(
                    accountKey = accountKey,
                    service = service,
                ),
            )
        }

    @Test
    fun `favourite timeline prepend is disabled`() =
        runTest {
            assertPrependDisabled(
                FavouriteTimelineRemoteMediator(
                    accountKey = accountKey,
                    service = service,
                ),
            )
        }

    @Test
    fun `channel timeline prepend is disabled`() =
        runTest {
            assertPrependDisabled(
                ChannelTimelineRemoteMediator(
                    service = service,
                    accountKey = accountKey,
                    id = "channel-id",
                ),
            )
        }

    @Test
    fun `antennas timeline prepend is disabled`() =
        runTest {
            assertPrependDisabled(
                AntennasTimelineRemoteMediator(
                    service = service,
                    accountKey = accountKey,
                    id = "antenna-id",
                ),
            )
        }

    @Test
    fun `list timeline prepend is disabled`() =
        runTest {
            assertPrependDisabled(
                ListTimelineRemoteMediator(
                    listId = "list-id",
                    service = service,
                    accountKey = accountKey,
                ),
            )
        }

    private suspend fun assertPrependDisabled(loader: CacheableRemoteLoader<UiTimelineV2>) {
        assertFalse(loader.supportPrepend)
        val result = loader.load(pageSize = 20, request = PagingRequest.Prepend("ignored"))
        assertTrue(result.data.isEmpty())
        assertNull(result.previousKey)
        assertNull(result.nextKey)
    }
}
