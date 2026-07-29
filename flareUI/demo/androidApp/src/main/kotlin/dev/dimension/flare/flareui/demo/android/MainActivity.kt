package dev.dimension.flare.flareui.demo.android

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

public class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Flare UI Demo"
        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                val padding = 24.dp
                setPadding(padding, padding, padding, padding)

                addView(
                    TextView(context).apply {
                        text = "Select a renderer backend"
                        textSize = 22f
                    },
                    LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT),
                )
                addView(
                    backendButton(
                        label = "Android View",
                        activity = AndroidViewDemoActivity::class.java,
                    ),
                )
                addView(
                    backendButton(
                        label = "Jetpack Compose",
                        activity = ComposeDemoActivity::class.java,
                    ),
                )
            },
        )
    }

    private fun backendButton(
        label: String,
        activity: Class<out Activity>,
    ): Button =
        Button(this).apply {
            text = label
            setOnClickListener {
                startActivity(Intent(this@MainActivity, activity))
            }
        }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()
}
