package dev.dimension.flare.data.datasource.vvo

import androidx.paging.PagingSource
import dev.dimension.flare.data.datasource.microblog.capabilities
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.model.tab.TimelineCandidate
import dev.dimension.flare.data.model.tab.TimelineSpec
import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.data.platform.VVoCredential
import dev.dimension.flare.data.platform.VvoPlatformSpec
import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiText
import dev.dimension.flare.ui.model.mapper.VvoRenderTestModule
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromHexString
import kotlinx.serialization.encodeToHexString
import kotlinx.serialization.protobuf.ProtoBuf
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.plugin.module.dsl.modules
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration

class VVOGroupTest {
    private val accountKey = MicroBlogKey("reader", "m.weibo.cn")

    @BeforeTest
    fun setup() {
        startKoin { modules(VvoRenderTestModule::class) }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun catalogUsesOnlyGroupsAndPreservesNumericIdsAndServerNames() =
        runTest {
            val service =
                service { request ->
                    assertEquals("/api/config/list", request.url.encodedPath)
                    assertEquals("MLOGIN=1; SUB=test", request.headers[HttpHeaders.Cookie])
                    """{"ok":1,"data":{"groups":[{"gid":9999999999999999999,"name":"科技"},{"gid":"custom/2","name":"朋友"},{"gid":"custom/2","name":"重复"}],"channel":[{"gid":"channel","name":"热门"}]}}"""
                }

            val result = assertIs<PagingSource.LoadResult.Page<Int, *>>(catalog(service))
            val candidates = result.data.map { assertIs<TimelineCandidate<*>>(it) }
            val data = candidates.map { assertIs<TimelineSpec.AccountResourceData>(it.target.data) }

            assertEquals(listOf("9999999999999999999", "custom/2"), data.map { it.resourceId })
            assertEquals(listOf(accountKey, accountKey), data.map { it.accountKey })
            assertEquals(listOf(UiText.Raw("科技"), UiText.Raw("朋友")), candidates.map { it.title })
            assertSame(VvoPlatformSpec.groupTimelineSpec, candidates.first().target.spec)
            assertNull(result.prevKey)
            assertNull(result.nextKey)
        }

    @Test
    fun loggedInEmptyCatalogIsSuccessful() =
        runTest {
            val result = catalog(service { """{"ok":1,"data":{"groups":[]}}""" })

            assertTrue(assertIs<PagingSource.LoadResult.Page<Int, *>>(result).data.isEmpty())
        }

    @Test
    fun expiredLoginDoesNotRequestAnAnonymousCatalog() =
        runTest {
            val result = catalog(service(loggedIn = false) { error("Must not load a logged-out catalog") })

            assertIs<LoginExpiredException>(assertIs<PagingSource.LoadResult.Error<Int, *>>(result).throwable)
        }

    @Test
    fun catalogFailuresAndMalformedDataDoNotBecomeEmptyGroups() =
        runTest {
            listOf(
                """{"ok":0,"data":{"groups":[]}}""",
                """{"ok":1,"data":{}}""",
                """{"ok":1,"data":{"groups":[{"name":"missing gid"}]}}""",
                """{"ok":1,"data":{"groups":[{"gid":"","name":"invalid"}]}}""",
            ).forEach { body ->
                val result = catalog(service { body })

                assertIs<PagingSource.LoadResult.Error<Int, *>>(result)
            }
            val expired = catalog(service { """{"ok":-100}""" })
            assertIs<LoginExpiredException>(assertIs<PagingSource.LoadResult.Error<Int, *>>(expired).throwable)
        }

    @Test
    fun catalogDoesNotSwallowCancellation() =
        runTest {
            assertFailsWith<CancellationException> {
                catalog(service { throw CancellationException("cancelled") })
            }
        }

    @Test
    fun groupTimelineUsesMaxIdAcrossPagesAndRefreshResetsIt() =
        runTest {
            val cursors = mutableListOf<String?>()
            val loader =
                GroupTimelineRemoteMediator(
                    service { request ->
                        assertEquals("/feed/group", request.url.encodedPath)
                        assertEquals("custom/2", request.url.parameters["gid"])
                        val cursor = request.url.parameters["max_id"]
                        cursors += cursor
                        when (cursor) {
                            null -> """{"ok":1,"data":{"statuses":[{"id":"30"}],"max_id":9999999999999999999,"next_cursor_str":"wrong"}}"""
                            "9999999999999999999" -> """{"ok":1,"data":{"statuses":[{"id":"20"}],"max_id":"older"}}"""
                            "older" -> """{"ok":1,"data":{"statuses":[{"id":"10"}],"max_id":0}}"""
                            else -> error("Unexpected cursor: $cursor")
                        }
                    },
                    accountKey,
                    "custom/2",
                )

            val refresh = loader.load(20, PagingRequest.Refresh)
            val append = loader.load(20, PagingRequest.Append(requireNotNull(refresh.nextKey)))
            val last = loader.load(20, PagingRequest.Append(requireNotNull(append.nextKey)))
            loader.load(20, PagingRequest.Refresh)

            assertEquals(listOf("30"), refresh.data.map { it.statusKey.id })
            assertEquals(listOf("20"), append.data.map { it.statusKey.id })
            assertEquals(listOf("10"), last.data.map { it.statusKey.id })
            assertNull(last.nextKey)
            assertEquals(listOf(null, "9999999999999999999", "older", null), cursors)
            assertFalse(loader.canJoinTimeline)
        }

    @Test
    fun emptyPagesMissingAndRepeatedCursorsStopPagination() =
        runTest {
            listOf(
                """{"statuses":[],"max_id":"older"}""",
                """{"statuses":[{"id":"1"}],"max_id":"older"}""",
                """{"statuses":[{"id":"1"}],"max_id":"0"}""",
                """{"statuses":[{"id":"1"}],"max_id":""}""",
                """{"statuses":[{"id":"1"}]}""",
            ).forEach { data ->
                val loader = GroupTimelineRemoteMediator(service { """{"ok":1,"data":$data}""" }, accountKey, "group")

                assertNull(loader.load(20, PagingRequest.Append("older")).nextKey)
            }
        }

    @Test
    fun cardBasedGroupResponseUsesTheExistingStatusRenderer() =
        runTest {
            val loader =
                GroupTimelineRemoteMediator(
                    service { """{"ok":1,"data":{"cards":[{"card_type":9,"mblog":{"id":"42"}}],"max_id":"next"}}""" },
                    accountKey,
                    "group",
                )

            val result = loader.load(20, PagingRequest.Refresh)

            assertEquals(listOf("42"), result.data.map { it.statusKey.id })
            assertEquals("next", result.nextKey)
        }

    @Test
    fun groupTimelineFailuresDoNotBecomeEmptyPages() =
        runTest {
            listOf(
                """{"ok":0,"data":{"statuses":[]}}""",
                """{"ok":1,"data":{}}""",
                """{"ok":1,"data":{"statuses":[{"not_a_status":true}]}}""",
            ).forEach { body ->
                val loader = GroupTimelineRemoteMediator(service { body }, accountKey, "group")

                assertFailsWith<IllegalStateException> { loader.load(20, PagingRequest.Refresh) }
            }
            val expired = GroupTimelineRemoteMediator(service { """{"ok":-100}""" }, accountKey, "group")
            assertEquals(accountKey, assertFailsWith<LoginExpiredException> { expired.load(20, PagingRequest.Refresh) }.accountKey)
            val loggedOut = GroupTimelineRemoteMediator(service(loggedIn = false) { error("Must not load") }, accountKey, "group")
            assertFailsWith<LoginExpiredException> { loggedOut.load(20, PagingRequest.Refresh) }
        }

    @Test
    fun groupTimelineDoesNotRequestPrepend() =
        runTest {
            val loader = GroupTimelineRemoteMediator(service { error("Must not request prepend") }, accountKey, "group")

            assertTrue(loader.load(20, PagingRequest.Prepend("newer")).data.isEmpty())
        }

    @Test
    fun blankPersistedGroupIdCannotFallBackToAnUnscopedFeed() {
        assertFailsWith<IllegalArgumentException> {
            GroupTimelineRemoteMediator(service { error("No request expected") }, accountKey, " ")
        }
    }

    @Test
    fun groupIdentityAndCacheAreIsolatedByAccountAndGroup() {
        val service = service { error("No network needed") }
        val keys =
            listOf(accountKey, MicroBlogKey("other", "m.weibo.cn")).flatMap { account ->
                listOf("one", "two").map { group ->
                    GroupTimelineRemoteMediator(service, account, group).pagingKey
                }
            }

        assertEquals(4, keys.toSet().size)
        assertNotEquals("home_$accountKey", keys.first())
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun groupSpecPersistsItsAccountAndResourceWithoutEnablingManagement() {
        val spec = VvoPlatformSpec.groupTimelineSpec
        val data = TimelineSpec.AccountResourceData(accountKey, "9999999999999999999")
        val restored = ProtoBuf.decodeFromHexString(spec.serializer, ProtoBuf.encodeToHexString(spec.serializer, data))
        val dataSource = VVODataSource(accountKey, flowOf(VVoCredential("")))

        assertEquals(data, restored)
        assertEquals(spec.itemId(data), spec.candidate(restored).id)
        assertTrue(VvoPlatformSpec.timelineSpecs.any { it.id == spec.id })
        assertSame(dataSource, dataSource.capabilities.tabCatalog?.pinnable)
        assertEquals(1, dataSource.pinnableTimelineTabs.size)
        assertNull(dataSource.capabilities.list)
    }

    private suspend fun catalog(service: VVOService) =
        VVOGroupPagingSource(service, accountKey).load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 20, placeholdersEnabled = false),
        )

    private fun service(
        loggedIn: Boolean = true,
        response: (HttpRequestData) -> String,
    ): VVOService =
        VVOService(
            credentialFlow = flowOf(VVoCredential(if (loggedIn) "MLOGIN=1; SUB=test" else "")),
            configLoginRetryDelay = Duration.ZERO,
            httpClientFactory = { config ->
                HttpClient(
                    MockEngine { request ->
                        val body =
                            if (request.url.encodedPath == "/api/config") {
                                """{"ok":1,"data":{"login":$loggedIn}}"""
                            } else {
                                response(request)
                            }
                        respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
                    },
                    config,
                )
            },
        )
}
