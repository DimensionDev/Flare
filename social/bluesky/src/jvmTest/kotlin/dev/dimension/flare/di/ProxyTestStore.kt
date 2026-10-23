package dev.dimension.flare.di

import androidx.datastore.core.DataStore
import dev.dimension.flare.data.model.ProxySettings
import dev.dimension.flare.data.repository.ProxyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

internal fun startProxyTestStore(): AutoCloseable {
    val store =
        object : DataStore<ProxySettings> {
            private val state = MutableStateFlow(ProxySettings())
            private val mutex = Mutex()
            override val data: Flow<ProxySettings> = state

            override suspend fun updateData(transform: suspend (ProxySettings) -> ProxySettings): ProxySettings =
                mutex.withLock { transform(state.value).also { state.value = it } }
        }
    val repository = ProxyRepository(store)
    startKoin { modules(module { single { repository } }) }
    return AutoCloseable { stopKoin() }
}
