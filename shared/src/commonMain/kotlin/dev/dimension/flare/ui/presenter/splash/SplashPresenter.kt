package dev.dimension.flare.ui.presenter.splash

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.di.koinInject
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.model.flattenUiState
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.delay

public class SplashPresenter(
    private val toHome: () -> Unit,
    private val toLogin: () -> Unit,
) : PresenterBase<SplashType>() {
    private val accountRepository: AccountRepository by koinInject()

    @Composable
    override fun body(): SplashType {
        val accountState by accountRepository.activeAccount.flattenUiState()
        var type: SplashType by remember { mutableStateOf(SplashType.Splash) }
        LaunchedEffect(accountState) {
            when (accountState) {
                is UiState.Error -> {
                    delay(1000)
                    type = SplashType.Login
                    toLogin()
                }

                is UiState.Loading -> {
                    Unit
                }

                is UiState.Success -> {
                    delay(1000)
                    type = SplashType.Home
                    toHome()
                }
            }
        }
        return type
    }
}

@Immutable
public enum class SplashType {
    Splash,
    Login,
    Home,
}
