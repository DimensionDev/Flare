package dev.dimension.flare.ui.presenter.home

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import dev.dimension.flare.RobolectricTest
import dev.dimension.flare.common.protobufSerializer
import dev.dimension.flare.createTestFileSystem
import dev.dimension.flare.createTestRootPath
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.datastore.createDataStoreStorage
import dev.dimension.flare.data.datastore.model.AccountSelectionPreferences
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

class AccountSelectionPreferencesTest : RobolectricTest() {
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
    fun discoverAndNotificationsKeepIndependentSelections() =
        runTest {
            val dataStore = AppDataStore(fileStorage)
            val notificationKey = MicroBlogKey("alice", "example.social")
            val discoverKey = MicroBlogKey("bob", "example.social")
            val notification = RememberedAccountSelection(dataStore.notificationPreferences, this)
            val discover = RememberedAccountSelection(dataStore.discoverPreferences, this)
            listOf(notification, discover).forEach {
                it.updateAccounts(listOf(notificationKey, discoverKey))
                it.restore()
            }
            notification.selectAccount(notificationKey)
            discover.selectAccount(discoverKey)

            assertEquals(
                notificationKey,
                dataStore.notificationPreferences.data
                    .first { it.selectedAccountKey != null }
                    .selectedAccountKey,
            )
            assertEquals(
                discoverKey,
                dataStore.discoverPreferences.data
                    .first { it.selectedAccountKey != null }
                    .selectedAccountKey,
            )
            val reopened = RememberedAccountSelection(dataStore.discoverPreferences, this)
            reopened.updateAccounts(listOf(notificationKey, discoverKey))
            reopened.restore()
            assertEquals(discoverKey, reopened.selectedAccountKey.first())
            assertEquals(notificationKey, notification.selectedAccountKey.first())
        }

    @Test
    fun recreatedStoreRestoresTheSelectedKeyIncludingItsHost() =
        runTest {
            val key = MicroBlogKey("user:@,\\id", "example.social")
            val storeJob = Job(coroutineContext[Job])
            val originalStore = store(CoroutineScope(coroutineContext + storeJob))
            try {
                val selection = RememberedAccountSelection(originalStore, this)
                selection.updateAccounts(listOf(MicroBlogKey(key.id, "other.social"), key))
                selection.restore()
                selection.selectAccount(key)
                originalStore.data.first { it.selectedAccountKey == key }
            } finally {
                storeJob.cancelAndJoin()
            }

            val reopenedJob = Job(coroutineContext[Job])
            try {
                val reopened = RememberedAccountSelection(store(CoroutineScope(coroutineContext + reopenedJob)), this)
                reopened.updateAccounts(listOf(MicroBlogKey(key.id, "other.social"), key))
                reopened.restore()
                assertEquals(key, reopened.selectedAccountKey.first())
            } finally {
                reopenedJob.cancelAndJoin()
            }
        }

    private fun store(scope: CoroutineScope): DataStore<AccountSelectionPreferences> =
        DataStoreFactory.create(
            storage =
                createDataStoreStorage(
                    name = "notification-${root.name}.pb",
                    serializer = protobufSerializer(AccountSelectionPreferences()),
                    fileStorage = fileStorage,
                ),
            scope = scope,
        )
}
