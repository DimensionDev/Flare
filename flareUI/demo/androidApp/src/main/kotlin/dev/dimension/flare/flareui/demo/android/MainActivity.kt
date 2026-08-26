package dev.dimension.flare.flareui.demo.android

import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.textview.MaterialTextView
import dev.dimension.flare.ui.demo.createAndroidComposeDemoView
import dev.dimension.flare.ui.demo.createAndroidViewDemoView

public class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Flare UI Demo"
        val content =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(backendLabel("Android View backend"))
                addView(createAndroidViewDemoView(this@MainActivity), wrapContentParams())
                addView(backendLabel("Android Compose backend"))
                addView(createAndroidComposeDemoView(this@MainActivity), wrapContentParams())
            }
        val scrollView =
            ScrollView(this).apply {
                addView(content)
            }
        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { view, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            windowInsets
        }
        setContentView(scrollView)
    }

    private fun backendLabel(value: String): MaterialTextView =
        MaterialTextView(this).apply {
            val horizontalPadding = (24 * resources.displayMetrics.density).toInt()
            val verticalPadding = (16 * resources.displayMetrics.density).toInt()
            text = value
            textSize = 20f
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, 0)
        }

    private fun wrapContentParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
}
