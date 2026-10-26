package dev.dimension.flare.ui.presenter.home

import androidx.datastore.core.DataStore
import dev.dimension.flare.data.datastore.model.NotificationPreferences
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class NotificationAccountSelection(
    private val preferences: DataStore<NotificationPreferences>,
    private val appScope: CoroutineScope,
) {
    private val selected = MutableStateFlow<MicroBlogKey?>(null)
    val selectedAccountKey = selected.asStateFlow()
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
        selected.value = key
        persist { it.copy(selectedAccountKey = key) }
    }

    private fun resolveAccount() {
        val keys = accounts ?: return
        if (!restored) return
        val previous = selected.value ?: initialAccountKey
        initialAccountKey = null
        selected.value = previous?.takeIf { it in keys } ?: keys.firstOrNull()
        if (previous != null && previous !in keys) {
            persist {
                if (it.selectedAccountKey == previous) it.copy(selectedAccountKey = null) else it
            }
        }
    }

    private fun persist(transform: (NotificationPreferences) -> NotificationPreferences) {
        // Enqueue in click order, with a lifetime independent of the notification page.
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
