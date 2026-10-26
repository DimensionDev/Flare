package dev.dimension.flare.ui.presenter.home

import androidx.datastore.core.DataStore
import dev.dimension.flare.data.datastore.model.AccountSelectionPreferences
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class RememberedAccountSelectionTest {
    private val firstAccount = MicroBlogKey("alice", "example.social")
    private val lastAccount = MicroBlogKey("bob", "example.social")

    @Test
    fun guestIsOnlyResolvedAfterBothPreferencesAndAccountsAreReady() =
        runTest {
            val selection = RememberedAccountSelection(MemoryAccountSelectionPreferences(), this)
            assertNull(selection.selectedAccountType.value)
            selection.restore()
            assertNull(selection.selectedAccountType.value)
            selection.updateAccounts(emptyList())
            assertEquals(AccountType.Guest, selection.selectedAccountType.value)
            selection.updateAccounts(listOf(firstAccount))
            assertEquals(AccountType.Specific(firstAccount), selection.selectedAccountType.value)
        }

    @Test
    fun recreatedSelectionRestoresLastManuallySelectedAccount() =
        runTest {
            val preferences = MemoryAccountSelectionPreferences()
            val original = RememberedAccountSelection(preferences, this)
            original.updateAccounts(listOf(firstAccount, lastAccount))
            original.restore()
            original.selectAccount(lastAccount)
            assertEquals(lastAccount, original.selectedAccountKey.first())

            val reopened = RememberedAccountSelection(preferences, this)
            reopened.updateAccounts(listOf(firstAccount, lastAccount))
            reopened.restore()

            assertEquals(lastAccount, reopened.selectedAccountKey.first())
        }

    @Test
    fun restorationWaitsForPreferencesInsteadOfSelectingTheFirstAccount() =
        runTest {
            val readGate = CompletableDeferred<Unit>()
            val preferences = MemoryAccountSelectionPreferences(lastAccount, beforeRead = { readGate.await() })
            val selection = RememberedAccountSelection(preferences, this)
            selection.updateAccounts(listOf(firstAccount, lastAccount))
            val restore = launch(start = CoroutineStart.UNDISPATCHED) { selection.restore() }
            assertNull(selection.selectedAccountKey.first())

            readGate.complete(Unit)
            restore.join()
            assertEquals(lastAccount, selection.selectedAccountKey.first())
        }

    @Test
    fun restorationWaitsForTheLocalAccountList() =
        runTest {
            val preferences = MemoryAccountSelectionPreferences(lastAccount)
            val selection = RememberedAccountSelection(preferences, this)
            selection.restore()
            assertNull(selection.selectedAccountKey.first())
            assertEquals(lastAccount, preferences.snapshot.value.selectedAccountKey)

            selection.updateAccounts(listOf(firstAccount, lastAccount))
            assertEquals(lastAccount, selection.selectedAccountKey.first())
        }

    @Test
    fun manualSelectionWinsOverALateRestoration() =
        runTest {
            val readGate = CompletableDeferred<Unit>()
            val preferences = MemoryAccountSelectionPreferences(lastAccount, beforeRead = { readGate.await() })
            val selection = RememberedAccountSelection(preferences, this)
            selection.updateAccounts(listOf(firstAccount, lastAccount))
            val restore = launch(start = CoroutineStart.UNDISPATCHED) { selection.restore() }
            selection.selectAccount(firstAccount)

            readGate.complete(Unit)
            restore.join()
            advanceUntilIdle()
            assertEquals(firstAccount, selection.selectedAccountKey.first())
            assertEquals(firstAccount, preferences.snapshot.value.selectedAccountKey)
        }

    @Test
    fun accountReorderingDoesNotChangeTheSelectionOrSaveAnAutomaticDefault() =
        runTest {
            val preferences = MemoryAccountSelectionPreferences()
            val selection = RememberedAccountSelection(preferences, this)
            selection.updateAccounts(listOf(firstAccount, lastAccount))
            selection.restore()
            assertEquals(firstAccount, selection.selectedAccountKey.first())

            selection.updateAccounts(listOf(lastAccount, firstAccount))
            assertEquals(firstAccount, selection.selectedAccountKey.first())
            assertNull(preferences.snapshot.value.selectedAccountKey)
        }

    @Test
    fun removingSelectedAccountClearsItsPreferenceAndDoesNotRestoreItOnReadd() =
        runTest {
            val preferences = MemoryAccountSelectionPreferences(lastAccount)
            val selection = RememberedAccountSelection(preferences, this)
            selection.updateAccounts(listOf(firstAccount, lastAccount))
            selection.restore()

            selection.updateAccounts(listOf(firstAccount))
            assertEquals(firstAccount, selection.selectedAccountKey.first())
            assertNull(preferences.snapshot.value.selectedAccountKey)

            selection.updateAccounts(listOf(firstAccount, lastAccount))
            assertEquals(firstAccount, selection.selectedAccountKey.first())
        }

    @Test
    fun missingSavedAccountFallsBackInAccountManagementOrder() =
        runTest {
            val preferences = MemoryAccountSelectionPreferences(MicroBlogKey("deleted", "example.social"))
            val selection = RememberedAccountSelection(preferences, this)
            selection.updateAccounts(listOf(lastAccount, firstAccount))
            selection.restore()
            assertEquals(lastAccount, selection.selectedAccountKey.first())
            assertNull(preferences.snapshot.value.selectedAccountKey)
        }

    @Test
    fun removingAllAccountsClearsSelectionAndAddingOneSelectsIt() =
        runTest {
            val preferences = MemoryAccountSelectionPreferences(lastAccount)
            val selection = RememberedAccountSelection(preferences, this)
            selection.updateAccounts(listOf(lastAccount))
            selection.restore()
            selection.updateAccounts(emptyList())
            assertNull(selection.selectedAccountKey.first())
            assertNull(preferences.snapshot.value.selectedAccountKey)

            selection.updateAccounts(listOf(firstAccount))
            assertEquals(firstAccount, selection.selectedAccountKey.first())
        }

    @Test
    fun unknownAccountCannotBeSelectedAndKeysIncludeTheHost() =
        runTest {
            val otherHost = firstAccount.copy(host = "other.social")
            val preferences = MemoryAccountSelectionPreferences(otherHost)
            val selection = RememberedAccountSelection(preferences, this)
            selection.updateAccounts(listOf(firstAccount, otherHost))
            selection.restore()
            selection.selectAccount(lastAccount)
            assertEquals(otherHost, selection.selectedAccountKey.first())
            assertEquals(otherHost, preferences.snapshot.value.selectedAccountKey)
        }

    @Test
    fun anotherWindowDoesNotChangeAnAlreadyOpenSelection() =
        runTest {
            val preferences = MemoryAccountSelectionPreferences(firstAccount)
            val firstWindow = RememberedAccountSelection(preferences, this)
            firstWindow.updateAccounts(listOf(firstAccount, lastAccount))
            firstWindow.restore()
            val secondWindow = RememberedAccountSelection(preferences, this)
            secondWindow.updateAccounts(listOf(firstAccount, lastAccount))
            secondWindow.restore()
            secondWindow.selectAccount(lastAccount)

            firstWindow.restore()
            assertEquals(firstAccount, firstWindow.selectedAccountKey.first())
            assertEquals(lastAccount, secondWindow.selectedAccountKey.first())
            assertEquals(lastAccount, preferences.snapshot.value.selectedAccountKey)

            firstWindow.updateAccounts(listOf(lastAccount))
            assertEquals(lastAccount, preferences.snapshot.value.selectedAccountKey)
        }

    @Test
    fun rapidSelectionsAreWrittenInOrderEvenAfterThePageCloses() =
        runTest {
            val writeGate = CompletableDeferred<Unit>()
            val preferences = MemoryAccountSelectionPreferences(beforeWrite = { writeGate.await() })
            val selection = RememberedAccountSelection(preferences, this)
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
            assertEquals(lastAccount, selection.selectedAccountKey.first())
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
            val preferences = MemoryAccountSelectionPreferences(firstAccount, beforeWrite = { writeGate.await() })
            val original = RememberedAccountSelection(preferences, this)
            original.updateAccounts(listOf(firstAccount, lastAccount))
            original.restore()
            original.selectAccount(lastAccount)

            val reopened = RememberedAccountSelection(preferences, this)
            reopened.updateAccounts(listOf(firstAccount, lastAccount))
            val restore = launch(start = CoroutineStart.UNDISPATCHED) { reopened.restore() }
            try {
                assertNull(reopened.selectedAccountKey.first())
            } finally {
                writeGate.complete(Unit)
            }
            restore.join()
            assertEquals(lastAccount, reopened.selectedAccountKey.first())
        }

    @Test
    fun queuedDeletionCannotBeUndoneByAnEarlierSelectionWrite() =
        runTest {
            val writeGate = CompletableDeferred<Unit>()
            val preferences = MemoryAccountSelectionPreferences(beforeWrite = { writeGate.await() })
            val selection = RememberedAccountSelection(preferences, this)
            selection.updateAccounts(listOf(firstAccount, lastAccount))
            selection.restore()
            selection.selectAccount(lastAccount)
            selection.updateAccounts(listOf(firstAccount))

            writeGate.complete(Unit)
            advanceUntilIdle()
            assertEquals(firstAccount, selection.selectedAccountKey.first())
            assertNull(preferences.snapshot.value.selectedAccountKey)
        }

    @Test
    fun unreadablePreferencesUseTheDefaultWithoutBlockingManualSelection() =
        runTest {
            val preferences = MemoryAccountSelectionPreferences(beforeRead = { error("read failed") })
            val selection = RememberedAccountSelection(preferences, this)
            selection.updateAccounts(listOf(firstAccount, lastAccount))
            selection.restore()
            assertEquals(firstAccount, selection.selectedAccountKey.first())
            selection.selectAccount(lastAccount)
            assertEquals(lastAccount, selection.selectedAccountKey.first())
        }

    @Test
    fun failedWriteKeepsTheCurrentSelectionUsable() =
        runTest {
            val preferences = MemoryAccountSelectionPreferences(beforeWrite = { error("write failed") })
            val selection = RememberedAccountSelection(preferences, this)
            selection.updateAccounts(listOf(firstAccount, lastAccount))
            selection.restore()
            selection.selectAccount(lastAccount)
            assertEquals(lastAccount, selection.selectedAccountKey.first())
            assertNull(preferences.snapshot.value.selectedAccountKey)
        }
}

private class MemoryAccountSelectionPreferences(
    initialAccount: MicroBlogKey? = null,
    private val beforeRead: suspend () -> Unit = {},
    private val beforeWrite: suspend () -> Unit = {},
) : DataStore<AccountSelectionPreferences> {
    val snapshot = MutableStateFlow(AccountSelectionPreferences(initialAccount))
    val writes = mutableListOf<MicroBlogKey?>()
    private val mutex = Mutex()
    private var initialized = false
    override val data = snapshot

    override suspend fun updateData(
        transform: suspend (t: AccountSelectionPreferences) -> AccountSelectionPreferences,
    ): AccountSelectionPreferences =
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
