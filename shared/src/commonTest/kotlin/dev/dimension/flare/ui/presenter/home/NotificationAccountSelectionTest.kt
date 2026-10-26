package dev.dimension.flare.ui.presenter.home

import androidx.datastore.core.DataStore
import dev.dimension.flare.data.datastore.model.NotificationPreferences
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationAccountSelectionTest {
    private val firstAccount = MicroBlogKey("alice", "example.social")
    private val lastAccount = MicroBlogKey("bob", "example.social")

    @Test
    fun recreatedSelectionRestoresLastManuallySelectedAccount() =
        runTest {
            val preferences = MemoryNotificationPreferences()
            val original = NotificationAccountSelection(preferences, this)
            original.updateAccounts(listOf(firstAccount, lastAccount))
            original.restore()
            original.selectAccount(lastAccount)
            assertEquals(lastAccount, original.selectedAccountKey.value)

            val reopened = NotificationAccountSelection(preferences, this)
            reopened.updateAccounts(listOf(firstAccount, lastAccount))
            reopened.restore()

            assertEquals(lastAccount, reopened.selectedAccountKey.value)
        }

    @Test
    fun restorationWaitsForPreferencesInsteadOfSelectingTheFirstAccount() =
        runTest {
            val readGate = CompletableDeferred<Unit>()
            val preferences = MemoryNotificationPreferences(lastAccount, beforeRead = { readGate.await() })
            val selection = NotificationAccountSelection(preferences, this)
            selection.updateAccounts(listOf(firstAccount, lastAccount))
            val restore = launch(start = CoroutineStart.UNDISPATCHED) { selection.restore() }
            assertNull(selection.selectedAccountKey.value)

            readGate.complete(Unit)
            restore.join()
            assertEquals(lastAccount, selection.selectedAccountKey.value)
        }

    @Test
    fun restorationWaitsForTheLocalAccountList() =
        runTest {
            val preferences = MemoryNotificationPreferences(lastAccount)
            val selection = NotificationAccountSelection(preferences, this)
            selection.restore()
            assertNull(selection.selectedAccountKey.value)
            assertEquals(lastAccount, preferences.snapshot.value.selectedAccountKey)

            selection.updateAccounts(listOf(firstAccount, lastAccount))
            assertEquals(lastAccount, selection.selectedAccountKey.value)
        }

    @Test
    fun manualSelectionWinsOverALateRestoration() =
        runTest {
            val readGate = CompletableDeferred<Unit>()
            val preferences = MemoryNotificationPreferences(lastAccount, beforeRead = { readGate.await() })
            val selection = NotificationAccountSelection(preferences, this)
            selection.updateAccounts(listOf(firstAccount, lastAccount))
            val restore = launch(start = CoroutineStart.UNDISPATCHED) { selection.restore() }
            selection.selectAccount(firstAccount)

            readGate.complete(Unit)
            restore.join()
            advanceUntilIdle()
            assertEquals(firstAccount, selection.selectedAccountKey.value)
            assertEquals(firstAccount, preferences.snapshot.value.selectedAccountKey)
        }

    @Test
    fun accountReorderingDoesNotChangeTheSelectionOrSaveAnAutomaticDefault() =
        runTest {
            val preferences = MemoryNotificationPreferences()
            val selection = NotificationAccountSelection(preferences, this)
            selection.updateAccounts(listOf(firstAccount, lastAccount))
            selection.restore()
            assertEquals(firstAccount, selection.selectedAccountKey.value)

            selection.updateAccounts(listOf(lastAccount, firstAccount))
            assertEquals(firstAccount, selection.selectedAccountKey.value)
            assertNull(preferences.snapshot.value.selectedAccountKey)
        }

    @Test
    fun removingSelectedAccountClearsItsPreferenceAndDoesNotRestoreItOnReadd() =
        runTest {
            val preferences = MemoryNotificationPreferences(lastAccount)
            val selection = NotificationAccountSelection(preferences, this)
            selection.updateAccounts(listOf(firstAccount, lastAccount))
            selection.restore()

            selection.updateAccounts(listOf(firstAccount))
            assertEquals(firstAccount, selection.selectedAccountKey.value)
            assertNull(preferences.snapshot.value.selectedAccountKey)

            selection.updateAccounts(listOf(firstAccount, lastAccount))
            assertEquals(firstAccount, selection.selectedAccountKey.value)
        }

    @Test
    fun missingSavedAccountFallsBackInAccountManagementOrder() =
        runTest {
            val preferences = MemoryNotificationPreferences(MicroBlogKey("deleted", "example.social"))
            val selection = NotificationAccountSelection(preferences, this)
            selection.updateAccounts(listOf(lastAccount, firstAccount))
            selection.restore()
            assertEquals(lastAccount, selection.selectedAccountKey.value)
            assertNull(preferences.snapshot.value.selectedAccountKey)
        }

    @Test
    fun removingAllAccountsClearsSelectionAndAddingOneSelectsIt() =
        runTest {
            val preferences = MemoryNotificationPreferences(lastAccount)
            val selection = NotificationAccountSelection(preferences, this)
            selection.updateAccounts(listOf(lastAccount))
            selection.restore()
            selection.updateAccounts(emptyList())
            assertNull(selection.selectedAccountKey.value)
            assertNull(preferences.snapshot.value.selectedAccountKey)

            selection.updateAccounts(listOf(firstAccount))
            assertEquals(firstAccount, selection.selectedAccountKey.value)
        }

    @Test
    fun unknownAccountCannotBeSelectedAndKeysIncludeTheHost() =
        runTest {
            val otherHost = firstAccount.copy(host = "other.social")
            val preferences = MemoryNotificationPreferences(otherHost)
            val selection = NotificationAccountSelection(preferences, this)
            selection.updateAccounts(listOf(firstAccount, otherHost))
            selection.restore()
            selection.selectAccount(lastAccount)
            assertEquals(otherHost, selection.selectedAccountKey.value)
            assertEquals(otherHost, preferences.snapshot.value.selectedAccountKey)
        }

    @Test
    fun anotherWindowDoesNotChangeAnAlreadyOpenSelection() =
        runTest {
            val preferences = MemoryNotificationPreferences(firstAccount)
            val firstWindow = NotificationAccountSelection(preferences, this)
            firstWindow.updateAccounts(listOf(firstAccount, lastAccount))
            firstWindow.restore()
            val secondWindow = NotificationAccountSelection(preferences, this)
            secondWindow.updateAccounts(listOf(firstAccount, lastAccount))
            secondWindow.restore()
            secondWindow.selectAccount(lastAccount)

            firstWindow.restore()
            assertEquals(firstAccount, firstWindow.selectedAccountKey.value)
            assertEquals(lastAccount, secondWindow.selectedAccountKey.value)
            assertEquals(lastAccount, preferences.snapshot.value.selectedAccountKey)

            firstWindow.updateAccounts(listOf(lastAccount))
            assertEquals(lastAccount, preferences.snapshot.value.selectedAccountKey)
        }

    @Test
    fun rapidSelectionsAreWrittenInOrderEvenAfterThePageCloses() =
        runTest {
            val writeGate = CompletableDeferred<Unit>()
            val preferences = MemoryNotificationPreferences(beforeWrite = { writeGate.await() })
            val selection = NotificationAccountSelection(preferences, this)
            selection.updateAccounts(listOf(firstAccount, lastAccount))
            selection.restore()
            val page =
                launch {
                    selection.selectAccount(lastAccount)
                    selection.selectAccount(firstAccount)
                    selection.selectAccount(lastAccount)
                }
            page.join()
            page.cancelAndJoin()
            assertEquals(lastAccount, selection.selectedAccountKey.value)
            assertNull(preferences.snapshot.value.selectedAccountKey)

            writeGate.complete(Unit)
            advanceUntilIdle()
            assertEquals(listOf<MicroBlogKey?>(lastAccount, firstAccount, lastAccount), preferences.writes)
            assertEquals(lastAccount, preferences.snapshot.value.selectedAccountKey)
        }

    @Test
    fun reopeningWaitsForThePreviousPagesPendingSave() =
        runTest {
            val writeGate = CompletableDeferred<Unit>()
            val preferences = MemoryNotificationPreferences(firstAccount, beforeWrite = { writeGate.await() })
            val original = NotificationAccountSelection(preferences, this)
            original.updateAccounts(listOf(firstAccount, lastAccount))
            original.restore()
            original.selectAccount(lastAccount)

            val reopened = NotificationAccountSelection(preferences, this)
            reopened.updateAccounts(listOf(firstAccount, lastAccount))
            val restore = launch(start = CoroutineStart.UNDISPATCHED) { reopened.restore() }
            try {
                assertNull(reopened.selectedAccountKey.value)
            } finally {
                writeGate.complete(Unit)
            }
            restore.join()
            assertEquals(lastAccount, reopened.selectedAccountKey.value)
        }

    @Test
    fun queuedDeletionCannotBeUndoneByAnEarlierSelectionWrite() =
        runTest {
            val writeGate = CompletableDeferred<Unit>()
            val preferences = MemoryNotificationPreferences(beforeWrite = { writeGate.await() })
            val selection = NotificationAccountSelection(preferences, this)
            selection.updateAccounts(listOf(firstAccount, lastAccount))
            selection.restore()
            selection.selectAccount(lastAccount)
            selection.updateAccounts(listOf(firstAccount))

            writeGate.complete(Unit)
            advanceUntilIdle()
            assertEquals(firstAccount, selection.selectedAccountKey.value)
            assertNull(preferences.snapshot.value.selectedAccountKey)
        }

    @Test
    fun unreadablePreferencesUseTheDefaultWithoutBlockingManualSelection() =
        runTest {
            val preferences = MemoryNotificationPreferences(beforeRead = { error("read failed") })
            val selection = NotificationAccountSelection(preferences, this)
            selection.updateAccounts(listOf(firstAccount, lastAccount))
            selection.restore()
            assertEquals(firstAccount, selection.selectedAccountKey.value)
            selection.selectAccount(lastAccount)
            assertEquals(lastAccount, selection.selectedAccountKey.value)
        }

    @Test
    fun failedWriteKeepsTheCurrentSelectionUsable() =
        runTest {
            val preferences = MemoryNotificationPreferences(beforeWrite = { error("write failed") })
            val selection = NotificationAccountSelection(preferences, this)
            selection.updateAccounts(listOf(firstAccount, lastAccount))
            selection.restore()
            selection.selectAccount(lastAccount)
            assertEquals(lastAccount, selection.selectedAccountKey.value)
            assertNull(preferences.snapshot.value.selectedAccountKey)
        }
}

private class MemoryNotificationPreferences(
    initialAccount: MicroBlogKey? = null,
    private val beforeRead: suspend () -> Unit = {},
    private val beforeWrite: suspend () -> Unit = {},
) : DataStore<NotificationPreferences> {
    val snapshot = MutableStateFlow(NotificationPreferences(initialAccount))
    val writes = mutableListOf<MicroBlogKey?>()
    private val mutex = Mutex()
    private var initialized = false
    override val data = snapshot

    override suspend fun updateData(transform: suspend (t: NotificationPreferences) -> NotificationPreferences): NotificationPreferences =
        mutex.withLock {
            if (!initialized) {
                beforeRead()
                initialized = true
            }
            val updated = transform(snapshot.value)
            if (updated != snapshot.value) {
                beforeWrite()
                snapshot.value = updated
                writes.add(updated.selectedAccountKey)
            }
            snapshot.value
        }
}
