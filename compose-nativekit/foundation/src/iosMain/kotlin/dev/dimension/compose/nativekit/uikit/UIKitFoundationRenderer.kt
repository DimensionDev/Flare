@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.dimension.compose.nativekit.uikit

import dev.dimension.compose.nativekit.NativeKitRendererPlugin
import dev.dimension.compose.nativekit.NativeKitWidgetRegistrar
import dev.dimension.compose.nativekit.NativeKitWidgetSystem
import dev.dimension.compose.nativekit.foundation.ColumnWidget
import dev.dimension.compose.nativekit.foundation.HorizontalAlignment
import dev.dimension.compose.nativekit.foundation.NativeButtonWidget
import dev.dimension.compose.nativekit.foundation.RowWidget
import dev.dimension.compose.nativekit.foundation.TextWidget
import dev.dimension.compose.nativekit.foundation.VerticalAlignment
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

public object UIKitFoundationRendererPlugin : NativeKitRendererPlugin<UIKitBackend> {
    override fun register(registrar: NativeKitWidgetRegistrar<UIKitBackend>) {
        registrar.register(ColumnWidget::class) { _ -> UIKitColumnWidget() }
        registrar.register(RowWidget::class) { _ -> UIKitRowWidget() }
        registrar.register(TextWidget::class) { _ -> UIKitTextWidget() }
        registrar.register(NativeButtonWidget::class) { _ -> UIKitNativeButtonWidget() }
    }
}

/** Builds the UIKit renderer set supplied by Foundation and optional plugins. */
public fun createUIKitWidgetSystem(vararg plugins: NativeKitRendererPlugin<UIKitBackend>): NativeKitWidgetSystem<UIKitBackend> =
    NativeKitWidgetSystem(
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
