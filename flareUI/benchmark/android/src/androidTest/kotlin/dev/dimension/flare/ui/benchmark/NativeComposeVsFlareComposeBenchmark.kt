@file:OptIn(dev.dimension.flare.ui.LowLevelFlareApi::class)

package dev.dimension.flare.ui.benchmark

import android.content.Intent
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeatedOnMainThread
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.dimension.flare.ui.FlareUiComposable
import dev.dimension.flare.ui.compose.FlareComposeHost
import dev.dimension.flare.ui.compose.createAndroidComposeWidgetSystem
import dev.dimension.flare.ui.foundation.Text
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.compose.foundation.layout.Column as ComposeColumn
import dev.dimension.flare.ui.foundation.Column as FlareColumn

/**
 * CPU microbenchmarks for equivalent Compose UI trees.
 *
 * Both paths reuse one parent Recomposer and mount into an attached ComposeView. Flare therefore
 * measures only its additional composition/widget-node layer, not a second cold runtime. Update
 * state is read by one dedicated restart scope on both paths.
 */
@RunWith(AndroidJUnit4::class)
public class NativeComposeVsFlareComposeBenchmark {
    @get:Rule
    public val benchmarkRule: BenchmarkRule = BenchmarkRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var scenario: ActivityScenario<BenchmarkHostActivity>
    private lateinit var activity: BenchmarkHostActivity

    @Before
    public fun launchActivity() {
        val intent =
            Intent(
                instrumentation.targetContext,
                BenchmarkHostActivity::class.java,
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        scenario = ActivityScenario.launch(intent)
        scenario.onActivity {
            activity = it
        }
    }

    @After
    public fun closeActivity() {
        scenario.close()
    }

    @Test
    public fun mount100Text_nativeCompose_warmRuntime() {
        BenchmarkRecomposerRuntime().use { runtime ->
            benchmarkRule.measureRepeatedOnMainThread {
                lateinit var root: ComposeView
                runWithMeasurementDisabled {
                    root = ComposeView(activity)
                    root.setParentCompositionContext(runtime.recomposer)
                }

                root.setContent {
                    Box {
                        ComposeColumn {
                            INITIAL_ITEM_TEXTS.forEach { text ->
                                BasicText(text)
                            }
                        }
                    }
                }
                activity.benchmarkContainer.addView(root)
                runtime.awaitIdle()
                measureAndLayout(root)

                runWithMeasurementDisabled {
                    check(root.hasComposition)
                    check(root.measuredHeight > 0)
                    root.disposeComposition()
                    activity.benchmarkContainer.removeView(root)
                }
            }
        }
    }

    @Test
    public fun mount100Text_flareCompose_warmRuntime() {
        BenchmarkRecomposerRuntime().use { runtime ->
            benchmarkRule.measureRepeatedOnMainThread {
                lateinit var root: ComposeView
                runWithMeasurementDisabled {
                    root = ComposeView(activity)
                    root.setParentCompositionContext(runtime.recomposer)
                }

                root.setContent {
                    FlareComposeHost(widgetSystem = widgetSystem) {
                        FlareColumn {
                            INITIAL_ITEM_TEXTS.forEach { text ->
                                Text(text)
                            }
                        }
                    }
                }
                activity.benchmarkContainer.addView(root)
                runtime.awaitIdle()
                measureAndLayout(root)

                runWithMeasurementDisabled {
                    check(root.hasComposition)
                    check(root.measuredHeight > 0)
                    root.disposeComposition()
                    activity.benchmarkContainer.removeView(root)
                }
            }
        }
    }

    @Test
    public fun updateOneOf100Text_nativeCompose() {
        BenchmarkRecomposerRuntime().use { runtime ->
            benchmarkRule.measureRepeatedOnMainThread {
                lateinit var root: ComposeView
                lateinit var updatedText: MutableState<String>
                lateinit var probe: RenderProbe
                runWithMeasurementDisabled {
                    updatedText = mutableStateOf(INITIAL_TEXT)
                    probe = RenderProbe()
                    root =
                        ComposeView(activity).apply {
                            setParentCompositionContext(runtime.recomposer)
                            setContent {
                                Box {
                                    ComposeColumn {
                                        INITIAL_ITEM_TEXTS.forEachIndexed { index, text ->
                                            if (index == UPDATED_INDEX) {
                                                StatefulComposeText(updatedText, probe)
                                            } else {
                                                BasicText(text)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    activity.benchmarkContainer.addView(root)
                    runtime.awaitIdle()
                    measureAndLayout(root)
                    check(probe.text == INITIAL_TEXT)
                }

                updatedText.value = UPDATED_TEXT
                runtime.awaitIdle()
                measureAndLayout(root)

                runWithMeasurementDisabled {
                    check(probe.text == UPDATED_TEXT)
                    root.disposeComposition()
                    activity.benchmarkContainer.removeView(root)
                }
            }
        }
    }

    @Test
    public fun updateOneOf100Text_flareCompose() {
        BenchmarkRecomposerRuntime().use { runtime ->
            benchmarkRule.measureRepeatedOnMainThread {
                lateinit var root: ComposeView
                lateinit var updatedText: MutableState<String>
                lateinit var probe: RenderProbe
                runWithMeasurementDisabled {
                    updatedText = mutableStateOf(INITIAL_TEXT)
                    probe = RenderProbe()
                    root =
                        ComposeView(activity).apply {
                            setParentCompositionContext(runtime.recomposer)
                            setContent {
                                FlareComposeHost(widgetSystem = widgetSystem) {
                                    FlareColumn {
                                        INITIAL_ITEM_TEXTS.forEachIndexed { index, text ->
                                            if (index == UPDATED_INDEX) {
                                                StatefulFlareText(updatedText, probe)
                                            } else {
                                                Text(text)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    activity.benchmarkContainer.addView(root)
                    runtime.awaitIdle()
                    measureAndLayout(root)
                    check(probe.text == INITIAL_TEXT)
                }

                updatedText.value = UPDATED_TEXT
                runtime.awaitIdle()
                measureAndLayout(root)

                runWithMeasurementDisabled {
                    check(probe.text == UPDATED_TEXT)
                    root.disposeComposition()
                    activity.benchmarkContainer.removeView(root)
                }
            }
        }
    }

    @Composable
    private fun StatefulComposeText(
        text: MutableState<String>,
        probe: RenderProbe,
    ) {
        val current = text.value
        probe.text = current
        BasicText(current)
    }

    @Composable
    @FlareUiComposable
    private fun StatefulFlareText(
        text: MutableState<String>,
        probe: RenderProbe,
    ) {
        val current = text.value
        probe.text = current
        Text(current)
    }

    private class RenderProbe {
        var text: String = ""
    }

    private companion object {
        val widgetSystem = createAndroidComposeWidgetSystem()
    }
}
