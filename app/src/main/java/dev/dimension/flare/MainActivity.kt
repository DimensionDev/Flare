package dev.dimension.flare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dev.dimension.flare.ui.AppContainer

class MainActivity : ComponentActivity() {
    private var keepSplashOnScreen = true

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { keepSplashOnScreen }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        android.webkit.WebView.setWebContentsDebuggingEnabled(true)
        setContent {
            AppContainer(
                afterInit = {
                    keepSplashOnScreen = false
                },
            )
        }
    }
}
