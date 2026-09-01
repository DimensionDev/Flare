@file:OptIn(dev.dimension.flare.ui.LowLevelFlareApi::class)

package dev.dimension.flare.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import dev.dimension.flare.ui.EmitFlareWidget
import dev.dimension.flare.ui.FlareModifier
import dev.dimension.flare.ui.FlareUiComposable
import dev.dimension.flare.ui.currentFlareNativeControllerOwner
import dev.dimension.flare.ui.rememberFlareSubcompositionFactory

/**
 * Displays a Navigation3 back stack through the active Flare renderer.
 *
 * [backStack] remains the only business state. A committed native back interaction delivers one
 * exact, revisioned [NavigationBackRequest] through [onBack]; a cancelled interaction does not call
 * it. The caller should compare and remove the requested suffix with [NavigationBackRequest.applyTo]
 * or otherwise update its model to the request's exact target. A request may be retained for an
 * asynchronous reducer, then explicitly accepted or rejected. After rejection, a different model,
 * or the bounded platform deadline, the request becomes stale and can no longer mutate the stack.
 *
 * The stack must be non-empty and have the shape `[Page+][Overlay*]`. Every active
 * [NavEntry.contentKey] must be unique and stable, and an active key cannot change presentation in
 * place. The current renderer set supports Page only and rejects the reserved overlay values.
 * Stateful [entryDecorators] must be remembered or otherwise hoisted for the lifetime of
 * this back stack; their order is observable. The default decorator preserves saveable entry
 * state only within the current saveable host and does not provide process or native-controller
 * restoration.
 *
 * Entry and decorator content must emit only Flare widgets. Using another Compose applier can fail
 * at runtime because [NavEntry] does not retain its composable target in its public type.
 *
 * @throws IllegalArgumentException if the stack shape, content keys, or Flare presentation
 * metadata are invalid.
 */
@ExperimentalFlareNavigation
@Composable
@FlareUiComposable
public fun <K : Any> NavigationDisplay(
    backStack: List<K>,
    modifier: FlareModifier = FlareModifier.None.fillMaxSize(),
    onBack: (NavigationBackRequest<K>) -> Unit,
    entryDecorators: List<NavEntryDecorator<K>> =
        listOf(rememberSaveableStateHolderNavEntryDecorator()),
    entryProvider: (K) -> NavEntry<K>,
) {
    NavigationDisplay(
        entries =
            rememberDecoratedNavEntries(
                backStack = backStack,
                entryDecorators = entryDecorators,
                entryProvider = entryProvider,
            ),
        modifier = modifier,
        onBack = { request ->
            check(request.base.size == backStack.size) {
                "A route NavigationBackRequest must match the current back stack."
            }
            onBack(
                request.mapValues(
                    base = backStack.toList(),
                    target = backStack.dropLast(request.popCount),
                ),
            )
        },
    )
}

/**
 * Displays an already decorated Navigation3 entry list through the active Flare renderer.
 *
 * This overload never adds or reapplies decorators. [entries] must be non-empty and have the shape
 * `[Page+][Overlay*]`. Every active entry must have a unique, stable [NavEntry.contentKey], and an
 * active key cannot change presentation in place. The current renderer set supports Page only and
 * rejects the reserved overlay values. Entry and decorator content must emit only Flare widgets.
 *
 * A committed native back interaction invokes [onBack] once with an exact, revisioned
 * [NavigationBackRequest]. Re-delivering the unchanged topology keeps a deferred request pending;
 * delivering its target accepts it, while a different topology safely supersedes it. Rejection or
 * the bounded platform deadline restores the latest declared projection without quarantining future
 * back interactions. This overload provides saveable entry state only when the supplied entries
 * already contain an equivalent decorator.
 *
 * @throws IllegalArgumentException if the stack shape, content keys, or Flare presentation
 * metadata are invalid.
 */
@ExperimentalFlareNavigation
@Composable
@FlareUiComposable
public fun <K : Any> NavigationDisplay(
    entries: List<NavEntry<K>>,
    modifier: FlareModifier = FlareModifier.None.fillMaxSize(),
    onBack: (NavigationBackRequest<NavigationEntryIdentity>) -> Unit,
) {
    val dispatcher = remember { NavigationModelDispatcher() }
    val deliveryScope = rememberCoroutineScope()
    val model =
        NavigationModel(
            entries = resolveNavigationEntries(entries),
            onBack = onBack,
            subcompositions = rememberFlareSubcompositionFactory(),
            nativeControllerOwner = currentFlareNativeControllerOwner(),
        )
    SideEffect {
        // Scheduling yields before invoking the renderer, so no child composition can be created
        // from the parent's applyChanges call stack.
        dispatcher.stage(model, deliveryScope)
    }
    EmitFlareWidget(
        componentType = NavigationWidget::class,
        modifier = modifier,
        update = {
            set(dispatcher, NavigationWidget::setModelDispatcher)
        },
    )
}
