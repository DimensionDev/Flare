@file:OptIn(LowLevelFlareApi::class)

package dev.dimension.flare.ui

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class FlareRuntimeTest {
    @Test
    fun createsAndDisposesIndependentSubcomposition() {
        val events = mutableListOf<String>()
        val parentRoot = RecordingChildren()
        val itemRoot = RecordingChildren()
        val system = testWidgetSystem(events)
        lateinit var factory: FlareSubcompositionFactory

        HeadlessTestHost(parentRoot, system, TestBackend).use { host ->
            host.setContent {
                factory = rememberFlareSubcompositionFactory()
            }

            val itemComposition = factory.create(itemRoot)
            itemComposition.setContent {
                TestLeaf("item")
            }

            assertEquals("item", (itemRoot.widgets.single() as RecordingLeafWidget).renderedText)

            itemComposition.dispose()

            assertEquals(emptyList(), itemRoot.widgets)
            assertEquals(listOf("dispose:leaf"), events)
        }
    }

    @Test
    fun deactivatesSubcompositionEffectsWhilePreservingItsWidgetTree() {
        val events = mutableListOf<String>()
        val parentRoot = RecordingChildren()
        val itemRoot = RecordingChildren()
        val system = testWidgetSystem(events)
        var activeEffects = 0
        var disposedEffects = 0
        lateinit var factory: FlareSubcompositionFactory
        val content: (String) -> FlareContent = { label ->
            {
                DisposableEffect(Unit) {
                    activeEffects += 1
                    onDispose {
                        activeEffects -= 1
                        disposedEffects += 1
                    }
                }
                TestLeaf(label)
            }
        }

        HeadlessTestHost(parentRoot, system, TestBackend).use { host ->
            host.setContent {
                factory = rememberFlareSubcompositionFactory()
            }
            val itemComposition = factory.create(itemRoot)
            itemComposition.setContent(content("first"))
            val preservedWidget = itemRoot.widgets.single()
            assertEquals(1, activeEffects)

            itemComposition.deactivate()

            assertEquals(0, activeEffects)
            assertEquals(1, disposedEffects)
            assertSame(preservedWidget, itemRoot.widgets.single())
            assertEquals(emptyList(), events)

            itemComposition.setContent(content("second"))

            assertEquals(1, activeEffects)
            assertEquals(1, itemRoot.widgets.size)
            assertEquals("second", (itemRoot.widgets.single() as RecordingLeafWidget).renderedText)
        }

        assertEquals(0, activeEffects)
        assertEquals(2, disposedEffects)
        assertEquals(listOf("dispose:leaf", "dispose:leaf"), events)
    }

    @Test
    fun parentDisposesOwnedSubcompositions() {
        val events = mutableListOf<String>()
        val parentRoot = RecordingChildren()
        val itemRoot = RecordingChildren()
        val system = testWidgetSystem(events)
        lateinit var factory: FlareSubcompositionFactory

        HeadlessTestHost(parentRoot, system, TestBackend).use { host ->
            host.setContent {
                factory = rememberFlareSubcompositionFactory()
            }
            factory.create(itemRoot).setContent {
                TestLeaf("item")
            }
            assertEquals(1, itemRoot.widgets.size)
        }

        assertEquals(emptyList(), itemRoot.widgets)
        assertEquals(listOf("dispose:leaf"), events)
        assertFailsWith<IllegalStateException> {
            factory.create(RecordingChildren())
        }
    }

    @Test
    fun directlyBuildsNativeTreeAndDisposesBottomUp() {
        val events = mutableListOf<String>()
        val root = RecordingChildren()
        val system = testWidgetSystem(events)
        val content: FlareContent = {
            TestContainer {
                TestLeaf("first")
            }
        }
        val composition =
            FlareComposition(
                root = root,
                widgetSystem = system,
                backend = TestBackend,
                parent = Recomposer(EmptyCoroutineContext),
            )

        composition.setContent(content)

        val container = root.widgets.single() as RecordingContainerWidget
        val leaf = container.content.widgets.single() as RecordingLeafWidget
        assertEquals("first", leaf.renderedText)
        assertEquals(1, root.beginChangesCount)
        assertEquals(1, root.endChangesCount)

        composition.dispose()

        assertEquals(
            listOf(
                "dispose:leaf",
                "dispose:container",
            ),
            events,
        )
    }

    @Test
    fun rejectsDuplicateComponentRenderer() {
        val failure =
            assertFailsWith<IllegalStateException> {
                FlareWidgetSystem(
                    testPlugin(mutableListOf()),
                    object : FlareRendererPlugin<TestBackend> {
                        override fun register(registrar: FlareWidgetRegistrar<TestBackend>) {
                            registrar.register(LeafType) { _ ->
                                RecordingLeafWidget(mutableListOf())
                            }
                        }
                    },
                )
            }

        checkNotNull(failure.message)
        assertEquals(true, failure.message!!.contains(LeafType.toString()))
    }

    @Test
    fun recomposesHeadlesslyAndRetainsWidgetIdentity() {
        val root = RecordingChildren()
        val system = testWidgetSystem(mutableListOf())

        HeadlessTestHost(root, system, TestBackend).use { host ->
            host.setContent {
                var count by remember { mutableIntStateOf(0) }
                TestLeaf(
                    text = "Count: $count",
                    onClick = { count += 1 },
                )
            }

            val initial = root.widgets.single() as RecordingLeafWidget
            assertEquals("Count: 0", initial.renderedText)

            initial.click()
            host.awaitIdle()

            assertSame(initial, root.widgets.single())
            assertEquals("Count: 1", initial.renderedText)
        }
    }
}

private data object TestBackend : FlareBackend

private val ContainerType = TestContainerWidget::class
private val LeafType = TestLeafWidget::class

private interface TestContainerWidget : FlareWidget

private interface TestLeafWidget : FlareWidget {
    fun setText(value: String)

    fun setOnClick(value: () -> Unit)
}

@Composable
@FlareUiComposable
private fun TestContainer(content: FlareContent) {
    EmitFlareWidget(
        componentType = ContainerType,
        content = content,
    )
}

@Composable
@FlareUiComposable
private fun TestLeaf(
    text: String,
    onClick: () -> Unit = {},
) {
    EmitFlareWidget(
        componentType = LeafType,
        update = {
            set(text, TestLeafWidget::setText)
            set(onClick, TestLeafWidget::setOnClick)
        },
    )
}

private fun testWidgetSystem(events: MutableList<String>): FlareWidgetSystem<TestBackend> = FlareWidgetSystem(testPlugin(events))

private fun testPlugin(events: MutableList<String>): FlareRendererPlugin<TestBackend> =
    object : FlareRendererPlugin<TestBackend> {
        override fun register(registrar: FlareWidgetRegistrar<TestBackend>) {
            registrar.register(ContainerType) { _ ->
                RecordingContainerWidget(events)
            }
            registrar.register(LeafType) { _ ->
                RecordingLeafWidget(events)
            }
        }
    }

private class RecordingChildren : FlareChildren {
    val widgets = mutableListOf<FlareWidget>()
    var beginChangesCount: Int = 0
    var endChangesCount: Int = 0

    override fun onBeginChanges() {
        beginChangesCount += 1
    }

    override fun onEndChanges() {
        endChangesCount += 1
    }

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

private class RecordingContainerWidget(
    private val events: MutableList<String>,
) : AbstractFlareWidget(),
    TestContainerWidget {
    override val children: RecordingChildren = RecordingChildren()
    val content: RecordingChildren
        get() = children

    override fun dispose() {
        events += "dispose:container"
    }
}

private class RecordingLeafWidget(
    private val events: MutableList<String>,
) : AbstractFlareWidget(),
    TestLeafWidget {
    var renderedText: String = ""
    private var onClick: () -> Unit = {}

    override fun setText(value: String) {
        renderedText = value
    }

    override fun setOnClick(value: () -> Unit) {
        onClick = value
    }

    fun click() {
        onClick()
    }

    override fun dispose() {
        onClick = {}
        events += "dispose:leaf"
    }
}

private class HeadlessTestHost<B : FlareBackend>(
    root: FlareChildren,
    widgetSystem: FlareWidgetSystem<B>,
    backend: B,
) : AutoCloseable {
    private var frameTimeNanos: Long = 0L
    private val frameClock: BroadcastFrameClock = createFrameClock()
    private val scope =
        CoroutineScope(
            Dispatchers.Unconfined +
                SupervisorJob() +
                frameClock,
        )
    private val recomposer = Recomposer(scope.coroutineContext)
    private val composition = FlareComposition(root, widgetSystem, backend, recomposer)

    init {
        scope.launch {
            recomposer.runRecomposeAndApplyChanges()
        }
    }

    fun setContent(content: FlareContent) {
        composition.setContent(content)
        awaitIdle()
    }

    fun awaitIdle() {
        Snapshot.sendApplyNotifications()
        runBlocking {
            recomposer.awaitIdle()
        }
    }

    override fun close() {
        composition.dispose()
        recomposer.cancel()
        scope.cancel()
    }

    private fun createFrameClock(): BroadcastFrameClock {
        lateinit var clock: BroadcastFrameClock
        clock =
            BroadcastFrameClock {
                frameTimeNanos += FRAME_DURATION_NANOS
                clock.sendFrame(frameTimeNanos)
            }
        return clock
    }

    private companion object {
        const val FRAME_DURATION_NANOS: Long = 16_666_667L
    }
}
