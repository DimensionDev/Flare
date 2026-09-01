@file:OptIn(
    dev.dimension.flare.ui.LowLevelFlareApi::class,
    dev.dimension.flare.ui.navigation.ExperimentalFlareNavigation::class,
)

package dev.dimension.flare.ui.android

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.transition.Transition
import com.google.android.material.transition.MaterialSharedAxis
import dev.dimension.flare.ui.FlareNativeControllerOwner
import dev.dimension.flare.ui.FlareRendererPlugin
import dev.dimension.flare.ui.FlareWidgetRegistrar
import dev.dimension.flare.ui.navigation.NAVIGATION_ACKNOWLEDGEMENT_TIMEOUT_MILLIS
import dev.dimension.flare.ui.navigation.NavigationAcknowledgementHandle
import dev.dimension.flare.ui.navigation.NavigationCommand
import dev.dimension.flare.ui.navigation.NavigationCoordinator
import dev.dimension.flare.ui.navigation.NavigationCoordinatorState
import dev.dimension.flare.ui.navigation.NavigationEntryContentHost
import dev.dimension.flare.ui.navigation.NavigationInteractionHandle
import dev.dimension.flare.ui.navigation.NavigationModel
import dev.dimension.flare.ui.navigation.NavigationModelDispatcher
import dev.dimension.flare.ui.navigation.NavigationOperation
import dev.dimension.flare.ui.navigation.NavigationOperationResult
import dev.dimension.flare.ui.navigation.NavigationPresentation
import dev.dimension.flare.ui.navigation.NavigationWidget
import dev.dimension.flare.ui.navigation.ResolvedNavigationEntry
import java.util.UUID

/**
 * Explicit native containment owner required by [AndroidViewNavigationRendererPlugin].
 *
 * Pass an instance to `FlareAndroidViewHost` as its `nativeControllerOwner`; the renderer never
 * guesses an activity by unwrapping a `Context`.
 */
public class AndroidViewNavigationOwner internal constructor(
    public val activity: FragmentActivity,
    internal val fragmentManager: FragmentManager,
    internal val lifecycleOwner: LifecycleOwner,
) : FlareNativeControllerOwner {
    public constructor(activity: FragmentActivity) : this(
        activity = activity,
        fragmentManager = activity.supportFragmentManager,
        lifecycleOwner = activity,
    )

    internal constructor(fragment: Fragment) : this(
        activity = fragment.requireActivity(),
        fragmentManager = fragment.childFragmentManager,
        lifecycleOwner = fragment,
    )
}

/** Registers a Fragment-backed, Page-only Android View navigation renderer. */
public object AndroidViewNavigationRendererPlugin : FlareRendererPlugin<AndroidViewBackend> {
    override fun register(registrar: FlareWidgetRegistrar<AndroidViewBackend>) {
        registrar.register(NavigationWidget::class) { backend -> AndroidViewNavigationWidget(backend) }
    }
}

private class AndroidViewNavigationWidget(
    backend: AndroidViewBackend,
) : AbstractAndroidWidget<FragmentContainerView>(
        FragmentContainerView(backend.context).apply {
            id = View.generateViewId()
            layoutParams =
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                )
        },
    ),
    NavigationWidget,
    DefaultLifecycleObserver {
    private val adapterId = UUID.randomUUID().toString()
    private val records = linkedMapOf<Any, ViewEntryRecord>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var owner: AndroidViewNavigationOwner? = null
    private var modelDispatcher: NavigationModelDispatcher? = null
    private var stopObservingModels: (() -> Unit)? = null
    private var pendingAcknowledgementTimeout: Runnable? = null
    private var transitionCommandToken: Long? = null
    private var commandTransitionKeys: Set<Any> = emptySet()
    private var transitionInteraction: NavigationInteractionHandle? = null
    private var interactionTransitionKeys: Set<Any> = emptySet()
    private var registered = false
    private var disposed = false
    private val coordinator =
        NavigationCoordinator(
            emitCommand = ::perform,
            onRetainedEntriesChanged = ::updateRetainedEntries,
        )
    private val backCallback =
        object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                if (modelDispatcher?.hasUndeliveredModel == true) {
                    updateBackCallback()
                    return
                }
                val interaction = coordinator.beginUserBack()
                if (interaction == null) {
                    if (coordinator.state == NavigationCoordinatorState.Idle) {
                        passBackToActivity()
                    }
                } else {
                    updateBackCallback()
                    performUserBack(interaction)
                }
            }
        }
    private val attachListener =
        object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                coordinator.resumeOperations()
            }

            override fun onViewDetachedFromWindow(view: View) = Unit
        }

    init {
        view.addOnAttachStateChangeListener(attachListener)
    }

    override fun setModelDispatcher(dispatcher: NavigationModelDispatcher) {
        check(!disposed) { "Android View navigation widget is already disposed." }
        stopObservingModels?.invoke()
        modelDispatcher = dispatcher
        stopObservingModels = dispatcher.observe(::applyModel)
    }

    private fun applyModel(model: NavigationModel) {
        check(!disposed) { "Android View navigation widget is already disposed." }
        model.entries.requirePagesOnly()
        val navigationOwner =
            model.nativeControllerOwner as? AndroidViewNavigationOwner
                ?: error(
                    "Android View navigation requires AndroidViewNavigationOwner(FragmentActivity). " +
                        "Pass it to FlareAndroidViewHost(nativeControllerOwner = ...).",
                )
        val previousOwner = owner
        check(previousOwner == null || previousOwner === navigationOwner) {
            "Android View navigation cannot change its FragmentActivity owner while it is mounted."
        }
        owner = navigationOwner
        latestModel = model
        if (!registered) {
            registered = true
            NavigationFragmentRegistry.register(adapterId, this, navigationOwner)
            navigationOwner.lifecycleOwner.lifecycle.addObserver(this)
            navigationOwner.activity.onBackPressedDispatcher.addCallback(
                navigationOwner.lifecycleOwner,
                backCallback,
            )
        }
        val wasPaused = coordinator.state == NavigationCoordinatorState.Paused
        coordinator.setModel(model)
        if (!coordinator.hasPendingAcknowledgement) {
            clearAcknowledgementTimeout()
        }
        if (wasPaused && view.isAttachedToWindow && !navigationOwner.fragmentManager.isStateSaved) {
            // A new applied model is also a fresh platform-readiness opportunity after a bounded
            // recovery failure; resume once without spinning inside the failed command callback.
            coordinator.resumeOperations()
        }
        updateBackCallback()
    }

    override fun onResume(owner: LifecycleOwner) {
        coordinator.resumeOperations()
        updateBackCallback()
    }

    override fun dispose() {
        if (disposed) return
        disposed = true
        val navigationOwner = owner
        stopObservingModels?.invoke()
        stopObservingModels = null
        modelDispatcher = null
        backCallback.remove()
        navigationOwner?.lifecycleOwner?.lifecycle?.removeObserver(this)
        navigationOwner?.let { NavigationFragmentRegistry.removeWhenSafe(adapterId, it) }
        NavigationFragmentRegistry.unregister(adapterId, this)
        view.removeOnAttachStateChangeListener(attachListener)
        clearAcknowledgementTimeout()
        clearTransitionParticipants()
        coordinator.dispose()
        records.values.forEach(ViewEntryRecord::dispose)
        records.clear()
        latestModel = null
        owner = null
        super<AbstractAndroidWidget>.dispose()
    }

    /** Called by [FlareNavigationPageFragment] when its container view has been created. */
    fun attachFragment(
        token: String,
        fragment: Fragment,
        contentContainer: FrameLayout,
    ) {
        if (disposed) return
        val record = records.values.firstOrNull { it.token == token } ?: return
        record.attach(fragment, contentContainer)
    }

    /** Called when a page Fragment releases the view that owns its entry composition. */
    fun detachFragment(
        token: String,
        fragment: Fragment,
    ) {
        records.values.firstOrNull { it.token == token }?.detach(fragment)
    }

    private fun updateRetainedEntries(entries: List<ResolvedNavigationEntry>) {
        if (disposed) return
        val model = latestModel ?: return
        val retainedKeys = entries.mapTo(mutableSetOf(), ResolvedNavigationEntry::contentKey)
        entries.forEach { entry ->
            val record = records[entry.contentKey]
            if (record == null) {
                records[entry.contentKey] =
                    ViewEntryRecord(
                        token = "$adapterId:${UUID.randomUUID()}",
                        entry = entry,
                        model = model,
                    )
            } else {
                record.update(entry, model)
            }
        }
        val iterator = records.iterator()
        while (iterator.hasNext()) {
            val (_, record) = iterator.next()
            if (record.entry.contentKey !in retainedKeys) {
                record.dispose()
                iterator.remove()
            }
        }
        updateEntryRetention()
    }

    private fun updateEntryRetention() {
        val projectedEntries = coordinator.projectedEntries
        val activeKey = projectedEntries.lastOrNull()?.contentKey
        val frozenKey = projectedEntries.getOrNull(projectedEntries.lastIndex - 1)?.contentKey
        val transitionKeys = commandTransitionKeys + interactionTransitionKeys
        records.forEach { (contentKey, record) ->
            record.updateRetention(
                when (contentKey) {
                    in transitionKeys -> ViewEntryRetention.Active
                    activeKey -> ViewEntryRetention.Active
                    frozenKey -> ViewEntryRetention.Frozen
                    else -> ViewEntryRetention.Released
                },
            )
        }
    }

    private fun perform(command: NavigationCommand) {
        val fragmentManager =
            owner?.fragmentManager
                ?: return complete(command, NavigationOperationResult.Failed(IllegalStateException("Missing navigation owner.")))
        if (!view.isAttachedToWindow || fragmentManager.isStateSaved) {
            complete(command, NavigationOperationResult.Deferred)
            updateBackCallback()
            return
        }
        beginCommandTransition(command)
        try {
            when (val operation = command.operation) {
                is NavigationOperation.PushPage -> push(fragmentManager, operation, command)

                is NavigationOperation.PopPage -> pop(fragmentManager, operation, command)

                is NavigationOperation.Reconstruct -> reconstruct(fragmentManager, operation, command)

                is NavigationOperation.PresentOverlay,
                is NavigationOperation.DismissOverlay,
                -> error("Android View navigation currently supports only Page presentation.")
            }
        } catch (error: Throwable) {
            complete(command, NavigationOperationResult.Failed(error))
        }
    }

    private fun push(
        fragmentManager: FragmentManager,
        operation: NavigationOperation.PushPage,
        command: NavigationCommand,
    ) {
        val destination = requireRecord(operation.entry)
        val transaction = fragmentManager.beginTransaction().setReorderingAllowed(true)
        operation.targetStack.dropLast(1).lastOrNull()?.let { previous ->
            val previousRecord = requireRecord(previous)
            previousRecord.updateRetention(ViewEntryRetention.Active)
            previousRecord.fragment?.let { fragment ->
                if (operation.animated) {
                    fragment.exitTransition = pageTransition(forward = true)
                }
                transaction
                    .hide(fragment)
                    .setMaxLifecycle(fragment, Lifecycle.State.CREATED)
            }
        }
        destination.updateRetention(ViewEntryRetention.Active)
        val destinationFragment = destination.createFragment()
        val completionTransition =
            if (operation.animated) {
                pageTransition(forward = true).also { destinationFragment.enterTransition = it }
            } else {
                null
            }
        transaction
            .add(view.id, destinationFragment, destination.token)
            .setMaxLifecycle(destinationFragment, Lifecycle.State.RESUMED)
        commitTransaction(transaction, completionTransition) {
            complete(command, NavigationOperationResult.Succeeded())
        }
    }

    private fun pop(
        fragmentManager: FragmentManager,
        operation: NavigationOperation.PopPage,
        command: NavigationCommand,
    ) {
        val transaction = fragmentManager.beginTransaction().setReorderingAllowed(true)
        val poppedRecord = requireRecord(operation.entry)
        poppedRecord.updateRetention(ViewEntryRetention.Active)
        val poppedFragment =
            poppedRecord.fragment
                ?: error("Missing visible navigation Fragment.")
        val previousRecord =
            operation.targetStack.lastOrNull()?.let { previous ->
                requireRecord(previous)
            } ?: error("Missing previous navigation entry.")
        previousRecord.updateRetention(ViewEntryRetention.Active)
        val previousFragment = previousRecord.fragment ?: error("Missing previous navigation Fragment.")
        val completionTransition =
            if (operation.animated) {
                poppedFragment.exitTransition = pageTransition(forward = false)
                pageTransition(forward = false).also { previousFragment.enterTransition = it }
            } else {
                null
            }
        transaction
            .remove(poppedFragment)
            .show(previousFragment)
            .setMaxLifecycle(previousFragment, Lifecycle.State.RESUMED)
        commitTransaction(transaction, completionTransition) {
            complete(command, NavigationOperationResult.Succeeded())
        }
    }

    private fun reconstruct(
        fragmentManager: FragmentManager,
        operation: NavigationOperation.Reconstruct,
        command: NavigationCommand,
    ) {
        val transaction = fragmentManager.beginTransaction().setReorderingAllowed(true)
        fragmentManager.fragments
            .filter { it.navigationAdapterId() == adapterId }
            .forEach(transaction::remove)
        records.values.forEach(ViewEntryRecord::prepareForReconstruction)
        val activeKey = operation.targetStack.lastOrNull()?.contentKey
        val frozenKey = operation.targetStack.getOrNull(operation.targetStack.lastIndex - 1)?.contentKey
        records.forEach { (contentKey, record) ->
            record.updateRetention(
                when (contentKey) {
                    activeKey -> ViewEntryRetention.Active
                    frozenKey -> ViewEntryRetention.Frozen
                    else -> ViewEntryRetention.Released
                },
            )
        }
        operation.targetStack.forEachIndexed { index, entry ->
            val record = requireRecord(entry)
            val fragment = record.createFragment()
            transaction.add(view.id, fragment, record.token)
            if (index != operation.targetStack.lastIndex) {
                transaction
                    .hide(fragment)
                    .setMaxLifecycle(fragment, Lifecycle.State.CREATED)
            } else {
                transaction.setMaxLifecycle(fragment, Lifecycle.State.RESUMED)
            }
        }
        commitTransaction(transaction) {
            complete(command, NavigationOperationResult.Succeeded())
        }
    }

    private fun performUserBack(interaction: NavigationInteractionHandle) {
        val fragmentManager = owner?.fragmentManager
        if (fragmentManager == null || !view.isAttachedToWindow || fragmentManager.isStateSaved) {
            coordinator.cancelUserBack(interaction)
            passBackToActivity()
            return
        }
        val current = coordinator.projectedEntries
        val popped = current.lastOrNull() ?: return
        val previous = current.dropLast(1).lastOrNull() ?: return
        beginInteractionTransition(interaction, popped, previous)
        try {
            val previousRecord = requireRecord(previous)
            val poppedRecord = requireRecord(popped)
            previousRecord.updateRetention(ViewEntryRetention.Active)
            poppedRecord.updateRetention(ViewEntryRetention.Active)
            val previousFragment = previousRecord.fragment ?: error("Missing previous navigation Fragment.")
            val poppedFragment = poppedRecord.fragment ?: error("Missing visible navigation Fragment.")
            poppedFragment.exitTransition = pageTransition(forward = false)
            val completionTransition = pageTransition(forward = false)
            previousFragment.enterTransition = completionTransition
            val transaction =
                fragmentManager
                    .beginTransaction()
                    .setReorderingAllowed(true)
                    .remove(poppedFragment)
                    .show(previousFragment)
                    .setMaxLifecycle(
                        previousFragment,
                        Lifecycle.State.RESUMED,
                    )
            commitTransaction(transaction, completionTransition) {
                val acknowledgement = coordinator.commitUserBack(interaction)
                finishInteractionTransition(interaction)
                acknowledgement?.let(::scheduleAcknowledgementTimeout)
                updateBackCallback()
            }
        } catch (error: Throwable) {
            coordinator.cancelUserBack(interaction)
            finishInteractionTransition(interaction)
            updateBackCallback()
            if (fragmentManager.isStateSaved || fragmentManager.isDestroyed || !view.isAttachedToWindow) {
                return
            }
            throw error
        }
    }

    private fun pageTransition(forward: Boolean): Transition = MaterialSharedAxis(MaterialSharedAxis.X, forward)

    private fun commitTransaction(
        transaction: FragmentTransaction,
        completionTransition: Transition? = null,
        onComplete: () -> Unit,
    ) {
        val transactionOwner = owner
        var completed = false
        var transitionStarted = false
        var transitionListener: Transition.TransitionListener? = null
        lateinit var transitionFallback: Runnable

        fun detachTransitionListener() {
            transitionListener?.let { listener -> completionTransition?.removeListener(listener) }
            transitionListener = null
        }

        fun completeOnce() {
            if (completed) return
            completed = true
            mainHandler.removeCallbacks(transitionFallback)
            detachTransitionListener()
            if (disposed) return
            onComplete()
        }

        fun completeAfterTransitionCallback() {
            if (!view.post(::completeOnce)) completeOnce()
        }

        transitionFallback = Runnable(::completeOnce)

        if (completionTransition != null) {
            transitionListener =
                object : Transition.TransitionListener {
                    override fun onTransitionStart(transition: Transition) {
                        transitionStarted = true
                    }

                    override fun onTransitionEnd(transition: Transition) {
                        completeAfterTransitionCallback()
                    }

                    override fun onTransitionCancel(transition: Transition) {
                        completeAfterTransitionCallback()
                    }

                    override fun onTransitionPause(transition: Transition) = Unit

                    override fun onTransitionResume(transition: Transition) = Unit
                }
            completionTransition.addListener(checkNotNull(transitionListener))
        }
        transaction.runOnCommit {
            if (disposed) {
                completeOnce()
                transactionOwner?.let { NavigationFragmentRegistry.removeWhenSafe(adapterId, it) }
                return@runOnCommit
            }
            // Fragment skips transitions until its container has completed layout.
            if (completionTransition == null || !view.isLaidOut) {
                if (!view.post(::completeOnce)) completeOnce()
            } else {
                // Fragment transitions begin from a pre-draw callback after commit. Give that
                // callback one full frame; if AndroidX skips the transition, do not leave the
                // coordinator executing forever waiting for an end event that cannot arrive.
                view.postOnAnimation {
                    view.postOnAnimation {
                        if (!transitionStarted) completeOnce()
                    }
                }
            }
        }
        mainHandler.postDelayed(transitionFallback, ANDROID_TRANSITION_COMPLETION_TIMEOUT_MILLIS)
        try {
            transaction.commit()
        } catch (error: Throwable) {
            mainHandler.removeCallbacks(transitionFallback)
            detachTransitionListener()
            throw error
        }
    }

    private fun scheduleAcknowledgementTimeout(handle: NavigationAcknowledgementHandle) {
        clearAcknowledgementTimeout()
        lateinit var timeout: Runnable
        timeout =
            Runnable {
                if (pendingAcknowledgementTimeout !== timeout) return@Runnable
                pendingAcknowledgementTimeout = null
                if (!disposed) {
                    coordinator.acknowledgementDeadlineReached(handle)
                    updateBackCallback()
                }
            }
        pendingAcknowledgementTimeout = timeout
        mainHandler.postDelayed(timeout, NAVIGATION_ACKNOWLEDGEMENT_TIMEOUT_MILLIS)
    }

    private fun clearAcknowledgementTimeout() {
        pendingAcknowledgementTimeout?.let(mainHandler::removeCallbacks)
        pendingAcknowledgementTimeout = null
    }

    private fun complete(
        command: NavigationCommand,
        result: NavigationOperationResult,
    ) {
        coordinator.completeCommand(command.token, result)
        finishCommandTransition(command.token)
        updateBackCallback()
    }

    private fun beginCommandTransition(command: NavigationCommand) {
        transitionCommandToken = command.token
        commandTransitionKeys =
            when (command.operation) {
                is NavigationOperation.PushPage,
                is NavigationOperation.PopPage,
                -> {
                    setOfNotNull(
                        command.sourceStack.lastOrNull()?.contentKey,
                        command.operation
                            .targetStack
                            .lastOrNull()
                            ?.contentKey,
                    )
                }

                is NavigationOperation.Reconstruct -> {
                    setOfNotNull(
                        command.operation
                            .targetStack
                            .lastOrNull()
                            ?.contentKey,
                    )
                }

                is NavigationOperation.PresentOverlay,
                is NavigationOperation.DismissOverlay,
                -> {
                    emptySet()
                }
            }
        updateEntryRetention()
    }

    private fun finishCommandTransition(token: Long) {
        if (transitionCommandToken != token) return
        transitionCommandToken = null
        commandTransitionKeys = emptySet()
        updateEntryRetention()
    }

    private fun beginInteractionTransition(
        interaction: NavigationInteractionHandle,
        source: ResolvedNavigationEntry,
        target: ResolvedNavigationEntry,
    ) {
        transitionInteraction = interaction
        interactionTransitionKeys = setOf(source.contentKey, target.contentKey)
        updateEntryRetention()
    }

    private fun finishInteractionTransition(interaction: NavigationInteractionHandle) {
        if (transitionInteraction != interaction) return
        transitionInteraction = null
        interactionTransitionKeys = emptySet()
        updateEntryRetention()
    }

    private fun clearTransitionParticipants() {
        transitionCommandToken = null
        commandTransitionKeys = emptySet()
        transitionInteraction = null
        interactionTransitionKeys = emptySet()
    }

    private fun requireRecord(entry: ResolvedNavigationEntry): ViewEntryRecord =
        checkNotNull(records[entry.contentKey]) {
            "No Android View host exists for navigation contentKey ${entry.contentKey}."
        }

    private fun updateBackCallback() {
        // Stay installed as this host's routing gate even at its root. A staged-but-undelivered
        // model must be consumed here rather than accidentally escaping to an outer callback; an
        // actually idle root is forwarded explicitly by passBackToActivity().
        backCallback.isEnabled = !disposed && registered
    }

    private fun passBackToActivity() {
        backCallback.isEnabled = false
        try {
            checkNotNull(owner).activity.onBackPressedDispatcher.onBackPressed()
        } finally {
            updateBackCallback()
        }
    }

    private var latestModel: NavigationModel? = null
}

private enum class ViewEntryRetention {
    Active,
    Frozen,
    Released,
}

private class ViewEntryRecord(
    val token: String,
    var entry: ResolvedNavigationEntry,
    var model: NavigationModel,
) {
    var fragment: Fragment? = null
        private set
    private var contentHost: NavigationEntryContentHost? = null
    private var contentActive = false
    private var contentContainer: FrameLayout? = null
    private var attachedFragment: Fragment? = null
    private var attachedContainer: FrameLayout? = null
    private var retention = ViewEntryRetention.Released

    fun update(
        entry: ResolvedNavigationEntry,
        model: NavigationModel,
    ) {
        val subcompositionsChanged = this.model.subcompositions !== model.subcompositions
        this.entry = entry
        this.model = model
        if (subcompositionsChanged) {
            releaseContentHost()
            applyRetention()
        } else {
            contentHost?.update(entry)
        }
    }

    fun createFragment(): Fragment {
        prepareForReconstruction()
        return FlareNavigationPageFragment
            .newInstance(
                adapterId = token.substringBefore(':'),
                token = token,
            ).also { fragment = it }
    }

    fun attach(
        fragment: Fragment,
        container: FrameLayout,
    ) {
        if (this.fragment !== fragment) {
            releaseContentHost()
            clearAttachment()
            this.fragment = fragment
        }
        attachedFragment = fragment
        attachedContainer = container
        contentContainer?.let { attachContentContainer(it, container) }
        applyRetention()
    }

    fun detach(fragment: Fragment) {
        if (attachedFragment !== fragment) return
        contentContainer?.removeFromParent()
        attachedFragment = null
        attachedContainer = null
        applyRetention()
    }

    fun updateRetention(value: ViewEntryRetention) {
        retention = value
        // Re-apply even when the policy did not change: a Fragment may have become available while
        // a reconstruct transaction was in flight.
        applyRetention()
    }

    fun prepareForReconstruction() {
        releaseContentHost()
        clearAttachment()
        fragment = null
    }

    fun dispose() {
        prepareForReconstruction()
    }

    private fun applyRetention() {
        when (retention) {
            ViewEntryRetention.Active -> {
                val currentFragment = fragment ?: return
                ensureContentHost(currentFragment)
                activateContent()
            }

            ViewEntryRetention.Frozen -> {
                val currentFragment = fragment ?: return
                ensureContentHost(currentFragment)
                deactivateContent()
            }

            ViewEntryRetention.Released -> {
                releaseContentHost()
            }
        }
    }

    private fun ensureContentContainer(fragment: Fragment): FrameLayout {
        val current = contentContainer
        if (current != null) return current
        return FrameLayout(fragment.requireContext()).also { created ->
            created.layoutParams =
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                )
            contentContainer = created
            attachedContainer?.let { attachContentContainer(created, it) }
        }
    }

    private fun attachContentContainer(
        content: FrameLayout,
        container: FrameLayout,
    ) {
        if (content.parent === container) return
        content.removeFromParent()
        container.addView(content)
    }

    private fun ensureContentHost(fragment: Fragment) {
        val current = contentHost
        if (current != null) {
            current.update(entry)
            return
        }
        contentHost =
            NavigationEntryContentHost(
                root = AndroidViewChildren(ensureContentContainer(fragment)),
                nativeControllerOwner = AndroidViewNavigationOwner(fragment),
                subcompositions = model.subcompositions,
                initialEntry = entry,
            )
        contentActive = true
    }

    private fun activateContent() {
        if (contentActive) return
        checkNotNull(contentHost).activate()
        contentActive = true
    }

    private fun deactivateContent() {
        if (!contentActive) return
        checkNotNull(contentHost).deactivate()
        contentActive = false
    }

    private fun releaseContentHost() {
        contentHost?.dispose()
        contentHost = null
        contentActive = false
        contentContainer?.removeFromParent()
        contentContainer = null
    }

    private fun clearAttachment() {
        contentContainer?.removeFromParent()
        attachedFragment = null
        attachedContainer = null
    }
}

private fun View.removeFromParent() {
    (parent as? ViewGroup)?.removeView(this)
}

internal class FlareNavigationPageFragment : Fragment() {
    override fun onCreateView(
        inflater: android.view.LayoutInflater,
        container: android.view.ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = FrameLayout(requireContext())

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)
        NavigationFragmentRegistry.attach(
            adapterId = requireArguments().getString(ARG_ADAPTER_ID).orEmpty(),
            token = requireArguments().getString(ARG_TOKEN).orEmpty(),
            fragment = this,
            container = view as FrameLayout,
        )
    }

    override fun onDestroyView() {
        NavigationFragmentRegistry.detach(
            adapterId = requireArguments().getString(ARG_ADAPTER_ID).orEmpty(),
            token = requireArguments().getString(ARG_TOKEN).orEmpty(),
            fragment = this,
        )
        super.onDestroyView()
    }

    companion object {
        private const val ARG_ADAPTER_ID = "flare.navigation.adapter_id"
        private const val ARG_TOKEN = "flare.navigation.token"

        fun newInstance(
            adapterId: String,
            token: String,
        ): FlareNavigationPageFragment =
            FlareNavigationPageFragment().apply {
                arguments =
                    Bundle().apply {
                        putString(ARG_ADAPTER_ID, adapterId)
                        putString(ARG_TOKEN, token)
                    }
            }
    }
}

private object NavigationFragmentRegistry {
    private val widgets = mutableMapOf<String, AndroidViewNavigationWidget>()

    fun register(
        adapterId: String,
        widget: AndroidViewNavigationWidget,
        owner: AndroidViewNavigationOwner,
    ) {
        widgets[adapterId] = widget
        removeOrphansWhenSafe(owner)
    }

    fun unregister(
        adapterId: String,
        widget: AndroidViewNavigationWidget,
    ) {
        if (widgets[adapterId] === widget) widgets.remove(adapterId)
    }

    fun attach(
        adapterId: String,
        token: String,
        fragment: Fragment,
        container: FrameLayout,
    ) {
        widgets[adapterId]?.attachFragment(token, fragment, container)
    }

    fun detach(
        adapterId: String,
        token: String,
        fragment: Fragment,
    ) {
        widgets[adapterId]?.detachFragment(token, fragment)
    }

    fun removeWhenSafe(
        adapterId: String,
        owner: AndroidViewNavigationOwner,
    ) {
        scheduleRemoval(owner) { fragment -> fragment.navigationAdapterId() == adapterId }
    }

    private fun removeOrphansWhenSafe(owner: AndroidViewNavigationOwner) {
        scheduleRemoval(owner) { fragment ->
            val fragmentAdapterId = fragment.navigationAdapterId()
            fragmentAdapterId != null && fragmentAdapterId !in widgets
        }
    }

    private fun scheduleRemoval(
        navigationOwner: AndroidViewNavigationOwner,
        shouldRemove: (Fragment) -> Boolean,
    ) {
        if (remove(navigationOwner.fragmentManager, shouldRemove)) return
        val lifecycle = navigationOwner.lifecycleOwner.lifecycle
        val observer =
            object : DefaultLifecycleObserver {
                override fun onResume(owner: LifecycleOwner) {
                    if (remove(navigationOwner.fragmentManager, shouldRemove)) {
                        lifecycle.removeObserver(this)
                    }
                }

                override fun onDestroy(owner: LifecycleOwner) {
                    lifecycle.removeObserver(this)
                }
            }
        lifecycle.addObserver(observer)
    }

    private fun remove(
        fragmentManager: FragmentManager,
        shouldRemove: (Fragment) -> Boolean,
    ): Boolean {
        if (fragmentManager.isDestroyed) return true
        if (fragmentManager.isStateSaved) return false
        val fragments = fragmentManager.fragments.filter(shouldRemove)
        if (fragments.isEmpty()) return true
        val transaction = fragmentManager.beginTransaction().setReorderingAllowed(true)
        fragments.forEach(transaction::remove)
        transaction.commit()
        return true
    }
}

private fun Fragment.navigationAdapterId(): String? = arguments?.getString("flare.navigation.adapter_id")

private const val ANDROID_TRANSITION_COMPLETION_TIMEOUT_MILLIS: Long = 5_000L

private fun List<ResolvedNavigationEntry>.requirePagesOnly() {
    firstOrNull { it.presentation != NavigationPresentation.Page }?.let { entry ->
        error(
            "Android View navigation currently supports only Page presentation; " +
                "contentKey ${entry.contentKey} uses ${entry.presentation}.",
        )
    }
}
