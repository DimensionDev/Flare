package dev.dimension.flare.ui.android

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.widget.LinearLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView
import dev.dimension.flare.ui.FlareRendererPlugin
import dev.dimension.flare.ui.FlareWidgetRegistrar
import dev.dimension.flare.ui.FlareWidgetSystem
import dev.dimension.flare.ui.foundation.ColumnWidget
import dev.dimension.flare.ui.foundation.HorizontalAlignment
import dev.dimension.flare.ui.foundation.NativeButtonWidget
import dev.dimension.flare.ui.foundation.RowWidget
import dev.dimension.flare.ui.foundation.TextWidget
import dev.dimension.flare.ui.foundation.VerticalAlignment
import kotlin.math.roundToInt
import com.google.android.material.R as MaterialR

/** Builds the Android View renderer set supplied by Foundation and optional plugins. */
public fun createAndroidWidgetSystem(vararg plugins: FlareRendererPlugin<AndroidViewBackend>): FlareWidgetSystem<AndroidViewBackend> =
    FlareWidgetSystem(
        AndroidViewFoundationRendererPlugin,
        *plugins,
    )

public object AndroidViewFoundationRendererPlugin : FlareRendererPlugin<AndroidViewBackend> {
    override fun register(registrar: FlareWidgetRegistrar<AndroidViewBackend>) {
        registrar.register(ColumnWidget::class) { backend -> AndroidColumnWidget(backend) }
        registrar.register(RowWidget::class) { backend -> AndroidRowWidget(backend) }
        registrar.register(TextWidget::class) { backend -> AndroidTextWidget(backend) }
        registrar.register(NativeButtonWidget::class) { backend -> AndroidNativeButtonWidget(backend) }
    }
}

internal class AndroidColumnWidget(
    backend: AndroidViewBackend,
) : AbstractAndroidWidget<LinearLayout>(
        view =
            LinearLayout(backend.context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.TOP or Gravity.START
            },
    ),
    ColumnWidget {
    override val children: AndroidViewChildren = AndroidViewChildren(view)

    override fun setSpacing(value: Float) {
        view.setItemSpacing(value.toPixels(view.resources.displayMetrics.density))
    }

    override fun setHorizontalAlignment(value: HorizontalAlignment) {
        view.gravity =
            Gravity.TOP or
            when (value) {
                HorizontalAlignment.Start -> Gravity.START
                HorizontalAlignment.Center -> Gravity.CENTER_HORIZONTAL
                HorizontalAlignment.End -> Gravity.END
            }
    }
}

internal class AndroidRowWidget(
    backend: AndroidViewBackend,
) : AbstractAndroidWidget<LinearLayout>(
        view =
            LinearLayout(backend.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
            },
    ),
    RowWidget {
    override val children: AndroidViewChildren = AndroidViewChildren(view)

    override fun setSpacing(value: Float) {
        view.setItemSpacing(value.toPixels(view.resources.displayMetrics.density))
    }

    override fun setVerticalAlignment(value: VerticalAlignment) {
        view.gravity =
            Gravity.START or
            when (value) {
                VerticalAlignment.Top -> Gravity.TOP
                VerticalAlignment.Center -> Gravity.CENTER_VERTICAL
                VerticalAlignment.Bottom -> Gravity.BOTTOM
            }
    }
}

internal class AndroidTextWidget(
    backend: AndroidViewBackend,
) : AbstractAndroidWidget<MaterialTextView>(
        view =
            MaterialTextView(backend.context).apply {
                setTextAppearance(MaterialR.style.TextAppearance_Material3_BodyLarge)
            },
    ),
    TextWidget {
    override fun setText(value: String) {
        view.text = value
    }
}

internal class AndroidNativeButtonWidget(
    backend: AndroidViewBackend,
) : AbstractAndroidWidget<MaterialButton>(
        view = MaterialButton(backend.context),
    ),
    NativeButtonWidget {
    private var clickAction: () -> Unit = {}

    init {
        view.setOnClickListener {
            clickAction()
        }
    }

    override fun setLabel(value: String) {
        view.text = value
    }

    override fun setEnabled(value: Boolean) {
        view.isEnabled = value
    }

    override fun setOnClick(value: () -> Unit) {
        clickAction = value
    }

    override fun dispose() {
        clickAction = {}
        view.setOnClickListener(null)
    }
}

private fun LinearLayout.setItemSpacing(spacing: Int) {
    dividerDrawable =
        if (spacing == 0) {
            null
        } else {
            SpacingDrawable(spacing)
        }
    showDividers =
        if (spacing == 0) {
            LinearLayout.SHOW_DIVIDER_NONE
        } else {
            LinearLayout.SHOW_DIVIDER_MIDDLE
        }
}

private fun Float.toPixels(density: Float): Int = (this * density).roundToInt()

private class SpacingDrawable(
    private val spacing: Int,
) : ColorDrawable(Color.TRANSPARENT) {
    override fun getIntrinsicWidth(): Int = spacing

    override fun getIntrinsicHeight(): Int = spacing
}
