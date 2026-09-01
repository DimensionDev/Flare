package dev.dimension.flare.data.datasource.misskey

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.network.misskey.MisskeyService
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.test.runTest
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals

class StatusDetailRemoteMediatorTest {
    @Test
    fun loadsAncestorsAndNestedDescendantsAroundDetailPost() =
        runTest {
            val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            server.createContext("/api/notes/show") { it.respond(DETAIL_JSON) }
            server.createContext("/api/notes/conversation") { it.respond("[$PARENT_JSON,$GRANDPARENT_JSON]") }
            server.createContext("/api/notes/children") { exchange ->
                val body = exchange.requestBody.bufferedReader().use { it.readText() }
                exchange.respond(
                    when {
                        body.contains("\"noteId\":\"detail\"") -> "[$CHILD_JSON]"
                        body.contains("\"noteId\":\"child\"") -> "[$GRANDCHILD_JSON]"
                        else -> "[]"
                    },
                )
            }
            server.start()

            try {
                val mediator =
                    StatusDetailRemoteMediator(
                        statusKey = MicroBlogKey("detail", "example.test"),
                        accountKey = MicroBlogKey("me", "example.test"),
                        service = MisskeyService("http://127.0.0.1:${server.address.port}/api/"),
                        statusOnly = false,
                    )

                val refresh = mediator.load(pageSize = 20, request = PagingRequest.Refresh)
                val append = mediator.load(pageSize = 20, request = PagingRequest.Append(refresh.nextKey.orEmpty()))

                assertEquals(
                    listOf("grandparent", "parent", "detail", "child", "grandchild"),
                    (refresh.data + append.data).map { it.statusKey.id },
                )
                assertEquals("child", append.nextKey)
            } finally {
                server.stop(0)
            }
        }

    private fun HttpExchange.respond(body: String) {
        val bytes = body.encodeToByteArray()
        responseHeaders.add("Content-Type", "application/json")
        sendResponseHeaders(200, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }

    private companion object {
        const val GRANDPARENT_JSON =
            """{"id":"grandparent","createdAt":"2024-01-01T00:00:00Z","text":"grandparent","userId":"user","user":{"id":"user","username":"tester"},"visibility":"public","reactions":{},"renoteCount":0,"repliesCount":1}"""

        const val PARENT_JSON =
            """{"id":"parent","createdAt":"2024-01-01T00:01:00Z","text":"parent","userId":"user","user":{"id":"user","username":"tester"},"visibility":"public","reactions":{},"renoteCount":0,"repliesCount":1,"replyId":"grandparent","reply":$GRANDPARENT_JSON}"""

        const val DETAIL_JSON =
            """{"id":"detail","createdAt":"2024-01-01T00:02:00Z","text":"detail","userId":"user","user":{"id":"user","username":"tester"},"visibility":"public","reactions":{},"renoteCount":0,"repliesCount":1,"replyId":"parent","reply":$PARENT_JSON}"""

        const val CHILD_JSON =
            """{"id":"child","createdAt":"2024-01-01T00:03:00Z","text":"child","userId":"user","user":{"id":"user","username":"tester"},"visibility":"public","reactions":{},"renoteCount":0,"repliesCount":1,"replyId":"detail","reply":$DETAIL_JSON}"""

        const val GRANDCHILD_JSON =
            """{"id":"grandchild","createdAt":"2024-01-01T00:04:00Z","text":"grandchild","userId":"user","user":{"id":"user","username":"tester"},"visibility":"public","reactions":{},"renoteCount":0,"repliesCount":0,"replyId":"child","reply":$CHILD_JSON}"""
    }
}
