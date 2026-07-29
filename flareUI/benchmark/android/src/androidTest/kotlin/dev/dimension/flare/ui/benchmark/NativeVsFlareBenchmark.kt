@file:OptIn(dev.dimension.flare.ui.LowLevelFlareApi::class)

package dev.dimension.flare.ui.benchmark

import android.content.Context
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeatedOnMainThread
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.dimension.flare.ui.FlareComposition
import dev.dimension.flare.ui.FlareContent
import dev.dimension.flare.ui.FlareUiComposable
import dev.dimension.flare.ui.android.AndroidViewBackend
import dev.dimension.flare.ui.android.AndroidViewChildren
import dev.dimension.flare.ui.android.createAndroidWidgetSystem
import dev.dimension.flare.ui.foundation.Column
import dev.dimension.flare.ui.foundation.Text
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * CPU microbenchmarks for equivalent, non-virtualized Android View trees.
 *
 * Both paths create the same FrameLayout -> vertical LinearLayout -> TextView hierarchy and force
 * an Android measure/layout pass. Warm mount keeps the Recomposer outside the measured block;
 * cold mount deliberately includes it. The update state is read in its own restart scope so one
 * changed label does not re-execute the surrounding 100-item loop.
 */
@RunWith(AndroidJUnit4::class)
public class NativeVsFlareBenchmark {
    @get:Rule
    public val benchmarkRule: BenchmarkRule = BenchmarkRule()

    private val context: Context =
        InstrumentationRegistry
            .getInstrumentation()
            .targetContext

    @Test
    public fun mount100Text_nativeViews() {
        benchmarkRule.measureRepeatedOnMainThread {
            lateinit var root: FrameLayout
            runWithMeasurementDisabled {
                root = FrameLayout(context)
            }

            mountNativeTextTree(root)
            measureAndLayout(root)

            runWithMeasurementDisabled {
                check(root.childCount == 1)
                check((root.getChildAt(0) as LinearLayout).childCount == ITEM_COUNT)
                root.removeAllViews()
            }
        }
    }

    @Test
    public fun mount100Text_flareUi_warmRuntime() {
        BenchmarkRecomposerRuntime().use { runtime ->
            benchmarkRule.measureRepeatedOnMainThread {
                lateinit var root: FrameLayout
                lateinit var host: FlareBenchmarkComposition
                runWithMeasurementDisabled {
                    root = FrameLayout(context)
                }

                host = FlareBenchmarkComposition(context, root, runtime)
                host.setContent {
                    Column {
                        INITIAL_ITEM_TEXTS.forEach { text ->
                            Text(text)
                        }
                    }
                }
                measureAndLayout(root)

                runWithMeasurementDisabled {
                    check(root.childCount == 1)
                    check((root.getChildAt(0) as LinearLayout).childCount == ITEM_COUNT)
                    host.close()
                }
            }
        }
    }

    @Test
    public fun mount100Text_flareUi_coldRuntime() {
        benchmarkRule.measureRepeatedOnMainThread {
            lateinit var root: FrameLayout
            lateinit var runtime: BenchmarkRecomposerRuntime
            lateinit var host: FlareBenchmarkComposition
            runWithMeasurementDisabled {
                root = FrameLayout(context)
            }

            runtime = BenchmarkRecomposerRuntime()
            host = FlareBenchmarkComposition(context, root, runtime)
            host.setContent {
                Column {
                    INITIAL_ITEM_TEXTS.forEach { text ->
                        Text(text)
                    }
                }
            }
            measureAndLayout(root)

            runWithMeasurementDisabled {
                check(root.childCount == 1)
                check((root.getChildAt(0) as LinearLayout).childCount == ITEM_COUNT)
                host.close()
                runtime.close()
            }
        }
    }

    @Test
    public fun updateOneOf100Text_nativeViews() {
        benchmarkRule.measureRepeatedOnMainThread {
            lateinit var root: FrameLayout
            lateinit var target: TextView
            runWithMeasurementDisabled {
                root = FrameLayout(context)
                mountNativeTextTree(root)
                measureAndLayout(root)
                target =
                    (root.getChildAt(0) as LinearLayout)
                        .getChildAt(UPDATED_INDEX) as TextView
            }

            target.text = UPDATED_TEXT
            measureAndLayout(root)

            runWithMeasurementDisabled {
                check(target.text.toString() == UPDATED_TEXT)
                root.removeAllViews()
            }
        }
    }

    @Test
    public fun updateOneOf100Text_flareUi() {
        BenchmarkRecomposerRuntime().use { runtime ->
            benchmarkRule.measureRepeatedOnMainThread {
                lateinit var root: FrameLayout
                lateinit var host: FlareBenchmarkComposition
                lateinit var updatedText: MutableState<String>
                lateinit var target: TextView
                runWithMeasurementDisabled {
                    root = FrameLayout(context)
                    updatedText = mutableStateOf(INITIAL_TEXT)
                    host = FlareBenchmarkComposition(context, root, runtime)
                    host.setContent {
                        Column {
                            INITIAL_ITEM_TEXTS.forEachIndexed { index, text ->
                                if (index == UPDATED_INDEX) {
                                    StatefulFlareText(updatedText)
                                } else {
                                    Text(text)
                                }
                            }
                        }
                    }
                    measureAndLayout(root)
                    target =
                        (root.getChildAt(0) as LinearLayout)
                            .getChildAt(UPDATED_INDEX) as TextView
                }

                updatedText.value = UPDATED_TEXT
                runtime.awaitIdle()
                measureAndLayout(root)

                runWithMeasurementDisabled {
                    check(target.text.toString() == UPDATED_TEXT)
                    host.close()
                }
            }
        }
    }

    private fun mountNativeTextTree(root: FrameLayout) {
        val column =
            LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
        INITIAL_ITEM_TEXTS.forEach { text ->
            column.addView(TextView(context).apply { this.text = text })
        }
        root.addView(column)
    }

    @Composable
    @FlareUiComposable
    private fun StatefulFlareText(text: MutableState<String>) {
        Text(text.value)
    }
}

private class FlareBenchmarkComposition(
    context: Context,
    root: FrameLayout,
    private val runtime: BenchmarkRecomposerRuntime,
) : AutoCloseable {
    private val composition =
        FlareComposition(
            root = AndroidViewChildren(root),
            widgetSystem = widgetSystem,
            backend = AndroidViewBackend(context),
            parent = runtime.recomposer,
        )

    fun setContent(content: FlareContent) {
        composition.setContent(content)
        runtime.awaitIdle()
    }

    override fun close() {
        composition.dispose()
    }

    private companion object {
        val widgetSystem = createAndroidWidgetSystem()
    }
}
