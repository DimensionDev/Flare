package dev.dimension.flare.data.network.vvo

import dev.dimension.flare.data.platform.VVoCredential
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class VVOCookieRefreshTest {
    @Test
    fun configRefreshesStaleCookieBeforeRequestAndPersistsCredential() =
        runTest {
            val oldCookie = "MLOGIN=1; SUB=old-sub; XSRF-TOKEN=old-xsrf"
            val refreshedCookie = "MLOGIN=1; SUB=new-sub; XSRF-TOKEN=old-xsrf; NEW_COOKIE=new-value"
            val credentialFlow =
                MutableStateFlow(
                    VVoCredential(
                        chocolate = oldCookie,
                        lastCookieRefreshEpochMillis = null,
                    ),
                )
            val refreshCookies = mutableListOf<String?>()
            val configCookies = mutableListOf<String?>()

            val service =
                VVOService(
                    credentialFlow = credentialFlow,
                    refreshCookieWhenStale = true,
                    onCredentialRefreshed = { credential ->
                        credentialFlow.value = credential
                    },
                    httpClientFactory =
                        mockHttpClientFactory { request ->
                            when (request.url.encodedPath) {
                                "/" -> {
                                    refreshCookies += request.headers[HttpHeaders.Cookie]
                                    setCookieResponse(
                                        "SUB=new-sub; Path=/; Domain=.weibo.cn; HttpOnly",
                                        "NEW_COOKIE=new-value; Path=/; Domain=.weibo.cn; HttpOnly",
                                    )
                                }

                                "/api/config" -> {
                                    configCookies += request.headers[HttpHeaders.Cookie]
                                    configResponse(login = request.headers[HttpHeaders.Cookie] == refreshedCookie)
                                }

                                else -> {
                                    error("Unexpected request path: ${request.url.encodedPath}")
                                }
                            }
                        },
                )

            val response = service.config()

            assertEquals(true, response.data?.login)
            assertEquals(listOf<String?>(oldCookie), refreshCookies)
            assertEquals(listOf<String?>(refreshedCookie), configCookies)
            assertEquals(refreshedCookie, credentialFlow.value.chocolate)
            assertTrue(credentialFlow.value.lastCookieRefreshEpochMillis != null)
        }

    @Test
    fun configRefreshesAndRetriesWithReturnedCredentialWhenLoggedOut() =
        runTest {
            val oldCookie = "MLOGIN=1; SUB=old-sub"
            val refreshedCookie = "MLOGIN=1; SUB=new-sub"
            val credentialFlow = MutableStateFlow(VVoCredential(chocolate = oldCookie))
            var refreshedCredential: VVoCredential? = null
            val refreshCookies = mutableListOf<String?>()
            val configCookies = mutableListOf<String?>()

            val service =
                VVOService(
                    credentialFlow = credentialFlow,
                    refreshCookieWhenStale = false,
                    onCredentialRefreshed = { credential ->
                        refreshedCredential = credential
                    },
                    httpClientFactory =
                        mockHttpClientFactory { request ->
                            when (request.url.encodedPath) {
                                "/" -> {
                                    refreshCookies += request.headers[HttpHeaders.Cookie]
                                    setCookieResponse("SUB=new-sub; Path=/; Domain=.weibo.cn; HttpOnly")
                                }

                                "/api/config" -> {
                                    val cookie = request.headers[HttpHeaders.Cookie]
                                    configCookies += cookie
                                    configResponse(login = cookie == refreshedCookie)
                                }

                                else -> {
                                    error("Unexpected request path: ${request.url.encodedPath}")
                                }
                            }
                        },
                )

            val response = service.config()

            assertEquals(true, response.data?.login)
            assertEquals(listOf<String?>(oldCookie), refreshCookies)
            assertEquals(listOf<String?>(oldCookie, refreshedCookie), configCookies)
            assertEquals(oldCookie, credentialFlow.value.chocolate)
            assertEquals(refreshedCookie, refreshedCredential?.chocolate)
            assertTrue(refreshedCredential?.lastCookieRefreshEpochMillis != null)
        }

    @Test
    fun concurrentLoggedOutConfigRequestsReuseSingleRefresh() =
        runTest {
            val oldCookie = "MLOGIN=1; SUB=old-sub"
            val refreshedCookie = "MLOGIN=1; SUB=new-sub"
            val credentialFlow = MutableStateFlow(VVoCredential(chocolate = oldCookie))
            val bothLoggedOutReached = CompletableDeferred<Unit>()
            var oldConfigCalls = 0
            var refreshCalls = 0
            val refreshCookies = mutableListOf<String?>()
            val configCookies = mutableListOf<String?>()

            val service =
                VVOService(
                    credentialFlow = credentialFlow,
                    refreshCookieWhenStale = false,
                    onCredentialRefreshed = { credential ->
                        credentialFlow.value = credential
                    },
                    httpClientFactory =
                        mockHttpClientFactory { request ->
                            when (request.url.encodedPath) {
                                "/" -> {
                                    refreshCalls += 1
                                    refreshCookies += request.headers[HttpHeaders.Cookie]
                                    when (refreshCalls) {
                                        1 -> {
                                            setCookieResponse("SUB=new-sub; Path=/; Domain=.weibo.cn; HttpOnly")
                                        }

                                        else -> {
                                            error("Unexpected refresh call count: $refreshCalls")
                                        }
                                    }
                                }

                                "/api/config" -> {
                                    val cookie = request.headers[HttpHeaders.Cookie]
                                    configCookies += cookie
                                    when (cookie) {
                                        oldCookie -> {
                                            val callIndex = ++oldConfigCalls
                                            when (callIndex) {
                                                1 -> {
                                                    withTimeout(5_000) {
                                                        bothLoggedOutReached.await()
                                                    }
                                                }

                                                2 -> {
                                                    bothLoggedOutReached.complete(Unit)
                                                }

                                                else -> {
                                                    error("Unexpected old config call count: $callIndex")
                                                }
                                            }
                                            configResponse(login = false)
                                        }

                                        refreshedCookie -> {
                                            configResponse(login = true)
                                        }

                                        else -> {
                                            error("Unexpected config cookie: $cookie")
                                        }
                                    }
                                }

                                else -> {
                                    error("Unexpected request path: ${request.url.encodedPath}")
                                }
                            }
                        },
                )

            val results =
                supervisorScope {
                    val requestOne =
                        async(start = CoroutineStart.UNDISPATCHED) {
                            service.config().data?.login
                        }
                    val requestTwo =
                        async(start = CoroutineStart.UNDISPATCHED) {
                            service.config().data?.login
                        }
                    awaitAll(requestOne, requestTwo)
                }

            assertEquals(listOf(true, true), results)
            assertEquals(1, refreshCalls)
            assertEquals(listOf<String?>(oldCookie), refreshCookies)
            assertEquals(
                listOf<String?>(
                    oldCookie,
                    oldCookie,
                    refreshedCookie,
                    refreshedCookie,
                ),
                configCookies,
            )
            assertEquals(refreshedCookie, credentialFlow.value.chocolate)
        }

    @Test
    fun configDoesNotRefreshFreshCookieBeforeSuccessfulRequest() =
        runTest {
            val cookie = "MLOGIN=1; SUB=current-sub"
            val credentialFlow =
                MutableStateFlow(
                    VVoCredential(
                        chocolate = cookie,
                        lastCookieRefreshEpochMillis = Clock.System.now().toEpochMilliseconds(),
                    ),
                )
            var refreshCalls = 0
            val configCookies = mutableListOf<String?>()

            val service =
                VVOService(
                    credentialFlow = credentialFlow,
                    refreshCookieWhenStale = true,
                    onCredentialRefreshed = { credential ->
                        credentialFlow.value = credential
                    },
                    httpClientFactory =
                        mockHttpClientFactory { request ->
                            when (request.url.encodedPath) {
                                "/" -> {
                                    refreshCalls += 1
                                    setCookieResponse("SUB=new-sub; Path=/; Domain=.weibo.cn; HttpOnly")
                                }

                                "/api/config" -> {
                                    configCookies += request.headers[HttpHeaders.Cookie]
                                    configResponse(login = true)
                                }

                                else -> {
                                    error("Unexpected request path: ${request.url.encodedPath}")
                                }
                            }
                        },
                )

            val response = service.config()

            assertEquals(true, response.data?.login)
            assertEquals(0, refreshCalls)
            assertEquals(listOf<String?>(cookie), configCookies)
            assertEquals(cookie, credentialFlow.value.chocolate)
        }

    @Test
    fun vvoCredentialDefaultsLastCookieRefreshToNull() {
        val credential =
            Json.decodeFromString(
                VVoCredential.serializer(),
                """{"chocolate":"MLOGIN=1"}""",
            )

        assertEquals("MLOGIN=1", credential.chocolate)
        assertNull(credential.lastCookieRefreshEpochMillis)
    }

    @Test
    fun shouldRefreshVvoCookieWhenMissingOrOlderThanOneDay() {
        val now = 2.days.inWholeMilliseconds

        assertTrue(shouldRefreshVvoCookie(lastRefreshEpochMillis = null, nowEpochMillis = now))
        assertFalse(shouldRefreshVvoCookie(lastRefreshEpochMillis = now - 1.days.inWholeMilliseconds, nowEpochMillis = now))
        assertTrue(shouldRefreshVvoCookie(lastRefreshEpochMillis = now - 1.days.inWholeMilliseconds - 1, nowEpochMillis = now))
    }

    @Test
    fun mergeVvoCookieHeaderKeepsExistingCookiesMissingFromSetCookie() {
        val merged =
            mergeVvoCookieHeader(
                currentCookieHeader = "MLOGIN=1; SUB=old-sub; XSRF-TOKEN=old-xsrf",
                setCookieHeaders =
                    listOf(
                        "SUB=new-sub; Path=/; Domain=.weibo.cn; HttpOnly",
                    ),
            )

        assertEquals("MLOGIN=1; SUB=new-sub; XSRF-TOKEN=old-xsrf", merged)
    }

    @Test
    fun mergeVvoCookieHeaderAddsNewCookies() {
        val merged =
            mergeVvoCookieHeader(
                currentCookieHeader = "MLOGIN=1",
                setCookieHeaders =
                    listOf(
                        "SUB=new-sub; Path=/; Domain=.weibo.cn; HttpOnly",
                    ),
            )

        assertEquals("MLOGIN=1; SUB=new-sub", merged)
    }

    @Test
    fun mergeVvoCookieHeaderSkipsMaxAgeExpiredCookies() {
        val merged =
            mergeVvoCookieHeader(
                currentCookieHeader = "MLOGIN=1; SUB=old-sub; XSRF-TOKEN=old-xsrf",
                setCookieHeaders =
                    listOf(
                        "SUB=; Max-Age=0; Path=/; Domain=.weibo.cn",
                    ),
            )

        assertNull(merged)
    }

    @Test
    fun mergeVvoCookieHeaderSkipsExpiresExpiredCookies() {
        val merged =
            mergeVvoCookieHeader(
                currentCookieHeader = "MLOGIN=1; SUB=old-sub; XSRF-TOKEN=old-xsrf",
                setCookieHeaders =
                    listOf(
                        "SUB=new-sub; Expires=Thu, 01-Jan-1970 00:00:00 GMT; Path=/; Domain=.weibo.cn",
                    ),
            )

        assertNull(merged)
    }

    @Test
    fun mergeVvoCookieHeaderKeepsExistingCookiesWhenExpiredCookieIsMixedWithFreshCookie() {
        val merged =
            mergeVvoCookieHeader(
                currentCookieHeader = "MLOGIN=1; SUB=old-sub; XSRF-TOKEN=old-xsrf",
                setCookieHeaders =
                    listOf(
                        "SUB=new-sub; Expires=Thu, 01-Jan-1970 00:00:00 GMT; Path=/; Domain=.weibo.cn",
                        "MLOGIN=1; Expires=Fri, 31 Dec 9999 23:59:59 GMT; Path=/; Domain=.weibo.cn",
                        "NEW_COOKIE=new-value; Expires=Fri, 31 Dec 9999 23:59:59 GMT; Path=/; Domain=.weibo.cn",
                    ),
            )

        assertEquals("MLOGIN=1; SUB=old-sub; XSRF-TOKEN=old-xsrf; NEW_COOKIE=new-value", merged)
    }

    @Test
    fun mergeVvoCookieHeaderReturnsNullWhenNothingChanges() {
        val merged =
            mergeVvoCookieHeader(
                currentCookieHeader = "MLOGIN=1; SUB=old-sub",
                setCookieHeaders =
                    listOf(
                        "SUB=old-sub; Path=/; Domain=.weibo.cn; HttpOnly",
                    ),
            )

        assertNull(merged)
    }

    private fun mockHttpClientFactory(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): (HttpClientConfig<*>.() -> Unit) -> HttpClient =
        { config ->
            HttpClient(
                MockEngine { request ->
                    handler(request)
                },
            ) {
                config.invoke(this)
            }
        }

    private fun MockRequestHandleScope.configResponse(login: Boolean): HttpResponseData =
        jsonResponse(
            """
            {
              "ok": 1,
              "data": {
                "login": $login,
                "st": "st-value",
                "uid": "uid-value"
              }
            }
            """.trimIndent(),
        )

    private fun MockRequestHandleScope.jsonResponse(body: String): HttpResponseData =
        respond(
            content = body,
            status = HttpStatusCode.OK,
            headers =
                Headers.build {
                    append(HttpHeaders.ContentType, "application/json")
                },
        )

    private fun MockRequestHandleScope.setCookieResponse(vararg setCookieHeaders: String): HttpResponseData =
        respond(
            content = "",
            status = HttpStatusCode.OK,
            headers =
                Headers.build {
                    setCookieHeaders.forEach { setCookie ->
                        append(HttpHeaders.SetCookie, setCookie)
                    }
                },
        )
}
