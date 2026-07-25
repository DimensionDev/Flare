package dev.dimension.flare.flareui.view

import android.content.Context
import android.view.Gravity
import android.widget.LinearLayout

internal fun createColumnView(context: Context): AndroidViewNode =
    AndroidViewNode(
        type = dev.dimension.flare.flareui.ColumnType,
        view =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.START or Gravity.TOP
            },
    )

@Suppress("UNUSED_PARAMETER")
internal fun updateColumnView(
    node: AndroidViewNode,
    props: Unit,
): Unit = Unit
