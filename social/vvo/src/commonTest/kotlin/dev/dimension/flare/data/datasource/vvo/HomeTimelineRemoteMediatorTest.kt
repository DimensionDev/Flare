package dev.dimension.flare.data.datasource.vvo

import androidx.paging.ExperimentalPagingApi
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.data.platform.VVoCredential
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.mapper.VvoRenderTestModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.plugin.module.dsl.modules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalPagingApi::class)
class HomeTimelineRemoteMediatorTest {
    @Test
    fun friendsTimelineCanJoinItsCachedBoundaryAndKeepsTheWholeRefreshPage() =
        runTest {
            startKoin { modules(VvoRenderTestModule::class) }
            try {
                val cursors = mutableListOf<String?>()
                val service =
                    VVOService(
                        credentialFlow = flowOf(VVoCredential("MLOGIN=1; SUB=test")),
                        httpClientFactory = { config ->
                            HttpClient(
                                MockEngine { request ->
                                    val body =
                                        when (request.url.encodedPath) {
                                            "/api/config" -> {
                                                """{"ok":1,"data":{"login":true}}"""
                                            }

                                            "/feed/friends" -> {
                                                val cursor = request.url.parameters["max_id"]
                                                cursors += cursor
                                                if (cursor == null) {
                                                    """{"ok":1,"data":{"statuses":[{"id":"30"},{"id":"20"},{"id":"10"}],"next_cursor_str":"older"}}"""
                                                } else {
                                                    """{"ok":1,"data":{"statuses":[{"id":"5"}]}}"""
                                                }
                                            }

                                            else -> {
                                                error("Unexpected endpoint")
                                            }
                                        }
                                    respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                                },
                                config,
                            )
                        },
                    )
                val loader = HomeTimelineRemoteMediator(service, MicroBlogKey("reader", "m.weibo.cn"))
                assertTrue(loader.canJoinTimeline)
                val refresh = loader.load(20, PagingRequest.Refresh)
                assertEquals(listOf("30", "20", "10"), refresh.data.map { it.statusKey.id })
                assertEquals("older", refresh.nextKey)
                val append = loader.load(20, PagingRequest.Append("older"))
                assertEquals(listOf("5"), append.data.map { it.statusKey.id })
                assertNull(append.nextKey)
                assertEquals(listOf(null, "older"), cursors)
            } finally {
                stopKoin()
            }
        }
}
