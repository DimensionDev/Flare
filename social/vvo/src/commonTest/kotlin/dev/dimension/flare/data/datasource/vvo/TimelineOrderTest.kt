package dev.dimension.flare.data.datasource.vvo

import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.TimelineOrder
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.data.platform.VVoCredential
import dev.dimension.flare.model.MicroBlogKey
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class TimelineOrderTest {
    @Test
    fun homePassesOpaqueMaxIdThroughWithoutAssumingStatusContinuity() =
        runTest {
            val cursors = mutableListOf<String?>()
            val service =
                VVOService(
                    credentialFlow = flowOf(VVoCredential(chocolate = "MLOGIN=1; SUB=test")),
                    httpClientFactory = { config ->
                        HttpClient(
                            MockEngine { request ->
                                val body =
                                    if (request.url.encodedPath == "/api/config") {
                                        """{"ok":1,"data":{"login":true}}"""
                                    } else {
                                        assertEquals("/feed/friends", request.url.encodedPath)
                                        cursors += request.url.parameters["max_id"]
                                        """{"ok":1,"data":{"statuses":[],"next_cursor_str":"opaque:page/2"}}"""
                                    }
                                respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                            },
                        ) { config.invoke(this) }
                    },
                )
            val loader = HomeTimelineRemoteMediator(service, MicroBlogKey("reader", "m.weibo.cn"))
            assertEquals(TimelineOrder.Ranked, loader.timelineOrder)
            val first = loader.load(40, PagingRequest.Refresh)
            loader.load(40, PagingRequest.Append(requireNotNull(first.nextKey)))
            assertEquals(listOf(null, "opaque:page/2"), cursors)
        }
}
