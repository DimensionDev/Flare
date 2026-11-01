package dev.dimension.flare.ui.presenter.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.dimension.flare.data.model.ProxyPolicy
import dev.dimension.flare.data.model.ProxySettings
import dev.dimension.flare.data.model.ProxySettingsError
import dev.dimension.flare.data.model.ProxySettingsException
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.ProxyRepository
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.UiAccount
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.collectAsUiState
import dev.dimension.flare.ui.model.takeSuccess
import dev.dimension.flare.ui.presenter.PresenterBase
import dev.dimension.flare.ui.presenter.invoke
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

public class ProxySettingsPresenter : PresenterBase<ProxySettingsState>() {
    private val repository by koinInject<ProxyRepository>()
    private val accountRepository by koinInject<AccountRepository>()

    @Composable
    override fun body(): ProxySettingsState {
        var loadAttempt by remember { mutableIntStateOf(0) }
        val settingsState = key(loadAttempt) { repository.settings.collectAsUiState().value }
        val accounts by accountRepository.allAccounts.collectAsState(persistentListOf())
        val accountState = remember { AccountsPresenter() }.invoke()
        val accountProfiles =
            accountState.accounts
                .takeSuccess()
                .orEmpty()
                .mapNotNull { item ->
                    item.profile.takeSuccess()?.let { item.account.accountKey to it }
                }.toMap()
                .toImmutableMap()
        val scope = rememberCoroutineScope()
        var operationState by remember { mutableStateOf<UiState<Unit>?>(null) }
        return object : ProxySettingsState {
            override val settings = settingsState
            override val accounts = accounts
            override val accountProfiles = accountProfiles
            override val operation = operationState

            override fun retry() {
                loadAttempt++
            }

            override fun select(
                accountKey: MicroBlogKey?,
                policy: ProxyPolicy,
            ) {
                if (operationState is UiState.Loading) return
                operationState = null
                scope.launch {
                    try {
                        if (accountKey == null) repository.selectGlobal(policy) else repository.selectAccount(accountKey, policy)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (exception: Exception) {
                        if (exception !is ProxySettingsException) exception.printStackTrace()
                        operationState = UiState.Error(exception)
                    }
                }
            }

            override fun remove(id: String) {
                if (operationState is UiState.Loading) return
                operationState = UiState.Loading()
                scope.launch {
                    try {
                        repository.remove(id)
                        operationState = UiState.Success(Unit)
                    } catch (cancelled: CancellationException) {
                        operationState = null
                        throw cancelled
                    } catch (exception: Exception) {
                        if (exception !is ProxySettingsException) exception.printStackTrace()
                        operationState = UiState.Error(exception)
                    }
                }
            }
        }
    }
}

@Immutable
public interface ProxySettingsState {
    public val settings: UiState<ProxySettings>
    public val accounts: ImmutableList<UiAccount>
    public val accountProfiles: ImmutableMap<MicroBlogKey, UiProfile>
    public val operation: UiState<Unit>?
    public val error: ProxySettingsError?
        get() =
            ((settings as? UiState.Error)?.throwable ?: (operation as? UiState.Error)?.throwable)
                ?.let { (it as? ProxySettingsException)?.reason ?: ProxySettingsError.REQUEST_FAILED }

    public fun retry()

    public fun accountLabel(account: UiAccount): String =
        accountProfiles[account.accountKey]?.let { "${it.name.innerText}, ${it.handle.display}" }
            ?: "${account.platformDisplayName}, ${account.accountKey}"

    public fun select(
        accountKey: MicroBlogKey?,
        policy: ProxyPolicy,
    )

    public fun remove(id: String)
}
