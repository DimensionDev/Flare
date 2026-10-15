package dev.dimension.flare.data.datasource.xqt

import dev.dimension.flare.data.datasource.microblog.paging.NotificationTimelineLoader
import dev.dimension.flare.data.datasource.microblog.paging.PagingRequest
import dev.dimension.flare.data.datasource.microblog.paging.PagingResult
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.model.mapper.XqtRenderTestModule
import dev.dimension.flare.ui.render.toUi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.plugin.module.dsl.modules
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Instant

class FollowRequestsNotificationLoaderTest {
    private val accountKey = MicroBlogKey("account", "x.com")

    @BeforeTest
    fun setup() {
        startKoin { modules(XqtRenderTestModule::class) }
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun notificationsIncludeRequestsAndResumeBothCursorsAfterRecreation() =
        runTest {
            val request = item("pending")
            val notification = item("ordinary")
            val notifications = Source { PagingResult(listOf(notification), nextKey = "notifications-next") }
            val requests = Source { PagingResult(listOf(request), nextKey = "requests-next") }
            val first = FollowRequestsNotificationLoader(notifications, requests).load(20, PagingRequest.Refresh)

            assertEquals(listOf(request, notification), first.data)
            assertSame(request, first.data.first())
            assertEquals(listOf<PagingRequest>(PagingRequest.Refresh), requests.calls)

            notifications.result = { PagingResult(listOf(item("older"))) }
            requests.result = { PagingResult(nextKey = "requests-last") }
            val recreated = FollowRequestsNotificationLoader(notifications, requests)
            val second = recreated.load(20, PagingRequest.Append(first.nextKey!!))
            assertEquals(PagingRequest.Append("notifications-next"), notifications.calls.last())
            assertEquals(PagingRequest.Append("requests-next"), requests.calls.last())
            assertEquals(listOf("older"), second.data.map { it.statusKey.id })

            requests.result = { PagingResult(listOf(item("last-request"))) }
            val third = recreated.load(20, PagingRequest.Append(second.nextKey!!))
            assertEquals(2, notifications.calls.size)
            assertEquals(PagingRequest.Append("requests-last"), requests.calls.last())
            assertEquals(listOf("last-request"), third.data.map { it.statusKey.id })
            assertNull(third.nextKey)
            assertFalse(recreated.collapseReplyChains)
            assertEquals(notifications.pagingKey, recreated.pagingKey)
        }

    @Test
    fun exhaustedRequestsDoNotRestartWhileNotificationsContinueAndRefreshRemovesWithdrawnRequests() =
        runTest {
            val notifications = Source { PagingResult(listOf(item("notification")), nextKey = "older") }
            val requests = Source { PagingResult(listOf(item("pending"))) }
            val loader = FollowRequestsNotificationLoader(notifications, requests)
            val first = loader.load(20, PagingRequest.Refresh)
            val second = loader.load(20, PagingRequest.Append(first.nextKey!!))
            assertEquals(listOf("notification"), second.data.map { it.statusKey.id })
            assertEquals(1, requests.calls.size)

            requests.result = { PagingResult() }
            val refreshed = loader.load(20, PagingRequest.Refresh)
            assertEquals(listOf("notification"), refreshed.data.map { it.statusKey.id })
            assertEquals(PagingRequest.Refresh, notifications.calls.last())
            assertEquals(listOf<PagingRequest>(PagingRequest.Refresh, PagingRequest.Refresh), requests.calls)
        }

    @Test
    fun requestErrorsAreReportedWithoutHidingOrdinaryNotificationsAndRefreshRetries() =
        runTest {
            val failure = IllegalStateException("rate limited")
            val notifications = Source { PagingResult(listOf(item("notification"))) }
            val requests = Source { throw failure }
            val errors = mutableListOf<Throwable>()
            val loader = FollowRequestsNotificationLoader(notifications, requests).apply { reportError = errors::add }

            val first = loader.load(20, PagingRequest.Refresh)
            assertEquals(listOf("notification"), first.data.map { it.statusKey.id })
            assertEquals(listOf<Throwable>(failure), errors)
            assertNull(first.nextKey)
            requests.result = { PagingResult(listOf(item("pending"))) }
            assertEquals(listOf("pending", "notification"), loader.load(20, PagingRequest.Refresh).data.map { it.statusKey.id })
        }

    @Test
    fun cancellationAndUnreportedFailuresPropagate() =
        runTest {
            val notifications = Source { PagingResult(listOf(item("notification"))) }
            val requests = Source { throw CancellationException("cancelled") }
            val errors = mutableListOf<Throwable>()
            val loader = FollowRequestsNotificationLoader(notifications, requests).apply { reportError = errors::add }
            assertFailsWith<CancellationException> { loader.load(20, PagingRequest.Refresh) }
            assertTrue(errors.isEmpty())

            requests.result = { throw IllegalStateException("failed") }
            loader.reportError = null
            assertFailsWith<IllegalStateException> { loader.load(20, PagingRequest.Refresh) }

            notifications.result = { throw IllegalStateException("notification failed") }
            loader.reportError = errors::add
            assertFailsWith<IllegalStateException> { loader.load(20, PagingRequest.Refresh) }
        }

    @Test
    fun legacyCursorsContinueOrdinaryNotificationsAndPrependDoesNotLoad() =
        runTest {
            val notifications = Source { PagingResult(listOf(item("notification"))) }
            val requests = Source { error("Not expected before refresh") }
            val loader = FollowRequestsNotificationLoader(notifications, requests)
            assertEquals(1, loader.load(20, PagingRequest.Append("legacy-cursor")).data.size)
            assertEquals(listOf<PagingRequest>(PagingRequest.Append("legacy-cursor")), notifications.calls)
            assertTrue(requests.calls.isEmpty())
            assertTrue(loader.load(20, PagingRequest.Prepend("previous")).data.isEmpty())
            assertEquals(1, notifications.calls.size)
            assertTrue(requests.calls.isEmpty())
        }

    private class Source(
        var result: () -> PagingResult<UiTimelineV2>,
    ) : NotificationTimelineLoader {
        override val pagingKey = "notification_account@x.com"
        val calls = mutableListOf<PagingRequest>()

        override suspend fun load(
            pageSize: Int,
            request: PagingRequest,
        ): PagingResult<UiTimelineV2> {
            assertEquals(20, pageSize)
            calls += request
            return result()
        }
    }

    private fun item(id: String) =
        UiTimelineV2.Message(
            user = null,
            statusKey = MicroBlogKey(id, accountKey.host),
            icon = UiIcon.Follow,
            type = UiTimelineV2.Message.Type.Raw(id),
            createdAt = Instant.DISTANT_PAST.toUi(),
            clickEvent = ClickEvent.Noop,
            accountType = AccountType.Specific(accountKey),
        )
}
