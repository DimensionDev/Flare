package dev.dimension.flare.flareui.view

import android.content.Context
import android.view.Gravity
import com.google.android.material.button.MaterialButton
import dev.dimension.flare.flareui.ButtonProps
import dev.dimension.flare.flareui.ButtonType
import kotlin.math.roundToInt

internal const val COMPOSE_MATERIAL3_BUTTON_MIN_WIDTH_DP: Int = 58

internal fun createButtonView(context: Context): AndroidViewNode =
    AndroidViewNode(
        type = ButtonType,
        view =
            MaterialButton(context).apply {
                gravity = Gravity.CENTER
                val composeMinimumWidth =
                    (
                        COMPOSE_MATERIAL3_BUTTON_MIN_WIDTH_DP *
                            resources.displayMetrics.density
                    ).roundToInt()
                minimumWidth = composeMinimumWidth
                minWidth = composeMinimumWidth
            },
    )

internal fun updateButtonView(
    node: AndroidViewNode,
    props: ButtonProps,
) {
    (node.view as MaterialButton).apply {
        text = props.label
        isEnabled = props.enabled
        setOnClickListener { props.onClick() }
    }
}
