package dev.dimension.flare.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import dev.dimension.flare.common.protobufSerializer
import dev.dimension.flare.data.datastore.createDataStoreStorage
import dev.dimension.flare.data.io.FileStorage
import dev.dimension.flare.data.model.ProxyPolicy
import dev.dimension.flare.data.model.ProxyProfile
import dev.dimension.flare.data.model.ProxySettings
import dev.dimension.flare.data.network.ProxyNetwork
import dev.dimension.flare.model.MicroBlogKey
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.native.HiddenFromObjC

@HiddenFromObjC
public class ProxyRepository(
    private val store: DataStore<ProxySettings>,
) {
    // Device-local storage is intentionally separate from exported AppSettings and account databases.
    internal constructor(fileStorage: FileStorage) : this(
        DataStoreFactory.create(
            storage = createDataStoreStorage("proxy_settings.pb", protobufSerializer(ProxySettings()), fileStorage),
        ),
    )

    private val mutex = Mutex()
    public val settings: Flow<ProxySettings> = store.data

    internal suspend fun <T> withSettings(block: suspend (ProxySettings) -> T): T = mutex.withLock { block(store.data.first()) }

    private suspend fun update(block: (ProxySettings) -> ProxySettings) {
        withContext(NonCancellable) {
            mutex.withLock {
                val updated = store.updateData(block)
                ProxyNetwork.settingsChanged(updated)
            }
        }
    }

    public suspend fun save(profile: ProxyProfile): Unit = update { it.save(profile) }

    public suspend fun remove(id: String): Unit = update { it.remove(id) }

    public suspend fun selectGlobal(policy: ProxyPolicy): Unit =
        update {
            it.validatePolicy(policy, account = false)
            it.copy(global = policy)
        }

    public suspend fun selectAccount(
        accountKey: MicroBlogKey,
        policy: ProxyPolicy,
    ): Unit =
        update {
            require(accountKey.host != "nostr") { "Nostr does not support app proxies" }
            it.validatePolicy(policy, account = true)
            it.copy(accounts = it.accounts + (accountKey.toString() to policy))
        }

    internal suspend fun removeAccount(accountKey: MicroBlogKey): Unit =
        update {
            it.copy(accounts = it.accounts - accountKey.toString(), removedAccounts = it.removedAccounts + accountKey.toString())
        }

    internal suspend fun restoreAccount(accountKey: MicroBlogKey): Unit =
        update {
            it.copy(removedAccounts = it.removedAccounts - accountKey.toString())
        }
}
