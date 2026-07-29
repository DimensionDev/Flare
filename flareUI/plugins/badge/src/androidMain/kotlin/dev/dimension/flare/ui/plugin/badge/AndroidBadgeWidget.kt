package dev.dimension.flare.ui.plugin.badge

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.widget.TextView
import dev.dimension.flare.ui.FlareRenderer
import dev.dimension.flare.ui.android.AbstractAndroidWidget
import dev.dimension.flare.ui.android.AndroidViewBackend

@FlareRenderer
internal class AndroidBadgeWidget(
    backend: AndroidViewBackend,
) : AbstractAndroidWidget<TextView>(
        view =
            TextView(backend.context).apply {
                val horizontal = 10.dp(backend.context)
                val vertical = 4.dp(backend.context)
                setPadding(horizontal, vertical, horizontal, vertical)
            },
    ),
    BadgeWidget {
    private val badgeBackground =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 12.dp(view.context).toFloat()
        }
    private var clickAction: () -> Unit = {}

    init {
        view.background = badgeBackground
        view.setOnClickListener {
            clickAction()
        }
    }

    override fun setText(value: String) {
        view.text = value
    }

    override fun setTone(value: BadgeTone) {
        val color =
            when (value) {
                BadgeTone.Neutral -> Color.rgb(230, 230, 230)
                BadgeTone.Positive -> Color.rgb(190, 235, 198)
                BadgeTone.Warning -> Color.rgb(255, 225, 155)
            }
        badgeBackground.setColor(color)
    }

    override fun setOnClick(value: () -> Unit) {
        clickAction = value
    }

    override fun dispose() {
        clickAction = {}
        view.setOnClickListener(null)
    }
}

private fun Int.dp(context: android.content.Context): Int =
    TypedValue
        .applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            toFloat(),
            context.resources.displayMetrics,
        ).toInt()
