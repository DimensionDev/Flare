package dev.dimension.flare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dev.dimension.flare.common.VideoDownloadHelper
import dev.dimension.flare.ui.AppContainer
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private var keepSplashOnScreen = true
    private val videoDownloadHelper by inject<VideoDownloadHelper>()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { keepSplashOnScreen }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            AppContainer(
                afterInit = {
                    keepSplashOnScreen = false
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        videoDownloadHelper.onResume()
    }

    override fun onPause() {
        super.onPause()
        videoDownloadHelper.onPause()
    }
}
