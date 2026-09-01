package dev.dimension.flare.flareui.demo.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import dev.dimension.flare.ui.demo.createAndroidComposeDemoView
import dev.dimension.flare.ui.demo.createAndroidViewDemoView

public enum class DemoBackend(
    public val intentValue: String,
) {
    ANDROID_VIEW("android-view"),
    COMPOSE("compose"),
    ;

    public companion object {
        internal fun fromIntent(intent: Intent): DemoBackend {
            val intentValue = intent.getStringExtra(DemoActivity.EXTRA_BACKEND)
            return entries.firstOrNull { it.intentValue == intentValue }
                ?: error("Missing or invalid demo backend: $intentValue")
        }
    }
}

public class DemoActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        when (DemoBackend.fromIntent(intent)) {
            DemoBackend.ANDROID_VIEW -> {
                title = getString(R.string.demo_title_android_view)
                setContentView(createAndroidViewDemoView(this))
            }

            DemoBackend.COMPOSE -> {
                title = getString(R.string.demo_title_android_compose)
                setContentView(createAndroidComposeDemoView(this))
            }
        }
    }

    public companion object {
        internal const val EXTRA_BACKEND: String =
            "dev.dimension.flare.flareui.demo.android.extra.BACKEND"

        public fun createIntent(
            context: Context,
            backend: DemoBackend,
        ): Intent = Intent(context, DemoActivity::class.java).putExtra(EXTRA_BACKEND, backend.intentValue)
    }
}
