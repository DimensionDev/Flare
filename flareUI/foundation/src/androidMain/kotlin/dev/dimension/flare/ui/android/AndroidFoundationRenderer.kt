package dev.dimension.flare.ui.android

import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import dev.dimension.flare.ui.FlareChildren
import dev.dimension.flare.ui.FlareRenderer
import dev.dimension.flare.ui.FlareRendererPlugin
import dev.dimension.flare.ui.FlareSlotId
import dev.dimension.flare.ui.FlareWidgetSystem
import dev.dimension.flare.ui.foundation.ColumnWidget
import dev.dimension.flare.ui.foundation.NativeButtonWidget
import dev.dimension.flare.ui.foundation.RowWidget
import dev.dimension.flare.ui.foundation.TextWidget

/** Builds the Android View renderer set supplied by Foundation and optional plugins. */
public fun createAndroidWidgetSystem(vararg plugins: FlareRendererPlugin<AndroidViewBackend>): FlareWidgetSystem<AndroidViewBackend> =
    FlareWidgetSystem(
        AndroidViewFoundationRendererPlugin,
        *plugins,
    )

@FlareRenderer
internal class AndroidColumnWidget(
    backend: AndroidViewBackend,
) : AbstractAndroidWidget<LinearLayout>(
        view =
            LinearLayout(backend.context).apply {
                orientation = LinearLayout.VERTICAL
            },
    ),
    ColumnWidget {
    private val content = AndroidViewChildren(view)

    override fun children(slot: FlareSlotId): FlareChildren {
        require(slot == ColumnWidget.Content) { "Column does not expose slot '$slot'." }
        return content
    }
}

@FlareRenderer
internal class AndroidRowWidget(
    backend: AndroidViewBackend,
) : AbstractAndroidWidget<LinearLayout>(
        view =
            LinearLayout(backend.context).apply {
                orientation = LinearLayout.HORIZONTAL
            },
    ),
    RowWidget {
    private val content = AndroidViewChildren(view)

    override fun children(slot: FlareSlotId): FlareChildren {
        require(slot == RowWidget.Content) { "Row does not expose slot '$slot'." }
        return content
    }
}

@FlareRenderer
internal class AndroidTextWidget(
    backend: AndroidViewBackend,
) : AbstractAndroidWidget<TextView>(
        view = TextView(backend.context),
    ),
    TextWidget {
    override fun setText(value: String) {
        view.text = value
    }
}

@FlareRenderer
internal class AndroidNativeButtonWidget(
    backend: AndroidViewBackend,
) : AbstractAndroidWidget<Button>(
        view = Button(backend.context),
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
