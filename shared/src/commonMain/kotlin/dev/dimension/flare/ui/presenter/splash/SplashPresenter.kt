package dev.dimension.flare.ui.presenter.splash

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.dimension.flare.data.repository.AccountRepository
import dev.dimension.flare.data.repository.activeAccountPresenter
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.delay
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SplashPresenter(
    private val toHome: () -> Unit,
    private val toLogin: () -> Unit,
) : PresenterBase<SplashType>(),
    KoinComponent {
    private val accountRepository: AccountRepository by inject()

    @Composable
    override fun body(): SplashType {
        val accountState by activeAccountPresenter(repository = accountRepository)
        var type: SplashType by remember { mutableStateOf(SplashType.Splash) }
        LaunchedEffect(accountState) {
            when (accountState) {
                is UiState.Error -> {
                    delay(1000)
                    type = SplashType.Login
                    toLogin()
                }

                is UiState.Loading -> Unit
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
enum class SplashType {
    Splash,
    Login,
    Home,
}
