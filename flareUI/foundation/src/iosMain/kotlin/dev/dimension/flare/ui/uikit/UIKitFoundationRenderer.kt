@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.dimension.flare.ui.uikit

import dev.dimension.flare.ui.FlareRendererPlugin
import dev.dimension.flare.ui.FlareWidgetRegistrar
import dev.dimension.flare.ui.FlareWidgetSystem
import dev.dimension.flare.ui.foundation.ColumnWidget
import dev.dimension.flare.ui.foundation.NativeButtonWidget
import dev.dimension.flare.ui.foundation.RowWidget
import dev.dimension.flare.ui.foundation.TextWidget
import platform.UIKit.UIAction
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UILabel
import platform.UIKit.UILayoutConstraintAxisHorizontal
import platform.UIKit.UILayoutConstraintAxisVertical
import platform.UIKit.UIStackView

public object UIKitFoundationRendererPlugin : FlareRendererPlugin<UIKitBackend> {
    override fun register(registrar: FlareWidgetRegistrar<UIKitBackend>) {
        registrar.register(ColumnWidget::class) { _ -> UIKitColumnWidget() }
        registrar.register(RowWidget::class) { _ -> UIKitRowWidget() }
        registrar.register(TextWidget::class) { _ -> UIKitTextWidget() }
        registrar.register(NativeButtonWidget::class) { _ -> UIKitNativeButtonWidget() }
    }
}

/** Builds the UIKit renderer set supplied by Foundation and optional plugins. */
public fun createUIKitWidgetSystem(vararg plugins: FlareRendererPlugin<UIKitBackend>): FlareWidgetSystem<UIKitBackend> =
    FlareWidgetSystem(
        UIKitFoundationRendererPlugin,
        *plugins,
    )

internal class UIKitColumnWidget :
    AbstractUIKitWidget<UIStackView>(
        view =
            UIStackView().apply {
                axis = UILayoutConstraintAxisVertical
            },
    ),
    ColumnWidget {
    override val children: UIKitChildren = UIKitChildren(view)
}

internal class UIKitRowWidget :
    AbstractUIKitWidget<UIStackView>(
        view =
            UIStackView().apply {
                axis = UILayoutConstraintAxisHorizontal
            },
    ),
    RowWidget {
    override val children: UIKitChildren = UIKitChildren(view)
}

internal class UIKitTextWidget :
    AbstractUIKitWidget<UILabel>(
        view = UILabel(),
    ),
    TextWidget {
    override fun setText(value: String) {
        view.text = value
    }
}

internal class UIKitNativeButtonWidget :
    AbstractUIKitWidget<UIButton>(
        view = UIButton.buttonWithType(UIButtonTypeSystem),
    ),
    NativeButtonWidget {
    private var clickAction: () -> Unit = {}
    private var action: UIAction? = UIAction.actionWithHandler { clickAction() }

    init {
        view.addAction(checkNotNull(action), forControlEvents = UIControlEventTouchUpInside)
    }

    override fun setLabel(value: String) {
        view.setTitle(value, forState = UIControlStateNormal)
    }

    override fun setEnabled(value: Boolean) {
        view.enabled = value
    }

    override fun setOnClick(value: () -> Unit) {
        clickAction = value
    }

    override fun dispose() {
        clickAction = {}
        action?.let { current ->
            view.removeAction(current, forControlEvents = UIControlEventTouchUpInside)
        }
        action = null
    }
}
