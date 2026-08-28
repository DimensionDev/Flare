@file:OptIn(dev.dimension.flare.ui.LowLevelFlareApi::class)

package dev.dimension.flare.ui.lazy

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import dev.dimension.flare.ui.AbstractFlareWidget
import dev.dimension.flare.ui.EmitFlareWidget
import dev.dimension.flare.ui.FlareBackend
import dev.dimension.flare.ui.FlareChildren
import dev.dimension.flare.ui.FlareComposition
import dev.dimension.flare.ui.FlareContent
import dev.dimension.flare.ui.FlareRendererPlugin
import dev.dimension.flare.ui.FlareSubcomposition
import dev.dimension.flare.ui.FlareSubcompositionFactory
import dev.dimension.flare.ui.FlareUiComposable
import dev.dimension.flare.ui.FlareWidget
import dev.dimension.flare.ui.FlareWidgetRegistrar
import dev.dimension.flare.ui.FlareWidgetSystem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LazyListDslTest {
    @Test
    fun rebindingTheSameItemInTheSameModelKeepsItsComposition() {
        var contentUpdates = 0
        val factory =
            object : FlareSubcompositionFactory {
                override fun create(root: FlareChildren): FlareSubcomposition =
                    object : FlareSubcomposition {
                        override fun setContent(content: FlareContent) {
                            contentUpdates += 1
                        }

                        override fun dispose() = Unit
                    }
            }
        val coordinator =
            LazyCollectionCoordinator(
                owner = this,
                onModelChanged = { _, _ -> LazyRealizedItemUpdate.RendererManaged },
                onScroll = {},
            )
        val model =
            LazyCollectionModel(
                orientation = LazyListOrientation.Vertical,
                spacing = 0f,
                crossAxisAlignment = LazyCrossAxisAlignment.Stretch,
                itemProvider =
                    IntervalLazyListScope()
                        .apply { item(key = "stable") { TestLeaf("content") } }
                        .build(),
                subcompositions = factory,
                state = LazyListState(),
            )

        coordinator.setModel(model)
        val itemHost = coordinator.createItemHost(RecordingChildren())
        itemHost.bind(0)
        itemHost.bind(0)

        assertEquals(1, contentUpdates)
        coordinator.dispose()
    }

    @Test
    fun updatingAModelDoesNotSynchronouslyReenterARealizedItemComposition() {
        var applyingParentModel = false
        var contentUpdates = 0
        val factory =
            object : FlareSubcompositionFactory {
                override fun create(root: FlareChildren): FlareSubcomposition =
                    object : FlareSubcomposition {
                        override fun setContent(content: FlareContent) {
                            check(!applyingParentModel) {
                                "A realized item composition was updated while its parent model was being applied."
                            }
                            contentUpdates += 1
                        }

                        override fun dispose() = Unit
                    }
            }
        val coordinator =
            LazyCollectionCoordinator(
                owner = this,
                onModelChanged = { _, _ -> LazyRealizedItemUpdate.Rebind },
                onScroll = {},
            )
        val model = { label: String ->
            LazyCollectionModel(
                orientation = LazyListOrientation.Vertical,
                spacing = 0f,
                crossAxisAlignment = LazyCrossAxisAlignment.Stretch,
                itemProvider =
                    IntervalLazyListScope()
                        .apply { item(key = "stable") { TestLeaf(label) } }
                        .build(),
                subcompositions = factory,
                state = LazyListState(),
            )
        }
        coordinator.setModel(model("before"))
        coordinator.createItemHost(RecordingChildren()).bind(0)

        applyingParentModel = true
        try {
            coordinator.setModel(model("after"))
        } finally {
            applyingParentModel = false
            coordinator.dispose()
        }

        assertEquals(1, contentUpdates)
    }

    @Test
    fun largeListBuildsProviderWithoutComposingItems() {
        val root = RecordingChildren()
        val widget = RecordingLazyCollectionWidget()
        val system = testWidgetSystem(widget)
        var compositions = 0

        HeadlessTestHost(root, system).use { host ->
            host.setContent {
                LazyColumn {
                    items(
                        count = 100_000,
                        key = { index -> "item-$index" },
                        contentType = { index -> index % 2 },
                    ) {
                        compositions += 1
                    }
                }
            }

            val model = checkNotNull(widget.currentModel)
            assertEquals(LazyListOrientation.Vertical, model.orientation)
            assertEquals(100_000, model.itemProvider.itemCount)
            assertEquals("item-99", model.itemProvider.key(99))
            assertEquals(1, model.itemProvider.contentType(99))
            assertEquals(0, compositions)
        }
    }

    @Test
    fun largeListUpdateDoesNotScanEveryKeyForSaveableStateCleanup() {
        var generation by mutableStateOf(0)
        var keyLookups = 0
        val widget = RecordingLazyCollectionWidget(LazyRealizedItemUpdate.RendererManaged)

        HeadlessTestHost(RecordingChildren(), testWidgetSystem(widget)).use { host ->
            host.setContent {
                val generationSnapshot = generation
                LazyColumn {
                    items(
                        count = 10_000,
                        key = { index ->
                            keyLookups += 1
                            index
                        },
                        contentType = { generationSnapshot },
                    ) { index -> TestLeaf("Item $index") }
                }
            }
            val itemHost = widget.coordinator.createItemHost(RecordingChildren())
            itemHost.bind(0)
            host.awaitIdle()

            keyLookups = 0
            generation = 1
            host.awaitIdle()

            assertTrue(keyLookups < 500, "Large-list update resolved $keyLookups keys for saveable-state cleanup.")
            itemHost.dispose()
        }
    }

    @Test
    fun emptyListProducesAnEmptyProviderWithoutRealizingContent() {
        val widget = RecordingLazyCollectionWidget()

        HeadlessTestHost(RecordingChildren(), testWidgetSystem(widget)).use { host ->
            host.setContent {
                LazyColumn {
                    items(count = 0, key = { it }) {
                        error("An empty lazy list must not compose item content.")
                    }
                }
            }

            assertEquals(0, checkNotNull(widget.currentModel).itemProvider.itemCount)
        }
    }

    @Test
    fun stateForwardsImmediateAndAnimatedScrollCommands() {
        val root = RecordingChildren()
        val widget = RecordingLazyCollectionWidget()
        val system = testWidgetSystem(widget)
        val state = LazyListState()

        HeadlessTestHost(root, system).use { host ->
            host.setContent {
                LazyRow(state = state) {
                    items(
                        count = 100,
                        key = { index -> index },
                    ) {}
                }
            }

            runBlocking {
                state.scrollToItem(index = 37, scrollOffset = 4f)
                state.animateScrollToItem(index = 82, scrollOffset = 9f)
            }

            assertEquals(
                listOf(
                    RecordedScroll(index = 37, offset = 4f, animated = false),
                    RecordedScroll(index = 82, offset = 9f, animated = true),
                ),
                widget.scrolls,
            )
        }
    }

    @Test
    fun realizesOnlyRequestedItemAndDisposesItsSubtree() {
        val root = RecordingChildren()
        val widget = RecordingLazyCollectionWidget()
        val system = testWidgetSystem(widget)

        HeadlessTestHost(root, system).use { host ->
            host.setContent {
                LazyColumn {
                    items(
                        count = 100_000,
                        key = { index -> "item-$index" },
                    ) { index ->
                        TestLeaf("Item $index")
                    }
                }
            }

            val itemRoot = RecordingChildren()
            val itemHost = widget.coordinator.createItemHost(itemRoot)
            itemHost.bind(42)
            host.awaitIdle()

            assertEquals(1, itemRoot.widgets.size)
            assertEquals("Item 42", (itemRoot.widgets.single() as RecordingLeafWidget).renderedText)

            itemHost.bind(84)
            host.awaitIdle()
            assertEquals("Item 84", (itemRoot.widgets.single() as RecordingLeafWidget).renderedText)

            itemHost.dispose()
            assertEquals(emptyList(), itemRoot.widgets)
        }
    }

    @Test
    fun oneItemMayEmitMultipleRootPrimitives() {
        val widget = RecordingLazyCollectionWidget()

        HeadlessTestHost(RecordingChildren(), testWidgetSystem(widget)).use { host ->
            host.setContent {
                LazyColumn {
                    item(key = "multiple") {
                        TestLeaf("First")
                        TestLeaf("Second")
                    }
                }
            }
            val itemRoot = RecordingChildren()
            widget.coordinator.createItemHost(itemRoot).bind(0)
            host.awaitIdle()

            assertEquals(
                listOf("First", "Second"),
                itemRoot.widgets.map { (it as RecordingLeafWidget).renderedText },
            )
        }
    }

    @Test
    fun lazyListsStretchAcrossTheirCrossAxisByDefault() {
        val root = RecordingChildren()
        val widget = RecordingLazyCollectionWidget()

        HeadlessTestHost(root, testWidgetSystem(widget)).use { host ->
            host.setContent {
                LazyColumn {
                    item(key = "column") {}
                }
            }
            assertEquals(LazyCrossAxisAlignment.Stretch, widget.currentModel?.crossAxisAlignment)

            host.setContent {
                LazyRow {
                    item(key = "row") {}
                }
            }
            assertEquals(LazyCrossAxisAlignment.Stretch, widget.currentModel?.crossAxisAlignment)
        }
    }

    @Test
    fun realizedItemFollowsItsStableKeyAcrossContentChangesAndReorder() {
        val root = RecordingChildren()
        val widget = RecordingLazyCollectionWidget()
        var items by mutableStateOf(listOf(TestItem("a", "A"), TestItem("b", "B")))

        HeadlessTestHost(root, testWidgetSystem(widget)).use { host ->
            host.setContent {
                LazyColumn {
                    items(
                        items = items,
                        key = TestItem::id,
                    ) { item ->
                        TestLeaf(item.label)
                    }
                }
            }
            val itemRoot = RecordingChildren()
            val itemHost = widget.coordinator.createItemHost(itemRoot)
            itemHost.bind(0)
            host.awaitIdle()
            assertEquals("A", (itemRoot.widgets.single() as RecordingLeafWidget).renderedText)

            items = listOf(TestItem("b", "B2"), TestItem("a", "A2"))
            host.awaitIdle()

            assertEquals("a", itemHost.key)
            assertEquals(1, itemHost.index)
            assertEquals("A2", (itemRoot.widgets.single() as RecordingLeafWidget).renderedText)
        }
    }

    @Test
    fun stateUsesUpdatedItemCountAfterRecomposition() {
        val root = RecordingChildren()
        val widget = RecordingLazyCollectionWidget()
        val state = LazyListState()
        var count by mutableStateOf(1)

        HeadlessTestHost(root, testWidgetSystem(widget)).use { host ->
            host.setContent {
                LazyColumn(state = state) {
                    items(count = count, key = { it }) {}
                }
            }
            count = 3
            host.awaitIdle()

            runBlocking {
                state.scrollToItem(2)
            }
            assertEquals(2, widget.scrolls.single().index)
        }
    }

    @Test
    fun stateRejectsTargetsOutsideTheAttachedProvider() {
        val widget = RecordingLazyCollectionWidget()
        val state = LazyListState()

        HeadlessTestHost(RecordingChildren(), testWidgetSystem(widget)).use { host ->
            host.setContent {
                LazyColumn(state = state) {
                    item(key = "only") {}
                }
            }

            assertFailsWith<IllegalArgumentException> {
                runBlocking { state.scrollToItem(1) }
            }
        }
    }

    @Test
    fun duplicateRealizedKeysFailBeforeStateCanBeShared() {
        val root = RecordingChildren()
        val widget = RecordingLazyCollectionWidget()

        HeadlessTestHost(root, testWidgetSystem(widget)).use { host ->
            host.setContent {
                LazyColumn {
                    items(count = 2, key = { "duplicate" }) { index ->
                        TestLeaf("Item $index")
                    }
                }
            }
            widget.coordinator.createItemHost(RecordingChildren()).bind(0)

            assertFailsWith<IllegalStateException> {
                widget.coordinator.createItemHost(RecordingChildren()).bind(1)
            }
        }
    }

    @Test
    fun staleRealizedKeyOwnershipTransfersWithoutBlankingThePreviousHost() {
        var keyOffset = 0
        val coordinator =
            LazyCollectionCoordinator(
                owner = this,
                onModelChanged = { _, _ -> LazyRealizedItemUpdate.Rebind },
                onScroll = {},
            )
        val provider =
            IntervalLazyListScope()
                .apply {
                    items(
                        count = 1_000,
                        key = { index -> index - keyOffset },
                    ) {}
                }.build()
        coordinator.setModel(
            LazyCollectionModel(
                orientation = LazyListOrientation.Vertical,
                spacing = 0f,
                crossAxisAlignment = LazyCrossAxisAlignment.Stretch,
                itemProvider = provider,
                subcompositions = NoOpSubcompositions,
                state = LazyListState(),
            ),
        )
        val staleHost = coordinator.createItemHost(RecordingChildren())
        val currentHost = coordinator.createItemHost(RecordingChildren())
        try {
            staleHost.bind(537)

            // The DSL callback can observe snapshot state directly. Its old realized cell still
            // caches key 537, but the current provider now assigns that key to index 538.
            keyOffset = 1
            assertEquals(false, coordinator.realizedItemsMatch(provider))
            currentHost.bind(538)

            assertEquals(537, staleHost.index)
            assertEquals(536, staleHost.key)
            assertEquals(538, currentHost.index)
            assertEquals(537, currentHost.key)
        } finally {
            coordinator.dispose()
        }
    }

    @Test
    fun arrayAndIndexedListOverloadsKeepKeysAndContentTypes() {
        val scope = IntervalLazyListScope()
        scope.items(
            items = arrayOf("a", "b"),
            key = { value -> "array-$value" },
            contentType = { "array" },
        ) {}
        scope.itemsIndexed(
            items = listOf("c", "d"),
            key = { index, value -> "list-$index-$value" },
            contentType = { index, _ -> index },
        ) { _, _ -> }

        val provider = scope.build()
        assertEquals(4, provider.itemCount)
        assertEquals("array-b", provider.key(1))
        assertEquals("array", provider.contentType(1))
        assertEquals("list-1-d", provider.key(3))
        assertEquals(1, provider.contentType(3))
    }

    @Test
    fun itemProviderForwardsLayoutVersions() {
        val scope = IntervalLazyListScope()
        scope.items(
            count = 3,
            key = { index -> "key-$index" },
            layoutVersion = { index -> "revision-$index" },
        ) {}

        val provider = scope.build()

        assertEquals("revision-0", provider.layoutVersion(0))
        assertEquals("revision-2", provider.layoutVersion(2))
    }

    @Test
    fun rememberSaveableStateReturnsWhenAStableKeyIsRealizedAgain() {
        val root = RecordingChildren()
        val widget = RecordingLazyCollectionWidget()
        var increment: () -> Unit = {}

        HeadlessTestHost(root, testWidgetSystem(widget)).use { host ->
            host.setContent {
                LazyColumn {
                    item(key = "stable") {
                        var count by rememberSaveable { mutableStateOf(0) }
                        increment = { count += 1 }
                        TestLeaf("Count $count")
                    }
                }
            }
            val firstRoot = RecordingChildren()
            val firstHost = widget.coordinator.createItemHost(firstRoot)
            firstHost.bind(0)
            host.awaitIdle()
            increment()
            host.awaitIdle()
            assertEquals("Count 1", (firstRoot.widgets.single() as RecordingLeafWidget).renderedText)

            firstHost.dispose()
            val secondRoot = RecordingChildren()
            widget.coordinator.createItemHost(secondRoot).bind(0)
            host.awaitIdle()

            assertEquals("Count 1", (secondRoot.widgets.single() as RecordingLeafWidget).renderedText)
        }
    }

    @Test
    fun itemApplyTransactionNotifiesTheNativeMeasurementOwner() {
        val widget = RecordingLazyCollectionWidget()
        var expand: () -> Unit = {}
        var appliedTransactions = 0

        HeadlessTestHost(RecordingChildren(), testWidgetSystem(widget)).use { host ->
            host.setContent {
                LazyColumn {
                    item(key = "timeline-post") {
                        var expanded by rememberSaveable { mutableStateOf(false) }
                        expand = { expanded = true }
                        TestLeaf(if (expanded) "expanded body" else "title")
                    }
                }
            }
            val itemRoot = RecordingChildren()
            val invalidatingRoot =
                InvalidatingLazyItemChildren(itemRoot) {
                    appliedTransactions += 1
                }
            val itemHost = widget.coordinator.createItemHost(invalidatingRoot)
            itemHost.bind(0)
            host.awaitIdle()
            val initialTransactions = appliedTransactions

            expand()
            host.awaitIdle()

            assertTrue(appliedTransactions > initialTransactions)
            assertEquals("expanded body", (itemRoot.widgets.single() as RecordingLeafWidget).renderedText)
        }
    }
}

private data class TestItem(
    val id: String,
    val label: String,
)

private data object TestBackend : FlareBackend

private data object NoOpSubcompositions : FlareSubcompositionFactory {
    override fun create(root: FlareChildren): FlareSubcomposition =
        object : FlareSubcomposition {
            override fun setContent(content: FlareContent) = Unit

            override fun dispose() = Unit
        }
}

private class RecordingLazyCollectionWidget(
    private val realizedItemUpdate: LazyRealizedItemUpdate = LazyRealizedItemUpdate.Rebind,
) : AbstractFlareWidget(),
    LazyCollectionWidget {
    var currentModel: LazyCollectionModel? = null
    val scrolls = mutableListOf<RecordedScroll>()
    val coordinator =
        LazyCollectionCoordinator(
            owner = this,
            onModelChanged = { _, _ -> realizedItemUpdate },
            onScroll = { request ->
                scrolls += RecordedScroll(request.index, request.scrollOffset, request.animated)
                request.complete()
            },
        )

    override fun setModel(model: LazyCollectionModel) {
        currentModel = model
        coordinator.setModel(model)
    }

    override fun dispose() {
        coordinator.dispose()
    }
}

private data class RecordedScroll(
    val index: Int,
    val offset: Float,
    val animated: Boolean,
)

private fun testWidgetSystem(widget: RecordingLazyCollectionWidget): FlareWidgetSystem<TestBackend> =
    FlareWidgetSystem(
        object : FlareRendererPlugin<TestBackend> {
            override fun register(registrar: FlareWidgetRegistrar<TestBackend>) {
                registrar.register(LazyCollectionWidget::class) { _ -> widget }
                registrar.register(TestLeafWidget::class) { _ -> RecordingLeafWidget() }
            }
        },
    )

private interface TestLeafWidget : FlareWidget {
    fun setText(value: String)
}

@Composable
@FlareUiComposable
private fun TestLeaf(text: String) {
    EmitFlareWidget(
        componentType = TestLeafWidget::class,
        update = {
            set(text, TestLeafWidget::setText)
        },
    )
}

private class RecordingLeafWidget :
    AbstractFlareWidget(),
    TestLeafWidget {
    var renderedText: String = ""

    override fun setText(value: String) {
        renderedText = value
    }
}

private class RecordingChildren : FlareChildren {
    val widgets = mutableListOf<FlareWidget>()

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

private class HeadlessTestHost(
    root: FlareChildren,
    widgetSystem: FlareWidgetSystem<TestBackend>,
) : AutoCloseable {
    private var frameTimeNanos: Long = 0L
    private val frameClock = createFrameClock()
    private val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob() + frameClock)
    private val recomposer = Recomposer(scope.coroutineContext)
    private val composition = FlareComposition(root, widgetSystem, TestBackend, recomposer)

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
                frameTimeNanos += 16_666_667L
                clock.sendFrame(frameTimeNanos)
            }
        return clock
    }
}
