package dev.dimension.flare.flareui.view

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout

internal fun createRowView(context: Context): AndroidViewNode =
    AndroidViewNode(
        type = dev.dimension.flare.flareui.RowType,
        view =
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.START or Gravity.TOP
                isBaselineAligned = false
            },
    )

@Suppress("UNUSED_PARAMETER")
internal fun updateRowView(
    node: AndroidViewNode,
    props: Unit,
): Unit = Unit
