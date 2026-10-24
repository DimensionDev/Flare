package dev.dimension.flare.data.network.proxy

import dev.dimension.flare.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single

/**
 * Keeps [ProxySettingsStateHolder] in sync with the persisted proxy setting.
 *
 * Created at start-up like the other coordinators so that the current value is available to the
 * first network connect.
 */
@Single(createdAtStart = true)
internal class ProxySettingsCoordinator(
    private val settingsRepository: SettingsRepository,
    private val coroutineScope: CoroutineScope,
) {
    init {
        coroutineScope.launch {
            settingsRepository.appSettings.collect { settings ->
                ProxySettingsStateHolder.update(settings.proxy)
            }
        }
    }
}
