@file:OptIn(LowLevelNativeKitApi::class)

package dev.dimension.compose.nativekit

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

class NativeKitRuntimeTest {
    @Test
    fun createsAndDisposesIndependentSubcomposition() {
        val events = mutableListOf<String>()
        val parentRoot = RecordingChildren()
        val itemRoot = RecordingChildren()
        val system = testWidgetSystem(events)
        lateinit var factory: NativeKitSubcompositionFactory

        HeadlessTestHost(parentRoot, system, TestBackend).use { host ->
            host.setContent {
                factory = rememberNativeKitSubcompositionFactory()
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
        lateinit var factory: NativeKitSubcompositionFactory
        val content: (String) -> NativeKitContent = { label ->
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
                factory = rememberNativeKitSubcompositionFactory()
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
        lateinit var factory: NativeKitSubcompositionFactory

        HeadlessTestHost(parentRoot, system, TestBackend).use { host ->
            host.setContent {
                factory = rememberNativeKitSubcompositionFactory()
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
        val content: NativeKitContent = {
            TestContainer {
                TestLeaf("first")
            }
        }
        val composition =
            NativeKitComposition(
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
                NativeKitWidgetSystem(
                    testPlugin(mutableListOf()),
                    object : NativeKitRendererPlugin<TestBackend> {
                        override fun register(registrar: NativeKitWidgetRegistrar<TestBackend>) {
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

private data object TestBackend : NativeKitBackend

private val ContainerType = TestContainerWidget::class
private val LeafType = TestLeafWidget::class

private interface TestContainerWidget : NativeKitWidget

private interface TestLeafWidget : NativeKitWidget {
    fun setText(value: String)

    fun setOnClick(value: () -> Unit)
}

@Composable
@NativeKitComposable
private fun TestContainer(content: NativeKitContent) {
    EmitNativeKitWidget(
        componentType = ContainerType,
        content = content,
    )
}

@Composable
@NativeKitComposable
private fun TestLeaf(
    text: String,
    onClick: () -> Unit = {},
) {
    EmitNativeKitWidget(
        componentType = LeafType,
        update = {
            set(text, TestLeafWidget::setText)
            set(onClick, TestLeafWidget::setOnClick)
        },
    )
}

private fun testWidgetSystem(events: MutableList<String>): NativeKitWidgetSystem<TestBackend> = NativeKitWidgetSystem(testPlugin(events))

private fun testPlugin(events: MutableList<String>): NativeKitRendererPlugin<TestBackend> =
    object : NativeKitRendererPlugin<TestBackend> {
        override fun register(registrar: NativeKitWidgetRegistrar<TestBackend>) {
            registrar.register(ContainerType) { _ ->
                RecordingContainerWidget(events)
            }
            registrar.register(LeafType) { _ ->
                RecordingLeafWidget(events)
            }
        }
    }

private class RecordingChildren : NativeKitChildren {
    val widgets = mutableListOf<NativeKitWidget>()
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
        widget: NativeKitWidget,
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
) : AbstractNativeKitWidget(),
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
) : AbstractNativeKitWidget(),
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

private class HeadlessTestHost<B : NativeKitBackend>(
    root: NativeKitChildren,
    widgetSystem: NativeKitWidgetSystem<B>,
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
    private val composition = NativeKitComposition(root, widgetSystem, backend, recomposer)

    init {
        scope.launch {
            recomposer.runRecomposeAndApplyChanges()
        }
    }

    fun setContent(content: NativeKitContent) {
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
