package dev.dimension.flare.flareui.demo.android

import android.os.Bundle
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton

public class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.app_name)
        val content =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(
                    backendButton(
                        id = R.id.open_android_view,
                        label = getString(R.string.android_view_backend),
                        backend = DemoBackend.ANDROID_VIEW,
                    ),
                    wrapContentParams(),
                )
                addView(
                    backendButton(
                        id = R.id.open_android_compose,
                        label = getString(R.string.android_compose_backend),
                        backend = DemoBackend.COMPOSE,
                    ),
                    wrapContentParams(),
                )
            }
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, windowInsets ->
            val systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            windowInsets
        }
        setContentView(content)
    }

    private fun backendButton(
        id: Int,
        label: String,
        backend: DemoBackend,
    ): MaterialButton =
        MaterialButton(this).apply {
            this.id = id
            val horizontalPadding = (24 * resources.displayMetrics.density).toInt()
            val verticalPadding = (16 * resources.displayMetrics.density).toInt()
            text = label
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
            setOnClickListener {
                startActivity(DemoActivity.createIntent(this@MainActivity, backend))
            }
        }

    private fun wrapContentParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
}
