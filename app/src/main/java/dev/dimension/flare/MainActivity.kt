package dev.dimension.flare

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import coil.compose.LocalImageLoader
import dev.dimension.flare.common.BrowserLoginDeepLinksChannel
import dev.dimension.flare.ui.initialRoute
import dev.dimension.flare.ui.main
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Content()
        }
    }


    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.data?.let {
            onDeeplink(it)
        }
    }


    private fun onDeeplink(it: Uri) {
        if (BrowserLoginDeepLinksChannel.canHandle(it.toString())) {
            lifecycleScope.launch {
                repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    BrowserLoginDeepLinksChannel.send(it.toString())
                }
            }
        } else {
        }
    }

}
@Composable
fun Content(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = initialRoute,
        modifier = modifier,
        enterTransition = {
            fadeIn() + slideInHorizontally { it / 4 }
        },
        exitTransition = {
            fadeOut() + slideOutHorizontally { -it / 4 }
        }
    ) {
        with(navController) {
            main()
        }
    }
}