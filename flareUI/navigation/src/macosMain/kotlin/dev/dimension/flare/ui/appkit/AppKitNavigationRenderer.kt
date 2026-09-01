@file:OptIn(
    dev.dimension.flare.ui.LowLevelFlareApi::class,
    dev.dimension.flare.ui.navigation.ExperimentalFlareNavigation::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package dev.dimension.flare.ui.appkit

import dev.dimension.flare.ui.FlareNativeControllerOwner
import dev.dimension.flare.ui.FlareRendererPlugin
import dev.dimension.flare.ui.FlareSubcompositionFactory
import dev.dimension.flare.ui.FlareWidgetRegistrar
import dev.dimension.flare.ui.navigation.NAVIGATION_ACKNOWLEDGEMENT_TIMEOUT_MILLIS
import dev.dimension.flare.ui.navigation.NavigationAcknowledgementHandle
import dev.dimension.flare.ui.navigation.NavigationCommand
import dev.dimension.flare.ui.navigation.NavigationCoordinator
import dev.dimension.flare.ui.navigation.NavigationCoordinatorState
import dev.dimension.flare.ui.navigation.NavigationEntryContentHost
import dev.dimension.flare.ui.navigation.NavigationEntryIdentity
import dev.dimension.flare.ui.navigation.NavigationInteractionHandle
import dev.dimension.flare.ui.navigation.NavigationModel
import dev.dimension.flare.ui.navigation.NavigationModelDispatcher
import dev.dimension.flare.ui.navigation.NavigationOperation
import dev.dimension.flare.ui.navigation.NavigationOperationResult
import dev.dimension.flare.ui.navigation.NavigationPresentation
import dev.dimension.flare.ui.navigation.NavigationWidget
import dev.dimension.flare.ui.navigation.ResolvedNavigationEntry
import dev.dimension.flare.ui.navigation.identity
import kotlinx.cinterop.CValue
import kotlinx.cinterop.pointed
import kotlinx.cinterop.useContents
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.AppKit.NSAnimationContext
import platform.AppKit.NSColor
import platform.AppKit.NSEvent
import platform.AppKit.NSEventGestureAxisHorizontal
import platform.AppKit.NSEventPhaseBegan
import platform.AppKit.NSEventPhaseCancelled
import platform.AppKit.NSEventPhaseChanged
import platform.AppKit.NSEventPhaseEnded
import platform.AppKit.NSEventPhaseMayBegin
import platform.AppKit.NSEventPhaseNone
import platform.AppKit.NSEventSwipeTrackingClampGestureAmount
import platform.AppKit.NSEventSwipeTrackingLockDirection
import platform.AppKit.NSLayoutConstraint
import platform.AppKit.NSStackView
import platform.AppKit.NSUserInterfaceLayoutOrientationVertical
import platform.AppKit.NSView
import platform.AppKit.NSViewController
import platform.AppKit.NSWindowAbove
import platform.AppKit.NSWindowBelow
import platform.AppKit.NSWorkspace
import platform.AppKit.accessibilityDisplayShouldReduceMotion
import platform.AppKit.addChildViewController
import platform.AppKit.bottomAnchor
import platform.AppKit.childViewControllers
import platform.AppKit.leadingAnchor
import platform.AppKit.removeFromParentViewController
import platform.AppKit.topAnchor
import platform.AppKit.trailingAnchor
import platform.AppKit.translatesAutoresizingMaskIntoConstraints
import platform.AppKit.widthAnchor
import platform.CoreGraphics.CGPoint
import platform.CoreGraphics.CGRectMake
import kotlin.math.abs
import platform.AppKit.NSUserInterfaceLayoutDirectionRightToLeft as AppKitRightToLeft

internal data class AppKitSwipeBackTrackingDirection(
    val gestureAmountSign: Double,
) {
    init {
        require(abs(gestureAmountSign) == 1.0)
    }

    val minimumDampenThreshold: Double
        get() = if (gestureAmountSign < 0.0) -1.0 else 0.0

    val maximumDampenThreshold: Double
        get() = if (gestureAmountSign > 0.0) 1.0 else 0.0

    fun progress(gestureAmount: Double): Double = (gestureAmount * gestureAmountSign).coerceIn(0.0, 1.0)
}

internal fun appKitSwipeBackTrackingDirection(
    scrollingDeltaX: Double,
    scrollingDeltaY: Double,
    directionInvertedFromDevice: Boolean,
    rightToLeft: Boolean,
): AppKitSwipeBackTrackingDirection? {
    if (scrollingDeltaX == 0.0 || abs(scrollingDeltaX) <= abs(scrollingDeltaY)) return null

    // NSEvent applies the user's natural-scrolling preference to scrollingDeltaX. Navigation is
    // tied to the physical gesture direction, so undo that preference before checking whether the
    // fingers are moving toward the trailing edge (right in LTR, left in RTL).
    val trailingTravel =
        (if (directionInvertedFromDevice) scrollingDeltaX else -scrollingDeltaX) *
            (if (rightToLeft) -1.0 else 1.0)
    if (trailingTravel <= 0.0) return null

    return AppKitSwipeBackTrackingDirection(
        gestureAmountSign = if (scrollingDeltaX > 0.0) 1.0 else -1.0,
    )
}

internal fun isAppKitDiscreteSwipeBack(
    deltaX: Double,
    deltaY: Double,
    rightToLeft: Boolean,
): Boolean =
    abs(deltaX) > abs(deltaY) &&
        if (rightToLeft) {
            deltaX > 0.0
        } else {
            deltaX < 0.0
        }

internal data class AppKitSwipeBackOffsets(
    val outgoing: Double,
    val incoming: Double,
)

internal enum class AppKitPageTransitionKind {
    Push,
    Pop,
}

internal data class AppKitPageTransitionOffsets(
    val source: Double,
    val destination: Double,
)

internal interface AppKitPageAnimationScheduler {
    fun animate(
        view: NSView,
        durationSeconds: Double,
        updateProgress: (Double) -> Unit,
        completion: () -> Unit,
    )
}

private object DefaultAppKitPageAnimationScheduler : AppKitPageAnimationScheduler {
    override fun animate(
        view: NSView,
        durationSeconds: Double,
        updateProgress: (Double) -> Unit,
        completion: () -> Unit,
    ) {
        if (NSWorkspace.sharedWorkspace.accessibilityDisplayShouldReduceMotion) {
            updateProgress(1.0)
            view.layoutSubtreeIfNeeded()
            completion()
            return
        }
        NSAnimationContext.runAnimationGroup(
            changes = { context ->
                context?.duration = durationSeconds
                context?.allowsImplicitAnimation = true
                updateProgress(1.0)
                view.layoutSubtreeIfNeeded()
            },
            completionHandler = completion,
        )
    }
}

internal fun appKitSwipeBackOffsets(
    progress: Double,
    width: Double,
): AppKitSwipeBackOffsets {
    val offsets =
        appKitPageTransitionOffsets(
            kind = AppKitPageTransitionKind.Pop,
            progress = progress,
            width = width,
        )
    return AppKitSwipeBackOffsets(
        outgoing = offsets.source,
        incoming = offsets.destination,
    )
}

internal fun appKitPageTransitionOffsets(
    kind: AppKitPageTransitionKind,
    progress: Double,
    width: Double,
): AppKitPageTransitionOffsets {
    val boundedProgress = progress.coerceIn(0.0, 1.0)
    val boundedWidth = width.coerceAtLeast(0.0)
    return when (kind) {
        AppKitPageTransitionKind.Push -> {
            AppKitPageTransitionOffsets(
                source =
                    if (boundedProgress == 0.0) {
                        0.0
                    } else {
                        -boundedWidth / 3.0 * boundedProgress
                    },
                destination =
                    if (boundedProgress == 1.0) {
                        0.0
                    } else {
                        boundedWidth * (1.0 - boundedProgress)
                    },
            )
        }

        AppKitPageTransitionKind.Pop -> {
            AppKitPageTransitionOffsets(
                source =
                    if (boundedProgress == 0.0) {
                        0.0
                    } else {
                        boundedWidth * boundedProgress
                    },
                destination =
                    if (boundedProgress == 1.0) {
                        0.0
                    } else {
                        -boundedWidth / 3.0 * (1.0 - boundedProgress)
                    },
            )
        }
    }
}

internal class AppKitSwipeBackTrackingSession(
    private val direction: AppKitSwipeBackTrackingDirection,
    onProgress: (Double) -> Unit,
    onComplete: (committed: Boolean) -> Unit,
) {
    private var active: Boolean = true
    private var progressCallback: ((Double) -> Unit)? = onProgress
    private var completionCallback: ((Boolean) -> Unit)? = onComplete

    fun update(
        gestureAmount: Double,
        isComplete: Boolean,
    ) {
        if (!active) return
        val progress = direction.progress(gestureAmount)
        progressCallback?.invoke(progress)
        if (isComplete) {
            val completion = completionCallback
            active = false
            progressCallback = null
            completionCallback = null
            completion?.invoke(progress >= 0.5)
        }
    }

    fun invalidate() {
        active = false
        progressCallback = null
        completionCallback = null
    }
}

internal interface AppKitSwipeBackDelegate {
    fun canBeginSwipeBack(): Boolean

    fun beginSwipeBack(): Boolean

    fun updateSwipeBack(progress: Double)

    fun finishSwipeBack(committed: Boolean)

    fun performDiscreteSwipeBack(): Boolean
}

internal class AppKitNavigationContainerView : NSView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0)) {
    var swipeBackDelegate: AppKitSwipeBackDelegate? = null
    internal var blocksPageTransitionInteraction: Boolean = false
    private var trackingSession: AppKitSwipeBackTrackingSession? = null
    private var scrollGestureRejected: Boolean = false

    init {
        clipsToBounds = true
        wantsLayer = true
    }

    override fun hitTest(point: CValue<CGPoint>): NSView? {
        val hitView = super.hitTest(point)
        return if (blocksPageTransitionInteraction && hitView != null) this else hitView
    }

    override fun wantsScrollEventsForSwipeTrackingOnAxis(axis: Long): Boolean =
        axis == NSEventGestureAxisHorizontal &&
            NSEvent.swipeTrackingFromScrollEventsEnabled &&
            swipeBackDelegate?.canBeginSwipeBack() == true

    override fun scrollWheel(event: NSEvent) {
        if (trackingSession != null) return
        when (event.phase) {
            NSEventPhaseMayBegin -> {
                scrollGestureRejected = false
                super.scrollWheel(event)
                return
            }

            NSEventPhaseEnded,
            NSEventPhaseCancelled,
            NSEventPhaseNone,
            -> {
                scrollGestureRejected = false
                super.scrollWheel(event)
                return
            }

            NSEventPhaseBegan -> {
                scrollGestureRejected = false
            }
        }
        if (event.phase != NSEventPhaseBegan && event.phase != NSEventPhaseChanged) {
            super.scrollWheel(event)
            return
        }
        if (scrollGestureRejected) {
            super.scrollWheel(event)
            return
        }

        val delegate = swipeBackDelegate
        val direction =
            appKitSwipeBackTrackingDirection(
                scrollingDeltaX = event.scrollingDeltaX,
                scrollingDeltaY = event.scrollingDeltaY,
                directionInvertedFromDevice = event.directionInvertedFromDevice,
                rightToLeft = userInterfaceLayoutDirection == AppKitRightToLeft,
            )
        if (direction == null) {
            if (event.scrollingDeltaX != 0.0 || event.scrollingDeltaY != 0.0) {
                scrollGestureRejected = true
            }
            super.scrollWheel(event)
            return
        }
        if (delegate == null ||
            !NSEvent.swipeTrackingFromScrollEventsEnabled ||
            !delegate.beginSwipeBack()
        ) {
            scrollGestureRejected = true
            super.scrollWheel(event)
            return
        }

        val session =
            AppKitSwipeBackTrackingSession(
                direction = direction,
                onProgress = delegate::updateSwipeBack,
                onComplete = delegate::finishSwipeBack,
            )
        trackingSession = session
        event.trackSwipeEventWithOptions(
            options = NSEventSwipeTrackingLockDirection or NSEventSwipeTrackingClampGestureAmount,
            dampenAmountThresholdMin = direction.minimumDampenThreshold,
            max = direction.maximumDampenThreshold,
            usingHandler = handler@{ gestureAmount, _, isComplete, stop ->
                if (trackingSession !== session) {
                    stop?.pointed?.value = true
                    return@handler
                }
                if (isComplete && trackingSession === session) trackingSession = null
                session.update(gestureAmount, isComplete)
            },
        )
    }

    override fun swipeWithEvent(event: NSEvent) {
        val isBack =
            isAppKitDiscreteSwipeBack(
                deltaX = event.deltaX,
                deltaY = event.deltaY,
                rightToLeft = userInterfaceLayoutDirection == AppKitRightToLeft,
            )
        if (isBack && swipeBackDelegate?.performDiscreteSwipeBack() == true) return
        super.swipeWithEvent(event)
    }

    fun invalidateSwipeTracking() {
        trackingSession?.invalidate()
        trackingSession = null
        scrollGestureRejected = false
        swipeBackDelegate = null
    }
}

/**
 * Supplies an AppKit controller to [NavigationDisplay][dev.dimension.flare.ui.navigation.NavigationDisplay].
 *
 * The renderer installs one child container controller below [parent]. Each page is then hosted in
 * its own child controller, making this owner safe to use for nested NavigationDisplay instances.
 */
public class AppKitNavigationOwner(
    public val parent: NSViewController,
) : FlareNativeControllerOwner

/** AppKit renderer plugin for Page-only Flare Navigation. */
public object AppKitNavigationRendererPlugin : FlareRendererPlugin<AppKitBackend> {
    override fun register(registrar: FlareWidgetRegistrar<AppKitBackend>) {
        registrar.register(NavigationWidget::class) { AppKitNavigationWidget() }
    }
}

/**
 * This is deliberately a controller-backed widget. [view] is only the container controller's
 * carrier view; it is not a view-managed navigation stack.
 */
internal class AppKitNavigationWidget(
    private val navigationView: AppKitNavigationContainerView = AppKitNavigationContainerView(),
    private val pageAnimationScheduler: AppKitPageAnimationScheduler = DefaultAppKitPageAnimationScheduler,
) : AbstractAppKitWidget<NSView>(navigationView),
    AppKitSwipeBackDelegate,
    NavigationWidget {
    private val container = NSViewController().apply { view = navigationView }
    private val entries = linkedMapOf<NavigationEntryIdentity, AppKitNavigationEntryController>()
    private val viewConstraints = mutableMapOf<AppKitNavigationEntryController, List<NSLayoutConstraint>>()
    private val acknowledgementScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val coordinator =
        NavigationCoordinator(
            emitCommand = ::execute,
            onRetainedEntriesChanged = ::updateRetainedEntries,
        )
    private var owner: AppKitNavigationOwner? = null
    private var subcompositions: FlareSubcompositionFactory? = null
    private var modelDispatcher: NavigationModelDispatcher? = null
    private var stopObservingModels: (() -> Unit)? = null
    private var transitionGeneration: Long = 0L
    private var activePageTransition: AppKitPageSlideTransition? = null
    private var interactionBlockingTransition: AppKitPageSlideTransition? = null
    private var swipeInteraction: AppKitSwipeBackInteraction? = null
    private var acknowledgementJob: Job? = null
    private var disposed: Boolean = false

    init {
        navigationView.swipeBackDelegate = this
    }

    override fun setModelDispatcher(dispatcher: NavigationModelDispatcher) {
        check(!disposed) { "AppKit navigation widget is already disposed." }
        stopObservingModels?.invoke()
        modelDispatcher = dispatcher
        stopObservingModels = dispatcher.observe(::applyModel)
    }

    private fun applyModel(model: NavigationModel) {
        check(!disposed) { "AppKit navigation widget is already disposed." }
        require(model.entries.all { it.presentation == NavigationPresentation.Page }) {
            "AppKit navigation supports only Page presentation; overlays are not available."
        }
        val newOwner =
            model.nativeControllerOwner as? AppKitNavigationOwner
                ?: error(
                    "AppKit NavigationDisplay requires AppKitNavigationOwner from a controller-aware host.",
                )
        bindOwner(newOwner)
        subcompositions = model.subcompositions
        val wasPaused = coordinator.state == NavigationCoordinatorState.Paused
        coordinator.setModel(model)
        if (!coordinator.hasPendingAcknowledgement) {
            clearAcknowledgementTimeout()
        }
        // A fresh applied model is a bounded retry opportunity if the preceding native recovery
        // failed. Retrying here avoids a permanently paused projection without a callback loop.
        if (wasPaused) coordinator.resumeOperations()
        applyContentRetentionPolicyIfStable()
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        transitionGeneration += 1L
        stopObservingModels?.invoke()
        stopObservingModels = null
        modelDispatcher = null
        navigationView.invalidateSwipeTracking()
        activePageTransition?.constraints?.let(NSLayoutConstraint::deactivateConstraints)
        activePageTransition = null
        interactionBlockingTransition = null
        navigationView.blocksPageTransitionInteraction = false
        swipeInteraction = null
        clearAcknowledgementTimeout()
        acknowledgementScope.cancel()
        coordinator.dispose()
        clearPhysicalProjection()
        entries.values.forEach(AppKitNavigationEntryController::dispose)
        entries.clear()
        NSLayoutConstraint.deactivateConstraints(viewConstraints.values.flatten())
        viewConstraints.clear()
        subcompositions = null
        container.removeFromParentViewController()
        owner = null
    }

    private fun bindOwner(value: AppKitNavigationOwner) {
        val current = owner
        when {
            current == null -> {
                owner = value
                value.parent.addChildViewController(container)
            }

            current.parent != value.parent -> {
                error("An AppKit NavigationDisplay cannot move to a different parent controller.")
            }
        }
    }

    private fun updateRetainedEntries(value: List<ResolvedNavigationEntry>) {
        if (disposed) return
        val subcompositions = requireSubcompositions()
        val retained = value.associateBy(ResolvedNavigationEntry::identity)
        val physicalControllers = container.childViewControllers
        value.forEach { entry ->
            entries[entry.identity()]?.update(entry, subcompositions)
                ?: AppKitNavigationEntryController(entry, subcompositions).also {
                    entries[entry.identity()] = it
                }
        }
        val obsolete = entries.keys.filter { it !in retained }
        obsolete.forEach { identity ->
            val controller = checkNotNull(entries[identity])
            check(physicalControllers.none { it == controller }) {
                "AppKit navigation released a controller that is still displayed."
            }
            entries.remove(identity)
            removeController(controller)
            controller.dispose()
        }
    }

    private fun requireSubcompositions(): FlareSubcompositionFactory =
        checkNotNull(subcompositions) {
            "Navigation entries must be retained only after their NavigationModel is installed."
        }

    private fun execute(command: NavigationCommand) {
        check(activePageTransition == null) {
            "AppKit navigation command overlapped an active page transition."
        }
        try {
            when (val operation = command.operation) {
                is NavigationOperation.Reconstruct -> {
                    reconstruct(command, operation.targetStack)
                }

                is NavigationOperation.PushPage -> {
                    push(command, operation)
                }

                is NavigationOperation.PopPage -> {
                    pop(command, operation)
                }

                is NavigationOperation.PresentOverlay,
                is NavigationOperation.DismissOverlay,
                -> {
                    error("AppKit navigation supports only Page presentation; overlay operation received.")
                }
            }
        } catch (error: Throwable) {
            coordinator.completeCommand(command.token, NavigationOperationResult.Failed(error))
        }
    }

    private fun reconstruct(
        command: NavigationCommand,
        target: List<ResolvedNavigationEntry>,
    ) {
        finishCommand(command) {
            clearPhysicalProjection()
            val controllers = target.map(::controllerFor)
            controllers.forEach(container::addChildViewController)
            controllers.lastOrNull()?.let(::installInitialView)
            applyContentRetentionPolicy()
        }
    }

    private fun push(
        command: NavigationCommand,
        operation: NavigationOperation.PushPage,
    ) {
        val projection = physicalControllers()
        val from =
            projection.lastOrNull()
                ?: error("AppKit navigation cannot push without a displayed root controller.")
        val to = controllerFor(operation.entry)
        check(to !in projection) { "AppKit navigation attempted to push an existing page controller." }
        val generation = ++transitionGeneration
        var transition: AppKitPageSlideTransition? = null
        var addedController = false
        try {
            container.addChildViewController(to)
            addedController = true
            val prepared = preparePagePushTransition(from = from, to = to)
            transition = prepared
            activePageTransition = prepared
            animatePageTransitionToEnd(
                transition = prepared,
                kind = AppKitPageTransitionKind.Push,
            ) completion@{
                if (
                    disposed ||
                    generation != transitionGeneration ||
                    activePageTransition !== prepared
                ) {
                    return@completion
                }
                activePageTransition = null
                finishCommand(command) {
                    finalizePagePushTransition(prepared, committed = true)
                }
            }
        } catch (error: Throwable) {
            if (transition != null && activePageTransition === transition) {
                activePageTransition = null
                runCatching {
                    finalizePagePushTransition(transition, committed = false)
                }.exceptionOrNull()?.let(error::addSuppressed)
            } else if (transition == null && addedController) {
                runCatching {
                    removeController(to)
                    if (from.view.superview == null) navigationView.addSubview(from.view)
                    pin(from)
                    applyContentRetentionPolicy()
                    navigationView.layoutSubtreeIfNeeded()
                }.exceptionOrNull()?.let(error::addSuppressed)
            }
            throw error
        }
    }

    private fun pop(
        command: NavigationCommand,
        operation: NavigationOperation.PopPage,
    ) {
        val projection = physicalControllers()
        val from =
            projection.lastOrNull()
                ?: error("AppKit navigation cannot pop without a displayed page controller.")
        check(from.identity == operation.entry.identity()) {
            "AppKit navigation pop source does not match the displayed page controller."
        }
        val to =
            projection.getOrNull(projection.lastIndex - 1)
                ?: error("AppKit navigation cannot pop its root page controller.")
        val generation = ++transitionGeneration
        val transition = preparePagePopTransition(from = from, to = to)
        activePageTransition = transition
        try {
            animatePageTransitionToEnd(
                transition = transition,
                kind = AppKitPageTransitionKind.Pop,
            ) completion@{
                if (
                    disposed ||
                    generation != transitionGeneration ||
                    activePageTransition !== transition
                ) {
                    return@completion
                }
                activePageTransition = null
                finishCommand(command) {
                    finalizePagePopTransition(transition, committed = true)
                }
            }
        } catch (error: Throwable) {
            if (activePageTransition === transition) {
                activePageTransition = null
                runCatching {
                    finalizePagePopTransition(transition, committed = false)
                }.exceptionOrNull()?.let(error::addSuppressed)
            }
            throw error
        }
    }

    override fun canBeginSwipeBack(): Boolean =
        !disposed &&
            activePageTransition == null &&
            swipeInteraction == null &&
            modelDispatcher?.hasUndeliveredModel != true &&
            coordinator.canBeginUserBack()

    override fun beginSwipeBack(): Boolean {
        if (!canBeginSwipeBack()) return false
        val handle = coordinator.beginUserBack() ?: return false
        var preparedTransition: AppKitPageSlideTransition? = null

        return try {
            val projection = physicalControllers()
            val source =
                projection.lastOrNull()
                    ?: error("AppKit navigation cannot begin swipe-back without a displayed page.")
            val destination =
                projection.getOrNull(projection.lastIndex - 1)
                    ?: error("AppKit navigation cannot swipe back from its root page.")
            val transition = preparePagePopTransition(from = source, to = destination)
            preparedTransition = transition
            val interaction =
                AppKitSwipeBackInteraction(
                    handle = handle,
                    transition = transition,
                )
            activePageTransition = transition
            swipeInteraction = interaction
            true
        } catch (_: Throwable) {
            swipeInteraction = null
            preparedTransition?.let { transition ->
                if (activePageTransition === transition) activePageTransition = null
                runCatching {
                    finalizePagePopTransition(transition, committed = false)
                }
            }
            coordinator.failUserBack(handle)
            false
        }
    }

    override fun updateSwipeBack(progress: Double) {
        val interaction = swipeInteraction ?: return
        val transition = interaction.transition
        if (activePageTransition !== transition) return
        setPageTransitionProgress(transition, AppKitPageTransitionKind.Pop, progress)
        navigationView.layoutSubtreeIfNeeded()
    }

    override fun finishSwipeBack(committed: Boolean) {
        val interaction = swipeInteraction ?: return
        val transition = interaction.transition
        swipeInteraction = null
        stopBlockingPageTransitionInteraction(transition)
        if (activePageTransition === transition) activePageTransition = null
        val finalized =
            try {
                finalizePagePopTransition(transition, committed)
                true
            } catch (_: Throwable) {
                false
            }
        if (!finalized) {
            coordinator.failUserBack(interaction.handle)
            return
        }

        if (committed) {
            coordinator.commitUserBack(interaction.handle)?.let(::scheduleAcknowledgementTimeout)
        } else {
            coordinator.cancelUserBack(interaction.handle)
        }
    }

    override fun performDiscreteSwipeBack(): Boolean {
        if (!beginSwipeBack()) return false
        val interaction = swipeInteraction ?: return false
        val transition = interaction.transition
        return try {
            animatePageTransitionToEnd(
                transition = transition,
                kind = AppKitPageTransitionKind.Pop,
            ) {
                if (
                    !disposed &&
                    activePageTransition === transition &&
                    swipeInteraction === interaction
                ) {
                    finishSwipeBack(committed = true)
                }
            }
            true
        } catch (_: Throwable) {
            if (swipeInteraction === interaction) {
                swipeInteraction = null
                if (activePageTransition === transition) activePageTransition = null
                val restored =
                    runCatching {
                        finalizePagePopTransition(transition, committed = false)
                    }.isSuccess
                if (restored) {
                    coordinator.cancelUserBack(interaction.handle)
                } else {
                    coordinator.failUserBack(interaction.handle)
                }
            }
            false
        }
    }

    private fun preparePagePushTransition(
        from: AppKitNavigationEntryController,
        to: AppKitNavigationEntryController,
    ): AppKitPageSlideTransition {
        check(activePageTransition == null) {
            "AppKit navigation cannot prepare two page transitions at once."
        }
        check(from != to) { "AppKit navigation page-push source and destination must differ." }
        var transition: AppKitPageSlideTransition? = null
        try {
            from.realizeContent()
            to.realizeContent()
            val fromView = from.view
            val toView = to.view
            unpin(from)
            unpin(to)
            fromView.translatesAutoresizingMaskIntoConstraints = false
            toView.translatesAutoresizingMaskIntoConstraints = false
            navigationView.addSubview(toView, positioned = NSWindowAbove, relativeTo = fromView)

            val prepared = createPageSlideTransition(from = from, to = to)
            transition = prepared
            setPageTransitionProgress(prepared, AppKitPageTransitionKind.Push, progress = 0.0)
            NSLayoutConstraint.activateConstraints(prepared.constraints)
            navigationView.layoutSubtreeIfNeeded()
            return prepared
        } catch (error: Throwable) {
            transition?.constraints?.let(NSLayoutConstraint::deactivateConstraints)
            runCatching {
                to.view.removeFromSuperview()
                if (from.view.superview == null) navigationView.addSubview(from.view)
                pin(from)
                navigationView.layoutSubtreeIfNeeded()
            }.exceptionOrNull()?.let(error::addSuppressed)
            throw error
        }
    }

    private fun preparePagePopTransition(
        from: AppKitNavigationEntryController,
        to: AppKitNavigationEntryController,
    ): AppKitPageSlideTransition {
        check(activePageTransition == null) {
            "AppKit navigation cannot prepare two page transitions at once."
        }
        check(from != to) { "AppKit navigation page-pop source and destination must differ." }
        var transition: AppKitPageSlideTransition? = null
        try {
            from.realizeContent()
            to.realizeContent()
            val fromView = from.view
            val toView = to.view
            unpin(from)
            unpin(to)
            fromView.translatesAutoresizingMaskIntoConstraints = false
            toView.translatesAutoresizingMaskIntoConstraints = false
            navigationView.addSubview(toView, positioned = NSWindowBelow, relativeTo = fromView)

            val prepared = createPageSlideTransition(from = from, to = to)
            transition = prepared
            setPageTransitionProgress(prepared, AppKitPageTransitionKind.Pop, progress = 0.0)
            NSLayoutConstraint.activateConstraints(prepared.constraints)
            navigationView.layoutSubtreeIfNeeded()
            return prepared
        } catch (error: Throwable) {
            transition?.constraints?.let(NSLayoutConstraint::deactivateConstraints)
            runCatching {
                to.view.removeFromSuperview()
                pin(from)
                applyContentRetentionPolicy()
                navigationView.layoutSubtreeIfNeeded()
            }.exceptionOrNull()?.let(error::addSuppressed)
            throw error
        }
    }

    private fun createPageSlideTransition(
        from: AppKitNavigationEntryController,
        to: AppKitNavigationEntryController,
    ): AppKitPageSlideTransition {
        val fromView = from.view
        val toView = to.view
        val fromLeading = fromView.leadingAnchor.constraintEqualToAnchor(navigationView.leadingAnchor)
        val toLeading = toView.leadingAnchor.constraintEqualToAnchor(navigationView.leadingAnchor)
        return AppKitPageSlideTransition(
            from = from,
            to = to,
            fromLeading = fromLeading,
            toLeading = toLeading,
            constraints =
                listOf(
                    fromLeading,
                    fromView.widthAnchor.constraintEqualToAnchor(navigationView.widthAnchor),
                    fromView.topAnchor.constraintEqualToAnchor(navigationView.topAnchor),
                    fromView.bottomAnchor.constraintEqualToAnchor(navigationView.bottomAnchor),
                    toLeading,
                    toView.widthAnchor.constraintEqualToAnchor(navigationView.widthAnchor),
                    toView.topAnchor.constraintEqualToAnchor(navigationView.topAnchor),
                    toView.bottomAnchor.constraintEqualToAnchor(navigationView.bottomAnchor),
                ),
        )
    }

    private fun setPageTransitionProgress(
        transition: AppKitPageSlideTransition,
        kind: AppKitPageTransitionKind,
        progress: Double,
    ) {
        val width = navigationView.bounds.useContents { size.width }
        val offsets =
            appKitPageTransitionOffsets(
                kind = kind,
                progress = progress,
                width = width,
            )
        transition.fromLeading.constant = offsets.source
        transition.toLeading.constant = offsets.destination
    }

    private fun animatePageTransitionToEnd(
        transition: AppKitPageSlideTransition,
        kind: AppKitPageTransitionKind,
        completion: () -> Unit,
    ) {
        check(interactionBlockingTransition == null) {
            "AppKit navigation cannot block interaction for two page transitions at once."
        }
        interactionBlockingTransition = transition
        navigationView.blocksPageTransitionInteraction = true
        try {
            pageAnimationScheduler.animate(
                view = navigationView,
                durationSeconds = APPKIT_PAGE_TRANSITION_DURATION_SECONDS,
                updateProgress = { progress ->
                    if (activePageTransition === transition) {
                        setPageTransitionProgress(transition, kind, progress)
                    }
                },
                completion = {
                    stopBlockingPageTransitionInteraction(transition)
                    completion()
                },
            )
        } catch (error: Throwable) {
            stopBlockingPageTransitionInteraction(transition)
            throw error
        }
    }

    private fun stopBlockingPageTransitionInteraction(transition: AppKitPageSlideTransition) {
        if (interactionBlockingTransition !== transition) return
        interactionBlockingTransition = null
        navigationView.blocksPageTransitionInteraction = false
    }

    private fun finalizePagePushTransition(
        transition: AppKitPageSlideTransition,
        committed: Boolean,
    ) {
        NSLayoutConstraint.deactivateConstraints(transition.constraints)
        if (committed) {
            val controllers = physicalControllers()
            check(
                controllers.lastOrNull() == transition.to &&
                    controllers.getOrNull(controllers.lastIndex - 1) == transition.from,
            ) {
                "AppKit page-push controllers are no longer at the top of the physical stack."
            }
            transition.from.view.removeFromSuperview()
            pin(transition.to)
        } else {
            removeController(transition.to)
            if (transition.from.view.superview == null) navigationView.addSubview(transition.from.view)
            pin(transition.from)
        }
        applyContentRetentionPolicy()
        navigationView.layoutSubtreeIfNeeded()
    }

    private fun finalizePagePopTransition(
        transition: AppKitPageSlideTransition,
        committed: Boolean,
    ) {
        NSLayoutConstraint.deactivateConstraints(transition.constraints)
        if (committed) {
            check(physicalControllers().lastOrNull() == transition.from) {
                "AppKit page-pop source is no longer the displayed page."
            }
            removeController(transition.from)
            pin(transition.to)
        } else {
            transition.to.view.removeFromSuperview()
            pin(transition.from)
        }
        applyContentRetentionPolicy()
        navigationView.layoutSubtreeIfNeeded()
    }

    private fun scheduleAcknowledgementTimeout(handle: NavigationAcknowledgementHandle) {
        clearAcknowledgementTimeout()
        acknowledgementJob =
            acknowledgementScope.launch {
                delay(NAVIGATION_ACKNOWLEDGEMENT_TIMEOUT_MILLIS)
                acknowledgementJob = null
                if (!disposed) coordinator.acknowledgementDeadlineReached(handle)
            }
    }

    private fun clearAcknowledgementTimeout() {
        acknowledgementJob?.cancel()
        acknowledgementJob = null
    }

    private inline fun finishCommand(
        command: NavigationCommand,
        mutation: () -> Unit,
    ) {
        val result =
            try {
                mutation()
                NavigationOperationResult.Succeeded(observedTopology())
            } catch (error: Throwable) {
                NavigationOperationResult.Failed(error)
            }
        coordinator.completeCommand(
            token = command.token,
            result = result,
        )
    }

    private fun observedTopology(): List<NavigationEntryIdentity> {
        val controllers = physicalControllers()
        val visibleView = navigationView.subviews.singleOrNull()
        val expectedView = controllers.lastOrNull()?.view
        // Objective-C collections may vend a new Kotlin wrapper for the same native object, so
        // NSObject equality is the correct identity check here rather than Kotlin referential
        // equality.
        check(visibleView == expectedView) {
            "AppKit navigation visible view does not match its physical controller stack."
        }
        return controllers.map(AppKitNavigationEntryController::identity)
    }

    private fun physicalControllers(): List<AppKitNavigationEntryController> =
        container.childViewControllers.map { controller ->
            val entryController =
                controller as? AppKitNavigationEntryController
                    ?: error("AppKit navigation container contains a controller not owned by Flare navigation.")
            check(entries[entryController.identity] == entryController) {
                "AppKit navigation container contains an entry controller owned by another projection."
            }
            entryController
        }

    private fun clearPhysicalProjection() {
        NSLayoutConstraint.deactivateConstraints(viewConstraints.values.flatten())
        viewConstraints.clear()
        navigationView.subviews.map { it as NSView }.forEach { it.removeFromSuperview() }
        container.childViewControllers.map { it as NSViewController }.asReversed().forEach { controller ->
            if (controller is AppKitNavigationEntryController) {
                removeController(controller)
                if (entries[controller.identity] != controller) controller.dispose()
            } else {
                controller.view.removeFromSuperview()
                controller.removeFromParentViewController()
            }
        }
    }

    /** Keeps the current page active and one frozen predecessor ready for interactive back. */
    private fun applyContentRetentionPolicyIfStable() {
        if (disposed || activePageTransition != null || swipeInteraction != null) return
        applyContentRetentionPolicy()
    }

    private fun applyContentRetentionPolicy() {
        val controllers = physicalControllers()
        controllers.forEachIndexed { index, controller ->
            when (index) {
                controllers.lastIndex -> controller.realizeContent()
                controllers.lastIndex - 1 -> controller.deactivateContent()
                else -> controller.releaseContent()
            }
        }
    }

    private fun controllerFor(entry: ResolvedNavigationEntry): AppKitNavigationEntryController =
        checkNotNull(entries[entry.identity()]) {
            "AppKit navigation has no retained controller for ${entry.identity()}."
        }

    private fun installInitialView(controller: AppKitNavigationEntryController) {
        controller.realizeContent()
        val entryView = controller.view
        entryView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(entryView)
        pin(controller)
    }

    private fun pin(controller: AppKitNavigationEntryController) {
        if (viewConstraints.containsKey(controller)) return
        val entryView = controller.view
        entryView.translatesAutoresizingMaskIntoConstraints = false
        val constraints =
            listOf(
                entryView.leadingAnchor.constraintEqualToAnchor(view.leadingAnchor),
                entryView.trailingAnchor.constraintEqualToAnchor(view.trailingAnchor),
                entryView.topAnchor.constraintEqualToAnchor(view.topAnchor),
                entryView.bottomAnchor.constraintEqualToAnchor(view.bottomAnchor),
            )
        viewConstraints[controller] = constraints
        NSLayoutConstraint.activateConstraints(constraints)
    }

    private fun unpin(controller: AppKitNavigationEntryController) {
        NSLayoutConstraint.deactivateConstraints(viewConstraints.remove(controller).orEmpty())
    }

    private fun removeController(controller: AppKitNavigationEntryController) {
        unpin(controller)
        controller.view.removeFromSuperview()
        controller.removeFromParentViewController()
        controller.releaseContent()
    }
}

private data class AppKitPageSlideTransition(
    val from: AppKitNavigationEntryController,
    val to: AppKitNavigationEntryController,
    val fromLeading: NSLayoutConstraint,
    val toLeading: NSLayoutConstraint,
    val constraints: List<NSLayoutConstraint>,
)

private data class AppKitSwipeBackInteraction(
    val handle: NavigationInteractionHandle,
    val transition: AppKitPageSlideTransition,
)

private const val APPKIT_PAGE_TRANSITION_DURATION_SECONDS: Double = 0.2

internal class AppKitNavigationEntryController(
    initialEntry: ResolvedNavigationEntry,
    private var subcompositions: FlareSubcompositionFactory,
) : NSViewController(nibName = null, bundle = null) {
    private val contentRoot =
        NSStackView().apply {
            orientation = NSUserInterfaceLayoutOrientationVertical
            wantsLayer = true
            layer?.backgroundColor = NSColor.windowBackgroundColor.CGColor
        }
    private var entry: ResolvedNavigationEntry = initialEntry
    private var contentHost: NavigationEntryContentHost? = null
    private var disposed: Boolean = false

    val identity: NavigationEntryIdentity
        get() = entry.identity()

    init {
        view = contentRoot
    }

    fun update(
        entry: ResolvedNavigationEntry,
        subcompositions: FlareSubcompositionFactory = this.subcompositions,
    ) {
        check(!disposed) { "AppKit navigation entry controller is already disposed." }
        require(entry.identity() == identity) {
            "An AppKit navigation entry controller cannot change identity."
        }
        if (this.subcompositions !== subcompositions) {
            releaseContent()
            this.subcompositions = subcompositions
        }
        this.entry = entry
        contentHost?.update(entry)
    }

    internal fun realizeContent() {
        check(!disposed) { "AppKit navigation entry controller is already disposed." }
        ensureContentHost().activate()
    }

    internal fun deactivateContent() {
        check(!disposed) { "AppKit navigation entry controller is already disposed." }
        ensureContentHost().deactivate()
    }

    private fun ensureContentHost(): NavigationEntryContentHost =
        contentHost
            ?: NavigationEntryContentHost(
                root = AppKitChildren(contentRoot),
                nativeControllerOwner = AppKitNavigationOwner(this),
                subcompositions = subcompositions,
                initialEntry = entry,
            ).also { contentHost = it }

    internal fun releaseContent() {
        contentHost?.dispose()
        contentHost = null
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        releaseContent()
    }
}
