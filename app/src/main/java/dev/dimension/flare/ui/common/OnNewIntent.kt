package dev.dimension.flare.ui.common

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.util.Consumer

private fun Context.getActivity(): Activity {
    if (this is Activity) return this
    return if (this is ContextWrapper) baseContext.getActivity() else getActivity()
}

@Composable
fun OnNewIntent(
    withOnCreateIntent: Boolean = false,
    onNewIntent: (Intent) -> Unit,
) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = (context.getActivity() as ComponentActivity)
        val listener =
            Consumer<Intent> {
                onNewIntent(it)
            }
        activity.addOnNewIntentListener(listener)
        if (withOnCreateIntent) {
            onNewIntent.invoke(activity.intent)
        }
        onDispose { activity.removeOnNewIntentListener(listener) }
    }
}
