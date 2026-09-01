@file:OptIn(
    ExperimentalFlareNavigation::class,
    dev.dimension.flare.ui.LowLevelFlareApi::class,
)

package dev.dimension.flare.ui.navigation

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Recomposer
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import dev.dimension.flare.ui.AbstractFlareWidget
import dev.dimension.flare.ui.FlareBackend
import dev.dimension.flare.ui.FlareChildren
import dev.dimension.flare.ui.FlareComposition
import dev.dimension.flare.ui.FlareNativeControllerOwner
import dev.dimension.flare.ui.FlareRendererPlugin
import dev.dimension.flare.ui.FlareSubcomposition
import dev.dimension.flare.ui.FlareSubcompositionFactory
import dev.dimension.flare.ui.FlareWidget
import dev.dimension.flare.ui.FlareWidgetRegistrar
import dev.dimension.flare.ui.FlareWidgetSystem
import dev.dimension.flare.ui.ProvideFlareNativeControllerOwner
import dev.dimension.flare.ui.currentFlareNativeControllerOwner
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

public class NavigationDisplayTest {
    @Test
    public fun conflatesStagedModelsBeforePostApplyDelivery(): Unit =
        runBlocking {
            val dispatcher = NavigationModelDispatcher()
            val delivered = mutableListOf<NavigationModel>()
            dispatcher.observe(delivered::add)
            val stale = unusedDisplayModel()
            val latest = unusedDisplayModel()

            dispatcher.stage(stale, this)
            dispatcher.stage(latest, this)
            assertTrue(dispatcher.hasUndeliveredModel)
            withTimeout(1_000L) {
                while (delivered.isEmpty()) yield()
            }

            assertEquals(listOf(latest), delivered)
            assertFalse(dispatcher.hasUndeliveredModel)
        }

    @Test
    public fun deliversTheModelOnlyAfterTheRendererIsInstalled(): Unit =
        runNavigationDisplayTest { recomposer ->
            val widget = RecordingNavigationWidget()
            val composition =
                FlareComposition(
                    root = RecordingChildren(),
                    widgetSystem = testWidgetSystem(widget),
                    backend = TestBackend,
                    parent = recomposer,
                )

            composition.setContent {
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

            widget.awaitModel()
            assertFalse(widget.modelWasDeliveredDuringDispatcherInstall)
            composition.dispose()
        }

    @Test
    public fun decoratesABackStackWithoutEagerlyComposingEntries(): Unit =
        runNavigationDisplayTest { recomposer ->
            val root = RecordingChildren()
            val widget = RecordingNavigationWidget()
            val events = mutableListOf<String>()
            val provider: (DisplayTestRoute) -> NavEntry<DisplayTestRoute> =
                entryProvider<DisplayTestRoute> {
                    entry<DisplayTestHome> {
                        events += "entry"
                    }
                }
            val decorators =
                listOf(
                    recordingDecorator<DisplayTestRoute>("first", events),
                    recordingDecorator<DisplayTestRoute>("second", events),
                )
            val composition =
                FlareComposition(
                    root = root,
                    widgetSystem = testWidgetSystem(widget),
                    backend = TestBackend,
                    parent = recomposer,
                )

            composition.setContent {
                NavigationDisplay(
                    backStack = listOf(DisplayTestHome),
                    onBack = {},
                    entryDecorators = decorators,
                    entryProvider = provider,
                )
            }

            val model = widget.awaitModel()
            assertEquals(1, model.entries.size)
            assertEquals(emptyList(), events)

            val entryComposition = model.subcompositions.create(RecordingChildren())
            entryComposition.setContent {
                model.entries
                    .single()
                    .entry
                    .Content()
            }
            assertEquals(
                listOf(
                    "first:before",
                    "second:before",
                    "entry",
                    "second:after",
                    "first:after",
                ),
                events,
            )
            entryComposition.dispose()
            composition.dispose()
        }

    @Test
    public fun highLevelBackRequestUsesRouteValuesAndAppliesDirectly(): Unit =
        runNavigationDisplayTest { recomposer ->
            val widget = RecordingNavigationWidget()
            val backStack = listOf<DisplayTestRoute>(DisplayTestHome, DisplayTestDetail)
            var received: NavigationBackRequest<DisplayTestRoute>? = null
            val composition =
                FlareComposition(
                    root = RecordingChildren(),
                    widgetSystem = testWidgetSystem(widget),
                    backend = TestBackend,
                    parent = recomposer,
                )

            try {
                composition.setContent {
                    NavigationDisplay(
                        backStack = backStack,
                        onBack = { received = it },
                        entryDecorators = emptyList(),
                        entryProvider = { route ->
                            NavEntry(
                                key = route,
                                contentKey = route,
                            ) {}
                        },
                    )
                }

                val model = widget.awaitModel()
                model.onBack(
                    NavigationBackRequest(
                        requestId = 42L,
                        baseRevision = 7L,
                        base = model.entries.topology(),
                        target = model.entries.dropLast(1).topology(),
                        popCount = 1,
                        isActiveRequest = { true },
                        acceptRequest = { true },
                        rejectRequest = { true },
                        abortAcceptedRequest = { true },
                    ),
                )

                val request = assertNotNull(received)
                assertEquals(42L, request.requestId)
                assertEquals(7L, request.baseRevision)
                assertEquals(backStack, request.base)
                assertEquals(listOf(DisplayTestHome), request.target)
                val mutableBackStack = backStack.toMutableList()
                assertTrue(request.applyTo(mutableBackStack))
                assertEquals(listOf<DisplayTestRoute>(DisplayTestHome), mutableBackStack)
            } finally {
                composition.dispose()
            }
        }

    @Test
    public fun deliversOneAtomicModelWithoutEagerlyComposingEntries(): Unit =
        runNavigationDisplayTest { recomposer ->
            val root = RecordingChildren()
            val widget = RecordingNavigationWidget()
            var entryCompositions = 0
            var requestedPopCount = 0
            val composition =
                FlareComposition(
                    root = root,
                    widgetSystem = testWidgetSystem(widget),
                    backend = TestBackend,
                    parent = recomposer,
                )

            composition.setContent {
                NavigationDisplay(
                    entries =
                        listOf(
                            NavEntry(
                                key = "home",
                                contentKey = "home",
                            ) {
                                entryCompositions += 1
                            },
                        ),
                    onBack = { requestedPopCount = it.popCount },
                )
            }

            val model = widget.awaitModel()
            assertEquals(listOf("home"), model.entries.map(ResolvedNavigationEntry::contentKey))
            assertEquals(0, entryCompositions)
            val entryComposition = model.subcompositions.create(RecordingChildren())
            entryComposition.setContent {
                model.entries
                    .single()
                    .entry
                    .Content()
            }
            assertEquals(1, entryCompositions)
            model.onBack(testBackRequest(popCount = 2))
            assertEquals(2, requestedPopCount)

            entryComposition.dispose()
            composition.dispose()
            assertEquals(emptyList(), root.widgets)
        }

    @Test
    public fun propagatesTheHostOwnerAndOverridesItForEntryContent(): Unit =
        runNavigationDisplayTest { recomposer ->
            val root = RecordingChildren()
            val widget = RecordingNavigationWidget()
            val hostOwner = TestNativeControllerOwner("host")
            val entryOwner = TestNativeControllerOwner("entry")
            var ownerSeenByEntry: FlareNativeControllerOwner? = null
            val composition =
                FlareComposition(
                    root = root,
                    widgetSystem = testWidgetSystem(widget),
                    backend = TestBackend,
                    parent = recomposer,
                )

            composition.setContent {
                ProvideFlareNativeControllerOwner(hostOwner) {
                    NavigationDisplay(
                        entries =
                            listOf(
                                NavEntry(
                                    key = "home",
                                    contentKey = "home",
                                ) {
                                    ownerSeenByEntry = currentFlareNativeControllerOwner()
                                },
                            ),
                        onBack = {},
                    )
                }
            }

            val model = widget.awaitModel()
            assertSame(hostOwner, model.nativeControllerOwner)
            val entryHost =
                NavigationEntryContentHost(
                    root = RecordingChildren(),
                    nativeControllerOwner = entryOwner,
                    subcompositions = model.subcompositions,
                    initialEntry = model.entries.single(),
                )
            assertSame(entryOwner, ownerSeenByEntry)

            entryHost.dispose()
            composition.dispose()
        }
}

private fun testBackRequest(popCount: Int): NavigationBackRequest<NavigationEntryIdentity> {
    val base =
        List(popCount + 1) { index ->
            NavigationEntryIdentity(
                contentKey = "route-$index",
                presentation = NavigationPresentation.Page,
            )
        }
    return NavigationBackRequest(
        requestId = 1L,
        baseRevision = 1L,
        base = base,
        target = base.dropLast(popCount),
        popCount = popCount,
        isActiveRequest = { true },
        acceptRequest = { true },
        rejectRequest = { true },
        abortAcceptedRequest = { true },
    )
}

private fun unusedDisplayModel(): NavigationModel =
    NavigationModel(
        entries = emptyList(),
        onBack = {},
        subcompositions = UnusedDisplaySubcompositionFactory,
    )

private fun runNavigationDisplayTest(block: suspend (Recomposer) -> Unit) {
    runBlocking(BroadcastFrameClock()) {
        val recomposer = Recomposer(coroutineContext)
        val runner = launch { recomposer.runRecomposeAndApplyChanges() }
        try {
            block(recomposer)
        } finally {
            recomposer.close()
            runner.join()
        }
    }
}

private data object TestBackend : FlareBackend

private data class TestNativeControllerOwner(
    val label: String,
) : FlareNativeControllerOwner

private sealed interface DisplayTestRoute

private data object DisplayTestHome : DisplayTestRoute

private data object DisplayTestDetail : DisplayTestRoute

private object UnusedDisplaySubcompositionFactory : FlareSubcompositionFactory {
    override fun create(root: FlareChildren): FlareSubcomposition = error("Dispatcher test does not compose entries.")
}

private fun <T : Any> recordingDecorator(
    label: String,
    events: MutableList<String>,
): NavEntryDecorator<T> =
    NavEntryDecorator(
        decorate = { entry ->
            events += "$label:before"
            entry.Content()
            events += "$label:after"
        },
    )

private class RecordingNavigationWidget :
    AbstractFlareWidget(),
    NavigationWidget {
    private var stopObservingModels: (() -> Unit)? = null

    var model: NavigationModel? = null
        private set

    var modelWasDeliveredDuringDispatcherInstall: Boolean = false
        private set

    override fun setModelDispatcher(dispatcher: NavigationModelDispatcher) {
        stopObservingModels?.invoke()
        var installing = true
        stopObservingModels =
            dispatcher.observe { model ->
                modelWasDeliveredDuringDispatcherInstall = installing
                this.model = model
            }
        installing = false
    }

    override fun dispose() {
        stopObservingModels?.invoke()
        stopObservingModels = null
    }

    suspend fun awaitModel(): NavigationModel =
        withTimeout(1_000L) {
            while (model == null) yield()
            checkNotNull(model)
        }
}

private fun testWidgetSystem(widget: RecordingNavigationWidget): FlareWidgetSystem<TestBackend> =
    FlareWidgetSystem(
        object : FlareRendererPlugin<TestBackend> {
            override fun register(registrar: FlareWidgetRegistrar<TestBackend>) {
                registrar.register(NavigationWidget::class) { widget }
            }
        },
    )

private class RecordingChildren : FlareChildren {
    val widgets: MutableList<FlareWidget> = mutableListOf()

    override fun insert(
        index: Int,
        widget: FlareWidget,
    ) {
        widgets.add(index, widget)
    }

    override fun move(
        fromIndex: Int,
        toIndex: Int,
        count: Int,
    ) {
        val moved = widgets.subList(fromIndex, fromIndex + count).toList()
        widgets.subList(fromIndex, fromIndex + count).clear()
        val destination = if (fromIndex > toIndex) toIndex else toIndex - count
        widgets.addAll(destination, moved)
    }

    override fun remove(
        index: Int,
        count: Int,
    ) {
        widgets.subList(index, index + count).clear()
    }
}
