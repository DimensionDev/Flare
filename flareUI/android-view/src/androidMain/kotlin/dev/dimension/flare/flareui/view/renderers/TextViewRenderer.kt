package dev.dimension.flare.flareui.view

import android.content.Context
import android.view.Gravity
import com.google.android.material.textview.MaterialTextView
import dev.dimension.flare.flareui.TextProps
import dev.dimension.flare.flareui.TextType

internal fun createTextView(context: Context): AndroidViewNode =
    AndroidViewNode(
        type = TextType,
        view =
            MaterialTextView(context).apply {
                gravity = Gravity.START or Gravity.TOP
            },
    )

internal fun updateTextView(
    node: AndroidViewNode,
    props: TextProps,
) {
    (node.view as MaterialTextView).text = props.value
}
