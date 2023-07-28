package dev.dimension.flare.ui.screen.splash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import dev.dimension.flare.R
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
                    painterResource(id = R.drawable.ic_launcher_foreground),
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