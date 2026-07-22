package dev.dimension.flare.ui.presenter.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.MxgaRepository
import dev.dimension.flare.data.repository.SettingsRepository
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

public class MxgaSettingsPresenter : PresenterBase<MxgaSettingsState>() {
    private val accountRepository: AccountRepository by koinInject()
    private val settingsRepository: SettingsRepository by koinInject()
    private val mxgaRepository: MxgaRepository by koinInject()

    @Composable
    override fun body(): MxgaSettingsState {
        val scope = rememberCoroutineScope()
        val hasXQtAccount by
            accountRepository.allAccounts
                .map { accounts -> accounts.any { it.platformType == PlatformType.xQt } }
                .collectAsState(false)
        val appSettings by settingsRepository.appSettings.collectAsUiState()
        val isRefreshing by mxgaRepository.isRefreshing.collectAsState()
        val snapshot by mxgaRepository.snapshot.collectAsState()
        return object : MxgaSettingsState {
            override val hasXQtAccount: Boolean = hasXQtAccount
            override val isEnabled: Boolean = appSettings.takeSuccess()?.mxgaEnabled == true
            override val isRefreshing: Boolean = isRefreshing
            override val lastCheckedAt: Long = snapshot.checkedAt

            override fun setEnabled(value: Boolean) {
                scope.launch {
                    settingsRepository.updateAppSettings { copy(mxgaEnabled = value) }
                }
            }

            override fun refresh() {
                mxgaRepository.refresh()
            }
        }
    }
}

@Immutable
public interface MxgaSettingsState {
    public val hasXQtAccount: Boolean
    public val isEnabled: Boolean
    public val isRefreshing: Boolean
    public val lastCheckedAt: Long

    public fun setEnabled(value: Boolean)

    public fun refresh()
}
