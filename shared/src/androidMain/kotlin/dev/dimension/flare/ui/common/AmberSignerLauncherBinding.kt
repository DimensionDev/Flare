package dev.dimension.flare.ui.common

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import dev.dimension.flare.data.network.nostr.AmberIntentLauncherRegistry
import dev.dimension.flare.data.network.nostr.AmberIntentResult
import org.koin.compose.koinInject

@Composable
public fun BindAmberSignerLauncher() {
    val registry = koinInject<AmberIntentLauncherRegistry>()
    val launcher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult(),
        ) { result ->
            pendingCallback?.invoke(
                AmberIntentResult(
                    resultCode = result.resultCode,
                    data = result.data,
                ),
            )
            pendingCallback = null
        }

    DisposableEffect(registry, launcher) {
        val closeable =
            registry.attach { intent: Intent, callback: (AmberIntentResult) -> Unit ->
                pendingCallback = callback
                launcher.launch(intent)
            }
        onDispose {
            pendingCallback = null
            closeable.close()
        }
    }
}

private var pendingCallback: ((AmberIntentResult) -> Unit)? = null
