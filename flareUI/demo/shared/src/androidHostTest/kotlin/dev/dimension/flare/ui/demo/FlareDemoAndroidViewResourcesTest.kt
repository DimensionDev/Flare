@file:OptIn(
    dev.dimension.flare.ui.LowLevelFlareApi::class,
    dev.dimension.flare.ui.navigation.ExperimentalFlareNavigation::class,
)

package dev.dimension.flare.ui.demo

import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.navigation3.runtime.entryProvider
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.textview.MaterialTextView
import com.google.android.material.transition.MaterialSharedAxis
import dev.dimension.flare.ui.FlareModifier
import dev.dimension.flare.ui.FlareUiComposable
import dev.dimension.flare.ui.android.AndroidViewNavigationOwner
import dev.dimension.flare.ui.android.AndroidViewNavigationRendererPlugin
import dev.dimension.flare.ui.android.FlareAndroidViewHost
import dev.dimension.flare.ui.android.createAndroidWidgetSystem
import dev.dimension.flare.ui.foundation.Text
import dev.dimension.flare.ui.navigation.NavigationDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration
import kotlin.math.roundToInt
import com.google.android.material.R as MaterialR

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en")
@LooperMode(LooperMode.Mode.PAUSED)
public class FlareDemoAndroidViewResourcesTest {
    @Test
    public fun navigatesTheCatalogAndUpdatesEveryFeaturePage() {
        withAttachedHost { host ->
            val catalog = host.requireTaggedView<LinearLayout>("demo-catalog")
            val catalogImage = catalog.requireTaggedView<ShapeableImageView>("demo-catalog-image")
            assertEquals(64.dp(catalog), catalogImage.width)
            assertEquals(64.dp(catalog), catalogImage.height)
            assertEquals(
                "Flare UI Catalog",
                catalog.requireTaggedView<MaterialTextView>("demo-catalog-title").text.toString(),
            )

            catalog.requireTaggedView<MaterialButton>("demo-open-resources").performClick()
            idleMainThread()

            val resources = host.requireTaggedView<LinearLayout>("demo-resources")
            val image = resources.requireTaggedView<ShapeableImageView>("demo-image")
            val count = resources.requireTaggedView<MaterialTextView>("demo-count")
            val updates = resources.requireTaggedView<MaterialTextView>("demo-updates")
            val actions = resources.requireTaggedView<LinearLayout>("demo-actions")
            val increment = resources.requireTaggedView<MaterialButton>("demo-increment")

            assertNotNull(image.drawable)
            assertEquals("Flare resource image", image.contentDescription)
            assertEquals("Count: 0", count.text.toString())
            assertEquals("0 updates", updates.text.toString())
            assertEquals(Gravity.TOP or Gravity.START, resources.gravity)
            assertEquals(Gravity.START or Gravity.CENTER_VERTICAL, actions.gravity)
            assertEquals(12.dp(resources), resources.dividerDrawable.intrinsicHeight)
            assertEquals(12.dp(actions), actions.dividerDrawable.intrinsicWidth)

            increment.performClick()
            idleMainThread()

            assertSame(count, resources.requireTaggedView<MaterialTextView>("demo-count"))
            assertEquals("Count: 1", count.text.toString())
            assertEquals("1 update", updates.text.toString())

            resources.requireTaggedView<MaterialButton>("demo-back").performClick()
            idleMainThread()
            host.requireTaggedView<MaterialButton>("demo-open-lazy-layouts").performClick()
            idleMainThread()

            val lazyRow = host.requireTaggedView<RecyclerView>("demo-lazy-row")
            val lazyColumn = host.requireTaggedView<RecyclerView>("demo-lazy-column")
            assertEquals(50, checkNotNull(lazyRow.adapter).itemCount)
            assertEquals(10_000, checkNotNull(lazyColumn.adapter).itemCount)
        }
    }

    @Test
    @Config(qualifiers = "zh")
    public fun rendersChineseCatalogAndResources() {
        withAttachedHost { host ->
            assertEquals(
                "Flare UI 功能目录",
                host.requireTaggedView<MaterialTextView>("demo-catalog-title").text.toString(),
            )
            val openResources = host.requireTaggedView<MaterialButton>("demo-open-resources")
            assertEquals("基础组件与资源", openResources.text.toString())

            openResources.performClick()
            idleMainThread()

            assertEquals(
                "Flare UI 渲染运行时",
                host.requireTaggedView<MaterialTextView>("demo-title").text.toString(),
            )
            assertEquals(
                "计数：0",
                host.requireTaggedView<MaterialTextView>("demo-count").text.toString(),
            )
            assertEquals(
                "已更新 0 次",
                host.requireTaggedView<MaterialTextView>("demo-updates").text.toString(),
            )
            assertEquals(
                "增加",
                host.requireTaggedView<MaterialButton>("demo-increment").text.toString(),
            )
        }
    }

    @Test
    public fun rapidCatalogClicksCommitOnlyTheFirstDestination() {
        withAttachedHost { host ->
            val catalog = host.requireTaggedView<LinearLayout>("demo-catalog")
            val resources = catalog.requireTaggedView<MaterialButton>("demo-open-resources")
            val lazyLayouts = catalog.requireTaggedView<MaterialButton>("demo-open-lazy-layouts")

            resources.performClick()
            resources.performClick()
            lazyLayouts.performClick()
            idleMainThread()

            host.requireTaggedView<LinearLayout>("demo-resources")
            host.requireTaggedView<MaterialButton>("demo-back").performClick()
            idleMainThread()
            host.requireTaggedView<LinearLayout>("demo-catalog")
        }
    }

    @Test
    public fun programmaticPushUsesAForwardPageTransition() {
        withAttachedHost { activity, host ->
            val outgoing = activity.supportFragmentManager.fragments.single()
            host.requireTaggedView<MaterialButton>("demo-open-resources").performClick()

            idleMainThread()

            val incoming = activity.supportFragmentManager.fragments.single { it !== outgoing }
            val outgoingTransition = outgoing.exitTransition as MaterialSharedAxis
            val incomingTransition = incoming.enterTransition as MaterialSharedAxis
            assertEquals(MaterialSharedAxis.X, outgoingTransition.axis)
            assertEquals(MaterialSharedAxis.X, incomingTransition.axis)
            assertTrue(outgoingTransition.isForward)
            assertTrue(incomingTransition.isForward)
            assertNotNull(host.findViewWithTag<View>("demo-resources"))
        }
    }

    @Test
    public fun programmaticPopUsesABackwardPageTransition() {
        withAttachedHost { activity, host ->
            val incoming = activity.supportFragmentManager.fragments.single()
            host.requireTaggedView<MaterialButton>("demo-open-resources").performClick()
            idleMainThread()
            val outgoing = activity.supportFragmentManager.fragments.single { it !== incoming }

            host.requireTaggedView<MaterialButton>("demo-back").performClick()
            idleMainThread()

            val outgoingTransition = outgoing.exitTransition as MaterialSharedAxis
            val incomingTransition = incoming.enterTransition as MaterialSharedAxis
            assertEquals(MaterialSharedAxis.X, outgoingTransition.axis)
            assertEquals(MaterialSharedAxis.X, incomingTransition.axis)
            assertFalse(outgoingTransition.isForward)
            assertFalse(incomingTransition.isForward)
            assertNotNull(host.findViewWithTag<View>("demo-catalog"))
        }
    }

    @Test
    public fun systemBackReturnsToCatalog() {
        withAttachedHost { activity, host ->
            val incoming = activity.supportFragmentManager.fragments.single()
            host.requireTaggedView<MaterialButton>("demo-open-resources").performClick()
            idleMainThread()
            host.requireTaggedView<LinearLayout>("demo-resources")
            val outgoing = activity.supportFragmentManager.fragments.single { it !== incoming }

            activity.onBackPressedDispatcher.onBackPressed()
            activity.onBackPressedDispatcher.onBackPressed()
            idleMainThread()

            assertFalse((outgoing.exitTransition as MaterialSharedAxis).isForward)
            assertFalse((incoming.enterTransition as MaterialSharedAxis).isForward)
            assertFalse(activity.isFinishing)
            host.requireTaggedView<LinearLayout>("demo-catalog")
        }
    }

    @Test
    public fun reconstructionRebindsRetainedPagesToTheirNewFragmentViews() {
        val backStack = mutableStateListOf<ReconstructionRoute>(ReconstructionHome, ReconstructionDetail("first"))
        withAttachedHost(
            createHost = { activity ->
                FlareAndroidViewHost(
                    context = activity,
                    widgetSystem = createAndroidWidgetSystem(AndroidViewNavigationRendererPlugin),
                    nativeControllerOwner = AndroidViewNavigationOwner(activity),
                ).apply {
                    setContent {
                        val provider =
                            remember {
                                entryProvider<ReconstructionRoute> {
                                    entry<ReconstructionHome> {
                                        Text(
                                            "Home",
                                            FlareModifier(testTag = "reconstruction-home"),
                                        )
                                    }
                                    entry<ReconstructionDetail> { route ->
                                        Text(
                                            route.label,
                                            FlareModifier(testTag = "reconstruction-${route.label}"),
                                        )
                                    }
                                }
                            }
                        NavigationDisplay(
                            backStack = backStack,
                            onBack = { request -> request.applyTo(backStack) },
                            entryProvider = provider,
                        )
                    }
                }
            },
        ) { _, host ->
            host.requireTaggedView<MaterialTextView>("reconstruction-first")

            backStack[1] = ReconstructionDetail("replacement")
            idleMainThread()
            host.requireTaggedView<MaterialTextView>("reconstruction-replacement")

            backStack.removeAt(backStack.lastIndex)
            idleMainThread()
            assertEquals(
                "Home",
                host.requireTaggedView<MaterialTextView>("reconstruction-home").text.toString(),
            )
        }
    }

    @Test
    public fun disposingTheHostRemovesItsNavigationFragments() {
        withAttachedHost { activity, host ->
            host.requireTaggedView<MaterialButton>("demo-open-resources").performClick()
            idleMainThread()
            assertFalse(activity.supportFragmentManager.fragments.isEmpty())

            host.disposeComposition()
            idleMainThread()

            assertTrue(activity.supportFragmentManager.fragments.isEmpty())
        }
    }

    @Test
    public fun pushAndPopDeactivateAndReactivateTheFrozenPredecessor() {
        val activeEntries = mutableSetOf<String>()
        val realizationCounts = mutableMapOf<String, Int>()
        val disposalCounts = mutableMapOf<String, Int>()
        val backStack = mutableStateListOf<ViewLifecycleRoute>(ViewLifecycleHome)
        withAttachedHost(
            createHost = { activity ->
                FlareAndroidViewHost(
                    context = activity,
                    widgetSystem = createAndroidWidgetSystem(AndroidViewNavigationRendererPlugin),
                    nativeControllerOwner = AndroidViewNavigationOwner(activity),
                ).apply {
                    setContent {
                        val provider =
                            remember {
                                entryProvider<ViewLifecycleRoute> {
                                    entry<ViewLifecycleHome> {
                                        TrackedAndroidViewNavigationPage(
                                            label = "home",
                                            activeEntries = activeEntries,
                                            realizationCounts = realizationCounts,
                                            disposalCounts = disposalCounts,
                                        )
                                    }
                                    entry<ViewLifecycleMiddle> {
                                        TrackedAndroidViewNavigationPage(
                                            label = "middle",
                                            activeEntries = activeEntries,
                                            realizationCounts = realizationCounts,
                                            disposalCounts = disposalCounts,
                                        )
                                    }
                                }
                            }
                        NavigationDisplay(
                            backStack = backStack,
                            onBack = { request -> request.applyTo(backStack) },
                            entryProvider = provider,
                        )
                    }
                }
            },
        ) { _, _ ->
            backStack += ViewLifecycleMiddle
            idleMainThread()
            assertEquals(setOf("middle"), activeEntries)
            assertEquals(mapOf("home" to 1, "middle" to 1), realizationCounts)
            assertEquals(mapOf("home" to 1), disposalCounts)

            backStack.removeAt(backStack.lastIndex)
            idleMainThread()
            assertEquals(setOf("home"), activeEntries)
            assertEquals(mapOf("home" to 2, "middle" to 1), realizationCounts)
            assertEquals(mapOf("home" to 1, "middle" to 1), disposalCounts)
        }
    }

    @Test
    public fun sameTopologyModelDeliveryKeepsTransitionParticipantsActive() {
        val activeEntries = mutableSetOf<String>()
        val realizationCounts = mutableMapOf<String, Int>()
        val disposalCounts = mutableMapOf<String, Int>()
        val backStack = mutableStateListOf<ViewLifecycleRoute>(ViewLifecycleHome)
        val modelEpoch = mutableIntStateOf(0)
        var requestedRedelivery = false
        withAttachedHost(
            createHost = { activity ->
                FlareAndroidViewHost(
                    context = activity,
                    widgetSystem = createAndroidWidgetSystem(AndroidViewNavigationRendererPlugin),
                    nativeControllerOwner = AndroidViewNavigationOwner(activity),
                ).apply {
                    setContent {
                        val appliedEpoch = modelEpoch.intValue
                        val provider =
                            remember {
                                entryProvider<ViewLifecycleRoute> {
                                    entry<ViewLifecycleHome> {
                                        TrackedAndroidViewNavigationPage(
                                            label = "home",
                                            activeEntries = activeEntries,
                                            realizationCounts = realizationCounts,
                                            disposalCounts = disposalCounts,
                                        )
                                    }
                                    entry<ViewLifecycleMiddle> {
                                        TrackedAndroidViewNavigationPage(
                                            label = "middle",
                                            activeEntries = activeEntries,
                                            realizationCounts = realizationCounts,
                                            disposalCounts = disposalCounts,
                                            onRealized = {
                                                if (!requestedRedelivery) {
                                                    requestedRedelivery = true
                                                    modelEpoch.intValue += 1
                                                }
                                            },
                                        )
                                    }
                                }
                            }
                        NavigationDisplay(
                            backStack = backStack,
                            onBack = { request ->
                                check(appliedEpoch >= 0)
                                request.applyTo(backStack)
                            },
                            entryProvider = provider,
                        )
                    }
                }
            },
        ) { _, _ ->
            backStack += ViewLifecycleMiddle
            idleMainThread()

            assertTrue(requestedRedelivery)
            assertEquals(setOf("middle"), activeEntries)
            assertEquals(mapOf("home" to 1, "middle" to 1), realizationCounts)
            assertEquals(mapOf("home" to 1), disposalCounts)
        }
    }

    @Test
    public fun onlyTheVisibleAndroidViewPageKeepsItsEffectsActive() {
        val activeEntries = mutableSetOf<String>()
        val realizationCounts = mutableMapOf<String, Int>()
        val disposalCounts = mutableMapOf<String, Int>()
        val backStack =
            mutableStateListOf<ViewLifecycleRoute>(
                ViewLifecycleHome,
                ViewLifecycleMiddle,
                ViewLifecycleDetail,
            )
        withAttachedHost(
            createHost = { activity ->
                FlareAndroidViewHost(
                    context = activity,
                    widgetSystem = createAndroidWidgetSystem(AndroidViewNavigationRendererPlugin),
                    nativeControllerOwner = AndroidViewNavigationOwner(activity),
                ).apply {
                    setContent {
                        val provider =
                            remember {
                                entryProvider<ViewLifecycleRoute> {
                                    entry<ViewLifecycleHome> {
                                        TrackedAndroidViewNavigationPage(
                                            label = "home",
                                            activeEntries = activeEntries,
                                            realizationCounts = realizationCounts,
                                            disposalCounts = disposalCounts,
                                        )
                                    }
                                    entry<ViewLifecycleMiddle> {
                                        TrackedAndroidViewNavigationPage(
                                            label = "middle",
                                            activeEntries = activeEntries,
                                            realizationCounts = realizationCounts,
                                            disposalCounts = disposalCounts,
                                        )
                                    }
                                    entry<ViewLifecycleDetail> {
                                        TrackedAndroidViewNavigationPage(
                                            label = "detail",
                                            activeEntries = activeEntries,
                                            realizationCounts = realizationCounts,
                                            disposalCounts = disposalCounts,
                                        )
                                    }
                                }
                            }
                        NavigationDisplay(
                            backStack = backStack,
                            onBack = { request -> request.applyTo(backStack) },
                            entryProvider = provider,
                        )
                    }
                }
            },
        ) { _, _ ->
            assertEquals(setOf("detail"), activeEntries)
            assertEquals(mapOf("middle" to 1, "detail" to 1), realizationCounts)
            assertEquals(mapOf("middle" to 1), disposalCounts)

            backStack.removeAt(backStack.lastIndex)
            idleMainThread()
            assertEquals(setOf("middle"), activeEntries)
            assertEquals(
                mapOf("home" to 1, "middle" to 2, "detail" to 1),
                realizationCounts,
            )
            assertEquals(
                mapOf("home" to 1, "middle" to 1, "detail" to 1),
                disposalCounts,
            )

            backStack.removeAt(backStack.lastIndex)
            idleMainThread()
            assertEquals(setOf("home"), activeEntries)
            assertEquals(
                mapOf("home" to 2, "middle" to 2, "detail" to 1),
                realizationCounts,
            )
            assertEquals(
                mapOf("home" to 1, "middle" to 2, "detail" to 1),
                disposalCounts,
            )
        }
    }

    @Test
    public fun androidViewNavigationPreservesTheOuterPrimaryFragment() {
        lateinit var outerPrimary: Fragment
        withAttachedHost(
            createHost = { activity ->
                outerPrimary = Fragment()
                activity.supportFragmentManager
                    .beginTransaction()
                    .add(outerPrimary, "outer-primary")
                    .setPrimaryNavigationFragment(outerPrimary)
                    .commitNow()
                createAndroidViewDemoView(activity) as FlareAndroidViewHost
            },
        ) { activity, host ->
            assertSame(outerPrimary, activity.supportFragmentManager.primaryNavigationFragment)

            host.requireTaggedView<MaterialButton>("demo-open-resources").performClick()
            idleMainThread()
            assertSame(outerPrimary, activity.supportFragmentManager.primaryNavigationFragment)

            host.disposeComposition()
            idleMainThread()
            assertSame(outerPrimary, activity.supportFragmentManager.primaryNavigationFragment)
        }
    }

    @Test
    public fun multipleAndroidViewNavigationsDoNotClaimAPrimaryFragment() {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java)
        val activity = controller.get().apply { setTheme(MaterialR.style.Theme_Material3_DayNight) }
        controller.setup()
        val firstHost = createAndroidViewDemoView(activity) as FlareAndroidViewHost
        val secondHost = createAndroidViewDemoView(activity) as FlareAndroidViewHost
        val root = FrameLayout(activity)

        try {
            root.addView(
                firstHost,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            root.addView(
                secondHost,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            activity.setContentView(root)
            idleMainThread()

            firstHost.requireTaggedView<MaterialButton>("demo-open-resources").performClick()
            secondHost.requireTaggedView<MaterialButton>("demo-open-lazy-layouts").performClick()
            idleMainThread()

            assertNull(activity.supportFragmentManager.primaryNavigationFragment)
            firstHost.disposeComposition()
            idleMainThread()
            assertNull(activity.supportFragmentManager.primaryNavigationFragment)
            secondHost.requireTaggedView<LinearLayout>("demo-lazy-layouts")
        } finally {
            firstHost.disposeComposition()
            secondHost.disposeComposition()
            controller.pause().stop().destroy()
            idleMainThread()
        }
    }

    private fun withAttachedHost(block: (FlareAndroidViewHost) -> Unit) {
        withAttachedHost { _, host -> block(host) }
    }

    private fun withAttachedHost(block: (FragmentActivity, FlareAndroidViewHost) -> Unit) {
        withAttachedHost(
            createHost = { activity -> createAndroidViewDemoView(activity) as FlareAndroidViewHost },
            block = block,
        )
    }

    private fun withAttachedHost(
        createHost: (FragmentActivity) -> FlareAndroidViewHost,
        block: (FragmentActivity, FlareAndroidViewHost) -> Unit,
    ) {
        val controller = Robolectric.buildActivity(FragmentActivity::class.java)
        val activity = controller.get().apply { setTheme(MaterialR.style.Theme_Material3_DayNight) }
        controller.setup()
        val host = createHost(activity)

        try {
            activity.setContentView(host)
            idleMainThread()
            block(activity, host)
        } finally {
            host.disposeComposition()
            controller.pause().stop().destroy()
            idleMainThread()
        }
    }

    private fun idleMainThread() {
        idleMainThread(Duration.ofMillis(64))
    }

    private fun idleMainThread(duration: Duration) {
        shadowOf(Looper.getMainLooper()).idleFor(duration)
    }

    private fun <T : View> View.requireTaggedView(tag: String): T = checkNotNull(findViewWithTag<T>(tag)) { "No view has tag $tag." }

    private fun Int.dp(view: View): Int = (this * view.resources.displayMetrics.density).roundToInt()
}

private sealed interface ReconstructionRoute

private data object ReconstructionHome : ReconstructionRoute

private data class ReconstructionDetail(
    val label: String,
) : ReconstructionRoute

private sealed interface ViewLifecycleRoute

private data object ViewLifecycleHome : ViewLifecycleRoute

private data object ViewLifecycleMiddle : ViewLifecycleRoute

private data object ViewLifecycleDetail : ViewLifecycleRoute

@Composable
@FlareUiComposable
private fun TrackedAndroidViewNavigationPage(
    label: String,
    activeEntries: MutableSet<String>,
    realizationCounts: MutableMap<String, Int>,
    disposalCounts: MutableMap<String, Int>,
    onRealized: () -> Unit = {},
) {
    DisposableEffect(label) {
        activeEntries += label
        realizationCounts[label] = realizationCounts.getOrElse(label) { 0 } + 1
        onRealized()
        onDispose {
            activeEntries -= label
            disposalCounts[label] = disposalCounts.getOrElse(label) { 0 } + 1
        }
    }
    Text(
        text = label,
        modifier = FlareModifier(testTag = "lifecycle-$label"),
    )
}
