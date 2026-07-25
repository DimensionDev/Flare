package dev.dimension.flare.flareui.demo.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.LinearLayout.LayoutParams
import com.google.android.material.button.MaterialButton
import com.google.android.material.textview.MaterialTextView

public class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val padding = 24.dp
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(padding, padding, padding, padding)

                addView(
                    MaterialTextView(context).apply {
                        text = "Choose the Android renderer"
                        textSize = 22f
                    },
                    matchWidth(),
                )
                addView(
                    MaterialButton(context).apply {
                        text = "Compose UI"
                        setOnClickListener {
                            open(ComposeDemoActivity::class.java)
                        }
                    },
                    matchWidth(),
                )
                addView(
                    MaterialButton(context).apply {
                        text = "Android Views"
                        setOnClickListener {
                            open(ViewDemoActivity::class.java)
                        }
                    },
                    matchWidth(),
                )
            },
        )
    }

    private fun open(activityClass: Class<out Activity>) {
        startActivity(Intent(this, activityClass))
    }

    private fun matchWidth(): LayoutParams =
        LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            topMargin = 12.dp
        }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()
}
