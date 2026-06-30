package dev.dimension.flare

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.ComposeRuntimeFlags
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ExperimentalComposeApi
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.ComposeUiFlags
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dev.dimension.flare.common.AndroidDownloadManager
import dev.dimension.flare.ui.AppContainer
import dev.dimension.flare.ui.component.platform.LocalWifiState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import org.koin.android.ext.android.inject

class MainActivity : ComponentActivity() {
    private var keepSplashOnScreen = true
    private val downloadManager by inject<AndroidDownloadManager>()
    private val wifiStateFlow by lazy {
        observeWifiStateAsFlow()
    }

    @OptIn(ExperimentalComposeUiApi::class, ExperimentalComposeApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen().setKeepOnScreenCondition { keepSplashOnScreen }
        enableEdgeToEdge()
        ComposeUiFlags.isMediaQueryIntegrationEnabled = true
        ComposeRuntimeFlags.isLinkBufferComposerEnabled = true
        super.onCreate(savedInstanceState)
        setContent {
            val wifiState by wifiStateFlow.collectAsState(false)
            CompositionLocalProvider(
                LocalWifiState provides wifiState,
            ) {
                AppContainer(
                    afterInit = {
                        keepSplashOnScreen = false
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        setIntent(intent)
        super.onNewIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        downloadManager.onResume()
    }

    override fun onPause() {
        super.onPause()
        downloadManager.onPause()
    }
}

private fun Context.observeWifiStateAsFlow() =
    callbackFlow {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkRequest =
            NetworkRequest
                .Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
        val networkCallback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities,
                ) {
                    trySend(networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
                }
            }
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
        val currentNetwork = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(currentNetwork)
        trySend(caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true)
        awaitClose {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }
