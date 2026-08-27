@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.dimension.flare.ui.uikit

import dev.dimension.flare.ui.FlareRendererPlugin
import dev.dimension.flare.ui.FlareWidgetRegistrar
import dev.dimension.flare.ui.FlareWidgetSystem
import dev.dimension.flare.ui.foundation.ColumnWidget
import dev.dimension.flare.ui.foundation.HorizontalAlignment
import dev.dimension.flare.ui.foundation.NativeButtonWidget
import dev.dimension.flare.ui.foundation.RowWidget
import dev.dimension.flare.ui.foundation.TextWidget
import dev.dimension.flare.ui.foundation.VerticalAlignment
import platform.UIKit.UIAction
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UILabel
import platform.UIKit.UILayoutConstraintAxisHorizontal
import platform.UIKit.UILayoutConstraintAxisVertical
import platform.UIKit.UIStackView
import platform.UIKit.UIStackViewAlignmentBottom
import platform.UIKit.UIStackViewAlignmentCenter
import platform.UIKit.UIStackViewAlignmentFill
import platform.UIKit.UIStackViewAlignmentLeading
import platform.UIKit.UIStackViewAlignmentTop
import platform.UIKit.UIStackViewAlignmentTrailing

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

    override fun setSpacing(value: Float) {
        view.spacing = value.toDouble()
    }

    override fun setHorizontalAlignment(value: HorizontalAlignment) {
        view.alignment =
            when (value) {
                HorizontalAlignment.Start -> UIStackViewAlignmentLeading
                HorizontalAlignment.Center -> UIStackViewAlignmentCenter
                HorizontalAlignment.End -> UIStackViewAlignmentTrailing
                HorizontalAlignment.Stretch -> UIStackViewAlignmentFill
            }
    }
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

    override fun setSpacing(value: Float) {
        view.spacing = value.toDouble()
    }

    override fun setVerticalAlignment(value: VerticalAlignment) {
        view.alignment =
            when (value) {
                VerticalAlignment.Top -> UIStackViewAlignmentTop
                VerticalAlignment.Center -> UIStackViewAlignmentCenter
                VerticalAlignment.Bottom -> UIStackViewAlignmentBottom
                VerticalAlignment.Stretch -> UIStackViewAlignmentFill
            }
    }
}

internal class UIKitTextWidget :
    AbstractUIKitWidget<UILabel>(
        view =
            UILabel().apply {
                numberOfLines = 0
            },
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
