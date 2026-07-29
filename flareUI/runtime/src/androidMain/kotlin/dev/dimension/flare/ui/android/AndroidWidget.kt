package dev.dimension.flare.ui.android

import android.content.Context
import android.os.Build
import android.view.View
import android.view.ViewGroup
import dev.dimension.flare.ui.AbstractFlareWidget
import dev.dimension.flare.ui.FlareBackend
import dev.dimension.flare.ui.FlareBackendWidget
import dev.dimension.flare.ui.FlareChildren
import dev.dimension.flare.ui.FlareModifier
import dev.dimension.flare.ui.FlareWidget
import dev.dimension.flare.ui.testTagOrNull

/** Strong type token for Android View renderer plugins. */
public class AndroidViewBackend(
    public val context: Context,
) : FlareBackend {
    override fun toString(): String = "AndroidViewBackend"
}

/** Renderer contract implemented by Android View-backed primitive plugins. */
public interface AndroidNativeWidget : FlareBackendWidget<AndroidViewBackend> {
    public val view: View
}

public abstract class AbstractAndroidWidget<V : View>(
    final override val view: V,
) : AbstractFlareWidget(),
    AndroidNativeWidget {
    override fun onModifierChanged(
        previous: FlareModifier,
        current: FlareModifier,
    ) {
        view.tag = current.testTagOrNull()
    }
}

public class AndroidViewChildren(
    private val parent: ViewGroup,
) : FlareChildren {
    private var changeDepth: Int = 0

    override fun onBeginChanges() {
        changeDepth += 1
        if (changeDepth == 1 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            parent.suppressLayout(true)
        }
    }

    override fun onEndChanges() {
        check(changeDepth > 0) { "AndroidViewChildren received an unmatched endChanges call." }
        changeDepth -= 1
        if (changeDepth == 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            parent.suppressLayout(false)
        }
    }

    override fun insert(
        index: Int,
        widget: FlareWidget,
    ) {
        val child = widget.requireAndroidWidget().view
        parent.addView(child, index)
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

    private fun FlareWidget.requireAndroidWidget(): AndroidNativeWidget =
        this as? AndroidNativeWidget
            ?: error("Android View backend received non-Android widget $this.")
}
