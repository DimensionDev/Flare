@file:OptIn(
    dev.dimension.flare.ui.LowLevelFlareApi::class,
    dev.dimension.flare.ui.navigation.ExperimentalFlareNavigation::class,
    kotlinx.cinterop.ExperimentalForeignApi::class,
)

package dev.dimension.flare.ui.navigation

import dev.dimension.flare.ui.FlareNativeControllerOwner
import dev.dimension.flare.ui.FlareRendererPlugin
import dev.dimension.flare.ui.FlareSubcompositionFactory
import dev.dimension.flare.ui.FlareWidgetRegistrar
import dev.dimension.flare.ui.uikit.AbstractUIKitWidget
import dev.dimension.flare.ui.uikit.UIKitBackend
import dev.dimension.flare.ui.uikit.UIKitChildren
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.Foundation.NSProcessInfo
import platform.UIKit.NSLayoutConstraint
import platform.UIKit.UIColor
import platform.UIKit.UIGestureRecognizer
import platform.UIKit.UIGestureRecognizerDelegateProtocol
import platform.UIKit.UILayoutConstraintAxisHorizontal
import platform.UIKit.UINavigationController
import platform.UIKit.UINavigationControllerDelegateProtocol
import platform.UIKit.UIStackView
import platform.UIKit.UIStackViewAlignmentTop
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.UIKit.addChildViewController
import platform.UIKit.didMoveToParentViewController
import platform.UIKit.removeFromParentViewController
import platform.UIKit.systemBackgroundColor
import platform.UIKit.transitionCoordinator
import platform.UIKit.willMoveToParentViewController
import platform.darwin.NSObject

/**
 * Supplies the UIKit parent controller that owns a [NavigationDisplay].
 *
 * Pass this owner to [dev.dimension.flare.ui.uikit.FlareUIKitHost]. The navigation renderer adds
 * its `UINavigationController` as a child of [parent], while each Page receives its own owner.
 */
public class UIKitNavigationOwner(
    public val parent: UIViewController,
) : FlareNativeControllerOwner

/** UIKit renderer for Page-only [NavigationDisplay] stacks. */
public object UIKitNavigationRendererPlugin : FlareRendererPlugin<UIKitBackend> {
    override fun register(registrar: FlareWidgetRegistrar<UIKitBackend>) {
        registrar.register(NavigationWidget::class) { UIKitNavigationWidget() }
    }
}

internal class UIKitNavigationWidget(
    internal val navigationController: UINavigationController = UINavigationController(),
) : AbstractUIKitWidget<UIView>(UIView()),
    NavigationWidget {
    private val controllers = linkedMapOf<NavigationEntryIdentity, UIKitNavigationEntryController>()
    private val delegate = UIKitNavigationDelegate(this)
    private val interactivePopDelegate = UIKitInteractivePopGestureDelegate(this)
    private val acknowledgementScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val coordinator =
        NavigationCoordinator(
            emitCommand = ::execute,
            onRetainedEntriesChanged = ::updateRetainedEntries,
        )
    private var parent: UIViewController? = null
    private var subcompositions: FlareSubcompositionFactory? = null
    private var modelDispatcher: NavigationModelDispatcher? = null
    private var stopObservingModels: (() -> Unit)? = null
    private var pendingCommand: NavigationCommand? = null
    private var interaction: NavigationInteractionHandle? = null
    private var interactionTargetIdentity: NavigationEntryIdentity? = null
    private var acknowledgementJob: Job? = null
    private var disposed: Boolean = false

    init {
        navigationController.delegate = delegate
        navigationController.setNavigationBarHidden(hidden = true, animated = false)
    }

    override fun setModelDispatcher(dispatcher: NavigationModelDispatcher) {
        check(!disposed) { "UIKit navigation widget is already disposed." }
        stopObservingModels?.invoke()
        modelDispatcher = dispatcher
        stopObservingModels = dispatcher.observe(::applyModel)
    }

    private fun applyModel(model: NavigationModel) {
        check(!disposed) { "UIKit navigation widget is already disposed." }
        require(model.entries.all { it.presentation == NavigationPresentation.Page }) {
            "UIKitNavigationRendererPlugin supports only Page presentation."
        }
        val owner =
            model.nativeControllerOwner as? UIKitNavigationOwner
                ?: error(
                    "UIKitNavigationRendererPlugin requires UIKitNavigationOwner. Pass " +
                        "UIKitNavigationOwner(parentViewController) to FlareUIKitHost.",
                )
        validateParent(owner.parent)
        subcompositions = model.subcompositions
        attachTo(owner.parent)
        val wasPaused = coordinator.state == NavigationCoordinatorState.Paused
        coordinator.setModel(model)
        if (!coordinator.hasPendingAcknowledgement) {
            clearAcknowledgementTimeout()
        }
        // A fresh applied model is a bounded retry opportunity if the preceding native recovery
        // failed. Retrying here avoids a permanently paused projection without a callback loop.
        if (wasPaused) coordinator.resumeOperations()
        applyContentRetentionPolicyIfStable()
        updateInteractivePopAvailability()
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        stopObservingModels?.invoke()
        stopObservingModels = null
        modelDispatcher = null
        pendingCommand = null
        interaction = null
        interactionTargetIdentity = null
        clearAcknowledgementTimeout()
        acknowledgementScope.cancel()
        coordinator.dispose()
        updateInteractivePopAvailability()
        clearInteractivePopGestureDelegates()
        navigationController.delegate = null
        navigationController.willMoveToParentViewController(null)
        navigationController.view.removeFromSuperview()
        navigationController.removeFromParentViewController()
        parent = null
    }

    private fun attachTo(value: UIViewController) {
        validateParent(value)
        if (parent != null) return
        parent = value
        value.addChildViewController(navigationController)
        val navigationView = navigationController.view
        navigationView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(navigationView)
        NSLayoutConstraint.activateConstraints(
            listOf(
                navigationView.leadingAnchor.constraintEqualToAnchor(view.leadingAnchor),
                navigationView.trailingAnchor.constraintEqualToAnchor(view.trailingAnchor),
                navigationView.topAnchor.constraintEqualToAnchor(view.topAnchor),
                navigationView.bottomAnchor.constraintEqualToAnchor(view.bottomAnchor),
            ),
        )
        installInteractivePopGestureDelegates()
        navigationController.didMoveToParentViewController(value)
    }

    private fun validateParent(value: UIViewController) {
        val previous = parent
        // Objective-C collections and properties may vend a different Kotlin wrapper for the same
        // native controller. NSObject equality is the stable identity boundary here.
        check(previous == null || previous == value) {
            "A UIKit navigation widget cannot move between UIViewController parents."
        }
    }

    private fun updateRetainedEntries(entries: List<ResolvedNavigationEntry>) {
        if (disposed) {
            controllers.values.forEach(UIKitNavigationEntryController::dispose)
            controllers.clear()
            return
        }
        val subcompositions = requireSubcompositions()
        val retained = entries.associateBy(ResolvedNavigationEntry::identity)
        val physicalControllers = navigationController.viewControllers
        controllers.entries
            .filter { (identity, _) -> identity !in retained }
            .forEach { (identity, controller) ->
                check(physicalControllers.none { it == controller }) {
                    "UIKit navigation released a controller that is still displayed."
                }
                controllers.remove(identity)
                controller.dispose()
            }
        retained.forEach { (identity, entry) ->
            controllers[identity]?.update(entry, subcompositions)
        }
    }

    private fun requireSubcompositions(): FlareSubcompositionFactory =
        checkNotNull(subcompositions) {
            "Navigation entries must be retained only after their NavigationModel is installed."
        }

    private fun execute(command: NavigationCommand) {
        check(pendingCommand == null) { "UIKit navigation command overlap." }
        pendingCommand = command
        updateInteractivePopAvailability()
        try {
            when (val operation = command.operation) {
                is NavigationOperation.PushPage -> {
                    val projection = physicalControllers()
                    val from =
                        projection.lastOrNull()
                            ?: error("UIKit navigation cannot push without a displayed root controller.")
                    val to = controllerFor(operation.entry)
                    check(projection.none { it == to }) {
                        "UIKit navigation attempted to push an existing page controller."
                    }
                    from.realizeContent()
                    to.realizeContent()
                    navigationController.pushViewController(
                        viewController = to,
                        animated = operation.animated,
                    )
                }

                is NavigationOperation.PopPage -> {
                    val projection = physicalControllers()
                    val from =
                        projection.lastOrNull()
                            ?: error("UIKit navigation cannot pop without a displayed page controller.")
                    check(from.identity == operation.entry.identity()) {
                        "UIKit navigation pop source does not match the displayed page controller."
                    }
                    val to =
                        projection.getOrNull(projection.lastIndex - 1)
                            ?: error("UIKit navigation cannot pop its root page controller.")
                    from.realizeContent()
                    to.realizeContent()
                    val popped =
                        checkNotNull(
                            navigationController.popViewControllerAnimated(operation.animated),
                        ) {
                            "UIKit navigation controller rejected a requested page pop."
                        }
                    check(popped == from) {
                        "UIKit navigation controller popped a different page than requested."
                    }
                }

                is NavigationOperation.Reconstruct -> {
                    navigationController.setViewControllers(
                        viewControllers = operation.targetStack.map(::controllerFor),
                        animated = operation.animated,
                    )
                }

                is NavigationOperation.PresentOverlay,
                is NavigationOperation.DismissOverlay,
                -> {
                    error("UIKitNavigationRendererPlugin supports only Page presentation.")
                }
            }
            if (!command.operation.animated) {
                completePendingCommandIfNeeded(expectedToken = command.token)
            }
        } catch (error: Throwable) {
            // A UIKit delegate is allowed to complete synchronously. Do not clear a newer command
            // if the native call throws after invoking that delegate.
            if (pendingCommand?.token == command.token) {
                pendingCommand = null
                coordinator.completeCommand(command.token, NavigationOperationResult.Failed(error))
            }
            applyContentRetentionPolicyIfStable()
            updateInteractivePopAvailability()
        }
    }

    private fun controllerFor(entry: ResolvedNavigationEntry): UIKitNavigationEntryController =
        controllers
            .getOrPut(entry.identity()) {
                UIKitNavigationEntryController(
                    initialEntry = entry,
                    subcompositions = requireSubcompositions(),
                )
            }.also { it.update(entry, requireSubcompositions()) }

    private fun completePendingCommandIfNeeded(expectedToken: Long? = null) {
        val command = pendingCommand ?: return
        if (expectedToken != null && command.token != expectedToken) return
        val result =
            try {
                NavigationOperationResult.Succeeded(observedTopology())
            } catch (error: Throwable) {
                NavigationOperationResult.Failed(error)
            }
        pendingCommand = null
        coordinator.completeCommand(
            token = command.token,
            result = result,
        )
        applyContentRetentionPolicyIfStable()
        updateInteractivePopAvailability()
    }

    private fun observedTopology(): List<NavigationEntryIdentity> = physicalControllers().map(UIKitNavigationEntryController::identity)

    private fun physicalControllers(): List<UIKitNavigationEntryController> =
        navigationController.viewControllers.map { controller ->
            val entryController =
                controller as? UIKitNavigationEntryController
                    ?: error("UIKit navigation stack contains a controller not owned by Flare navigation.")
            check(controllers[entryController.identity] == entryController) {
                "UIKit navigation stack contains an entry controller owned by another projection."
            }
            entryController
        }

    /** Keeps the current page active and one frozen predecessor ready for interactive back. */
    private fun applyContentRetentionPolicyIfStable() {
        if (disposed || pendingCommand != null || interaction != null) return
        val projection = physicalControllers()
        projection.forEachIndexed { index, controller ->
            when (index) {
                projection.lastIndex -> controller.realizeContent()
                projection.lastIndex - 1 -> controller.deactivateContent()
                else -> controller.releaseContent()
            }
        }
    }

    internal fun beginUserPop(target: UIViewController): NavigationInteractionHandle? {
        if (pendingCommand != null || modelDispatcher?.hasUndeliveredModel == true) return null
        val entryController = target as? UIKitNavigationEntryController ?: return null
        val targetIdentity = entryController.identity
        if (controllers[targetIdentity] != entryController) return null
        val targetIndex =
            coordinator.projectedEntries.indexOfFirst { entry ->
                entry.identity() == targetIdentity
            }
        if (targetIndex < 0) return null
        val popCount = coordinator.projectedEntries.lastIndex - targetIndex
        if (popCount <= 0) return null
        return coordinator.beginUserBack(popCount)?.also {
            physicalControllers().lastOrNull()?.realizeContent()
            entryController.realizeContent()
            interaction = it
            interactionTargetIdentity = targetIdentity
        }
    }

    internal fun finishUserPop(cancelled: Boolean) {
        val handle = interaction ?: return
        interaction = null
        interactionTargetIdentity = null
        if (cancelled) {
            coordinator.cancelUserBack(handle)
            applyContentRetentionPolicyIfStable()
            updateInteractivePopAvailability()
            return
        }
        coordinator.commitUserBack(handle)?.let(::scheduleAcknowledgementTimeout)
        applyContentRetentionPolicyIfStable()
        updateInteractivePopAvailability()
    }

    private fun scheduleAcknowledgementTimeout(handle: NavigationAcknowledgementHandle) {
        clearAcknowledgementTimeout()
        acknowledgementJob =
            acknowledgementScope.launch {
                delay(NAVIGATION_ACKNOWLEDGEMENT_TIMEOUT_MILLIS)
                acknowledgementJob = null
                if (!disposed) {
                    coordinator.acknowledgementDeadlineReached(handle)
                    applyContentRetentionPolicyIfStable()
                    updateInteractivePopAvailability()
                }
            }
    }

    private fun clearAcknowledgementTimeout() {
        acknowledgementJob?.cancel()
        acknowledgementJob = null
    }

    internal fun finishUserPopIfNeeded(shown: UIViewController) {
        val targetIdentity = interactionTargetIdentity ?: return
        val shownController = shown as? UIKitNavigationEntryController
        finishUserPop(
            cancelled =
                shownController == null ||
                    shownController.identity != targetIdentity ||
                    controllers[targetIdentity] != shownController,
        )
    }

    private fun canBeginInteractivePop(): Boolean =
        !disposed &&
            pendingCommand == null &&
            interaction == null &&
            modelDispatcher?.hasUndeliveredModel != true &&
            coordinator.canBeginUserBack()

    private fun installInteractivePopGestureDelegates() {
        navigationController.interactivePopGestureRecognizer?.delegate = interactivePopDelegate
        interactiveContentPopGestureRecognizer()?.delegate = interactivePopDelegate
    }

    private fun updateInteractivePopAvailability() {
        if (interaction != null) return
        val enabled = !disposed
        navigationController.interactivePopGestureRecognizer?.enabled = enabled
        interactiveContentPopGestureRecognizer()?.enabled = enabled
    }

    private fun clearInteractivePopGestureDelegates() {
        navigationController.interactivePopGestureRecognizer?.let { gesture ->
            if (gesture.delegate === interactivePopDelegate) {
                gesture.delegate = null
            }
        }
        interactiveContentPopGestureRecognizer()?.let { gesture ->
            if (gesture.delegate === interactivePopDelegate) {
                gesture.delegate = null
            }
        }
    }

    private fun interactiveContentPopGestureRecognizer(): UIGestureRecognizer? {
        val isIOS26OrLater =
            NSProcessInfo.processInfo.operatingSystemVersion.useContents { majorVersion >= 26 }
        return if (isIOS26OrLater) {
            navigationController.interactiveContentPopGestureRecognizer
        } else {
            null
        }
    }

    private class UIKitNavigationDelegate(
        private val widget: UIKitNavigationWidget,
    ) : NSObject(),
        UINavigationControllerDelegateProtocol {
        @ObjCSignatureOverride
        override fun navigationController(
            navigationController: UINavigationController,
            willShowViewController: UIViewController,
            animated: Boolean,
        ) {
            val transition = navigationController.transitionCoordinator
            widget.beginUserPop(willShowViewController) ?: return
            transition?.animateAlongsideTransition(
                animation = null,
                completion = { context -> widget.finishUserPop(context?.isCancelled() ?: true) },
            )
        }

        @ObjCSignatureOverride
        override fun navigationController(
            navigationController: UINavigationController,
            didShowViewController: UIViewController,
            animated: Boolean,
        ) {
            widget.completePendingCommandIfNeeded()
            widget.finishUserPopIfNeeded(didShowViewController)
        }
    }

    private class UIKitInteractivePopGestureDelegate(
        private val widget: UIKitNavigationWidget,
    ) : NSObject(),
        UIGestureRecognizerDelegateProtocol {
        @ObjCSignatureOverride
        override fun gestureRecognizerShouldBegin(gestureRecognizer: UIGestureRecognizer): Boolean = widget.canBeginInteractivePop()
    }
}

internal class UIKitNavigationEntryController(
    initialEntry: ResolvedNavigationEntry,
    private var subcompositions: FlareSubcompositionFactory,
) : UIViewController(nibName = null, bundle = null) {
    private val contentView =
        UIStackView().apply {
            axis = UILayoutConstraintAxisHorizontal
            alignment = UIStackViewAlignmentTop
            backgroundColor = UIColor.systemBackgroundColor
        }
    private var contentHost: NavigationEntryContentHost? = null
    private var entry: ResolvedNavigationEntry = initialEntry
    private var disposed: Boolean = false

    internal val identity: NavigationEntryIdentity
        get() = entry.identity()

    override fun loadView() {
        view = contentView
    }

    fun update(
        entry: ResolvedNavigationEntry,
        subcompositions: FlareSubcompositionFactory = this.subcompositions,
    ) {
        check(!disposed) { "UIKit navigation entry controller is already disposed." }
        require(entry.identity() == identity) {
            "A UIKit navigation entry controller cannot change identity."
        }
        if (this.subcompositions !== subcompositions) {
            releaseContent()
            this.subcompositions = subcompositions
        }
        this.entry = entry
        contentHost?.update(entry)
    }

    internal fun realizeContent() {
        check(!disposed) { "UIKit navigation entry controller is already disposed." }
        ensureContentHost().activate()
    }

    internal fun deactivateContent() {
        check(!disposed) { "UIKit navigation entry controller is already disposed." }
        ensureContentHost().deactivate()
    }

    private fun ensureContentHost(): NavigationEntryContentHost =
        contentHost
            ?: NavigationEntryContentHost(
                root = UIKitChildren(contentView),
                nativeControllerOwner = UIKitNavigationOwner(this),
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
