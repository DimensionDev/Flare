package dev.dimension.flare.ui.presenter.splash

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import dev.dimension.flare.data.repository.activeAccountPresenter
import dev.dimension.flare.ui.model.UiState
import dev.dimension.flare.ui.presenter.PresenterBase
import kotlinx.coroutines.delay

class SplashPresenter(
    private val toHome: () -> Unit,
    private val toLogin: () -> Unit,
) : PresenterBase<Unit>() {
    @Composable
    override fun body() {
        val accountState by activeAccountPresenter()
        LaunchedEffect(accountState) {
            when (accountState) {
                is UiState.Error -> {
                    delay(1000)
                    toLogin()
                }

                is UiState.Loading -> Unit
                is UiState.Success -> {
                    delay(1000)
                    toHome()
                }
            }
        }
    }
}