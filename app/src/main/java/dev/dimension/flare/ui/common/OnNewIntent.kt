package dev.dimension.flare.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.util.Consumer

private fun Context.getActivity(): Activity {
    if (this is Activity) return this
    return if (this is ContextWrapper) baseContext.getActivity() else getActivity()
}

@Composable
fun OnNewIntent(
    key1: Any? = null,
    key2: Any? = null,
    key3: Any? = null,
    onNewIntent: (Intent) -> Unit,
) {
    val context = LocalContext.current
    DisposableEffect(key1 = key1, key2 = key2, key3 = key3) {
        val activity = (context.getActivity() as ComponentActivity)
        val listener =
            Consumer<Intent> {
                onNewIntent(it)
            }
        activity.addOnNewIntentListener(listener)
        onDispose { activity.removeOnNewIntentListener(listener) }
    }
}

@Composable
fun FullScreenBox(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val context = LocalContext.current
    val windowManager =
        remember { context.getSystemService(Context.WINDOW_SERVICE) as WindowManager }

    val (widthPx, heightPx) =
        remember(windowManager) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                windowManager.currentWindowMetrics.bounds.width() to
                    windowManager.currentWindowMetrics.bounds.height()
            } else {
                DisplayMetrics().apply {
                    @Suppress("DEPRECATION")
                    windowManager.defaultDisplay.getRealMetrics(this)
                }.let {
                    it.widthPixels to it.heightPixels
                }
            }
        }
    val (width, height) =
        with(LocalDensity.current) {
            remember(widthPx, heightPx) {
                Pair(widthPx.toDp(), heightPx.toDp())
            }
        }
    Box(modifier = Modifier.requiredSize(width, height).then(modifier), content = content)
}
