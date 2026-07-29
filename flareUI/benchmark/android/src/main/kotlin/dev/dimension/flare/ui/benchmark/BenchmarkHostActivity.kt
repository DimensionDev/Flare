package dev.dimension.flare.ui.benchmark

import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity

/** Attached View tree used by Compose UI microbenchmarks. */
public class BenchmarkHostActivity : ComponentActivity() {
    public lateinit var benchmarkContainer: FrameLayout
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        benchmarkContainer = FrameLayout(this)
        setContentView(benchmarkContainer)
    }
}
