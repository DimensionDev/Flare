@file:OptIn(
    ExperimentalFlareNavigation::class,
    dev.dimension.flare.ui.LowLevelFlareApi::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package dev.dimension.flare.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavEntry
import dev.dimension.flare.ui.EmitFlareWidget
import dev.dimension.flare.ui.FlareChildren
import dev.dimension.flare.ui.FlareContent
import dev.dimension.flare.ui.FlareRendererPlugin
import dev.dimension.flare.ui.FlareSubcomposition
import dev.dimension.flare.ui.FlareSubcompositionFactory
import dev.dimension.flare.ui.FlareUiComposable
import dev.dimension.flare.ui.FlareWidget
import dev.dimension.flare.ui.FlareWidgetRegistrar
import dev.dimension.flare.ui.FlareWidgetSystem
import dev.dimension.flare.ui.uikit.AbstractUIKitWidget
import dev.dimension.flare.ui.uikit.FlareUIKitHost
import dev.dimension.flare.ui.uikit.UIKitBackend
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import platform.CoreFoundation.CFRunLoopRunInMode
import platform.CoreFoundation.kCFRunLoopDefaultMode
import platform.CoreGraphics.CGRectMake
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIColor
import platform.UIKit.UILabel
import platform.UIKit.UILayoutConstraintAxisHorizontal
import platform.UIKit.UINavigationController
import platform.UIKit.UIStackView
import platform.UIKit.UIStackViewAlignmentTop
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.childViewControllers
import platform.UIKit.systemBackgroundColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

public class UIKitNavigationRendererTest {
    @Test
    public fun deepStackKeepsOnlyCurrentAndImmediatePredecessorMaterialized() {
        val fixture = UIKitNavigationFixture()

        try {
            fixture.dispatch(listOf(fixture.home, fixture.detail, fixture.editor, fixture.settings))
            awaitUIKitNavigation("UIKit navigation did not reconstruct the deep Page stack.") {
                fixture.navigationController.viewControllers.size == 4
            }

            assertEquals(2, fixture.subcompositions.created)
            assertEquals(2, fixture.subcompositions.installed)
            assertEquals(1, fixture.subcompositions.deactivated)
            assertEquals(0, fixture.subcompositions.disposed)
        } finally {
            fixture.dispose()
        }

        assertEquals(2, fixture.subcompositions.disposed)
        assertEquals(0, fixture.parent.childViewControllers.size)
    }

    @Test
    public fun factoryRebindRestoresActiveFrozenAndReleasedPagesWithTheNewFactory() {
        val fixture = UIKitNavigationFixture()
        val replacement = UIKitRecordingSubcompositionFactory()
        val reboundHome = uikitEntry("home")
        val reboundDetail = uikitEntry("detail")
        val reboundEditor = uikitEntry("editor")

        try {
            fixture.dispatch(listOf(fixture.home, fixture.detail, fixture.editor))
            awaitUIKitNavigation("UIKit navigation did not apply the initial retention policy.") {
                fixture.navigationController.viewControllers.size == 3 &&
                    fixture.subcompositions.created == 2
            }
            fixture.subcompositions.disposeOwnedCompositions()

            fixture.dispatch(
                entries = listOf(reboundHome, reboundDetail, reboundEditor),
                subcompositions = replacement,
            )
            awaitUIKitNavigation("UIKit navigation did not restore retained content with the new factory.") {
                replacement.created == 2 && replacement.deactivated == 1
            }

            assertEquals(2, fixture.subcompositions.disposed)
            assertEquals(2, replacement.installed)
            assertEquals(0, replacement.disposed)

            fixture.dispatch(listOf(reboundHome), replacement)
            awaitUIKitNavigation("UIKit navigation did not realize the released root with the new factory.") {
                fixture.navigationController.viewControllers.size == 1 && replacement.created == 3
            }
        } finally {
            fixture.dispose()
        }
    }

    @Test
    public fun programmaticPushAndPopApplyContentRetentionPolicy() {
        val fixture = UIKitNavigationFixture()

        try {
            fixture.dispatch(listOf(fixture.home))
            awaitUIKitNavigation("UIKit navigation did not install its root Page.") {
                fixture.navigationController.viewControllers.size == 1 &&
                    fixture.subcompositions.created == 1
            }

            fixture.dispatch(listOf(fixture.home, fixture.detail))
            awaitUIKitNavigation("UIKit navigation did not finish the Page push.") {
                fixture.navigationController.viewControllers.size == 2 &&
                    fixture.navigationController.topEntryIdentity == fixture.detail.identity() &&
                    fixture.subcompositions.deactivated == 1
            }

            assertEquals(2, fixture.subcompositions.created)
            assertEquals(2, fixture.subcompositions.installed)
            assertEquals(0, fixture.subcompositions.disposed)

            fixture.dispatch(listOf(fixture.home))
            awaitUIKitNavigation("UIKit navigation did not finish the Page pop.") {
                fixture.navigationController.viewControllers.size == 1 &&
                    fixture.navigationController.topEntryIdentity == fixture.home.identity() &&
                    fixture.subcompositions.disposed == 1
            }

            assertEquals(2, fixture.subcompositions.created)
            assertEquals(3, fixture.subcompositions.installed)
            assertEquals(1, fixture.subcompositions.deactivated)
        } finally {
            fixture.dispose()
        }
    }

    @Test
    public fun interactivePopCancelRefreezesAndCommitReleasesSource() {
        val fixture = UIKitNavigationFixture()

        try {
            fixture.dispatch(listOf(fixture.home, fixture.detail))
            awaitUIKitNavigation("UIKit navigation did not reconstruct the interactive Page stack.") {
                fixture.navigationController.viewControllers.size == 2 &&
                    fixture.subcompositions.deactivated == 1
            }
            val homeController = fixture.navigationController.viewControllers.first() as UIViewController

            assertNotNull(fixture.widget.beginUserPop(homeController))
            assertEquals(3, fixture.subcompositions.installed)
            fixture.widget.finishUserPop(cancelled = true)

            assertEquals(0, fixture.backRequestCount)
            assertEquals(2, fixture.navigationController.viewControllers.size)
            assertEquals(2, fixture.subcompositions.deactivated)
            assertEquals(0, fixture.subcompositions.disposed)

            assertNotNull(fixture.widget.beginUserPop(homeController))
            assertEquals(4, fixture.subcompositions.installed)
            fixture.navigationController.setViewControllers(listOf(homeController), animated = false)
            fixture.widget.finishUserPop(cancelled = false)
            awaitUIKitNavigation("UIKit navigation did not acknowledge the committed native pop.") {
                fixture.backRequestCount == 1 &&
                    fixture.navigationController.viewControllers.size == 1 &&
                    fixture.subcompositions.disposed == 1
            }

            assertEquals(fixture.home.identity(), fixture.navigationController.topEntryIdentity)
        } finally {
            fixture.dispose()
        }
    }

    @Test
    public fun interactiveCancelCompletionAndDidShowOrdersAreIdempotent() {
        listOf(true, false).forEach { completionFirst ->
            val fixture = UIKitNavigationFixture()
            try {
                fixture.dispatch(listOf(fixture.home, fixture.detail))
                awaitUIKitNavigation("UIKit navigation did not install the interactive Page stack.") {
                    fixture.navigationController.viewControllers.size == 2 &&
                        fixture.subcompositions.deactivated == 1
                }
                val target = fixture.navigationController.viewControllers.first() as UIViewController
                val shownAfterCancellation = fixture.navigationController.viewControllers.last() as UIViewController
                assertNotNull(fixture.widget.beginUserPop(target))

                if (completionFirst) {
                    fixture.widget.finishUserPop(cancelled = true)
                    fixture.widget.finishUserPopIfNeeded(shownAfterCancellation)
                } else {
                    fixture.widget.finishUserPopIfNeeded(shownAfterCancellation)
                    fixture.widget.finishUserPop(cancelled = true)
                }

                assertEquals(0, fixture.backRequestCount)
                assertEquals(2, fixture.subcompositions.deactivated)
                assertEquals(0, fixture.subcompositions.disposed)
                assertNotNull(fixture.widget.beginUserPop(target))
                fixture.widget.finishUserPop(cancelled = true)
            } finally {
                fixture.dispose()
            }
        }
    }

    @Test
    public fun stagedModelBlocksInteractivePopAgainstStaleTopology() {
        val fixture = UIKitNavigationFixture()

        try {
            fixture.dispatch(listOf(fixture.home, fixture.detail))
            awaitUIKitNavigation("UIKit navigation did not install the initial Page stack.") {
                fixture.navigationController.viewControllers.size == 2
            }
            fixture.stage(listOf(fixture.home))

            assertNull(
                fixture.widget.beginUserPop(
                    fixture.navigationController.viewControllers.first() as UIViewController,
                ),
            )
        } finally {
            fixture.dispose()
        }
    }

    @Test
    public fun rejectedNativePopRecoversAndDoesNotLeaveCoordinatorExecuting() {
        val rejectingNavigationController = RejectingPopNavigationController()
        val fixture = UIKitNavigationFixture(navigationController = rejectingNavigationController)

        try {
            fixture.dispatch(listOf(fixture.home, fixture.detail))
            awaitUIKitNavigation("UIKit navigation did not install the initial Page stack.") {
                rejectingNavigationController.viewControllers.size == 2
            }

            fixture.dispatch(listOf(fixture.home))
            awaitUIKitNavigation("UIKit navigation did not reconstruct after a rejected pop.") {
                rejectingNavigationController.popAttempts == 1 &&
                    rejectingNavigationController.viewControllers.size == 1 &&
                    rejectingNavigationController.topEntryIdentity == fixture.home.identity()
            }

            fixture.dispatch(listOf(fixture.home, fixture.editor))
            awaitUIKitNavigation("UIKit navigation stayed stuck after a rejected pop.") {
                rejectingNavigationController.viewControllers.size == 2 &&
                    rejectingNavigationController.topEntryIdentity == fixture.editor.identity()
            }
        } finally {
            fixture.dispose()
        }
    }

    @Test
    public fun pageEntryRootPreservesWrapHeightOnItsCrossAxis() {
        val parent = UIViewController()
        val window = UIWindow(frame = CGRectMake(0.0, 0.0, 320.0, 640.0))
        val host =
            FlareUIKitHost(
                widgetSystem =
                    FlareWidgetSystem(
                        UIKitNavigationRendererPlugin,
                        wrapHeightLabelPlugin,
                    ),
                nativeControllerOwner = UIKitNavigationOwner(parent),
            )

        try {
            host.setContent {
                NavigationDisplay(
                    entries =
                        listOf(
                            NavEntry(
                                key = "home",
                                contentKey = "home",
                            ) {
                                WrapHeightLabel()
                            },
                        ),
                    onBack = {},
                )
            }
            window.rootViewController = parent
            host.view.translatesAutoresizingMaskIntoConstraints = false
            parent.view.addSubview(host.view)
            NSLayoutConstraint.activateConstraints(
                listOf(
                    host.view.leadingAnchor.constraintEqualToAnchor(parent.view.leadingAnchor),
                    host.view.trailingAnchor.constraintEqualToAnchor(parent.view.trailingAnchor),
                    host.view.topAnchor.constraintEqualToAnchor(parent.view.topAnchor),
                    host.view.bottomAnchor.constraintEqualToAnchor(parent.view.bottomAnchor),
                ),
            )
            window.hidden = false

            awaitUIKitNavigation("UIKit navigation did not create its Page controller.") {
                parent.childViewControllers
                    .filterIsInstance<UINavigationController>()
                    .singleOrNull()
                    ?.topViewController != null
            }

            val navigationController =
                parent.childViewControllers
                    .filterIsInstance<UINavigationController>()
                    .single()
            val entryRoot = navigationController.topViewController?.view as UIStackView
            parent.view.layoutIfNeeded()
            navigationController.view.layoutIfNeeded()
            entryRoot.layoutIfNeeded()

            val label = entryRoot.arrangedSubviews.single() as UILabel
            assertEquals(UILayoutConstraintAxisHorizontal, entryRoot.axis)
            assertEquals(UIStackViewAlignmentTop, entryRoot.alignment)
            val labelHeight = label.frame.useContents { size.height }
            val entryHeight = entryRoot.bounds.useContents { size.height }
            assertTrue(
                labelHeight < entryHeight,
                "A wrap-height Page must not be stretched to the entry controller height.",
            )
        } finally {
            host.dispose()
            window.hidden = true
        }
    }

    @Test
    public fun pageControllerUsesTheDynamicSystemBackground() {
        val parent = UIViewController()
        val window = UIWindow(frame = CGRectMake(0.0, 0.0, 320.0, 640.0))
        val host =
            FlareUIKitHost(
                widgetSystem = FlareWidgetSystem<UIKitBackend>(UIKitNavigationRendererPlugin),
                nativeControllerOwner = UIKitNavigationOwner(parent),
            )

        try {
            host.setContent {
                NavigationDisplay(
                    entries =
                        listOf(
                            NavEntry(
                                key = "home",
                                contentKey = "home",
                            ) {},
                        ),
                    onBack = {},
                )
            }
            window.rootViewController = parent
            parent.view.addSubview(host.view)
            window.hidden = false

            awaitUIKitNavigation("UIKit navigation did not create its Page controller.") {
                parent.childViewControllers
                    .filterIsInstance<UINavigationController>()
                    .singleOrNull()
                    ?.topViewController != null
            }

            val navigationController =
                parent.childViewControllers
                    .filterIsInstance<UINavigationController>()
                    .single()
            val backgroundColor = navigationController.topViewController?.view?.backgroundColor
            assertTrue(
                backgroundColor?.isEqual(UIColor.systemBackgroundColor) == true,
                "Every UIKit Page controller must provide the dynamic system background surface.",
            )
        } finally {
            host.dispose()
            window.hidden = true
        }
    }
}

private class UIKitNavigationFixture(
    val navigationController: UINavigationController = UINavigationController(),
) {
    val parent: UIViewController = UIViewController()
    val widget: UIKitNavigationWidget = UIKitNavigationWidget(navigationController)
    val home: ResolvedNavigationEntry = uikitEntry("home")
    val detail: ResolvedNavigationEntry = uikitEntry("detail")
    val editor: ResolvedNavigationEntry = uikitEntry("editor")
    val settings: ResolvedNavigationEntry = uikitEntry("settings")
    val subcompositions = UIKitRecordingSubcompositionFactory()
    var backRequestCount: Int = 0
        private set

    private val dispatcher = NavigationModelDispatcher()
    private val stagingScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val window = UIWindow(frame = CGRectMake(0.0, 0.0, 320.0, 640.0))
    private var currentEntries: List<ResolvedNavigationEntry> = emptyList()
    private var currentSubcompositions: FlareSubcompositionFactory = subcompositions

    init {
        // These tests exercise command/delegate ordering and content retention, not Core Animation.
        // Disabling the animation clock keeps native transitions deterministic in the CLI runner.
        UIView.setAnimationsEnabled(false)
        window.rootViewController = parent
        widget.view.translatesAutoresizingMaskIntoConstraints = false
        parent.view.addSubview(widget.view)
        NSLayoutConstraint.activateConstraints(
            listOf(
                widget.view.leadingAnchor.constraintEqualToAnchor(parent.view.leadingAnchor),
                widget.view.trailingAnchor.constraintEqualToAnchor(parent.view.trailingAnchor),
                widget.view.topAnchor.constraintEqualToAnchor(parent.view.topAnchor),
                widget.view.bottomAnchor.constraintEqualToAnchor(parent.view.bottomAnchor),
            ),
        )
        window.hidden = false
        widget.setModelDispatcher(dispatcher)
    }

    fun dispatch(
        entries: List<ResolvedNavigationEntry>,
        subcompositions: FlareSubcompositionFactory = currentSubcompositions,
    ) {
        currentEntries = entries
        currentSubcompositions = subcompositions
        dispatcher.dispatch(model(entries, subcompositions))
    }

    fun stage(entries: List<ResolvedNavigationEntry>) {
        currentEntries = entries
        dispatcher.stage(model(entries, currentSubcompositions), stagingScope)
    }

    fun dispose() {
        widget.dispose()
        stagingScope.cancel()
        window.hidden = true
        UIView.setAnimationsEnabled(true)
    }

    private fun model(
        entries: List<ResolvedNavigationEntry>,
        subcompositions: FlareSubcompositionFactory,
    ): NavigationModel =
        NavigationModel(
            entries = entries,
            onBack = { request ->
                backRequestCount += 1
                request.accept()
                dispatch(currentEntries.dropLast(request.popCount), currentSubcompositions)
            },
            subcompositions = subcompositions,
            nativeControllerOwner = UIKitNavigationOwner(parent),
        )
}

private val UINavigationController.topEntryIdentity: NavigationEntryIdentity?
    get() = (topViewController as? UIKitNavigationEntryController)?.identity

private class RejectingPopNavigationController : UINavigationController(nibName = null, bundle = null) {
    var popAttempts: Int = 0
        private set

    @ObjCSignatureOverride
    override fun popViewControllerAnimated(animated: Boolean): UIViewController? {
        popAttempts += 1
        return null
    }
}

private fun uikitEntry(contentKey: String): ResolvedNavigationEntry =
    ResolvedNavigationEntry(
        contentKey = contentKey,
        presentation = NavigationPresentation.Page,
        entry =
            NavEntry(
                key = contentKey,
                contentKey = contentKey,
            ) {},
    )

private class UIKitRecordingSubcompositionFactory : FlareSubcompositionFactory {
    private val compositions = mutableListOf<UIKitRecordingSubcomposition>()
    private var disposedFactory: Boolean = false
    var created: Int = 0
        private set
    var disposed: Int = 0
        private set
    var deactivated: Int = 0
        private set
    var installed: Int = 0
        private set

    override fun create(root: FlareChildren): FlareSubcomposition {
        check(!disposedFactory) { "UIKit test subcomposition factory is already disposed." }
        created += 1
        return UIKitRecordingSubcomposition(
            onInstalled = { installed += 1 },
            onDeactivated = { deactivated += 1 },
            onDisposed = { disposed += 1 },
        ).also(compositions::add)
    }

    fun disposeOwnedCompositions() {
        disposedFactory = true
        compositions.forEach(UIKitRecordingSubcomposition::dispose)
    }
}

private class UIKitRecordingSubcomposition(
    private val onInstalled: () -> Unit,
    private val onDeactivated: () -> Unit,
    private val onDisposed: () -> Unit,
) : FlareSubcomposition {
    private var disposed: Boolean = false

    override fun setContent(content: FlareContent) {
        check(!disposed) { "UIKit test subcomposition is already disposed." }
        onInstalled()
    }

    override fun deactivate() {
        check(!disposed) { "UIKit test subcomposition is already disposed." }
        onDeactivated()
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        onDisposed()
    }
}

private interface WrapHeightLabelWidget : FlareWidget

@Composable
@FlareUiComposable
private fun WrapHeightLabel() {
    EmitFlareWidget(componentType = WrapHeightLabelWidget::class)
}

private val wrapHeightLabelPlugin =
    object : FlareRendererPlugin<UIKitBackend> {
        override fun register(registrar: FlareWidgetRegistrar<UIKitBackend>) {
            registrar.register(WrapHeightLabelWidget::class) {
                object :
                    AbstractUIKitWidget<UILabel>(
                        UILabel().apply {
                            text = "Wrap height"
                        },
                    ),
                    WrapHeightLabelWidget {}
            }
        }
    }

private fun awaitUIKitNavigation(
    message: String,
    condition: () -> Boolean,
) {
    val startedAt = TimeSource.Monotonic.markNow()
    while (!condition() && startedAt.elapsedNow() < 5.seconds) {
        CFRunLoopRunInMode(kCFRunLoopDefaultMode, 0.01, true)
    }
    check(condition()) { message }
}
