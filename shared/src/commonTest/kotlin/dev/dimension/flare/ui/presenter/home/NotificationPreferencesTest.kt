package dev.dimension.flare.ui.presenter.home

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.common.protobufSerializer
import dev.dimension.flare.createTestFileSystem
import dev.dimension.flare.createTestRootPath
import dev.dimension.flare.data.datastore.createDataStoreStorage
import dev.dimension.flare.data.datastore.model.NotificationPreferences
import dev.dimension.flare.data.io.OkioFileStorage
import dev.dimension.flare.deleteTestRootPath
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NotificationPreferencesTest : RobolectricTest() {
    private val root = createTestRootPath()
    private val fileStorage = OkioFileStorage(createTestFileSystem(), root)

    @AfterTest
    fun tearDown() {
        deleteTestRootPath(root)
    }

    @Test
    fun freshPreferencesHaveNoSelection() =
        runTest {
            val storeJob = Job(coroutineContext[Job])
            try {
                assertNull(store(CoroutineScope(coroutineContext + storeJob)).data.first().selectedAccountKey)
            } finally {
                storeJob.cancelAndJoin()
            }
        }

    @Test
    fun recreatedStoreRestoresTheSelectedKeyIncludingItsHost() =
        runTest {
            val key = MicroBlogKey("user:@,\\id", "example.social")
            val storeJob = Job(coroutineContext[Job])
            val originalStore = store(CoroutineScope(coroutineContext + storeJob))
            try {
                val selection = NotificationAccountSelection(originalStore, this)
                selection.updateAccounts(listOf(MicroBlogKey(key.id, "other.social"), key))
                selection.restore()
                selection.selectAccount(key)
                originalStore.data.first { it.selectedAccountKey == key }
            } finally {
                storeJob.cancelAndJoin()
            }

            val reopenedJob = Job(coroutineContext[Job])
            try {
                val reopened = NotificationAccountSelection(store(CoroutineScope(coroutineContext + reopenedJob)), this)
                reopened.updateAccounts(listOf(MicroBlogKey(key.id, "other.social"), key))
                reopened.restore()
                assertEquals(key, reopened.selectedAccountKey.value)
            } finally {
                reopenedJob.cancelAndJoin()
            }
        }

    private fun store(scope: CoroutineScope): DataStore<NotificationPreferences> =
        DataStoreFactory.create(
            storage =
                createDataStoreStorage(
                    name = "notification-${root.name}.pb",
                    serializer = protobufSerializer(NotificationPreferences()),
                    fileStorage = fileStorage,
                ),
            scope = scope,
        )
}
