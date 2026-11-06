package dev.dimension.flare.feature.plugin.host

import dev.dimension.flare.feature.plugin.wire.HttpAuthorizationV1
import dev.dimension.flare.feature.plugin.wire.HttpBodyV1
import dev.dimension.flare.feature.plugin.wire.HttpRedirectV1
import dev.dimension.flare.feature.plugin.wire.HttpRequestV1
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PluginHttpV2Test {
    private val origin = "https://instance.example"
    private val context = PluginInvocationContextV1.guest("dev.example.plugin", "example", "a".repeat(64), origin, "en")

    @Test
    fun manualReturnsOneHopAndApplicationHeadersWithoutTransportSecrets() =
        runBlocking {
            for (method in listOf("GET", "POST")) {
                val calls = mutableListOf<PluginTransportRequestV1>()
                val host =
                    PluginHttpHost(
                        object : PluginHttpTransport {
                            override suspend fun execute(request: PluginTransportRequestV1): PluginTransportResponseV1 {
                                calls += request
                                return PluginTransportResponseV1(
                                    307,
                                    mapOf(
                                        "Set-Cookie" to
                                            listOf("first=one; Expires=Wed, 09 Jun 2038 10:18:14 GMT; Path=/, second=two; Path=/"),
                                        "Location" to listOf("../finish?step=2#fragment"),
                                        "Authorization" to listOf("Custom refreshed"),
                                        "X-Token" to listOf("one", "two"),
                                        "Connection" to listOf("X-Internal"),
                                        "X-Internal" to listOf("hidden"),
                                        "Proxy-Authenticate" to listOf("hidden"),
                                    ),
                                    "redirect".encodeToByteArray(),
                                )
                            }
                        },
                    )
                val response =
                    host.execute(
                        HttpRequestV1(
                            method = method,
                            url = "$origin/start/path?old=1",
                            body = if (method == "POST") HttpBodyV1.Text("once") else null,
                            redirect = HttpRedirectV1.Manual,
                        ),
                        context,
                        5_000,
                        apiVersion = 2,
                    )
                assertEquals(1, calls.size)
                assertEquals(307, response.status)
                assertEquals("$origin/start/path?old=1", response.url)
                assertEquals("$origin/finish?step=2", response.redirectUrl)
                assertEquals(listOf("Custom refreshed"), response.headers["authorization"])
                assertEquals(listOf("one", "two"), response.headers["x-token"])
                assertEquals(2, response.headers.getValue("set-cookie").size)
                assertTrue(response.headers.keys.none { it in setOf("connection", "x-internal", "proxy-authenticate") })
            }
        }

    @Test
    fun unapprovedOrAmbiguousLocationIsDataAndNeverStartsAnotherRequest() =
        runBlocking {
            for (locations in listOf(
                listOf("https://other.example/collect"),
                listOf("/first", "/second"),
                listOf("http://instance.example/"),
                listOf(""),
                listOf("   "),
                listOf("https://user:password@instance.example/path"),
            )) {
                var calls = 0
                val host =
                    PluginHttpHost(
                        object : PluginHttpTransport {
                            override suspend fun execute(request: PluginTransportRequestV1): PluginTransportResponseV1 {
                                calls++
                                return PluginTransportResponseV1(
                                    302,
                                    mapOf("Location" to locations, "Set-Cookie" to listOf("session=new")),
                                    byteArrayOf(),
                                )
                            }
                        },
                    )
                val response = host.execute(HttpRequestV1(url = origin, redirect = HttpRedirectV1.Manual), context, 5_000, 2)
                assertEquals(locations, response.headers["location"])
                assertEquals(listOf("session=new"), response.headers["set-cookie"])
                assertNull(response.redirectUrl)
                assertEquals(1, calls)
            }
        }

    @Test
    fun redirectTargetsResolveQueriesFragmentsRelativeAndEncodedPaths() =
        runBlocking {
            val targets =
                mapOf(
                    "next//step" to "$origin/a/next//step",
                    "next///./step/../end" to "$origin/a/next///end",
                    "?next=2" to "$origin/a/encoded%20path?next=2",
                    "#fragment" to "$origin/a/encoded%20path?previous=1",
                    "../finish?value=%2F#fragment" to "$origin/finish?value=%2F",
                    "//instance.example/finish" to "$origin/finish",
                    "/finish?next=2#fragment" to "$origin/finish?next=2",
                )
            for ((location, target) in targets) {
                val host =
                    PluginHttpHost(
                        object : PluginHttpTransport {
                            override suspend fun execute(request: PluginTransportRequestV1) =
                                PluginTransportResponseV1(
                                    302,
                                    mapOf("Location" to listOf(location)),
                                    byteArrayOf(),
                                )
                        },
                    )
                assertEquals(
                    target,
                    host
                        .execute(
                            HttpRequestV1(url = "$origin/a/encoded%20path?previous=1", redirect = HttpRedirectV1.Manual),
                            context,
                            5_000,
                            2,
                        ).redirectUrl,
                    location,
                )
            }
        }

    @Test
    fun v1CannotOptIntoV2WithARequestField() =
        runBlocking {
            var calls = 0
            val host =
                PluginHttpHost(
                    object : PluginHttpTransport {
                        override suspend fun execute(request: PluginTransportRequestV1): PluginTransportResponseV1 {
                            calls++
                            return PluginTransportResponseV1(200, emptyMap(), byteArrayOf())
                        }
                    },
                )
            assertFailsWith<IllegalArgumentException> {
                host.execute(HttpRequestV1(url = origin, redirect = HttpRedirectV1.Manual), context, 5_000, 1)
            }
            assertEquals(0, calls)
        }

    @Test
    fun generatedHeadersAndFoldedCookiesStillObeyHeaderLimits() =
        runBlocking {
            var calls = 0
            val host =
                PluginHttpHost(
                    object : PluginHttpTransport {
                        override suspend fun execute(request: PluginTransportRequestV1): PluginTransportResponseV1 {
                            calls++
                            return PluginTransportResponseV1(
                                200,
                                mapOf(
                                    "Set-Cookie" to listOf((1..17).joinToString(", ") { "cookie$it=value" }),
                                ),
                                byteArrayOf(),
                            )
                        }
                    },
                )
            val oversizedAuthorization = HttpAuthorizationV1.Bearer("x".repeat(64 * 1024))
            assertFailsWith<IllegalArgumentException> {
                host.execute(
                    HttpRequestV1(url = origin, authorization = oversizedAuthorization),
                    context,
                    5_000,
                    2,
                )
            }
            assertEquals(0, calls)
            assertFailsWith<IllegalArgumentException> { host.execute(HttpRequestV1(url = origin), context, 5_000, 2) }
            assertEquals(1, calls)
        }

    @Test
    fun combinedCookiesPreserveExpiresAndQuotedCommas() {
        val first = "first=one; Expires=Wed, 09 Jun 2038 10:18:14 GMT"
        val second = "second=\"one,two=three\"; Path=/"
        val third = "third=last; Max-Age=0"
        assertEquals(listOf(first, second, third), splitCombinedSetCookie("$first, $second, $third"))
        assertEquals(listOf(second), splitCombinedSetCookie(second))
    }
}
