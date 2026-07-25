package dev.dimension.flare.flareui.view

import android.content.Context
import android.view.Gravity
import com.google.android.material.textview.MaterialTextView
import dev.dimension.flare.flareui.AndroidFlareResourceResolver
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
    resources: AndroidFlareResourceResolver,
) {
    (node.view as MaterialTextView).apply {
        text = context.resolveFlareText(props.value, resources)
    }
}
