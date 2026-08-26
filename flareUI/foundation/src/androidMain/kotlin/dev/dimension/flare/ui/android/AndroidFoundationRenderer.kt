package dev.dimension.flare.ui.android

import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import dev.dimension.flare.ui.FlareRendererPlugin
import dev.dimension.flare.ui.FlareWidgetRegistrar
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
            },
    ),
    ColumnWidget {
    override val children: AndroidViewChildren = AndroidViewChildren(view)
}

internal class AndroidRowWidget(
    backend: AndroidViewBackend,
) : AbstractAndroidWidget<LinearLayout>(
        view =
            LinearLayout(backend.context).apply {
                orientation = LinearLayout.HORIZONTAL
            },
    ),
    RowWidget {
    override val children: AndroidViewChildren = AndroidViewChildren(view)
}

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
