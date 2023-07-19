package dev.dimension.flare.ui.screen.splash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.dimension.flare.data.repository.activeAccountPresenter
import dev.dimension.flare.molecule.producePresenter
import dev.dimension.flare.ui.UiState
import dev.dimension.flare.ui.theme.FlareTheme
import kotlinx.coroutines.delay

@Composable
internal fun SplashScreen(
    toHome: () -> Unit,
    toLogin: () -> Unit,
) {
    producePresenter {
        SplashPresenter(
            toHome,
            toLogin
        )
    }
    FlareTheme {
        Scaffold {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(it),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = null,
                )
            }
        }
    }
}

@Composable
private fun SplashPresenter(
    toHome: () -> Unit,
    toLogin: () -> Unit,
) {
    val accountState by activeAccountPresenter()
    LaunchedEffect(accountState) {
        when (val state = accountState) {
            is UiState.Error -> Unit
            is UiState.Loading -> Unit
            is UiState.Success -> {
                delay(1000)
                if (state.data == null) {
                    toLogin()
                } else {
                    toHome()
                }
            }
        }
    }
}