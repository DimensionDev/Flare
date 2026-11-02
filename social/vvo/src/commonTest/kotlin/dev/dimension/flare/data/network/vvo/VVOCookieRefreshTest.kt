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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
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
    fun configKeepsRefreshingUntilLoginRecoversWithinRetryLimit() =
        runTest {
            val oldCookie = "MLOGIN=1; SUB=old-sub"
            val recoveredCookie = "MLOGIN=1; SUB=retry-3"
            val credentialFlow = MutableStateFlow(VVoCredential(chocolate = oldCookie))
            var refreshCalls = 0
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
                                    setCookieResponse("SUB=retry-$refreshCalls; Path=/; Domain=.weibo.cn; HttpOnly")
                                }

                                "/api/config" -> {
                                    val cookie = request.headers[HttpHeaders.Cookie]
                                    configCookies += cookie
                                    configResponse(login = cookie == recoveredCookie)
                                }

                                else -> {
                                    error("Unexpected request path: ${request.url.encodedPath}")
                                }
                            }
                        },
                )

            val response = service.config()

            assertEquals(true, response.data?.login)
            assertEquals(3, refreshCalls)
            assertEquals(
                listOf<String?>(
                    oldCookie,
                    "MLOGIN=1; SUB=retry-1",
                    "MLOGIN=1; SUB=retry-2",
                    recoveredCookie,
                ),
                configCookies,
            )
            assertEquals(recoveredCookie, credentialFlow.value.chocolate)
        }

    @Test
    fun refreshFollowsPassportSsoRedirectAndAdoptsNewSub() =
        runTest {
            val oldCookie = "MLOGIN=1; SUB=old-sub; SCF=old-scf; ALF=9999999999"
            val passportCookie = "MLOGIN=1; SUB=old-sub; SCF=new-scf; ALF=9999999999"
            val refreshedCookie = "MLOGIN=1; SUB=new-sub; SCF=new-scf; ALF=9999999999"
            val credentialFlow = MutableStateFlow(VVoCredential(chocolate = oldCookie))
            val homeHops = mutableListOf<String?>()

            val service =
                VVOService(
                    credentialFlow = credentialFlow,
                    refreshCookieWhenStale = false,
                    onCredentialRefreshed = { credentialFlow.value = it },
                    httpClientFactory =
                        mockHttpClientFactory { request ->
                            when {
                                request.url.host == "m.weibo.cn" && request.url.encodedPath == "/" -> {
                                    homeHops += request.headers[HttpHeaders.Cookie]
                                    redirectResponse(
                                        "https://passport.weibo.cn/sso/login?entry=mweibo&ticket=old",
                                        "SCF=new-scf; Path=/; Domain=.weibo.cn; HttpOnly",
                                    )
                                }

                                request.url.host == "passport.weibo.cn" && request.url.encodedPath == "/sso/login" -> {
                                    assertEquals(passportCookie, request.headers[HttpHeaders.Cookie])
                                    redirectResponse(
                                        "https://m.weibo.cn/sso/complete?ticket=new",
                                        "SUB=new-sub; Path=/; Domain=.weibo.cn; HttpOnly",
                                    )
                                }

                                request.url.encodedPath == "/sso/complete" -> {
                                    assertEquals("https://m.weibo.cn/sso/complete?ticket=new", request.url.toString())
                                    assertEquals(refreshedCookie, request.headers[HttpHeaders.Cookie])
                                    setCookieResponse()
                                }

                                request.url.encodedPath == "/api/config" -> {
                                    val cookie = request.headers[HttpHeaders.Cookie]
                                    configResponse(login = cookie?.contains("SUB=new-sub") == true)
                                }

                                else -> {
                                    error("Unexpected request: ${request.url}")
                                }
                            }
                        },
                )

            val response = service.config()

            assertEquals(true, response.data?.login)
            assertEquals(refreshedCookie, credentialFlow.value.chocolate)
            // The home hop is attempted with the stale cookie before the SSO leg re-signs it.
            assertEquals(listOf<String?>(oldCookie), homeHops)
        }

    @Test
    fun refreshStopsBeforeUntrustedRedirectTargets() =
        runTest {
            val oldCookie = "MLOGIN=1; SUB=old-sub; SCF=scf; ALF=9999999999"
            val refreshedCookie = "MLOGIN=1; SUB=new-sub; SCF=scf; ALF=9999999999"
            val passportUrl = "https://passport.weibo.cn/sso/login"
            val untrustedLocations =
                listOf(
                    "https://untrusted.example/collect",
                    "//untrusted.example/collect",
                    "https://passport.weibo.cn.untrusted.example/collect",
                    "https://passport.weibo.cn@untrusted.example/collect",
                    "http://passport.weibo.cn/collect",
                    "http://m.weibo.cn/collect",
                    "https://passport.weibo.cn:8443/collect",
                    "https://weibo.com.untrusted.example/collect",
                    "https://untrustedweibo.cn/collect",
                    "https://untrustedweibo.com/collect",
                    "https://login.sina.com.cn.untrusted.example/collect",
                    "https://news.sina.com.cn/collect",
                    "https://untrusted@passport.weibo.com/collect",
                    "http://passport.weibo.com/collect",
                    "https://passport.weibo.com:8443/collect",
                )

            for (location in untrustedLocations) {
                val credentialFlow = MutableStateFlow(VVoCredential(chocolate = oldCookie))
                val refreshHops = mutableListOf<String>()
                val service =
                    VVOService(
                        credentialFlow = credentialFlow,
                        refreshCookieWhenStale = true,
                        onCredentialRefreshed = { credentialFlow.value = it },
                        httpClientFactory =
                            mockHttpClientFactory { request ->
                                if (request.url.encodedPath == "/api/config") {
                                    return@mockHttpClientFactory configResponse(login = true)
                                }
                                refreshHops += request.url.toString()
                                when (request.url.toString()) {
                                    "https://m.weibo.cn/" -> {
                                        redirectResponse(passportUrl)
                                    }

                                    passportUrl -> {
                                        redirectResponse(
                                            location,
                                            "SUB=new-sub; Path=/; Domain=.weibo.cn; HttpOnly",
                                        )
                                    }

                                    else -> {
                                        setCookieResponse("SUB=untrusted-sub; Path=/")
                                    }
                                }
                            },
                    )

                assertEquals(true, service.config().data?.login)
                assertEquals(listOf("https://m.weibo.cn/", passportUrl), refreshHops, location)
                assertEquals(refreshedCookie, credentialFlow.value.chocolate, location)
            }
        }

    @Test
    fun refreshSharesTheCookieHeaderAcrossWeiboAndSinaSsoHops() =
        runTest {
            val oldCookie = "MLOGIN=1; SUB=old-sub; ALF=9999999999"
            val afterHome = "$oldCookie; SCF=shared-scf"
            val afterCom = "MLOGIN=1; SUB=com-sub; ALF=9999999999; SCF=shared-scf"
            val afterSina = "$afterCom; SSO=sina-sso"
            val refreshedCookie = "MLOGIN=1; SUB=cn-new-sub; ALF=9999999999; SCF=shared-scf; SSO=sina-sso"
            val hops =
                listOf(
                    Triple("https://m.weibo.cn/", oldCookie, listOf("SCF=shared-scf; Domain=.weibo.cn; Path=/; Secure")),
                    Triple("https://visitor.passport.weibo.cn/visitor/visitor?entry=sinawap", afterHome, emptyList()),
                    Triple(
                        "https://passport.weibo.com/sso/signin?entry=wapsso",
                        afterHome,
                        listOf("SUB=com-sub; Domain=.weibo.com; Path=/; Secure"),
                    ),
                    Triple(
                        "https://login.sina.com.cn/sso/login.php?entry=weibo",
                        afterCom,
                        listOf("SSO=sina-sso; Domain=.sina.com.cn; Path=/; Secure"),
                    ),
                    Triple("https://weibo.com/sso/crossdomain?ticket=one", afterSina, emptyList()),
                    Triple(
                        "https://passport.weibo.cn/sso/complete?ticket=two",
                        afterSina,
                        listOf("SUB=cn-new-sub; Domain=.weibo.cn; Path=/; Secure"),
                    ),
                )
            val credentialFlow = MutableStateFlow(VVoCredential(chocolate = oldCookie))
            var hopIndex = 0
            val service =
                VVOService(
                    credentialFlow = credentialFlow,
                    refreshCookieWhenStale = true,
                    onCredentialRefreshed = { credentialFlow.value = it },
                    httpClientFactory =
                        mockHttpClientFactory { request ->
                            if (request.url.encodedPath == "/api/config") {
                                assertEquals(refreshedCookie, request.headers[HttpHeaders.Cookie])
                                return@mockHttpClientFactory configResponse(login = true)
                            }
                            val (url, cookie, setCookies) = hops[hopIndex++]
                            assertEquals(url, request.url.toString())
                            assertEquals(cookie, request.headers[HttpHeaders.Cookie])
                            if (hopIndex < hops.size) {
                                redirectResponse(hops[hopIndex].first, *setCookies.toTypedArray())
                            } else {
                                setCookieResponse(*setCookies.toTypedArray())
                            }
                        },
                )

            assertEquals(true, service.config().data?.login)
            assertEquals(hops.size, hopIndex)
            assertEquals(refreshedCookie, credentialFlow.value.chocolate)
        }

    @Test
    fun refreshStopsOnceTheAlfSessionWindowHasClosed() =
        runTest {
            val expiredCookie = "MLOGIN=1; SUB=old-sub; ALF=1000"
            val credentialFlow = MutableStateFlow(VVoCredential(chocolate = expiredCookie))
            var refreshHops = 0

            val service =
                VVOService(
                    credentialFlow = credentialFlow,
                    refreshCookieWhenStale = false,
                    onCredentialRefreshed = { credentialFlow.value = it },
                    httpClientFactory =
                        mockHttpClientFactory { request ->
                            when (request.url.encodedPath) {
                                "/" -> {
                                    refreshHops += 1
                                    setCookieResponse("SUB=new-sub; Path=/; Domain=.weibo.cn; HttpOnly")
                                }

                                "/api/config" -> {
                                    configResponse(login = false)
                                }

                                else -> {
                                    error("Unexpected request: ${request.url}")
                                }
                            }
                        },
                )

            val response = service.config()

            assertEquals(false, response.data?.login)
            assertEquals(0, refreshHops)
            assertEquals(expiredCookie, credentialFlow.value.chocolate)
        }

    @Test
    fun configReturnsLoginFalseAfterRetryLimitIsExhausted() =
        runTest {
            val oldCookie = "MLOGIN=1; SUB=old-sub"
            val credentialFlow = MutableStateFlow(VVoCredential(chocolate = oldCookie))
            var refreshCalls = 0
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
                                    setCookieResponse("SUB=retry-$refreshCalls; Path=/; Domain=.weibo.cn; HttpOnly")
                                }

                                "/api/config" -> {
                                    configCookies += request.headers[HttpHeaders.Cookie]
                                    configResponse(login = false)
                                }

                                else -> {
                                    error("Unexpected request path: ${request.url.encodedPath}")
                                }
                            }
                        },
                )

            val response = service.config()

            assertEquals(false, response.data?.login)
            assertEquals(5, refreshCalls)
            assertEquals(
                listOf<String?>(
                    oldCookie,
                    "MLOGIN=1; SUB=retry-1",
                    "MLOGIN=1; SUB=retry-2",
                    "MLOGIN=1; SUB=retry-3",
                    "MLOGIN=1; SUB=retry-4",
                    "MLOGIN=1; SUB=retry-5",
                ),
                configCookies,
            )
            assertEquals("MLOGIN=1; SUB=retry-5", credentialFlow.value.chocolate)
        }

    @Test
    fun configDelaysBeforeEachLoginRetry() =
        runTest {
            val oldCookie = "MLOGIN=1; SUB=old-sub"
            val credentialFlow = MutableStateFlow(VVoCredential(chocolate = oldCookie))
            var refreshCalls = 0
            val configRequestTimes = mutableListOf<Long>()

            val service =
                VVOService(
                    credentialFlow = credentialFlow,
                    refreshCookieWhenStale = false,
                    configLoginRetryDelay = 500.milliseconds,
                    onCredentialRefreshed = { credential ->
                        credentialFlow.value = credential
                    },
                    httpClientFactory =
                        mockHttpClientFactory { request ->
                            when (request.url.encodedPath) {
                                "/" -> {
                                    refreshCalls += 1
                                    setCookieResponse("SUB=retry-$refreshCalls; Path=/; Domain=.weibo.cn; HttpOnly")
                                }

                                "/api/config" -> {
                                    configRequestTimes += testScheduler.currentTime
                                    configResponse(login = false)
                                }

                                else -> {
                                    error("Unexpected request path: ${request.url.encodedPath}")
                                }
                            }
                        },
                )

            val response = service.config()

            assertEquals(false, response.data?.login)
            assertEquals(
                listOf(
                    0L,
                    500L,
                    1_000L,
                    1_500L,
                    2_000L,
                    2_500L,
                ),
                configRequestTimes,
            )
        }

    @Test
    fun concurrentLoggedOutConfigRequestsReuseSingleRefresh() =
        runTest {
            val oldCookie = "MLOGIN=1; SUB=old-sub"
            val refreshedCookie = "MLOGIN=1; SUB=new-sub"
            val credentialFlow = MutableStateFlow(VVoCredential(chocolate = oldCookie))
            val bothLoggedOutReached = CompletableDeferred<Unit>()
            val requestStateMutex = Mutex()
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
                                    val refreshCallIndex =
                                        requestStateMutex.withLock {
                                            refreshCookies += request.headers[HttpHeaders.Cookie]
                                            ++refreshCalls
                                        }
                                    when (refreshCallIndex) {
                                        1 -> {
                                            setCookieResponse("SUB=new-sub; Path=/; Domain=.weibo.cn; HttpOnly")
                                        }

                                        else -> {
                                            error("Unexpected refresh call count: $refreshCallIndex")
                                        }
                                    }
                                }

                                "/api/config" -> {
                                    val cookie = request.headers[HttpHeaders.Cookie]
                                    val oldConfigCallIndex =
                                        requestStateMutex.withLock {
                                            configCookies += cookie
                                            if (cookie == oldCookie) {
                                                ++oldConfigCalls
                                            } else {
                                                null
                                            }
                                        }
                                    when (cookie) {
                                        oldCookie -> {
                                            when (oldConfigCallIndex) {
                                                1 -> {
                                                    withTimeout(5_000) {
                                                        bothLoggedOutReached.await()
                                                    }
                                                }

                                                2 -> {
                                                    bothLoggedOutReached.complete(Unit)
                                                }

                                                else -> {
                                                    error("Unexpected old config call count: $oldConfigCallIndex")
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
    fun shouldRefreshVvoCookieWhenMissingOrOlderThanInterval() {
        val now = 1.days.inWholeMilliseconds
        val interval = 6.hours.inWholeMilliseconds

        assertTrue(shouldRefreshVvoCookie(lastRefreshEpochMillis = null, nowEpochMillis = now))
        assertFalse(shouldRefreshVvoCookie(lastRefreshEpochMillis = now - interval + 1, nowEpochMillis = now))
        assertTrue(shouldRefreshVvoCookie(lastRefreshEpochMillis = now - interval - 1, nowEpochMillis = now))
    }

    @Test
    fun isRefreshRedirectRequiresRedirectStatusAndLocation() {
        assertTrue(isRefreshRedirect(302, "https://passport.weibo.cn/sso/login"))
        assertTrue(isRefreshRedirect(301, "https://passport.weibo.cn/"))
        assertFalse(isRefreshRedirect(200, "https://passport.weibo.cn/"))
        assertFalse(isRefreshRedirect(302, null))
        assertFalse(isRefreshRedirect(302, ""))
    }

    @Test
    fun resolveRefreshRedirectUrlResolvesAbsoluteAndRelativeLocations() {
        assertEquals(
            "https://passport.weibo.cn/sso/login?entry=mweibo",
            resolveRefreshRedirectUrl(
                current = "https://m.weibo.cn/?entry=old",
                location = "https://passport.weibo.cn/sso/login?entry=mweibo",
            ),
        )
        assertEquals(
            "https://m.weibo.cn/sso/crossdomain?action=login",
            resolveRefreshRedirectUrl(
                current = "https://m.weibo.cn/home?action=old",
                location = "/sso/crossdomain?action=login",
            ),
        )
        assertEquals(
            "https://m.weibo.cn/home?action=login",
            resolveRefreshRedirectUrl(
                current = "https://m.weibo.cn/home?action=old",
                location = "?action=login",
            ),
        )
        assertEquals(
            "https://m.weibo.cn/",
            resolveRefreshRedirectUrl(
                current = "https://passport.weibo.cn/sso/login?ticket=old",
                location = "https://m.weibo.cn/",
            ),
        )
    }

    @Test
    fun resolveRefreshRedirectUrlAcceptsWeiboSitesAndSinaSso() {
        val locations =
            listOf(
                "https://weibo.cn/",
                "https://weibo.com/",
                "https://www.weibo.com/",
                "https://visitor.passport.weibo.cn/visitor/visitor",
                "https://visitor.passport.weibo.com/visitor/visitor",
                "https://passport.weibo.com/sso/signin",
                "https://login.sina.com.cn/sso/login.php",
                "https://PASSPORT.WEIBO.COM:443/sso/signin",
                "//visitor.passport.weibo.com/visitor/visitor",
            )
        for (location in locations) {
            assertNotNull(resolveRefreshRedirectUrl(current = "https://m.weibo.cn/", location = location), location)
        }
    }

    @Test
    fun vvoSessionExpiryReadsAlfTimestampAsEpochMillis() {
        assertEquals(1_794_367_522_000L, vvoSessionExpiryEpochMillis("MLOGIN=1; ALF=1794367522; SUB=value"))
        assertNull(vvoSessionExpiryEpochMillis("MLOGIN=1; SUB=value"))
        assertNull(vvoSessionExpiryEpochMillis("MLOGIN=1; ALF=0; SUB=value"))
    }

    @Test
    fun isVvoSessionExpiredComparesAgainstAlf() {
        val cookie = "MLOGIN=1; ALF=1000; SUB=value"
        assertTrue(isVvoSessionExpired(cookie, nowEpochMillis = 2_000_000L))
        assertFalse(isVvoSessionExpired(cookie, nowEpochMillis = 500_000L))
        // Without ALF there is nothing to say the session is over.
        assertFalse(isVvoSessionExpired("MLOGIN=1; SUB=value", nowEpochMillis = Long.MAX_VALUE))
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

    private fun MockRequestHandleScope.redirectResponse(
        location: String,
        vararg setCookieHeaders: String,
    ): HttpResponseData =
        respond(
            content = "",
            status = HttpStatusCode.Found,
            headers =
                Headers.build {
                    append(HttpHeaders.Location, location)
                    setCookieHeaders.forEach { setCookie ->
                        append(HttpHeaders.SetCookie, setCookie)
                    }
                },
        )
}
