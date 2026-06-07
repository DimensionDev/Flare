package dev.dimension.flare.feature.agent.runtime

import ai.koog.http.client.KoogHttpClient
import ai.koog.http.client.ktor.KtorKoogHttpClient
import dev.dimension.flare.data.network.ktorClient
import dev.dimension.flare.data.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.koin.core.annotation.Single

@Single
internal class FlareAgentRuntimeProvider(
    private val settingsRepository: SettingsRepository,
) {
    private val bridge = AiConfigKoogBridge()
    private val httpClientFactory: KoogHttpClient.Factory by lazy {
        KtorKoogHttpClient.Factory(
            baseClient = ktorClient(config = {}),
        )
    }

    val availability: Flow<AgentAvailability> =
        settingsRepository
            .appSettings
            .map { settings -> bridge.availability(settings.aiConfig) }
            .distinctUntilChanged()

    suspend fun availability(): AgentAvailability =
        bridge.availability(
            settingsRepository
                .appSettings
                .first()
                .aiConfig,
        )

    suspend fun createRuntime(): FlareAgentRuntime? =
        bridge.createRuntime(
            aiConfig =
                settingsRepository
                    .appSettings
                    .first()
                    .aiConfig,
            httpClientFactory = httpClientFactory,
        )
}
