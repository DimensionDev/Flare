package dev.dimension.flare.flareui

import androidx.compose.runtime.Composition
import androidx.compose.runtime.Recomposer
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class WidgetRegistryTest {
    @Test
    fun buildsTypedTreeAndDispatchesButtonEvent() {
        val root = RecordingNode(type = null)
        val registry = recordingRegistry()
        var clicks = 0
        val composition =
            Composition(
                applier = FlareUiApplier(root),
                parent = Recomposer(EmptyCoroutineContext),
            )

        composition.setContent {
            ProvideWidgetRegistry(registry) {
                Column {
                    Text("Title")
                    Row {
                        Text("Value")
                        Button(
                            label = "Tap",
                            onClick = { clicks += 1 },
                        )
                    }
                }
            }
        }

        val column = root.children.single()
        assertEquals(ColumnType, column.type)
        assertEquals("Title", assertIs<TextProps>(column.children[0].value).value.literal)

        val row = column.children[1]
        assertEquals(RowType, row.type)
        val button = assertIs<ButtonProps>(row.children[1].value)
        assertEquals("Tap", button.label.literal)

        button.onClick()
        assertEquals(1, clicks)

        composition.dispose()
    }

    private fun recordingRegistry(): WidgetRegistry =
        WidgetRegistry.build {
            bind(ColumnType, { RecordingNode(ColumnType) }) { value = it }
            bind(RowType, { RecordingNode(RowType) }) { value = it }
            bind(TextType, { RecordingNode(TextType) }) { value = it }
            bind(ButtonType, { RecordingNode(ButtonType) }) { value = it }
        }
}

private class RecordingNode(
    type: WidgetType<*>?,
) : WidgetNode(type) {
    var value: Any? = null
    val children = mutableListOf<RecordingNode>()

    override fun insert(
        index: Int,
        child: WidgetNode,
    ) {
        children.add(index, child as RecordingNode)
    }

    override fun move(
        from: Int,
        to: Int,
        count: Int,
    ) {
        val moved = children.subList(from, from + count).toList()
        repeat(count) {
            children.removeAt(from)
        }
        val destination = if (from > to) to else to - count
        children.addAll(destination, moved)
    }

    override fun remove(
        index: Int,
        count: Int,
    ) {
        repeat(count) {
            children.removeAt(index)
        }
    }

    override fun clear() {
        children.clear()
    }
}
