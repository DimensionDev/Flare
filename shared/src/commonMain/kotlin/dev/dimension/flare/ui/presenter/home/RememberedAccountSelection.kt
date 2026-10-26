package dev.dimension.flare.ui.presenter.home

import androidx.datastore.core.DataStore
import dev.dimension.flare.data.datastore.model.AccountSelectionPreferences
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.toAccountType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal class RememberedAccountSelection(
    private val preferences: DataStore<AccountSelectionPreferences>,
    private val appScope: CoroutineScope,
) {
    // null means restoration is pending; Guest means the account list is ready and empty.
    private val selected = MutableStateFlow<AccountType?>(null)
    val selectedAccountType = selected.asStateFlow()
    val selectedAccountKey = selected.map { (it as? AccountType.Specific)?.accountKey }.distinctUntilChanged()
    private var accounts: List<MicroBlogKey>? = null
    private var restored = false
    private var initialAccountKey: MicroBlogKey? = null

    suspend fun restore() {
        if (restored) return
        val saved =
            try {
                // Wait behind saves from a closing page without changing the preference.
                preferences.updateData { it }.selectedAccountKey
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                error.printStackTrace()
                null
            }
        if (!restored) {
            initialAccountKey = saved
            restored = true
        }
        resolveAccount()
    }

    fun updateAccounts(keys: List<MicroBlogKey>) {
        accounts = keys
        resolveAccount()
    }

    fun selectAccount(key: MicroBlogKey) {
        if (accounts?.contains(key) != true) return
        restored = true
        initialAccountKey = null
        selected.value = AccountType.Specific(key)
        persist { it.copy(selectedAccountKey = key) }
    }

    private fun resolveAccount() {
        val keys = accounts ?: return
        if (!restored) return
        val previous = (selected.value as? AccountType.Specific)?.accountKey ?: initialAccountKey
        initialAccountKey = null
        selected.value = (previous?.takeIf { it in keys } ?: keys.firstOrNull()).toAccountType()
        if (previous != null && previous !in keys) {
            persist {
                if (it.selectedAccountKey == previous) it.copy(selectedAccountKey = null) else it
            }
        }
    }

    private fun persist(transform: (AccountSelectionPreferences) -> AccountSelectionPreferences) {
        // Enqueue in click order, with a lifetime independent of the page.
        appScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                preferences.updateData { transform(it) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                error.printStackTrace()
            }
        }
    }
}
