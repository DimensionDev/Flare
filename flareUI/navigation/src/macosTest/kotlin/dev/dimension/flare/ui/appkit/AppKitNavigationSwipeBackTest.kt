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
import dev.dimension.flare.ui.navigation.NavigationBackRequest
import dev.dimension.flare.ui.navigation.NavigationEntryIdentity
import dev.dimension.flare.ui.navigation.NavigationModel
import dev.dimension.flare.ui.navigation.NavigationModelDispatcher
import dev.dimension.flare.ui.navigation.NavigationPresentation
import dev.dimension.flare.ui.navigation.ResolvedNavigationEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import platform.AppKit.NSView
import platform.AppKit.NSViewController
import platform.AppKit.childViewControllers
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

public class AppKitNavigationSwipeBackTest {
    @Test
    public fun pageTransitionInteractionBlockerKeepsHitsOffDescendantPages() {
        val container = AppKitNavigationContainerView().apply { frame = CGRectMake(0.0, 0.0, 300.0, 200.0) }
        val page = NSView(frame = container.bounds)
        container.addSubview(page)
        val point = CGPointMake(100.0, 100.0)

        assertEquals(page, container.hitTest(point))
        container.blocksPageTransitionInteraction = true
        assertEquals(container, container.hitTest(point))
    }

    @Test
    public fun recognizesPhysicalTrailingSwipeAcrossScrollPreferences() {
        val natural =
            appKitSwipeBackTrackingDirection(
                scrollingDeltaX = 8.0,
                scrollingDeltaY = 1.0,
                directionInvertedFromDevice = true,
                rightToLeft = false,
            )
        val traditional =
            appKitSwipeBackTrackingDirection(
                scrollingDeltaX = -8.0,
                scrollingDeltaY = 1.0,
                directionInvertedFromDevice = false,
                rightToLeft = false,
            )

        assertEquals(1.0, natural?.gestureAmountSign)
        assertEquals(-1.0, traditional?.gestureAmountSign)
    }

    @Test
    public fun rejectsVerticalAndForwardGestures() {
        assertNull(
            appKitSwipeBackTrackingDirection(
                scrollingDeltaX = 2.0,
                scrollingDeltaY = 3.0,
                directionInvertedFromDevice = true,
                rightToLeft = false,
            ),
        )
        assertNull(
            appKitSwipeBackTrackingDirection(
                scrollingDeltaX = -3.0,
                scrollingDeltaY = 0.0,
                directionInvertedFromDevice = true,
                rightToLeft = false,
            ),
        )
    }

    @Test
    public fun recognizesOppositePhysicalBackDirectionForRightToLeftLayout() {
        val direction =
            appKitSwipeBackTrackingDirection(
                scrollingDeltaX = -6.0,
                scrollingDeltaY = 0.0,
                directionInvertedFromDevice = true,
                rightToLeft = true,
            )

        assertEquals(-1.0, direction?.gestureAmountSign)
    }

    @Test
    public fun recognizesDiscreteBackSwipeInBothLayoutDirections() {
        assertTrue(isAppKitDiscreteSwipeBack(deltaX = -1.0, deltaY = 0.0, rightToLeft = false))
        assertTrue(isAppKitDiscreteSwipeBack(deltaX = 1.0, deltaY = 0.0, rightToLeft = true))
        assertEquals(false, isAppKitDiscreteSwipeBack(deltaX = 1.0, deltaY = 0.0, rightToLeft = false))
        assertEquals(false, isAppKitDiscreteSwipeBack(deltaX = -1.0, deltaY = 0.0, rightToLeft = true))
        assertEquals(false, isAppKitDiscreteSwipeBack(deltaX = -1.0, deltaY = 2.0, rightToLeft = false))
    }

    @Test
    public fun usesTelegramStyleOneThirdIncomingParallaxInLeftToRightLayout() {
        assertEquals(
            AppKitSwipeBackOffsets(outgoing = 0.0, incoming = -100.0),
            appKitSwipeBackOffsets(progress = 0.0, width = 300.0),
        )
        assertEquals(
            AppKitSwipeBackOffsets(outgoing = 150.0, incoming = -50.0),
            appKitSwipeBackOffsets(progress = 0.5, width = 300.0),
        )
        assertEquals(
            AppKitSwipeBackOffsets(outgoing = 300.0, incoming = 0.0),
            appKitSwipeBackOffsets(progress = 1.0, width = 300.0),
        )
    }

    @Test
    public fun pushAndPopPageOffsetsAreTimeReverses() {
        listOf(0.0, 0.25, 0.5, 0.75, 1.0).forEach { progress ->
            val push =
                appKitPageTransitionOffsets(
                    kind = AppKitPageTransitionKind.Push,
                    progress = progress,
                    width = 300.0,
                )
            val reversedPop =
                appKitPageTransitionOffsets(
                    kind = AppKitPageTransitionKind.Pop,
                    progress = 1.0 - progress,
                    width = 300.0,
                )

            assertEquals(push.source, reversedPop.destination)
            assertEquals(push.destination, reversedPop.source)
        }
    }

    @Test
    public fun waitsForFluidTrackingCompletionBeforeCommitting() {
        val progress = mutableListOf<Double>()
        val completions = mutableListOf<Boolean>()
        val session =
            AppKitSwipeBackTrackingSession(
                direction = AppKitSwipeBackTrackingDirection(1.0),
                onProgress = progress::add,
                onComplete = completions::add,
            )

        session.update(gestureAmount = 0.7, isComplete = false)
        assertEquals(listOf(0.7), progress)
        assertEquals(emptyList(), completions)

        session.update(gestureAmount = 1.0, isComplete = true)
        session.update(gestureAmount = 0.0, isComplete = true)
        assertEquals(listOf(0.7, 1.0), progress)
        assertEquals(listOf(true), completions)
    }

    @Test
    public fun cancelsWhenFluidTrackingReturnsToOrigin() {
        val completions = mutableListOf<Boolean>()
        val session =
            AppKitSwipeBackTrackingSession(
                direction = AppKitSwipeBackTrackingDirection(-1.0),
                onProgress = {},
                onComplete = completions::add,
            )

        session.update(gestureAmount = -0.4, isComplete = false)
        session.update(gestureAmount = 0.0, isComplete = true)

        assertEquals(listOf(false), completions)
    }

    @Test
    public fun invalidatedFluidTrackingDropsCallbacks() {
        val progress = mutableListOf<Double>()
        val completions = mutableListOf<Boolean>()
        val session =
            AppKitSwipeBackTrackingSession(
                direction = AppKitSwipeBackTrackingDirection(1.0),
                onProgress = progress::add,
                onComplete = completions::add,
            )

        session.invalidate()
        session.update(gestureAmount = 1.0, isComplete = true)

        assertEquals(emptyList(), progress)
        assertEquals(emptyList(), completions)
    }

    @Test
    public fun commitsPhysicalControllerPopBeforeSynchronousModelAcknowledgement() {
        val parent = testParentController()
        val widget = AppKitNavigationWidget()
        val dispatcher = NavigationModelDispatcher()
        val factory = SwipeRecordingSubcompositionFactory()
        val home = swipeEntry("home")
        val detail = swipeEntry("detail")
        var backCalls = 0
        widget.setModelDispatcher(dispatcher)
        parent.view.addSubview(widget.view)
        dispatcher.dispatch(
            swipeModel(
                entries = listOf(home, detail),
                parent = parent,
                factory = factory,
                onBack = { request ->
                    backCalls += 1
                    request.accept()
                    dispatcher.dispatch(
                        swipeModel(
                            entries = listOf(home),
                            parent = parent,
                            factory = factory,
                        ),
                    )
                },
            ),
        )

        val nativeContainer = parent.childViewControllers.single() as NSViewController
        assertEquals(2, nativeContainer.childViewControllers.size)
        assertTrue(widget.beginSwipeBack())
        widget.updateSwipeBack(0.6)
        widget.finishSwipeBack(committed = true)

        assertEquals(1, backCalls)
        assertEquals(1, nativeContainer.childViewControllers.size)
        assertEquals(1, widget.view.subviews.size)

        widget.dispose()
        assertEquals(0, parent.childViewControllers.size)
    }

    @Test
    public fun cancelledSwipeRestoresTopControllerWithoutRequestingBack() {
        val parent = testParentController()
        val widget = AppKitNavigationWidget()
        val dispatcher = NavigationModelDispatcher()
        val factory = SwipeRecordingSubcompositionFactory()
        var backCalls = 0
        widget.setModelDispatcher(dispatcher)
        parent.view.addSubview(widget.view)
        dispatcher.dispatch(
            swipeModel(
                entries = listOf(swipeEntry("home"), swipeEntry("detail")),
                parent = parent,
                factory = factory,
                onBack = { backCalls += 1 },
            ),
        )

        val nativeContainer = parent.childViewControllers.single() as NSViewController
        val detailController = nativeContainer.childViewControllers.last() as NSViewController
        assertTrue(widget.beginSwipeBack())
        widget.updateSwipeBack(0.3)
        widget.finishSwipeBack(committed = false)

        assertEquals(0, backCalls)
        assertEquals(2, nativeContainer.childViewControllers.size)
        assertEquals(1, widget.view.subviews.size)
        assertEquals(detailController.view, widget.view.subviews.single())
        assertTrue(widget.beginSwipeBack())

        widget.dispose()
        assertEquals(0, parent.childViewControllers.size)
    }

    @Test
    public fun stagedModelBlocksSwipeBackAgainstStaleTopology() {
        val parent = testParentController()
        val widget = AppKitNavigationWidget()
        val dispatcher = NavigationModelDispatcher()
        val stagingScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val factory = SwipeRecordingSubcompositionFactory()
        val home = swipeEntry("home")
        widget.setModelDispatcher(dispatcher)
        parent.view.addSubview(widget.view)
        dispatcher.dispatch(
            swipeModel(
                entries = listOf(home, swipeEntry("detail")),
                parent = parent,
                factory = factory,
            ),
        )

        dispatcher.stage(
            swipeModel(
                entries = listOf(home),
                parent = parent,
                factory = factory,
            ),
            stagingScope,
        )

        assertFalse(widget.canBeginSwipeBack())
        widget.dispose()
        stagingScope.cancel()
    }
}

private fun testParentController(): NSViewController =
    NSViewController().apply {
        view = NSView(frame = CGRectMake(0.0, 0.0, 600.0, 400.0))
    }

private fun swipeModel(
    entries: List<ResolvedNavigationEntry>,
    parent: NSViewController,
    factory: FlareSubcompositionFactory,
    onBack: (NavigationBackRequest<NavigationEntryIdentity>) -> Unit = {},
): NavigationModel =
    NavigationModel(
        entries = entries,
        onBack = onBack,
        subcompositions = factory,
        nativeControllerOwner = AppKitNavigationOwner(parent),
    )

private fun swipeEntry(contentKey: String): ResolvedNavigationEntry =
    ResolvedNavigationEntry(
        contentKey = contentKey,
        presentation = NavigationPresentation.Page,
        entry =
            NavEntry(
                key = contentKey,
                contentKey = contentKey,
            ) {},
    )

private class SwipeRecordingSubcompositionFactory : FlareSubcompositionFactory {
    override fun create(root: FlareChildren): FlareSubcomposition = SwipeRecordingSubcomposition()
}

private class SwipeRecordingSubcomposition : FlareSubcomposition {
    override fun setContent(content: FlareContent) = Unit

    override fun deactivate() = Unit

    override fun dispose() = Unit
}
