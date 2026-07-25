package dev.dimension.flare.flareui.view

import android.content.Context
import android.content.res.ColorStateList
import android.widget.ImageView
import com.google.android.material.color.MaterialColors
import dev.dimension.flare.flareui.AndroidFlareResourceResolver
import dev.dimension.flare.flareui.IconProps
import dev.dimension.flare.flareui.IconType
import kotlin.math.roundToInt

private const val COMPOSE_ICON_SIZE_DP = 24

internal fun createIconView(context: Context): AndroidViewNode =
    AndroidViewNode(
        type = IconType,
        view =
            ImageView(context).apply {
                val iconSize =
                    (COMPOSE_ICON_SIZE_DP * resources.displayMetrics.density).roundToInt()
                minimumWidth = iconSize
                minimumHeight = iconSize
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                imageTintList =
                    ColorStateList.valueOf(
                        MaterialColors.getColor(
                            this,
                            com.google.android.material.R.attr.colorOnSurface,
                        ),
                    )
            },
    )

internal fun updateIconView(
    node: AndroidViewNode,
    props: IconProps,
    resources: AndroidFlareResourceResolver,
) {
    (node.view as ImageView).apply {
        setImageResource(resources.imageId(props.image))
        contentDescription =
            props.contentDescription?.let { description ->
                context.resolveFlareText(description, resources)
            }
        importantForAccessibility =
            if (contentDescription == null) {
                ImageView.IMPORTANT_FOR_ACCESSIBILITY_NO
            } else {
                ImageView.IMPORTANT_FOR_ACCESSIBILITY_YES
            }
    }
}
