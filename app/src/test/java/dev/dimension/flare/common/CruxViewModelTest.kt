package dev.dimension.flare.common

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class CruxViewModelTest {
    @Test
    fun actionErrorsKeepContentAndSuccessfulEventsClearThem() {
        val bridge = FakeBridge(7)
        val model = CruxViewModel(binding({ bridge }))
        bridge.failUpdate = true
        model.send(1)
        assertEquals(7, model.state.value)
        assertEquals("update failed", model.error.value)
        bridge.failUpdate = false
        model.send(2)
        assertEquals(9, model.state.value)
        assertNull(model.error.value)
        model.close()
    }

    @Test
    fun invalidEffectsAndViewFailuresKeepTheLastDecodedState() {
        val bridge = FakeBridge(7)
        var invalidEffects = true
        val model =
            CruxViewModel(
                binding({ bridge }) { _, render ->
                    check(!invalidEffects) { "invalid effects" }
                    render()
                },
            )
        model.send(1)
        assertEquals(7, model.state.value)
        assertEquals("invalid effects", model.error.value)
        invalidEffects = false
        bridge.failView = true
        model.send(1)
        assertEquals(7, model.state.value)
        assertEquals("view failed", model.error.value)
        bridge.failView = false
        model.send(1)
        assertEquals(10, model.state.value)
        assertNull(model.error.value)
        model.close()
    }

    @Test
    fun initializationFailureClosesTheBridgeAndDisablesDispatch() {
        val bridge = FakeBridge(7).apply { failView = true }
        val model = CruxViewModel(binding({ bridge }))
        assertNull(model.state.value)
        assertNotNull(model.error.value)
        assertEquals(1, bridge.closeCount)
        model.send(1)
        model.close()
        assertEquals(0, bridge.updateCount)
        assertEquals(1, bridge.closeCount)
    }

    @Test
    fun factoryFailureIsReportedWithoutAState() {
        val model = CruxViewModel(binding({ error("factory failed") }))
        assertNull(model.state.value)
        assertEquals("factory failed", model.error.value)
        model.send(1)
        model.close()
    }

    @Test
    fun closingIgnoresPendingRendersAndReleasesOnlyOnce() {
        val bridge = FakeBridge(7)
        var pendingRender: (() -> Unit)? = null
        val model = CruxViewModel(binding({ bridge }) { _, render -> pendingRender = render })
        model.send(1)
        model.close()
        model.close()
        pendingRender?.invoke()
        model.send(1)
        assertEquals(7, model.state.value)
        assertEquals(1, bridge.viewCount)
        assertEquals(1, bridge.updateCount)
        assertEquals(1, bridge.closeCount)
    }

    @Test
    fun keyedOwnersRetainDifferentStateTypesAndClearBothBridges() {
        val firstBridge = FakeBridge(7)
        val secondBridge = FakeBridge(9)
        val first = CruxViewModel(binding({ firstBridge }))
        val second =
            CruxViewModel(
                CruxBinding<String, String>(
                    createBridge = { secondBridge },
                    encodeEvent = { it.encodeToByteArray() },
                    decodeState = { "value:${it.decodeToString()}" },
                    processEffects = { _, render -> render() },
                ),
            )
        val store = ViewModelStore()
        val firstProvider = ViewModelProvider.create(store, viewModelFactory { initializer { first } })
        val secondProvider = ViewModelProvider.create(store, viewModelFactory { initializer { second } })
        assertSame(first, firstProvider["counter", CruxViewModel::class])
        assertSame(first, secondProvider["counter", CruxViewModel::class])
        assertSame(second, secondProvider["other", CruxViewModel::class])
        first.send(1)
        second.send("2")
        assertEquals(8, first.state.value)
        assertEquals("value:11", second.state.value)
        store.clear()
        assertEquals(1, firstBridge.closeCount)
        assertEquals(1, secondBridge.closeCount)
    }

    private fun binding(
        createBridge: () -> CruxBridge,
        processEffects: (ByteArray, () -> Unit) -> Unit = { _, render -> render() },
    ) = CruxBinding<Int, Int>(
        createBridge = createBridge,
        encodeEvent = { it.toString().encodeToByteArray() },
        decodeState = { it.decodeToString().toInt() },
        processEffects = processEffects,
    )

    private class FakeBridge(
        var value: Int,
    ) : CruxBridge {
        var failUpdate = false
        var failView = false
        var closeCount = 0
        var updateCount = 0
        var viewCount = 0

        override fun update(event: ByteArray): ByteArray {
            check(closeCount == 0)
            updateCount++
            check(!failUpdate) { "update failed" }
            value += event.decodeToString().toInt()
            return byteArrayOf(0)
        }

        override fun view(): ByteArray {
            check(closeCount == 0)
            viewCount++
            check(!failView) { "view failed" }
            return value.toString().encodeToByteArray()
        }

        override fun close() {
            closeCount++
        }
    }
}
