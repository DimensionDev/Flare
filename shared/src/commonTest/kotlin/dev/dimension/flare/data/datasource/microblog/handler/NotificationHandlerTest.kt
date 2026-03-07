package dev.dimension.flare.data.datasource.microblog.handler

import androidx.paging.LoadState
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.common.CacheState
import dev.dimension.flare.data.datasource.microblog.loader.NotificationLoader
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationHandlerTest : RobolectricTest() {
    @Test
    fun refreshLoadsBadgeCountIntoCache() =
        runTest {
            val loader = FakeNotificationLoader(nextCount = 7)
            val handler =
                NotificationHandler(
                    accountKey = MicroBlogKey("user-1", "test.social"),
                    loader = loader,
                )

            val valueDeferred =
                async {
                    handler.notificationBadgeCount.data
                        .filterIsInstance<CacheState.Success<Int>>()
                        .first()
                        .data
                }

            val refreshState =
                handler.notificationBadgeCount.refreshState
                    .drop(1)
                    .first()

            assertTrue(refreshState is LoadState.NotLoading)
            assertEquals(1, loader.callCount)
            assertEquals(7, valueDeferred.await())
        }

    @Test
    fun clearSetsBadgeToZero() =
        runTest {
            val loader = FakeNotificationLoader(nextCount = 3)
            val handler =
                NotificationHandler(
                    accountKey = MicroBlogKey("user-2", "test.social"),
                    loader = loader,
                )

            val valueDeferred =
                async {
                    handler.notificationBadgeCount.data
                        .filterIsInstance<CacheState.Success<Int>>()
                        .first()
                        .data
                }

            handler.notificationBadgeCount.refreshState
                .drop(1)
                .first()
            assertEquals(3, valueDeferred.await())
            handler.clear()

            val latest =
                handler.notificationBadgeCount.data
                    .filterIsInstance<CacheState.Success<Int>>()
                    .first()
                    .data
            assertEquals(0, latest)
        }

    @Test
    fun refreshFailureKeepsPreviousValue() =
        runTest {
            val loader = FakeNotificationLoader(nextCount = 5)
            val handler =
                NotificationHandler(
                    accountKey = MicroBlogKey("user-3", "test.social"),
                    loader = loader,
                )

            // Warm up cache with a successful refresh.
            val initialValueDeferred =
                async {
                    handler.notificationBadgeCount.data
                        .filterIsInstance<CacheState.Success<Int>>()
                        .first()
                        .data
                }
            handler.notificationBadgeCount.refreshState
                .drop(1)
                .first()
            assertEquals(5, initialValueDeferred.await())

            loader.shouldFail = true
            val failedState =
                handler.notificationBadgeCount.refreshState
                    .drop(1)
                    .first()
            assertTrue(failedState is LoadState.Error)

            val latest =
                handler.notificationBadgeCount.data
                    .filterIsInstance<CacheState.Success<Int>>()
                    .first()
                    .data
            assertEquals(5, latest)
        }

    private class FakeNotificationLoader(
        var nextCount: Int = 0,
    ) : NotificationLoader {
        var shouldFail: Boolean = false
        var callCount: Int = 0

        override suspend fun notificationBadgeCount(): Int {
            callCount++
            if (shouldFail) {
                error("loader failed")
            }
            return nextCount
        }
    }
}
