@file:OptIn(
    dev.dimension.flare.ui.LowLevelFlareApi::class,
    dev.dimension.flare.ui.navigation.ExperimentalFlareNavigation::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package dev.dimension.flare.ui.appkit

import androidx.navigation3.runtime.NavEntry
import dev.dimension.flare.ui.FlareChildren
import dev.dimension.flare.ui.FlareContent
import dev.dimension.flare.ui.FlareSubcomposition
import dev.dimension.flare.ui.FlareSubcompositionFactory
import dev.dimension.flare.ui.navigation.NavigationModel
import dev.dimension.flare.ui.navigation.NavigationModelDispatcher
import dev.dimension.flare.ui.navigation.NavigationPresentation
import dev.dimension.flare.ui.navigation.ResolvedNavigationEntry
import kotlinx.cinterop.useContents
import platform.AppKit.NSApplication
import platform.AppKit.NSBackingStoreBuffered
import platform.AppKit.NSUserInterfaceLayoutDirectionRightToLeft
import platform.AppKit.NSView
import platform.AppKit.NSViewController
import platform.AppKit.NSWindow
import platform.AppKit.NSWindowStyleMaskBorderless
import platform.AppKit.NSWorkspace
import platform.AppKit.accessibilityDisplayShouldReduceMotion
import platform.AppKit.addChildViewController
import platform.AppKit.childViewControllers
import platform.AppKit.parentViewController
import platform.CoreFoundation.CFRunLoopRunInMode
import platform.CoreFoundation.kCFRunLoopDefaultMode
import platform.CoreGraphics.CGRectMake
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

public class AppKitNavigationTransitionTest {
    @Test
    public fun defaultProgrammaticPushProducesParallaxPresentationFrames() {
        val fixture = AppKitTransitionFixture()

        try {
            if (NSWorkspace.sharedWorkspace.accessibilityDisplayShouldReduceMotion) return
            fixture.dispatch(listOf(fixture.home))
            fixture.showWindow()
            CFRunLoopRunInMode(kCFRunLoopDefaultMode, 0.02, false)
            val container = fixture.parent.childViewControllers.single() as NSViewController
            val outgoing = container.childViewControllers.single() as NSViewController
            val width = container.view.bounds.useContents { size.width }

            fixture.dispatch(listOf(fixture.home, fixture.detail))
            assertEquals(2, container.childViewControllers.size)
            val incoming = container.childViewControllers.last() as NSViewController
            val observedPositions = mutableListOf<Pair<Double?, Double?>>()
            val startedAt = TimeSource.Monotonic.markNow()
            var intermediatePosition: Pair<Double, Double>? = null
            while (
                intermediatePosition == null &&
                outgoing.view.superview != null &&
                startedAt.elapsedNow() < 2.seconds
            ) {
                CFRunLoopRunInMode(kCFRunLoopDefaultMode, 1.0 / 240.0, false)
                val outgoingX = outgoing.view.presentationX()
                val incomingX = incoming.view.presentationX()
                observedPositions += outgoingX to incomingX
                if (
                    outgoingX != null &&
                    incomingX != null &&
                    outgoingX < -0.5 &&
                    outgoingX > -width / 3.0 + 0.5 &&
                    incomingX > 0.5 &&
                    incomingX < width - 0.5 &&
                    abs(-outgoingX / (width / 3.0) - (1.0 - incomingX / width)) < 0.05 &&
                    container.childViewControllers.size == 2
                ) {
                    intermediatePosition = outgoingX to incomingX
                }
            }

            assertTrue(
                intermediatePosition != null,
                "Programmatic push completed without a shared parallax presentation frame; " +
                    "last observed (outgoing, incoming)=${observedPositions.takeLast(20)}.",
            )
            awaitAppKitNavigationTransition("Programmatic push did not finish after rendering an intermediate frame.") {
                container.view.subviews.singleOrNull() == incoming.view
            }
        } finally {
            fixture.dispose()
        }
    }

    @Test
    public fun defaultProgrammaticPopProducesIntermediatePresentationFrames() {
        val fixture = AppKitTransitionFixture()

        try {
            if (NSWorkspace.sharedWorkspace.accessibilityDisplayShouldReduceMotion) return
            fixture.dispatch(listOf(fixture.home, fixture.detail))
            fixture.showWindow()
            CFRunLoopRunInMode(kCFRunLoopDefaultMode, 0.02, false)
            val container = fixture.parent.childViewControllers.single() as NSViewController
            val incoming = container.childViewControllers.first() as NSViewController
            val outgoing = container.childViewControllers.last() as NSViewController
            val width = container.view.bounds.useContents { size.width }

            fixture.dispatch(listOf(fixture.home))
            assertEquals(2, container.childViewControllers.size)
            val observedPositions = mutableListOf<Pair<Double?, Double?>>()
            val startedAt = TimeSource.Monotonic.markNow()
            var intermediatePosition: Pair<Double, Double>? = null
            while (
                intermediatePosition == null &&
                outgoing in container.childViewControllers &&
                startedAt.elapsedNow() < 2.seconds
            ) {
                CFRunLoopRunInMode(kCFRunLoopDefaultMode, 1.0 / 240.0, false)
                val outgoingX = outgoing.view.presentationX()
                val incomingX = incoming.view.presentationX()
                observedPositions += outgoingX to incomingX
                if (
                    outgoingX != null &&
                    incomingX != null &&
                    outgoingX > 0.5 &&
                    outgoingX < width - 0.5 &&
                    incomingX > -width / 3.0 + 0.5 &&
                    incomingX < -0.5 &&
                    container.childViewControllers.size == 2
                ) {
                    intermediatePosition = outgoingX to incomingX
                }
            }

            assertTrue(
                intermediatePosition != null,
                "Programmatic pop completed without an intermediate presentation frame; " +
                    "last observed (outgoing, incoming)=${observedPositions.takeLast(20)}.",
            )
            awaitAppKitNavigationTransition("Programmatic pop did not finish after rendering an intermediate frame.") {
                container.childViewControllers.singleOrNull() == incoming
            }
        } finally {
            fixture.dispose()
        }
    }

    @Test
    public fun navigationContainerIsLayerBackedBeforeProgrammaticTransitions() {
        val fixture = AppKitTransitionFixture()

        try {
            fixture.dispatch(listOf(fixture.home))

            val container = fixture.parent.childViewControllers.single() as NSViewController
            val root = container.childViewControllers.single() as NSViewController
            assertEquals(fixture.widget.view, container.view)
            assertEquals(container.view, root.view.superview)
            assertTrue(
                container.view.wantsLayer,
                "The transition container must be layer-backed before AppKit performs a slide transition.",
            )
        } finally {
            fixture.dispose()
        }
    }

    @Test
    public fun initialDeepStackMaterializesOnlyCurrentAndImmediatePredecessor() {
        val fixture = AppKitTransitionFixture()

        try {
            fixture.dispatch(
                listOf(
                    fixture.home,
                    fixture.detail,
                    fixture.editor,
                    fixture.settings,
                ),
            )
            val container = fixture.parent.childViewControllers.single() as NSViewController

            assertEquals(4, container.childViewControllers.size)
            assertEquals(2, fixture.subcompositions.created)
            assertEquals(2, fixture.subcompositions.installed)
            assertEquals(1, fixture.subcompositions.deactivated)
            assertEquals(0, fixture.subcompositions.disposed)
        } finally {
            fixture.dispose()
        }
    }

    @Test
    public fun factoryRebindRestoresActiveFrozenAndReleasedPagesWithTheNewFactory() {
        val fixture = AppKitTransitionFixture()
        val replacement = TransitionSubcompositionFactory()
        val reboundHome = transitionEntry("home")
        val reboundDetail = transitionEntry("detail")
        val reboundEditor = transitionEntry("editor")

        try {
            fixture.dispatch(listOf(fixture.home, fixture.detail, fixture.editor))
            val container = fixture.parent.childViewControllers.single() as NSViewController
            assertEquals(3, container.childViewControllers.size)
            assertEquals(2, fixture.subcompositions.created)
            fixture.subcompositions.disposeOwnedCompositions()

            fixture.dispatch(
                entries = listOf(reboundHome, reboundDetail, reboundEditor),
                subcompositions = replacement,
            )

            assertEquals(2, fixture.subcompositions.disposed)
            assertEquals(2, replacement.created)
            assertEquals(2, replacement.installed)
            assertEquals(1, replacement.deactivated)
            assertEquals(0, replacement.disposed)

            fixture.dispatch(listOf(reboundHome), replacement)
            awaitAppKitNavigationTransition("AppKit did not realize the released root with the new factory.") {
                container.childViewControllers.size == 1 && replacement.created == 3
            }
        } finally {
            fixture.dispose()
        }
    }

    @Test
    public fun programmaticPushAndPopCompleteWithStableControllerHierarchy() {
        val fixture = AppKitTransitionFixture()

        try {
            fixture.dispatch(listOf(fixture.home))
            val container = fixture.parent.childViewControllers.single() as NSViewController
            val root = container.childViewControllers.single() as NSViewController
            assertEquals(1, fixture.subcompositions.created)
            assertEquals(1, fixture.subcompositions.installed)

            fixture.dispatch(listOf(fixture.home, fixture.detail))
            awaitAppKitNavigationTransition("AppKit did not complete the programmatic push.") {
                container.childViewControllers.size == 2 &&
                    container.view.subviews.singleOrNull() ==
                    (container.childViewControllers.last() as NSViewController).view
            }

            val detail = container.childViewControllers.last() as NSViewController
            assertEquals(root, container.childViewControllers.first())
            assertNull(root.view.superview)
            assertEquals(container.view, detail.view.superview)
            assertEquals(2, fixture.subcompositions.created)
            assertEquals(2, fixture.subcompositions.installed)
            assertEquals(1, fixture.subcompositions.deactivated)
            assertEquals(0, fixture.subcompositions.disposed)

            fixture.dispatch(listOf(fixture.home))
            awaitAppKitNavigationTransition("AppKit did not complete the programmatic pop.") {
                container.childViewControllers.singleOrNull() == root &&
                    container.view.subviews.singleOrNull() == root.view
            }

            assertEquals(1, fixture.parent.childViewControllers.size)
            assertEquals(container.view, root.view.superview)
            assertTrue(detail !in container.childViewControllers)
            assertNull(detail.view.superview)
            assertEquals(2, fixture.subcompositions.created)
            assertEquals(3, fixture.subcompositions.installed)
            assertEquals(1, fixture.subcompositions.disposed)
        } finally {
            fixture.dispose()
        }
    }

    @Test
    public fun reconstructionRemovesForeignControllersAndOrphanViews() {
        val fixture = AppKitTransitionFixture()

        try {
            fixture.dispatch(listOf(fixture.home))
            val container = fixture.parent.childViewControllers.single() as NSViewController
            val orphanView = NSView(frame = container.view.bounds)
            val foreignController =
                NSViewController().apply {
                    view = NSView(frame = container.view.bounds)
                }
            container.view.addSubview(orphanView)
            container.addChildViewController(foreignController)

            fixture.dispatch(listOf(fixture.home, fixture.detail))

            assertEquals(2, container.childViewControllers.size)
            assertTrue(foreignController !in container.childViewControllers)
            assertNull(foreignController.parentViewController)
            assertNull(orphanView.superview)
            assertEquals(1, container.view.subviews.size)
            assertTrue(fixture.widget.beginSwipeBack())
            fixture.widget.finishSwipeBack(committed = false)
        } finally {
            fixture.dispose()
        }
    }

    @Test
    public fun foreignControllerAtSwipeBeginTriggersAuthoritativeRecovery() {
        val fixture = AppKitTransitionFixture()

        try {
            fixture.dispatch(listOf(fixture.home, fixture.detail))
            val container = fixture.parent.childViewControllers.single() as NSViewController
            val foreignController =
                NSViewController().apply {
                    view = NSView(frame = container.view.bounds)
                }
            container.addChildViewController(foreignController)

            assertFalse(fixture.widget.beginSwipeBack())

            assertEquals(2, container.childViewControllers.size)
            assertTrue(foreignController !in container.childViewControllers)
            assertNull(foreignController.parentViewController)
            assertEquals(
                (container.childViewControllers.last() as NSViewController).view,
                container.view.subviews.single(),
            )
            assertTrue(fixture.widget.beginSwipeBack())
            fixture.widget.finishSwipeBack(committed = false)
        } finally {
            fixture.dispose()
        }
    }

    @Test
    public fun programmaticAndInteractiveBackShareHalfwayGeometry() {
        val scheduler = ManualAppKitPageAnimationScheduler()
        val programmatic =
            AppKitTransitionFixture(
                widget = AppKitNavigationWidget(pageAnimationScheduler = scheduler),
            )
        val interactive = AppKitTransitionFixture()

        try {
            programmatic.dispatch(listOf(programmatic.home, programmatic.detail))
            interactive.dispatch(listOf(interactive.home, interactive.detail))
            val programmaticContainer = programmatic.parent.childViewControllers.single() as NSViewController
            val programmaticIncoming = programmaticContainer.childViewControllers.first() as NSViewController
            val programmaticOutgoing = programmaticContainer.childViewControllers.last() as NSViewController
            val interactiveContainer = interactive.parent.childViewControllers.single() as NSViewController
            val interactiveIncoming = interactiveContainer.childViewControllers.first() as NSViewController
            val interactiveOutgoing = interactiveContainer.childViewControllers.last() as NSViewController

            programmatic.dispatch(listOf(programmatic.home))
            assertTrue(scheduler.hasPendingAnimation, "Programmatic pop did not use the shared back animator.")
            scheduler.seek(0.5)
            val programmaticGeometry = captureBackGeometry(programmaticOutgoing.view, programmaticIncoming.view)
            assertBackTransitionHierarchy(
                container = programmaticContainer,
                incoming = programmaticIncoming,
                outgoing = programmaticOutgoing,
            )

            assertTrue(interactive.widget.beginSwipeBack())
            interactive.widget.updateSwipeBack(0.5)
            val interactiveGeometry = captureBackGeometry(interactiveOutgoing.view, interactiveIncoming.view)
            assertBackTransitionHierarchy(
                container = interactiveContainer,
                incoming = interactiveIncoming,
                outgoing = interactiveOutgoing,
            )

            assertBackGeometryMatchesHalfwaySpec(programmaticGeometry)
            assertBackGeometryMatchesHalfwaySpec(interactiveGeometry)

            scheduler.complete()
            interactive.widget.finishSwipeBack(committed = false)
            assertPageGeometryEquals(
                expected = PageGeometry(x = 0.0, y = 0.0, width = 600.0, height = 400.0),
                actual = interactiveOutgoing.view.pageGeometry(),
            )
            assertNull(interactiveIncoming.view.superview)
            assertEquals(
                listOf(interactiveIncoming, interactiveOutgoing),
                interactiveContainer.childViewControllers.map { it as NSViewController },
            )
            assertEquals(
                listOf(interactiveOutgoing.view),
                interactiveContainer.view.subviews.map { it as NSView },
            )
        } finally {
            interactive.dispose()
            programmatic.dispose()
        }
    }

    @Test
    public fun programmaticPushUsesTelegramStyleHalfwayGeometry() {
        val scheduler = ManualAppKitPageAnimationScheduler()
        val navigationView = AppKitNavigationContainerView()
        val fixture =
            AppKitTransitionFixture(
                widget =
                    AppKitNavigationWidget(
                        navigationView = navigationView,
                        pageAnimationScheduler = scheduler,
                    ),
            )

        try {
            fixture.dispatch(listOf(fixture.home))
            val container = fixture.parent.childViewControllers.single() as NSViewController
            val outgoing = container.childViewControllers.single() as NSViewController

            fixture.dispatch(listOf(fixture.home, fixture.detail))
            assertTrue(scheduler.hasPendingAnimation, "Programmatic push did not use the shared page animator.")
            val incoming = container.childViewControllers.last() as NSViewController
            assertTrue(navigationView.blocksPageTransitionInteraction)
            assertPageGeometryEquals(
                expected = PageGeometry(x = 0.0, y = 0.0, width = 600.0, height = 400.0),
                actual = outgoing.view.pageGeometry(),
            )
            assertPageGeometryEquals(
                expected = PageGeometry(x = 600.0, y = 0.0, width = 600.0, height = 400.0),
                actual = incoming.view.pageGeometry(),
            )
            scheduler.seek(0.5)

            assertPushGeometryMatchesHalfwaySpec(captureBackGeometry(outgoing.view, incoming.view))
            assertBackTransitionHierarchy(container, outgoing, incoming)
            scheduler.complete()
            assertFalse(navigationView.blocksPageTransitionInteraction)
            assertNull(outgoing.view.superview)
            assertEquals(listOf(incoming.view), container.view.subviews.map { it as NSView })
        } finally {
            fixture.dispose()
        }
    }

    @Test
    public fun schedulerFailureAfterSynchronousCompletionDoesNotRollbackCommittedPush() {
        val fixture =
            AppKitTransitionFixture(
                widget =
                    AppKitNavigationWidget(
                        pageAnimationScheduler = CompletingThenThrowingAppKitPageAnimationScheduler,
                    ),
            )

        try {
            fixture.dispatch(listOf(fixture.home))
            val container = fixture.parent.childViewControllers.single() as NSViewController
            val outgoing = container.childViewControllers.single() as NSViewController

            fixture.dispatch(listOf(fixture.home, fixture.detail))
            val incoming = container.childViewControllers.last() as NSViewController

            assertEquals(
                listOf(outgoing, incoming),
                container.childViewControllers.map { it as NSViewController },
            )
            assertNull(outgoing.view.superview)
            assertEquals(listOf(incoming.view), container.view.subviews.map { it as NSView })
        } finally {
            fixture.dispose()
        }
    }

    @Test
    public fun rightToLeftProgrammaticPushMirrorsHalfwayGeometry() {
        val scheduler = ManualAppKitPageAnimationScheduler()
        val navigationView =
            AppKitNavigationContainerView().apply {
                userInterfaceLayoutDirection = NSUserInterfaceLayoutDirectionRightToLeft
            }
        val fixture =
            AppKitTransitionFixture(
                widget =
                    AppKitNavigationWidget(
                        navigationView = navigationView,
                        pageAnimationScheduler = scheduler,
                    ),
            )

        try {
            fixture.dispatch(listOf(fixture.home))
            val container = fixture.parent.childViewControllers.single() as NSViewController
            val outgoing = container.childViewControllers.single() as NSViewController

            fixture.dispatch(listOf(fixture.home, fixture.detail))
            val incoming = container.childViewControllers.last() as NSViewController
            scheduler.seek(0.5)

            assertPushGeometryMatchesRightToLeftHalfwaySpec(
                captureBackGeometry(outgoing.view, incoming.view),
            )
            assertBackTransitionHierarchy(container, outgoing, incoming)
            scheduler.complete()
        } finally {
            fixture.dispose()
        }
    }

    @Test
    public fun rightToLeftBackGeometryUsesSemanticLeadingDirection() {
        val scheduler = ManualAppKitPageAnimationScheduler()
        val navigationView =
            AppKitNavigationContainerView().apply {
                userInterfaceLayoutDirection = NSUserInterfaceLayoutDirectionRightToLeft
            }
        val fixture =
            AppKitTransitionFixture(
                widget =
                    AppKitNavigationWidget(
                        navigationView = navigationView,
                        pageAnimationScheduler = scheduler,
                    ),
            )

        try {
            fixture.dispatch(listOf(fixture.home, fixture.detail))
            val container = fixture.parent.childViewControllers.single() as NSViewController
            val incoming = container.childViewControllers.first() as NSViewController
            val outgoing = container.childViewControllers.last() as NSViewController

            fixture.dispatch(listOf(fixture.home))
            scheduler.seek(0.5)

            assertBackGeometryMatchesRightToLeftHalfwaySpec(
                captureBackGeometry(outgoing.view, incoming.view),
            )
            assertBackTransitionHierarchy(container, incoming, outgoing)
            scheduler.complete()
        } finally {
            fixture.dispose()
        }
    }

    @Test
    public fun discreteSwipeUsesSharedBackAnimatorAndCommitsOnce() {
        val scheduler = ManualAppKitPageAnimationScheduler()
        val fixture =
            AppKitTransitionFixture(
                widget = AppKitNavigationWidget(pageAnimationScheduler = scheduler),
            )

        try {
            fixture.dispatch(listOf(fixture.home, fixture.detail))
            val container = fixture.parent.childViewControllers.single() as NSViewController
            val incoming = container.childViewControllers.first() as NSViewController
            val outgoing = container.childViewControllers.last() as NSViewController

            assertTrue(fixture.widget.performDiscreteSwipeBack())
            assertTrue(scheduler.hasPendingAnimation)
            scheduler.seek(0.5)
            assertBackGeometryMatchesHalfwaySpec(captureBackGeometry(outgoing.view, incoming.view))
            assertBackTransitionHierarchy(container, incoming, outgoing)

            scheduler.complete()

            assertEquals(1, fixture.backRequestCount)
            assertEquals(listOf(incoming), container.childViewControllers.map { it as NSViewController })
            assertEquals(listOf(incoming.view), container.view.subviews.map { it as NSView })
            assertNull(outgoing.view.superview)
        } finally {
            fixture.dispose()
        }
    }
}

private class AppKitTransitionFixture(
    val widget: AppKitNavigationWidget = AppKitNavigationWidget(),
) {
    val parent: NSViewController =
        NSViewController().apply {
            view = NSView(frame = CGRectMake(0.0, 0.0, 600.0, 400.0))
        }
    val home: ResolvedNavigationEntry = transitionEntry("home")
    val detail: ResolvedNavigationEntry = transitionEntry("detail")
    val editor: ResolvedNavigationEntry = transitionEntry("editor")
    val settings: ResolvedNavigationEntry = transitionEntry("settings")

    private val dispatcher = NavigationModelDispatcher()
    private var currentEntries: List<ResolvedNavigationEntry> = emptyList()
    val subcompositions = TransitionSubcompositionFactory()
    private var currentSubcompositions: FlareSubcompositionFactory = subcompositions
    var backRequestCount: Int = 0
        private set
    private val window: NSWindow

    init {
        NSApplication.sharedApplication
        window =
            NSWindow(
                contentRect = CGRectMake(0.0, 0.0, 600.0, 400.0),
                styleMask = NSWindowStyleMaskBorderless,
                backing = NSBackingStoreBuffered,
                defer = false,
            )
        window.contentView = parent.view
        widget.view.frame = parent.view.bounds
        parent.view.addSubview(widget.view)
        widget.setModelDispatcher(dispatcher)
    }

    fun dispatch(
        entries: List<ResolvedNavigationEntry>,
        subcompositions: FlareSubcompositionFactory = currentSubcompositions,
    ) {
        currentEntries = entries
        currentSubcompositions = subcompositions
        dispatcher.dispatch(
            NavigationModel(
                entries = entries,
                onBack = { request ->
                    backRequestCount += 1
                    request.accept()
                    dispatch(currentEntries.dropLast(request.popCount), currentSubcompositions)
                },
                subcompositions = subcompositions,
                nativeControllerOwner = AppKitNavigationOwner(parent),
            ),
        )
    }

    fun showWindow() {
        window.alphaValue = 0.001
        window.orderFrontRegardless()
        check(window.visible) { "AppKit animation tests require a visible window." }
    }

    fun dispose() {
        widget.dispose()
        window.close()
    }
}

private class ManualAppKitPageAnimationScheduler : AppKitPageAnimationScheduler {
    private var view: NSView? = null
    private var updateProgress: ((Double) -> Unit)? = null
    private var completion: (() -> Unit)? = null

    val hasPendingAnimation: Boolean
        get() = updateProgress != null

    override fun animate(
        view: NSView,
        durationSeconds: Double,
        updateProgress: (Double) -> Unit,
        completion: () -> Unit,
    ) {
        check(!hasPendingAnimation) { "Only one AppKit back animation can be pending." }
        this.view = view
        this.updateProgress = updateProgress
        this.completion = completion
    }

    fun seek(progress: Double) {
        checkNotNull(updateProgress).invoke(progress)
        checkNotNull(view).layoutSubtreeIfNeeded()
    }

    fun complete() {
        seek(1.0)
        val completion = checkNotNull(completion)
        view = null
        updateProgress = null
        this.completion = null
        completion()
    }
}

private object CompletingThenThrowingAppKitPageAnimationScheduler : AppKitPageAnimationScheduler {
    override fun animate(
        view: NSView,
        durationSeconds: Double,
        updateProgress: (Double) -> Unit,
        completion: () -> Unit,
    ) {
        updateProgress(1.0)
        view.layoutSubtreeIfNeeded()
        completion()
        error("Animation scheduler failed after invoking its synchronous completion.")
    }
}

private data class BackGeometry(
    val outgoing: PageGeometry,
    val incoming: PageGeometry,
)

private data class PageGeometry(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
)

private fun captureBackGeometry(
    outgoing: NSView,
    incoming: NSView,
): BackGeometry =
    BackGeometry(
        outgoing = outgoing.pageGeometry(),
        incoming = incoming.pageGeometry(),
    )

private fun NSView.pageGeometry(): PageGeometry =
    frame.useContents {
        PageGeometry(
            x = origin.x,
            y = origin.y,
            width = size.width,
            height = size.height,
        )
    }

private fun NSView.presentationX(): Double? =
    layer
        ?.presentationLayer()
        ?.frame
        ?.useContents { origin.x }

private fun assertBackGeometryMatchesHalfwaySpec(actual: BackGeometry) {
    assertPageGeometryEquals(
        expected = PageGeometry(x = 300.0, y = 0.0, width = 600.0, height = 400.0),
        actual = actual.outgoing,
    )
    assertPageGeometryEquals(
        expected = PageGeometry(x = -100.0, y = 0.0, width = 600.0, height = 400.0),
        actual = actual.incoming,
    )
}

private fun assertPushGeometryMatchesHalfwaySpec(actual: BackGeometry) {
    assertPageGeometryEquals(
        expected = PageGeometry(x = -100.0, y = 0.0, width = 600.0, height = 400.0),
        actual = actual.outgoing,
    )
    assertPageGeometryEquals(
        expected = PageGeometry(x = 300.0, y = 0.0, width = 600.0, height = 400.0),
        actual = actual.incoming,
    )
}

private fun assertPushGeometryMatchesRightToLeftHalfwaySpec(actual: BackGeometry) {
    assertPageGeometryEquals(
        expected = PageGeometry(x = 100.0, y = 0.0, width = 600.0, height = 400.0),
        actual = actual.outgoing,
    )
    assertPageGeometryEquals(
        expected = PageGeometry(x = -300.0, y = 0.0, width = 600.0, height = 400.0),
        actual = actual.incoming,
    )
}

private fun assertBackGeometryMatchesRightToLeftHalfwaySpec(actual: BackGeometry) {
    assertPageGeometryEquals(
        expected = PageGeometry(x = -300.0, y = 0.0, width = 600.0, height = 400.0),
        actual = actual.outgoing,
    )
    assertPageGeometryEquals(
        expected = PageGeometry(x = 100.0, y = 0.0, width = 600.0, height = 400.0),
        actual = actual.incoming,
    )
}

private fun assertBackTransitionHierarchy(
    container: NSViewController,
    incoming: NSViewController,
    outgoing: NSViewController,
) {
    assertEquals(
        listOf(incoming, outgoing),
        container.childViewControllers.map { it as NSViewController },
    )
    assertEquals(
        listOf(incoming.view, outgoing.view),
        container.view.subviews.map { it as NSView },
    )
    assertEquals(container.view, incoming.view.superview)
    assertEquals(container.view, outgoing.view.superview)
}

private fun assertPageGeometryEquals(
    expected: PageGeometry,
    actual: PageGeometry,
) {
    assertEquals(expected.x, actual.x, absoluteTolerance = 0.5)
    assertEquals(expected.y, actual.y, absoluteTolerance = 0.5)
    assertEquals(expected.width, actual.width, absoluteTolerance = 0.5)
    assertEquals(expected.height, actual.height, absoluteTolerance = 0.5)
}

private fun transitionEntry(contentKey: String): ResolvedNavigationEntry =
    ResolvedNavigationEntry(
        contentKey = contentKey,
        presentation = NavigationPresentation.Page,
        entry =
            NavEntry(
                key = contentKey,
                contentKey = contentKey,
            ) {},
    )

private class TransitionSubcompositionFactory : FlareSubcompositionFactory {
    private val compositions = mutableListOf<TransitionSubcomposition>()
    private var disposedFactory: Boolean = false
    var created: Int = 0
        private set
    var installed: Int = 0
        private set
    var deactivated: Int = 0
        private set
    var disposed: Int = 0
        private set

    override fun create(root: FlareChildren): FlareSubcomposition {
        check(!disposedFactory) { "AppKit test subcomposition factory is already disposed." }
        created += 1
        return TransitionSubcomposition(
            onInstalled = { installed += 1 },
            onDeactivated = { deactivated += 1 },
            onDisposed = { disposed += 1 },
        ).also(compositions::add)
    }

    fun disposeOwnedCompositions() {
        disposedFactory = true
        compositions.forEach(TransitionSubcomposition::dispose)
    }
}

private class TransitionSubcomposition(
    private val onInstalled: () -> Unit,
    private val onDeactivated: () -> Unit,
    private val onDisposed: () -> Unit,
) : FlareSubcomposition {
    private var disposed: Boolean = false

    override fun setContent(content: FlareContent) {
        check(!disposed) { "AppKit test subcomposition is already disposed." }
        onInstalled()
    }

    override fun deactivate() {
        check(!disposed) { "AppKit test subcomposition is already disposed." }
        onDeactivated()
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        onDisposed()
    }
}

private fun awaitAppKitNavigationTransition(
    message: String,
    condition: () -> Boolean,
) {
    val startedAt = TimeSource.Monotonic.markNow()
    while (!condition() && startedAt.elapsedNow() < 5.seconds) {
        CFRunLoopRunInMode(kCFRunLoopDefaultMode, 0.01, true)
    }
    check(condition()) { message }
}
