package dev.dimension.compose.nativekit.android

import android.content.Context
import android.os.Build
import android.view.View
import android.view.ViewGroup
import dev.dimension.compose.nativekit.AbstractNativeKitWidget
import dev.dimension.compose.nativekit.NativeKitBackend
import dev.dimension.compose.nativekit.NativeKitChildren
import dev.dimension.compose.nativekit.NativeKitModifier
import dev.dimension.compose.nativekit.NativeKitSize
import dev.dimension.compose.nativekit.NativeKitWidget
import kotlin.math.roundToInt

/** Strong type token for Android View renderer plugins. */
public class AndroidViewBackend(
    public val context: Context,
) : NativeKitBackend {
    override fun toString(): String = "AndroidViewBackend"
}

/** Renderer contract implemented by Android View-backed primitive plugins. */
public interface AndroidNativeWidget : NativeKitWidget {
    public val view: View
}

public abstract class AbstractAndroidWidget<V : View>(
    final override val view: V,
) : AbstractNativeKitWidget(),
    AndroidNativeWidget {
    override fun onModifierChanged(
        previous: NativeKitModifier,
        current: NativeKitModifier,
    ) {
        view.tag = current.testTag
        val currentParams = view.layoutParams
        val width = current.width.toLayoutSize(view)
        val height = current.height.toLayoutSize(view)
        if (currentParams == null) {
            view.layoutParams = ViewGroup.LayoutParams(width, height)
        } else {
            currentParams.width = width
            currentParams.height = height
            view.layoutParams = currentParams
        }
    }
}

private fun NativeKitSize.toLayoutSize(view: View): Int =
    when (this) {
        NativeKitSize.Wrap -> ViewGroup.LayoutParams.WRAP_CONTENT
        NativeKitSize.Fill -> ViewGroup.LayoutParams.MATCH_PARENT
        is NativeKitSize.Fixed -> (value * view.resources.displayMetrics.density).roundToInt()
    }

public class AndroidViewChildren(
    private val parent: ViewGroup,
) : NativeKitChildren {
    override fun onBeginChanges() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            parent.suppressLayout(true)
        }
    }

    override fun onEndChanges() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            parent.suppressLayout(false)
        }
    }

    override fun insert(
        index: Int,
        widget: NativeKitWidget,
    ) {
        val child = widget.requireAndroidWidget().view
        val layoutParams =
            child.layoutParams
                ?: ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
        parent.addView(child, index, layoutParams)
    }

    override fun move(
        fromIndex: Int,
        toIndex: Int,
        count: Int,
    ) {
        if (fromIndex == toIndex || count == 0) return
        val moved =
            List(count) { offset ->
                parent.getChildAt(fromIndex + offset) to
                    parent.getChildAt(fromIndex + offset).layoutParams
            }
        parent.removeViews(fromIndex, count)
        val destination = if (fromIndex > toIndex) toIndex else toIndex - count
        moved.forEachIndexed { offset, (view, params) ->
            parent.addView(view, destination + offset, params)
        }
    }

    override fun remove(
        index: Int,
        count: Int,
    ) {
        parent.removeViews(index, count)
    }

    private fun NativeKitWidget.requireAndroidWidget(): AndroidNativeWidget =
        this as? AndroidNativeWidget
            ?: error("Android View backend received non-Android widget $this.")
}
