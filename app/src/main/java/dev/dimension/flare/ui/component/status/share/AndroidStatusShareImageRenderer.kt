package dev.dimension.flare.ui.component.status.share

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.ui.platform.ComposeView
import androidx.core.graphics.createBitmap
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal suspend fun renderAndroidStatusShareImage(
    context: Context,
    view: View,
    widthPx: Int,
    parentCompositionContext: CompositionContext? = null,
    lifecycleOwner: LifecycleOwner? = null,
    savedStateRegistryOwner: SavedStateRegistryOwner? = null,
    viewModelStoreOwner: ViewModelStoreOwner? = null,
    content: @Composable () -> Unit,
): Bitmap? =
    runCatching {
        val themedContext = ContextThemeWrapper(context, context.theme)
        val captureHost =
            checkNotNull(context.findActivityCaptureHostView() ?: view.findCaptureHostView()) {
                "Unable to find a host view for share capture"
            }
        val composeView =
            ComposeView(themedContext).apply {
                parentCompositionContext?.let(::setParentCompositionContext)
                lifecycleOwner?.let(::setViewTreeLifecycleOwner)
                savedStateRegistryOwner?.let(::setViewTreeSavedStateRegistryOwner)
                viewModelStoreOwner?.let(::setViewTreeViewModelStoreOwner)
                setContent(content)
            }
        val container =
            FrameLayout(themedContext).apply {
                alpha = 0f
                translationX = -10_000f
                clipChildren = false
                clipToPadding = false
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                addView(
                    composeView,
                    FrameLayout.LayoutParams(
                        widthPx,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            }
        try {
            captureHost.addView(
                container,
                ViewGroup.LayoutParams(
                    widthPx,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )

            repeat(2) { composeView.awaitAnimationFrame() }

            val widthSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            repeat(2) {
                check(composeView.isAttachedToWindow) {
                    "Share capture host detached from its window"
                }
                container.measure(widthSpec, heightSpec)
                container.layout(
                    -container.measuredWidth * 2,
                    0,
                    -container.measuredWidth,
                    container.measuredHeight,
                )
                composeView.awaitAnimationFrame()
            }

            val captureWidth = composeView.measuredWidth
            val captureHeight = composeView.measuredHeight
            check(captureWidth > 0 && captureHeight > 0) {
                "Unable to measure share content"
            }

            createBitmap(captureWidth, captureHeight).also { bitmap ->
                composeView.draw(Canvas(bitmap))
            }
        } finally {
            captureHost.removeView(container)
            composeView.disposeComposition()
        }
    }.onFailure(Throwable::printStackTrace).getOrNull()

private suspend fun View.awaitAnimationFrame() {
    suspendCancellableCoroutine { continuation ->
        val callback =
            Runnable {
                if (continuation.isActive) {
                    continuation.resume(Unit)
                }
            }
        postOnAnimation(callback)
        continuation.invokeOnCancellation {
            removeCallbacks(callback)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private fun Context.findActivityCaptureHostView(): ViewGroup? =
    findActivity()
        ?.findViewById<ViewGroup?>(android.R.id.content)
        ?.takeIf { it.isAttachedToWindow }

private fun View.findCaptureHostView(): ViewGroup? =
    (
        rootView.findViewById<ViewGroup?>(android.R.id.content)
            ?: rootView as? ViewGroup
            ?: parent as? ViewGroup
    )?.takeIf { it.isAttachedToWindow }
