package dev.dimension.flare.ui.presenter.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.dimension.flare.data.datastore.AppDataStore
import dev.dimension.flare.data.datastore.model.AppSettings
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.di.koinInject

public class AiAgentEnabledPresenter :
    PresenterBase<AiAgentEnabledPresenter.State>() {
    private val appDataStore: AppDataStore by koinInject()

    public interface State {
        public val enabled: Boolean
    }

    @Composable
    override fun body(): State {
        val appSettings by appDataStore.appSettingsStore.data.collectAsState(AppSettings(version = ""))
        return StateImpl(
            enabled = appSettings.aiConfig.isAiAgentEnabled(),
        )
    }

    private data class StateImpl(
        override val enabled: Boolean,
    ) : State
}

internal fun AppSettings.AiConfig.isAiAgentEnabled(): Boolean =
    agent && (type as? AppSettings.AiConfig.Type.OpenAI)?.model?.isNotBlank() == true
