package dev.dimension.flare.data.datasource.xqt

import de.jensklingenberg.ktorfit.converter.ResponseConverterFactory
import de.jensklingenberg.ktorfit.ktorfit
import dev.dimension.flare.data.datasource.microblog.PostEvent
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.network.xqt.XQT_JSON
import dev.dimension.flare.data.network.xqt.api.FollowRequestsApi
import dev.dimension.flare.data.network.xqt.api.createFollowRequestsApi
import dev.dimension.flare.data.network.xqt.api.respondToFollowRequest
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.XqtRenderTestModule
import dev.dimension.flare.ui.model.postEventOrNull
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.http.parseQueryString
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.plugin.module.dsl.modules
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FollowRequestsRemoteMediatorTest {
    private val accountKey = MicroBlogKey("account", "x.com")
    private val clients = mutableListOf<HttpClient>()

    @BeforeTest
    fun setup() {
        startKoin { modules(XqtRenderTestModule::class) }
    }

    @AfterTest
    fun tearDown() {
        clients.forEach { it.close() }
        stopKoin()
    }

    @Test
    fun pagesRequestsInServerOrderAndKeepsAccountScopedActions() =
        runTest {
            val cursors = mutableListOf<String?>()
            val api =
                api { request ->
                    when (request.url.encodedPath) {
                        "/1.1/friendships/incoming.json" -> {
                            cursors += request.url.parameters["cursor"]
                            assertEquals("true", request.url.parameters["stringify_ids"])
                            assertEquals("20", request.url.parameters["count"])
                            """{"ids":["9007199254740993","2"],"next_cursor_str":"next"}"""
                        }

                        "/1.1/users/lookup.json" -> {
                            assertEquals("9007199254740993,2", request.url.parameters["user_id"])
                            """[{"id_str":"2","name":"Second","screen_name":"second"},{"id_str":"9007199254740993","name":"First","screen_name":"first"}]"""
                        }

                        else -> {
                            error("Unexpected request: ${request.url}")
                        }
                    }
                }
            val loader = FollowRequestsRemoteMediator(api, accountKey)
            val first = loader.load(20, PagingRequest.Refresh)
            val users = first.data.map { assertIs<UiTimelineV2.User>(it) }
            assertEquals(listOf("9007199254740993", "2"), users.map { it.value.key.id })
            assertEquals("next", first.nextKey)
            assertTrue(users.all { it.accountType == AccountType.Specific(accountKey) && it.button.size == 2 })
            assertTrue(users.all { it.statusKey != it.value.key })
            assertEquals(listOf("First", "Second"), users.map { it.value.name.raw })
            for (user in users) {
                val accept = user.button[0].clickEvent.postEventOrNull()!!
                val reject = user.button[1].clickEvent.postEventOrNull()!!
                assertEquals(accountKey, accept.accountKey)
                assertEquals(accountKey, reject.accountKey)
                assertEquals(PostEvent.XQT.AcceptFollowRequest(user.statusKey, user.value.key), accept.postEvent)
                assertEquals(PostEvent.XQT.RejectFollowRequest(user.statusKey, user.value.key), reject.postEvent)
            }
            val second = loader.load(20, PagingRequest.Append("next"))
            assertNull(second.nextKey)
            assertEquals(listOf<String?>("-1", "next"), cursors)
            assertTrue(loader.load(20, PagingRequest.Prepend("previous")).data.isEmpty())
            assertEquals(2, cursors.size)
            assertNotEquals(loader.pagingKey, FollowRequestsRemoteMediator(api, MicroBlogKey("other", "x.com")).pagingKey)
        }

    @Test
    fun emptyAndUnavailableUsersDoNotHideLaterPages() =
        runTest {
            var page = """{"ids":["missing"],"next_cursor_str":"next"}"""
            var lookups = 0
            val loader =
                FollowRequestsRemoteMediator(
                    api { request ->
                        if (request.url.encodedPath.endsWith("incoming.json")) {
                            page
                        } else {
                            lookups++
                            "[]"
                        }
                    },
                    accountKey,
                )
            val first = loader.load(20, PagingRequest.Refresh)
            assertTrue(first.data.isEmpty())
            assertEquals("next", first.nextKey)
            page = """{"ids":[],"next_cursor_str":"0"}"""
            assertNull(loader.load(20, PagingRequest.Append("next")).nextKey)
            assertEquals(1, lookups)
        }

    @Test
    fun acceptAndRejectUseSeparatePostEndpoints() =
        runTest {
            val paths = mutableListOf<String>()
            val api =
                api { request ->
                    paths += request.url.encodedPath
                    assertEquals(HttpMethod.Post, request.method)
                    assertEquals(
                        "9007199254740993",
                        parseQueryString((request.body as OutgoingContent.ByteArrayContent).bytes().decodeToString())["user_id"],
                    )
                    "{}"
                }
            api.respondToFollowRequest("9007199254740993", accept = true)
            api.respondToFollowRequest("9007199254740993", accept = false)
            assertEquals(listOf("/1.1/friendships/accept.json", "/1.1/friendships/deny.json"), paths)
        }

    @Test
    fun httpAndBusinessErrorsCannotReportSuccessfulMutation() =
        runTest {
            for ((status, body) in listOf(
                HttpStatusCode.TooManyRequests to "{}",
                HttpStatusCode.OK to """{"errors":[{"code":326,"message":"Account locked"}]}""",
                HttpStatusCode.OK to """{"errors":[{"code":49,"message":"No follow request"}]}""",
            )) {
                val api = api(status) { body }
                for (accept in listOf(true, false)) {
                    assertFailsWith<IllegalStateException> { api.respondToFollowRequest("user", accept) }
                }
                assertFailsWith<IllegalStateException> {
                    FollowRequestsRemoteMediator(api, accountKey).load(20, PagingRequest.Refresh)
                }
            }
        }

    private fun api(
        status: HttpStatusCode = HttpStatusCode.OK,
        response: (io.ktor.client.request.HttpRequestData) -> String,
    ): FollowRequestsApi {
        val client =
            HttpClient(
                MockEngine { request ->
                    respond(response(request), status, headersOf(HttpHeaders.ContentType, "application/json"))
                },
            ) {
                install(ContentNegotiation) { json(XQT_JSON) }
            }
        clients += client
        return ktorfit {
            baseUrl("https://api.x.com/")
            httpClient(client)
            converterFactories(ResponseConverterFactory())
        }.createFollowRequestsApi()
    }
}
