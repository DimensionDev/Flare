package dev.dimension.flare.ui.screen.splash

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import dev.dimension.flare.R
import dev.dimension.flare.ui.theme.FlareTheme

@Composable
internal fun SplashScreen() {
    FlareTheme {
        Scaffold {
            Box(
                modifier =
                    Modifier
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
