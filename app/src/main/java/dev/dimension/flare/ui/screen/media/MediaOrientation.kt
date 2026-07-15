package dev.dimension.flare.ui.screen.media

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun MediaLandscapeEffect(
    enabled: Boolean,
    originalOrientation: Int?,
    setOriginalOrientation: (Int?) -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    DisposableEffect(activity, enabled, originalOrientation) {
        if (enabled) {
            if (originalOrientation == null) {
                setOriginalOrientation(
                    activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
                )
            } else {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
        } else if (originalOrientation != null) {
            activity?.requestedOrientation = originalOrientation
            setOriginalOrientation(null)
        }
        onDispose {
            if (activity?.isChangingConfigurations != true && originalOrientation != null) {
                activity?.requestedOrientation = originalOrientation
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
